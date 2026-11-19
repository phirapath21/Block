package com.example.btcwidget.nostr

import com.example.btcwidget.nostr.core.RelayCheckup
import com.example.btcwidget.nostr.core.RelayInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Asking relays about themselves: NIP-11 info, the proof of work they want, and connection probes. */
class RelayChecks internal constructor(private val repo: NostrRepository) {
    private val infos = ConcurrentHashMap<String, RelayInfo>()
    private val minPow = ConcurrentHashMap<String, Int>()

    /** What [relay] says about itself (NIP-11), or null if it doesn't answer. Remembered for the session. */
    suspend fun info(relay: String): RelayInfo? {
        infos[relay]?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = okhttp3.Request.Builder().url(RelayInfo.httpUrl(relay))
                    .header("Accept", "application/nostr+json").build()
                RelayPool.defaultClient.newCall(request).execute().use { r ->
                    if (!r.isSuccessful) null else r.body.string().take(200_000).let { RelayInfo.parse(it) }
                }
            }.getOrNull()
        }?.also { infos[relay] = it }
    }

    /**
     * The most proof of work (NIP-11 `min_pow_difficulty`) any relay a post goes to asks for,
     * and which relay that is. Asks each relay once per session, waiting at most [timeoutMs].
     */
    suspend fun minPow(timeoutMs: Long = 1_500): Pair<Int, String?> = coroutineScope {
        val asks = repo.pool.urls.toList().map { url ->
            async {
                // Relays that don't answer count as asking nothing, and aren't asked again.
                url to (minPow[url] ?: (kotlinx.coroutines.withTimeoutOrNull(timeoutMs) { info(url) }?.minPowDifficulty ?: 0)
                    .also { minPow[url] = it })
            }
        }.awaitAll()
        val top = asks.maxByOrNull { it.second }
        val bits = top?.second ?: 0
        bits to top?.first?.takeIf { bits > 0 }
    }

    /**
     * Opens a fresh connection to each of [urls] at once, separate from the ones in use, and
     * reports how long each took to connect (or that it didn't) within [timeoutMs].
     */
    suspend fun probe(urls: List<String>, timeoutMs: Long = 8_000): List<RelayCheckup.Probe> {
        if (urls.isEmpty()) return emptyList()
        val probe = RelayPool()
        return try {
            probe.setRelays(urls)
            val deadline = System.currentTimeMillis() + timeoutMs
            fun settled(st: RelayStatus) = st.state == RelayStatus.State.CONNECTED || st.failures > 0 || st.state == RelayStatus.State.FAILED
            while (System.currentTimeMillis() < deadline && !probe.status().all(::settled)) delay(250)
            val byUrl = probe.status().associateBy { it.url }
            urls.map { u ->
                val st = byUrl[u]
                RelayCheckup.Probe(
                    u,
                    st?.takeIf { it.state == RelayStatus.State.CONNECTED }?.let { it.connectMs ?: 0L },
                    st?.lastError ?: if (st == null) "Blocked" else null
                )
            }
        } finally {
            probe.closeAll()
        }
    }
}
