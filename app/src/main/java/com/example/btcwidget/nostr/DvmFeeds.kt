package com.example.btcwidget.nostr

import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Filter

/** Feeds from content-discovery DVMs (NIP-90): the services on offer, and a feed from one. */
class DvmFeeds internal constructor(private val repo: NostrRepository) {
    /** Feed services (content-discovery DVMs) announced on relays. */
    suspend fun services(): List<com.example.btcwidget.nostr.core.Dvm.Service> {
        val D = com.example.btcwidget.nostr.core.Dvm
        val events = repo.queryEverywhere(listOf(Filter(kinds = listOf(D.ANNOUNCEMENT), tags = mapOf("k" to listOf(D.FEED_REQUEST.toString())), limit = 80)), timeoutMs = 6_000)
        return events.groupBy { it.pubkey + ":" + it.firstTag("d")?.get(1) }.values
            .mapNotNull { v -> D.service(v.maxBy { it.createdAt }) }
            .filter { it.pubkey !in repo.muted.value }
            .sortedBy { it.name.lowercase() }
    }

    class FeedUnavailable(message: String) : Exception(message)

    /**
     * Asks [service] for its feed and returns the notes. The request is signed with a
     * throwaway key, so the DVM doesn't learn who asked, unless the feed is personalized
     * and the user is logged in.
     */
    suspend fun feed(service: com.example.btcwidget.nostr.core.Dvm.Service): List<FeedItem> {
        val D = com.example.btcwidget.nostr.core.Dvm
        val useAccount = service.personalized && repo.pubkey != null
        val request = if (useAccount) {
            repo.requireSigner().sign(D.request(repo.pubkey!!, service.pubkey, repo.pool.urls.toList()))
        } else {
            val k = com.example.btcwidget.nostr.core.Schnorr.newSecretKey()
            val u = D.request(com.example.btcwidget.nostr.core.Schnorr.publicKey(k), service.pubkey, repo.pool.urls.toList())
            u.signed(com.example.btcwidget.nostr.core.Schnorr.sign(u.id, k))
        }
        val answer = kotlinx.coroutines.CompletableDeferred<Event>()
        val stop = repo.pool.subscribe(
            listOf(Filter(kinds = listOf(D.FEED_RESULT, D.FEEDBACK), tags = mapOf("e" to listOf(request.id)))),
            { e, _ ->
                if (e.pubkey != service.pubkey) return@subscribe
                if (e.kind == D.FEED_RESULT) answer.complete(e)
                else if (e.firstTag("status")?.get(1) in setOf("error", "payment-required")) answer.completeExceptionally(
                    FeedUnavailable(if (e.firstTag("status")?.get(1) == "payment-required") "This feed asks for payment" else e.firstTag("status")?.getOrNull(2)?.take(120) ?: "The feed reported an error")
                )
            }
        )
        try {
            if (repo.pool.publish(request).none { it.accepted }) throw FeedUnavailable("No relay took the request")
            val result = kotlinx.coroutines.withTimeoutOrNull(25_000) { answer.await() } ?: throw FeedUnavailable("The feed didn't answer in time")
            val ids = D.noteIds(result).take(100)
            if (ids.isEmpty()) return emptyList()
            val missing = ids.filter { !repo.notes.containsKey(it) }
            val fetched = if (missing.isEmpty()) emptyList() else repo.queryEverywhere(listOf(Filter(ids = missing)), timeoutMs = 6_000)
            val all = ids.mapNotNull { repo.notes[it] ?: fetched.firstOrNull { e -> e.id == it } }
            val order = ids.withIndex().associate { it.value to it.index }
            return repo.buildTimeline(all).sortedBy { order[it.note.id] ?: Int.MAX_VALUE }
        } finally {
            stop()
        }
    }
}
