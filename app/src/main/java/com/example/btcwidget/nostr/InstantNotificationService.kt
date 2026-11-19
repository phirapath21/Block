package com.example.btcwidget.nostr

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.btcwidget.MainActivity
import com.example.btcwidget.R
import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Nip17
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Keeps a connection to the user's relays open while Block is closed, so notifications arrive
 * within seconds instead of at the next periodic check. Nothing goes through any other server.
 * Android requires a visible notification while it runs; it sits in a silent, minimised channel.
 */
class InstantNotificationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pool: RelayPool? = null
    private var account: String? = null
    private val seen = ConcurrentHashMap.newKeySet<String>()
    private var network: ConnectivityManager.NetworkCallback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        if (!InstantPush.wanted(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        InstantPush.running = true
        val me = NostrAccountStore.account(this)?.pubkey
        if (me != account || pool == null) connect(me) else pool?.let { p -> p.urls.forEach(p::retry) }
        return START_STICKY
    }

    private fun startInForeground() {
        val channel = "instant_notifications"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager?.getNotificationChannel(channel) == null) {
                manager?.createNotificationChannel(
                    NotificationChannel(channel, "Instant notifications", NotificationManager.IMPORTANCE_MIN).apply {
                        description = "Shown while Block stays connected to your relays. You can hide it here."
                        setShowBadge(false)
                    }
                )
            }
        }
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setContentTitle(com.example.btcwidget.ui.social.tr("Instant notifications are on"))
            .setContentText(com.example.btcwidget.ui.social.tr("Connected to your relays"))
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING else 0
        ServiceCompat.startForeground(this, FOREGROUND_ID, notification, type)
    }

    private fun connect(me: String?) {
        pool?.closeAll()
        pool = null
        account = me
        seen.clear()
        if (me == null) return
        val p = RelayPool(client = quietClient, stayAwake = true)
        pool = p
        p.setRelays(NostrBackground.relays(this))
        scope.launch {
            // First what arrived while nothing was listening, then everything new as it comes.
            runCatching { NostrBackground.check(this@InstantNotificationService) }
            p.subscribe(com.example.btcwidget.nostr.core.NotificationFilters.live(me, System.currentTimeMillis() / 1000), { event, _ -> onEvent(me, event) })
        }
        watchNetwork()
    }

    private fun onEvent(me: String, event: Event) {
        if (event.pubkey == me || !seen.add(event.id)) return
        if (event.kind == Nip17.GIFT_WRAP) {
            scope.launch {
                val signer = NostrAccountStore.signer(this@InstantNotificationService) ?: return@launch
                // A signer app that would need to ask returns null: that message waits for the app.
                val message = runCatching {
                    Nip17.unwrap(event) { c, peer -> signer.nip44Decrypt(c, peer, interactive = false) ?: error("locked") }
                }.getOrNull() ?: return@launch
                if (message.sender != me && message.isBubble) NostrBackground.announceMessage(this@InstantNotificationService, message)
            }
        } else {
            NostrBackground.announceActivity(this, event)
        }
    }

    /** A new network (Wi-Fi to mobile, back online): reconnect at once instead of waiting out the backoff. */
    private fun watchNetwork() {
        if (network != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(n: Network) { pool?.let { p -> p.urls.forEach(p::retry) } }
        }
        runCatching { cm.registerDefaultNetworkCallback(callback); network = callback }
    }

    override fun onDestroy() {
        InstantPush.running = false
        network?.let { cb -> runCatching { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) } }
        network = null
        pool?.closeAll()
        pool = null
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val FOREGROUND_ID = 150

        /** Pings less often than the app's own connections: the line mostly sits idle, and each ping wakes the radio. */
        val quietClient by lazy { RelayPool.defaultClient.newBuilder().pingInterval(4, TimeUnit.MINUTES).build() }
    }
}
