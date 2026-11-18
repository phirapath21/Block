package com.example.btcwidget.nostr

import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Filter
import com.example.btcwidget.nostr.core.Kind

/** Highlights (NIP-84): passages people marked in long-form articles. */
class ArticleHighlights internal constructor(private val repo: NostrRepository) {
    /** Highlights of [article] by anyone, newest first (muted people's left out). */
    suspend fun of(article: com.example.btcwidget.nostr.core.Article): List<com.example.btcwidget.nostr.core.Highlights.Highlight> {
        val address = "${Kind.ARTICLE}:${article.pubkey}:${article.identifier}"
        val events = repo.queryEverywhere(listOf(Filter(kinds = listOf(Kind.HIGHLIGHT), tags = mapOf("a" to listOf(address)), limit = 100)), timeoutMs = 5_000)
        return events.filter { !repo.hidden(it) }.sortedByDescending { it.createdAt }
            .mapNotNull { com.example.btcwidget.nostr.core.Highlights.parse(it) }
            .also { list -> repo.loadProfilesLater(list.map { it.event.pubkey }) }
    }

    suspend fun add(article: com.example.btcwidget.nostr.core.Article, text: String, comment: String?): Event =
        repo.publish(com.example.btcwidget.nostr.core.Highlights.of(repo.requireMe(), text, article, comment, repo.relayHint()))
}
