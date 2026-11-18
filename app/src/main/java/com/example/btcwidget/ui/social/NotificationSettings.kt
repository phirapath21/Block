package com.example.btcwidget.ui.social

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

// ---- Notifications ----

private val NOTIFICATION_TYPES = listOf(
    "reply" to "Replies", "mention" to "Mentions", "repost" to "Reposts",
    "like" to "Likes", "zap" to "Zaps", "dm" to "Messages"
)

@Composable
internal fun NotificationsPage(vm: SocialViewModel) {
    val push by vm.pushEnabled.collectAsState()
    val off by vm.notificationsOff.collectAsState()
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) {}
    SettingsSwitchRow(
        tr("Phone notifications"),
        summary = tr("Replies, zaps and messages while Block is closed, checked every refresh interval."),
        checked = push
    ) { on ->
        vm.setPushEnabled(on)
        if (on && Build.VERSION.SDK_INT >= 33) permission.launch("android.permission.POST_NOTIFICATIONS")
    }
    SettingsDivider()
    SettingsHeader(tr("Notify me about"))
    NOTIFICATION_TYPES.forEach { (type, label) ->
        SettingsSwitchRow(tr(label), checked = push && type !in off, enabled = push) { on -> vm.setNotificationType(type, on) }
    }
    SettingsNote(tr("These also apply to the Notifications tab's unread count."))
}
