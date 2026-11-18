package com.example.btcwidget.nostr

import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Zaps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lightning invoices from people's Lightning addresses: zaps (NIP-57) and plain payments. */
class ZapInvoices internal constructor(private val repo: NostrRepository) {
    /**
     * Gets a Lightning invoice zapping [amountSat] to the author of [note] (or to [recipient]).
     * The caller hands it to a wallet app.
     */
    suspend fun invoice(recipient: String, amountSat: Long, note: Event? = null, comment: String = "", address: String? = null): String = withContext(Dispatchers.IO) {
        val me = repo.requireMe()
        val profile = repo.profiles.value[recipient] ?: repo.refreshProfile(recipient)
            ?: error("Couldn't load their profile")
        val endpoint = Zaps.payEndpoint(profile) ?: error("They haven't set up a Lightning address")
        val info = Zaps.parsePayInfo(repo.httpGet(endpoint)) ?: error("Their Lightning address didn't answer")
        val msat = amountSat * 1000
        if (msat < info.minSendableMsat || msat > info.maxSendableMsat) {
            error("Amount must be between ${info.minSendableMsat / 1000} and ${info.maxSendableMsat / 1000} sats")
        }
        val lnurl = Zaps.encodeLnurl(endpoint)
        if (!info.allowsNostr) {
            // Plain LNURL payment: still pays, but no zap receipt will appear.
            return@withContext Zaps.parseInvoice(repo.httpGet(info.callback + (if ('?' in info.callback) "&" else "?") + "amount=$msat")).getOrThrow()
        }
        val request = repo.requireSigner().sign(
            Zaps.request(me, recipient, msat, lnurl, repo.pool.urls.toList(), note?.id, note?.kind, comment, address)
        )
        Zaps.parseInvoice(repo.httpGet(Zaps.callbackUrl(info, msat, request, lnurl))).getOrThrow()
    }

    /**
     * Gets a plain Lightning invoice (LNURL-pay, no zap request) for [amountSat] to [recipient]'s
     * Lightning address. Unlike a zap nothing is signed and no public receipt is published, so
     * paying for a Store order doesn't tell everyone who bought what.
     */
    suspend fun payInvoice(recipient: String, amountSat: Long): String = withContext(Dispatchers.IO) {
        val profile = repo.profiles.value[recipient] ?: repo.refreshProfile(recipient)
            ?: error("Couldn't load their profile")
        val endpoint = Zaps.payEndpoint(profile) ?: error("They haven't set up a Lightning address")
        val info = Zaps.parsePayInfo(repo.httpGet(endpoint)) ?: error("Their Lightning address didn't answer")
        val msat = amountSat * 1000
        if (msat < info.minSendableMsat || msat > info.maxSendableMsat) {
            error("Amount must be between ${info.minSendableMsat / 1000} and ${info.maxSendableMsat / 1000} sats")
        }
        Zaps.parseInvoice(repo.httpGet(info.callback + (if ('?' in info.callback) "&" else "?") + "amount=$msat")).getOrThrow()
    }
}
