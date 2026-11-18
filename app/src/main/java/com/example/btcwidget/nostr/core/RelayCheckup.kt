package com.example.btcwidget.nostr.core

/**
 * "Check and tidy relays": what to make of a round of connection tests, and which relays
 * to suggest instead. Pure, so the rules are tested on the JVM.
 */
object RelayCheckup {
    /** Slower than this to connect counts as slow (still kept unless the user says). */
    const val SLOW_MS = 1_500L
    /** Never tidy below this many relays. */
    const val MIN_KEEP = 2

    enum class Health { OK, SLOW, DOWN }

    /** One relay's test: [connectMs] when it connected, else null; [error] why not. */
    data class Probe(val url: String, val connectMs: Long?, val error: String? = null) {
        val health: Health get() = when {
            connectMs == null -> Health.DOWN
            connectMs > SLOW_MS -> Health.SLOW
            else -> Health.OK
        }
    }

    data class Report(
        val probes: List<Probe>,
        /** Nothing connected: most likely the phone is offline, so nothing should be removed. */
        val offline: Boolean,
        /** Ticked for removal to begin with: the relays that are down, unless that leaves too few. */
        val suggestedRemovals: Set<String>
    )

    /** Sorts [probes] (working fastest first, then slow, then down) and picks what to remove. */
    fun report(probes: List<Probe>): Report {
        val sorted = probes.sortedWith(compareBy({ it.health.ordinal }, { it.connectMs ?: Long.MAX_VALUE }, { it.url }))
        val offline = probes.isNotEmpty() && probes.all { it.health == Health.DOWN }
        val down = sorted.filter { it.health == Health.DOWN }.map { it.url }
        val removable = if (offline) emptyList() else down.take((probes.size - MIN_KEEP).coerceAtLeast(0))
        return Report(sorted, offline, removable.toSet())
    }

    /** Whether removing [remove] from [all] leaves enough relays. */
    fun canRemove(all: Collection<String>, remove: Collection<String>): Boolean =
        remove.isNotEmpty() && all.count { it !in remove } >= MIN_KEEP

    /**
     * Relays to suggest: the ones most of the people you follow write to ([writeRelays]:
     * pubkey → their NIP-65 write relays), leaving out yours, blocked ones and anything but
     * wss://. Most used first, as (url, how many of them use it).
     */
    fun suggestions(writeRelays: Map<String, List<String>>, mine: Set<String>, blocked: Set<String>, limit: Int = 8): List<Pair<String, Int>> {
        fun norm(u: String) = u.trim().trimEnd('/').lowercase()
        val skip = (mine + blocked).map(::norm).toSet()
        return writeRelays.values
            .flatMap { list -> list.map(::norm).distinct() }
            .filter { it.startsWith("wss://") && it !in skip && !it.contains("localhost") && !it.contains(".onion") }
            .groupingBy { it }.eachCount()
            .entries.sortedWith(compareBy({ -it.value }, { it.key }))
            .take(limit)
            .map { it.key to it.value }
    }
}
