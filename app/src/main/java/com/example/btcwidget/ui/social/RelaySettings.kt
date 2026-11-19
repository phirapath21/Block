package com.example.btcwidget.ui.social

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey

// ---- Relays and servers ----

@Composable
internal fun RelaysPage(vm: SocialViewModel, navigate: (NavKey) -> Unit) {
    val account by vm.account.collectAsState()
    val inbox by vm.dmInbox.collectAsState()
    val status = remember { vm.relayStatus().filter { it.primary } }
    SettingsLinkRow(
        tr("Relay status and lists"),
        summary = tr("Your relay lists (read and write, search, blocked) and how each relay is doing."),
        value = tr("{0} of {1} connected", status.count { it.state == com.example.btcwidget.nostr.RelayStatus.State.CONNECTED }, status.size)
    ) { navigate(com.example.btcwidget.RelaysKey) }

    SettingsDivider()
    SettingsHeader(tr("Relays Block connects to"))
    SettingsNote(tr("One wss:// address per line."))
    SettingsListEditor(
        initial = vm.repo.pool.urls.toList(),
        placeholder = "wss://relay.example.com",
        valid = { it.startsWith("wss://") || it.startsWith("ws://") },
        extraAction = { SettingsButton(tr("Use defaults")) { vm.setRelays(null) } }
    ) { urls, done -> vm.setRelays(urls); done() }

    val acct = account ?: return // Logged out: only the relays above (storage follows on the Advanced page).
    SettingsDivider()
    SettingsHeader(tr("Message inbox relays"))
    SettingsNote(tr("Where people send you private messages (your kind 10050 list, public)."))
    SettingsListEditor(
        initial = inbox.orEmpty(),
        placeholder = "wss://relay.example.com",
        valid = { it.startsWith("wss://") }
    ) { urls, done -> if (urls.isNotEmpty()) vm.setDmInbox(urls, done) else done() }

    SettingsDivider()
    SettingsHeader(tr("Media servers"))
    SettingsNote(tr("Blossom servers your photos and videos upload to, in order (your kind 10063 list). Block tries blossom.primal.net last if these fail."))
    SettingsListEditor(
        initial = remember(acct.pubkey) { vm.mediaServers() },
        placeholder = "https://blossom.example.com",
        valid = { it.startsWith("https://") }
    ) { urls, done -> vm.setMediaServers(urls, done) }
}
