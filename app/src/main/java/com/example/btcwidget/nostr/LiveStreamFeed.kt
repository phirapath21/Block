package com.example.btcwidget.nostr

import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Filter
import com.example.btcwidget.nostr.core.Kind
import com.example.btcwidget.nostr.core.LiveStreams
import java.util.concurrent.ConcurrentHashMap

/** Live streams (NIP-53): what's on, one stream, and its chat. */
class LiveStreamFeed internal constructor(private val repo: NostrRepository) {

    /** Streams live now and coming up, from our relays and the indexers. */
    suspend fun liveStreams(): List<LiveStreams.Live> {
        val events = repo.queryEverywhere(listOf(Filter(kinds = listOf(LiveStreams.KIND), limit = 150)), timeoutMs = 6_000)
        return LiveStreams.latest(events)
            .filter { it.status != LiveStreams.Status.ENDED && it.host !in repo.muted.value && it.event.pubkey !in repo.muted.value }
            .also { list -> repo.loadProfilesLater(list.map { it.host }) }
    }

    /** One stream by its address ("30311:<pubkey>:<d>"), live or not. */
    suspend fun liveStream(address: String): LiveStreams.Live? {
        val parts = address.split(':', limit = 3).takeIf { it.size == 3 && it[0] == LiveStreams.KIND.toString() } ?: return null
        val events = repo.queryEverywhere(listOf(Filter(kinds = listOf(LiveStreams.KIND), authors = listOf(parts[1]), tags = mapOf("d" to listOf(parts[2])))), timeoutMs = 6_000)
        return LiveStreams.latest(events).firstOrNull()?.also { repo.loadProfilesLater(listOf(it.host)) }
    }

    /** Recent chat in [live], then new messages as they arrive (also on its own relays). Returns a stop function. */
    fun watchLiveChat(live: LiveStreams.Live, onMessages: (List<Event>) -> Unit): () -> Unit {
        // Chat messages, and zaps to the stream (shown in the chat, like Super Chats).
        val filter = Filter(kinds = listOf(LiveStreams.CHAT, Kind.ZAP_RECEIPT), tags = mapOf("a" to listOf(live.address)), limit = 300)
        val seen = ConcurrentHashMap<String, Event>()
        fun emit() = onMessages(seen.values.filter { it.pubkey !in repo.muted.value && LiveStreams.chatOf(it) == live.address }.sortedBy { it.createdAt })
        val onEvent = { e: Event, _: String -> if (seen.putIfAbsent(e.id, e) == null) { repo.loadProfilesLater(listOf(e.pubkey)); emit() } }
        if (live.relays.isNotEmpty()) repo.outbox.addRelays(live.relays)
        val stops = listOf(
            repo.pool.subscribe(listOf(filter), onEvent, { _, _ -> emit() }),
            if (live.relays.isNotEmpty()) repo.outbox.subscribe(listOf(filter), onEvent, { _, _ -> emit() }, only = live.relays.toSet()) else ({})
        )
        return { stops.forEach { it() } }
    }

    suspend fun sendLiveChat(live: LiveStreams.Live, text: String) {
        val me = repo.pubkey ?: error("Log in to Nostr first")
        val signed = repo.publish(LiveStreams.chat(me, live, text.trim()))
        sendTo(live.relays, signed)
    }

    // ---- Helpers ----

    private suspend fun sendTo(relays: List<String>, event: Event) {
        val extra = relays.filter { it !in repo.pool.urls }.take(3).toSet()
        if (extra.isEmpty()) return
        runCatching { repo.outbox.addRelays(extra); repo.outbox.publish(event, only = extra) }
    }
}
