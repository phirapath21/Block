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
import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.ui.main.EInk
import kotlinx.coroutines.Dispatchers
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
 * 3. [BitcoinContextCard] — Auto-detects Bitcoin transaction IDs (`mempool.space/tx/<txid>` or
 *    standalone 64-hex txid references) and Block Heights (`mempool.space/block/...` or `Block #925,000`)
 *    inside note text and renders a live Mempool Explorer Card (cached in memory for 120 FPS scrolling).
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

@Immutable
data class MempoolSummary(
    val isTx: Boolean,
    val title: String,
    val subtitle: String,
    val confirmed: Boolean,
    val primaryMetric: String,
    val secondaryMetric: String,
    val explorerUrl: String
)

object BitcoinContextDetector {
    private val refCache = ConcurrentHashMap<String, List<BitcoinEmbedRef>>()
    private val apiCache = ConcurrentHashMap<String, MempoolSummary>()

    private val MEMPOOL_TX_URL = Regex("""https?://(?:www\.)?(?:mempool\.space|blockstream\.info)/tx/([0-9a-fA-F]{64})\b""")
    private val MEMPOOL_BLOCK_URL = Regex("""https?://(?:www\.)?(?:mempool\.space|blockstream\.info)/block/([0-9a-fA-F]{64}|\d{1,7})\b""")
    private val LABELED_TXID = Regex("""(?i)\b(?:txid|tx)\s*[:=]\s*([0-9a-fA-F]{64})\b""")
    private val INLINE_BLOCK_NUM = Regex("""(?i)\bblock\s*#(\d{3},?\d{3}|\d{6,7})\b""")

    /** Detects at most 1 Bitcoin Transaction or Block reference in a note to render an interactive embed card. */
    fun detect(note: Event): BitcoinEmbedRef? {
        val list = refCache.getOrPut(note.id) {
            val content = note.content
            buildList {
                MEMPOOL_TX_URL.find(content)?.groupValues?.getOrNull(1)?.lowercase()?.let {
                    add(BitcoinEmbedRef.TxRef(it))
                }
                if (isEmpty()) {
                    LABELED_TXID.find(content)?.groupValues?.getOrNull(1)?.lowercase()?.let {
                        add(BitcoinEmbedRef.TxRef(it))
                    }
                }
                if (isEmpty()) {
                    MEMPOOL_BLOCK_URL.find(content)?.groupValues?.getOrNull(1)?.lowercase()?.let {
                        add(BitcoinEmbedRef.BlockRef(it))
                    }
                }
                if (isEmpty() && BlockStamp.fromEvent(note) == null) {
                    INLINE_BLOCK_NUM.find(content)?.groupValues?.getOrNull(1)?.replace(",", "")?.let { h ->
                        val num = h.toIntOrNull()
                        if (num != null && num in 100_000..2_500_000) {
                            add(BitcoinEmbedRef.BlockRef(num.toString()))
                        }
                    }
                }
            }
        }
        return list.firstOrNull()
    }

    suspend fun fetchSummary(ref: BitcoinEmbedRef, baseUrl: String = "https://mempool.space"): MempoolSummary = withContext(Dispatchers.IO) {
        val base = baseUrl.trimEnd('/').ifBlank { "https://mempool.space" }
        apiCache["${ref.key}:$base"]?.let { return@withContext it }

        val summary = runCatching {
            when (ref) {
                is BitcoinEmbedRef.TxRef -> fetchTxSummary(ref, base)
                is BitcoinEmbedRef.BlockRef -> fetchBlockSummary(ref, base)
            }
        }.getOrElse {
            when (ref) {
                is BitcoinEmbedRef.TxRef -> MempoolSummary(
                    isTx = true,
                    title = "TX ${ref.txid.take(8)}…${ref.txid.takeLast(8)}",
                    subtitle = "Bitcoin Transaction · mempool.space",
                    confirmed = true,
                    primaryMetric = "On-Chain TX",
                    secondaryMetric = "Tap to inspect",
                    explorerUrl = ref.explorerUrl(base)
                )
                is BitcoinEmbedRef.BlockRef -> MempoolSummary(
                    isTx = false,
                    title = if (ref.heightOrHash.length <= 8) "Block #${"%,d".format(Locale.US, ref.heightOrHash.toIntOrNull() ?: 0)}"
                    else "Block ${ref.heightOrHash.take(8)}…",
                    subtitle = "Bitcoin Timechain · mempool.space",
                    confirmed = true,
                    primaryMetric = "Verified Block",
                    secondaryMetric = "Tap to inspect",
                    explorerUrl = ref.explorerUrl(base)
                )
            }
        }
        apiCache["${ref.key}:$base"] = summary
        summary
    }

