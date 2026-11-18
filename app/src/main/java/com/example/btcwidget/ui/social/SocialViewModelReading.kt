package com.example.btcwidget.ui.social

import androidx.lifecycle.viewModelScope
import com.example.btcwidget.nostr.FeedItem
import com.example.btcwidget.nostr.core.Profile
import kotlinx.coroutines.launch

// Reading notes, articles, feeds and profiles for the detail screens.
// Extensions on SocialViewModel, kept apart from the class: they use only what it shares.

/**
 * People who post about [tags] lately (for onboarding): authors of recent notes with
 * those hashtags, most active first. Real accounts from relays; nothing hand-picked.
 */
suspend fun SocialViewModel.topicPeople(tags: List<String>): List<Profile> = runCatching {
    val me = repo.pubkey
    // One quick query for recent notes with any of the topics (not a full hashtag search each).
    val ranked = repo.topicAuthors(tags.take(6)).filter { it != me && it !in repo.muted.value }.take(30)
    repo.loadProfiles(ranked)
    ranked.mapNotNull { repo.profiles.value[it] }.take(20)
}.getOrDefault(emptyList())

/** Well-known Bitcoin and Nostr people (each shown only if their profile matches who we expect). */
suspend fun SocialViewModel.starterPeople(): List<Profile> = runCatching {
    val known = com.example.btcwidget.nostr.core.StarterPeople.LIST
    repo.loadProfiles(known.map { it.pubkey })
    val profiles = repo.profiles.value
    known.mapNotNull { k -> profiles[k.pubkey]?.takeIf { p -> k.matches(p) } }
}.getOrDefault(emptyList())

suspend fun SocialViewModel.feedServices() = runCatching { repo.dvm.services() }.getOrDefault(emptyList())

/** The feed, or the reason it couldn't load. */
suspend fun SocialViewModel.dvmFeed(service: com.example.btcwidget.nostr.core.Dvm.Service): Result<List<FeedItem>> = runCatching { repo.dvm.feed(service) }

suspend fun SocialViewModel.followSuggestions() = runCatching { repo.followSuggestions() }.getOrDefault(emptyList())

suspend fun SocialViewModel.badgesOf(pubkey: String) = runCatching { repo.badges.of(pubkey) }.getOrDefault(emptyList())

/** Publishes a long-form article (NIP-23 kind 30023); [onResult] gets the published Article or null. */
fun SocialViewModel.publishArticle(unsigned: com.example.btcwidget.nostr.core.UnsignedEvent, onResult: (com.example.btcwidget.nostr.core.Article?) -> Unit) {
    var result: com.example.btcwidget.nostr.core.Article? = null
    act(tr("Couldn't publish the article"), finally = { onResult(result) }) {
        result = repo.articles.publish(unsigned)
        say(tr("Article published"))
    }
}

suspend fun SocialViewModel.thread(id: String) = runCatching { repo.thread(id) }.getOrNull()

suspend fun SocialViewModel.note(id: String, relays: List<String> = emptyList(), author: String? = null, via: String? = null) =
    runCatching { repo.note(id, relays, author, via) }.getOrNull()

fun SocialViewModel.hashtagSuggestions(prefix: String) = repo.hashtagSuggestions(prefix)

suspend fun SocialViewModel.articlesBy(pubkey: String) = runCatching { repo.articles.byAuthor(pubkey) }.getOrDefault(emptyList())

/** Video posts for Shorts: by the people you follow, or by everyone on your relays. */
suspend fun SocialViewModel.videoFeed(everyone: Boolean, until: Long? = null) =
    runCatching { repo.mediaFeed(com.example.btcwidget.nostr.core.HomeFeed.Media(videos = true, everyone = everyone), until) }.getOrDefault(emptyList())

suspend fun SocialViewModel.profileFeed(pubkey: String, until: Long? = null) = runCatching { repo.profileFeed(pubkey, until) }.getOrDefault(emptyList())

suspend fun SocialViewModel.hashtagFeed(tag: String) = runCatching { repo.hashtagFeed(tag) }.getOrDefault(emptyList())

suspend fun SocialViewModel.trendingHashtags() = runCatching { repo.trendingHashtags() }.getOrDefault(emptyList())

suspend fun SocialViewModel.refreshProfile(pubkey: String) = runCatching { repo.refreshProfile(pubkey) }.getOrNull()

suspend fun SocialViewModel.followingOf(pubkey: String) = runCatching { repo.followingOf(pubkey) }.getOrDefault(emptySet())

fun SocialViewModel.loadProfiles(pubkeys: Collection<String>) = viewModelScope.launch { runCatching { repo.loadProfiles(pubkeys) } }

suspend fun SocialViewModel.linkPreview(url: String) = repo.linkPreviews.get(url)

suspend fun SocialViewModel.engagement(noteId: String) = runCatching { repo.engagement(noteId) }.getOrNull()

suspend fun SocialViewModel.followersOf(pubkey: String) = runCatching { repo.followersOf(pubkey) }.getOrDefault(emptyList())

suspend fun SocialViewModel.globalFeed(relay: String?, until: Long? = null) = runCatching { repo.globalFeed(relay, until) }.getOrDefault(emptyList())

suspend fun SocialViewModel.pollTally(poll: com.example.btcwidget.nostr.core.Poll) = runCatching { repo.polls.tally(poll) }.getOrNull()

suspend fun SocialViewModel.pinnedNoteOf(pubkey: String) = runCatching { repo.pinnedNoteOf(pubkey) }.getOrNull()

suspend fun SocialViewModel.bookmarkedNotes() = runCatching { repo.bookmarkedNotes() }.getOrDefault(emptyList())
