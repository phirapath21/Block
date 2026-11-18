package com.example.btcwidget

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * A Bitcoin transaction as an Esplora API (mempool.space, blockstream.info, a self-hosted
 * mempool) returns it from `/api/tx/<txid>`: where the coins came from, where they went, the fee
 * and whether it's in a block. Pure Kotlin so it can be tested on the JVM.
 */
data class TxDetails(
    val txid: String,
    val inputs: List<TxIo>,
    val outputs: List<TxIo>,
    val feeSats: Long,
    val vsize: Int,
    val confirmed: Boolean,
    val blockHeight: Int?,
    /** Seconds since the epoch. */
    val blockTime: Long?
) {
    val totalOut: Long get() = outputs.sumOf { it.sats }
    val totalIn: Long get() = inputs.sumOf { it.sats }
    val isCoinbase: Boolean get() = inputs.any { it.kind == TxIo.Kind.COINBASE }
    val feeRate: Double get() = if (vsize > 0) feeSats.toDouble() / vsize else 0.0

    /** Blocks on top of (and including) the one it's in; 0 while in the mempool. */
    fun confirmations(tipHeight: Int?): Int = when {
        !confirmed -> 0
        blockHeight == null || tipHeight == null -> 1
        else -> (tipHeight - blockHeight + 1).coerceAtLeast(1)
    }

    companion object {
        fun parse(json: String): TxDetails = parse(JSONObject(json))

        fun parse(o: JSONObject): TxDetails {
            val vin = o.optJSONArray("vin") ?: JSONArray()
            val vout = o.optJSONArray("vout") ?: JSONArray()
            val inputs = (0 until vin.length()).mapNotNull { vin.optJSONObject(it) }.map { i ->
                if (i.optBoolean("is_coinbase")) TxIo(null, 0, TxIo.Kind.COINBASE)
                else i.optJSONObject("prevout").let { p -> TxIo.of(p) }
            }
            val outputs = (0 until vout.length()).mapNotNull { vout.optJSONObject(it) }.map { TxIo.of(it) }
            val weight = o.optInt("weight", 0)
            val vsize = if (weight > 0) (weight + 3) / 4 else o.optInt("size", 0)
            val status = o.optJSONObject("status")
            val confirmed = status?.optBoolean("confirmed") == true
            return TxDetails(
                txid = o.optString("txid"),
                inputs = inputs,
                outputs = outputs,
                // A coinbase pays no fee; Esplora reports 0 for it.
                feeSats = o.optLong("fee", 0L).coerceAtLeast(0),
                vsize = vsize,
                confirmed = confirmed,
                blockHeight = status?.optInt("block_height", 0)?.takeIf { confirmed && it > 0 },
                blockTime = status?.optLong("block_time", 0L)?.takeIf { confirmed && it > 0 }
            )
        }
    }
}

/** One input or output. [address] is null for coinbase, OP_RETURN and bare scripts. */
data class TxIo(val address: String?, val sats: Long, val kind: Kind) {
    enum class Kind { ADDRESS, COINBASE, OP_RETURN, SCRIPT }

    companion object {
        fun of(o: JSONObject?): TxIo {
            if (o == null) return TxIo(null, 0, Kind.SCRIPT)
            val address = o.optString("scriptpubkey_address").takeIf { it.isNotBlank() && it != "null" }
            val type = o.optString("scriptpubkey_type")
            val kind = when {
                type == "op_return" -> Kind.OP_RETURN
                address != null -> Kind.ADDRESS
                else -> Kind.SCRIPT
            }
            return TxIo(address, o.optLong("value", 0L).coerceAtLeast(0), kind)
        }
    }
}

/** Words and numbers for transaction cards. */
object TxFormat {
    /** "bc1qxy2k…0wlh": enough of both ends to compare by eye. */
    fun shortAddress(address: String): String =
        if (address.length <= 16) address else address.take(8) + "…" + address.takeLast(4)

    /** "a950cccb…298b". */
    fun shortTxid(txid: String): String = if (txid.length <= 14) txid else txid.take(8) + "…" + txid.takeLast(4)

    /**
     * An amount: whole bitcoins with up to 8 decimals (trailing zeros dropped, at least 2) from
     * 0.001 BTC up, sats below that. "2,484.5653 BTC", "0.05 BTC", "12,345 sats".
     */
    fun amount(sats: Long): String {
        if (sats < 100_000) return "%,d sats".format(Locale.US, sats)
        val whole = sats / 100_000_000
        val frac = (sats % 100_000_000).toString().padStart(8, '0').trimEnd('0').padEnd(2, '0')
        return "%,d".format(Locale.US, whole) + "." + frac + " BTC"
    }

    /** "1,234" with a "+" past [cap]: "1,000+". */
    fun count(n: Int, cap: Int = 100_000): String = if (n > cap) "%,d+".format(Locale.US, cap) else "%,d".format(Locale.US, n)

    /**
     * The rows a flow shows: the first [max] as they are, or the first [max] − 1 and one
     * "N more" row for the rest when there are more than [max].
     */
    fun visibleRows(list: List<TxIo>, max: Int): Pair<List<TxIo>, More?> {
        if (list.size <= max) return list to null
        val shown = list.take(max - 1)
        val rest = list.drop(max - 1)
        return shown to More(rest.size, rest.sumOf { it.sats })
    }

    data class More(val count: Int, val sats: Long)
}

/** Bitcoin transactions and blocks a note's text points at. */
object TxLinks {
    data class Found(val txids: List<String>, val block: String?)

    // Mainnet only: testnet and signet links (…/testnet/tx/…) don't match, as the API asked is mainnet's.
    private val TX_URL = Regex("""(?:https?://)?(?:www\.)?(?:mempool\.space|blockstream\.info)/tx/([0-9a-fA-F]{64})(?![0-9a-fA-F])""")
    private val BLOCK_URL = Regex("""(?:https?://)?(?:www\.)?(?:mempool\.space|blockstream\.info)/block/([0-9a-fA-F]{64}|\d{1,7})(?![0-9a-zA-Z])""")
    private val LABELED_TXID = Regex("""(?i)\b(?:txid|tx)\s*[:=]\s*([0-9a-fA-F]{64})(?![0-9a-fA-F])""")
    private val INLINE_BLOCK = Regex("""(?i)\bblock\s*#(\d{3},?\d{3}|\d{6,7})\b""")

    /**
     * Every transaction id in [content] (explorer links and `txid: …`), in order, without
     * repeats, at most [maxTxs]; and the block it points at when there's no transaction
     * (a block link, or "Block #925,000" unless the note is [stamped] with its own block).
     */
    fun find(content: String, stamped: Boolean = false, maxTxs: Int = 12): Found {
        val txids = (TX_URL.findAll(content) + LABELED_TXID.findAll(content))
            .sortedBy { it.range.first }
            .map { it.groupValues[1].lowercase() }
            .distinct()
            .take(maxTxs)
            .toList()
        if (txids.isNotEmpty()) return Found(txids, null)
        BLOCK_URL.find(content)?.groupValues?.get(1)?.lowercase()?.let { return Found(emptyList(), it) }
        if (!stamped) {
            INLINE_BLOCK.find(content)?.groupValues?.get(1)?.replace(",", "")?.toIntOrNull()
                ?.takeIf { it in 100_000..2_500_000 }
                ?.let { return Found(emptyList(), it.toString()) }
        }
        return Found(emptyList(), null)
    }
}
