package com.example.btcwidget.ui.social

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

// ---- Display and language ----

@Composable
internal fun DisplayPage(vm: SocialViewModel) {
    val context = LocalContext.current
    val inApp by vm.linksInApp.collectAsState()
    val autoplay by vm.videoAutoplay.collectAsState()
    val quality by vm.videoQuality.collectAsState()

    SettingsHeader(tr("Theme"))
    SettingsNote(tr("Colours for Block's screens and menus. Widgets keep their own theme (Bitcoin and widgets)."))
    LookPicker()

    SettingsDivider()
    SettingsHeader(tr("Language"))
    SettingsNote(tr("Text in menus, buttons and messages. Posts stay in the language they were written in."))
    Lang.Choice.entries.forEach { c ->
        SettingsRadioRow(if (c == Lang.Choice.SYSTEM) tr(c.label) else c.label, selected = Lang.choice == c) { Lang.choose(context, c) }
    }

    SettingsDivider()
    BitcoinCardsSettings()

    SettingsDivider()
    SettingsHeader(tr("Open links in"))
    SettingsRadioRow(tr("Block's browser"), summary = tr("The post stays under the page, so you can like or reply while reading."), selected = inApp) { vm.setLinksInApp(true) }
    SettingsRadioRow(tr("Phone's browser"), summary = tr("Has your logins and saved passwords."), selected = !inApp) { vm.setLinksInApp(false) }

    SettingsDivider()
    SettingsHeader(tr("Video autoplay"))
    SettingsNote(tr("Videos in the timeline can start by themselves, muted. Autoplay uses data and battery."))
    VideoAutoplay.entries.forEach { m -> SettingsRadioRow(tr(m.label), selected = autoplay == m) { vm.setVideoAutoplay(m) } }

    SettingsDivider()
    SettingsHeader(tr("Video quality"))
    SettingsNote(tr("Quality: videos that come in several resolutions start at this. Most Nostr videos are a single file and always play as they are."))
    VideoQuality.entries.forEach { q -> SettingsRadioRow(tr(q.label), selected = quality == q) { vm.setVideoQuality(q) } }
}
