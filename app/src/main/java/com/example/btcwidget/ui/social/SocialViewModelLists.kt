package com.example.btcwidget.ui.social

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Lists of people (NIP-51) and follow packs.
// Extensions on SocialViewModel, kept apart from the class: they use only what it shares.

suspend fun SocialViewModel.followPacks() = runCatching { repo.followPacks() }.getOrDefault(emptyList())

suspend fun SocialViewModel.followPack(address: String) = runCatching { repo.followPack(address) }.getOrNull()

fun SocialViewModel.followAll(pubkeys: Collection<String>) = act(tr("Couldn't follow them")) {
    repo.followAll(pubkeys)
    say(tr("Following {0} people", pubkeys.size))
}

val SocialViewModel.followedTags get() = repo.followedTags

fun SocialViewModel.followHashtag(tag: String, follow: Boolean) =
    act(if (follow) tr("Couldn't follow #{0}", tag) else tr("Couldn't unfollow #{0}", tag)) { repo.setHashtagFollowed(tag, follow) }

val SocialViewModel.lists get() = repo.peopleLists.lists

fun SocialViewModel.loadLists() { viewModelScope.launch { repo.pubkey?.let { runCatching { repo.peopleLists.of(it) } } } }

suspend fun SocialViewModel.listsBy(owner: String) = runCatching { repo.peopleLists.of(owner) }.getOrDefault(emptyList())

suspend fun SocialViewModel.peopleList(owner: String, d: String) = runCatching { repo.peopleLists.get(owner, d) }.getOrNull()

suspend fun SocialViewModel.listFeed(list: com.example.btcwidget.nostr.core.PeopleList) =
    runCatching { repo.feed(com.example.btcwidget.nostr.core.HomeFeed.People(list.owner, list.d, list.title)) }.getOrDefault(emptyList())

/** Makes a new list, optionally with someone in it already; [onDone] gets it once saved. */
fun SocialViewModel.createList(title: String, firstMember: String? = null, onDone: (com.example.btcwidget.nostr.core.PeopleList) -> Unit = {}) {
    val me = repo.pubkey ?: return
    val name = title.trim().ifBlank { return }
    act(tr("Couldn't create the list")) {
        var list = com.example.btcwidget.nostr.core.PeopleList.create(me, name)
        if (firstMember != null) list = list.with(firstMember)
        val saved = repo.peopleLists.update(list)
        say(tr("List created"))
        onDone(saved)
    }
}

fun SocialViewModel.setInList(list: com.example.btcwidget.nostr.core.PeopleList, pubkey: String, inList: Boolean) {
    act(tr("Couldn't update the list")) {
        repo.peopleLists.update(list) { if (inList) it.with(pubkey) else it.without(pubkey) }
        say(if (inList) tr("Added to {0}", list.title) else tr("Removed from {0}", list.title))
    }
}

/** Adds the list to Home's feed menu and switches to it. */
fun SocialViewModel.showListOnHome(list: com.example.btcwidget.nostr.core.PeopleList) =
    addFeed(com.example.btcwidget.nostr.core.HomeFeed.People(list.owner, list.d, list.title))
