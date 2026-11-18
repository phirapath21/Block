package com.example.btcwidget.ui.social

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

// ---- Local storage ----

@Composable
internal fun StoragePage(vm: SocialViewModel) {
    var stats by remember { mutableStateOf<com.example.btcwidget.nostr.StorageStats?>(null) }
    var retention by remember { mutableStateOf(vm.eventRetentionDays()) }
    var message by remember { mutableStateOf<String?>(null) }
    fun refresh() = vm.storageStats { stats = it }
    LaunchedEffect(Unit) { refresh() }

    fun size(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    SettingsHeader(tr("Event database"))
    SettingsNote(
        stats?.let { tr("{0} events · {1}", it.eventCount, size(it.databaseBytes)) }
            ?: tr("Reading database size…")
    )

    SettingsDivider()
    SettingsHeader(tr("Automatically remove old events"))
    SettingsNote(tr("Keeps your own posts and bookmarked events. Cleanup runs when Block opens."))
    listOf(0 to "Off", 30 to "30 days", 90 to "90 days").forEach { (days, label) ->
        SettingsRadioRow(tr(label), selected = retention == days) {
            retention = days
            vm.setEventRetentionDays(days)
            message = tr("Retention setting saved")
            refresh()
        }
    }

    SettingsDivider()
    SettingsHeader(tr("Image and video cache"))
    SettingsNote(
        stats?.let { tr("Using {0}", size(it.mediaCacheBytes)) }
            ?: tr("Reading cache size…")
    )
    SettingsButton(tr("Clear media cache"), destructive = true) {
        message = tr("Clearing media cache…")
        vm.clearMediaCache { cleared ->
            message = if (cleared) tr("Media cache cleared") else tr("Some media cache files are in use")
            refresh()
        }
    }
    message?.let { SettingsNote(it) }
}
