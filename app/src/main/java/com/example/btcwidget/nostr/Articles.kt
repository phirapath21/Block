package com.example.btcwidget.nostr

import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Filter
import com.example.btcwidget.nostr.core.Kind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Long-form articles (NIP-23): one article, an author's, search, recent ones, and publishing. */
class Articles internal constructor(private val repo: NostrRepository) {
    /** The latest version of the article at [address]. */
    suspend fun get(address: com.example.btcwidget.nostr.core.Nip19.Address): com.example.btcwidget.nostr.core.Article? {
        val events = repo.authoredEvents(listOf(address.pubkey), address.relays) {
            Filter(authors = it, kinds = listOf(address.kind), tags = mapOf("d" to listOf(address.identifier)), limit = 3)
        }
        val latest = events.filter { it.pubkey == address.pubkey && it.firstTag("d")?.get(1).orEmpty() == address.identifier }
            .maxByOrNull { it.createdAt } ?: return null
        repo.enrich(listOf(latest.pubkey), listOf(latest.id))
        return com.example.btcwidget.nostr.core.Article.of(latest)
    }

    /** Articles [pubkey] has written, newest first (one per identifier). */
    suspend fun byAuthor(pubkey: String): List<com.example.btcwidget.nostr.core.Article> =
        repo.authoredEvents(listOf(pubkey)) { Filter(authors = it, kinds = listOf(Kind.ARTICLE), limit = 30) }
            .filter { it.pubkey == pubkey }
            .groupBy { it.firstTag("d")?.get(1).orEmpty() }
            .mapNotNull { (_, versions) -> com.example.btcwidget.nostr.core.Article.of(versions.maxBy { it.createdAt }) }
            .sortedByDescending { it.publishedAt }

    /** Publishes a long-form article (NIP-23 kind 30023) and stores it locally. */
    suspend fun publish(unsigned: com.example.btcwidget.nostr.core.UnsignedEvent): com.example.btcwidget.nostr.core.Article? {
        val signed = repo.publish(unsigned)
        repo.store(signed)
        return com.example.btcwidget.nostr.core.Article.of(signed)
    }

    /** Long-form articles matching [query] (NIP-50 on kind 30023 + local SQLite cache), newest version of each. */
    suspend fun search(query: String): List<com.example.btcwidget.nostr.core.Article> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val found = coroutineScope {
            val remote = async { runCatching { repo.searchPool.query(listOf(Filter(kinds = listOf(Kind.ARTICLE), search = q, limit = 40)), 6_000) }.getOrDefault(emptyList()) }
            val local = async(Dispatchers.IO) { repo.events.search(q, kinds = listOf(Kind.ARTICLE), limit = 40) }
            (remote.await() + local.await()).distinctBy { it.id }
        }
        return fromEvents(found)
    }

    /** Recent articles: from the people you follow when [following], else from everyone on your relays. */
    suspend fun recent(following: Boolean, until: Long? = null): List<com.example.btcwidget.nostr.core.Article> {
        val authors = if (following) repo.homeAuthors().takeIf { it.isNotEmpty() } else null
        val events = if (authors != null) repo.authoredEvents(authors) { Filter(authors = it, kinds = listOf(Kind.ARTICLE), until = until, limit = 40) }
            else repo.queryEverywhere(listOf(Filter(kinds = listOf(Kind.ARTICLE), until = until, limit = 40)), timeoutMs = 6_000)
        return fromEvents(events)
    }

    private suspend fun fromEvents(events: List<Event>): List<com.example.btcwidget.nostr.core.Article> {
        val list = events.filter { it.kind == Kind.ARTICLE && !repo.hidden(it) && repo.trusted(it.pubkey) }
            .groupBy { it.pubkey + ":" + it.firstTag("d")?.get(1).orEmpty() }
            .mapNotNull { (_, v) -> com.example.btcwidget.nostr.core.Article.of(v.maxBy { it.createdAt }) }
            .filter { it.content.isNotBlank() }
            .sortedByDescending { it.publishedAt }
        repo.loadProfiles(list.map { it.pubkey })
        return list
    }
}
