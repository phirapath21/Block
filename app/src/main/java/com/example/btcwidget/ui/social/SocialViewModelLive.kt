package com.example.btcwidget.ui.social

// Live streams (NIP-53).
// Extensions on SocialViewModel, kept apart from the class: they use only what it shares.

val SocialViewModel.live get() = repo.live

suspend fun SocialViewModel.liveStreams() = runCatching { repo.live.liveStreams() }.getOrDefault(emptyList())

suspend fun SocialViewModel.liveStream(address: String) = runCatching { repo.live.liveStream(address) }.getOrNull()

fun SocialViewModel.sendLiveChat(live: com.example.btcwidget.nostr.core.LiveStreams.Live, text: String, onResult: (Boolean) -> Unit) {
    var ok = false
    act(tr("Couldn't send"), finally = { onResult(ok) }) { repo.live.sendLiveChat(live, text); ok = true }
}
