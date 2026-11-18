package com.example.btcwidget.ui.social

// Communities (NIP-72), public chats (NIP-28), relay groups (NIP-29) and live streams.
// Extensions on SocialViewModel, kept apart from the class: they use only what it shares.

val SocialViewModel.spaces get() = repo.spaces

suspend fun SocialViewModel.communities() = runCatching { repo.spaces.communities() }.getOrDefault(emptyList())

suspend fun SocialViewModel.community(coordinate: String) = runCatching { repo.spaces.community(coordinate) }.getOrNull()

suspend fun SocialViewModel.communityPosts(c: com.example.btcwidget.nostr.core.Communities.Community, includePending: Boolean, until: Long? = null) =
    runCatching { repo.spaces.posts(c, includePending, until) }.getOrDefault(emptyList())

fun SocialViewModel.postToCommunity(c: com.example.btcwidget.nostr.core.Communities.Community, text: String, onResult: (Boolean) -> Unit) {
    var ok = false
    act(tr("Couldn't post"), finally = { onResult(ok) }) { repo.spaces.post(c, text); ok = true; say(tr("Posted. It shows here once a moderator approves it.")) }
}

fun SocialViewModel.setCommunityJoined(c: com.example.btcwidget.nostr.core.Communities.Community, join: Boolean) =
    act(if (join) tr("Couldn't join") else tr("Couldn't leave")) { repo.spaces.setJoined(c, join) }

suspend fun SocialViewModel.channels() = runCatching { repo.spaces.channels() }.getOrDefault(emptyList())

suspend fun SocialViewModel.channel(id: String) = runCatching { repo.spaces.channel(id) }.getOrNull()

fun SocialViewModel.sendToChannel(channel: com.example.btcwidget.nostr.core.PublicChat.Channel, text: String, onResult: (Boolean) -> Unit) {
    var ok = false
    act(tr("Couldn't send"), finally = { onResult(ok) }) { repo.spaces.sendToChannel(channel, text); ok = true }
}

suspend fun SocialViewModel.liveStreams() = runCatching { repo.spaces.liveStreams() }.getOrDefault(emptyList())

suspend fun SocialViewModel.liveStream(address: String) = runCatching { repo.spaces.liveStream(address) }.getOrNull()

fun SocialViewModel.sendLiveChat(live: com.example.btcwidget.nostr.core.LiveStreams.Live, text: String, onResult: (Boolean) -> Unit) {
    var ok = false
    act(tr("Couldn't send"), finally = { onResult(ok) }) { repo.spaces.sendLiveChat(live, text); ok = true }
}

suspend fun SocialViewModel.group(address: com.example.btcwidget.nostr.core.RelayGroups.Address) = runCatching { repo.spaces.group(address) }.getOrNull()

/** [onResult] gets null when sent, or the relay's reason. */
fun SocialViewModel.sendToGroup(address: com.example.btcwidget.nostr.core.RelayGroups.Address, text: String, onResult: (String?) -> Unit) {
    var error: String? = "Couldn't send"
    act(tr("Couldn't send"), finally = { onResult(error) }) { error = repo.spaces.sendToGroup(address, text) }
}

fun SocialViewModel.joinGroup(address: com.example.btcwidget.nostr.core.RelayGroups.Address) =
    act(tr("Couldn't ask to join")) { val e = repo.spaces.joinGroup(address); say(e ?: tr("Asked to join")) }
