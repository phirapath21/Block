package com.example.btcwidget.ui.social

import android.app.Application
import com.example.btcwidget.AppPrefs
import com.example.btcwidget.nostr.NostrRepository
import com.example.btcwidget.nostr.core.Pow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Proof of work (NIP-13), only when a relay asks for it: some relays take posts only with enough
 * work, so Block mines what they ask (within reach) before sending. No setting: it's automatic.
 */
class PowSettings(private val app: Application, private val repo: NostrRepository, private val scope: CoroutineScope) {

    private val _hashRate = MutableStateFlow(AppPrefs.powHashRate(app).toDouble())
    /** This phone's hashes per second (0 until measured), for "≈ N s" estimates. */
    val hashRate: StateFlow<Double> = _hashRate.asStateFlow()
    internal fun rememberHashRate(rate: Double) {
        if (rate <= 0 || !rate.isFinite()) return
        _hashRate.value = rate
        AppPrefs.setPowHashRate(app, rate.toFloat())
    }

    private val _benchmarking = MutableStateFlow(false)

    /** Measures this phone's mining speed for a moment (skipped when it's known unless [force]). */
    fun benchmark(force: Boolean = false) {
        if (_benchmarking.value || (!force && _hashRate.value > 0)) return
        _benchmarking.value = true
        scope.launch {
            try { rememberHashRate(Pow.benchmark()) } finally { _benchmarking.value = false }
        }
    }

    private val _relayPow = MutableStateFlow<Pair<Int, String?>>(0 to null)
    /** The most proof of work any of your relays asks for, and which relay (0 = none known). */
    val relayPow: StateFlow<Pair<Int, String?>> = _relayPow.asStateFlow()
    fun checkRelayPow() = scope.launch { _relayPow.value = runCatching { repo.relaysMinPow() }.getOrDefault(0 to null) }

    /** What the relays a post goes to ask for (NIP-11), when within reach; 0 when none ask. */
    internal suspend fun bitsNeeded(): Int {
        val (asked, _) = runCatching { repo.relaysMinPow() }.getOrDefault(0 to null).also { _relayPow.value = it }
        return if (asked in 1..RELAY_POW_LIMIT) asked else 0
    }
}

/** Relays asking for more proof of work than this aren't matched automatically (it'd take minutes). */
private const val RELAY_POW_LIMIT = 24
