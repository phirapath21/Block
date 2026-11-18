package com.example.btcwidget.ui.social

import androidx.compose.foundation.background
import com.example.btcwidget.ui.main.screenBackground
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.PriceData
import com.example.btcwidget.TxDetails
import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.ui.main.EInk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Feature #6: Proof-of-Blockheight & Bitcoin Context Embeds.
 *
 * 1. [BlockStamp] — Cryptographic / social Proof-of-Blockheight attached to a Nostr event via a
 *    structured `["block", "<height>", "<btcUsd>", "<feeSatVb>"]` tag when signed.
 * 2. [BlockStampBadge] — Sleek Bitcoin-orange pill shown on stamped notes; tapping opens a detailed
 *    Timechain Snapshot dialog with Halving Epoch, Moscow Time, and 1-tap Mempool Explorer link.
 * 3. [BitcoinContextDetector] — finds every transaction a note links to (`mempool.space/tx/<txid>`,
 *    `txid: <hex>`), or the block it points at, and fetches them from the mempool server; the cards
 *    themselves ([BitcoinEmbeds]) are in TxCards.kt.
 */
@Immutable
data class BlockStamp(
    val height: Int,
    val btcUsd: Double? = null,
    val feeSatVb: Int? = null
) {
    val moscowTime: Int?
        get() = btcUsd?.takeIf { it > 0 }?.let { (100_000_000.0 / it).toInt() }

    val halvingEpoch: Int
        get() = (height / 210_000) + 1

    fun toTag(): List<String> = buildList {
        add("block")
        add(height.toString())
        btcUsd?.takeIf { it > 0 }?.let { add(it.toInt().toString()) }
        feeSatVb?.takeIf { it > 0 }?.let { add(it.toString()) }
    }

    fun compactLabel(): String {
        val h = "%,d".format(Locale.US, height)
        val p = btcUsd?.takeIf { it > 0 }?.let { " · \$${"%,.0f".format(Locale.US, it)}" }.orEmpty()
        return "⛓ #$h$p"
    }

    fun composerChipLabel(): String {
        val h = "%,d".format(Locale.US, height)
        val p = btcUsd?.takeIf { it > 0 }?.let { " · \$${"%,.0f".format(Locale.US, it)}" }.orEmpty()
        val f = feeSatVb?.takeIf { it > 0 }?.let { " · $it sat/vB" }.orEmpty()
        return "Block #$h$p$f"
    }

    companion object {
        private val stampCache = ConcurrentHashMap<String, Any>()
        private val NONE = Any()
        private val INLINE_BLOCK_REGEX = Regex("""(?i)\b(?:block\s*#?|⛓\s*#?)(\d{3},?\d{3}|\d{6,7})\b""")

        fun fromPriceData(data: PriceData?): BlockStamp? {
            val h = data?.blockHeight?.takeIf { it > 0 } ?: return null
            return BlockStamp(
                height = h,
                btcUsd = data.btcUsd.takeIf { it > 0 },
                feeSatVb = data.feeFastest.takeIf { it > 0 }
            )
        }

        fun fromEvent(event: Event): BlockStamp? {
            val cached = stampCache[event.id]
            if (cached != null) return cached.takeIf { it !== NONE } as? BlockStamp

            val blockTag = event.tags.firstOrNull {
                val k = it.firstOrNull()
                k == "block" || k == "blockheight" || k == "bitcoin_block"
            }
            val parsed = if (blockTag != null) {
                val h = blockTag.getOrNull(1)?.replace(",", "")?.toIntOrNull()?.takeIf { it in 1..10_000_000 }
                if (h != null) {
                    val usd = blockTag.getOrNull(2)?.toDoubleOrNull()?.takeIf { it > 0 }
                    val fee = blockTag.getOrNull(3)?.toIntOrNull()?.takeIf { it > 0 }
                    BlockStamp(h, usd, fee)
                } else null
            } else null

            stampCache[event.id] = parsed ?: NONE
            return parsed
        }

        /** Extracts either a tag-based [BlockStamp] or an inline `Block #925,000` reference. */
        fun fromEventOrInline(event: Event): BlockStamp? {
            fromEvent(event)?.let { return it }
            val match = INLINE_BLOCK_REGEX.find(event.content) ?: return null
            val h = match.groupValues[1].replace(",", "").toIntOrNull()?.takeIf { it in 100_000..2_500_000 } ?: return null
            return BlockStamp(h)
        }
    }
}

