package com.example.btcwidget.nostr

import com.example.btcwidget.nostr.core.Nip05
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** NIP-05 verification: whether a name@domain address really belongs to a key. */
class Nip05Checks internal constructor(private val repo: NostrRepository) {
    private val _verified = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    /** "pubkey|name@domain" → whether the domain's nostr.json really maps the name to that key. */
    val verified: StateFlow<Map<String, Boolean>> = _verified.asStateFlow()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    /**
     * Checks [identifier] against https://domain/.well-known/nostr.json once per session.
     * Until it's confirmed, the app doesn't show the address as verified.
     */
    fun verify(pubkey: String, identifier: String) {
        val key = "$pubkey|$identifier"
        if (_verified.value.containsKey(key) || !inFlight.add(key)) return
        repo.scope.launch(Dispatchers.IO) {
            val ok = runCatching {
                val url = Nip05.url(identifier) ?: return@runCatching false
                val name = Nip05.parts(identifier)?.first ?: return@runCatching false
                Nip05.pubkeyFrom(repo.httpGet(url), name) == pubkey
            }.getOrDefault(false)
            _verified.update { it + (key to ok) }
            inFlight.remove(key)
        }
    }
}
