package com.example.btcwidget.nostr

import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Filter
import com.example.btcwidget.nostr.core.Kind
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Polls (NIP-88): counting the votes, voting, and asking a question of your own. */
class Polls internal constructor(private val repo: NostrRepository) {
    /** Votes on [poll], from the relays it names and ours. */
    suspend fun tally(poll: com.example.btcwidget.nostr.core.Poll): com.example.btcwidget.nostr.core.Poll.Tally {
        val filter = Filter(kinds = listOf(Kind.POLL_RESPONSE), tags = mapOf("e" to listOf(poll.event.id)), limit = 500)
        val responses = coroutineScope {
            val ours = async { repo.pool.query(listOf(filter), 5_000, graceMs = 700) }
            val theirs = async {
                val urls = poll.relays.filter { it !in repo.pool.urls }.take(4).toSet()
                if (urls.isEmpty()) emptyList() else runCatching {
                    repo.outbox.addRelays(urls)
                    repo.outbox.query(listOf(filter), timeoutMs = 5_000, only = urls)
                }.getOrDefault(emptyList())
            }
            (ours.await() + theirs.await()).distinctBy { it.id }.filter { it.verify() }
        }
        return poll.tally(responses, repo.pubkey)
    }

    /** Votes for [optionIds]; sent to our relays and the ones the poll reads from. */
    suspend fun vote(poll: com.example.btcwidget.nostr.core.Poll, optionIds: List<String>) {
        val signed = repo.requireSigner().sign(com.example.btcwidget.nostr.core.Poll.response(repo.requireMe(), poll, optionIds))
        val extra = poll.relays.filter { it !in repo.pool.urls }.take(4).toSet()
        val results = coroutineScope {
            val ours = async { repo.pool.publish(signed) }
            val theirs = async {
                if (extra.isEmpty()) emptyList() else runCatching { repo.outbox.addRelays(extra); repo.outbox.publish(signed, only = extra) }.getOrDefault(emptyList())
            }
            ours.await() + theirs.await()
        }
        if (results.none { it.accepted }) throw IllegalStateException("No relay accepted the vote")
    }

    /** Publishes a poll; votes are collected on our relays. */
    suspend fun create(question: String, options: List<String>, multipleChoice: Boolean, endsAt: Long?): Event =
        repo.publish(com.example.btcwidget.nostr.core.Poll.create(repo.requireMe(), question, options, multipleChoice, endsAt, repo.pool.urls.take(4).toList()))
}