/**
 * Compact Proof-of-Blockheight badge rendered under the author line on stamped Nostr notes.
 * Tapping opens a rich Timechain Snapshot dialog with block metadata and a Mempool Explorer button.
 */
@Composable
fun BlockStampBadge(
    stamp: BlockStamp,
    note: Event,
    actions: NoteActions,
    modifier: Modifier = Modifier
) {
    var showSheet by remember { mutableStateOf(false) }
    val bitcoinOrange = Color(0xFFF7931A)

    Row(
        modifier = modifier
            .padding(top = 3.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(50))
            .background(bitcoinOrange.copy(alpha = 0.12f))
            .border(0.5.dp, bitcoinOrange.copy(alpha = 0.45f), RoundedCornerShape(50))
            .clickable(onClickLabel = tr("Proof of Blockheight")) { showSheet = true }
            .padding(horizontal = 8.dp, vertical = 2.5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            Glyphs.BlockCube,
            contentDescription = null,
            tint = bitcoinOrange,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = buildString {
                append("#")
                append("%,d".format(Locale.US, stamp.height))
                stamp.btcUsd?.takeIf { it > 0 }?.let {
                    append(" · \$")
                    append("%,.0f".format(Locale.US, it))
                }
                stamp.feeSatVb?.takeIf { it > 0 }?.let {
                    append(" · ")
                    append(it)
                    append(" sat/vB")
                }
            },
            color = bitcoinOrange,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }

    if (showSheet) {
        BlockDialog(
            onDismiss = { showSheet = false },
            title = tr("Proof of Blockheight"),
            confirmLabel = tr("View on Mempool"),
            onConfirm = {
                showSheet = false
                actions.openLink("https://mempool.space/block/${stamp.height}", note)
            }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    tr("This note was signed with a Bitcoin Timechain snapshot attached to the Nostr event."),
                    color = EInk.Label,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .screenBackground()
                        .border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SnapshotRow(tr("Block Height"), "#${"%,d".format(Locale.US, stamp.height)}", highlight = true)
                    stamp.btcUsd?.takeIf { it > 0 }?.let {
                        SnapshotRow(tr("BTC Price at Post"), "\$${"%,.0f".format(Locale.US, it)}")
                    }
                    stamp.moscowTime?.let {
                        SnapshotRow(tr("Moscow Time"), "${"%,d".format(Locale.US, it)} sats / \$1")
                    }
                    stamp.feeSatVb?.takeIf { it > 0 }?.let {
                        SnapshotRow(tr("Network Fee"), "$it sat/vB")
                    }
                    SnapshotRow(tr("Halving Epoch"), tr("Epoch {0} (3.125 BTC subsidy)", stamp.halvingEpoch))
                    SnapshotRow(tr("Signed Timestamp"), fullTimestamp(note.createdAt))
                }
            }
        }
    }
}

