package com.example.btcwidget.nostr

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.example.btcwidget.AppPrefs
import com.example.btcwidget.NotificationAccess

/**
 * Instant notifications without any push server: [InstantNotificationService] keeps a
 * connection to the user's relays open, so replies, zaps and messages are announced within
 * seconds. Runs only while the user is logged in, phone notifications are on, they chose
 * instant, and Android lets the app notify. The periodic check stays as a fallback.
 */
object InstantPush {
    private const val TAG = "InstantPush"

    /** True while the service holds the connection (the in-app subscription then stays quiet). */
    @Volatile var running = false
        internal set

    /** Whether the user picked instant notifications (false until asked). */
    fun chosen(context: Context): Boolean = AppPrefs.instantPush(context) == true

    /** Whether to ask about instant notifications: they were never offered on this phone. */
    fun notAsked(context: Context): Boolean = AppPrefs.instantPush(context) == null

    fun wanted(context: Context): Boolean =
        chosen(context) && NostrBackground.wanted(context) && NotificationAccess.canNotify(context)

    /** Turns instant notifications on or off for this phone, and starts or stops the service. */
    fun setChosen(context: Context, on: Boolean) {
        AppPrefs.setInstantPush(context, on)
        sync(context)
    }

    /**
     * Starts the service when it should run, stops it when it shouldn't. Safe to call often
     * (app start, resume, settings, login, boot, the periodic refresh). Android may refuse to
     * start it from the background; it then starts the next time the app opens.
     */
    fun sync(context: Context) {
        val app = context.applicationContext
        val intent = Intent(app, InstantNotificationService::class.java)
        if (wanted(app)) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) app.startForegroundService(intent) else app.startService(intent)
            }.onFailure { Log.d(TAG, "Couldn't start: ${it.message}") }
        } else if (running) {
            app.stopService(intent)
        }
    }

    /** Whether Android's battery optimisation leaves the connection alone while the phone sleeps. */
    fun unrestricted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
}
