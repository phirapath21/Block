package com.example.btcwidget.ui.social

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Your relays and media servers: checking, tidying and saving the lists, and relay status.
// Extensions on SocialViewModel, kept apart from the class: they use only what it shares.

fun SocialViewModel.mediaServers(): List<String> = repo.mediaServerUrls()

fun SocialViewModel.setMediaServers(urls: List<String>, onDone: () -> Unit) =
    act(tr("Couldn't save media servers")) { repo.setMediaServers(urls); say(tr("Media servers saved")); onDone() }

fun SocialViewModel.storageStats(onResult: (com.example.btcwidget.nostr.StorageStats) -> Unit) {
    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        val stats = repo.storageStats()
        withContext(kotlinx.coroutines.Dispatchers.Main) { onResult(stats) }
    }
}

fun SocialViewModel.eventRetentionDays(): Int = repo.eventRetentionDays()

fun SocialViewModel.setEventRetentionDays(days: Int) {
    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { repo.setEventRetentionDays(days) }
}

fun SocialViewModel.setRelays(urls: List<String>?) {
    repo.setRelays(urls)
    refreshHome()
}

fun SocialViewModel.myRelayUrls() = repo.myRelayUrls()

suspend fun SocialViewModel.probeRelays(urls: List<String>) = runCatching { repo.relayChecks.probe(urls) }.getOrDefault(emptyList())

suspend fun SocialViewModel.suggestedRelays() = runCatching { repo.suggestedRelays() }.getOrDefault(emptyList())

fun SocialViewModel.removeRelays(urls: Collection<String>) =
    tracked(tr("Removing relays"), tr("Relays removed"), tr("Couldn't update your relays")) { repo.removeRelays(urls) }

/** [pubkey]'s published relays (NIP-65), for their profile. */
suspend fun SocialViewModel.relayListOf(pubkey: String) = runCatching { repo.relayListOf(pubkey) }.getOrDefault(emptyList())

/**
 * Adds [url] to the user's relay list (NIP-65, read and write) and publishes it. With no
 * list published yet, it starts from the relays Block uses now, so none are dropped.
 */
fun SocialViewModel.addRelayToMine(url: String) {
    val current = repo.relayEntries.value.ifEmpty {
        repo.currentRelayUrls().map { com.example.btcwidget.nostr.core.RelayLists.Entry(it, read = true, write = true) }
    }
    // Relays set by hand in Settings are what Block connects to: the new one goes there too.
    repo.addToLocalRelays(url)
    if (current.any { it.url.trimEnd('/') == url.trimEnd('/') }) { say(tr("Already in your relays")); return }
    saveRelayList(current + com.example.btcwidget.nostr.core.RelayLists.Entry(url, read = true, write = true))
}

val SocialViewModel.relays: List<String> get() = repo.pool.urls.toList().sorted()

val SocialViewModel.relayEntries get() = repo.relayEntries

val SocialViewModel.searchRelays get() = repo.searchRelays

val SocialViewModel.blockedRelays get() = repo.blockedRelays

suspend fun SocialViewModel.relayInfo(url: String) = repo.relayChecks.info(url)

fun SocialViewModel.saveRelayList(entries: List<com.example.btcwidget.nostr.core.RelayLists.Entry>) =
    tracked(tr("Saving your relays"), tr("Relays saved"), tr("Couldn't save your relays")) { repo.saveRelayList(entries) }

fun SocialViewModel.saveSearchRelays(urls: List<String>) =
    tracked(tr("Saving search relays"), tr("Search relays saved"), tr("Couldn't save search relays")) { repo.saveSearchRelays(urls) }

fun SocialViewModel.saveBlockedRelays(urls: List<String>) =
    tracked(tr("Saving blocked relays"), tr("Blocked relays saved"), tr("Couldn't save blocked relays")) { repo.saveBlockedRelays(urls) }

fun SocialViewModel.relayStatus() = repo.pool.status()

fun SocialViewModel.retryRelay(url: String) = repo.pool.retry(url)