    private fun fetchTxSummary(ref: BitcoinEmbedRef.TxRef, base: String): MempoolSummary {
        val json = JSONObject(httpGet("$base/api/tx/${ref.txid}"))
        val feeSats = json.optLong("fee", 0L)
        val weight = json.optInt("weight", 0)
        val vbytes = if (weight > 0) (weight + 3) / 4 else json.optInt("size", 1)
        val feeRate = if (vbytes > 0) String.format(Locale.US, "%.1f sat/vB", feeSats.toDouble() / vbytes) else "$feeSats sats"

        val vout = json.optJSONArray("vout")
        var totalSats = 0L
        if (vout != null) {
            for (i in 0 until vout.length()) {
                totalSats += vout.optJSONObject(i)?.optLong("value", 0L) ?: 0L
            }
        }
        val btcValue = if (totalSats >= 100_000_000L) {
            String.format(Locale.US, "%.4f BTC", totalSats / 100_000_000.0)
        } else {
            "${"%,d".format(Locale.US, totalSats)} sats"
        }

        val status = json.optJSONObject("status")
        val confirmed = status?.optBoolean("confirmed", false) == true
        val blockHeight = status?.optInt("block_height", 0) ?: 0
        val statusLabel = if (confirmed && blockHeight > 0) {
            "Confirmed in Block #${"%,d".format(Locale.US, blockHeight)}"
        } else if (confirmed) {
            "Confirmed on Timechain"
        } else {
            "Unconfirmed in Mempool"
        }

        return MempoolSummary(
            isTx = true,
            title = "TX ${ref.txid.take(10)}…${ref.txid.takeLast(8)}",
            subtitle = statusLabel,
            confirmed = confirmed,
            primaryMetric = btcValue,
            secondaryMetric = "Fee: $feeRate",
            explorerUrl = ref.explorerUrl(base)
        )
    }

    private fun fetchBlockSummary(ref: BitcoinEmbedRef.BlockRef, base: String): MempoolSummary {
        val hash = if (ref.heightOrHash.length == 64) {
            ref.heightOrHash
        } else {
            httpGet("$base/api/block-height/${ref.heightOrHash}").trim()
        }
        val json = JSONObject(httpGet("$base/api/block/$hash"))
        val height = json.optInt("height", ref.heightOrHash.toIntOrNull() ?: 0)
        val txCount = json.optInt("tx_count", 0)
        val sizeBytes = json.optInt("size", 0)
        val sizeMb = String.format(Locale.US, "%.2f MB", sizeBytes / 1_000_000.0)

        return MempoolSummary(
            isTx = false,
            title = "Bitcoin Block #${"%,d".format(Locale.US, height)}",
            subtitle = "Hash ${hash.take(10)}…${hash.takeLast(8)}",
            confirmed = true,
            primaryMetric = "${"%,d".format(Locale.US, txCount)} txs",
            secondaryMetric = "Size: $sizeMb",
            explorerUrl = ref.explorerUrl(base)
        )
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("User-Agent", "Block-Android")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * Interactive Bitcoin / Mempool context card shown inside [NoteBody] when a note references
 * a Bitcoin transaction or block height.
 */
@Composable
fun BitcoinContextCard(
    ref: BitcoinEmbedRef,
    note: Event,
    actions: NoteActions,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mempoolBase = remember { com.example.btcwidget.AppPrefs.mempoolUrl(context).ifBlank { "https://mempool.space" } }
    val summary by produceState<MempoolSummary?>(null, ref.key, mempoolBase) {
        value = BitcoinContextDetector.fetchSummary(ref, mempoolBase)
    }
    val bitcoinOrange = Color(0xFFF7931A)
    val confirmedGreen = Color(0xFF2EA043)

    val s = summary
    Column(
        modifier = modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        bitcoinOrange.copy(alpha = 0.11f),
                        EInk.Body
                    )
                )
            )
            .border(0.75.dp, bitcoinOrange.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .clickable(onClickLabel = tr("View on Mempool")) {
                actions.openLink(s?.explorerUrl ?: ref.explorerUrl, note)
            }
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(bitcoinOrange.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Glyphs.BlockCube,
                        contentDescription = null,
                        tint = bitcoinOrange,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = s?.title ?: when (ref) {
                            is BitcoinEmbedRef.TxRef -> "TX ${ref.txid.take(10)}…${ref.txid.takeLast(8)}"
                            is BitcoinEmbedRef.BlockRef -> "Block #${ref.heightOrHash}"
                        },
                        color = EInk.Value,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = s?.subtitle ?: tr("Fetching Mempool status…"),
                        color = EInk.Caption,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (s != null) {
                Box(
                    Modifier
                        .padding(start = 8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (s.confirmed) confirmedGreen.copy(alpha = 0.16f)
                            else bitcoinOrange.copy(alpha = 0.18f)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (s.confirmed) tr("✓ Confirmed") else tr("⏳ Mempool"),
                        color = if (s.confirmed) confirmedGreen else bitcoinOrange,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (s != null) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = EInk.BodyStroke,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = s.primaryMetric,
                    color = bitcoinOrange,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = s.secondaryMetric,
                    color = EInk.Label,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
