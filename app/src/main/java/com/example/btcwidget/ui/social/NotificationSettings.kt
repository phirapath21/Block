package com.example.btcwidget.ui.social

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.btcwidget.nostr.InstantPush

// ---- Notifications ----

private val NOTIFICATION_TYPES = listOf(
    "reply" to "Replies", "mention" to "Mentions", "repost" to "Reposts",
    "like" to "Likes", "zap" to "Zaps", "dm" to "Messages"
)

@Composable
internal fun NotificationsPage(vm: SocialViewModel) {
    val context = LocalContext.current
    val push by vm.pushEnabled.collectAsState()
    val off by vm.notificationsOff.collectAsState()
    var instant by remember { mutableStateOf(InstantPush.chosen(context)) }
    var unrestricted by remember { mutableStateOf(InstantPush.unrestricted(context)) }
    var askInstant by remember { mutableStateOf(false) }
    // Back from the battery settings: show whether it worked.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { unrestricted = InstantPush.unrestricted(context) }
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { InstantPush.sync(context) }
    fun chooseInstant(on: Boolean) {
        instant = on
        InstantPush.setChosen(context, on)
        if (on && !InstantPush.unrestricted(context)) askBatteryExemption(context)
    }

    SettingsSwitchRow(
        tr("Phone notifications"),
        summary = tr("Replies, zaps and messages while Block is closed."),
        checked = push
    ) { on ->
        vm.setPushEnabled(on)
        if (on && Build.VERSION.SDK_INT >= 33) permission.launch("android.permission.POST_NOTIFICATIONS")
        // The first time: instant, or every refresh interval?
        if (on && InstantPush.notAsked(context)) askInstant = true
    }
    SettingsSwitchRow(
        tr("Instant notifications"),
        summary = if (instant) tr("Block stays connected to your relays, so notifications arrive within seconds. Uses a little more battery.")
            else tr("Off: Block checks every refresh interval (15–60 minutes)."),
        checked = push && instant,
        enabled = push
    ) { on -> chooseInstant(on) }
    if (push && instant && !unrestricted) {
        SettingsLinkRow(
            tr("Allow Block to stay connected"),
            summary = tr("Android's battery saver cuts the connection while the phone sleeps. Tap to let Block keep it.")
        ) { askBatteryExemption(context) }
    }
    if (push && instant) SettingsNote(
        tr("Android shows a small \"Instant notifications are on\" notification while connected; you can hide it in the system's notification settings. Some phones (Xiaomi, Samsung, Oppo, Vivo…) also close apps in the background: if notifications stop, allow Block to run in the background in the phone's battery settings.")
    )
    SettingsDivider()
    SettingsHeader(tr("Notify me about"))
    NOTIFICATION_TYPES.forEach { (type, label) ->
        SettingsSwitchRow(tr(label), checked = push && type !in off, enabled = push) { on -> vm.setNotificationType(type, on) }
    }
    SettingsNote(tr("These also apply to the Notifications tab's unread count."))

    if (askInstant) BlockDialog(
        onDismiss = { askInstant = false; chooseInstant(false) },
        title = tr("Get notifications instantly?"),
        text = tr("Block can stay connected to your relays so replies, zaps and messages arrive within seconds, like on X. It needs no server of anyone's: only your phone and your relays.") + "\n\n" +
            tr("It uses a little more battery, and Android shows a small notification while it's on. Otherwise Block checks every 15–60 minutes. You can change this any time here."),
        confirmLabel = tr("Instant"),
        onConfirm = { askInstant = false; chooseInstant(true) },
        dismissLabel = tr("Every 15–60 minutes")
    )
}

/** Asks Android to leave Block's connection alone while the phone sleeps (Doze). */
private fun askBatteryExemption(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    val ask = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + context.packageName))
    val fallback = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    runCatching { context.startActivity(ask) }.recoverCatching { context.startActivity(fallback) }
}