@Composable
private fun SnapshotRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = EInk.Caption, fontSize = 13.sp)
        Text(
            value,
            color = if (highlight) Color(0xFFF7931A) else EInk.Value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

// ============================================================================
// Interactive Bitcoin / Mempool Context Embeds (Transactions & Blocks)
// ============================================================================

sealed interface BitcoinEmbedRef {
    val key: String
    val explorerUrl: String
    fun explorerUrl(baseUrl: String): String = when (this) {
        is TxRef -> "${baseUrl.trimEnd('/')}/tx/$txid"
        is BlockRef -> "${baseUrl.trimEnd('/')}/block/$heightOrHash"
    }

    data class TxRef(val txid: String) : BitcoinEmbedRef {
        override val key: String = "tx:$txid"
        override val explorerUrl: String = "https://mempool.space/tx/$txid"
    }

    data class BlockRef(val heightOrHash: String) : BitcoinEmbedRef {
        override val key: String = "block:$heightOrHash"
        override val explorerUrl: String = "https://mempool.space/block/$heightOrHash"
    }
}

/** A block, as its card shows it. */
@Immutable
data class MempoolSummary(
    val title: String,
    val subtitle: String,
    val primaryMetric: String,
    val secondaryMetric: String,
    val explorerUrl: String
)

object BitcoinContextDetector {
    /** The most transactions one note's card lists. */
    const val MAX_TXS = 12

    private val refCache = ConcurrentHashMap<String, List<BitcoinEmbedRef>>()

    /**
     * What a note's Bitcoin card shows: every transaction it links to or labels (`txid: …`), in
     * the order they appear, without repeats, at most [MAX_TXS]; or, when there are none, the
     * one block it points at. Empty when there's nothing.
     */
    fun detectAll(note: Event): List<BitcoinEmbedRef> = refCache.getOrPut(note.id) {
        val links = com.example.btcwidget.TxLinks.find(note.content, stamped = BlockStamp.fromEvent(note) != null, maxTxs = MAX_TXS)
        links.txids.map { BitcoinEmbedRef.TxRef(it) }.ifEmpty { listOfNotNull(links.block?.let { BitcoinEmbedRef.BlockRef(it) }) }
    }

    /** The first of [detectAll]. */
    fun detect(note: Event): BitcoinEmbedRef? = detectAll(note).firstOrNull()

    // ---- Transactions ----

    private class Cached(val tx: TxDetails, val at: Long)
    private val txCache = ConcurrentHashMap<String, Cached>()
    private val txInFlight = ConcurrentHashMap<String, kotlinx.coroutines.Deferred<TxDetails?>>()
    private val tipCache = ConcurrentHashMap<String, Pair<Int, Long>>()
    private val blockCache = ConcurrentHashMap<String, MempoolSummary>()
    private val io = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)

    /** How long a transaction that could still change (in the mempool, or under 6 blocks) is reused. */
    private const val FRESH_MS = 30_000L

    /**
     * [txid] from [base]'s Esplora API, or null if it couldn't be fetched (not remembered, so
     * the next try asks again). Several cards asking at once share one request; a transaction
     * buried 6 blocks deep is kept for the session, anything newer for [FRESH_MS].
     */
    suspend fun tx(txid: String, base: String, force: Boolean = false): TxDetails? {
        val key = "$base $txid"
        val now = System.currentTimeMillis()
        txCache[key]?.let { c ->
            val settled = c.tx.confirmations(tipCache[base]?.first) >= 6
            if (!force && (settled || now - c.at < FRESH_MS)) return c.tx
        }
        // Lazy, so it can't finish (and remove itself) before it's in the map.
        val job = txInFlight.computeIfAbsent(key) {
            io.async(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                try {
                    runCatching { TxDetails.parse(httpGet("$base/api/tx/$txid")) }.getOrNull()
                        ?.also { txCache[key] = Cached(it, System.currentTimeMillis()) }
                } finally {
                    txInFlight.remove(key)
                }
            }
        }
        job.start()
        return job.await() ?: txCache[key]?.tx
    }

    /** The height of [base]'s latest block (for confirmations), reused for [FRESH_MS]; null if it can't be had. */
    suspend fun tipHeight(base: String): Int? {
        val now = System.currentTimeMillis()
        tipCache[base]?.let { (h, at) -> if (now - at < FRESH_MS) return h }
        return withContext(Dispatchers.IO) {
            runCatching { httpGet("$base/api/blocks/tip/height").trim().toInt() }.getOrNull()
        }?.also { tipCache[base] = it to now } ?: tipCache[base]?.first
    }

    // ---- Blocks ----

    /** A block's card, or null if it couldn't be fetched (asked again next time). */
    suspend fun block(ref: BitcoinEmbedRef.BlockRef, base: String): MempoolSummary? = withContext(Dispatchers.IO) {
        val key = "$base ${ref.key}"
        blockCache[key] ?: runCatching { fetchBlockSummary(ref, base) }.getOrNull()?.also { blockCache[key] = it }
    }

    private fun fetchBlockSummary(ref: BitcoinEmbedRef.BlockRef, base: String): MempoolSummary {
        val hash = if (ref.heightOrHash.length == 64) ref.heightOrHash else httpGet("$base/api/block-height/${ref.heightOrHash}").trim()
        val json = JSONObject(httpGet("$base/api/block/$hash"))
        val height = json.optInt("height", ref.heightOrHash.toIntOrNull() ?: 0)
        val txCount = json.optInt("tx_count", 0)
        val sizeMb = String.format(Locale.US, "%.2f MB", json.optInt("size", 0) / 1_000_000.0)
        return MempoolSummary(
            title = "Block #${"%,d".format(Locale.US, height)}",
            subtitle = "Hash ${hash.take(10)}…${hash.takeLast(8)}",
            primaryMetric = "${"%,d".format(Locale.US, txCount)} txs",
            secondaryMetric = "Size: $sizeMb",
            explorerUrl = ref.explorerUrl(base)
        )
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 6000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "Block-Android")
            if (conn.responseCode !in 200..299) throw java.io.IOException("HTTP ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText().take(2_000_000) }
        } finally {
            conn.disconnect()
        }
    }
}
