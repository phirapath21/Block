package com.example.btcwidget.nostr

import com.example.btcwidget.nostr.core.Filter
import com.example.btcwidget.nostr.core.Kind
import com.example.btcwidget.nostr.core.UnsignedEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Things for sale (NIP-99 listings): the Store's list, one listing, and publishing or deleting your own. */
class MarketListings internal constructor(private val repo: NostrRepository) {
    /**
     * Listings for sale, newest first: by [sellers] when given, else from everyone on your relays
     * and the indexers. Muted and untrusted sellers are left out; what's already stored on the
     * phone shows too, so the store fills in before the relays answer.
     */
    suspend fun latest(sellers: List<String>? = null, until: Long? = null): List<com.example.btcwidget.nostr.core.Listing> {
        val kinds = com.example.btcwidget.nostr.core.Marketplace.KINDS
        val local = withContext(Dispatchers.IO) {
            runCatching { repo.events.query(listOf(Filter(authors = sellers, kinds = kinds, until = until, limit = 300))) }.getOrDefault(emptyList())
        }
        val remote = runCatching {
            if (sellers != null) repo.authoredEvents(sellers) { Filter(authors = it, kinds = kinds, until = until, limit = 200) }
            else repo.queryEverywhere(listOf(Filter(kinds = kinds, until = until, limit = 200)), timeoutMs = 6_000)
        }.getOrDefault(emptyList())
        remote.forEach { repo.remember(it) }
        val list = com.example.btcwidget.nostr.core.Marketplace.latest(local + remote)
            .filter { !repo.hidden(it.event) && (sellers != null || repo.trusted(it.pubkey)) }
            .sortedByDescending { it.publishedAt }
        repo.loadProfiles(list.map { it.pubkey }.distinct())
        return list
    }

    /** The newest version of the listing at [address] ("kind:pubkey:d"). */
    suspend fun listing(address: String): com.example.btcwidget.nostr.core.Listing? {
        val parts = address.split(':', limit = 3)
        val kind = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val pubkey = parts.getOrNull(1) ?: return null
        val d = parts.getOrNull(2).orEmpty()
        val filter = { authors: List<String> -> Filter(authors = authors, kinds = listOf(kind), tags = mapOf("d" to listOf(d)), limit = 5) }
        val local = withContext(Dispatchers.IO) { runCatching { repo.events.query(listOf(filter(listOf(pubkey)))) }.getOrDefault(emptyList()) }
        val remote = runCatching { repo.authoredEvents(listOf(pubkey)) { filter(it) } }.getOrDefault(emptyList())
        remote.forEach { repo.remember(it) }
        val latest = (local + remote).filter { it.pubkey == pubkey && it.kind == kind && it.firstTag("d")?.get(1).orEmpty() == d }
            .maxByOrNull { it.createdAt } ?: return null
        repo.loadProfiles(listOf(pubkey))
        return com.example.btcwidget.nostr.core.Marketplace.parse(latest)
    }

    /** Signs and publishes a NIP-99 listing (new or edited, by its d tag). */
    suspend fun publish(unsigned: UnsignedEvent): com.example.btcwidget.nostr.core.Listing? =
        com.example.btcwidget.nostr.core.Marketplace.parse(repo.publish(unsigned))

    /** Asks relays to delete the listing (NIP-09 by address and id). */
    suspend fun delete(listing: com.example.btcwidget.nostr.core.Listing) {
        val me = repo.pubkey ?: throw IllegalStateException("Log in to Nostr first")
        repo.publish(UnsignedEvent(me, System.currentTimeMillis() / 1000, Kind.DELETION,
            listOf(listOf("e", listing.event.id), listOf("a", listing.address), listOf("k", listing.kind.toString())), ""))
    }
}
