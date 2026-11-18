package com.example.btcwidget.nostr

import com.example.btcwidget.nostr.core.Filter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.withLock

/** Lists of people (NIP-51 kind 30000): anyone's, and the user's own, edited one change at a time. */
class PeopleLists internal constructor(private val repo: NostrRepository) {
    private val _lists = MutableStateFlow<List<com.example.btcwidget.nostr.core.PeopleList>?>(null)
    /** The user's lists of people (NIP-51 kind 30000); null until loaded. */
    val lists: StateFlow<List<com.example.btcwidget.nostr.core.PeopleList>?> = _lists.asStateFlow()

    /** [owner]'s lists, newest version of each; the user's own are kept in [lists]. */
    suspend fun of(owner: String): List<com.example.btcwidget.nostr.core.PeopleList> {
        val events = repo.queryEverywhere(listOf(Filter(authors = listOf(owner), kinds = listOf(com.example.btcwidget.nostr.core.PeopleList.KIND), limit = 100)), timeoutMs = 5_000)
        val found = com.example.btcwidget.nostr.core.PeopleList.latest(events)
        if (owner == repo.pubkey) {
            // Keep edits made while this was loading (they're newer than what relays sent).
            val local = _lists.value.orEmpty().associateBy { it.d }
            val merged = (found.map { f -> local[f.d]?.takeIf { it.createdAt > f.createdAt } ?: f } + local.values.filter { l -> found.none { it.d == l.d } && l.createdAt > 0 })
                .filter { it.title.isNotBlank() }.sortedBy { it.title.lowercase() }
            _lists.value = merged
            return merged
        }
        return found
    }

    /** One list: the user's own from memory when loaded, else from relays. */
    suspend fun get(owner: String, d: String): com.example.btcwidget.nostr.core.PeopleList? {
        if (owner == repo.pubkey) _lists.value?.firstOrNull { it.d == d }?.let { return it }
        val events = repo.queryEverywhere(listOf(Filter(authors = listOf(owner), kinds = listOf(com.example.btcwidget.nostr.core.PeopleList.KIND), tags = mapOf("d" to listOf(d)))), timeoutMs = 5_000)
        return com.example.btcwidget.nostr.core.PeopleList.latest(events).firstOrNull()
    }

    private val listLock = kotlinx.coroutines.sync.Mutex()

    /**
     * Changes one of the user's lists (or saves a new one): shown at once, then published.
     * Edits go out one at a time, each built on the latest version, so quick taps on several
     * people don't overwrite each other. [change] must be safe to apply twice (add, remove, rename).
     */
    suspend fun update(
        list: com.example.btcwidget.nostr.core.PeopleList,
        change: (com.example.btcwidget.nostr.core.PeopleList) -> com.example.btcwidget.nostr.core.PeopleList = { it }
    ): com.example.btcwidget.nostr.core.PeopleList {
        val me = repo.requireMe()
        require(list.owner == me) { "Not your list" }
        fun put(l: com.example.btcwidget.nostr.core.PeopleList) {
            _lists.value = (_lists.value.orEmpty().filter { it.d != l.d } + l).sortedBy { it.title.lowercase() }
        }
        put(change(_lists.value?.firstOrNull { it.d == list.d } ?: list))
        return listLock.withLock {
            val next = change(_lists.value?.firstOrNull { it.d == list.d } ?: list)
            try {
                val signed = repo.publish(next.toEvent(System.currentTimeMillis() / 1000), fast = true)
                // Keep later edits already on screen; record what's now on the relays.
                val latest = _lists.value?.firstOrNull { it.d == list.d } ?: next
                latest.copy(createdAt = signed.createdAt, source = signed).also { put(it) }
            } catch (e: Exception) {
                runCatching { of(me) } // back to what the relays have
                throw e
            }
        }
    }

    /** Deletes a list: an empty replacement (relays keep only the newest) and a deletion request. */
    suspend fun delete(list: com.example.btcwidget.nostr.core.PeopleList) {
        val me = repo.requireMe()
        val now = System.currentTimeMillis() / 1000
        _lists.value = _lists.value.orEmpty().filter { it.d != list.d }
        repo.publish(com.example.btcwidget.nostr.core.UnsignedEvent(me, maxOf(now, list.createdAt + 1), com.example.btcwidget.nostr.core.PeopleList.KIND, listOf(listOf("d", list.d)), ""), fast = true)
        runCatching { repo.publish(com.example.btcwidget.nostr.core.PeopleList.deletion(list, now), fast = true) }
    }

    /** Forgets the user's lists when the session resets (logging out or switching accounts). */
    internal fun clear() {
        _lists.value = null
    }
}
