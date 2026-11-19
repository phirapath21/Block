package com.example.btcwidget.ui.social

import com.example.btcwidget.ui.main.TextSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import com.example.btcwidget.ui.main.EInk

// ---- Privacy and safety ----

@Composable
internal fun PrivacyPage(vm: SocialViewModel, navigate: (NavKey) -> Unit) {
    val mutes by vm.mutes.collectAsState()
    val wot by vm.wotFilter.collectAsState()
    val trustSize by vm.trustSize.collectAsState()
    var input by remember { mutableStateOf("") }

    SettingsHeader(tr("Muted words and hashtags"))
    SettingsNote(tr("Posts containing these are hidden everywhere. Your mute list is public, like your follows."))
    val entries = mutes.words.sorted().map { "word" to it } + mutes.hashtags.sorted().map { "t" to it }
    entries.forEach { (type, value) ->
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (type == "t") "#$value" else value, color = EInk.Value, fontSize = TextSize.Body, modifier = Modifier.weight(1f))
            androidx.compose.foundation.layout.Box(
                Modifier.size(40.dp).clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Unmute {0}", value)) { vm.muteEntry(type, value, false) },
                contentAlignment = Alignment.Center
            ) { Icon(Glyphs.Close, contentDescription = null, tint = EInk.Caption, modifier = Modifier.size(20.dp)) }
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        SettingsTextField(input, { input = it.take(60) }, tr("word or #hashtag"), Modifier.weight(1f))
        Spacer(Modifier.size(8.dp))
        SettingsButton(tr("Mute"), primary = true, enabled = input.isNotBlank()) {
            val v = input.trim()
            if (v.startsWith("#")) vm.muteEntry("t", v.removePrefix("#"), true) else vm.muteEntry("word", v, true)
            input = ""
        }
    }
    if (mutes.threads.isNotEmpty()) {
        SettingsLinkRow(tr("Muted conversations"), value = mutes.threads.size.toString(), chevron = false) {}
        Row(Modifier.padding(horizontal = 16.dp)) { SettingsButton(tr("Unmute all")) { vm.unmuteAllThreads() } }
    }

    SettingsDivider()
    SettingsHeader(tr("Filters"))
    val wotSummary = buildString {
        append(tr("Hide replies, mentions and search results from accounts that nobody you follow follows. Cuts most spam; people new to Nostr may be hidden too."))
        if (wot && trustSize > 0) append("\n" + tr("Trusted network: {0} accounts", "%,d".format(trustSize)))
    }
    SettingsSwitchRow(
        tr("Web of trust"),
        summary = wotSummary,
        checked = wot
    ) { vm.setWotFilter(it) }
    SettingsNote(tr("Bookmarks and mutes are private: encrypted so only you can read them (relays see only that the list exists). They sync to your other Nostr apps."))

    SettingsDivider()
    SettingsLinkRow(tr("Community rules"), summary = tr("What isn't allowed; mute and report")) { navigate(com.example.btcwidget.RulesKey) }
}
