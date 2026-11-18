package com.example.btcwidget.nostr

import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Filter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The wallet connected with Nostr Wallet Connect (NIP-47): its connection, payments, balance and invoices. */
class NwcWallet internal constructor(private val repo: NostrRepository) {
    private val _nwc = MutableStateFlow(NostrAccountStore.nwc(repo.appContext)?.let { com.example.btcwidget.nostr.core.Nwc.parse(it) })
    /** The connected wallet, or null: zaps then open a wallet app instead. */
    val connection: StateFlow<com.example.btcwidget.nostr.core.Nwc.Connection?> = _nwc.asStateFlow()

    /** Saves a `nostr+walletconnect://` string (encrypted). Throws if it isn't one. */
    fun connect(uri: String) {
        val c = com.example.btcwidget.nostr.core.Nwc.parse(uri) ?: throw IllegalArgumentException("That isn't a nostr+walletconnect:// link")
        NostrAccountStore.setNwc(repo.appContext, uri.trim())
        _nwc.value = c
    }

    fun disconnect() {
        NostrAccountStore.setNwc(repo.appContext, null)
        _nwc.value = null
    }

    /**
     * Sends a NIP-47 request to the connected wallet's relay and waits for its response event.
     */
    private suspend fun request(
        timeoutMs: Long = 30_000,
        buildRequest: (com.example.btcwidget.nostr.core.Nwc.Connection, Boolean) -> Event
    ): Pair<com.example.btcwidget.nostr.core.Nwc.Connection, Event> {
        val c = _nwc.value ?: error("No wallet connected")
        val nwc = com.example.btcwidget.nostr.core.Nwc
        val walletPool = RelayPool().also { it.setRelays(c.relays) }
        try {
            val info = walletPool.query(listOf(Filter(authors = listOf(c.walletPubkey), kinds = listOf(nwc.INFO), limit = 1)), 4_000).firstOrNull()
            val request = buildRequest(c, nwc.supportsNip44(info))
            val answer = kotlinx.coroutines.CompletableDeferred<Event>()
            val stop = walletPool.subscribe(
                listOf(Filter(authors = listOf(c.walletPubkey), kinds = listOf(nwc.RESPONSE), tags = mapOf("e" to listOf(request.id)))),
                { e, _ -> answer.complete(e) }
            )
            try {
                val sent = walletPool.publish(request)
                if (sent.none { it.accepted }) error(sent.firstOrNull { it.message.isNotBlank() }?.message ?: "The wallet's relay didn't accept the request")
                val response = kotlinx.coroutines.withTimeoutOrNull(timeoutMs) { answer.await() } ?: error("The wallet didn't answer in time")
                return c to response
            } finally {
                stop()
            }
        } finally {
            walletPool.closeAll()
        }
    }

    /**
     * Pays [invoice] through the connected wallet: sends the request to the wallet's relay
     * and waits up to 45 s for its answer. Returns the preimage; throws the wallet's error.
     */
    suspend fun pay(invoice: String): String {
        val nwc = com.example.btcwidget.nostr.core.Nwc
        val (c, response) = request(timeoutMs = 45_000) { conn, nip44 -> nwc.payInvoiceRequest(conn, invoice, nip44) }
        return nwc.parseResponse(c, response).getOrThrow()
    }

    /** Reads the wallet balance in millisatoshis via NIP-47 `get_balance`. */
    suspend fun balance(): Long {
        val nwc = com.example.btcwidget.nostr.core.Nwc
        val (c, response) = request(timeoutMs = 20_000) { conn, nip44 -> nwc.getBalanceRequest(conn, nip44) }
        return nwc.parseBalance(c, response).getOrThrow()
    }

    /** Creates a BOLT11 invoice for [amountSat] sats via NIP-47 `make_invoice`. */
    suspend fun makeInvoice(amountSat: Long, description: String = ""): String {
        val nwc = com.example.btcwidget.nostr.core.Nwc
        val (c, response) = request(timeoutMs = 25_000) { conn, nip44 -> nwc.makeInvoiceRequest(conn, amountSat, description, nip44) }
        return nwc.parseMakeInvoice(c, response).getOrThrow()
    }

    /** Lists recent wallet transactions via NIP-47 `list_transactions`. */
    suspend fun transactions(limit: Int = 20): List<com.example.btcwidget.nostr.core.Nwc.Transaction> {
        val nwc = com.example.btcwidget.nostr.core.Nwc
        val (c, response) = request(timeoutMs = 25_000) { conn, nip44 -> nwc.listTransactionsRequest(conn, limit, nip44) }
        return nwc.parseTransactions(c, response).getOrThrow()
    }

    /** Reads the saved connection again, after switching accounts. */
    internal fun reload() {
        _nwc.value = NostrAccountStore.nwc(repo.appContext)?.let { com.example.btcwidget.nostr.core.Nwc.parse(it) }
    }

    /** Forgets the connection in memory (the account's store is cleared separately). */
    internal fun forget() {
        _nwc.value = null
    }
}
