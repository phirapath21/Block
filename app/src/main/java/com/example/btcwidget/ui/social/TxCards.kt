package com.example.btcwidget.ui.social

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.TxDetails
import com.example.btcwidget.TxFormat
import com.example.btcwidget.TxIo
import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.ui.main.EInk
import kotlinx.coroutines.delay

/** Confirmations after which a transaction is treated as settled (and no longer refreshed). */
private const val SETTLED = 6

/** How often a card for a transaction that could still change asks again. */
private const val REFRESH_MS = 60_000L

/** Rows per side of a flow before the rest are folded into "N more". */
private const val FLOW_ROWS = 4

private val RowHeight = 36.dp
private val CardShape = RoundedCornerShape(16.dp)

/** How a transaction shows under a note: one thin row (details in a sheet) or the full card. */
enum class TxCardStyle(val id: String, val label: String, val summary: String) {
    ROW("row", "Single row", "Amount and confirmations; tap for the full details"),
    FULL("full", "Full card", "Confirmations, inputs flowing to outputs and the fee, right in the post");

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: ROW
    }
}

/** Where a note is shown, for [BitcoinCards]' per-place style. */
enum class EmbedPlace { FEED, POST }

/** Thread screens provide [EmbedPlace.POST]; everything else is a timeline. */
val LocalEmbedPlace = androidx.compose.runtime.staticCompositionLocalOf { EmbedPlace.FEED }

/**
 * Settings › Display › Bitcoin in posts: whether notes show transaction and block cards, and the
 * transaction style in timelines and on a post's page. Snapshot state, loaded at start-up.
 */
object BitcoinCards {
    var showTx by mutableStateOf(true)
        private set
    var showBlocks by mutableStateOf(true)
        private set
    var feedStyle by mutableStateOf(TxCardStyle.ROW)
        private set
    var postStyle by mutableStateOf(TxCardStyle.ROW)
        private set

    fun load(context: android.content.Context) {
        showTx = com.example.btcwidget.AppPrefs.btcTxCards(context)
        showBlocks = com.example.btcwidget.AppPrefs.btcBlockCards(context)
        feedStyle = TxCardStyle.of(com.example.btcwidget.AppPrefs.btcTxStyleFeed(context))
        postStyle = TxCardStyle.of(com.example.btcwidget.AppPrefs.btcTxStylePost(context))
    }

    fun setShowTx(context: android.content.Context, on: Boolean) { showTx = on; com.example.btcwidget.AppPrefs.setBtcTxCards(context, on) }
    fun setShowBlocks(context: android.content.Context, on: Boolean) { showBlocks = on; com.example.btcwidget.AppPrefs.setBtcBlockCards(context, on) }
    fun setFeedStyle(context: android.content.Context, s: TxCardStyle) { feedStyle = s; com.example.btcwidget.AppPrefs.setBtcTxStyleFeed(context, s.id) }
    fun setPostStyle(context: android.content.Context, s: TxCardStyle) { postStyle = s; com.example.btcwidget.AppPrefs.setBtcTxStylePost(context, s.id) }
}

/**
 * The Bitcoin card(s) under a note, as set in [BitcoinCards]:
 * - transactions as one thin row (more folded into "+N more"), tapping opens every one in
 *   full in a sheet; or in full: one card, or a compact list with "Show all" for several;
 * - a block as a thin row, tapping opens its details in a sheet.
 */
@Composable
fun BitcoinEmbeds(refs: List<BitcoinEmbedRef>, note: Event, actions: NoteActions, modifier: Modifier = Modifier) {
    val txs = refs.filterIsInstance<BitcoinEmbedRef.TxRef>()
    val style = if (LocalEmbedPlace.current == EmbedPlace.POST) BitcoinCards.postStyle else BitcoinCards.feedStyle
    when {
        txs.isNotEmpty() -> if (!BitcoinCards.showTx) return
            else if (style == TxCardStyle.ROW) TxRowCard(txs, note, actions, modifier)
            else if (txs.size >= 2) TxStack(txs, note, actions, modifier)
            else TxCard(txs[0], note, actions, modifier.padding(top = 8.dp))
        BitcoinCards.showBlocks -> refs.filterIsInstance<BitcoinEmbedRef.BlockRef>().firstOrNull()?.let { BlockRowCard(it, note, actions, modifier) }
    }
}

// ---- Loading ----

private sealed interface TxState {
    data object Loading : TxState
    data class Loaded(val tx: TxDetails, val tip: Int?) : TxState {
        val confirmations: Int get() = tx.confirmations(tip)
    }
    data object Failed : TxState
}

/** The user's mempool server, or mempool.space. */
@Composable
private fun mempoolBase(): String {
    val context = LocalContext.current
    return remember { com.example.btcwidget.AppPrefs.mempoolUrl(context).trimEnd('/').ifBlank { "https://mempool.space" } }
}

/**
 * [txid]'s state, kept fresh while on screen: asked again every minute until it's [SETTLED]
 * deep, so a card watched from the mempool ticks up to "6 confirmations" by itself (only when
 * [live]: rows in a timeline ask once). [retry] changing asks again at once.
 */
@Composable
private fun rememberTx(txid: String, base: String, retry: Int, live: Boolean = true): TxState {
    val state by produceState<TxState>(TxState.Loading, txid, base, retry) {
        var first = true
        while (true) {
            val tx = BitcoinContextDetector.tx(txid, base, force = !first)
            val tip = BitcoinContextDetector.tipHeight(base)
            value = if (tx != null) TxState.Loaded(tx, tip) else if (value is TxState.Loaded) value else TxState.Failed
            first = false
            val loaded = value as? TxState.Loaded ?: break
            if (!live || loaded.confirmations >= SETTLED) break
            delay(REFRESH_MS)
        }
    }
    return state
}

// ---- One transaction, in full ----

/**
 * A transaction in full. In a post, tapping it opens the explorer; [inSheet], it isn't a
 * button itself (so a scroll can't open the browser) and ends with "View on mempool".
 */
@Composable
private fun TxCard(ref: BitcoinEmbedRef.TxRef, note: Event, actions: NoteActions, modifier: Modifier = Modifier, inSheet: Boolean = false) {
    val base = mempoolBase()
    var retry by remember { mutableIntStateOf(0) }
    val state = rememberTx(ref.txid, base, retry)
    val accent = EInk.Accent
    val loaded = state as? TxState.Loaded
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.10f), EInk.Body.copy(alpha = 0.55f))))
            .border(0.75.dp, accent.copy(alpha = 0.38f), CardShape)
            .then(
                if (inSheet && state !is TxState.Failed) Modifier
                else Modifier.clickable(onClickLabel = if (state is TxState.Failed) tr("Try again") else tr("View on Mempool")) {
                    if (state is TxState.Failed) retry++ else actions.openLink(ref.explorerUrl(base), note)
                }
            )
            .semantics { loaded?.let { contentDescription = txDescription(it) } }
            .padding(14.dp)
            .animateContentSize()
    ) {
        TxHeader(ref.txid, state)
        when (state) {
            TxState.Loading -> TxSkeleton()
            TxState.Failed -> Text(
                tr("Couldn't load it from {0}. Tap to try again.", base.removePrefix("https://")),
                color = EInk.Caption, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)
            )
            is TxState.Loaded -> {
                if (state.confirmations < SETTLED) ConfirmationMeter(state.confirmations, Modifier.padding(top = 10.dp))
                Spacer(Modifier.height(12.dp))
                TxFlow(state.tx)
                HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke, modifier = Modifier.padding(top = 10.dp, bottom = 9.dp))
                TxFooter(state.tx, showLink = !inSheet)
                if (inSheet) ExplorerButton { actions.openLink(ref.explorerUrl(base), note) }
            }
        }
    }
}

private fun txDescription(s: TxState.Loaded): String {
    val tx = s.tx
    val status = if (s.confirmations == 0) tr("In the mempool") else tr("{0} confirmations", TxFormat.count(s.confirmations))
    return tr("Bitcoin transaction") + ". " + status + ". " +
        tr("{0} inputs, {1} outputs", tx.inputs.size, tx.outputs.size) + ". " +
        TxFormat.amount(tx.totalOut) + ". " + tr("Fee {0} sat/vB", "%.1f".format(tx.feeRate))
}

@Composable
private fun TxHeader(txid: String, state: TxState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(EInk.Accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) { Icon(Glyphs.BlockCube, contentDescription = null, tint = EInk.Accent, modifier = Modifier.size(18.dp)) }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                TxFormat.shortTxid(txid), color = EInk.Value, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace, maxLines = 1
            )
            Text(
                when (state) {
                    TxState.Loading -> tr("Checking the blockchain…")
                    TxState.Failed -> tr("Bitcoin transaction")
                    is TxState.Loaded -> statusLine(state)
                },
                color = EInk.Caption, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (state is TxState.Loaded) ConfirmationChip(state.confirmations, Modifier.padding(start = 8.dp))
    }
}

/** "Block #976,400 · 2h", or "Waiting in the mempool · 12.3 sat/vB". */
private fun statusLine(s: TxState.Loaded): String {
    val tx = s.tx
    if (!tx.confirmed) return tr("Waiting in the mempool") + " · " + "%.1f sat/vB".format(tx.feeRate)
    val block = tx.blockHeight?.let { tr("Block #{0}", "%,d".format(it)) } ?: tr("In a block")
    return block + (tx.blockTime?.let { " · " + relativeTime(it) } ?: "")
}

/** "✓ 1,234 confirmations" in green once settled, "3/6" in the accent before, a pulsing "In mempool" at 0. */
@Composable
private fun ConfirmationChip(confirmations: Int, modifier: Modifier = Modifier) {
    val settled = confirmations >= SETTLED
    val color = when {
        settled -> EInk.Success
        confirmations > 0 -> EInk.Accent
        else -> EInk.Caption
    }
    Row(
        modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.15f)).padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (confirmations == 0) {
            val pulse by rememberInfiniteTransition(label = "mempool").animateFloat(
                0.3f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dot"
            )
            Box(Modifier.size(7.dp).alpha(pulse).clip(CircleShape).background(EInk.Accent))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            when {
                settled -> "✓ " + tr("{0} conf.", TxFormat.count(confirmations))
                confirmations > 0 -> tr("{0}/6 conf.", confirmations)
                else -> tr("In mempool")
            },
            color = if (confirmations == 0) EInk.Value else color, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1
        )
    }
}

/** Six segments, one per confirmation, filling as blocks are found; the next one breathes. */
@Composable
private fun ConfirmationMeter(confirmations: Int, modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "meter").animateFloat(
        0.25f, 0.7f, infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "next"
    )
    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(SETTLED) { i ->
                val color = when {
                    i < confirmations -> EInk.Accent
                    i == confirmations -> EInk.Accent.copy(alpha = pulse)
                    else -> EInk.Track
                }
                Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(50)).background(color))
            }
        }
        Text(
            if (confirmations == 0) tr("Not in a block yet. Most wallets count it as settled after 6 confirmations.")
            else tr("{0} of 6 confirmations. About {1} min to go.", confirmations, (SETTLED - confirmations) * 10),
            color = EInk.Caption, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 5.dp)
        )
    }
}

@Composable
private fun TxSkeleton() {
    val shimmer by rememberInfiniteTransition(label = "skeleton").animateFloat(
        0.35f, 0.8f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "alpha"
    )
    Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        repeat(3) { i ->
            Row {
                Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(50)).background(EInk.Track.copy(alpha = shimmer)))
                Spacer(Modifier.width(if (i == 1) 60.dp else 36.dp))
                Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(50)).background(EInk.Track.copy(alpha = shimmer)))
            }
        }
    }
}

// ---- Inputs → outputs ----

/**
 * Inputs on the left flowing through the transaction (a dot) into outputs on the right, each
 * curve as thick as its share of the amount. Long sides fold into "N more", which unfolds
 * when tapped. The curves draw themselves in once, when the card first loads.
 */
@Composable
private fun TxFlow(tx: TxDetails) {
    var expanded by rememberSaveable(tx.txid) { mutableStateOf(false) }
    val max = if (expanded) 40 else FLOW_ROWS
    val (inRows, inMore) = TxFormat.visibleRows(tx.inputs, max)
    val (outRows, outMore) = TxFormat.visibleRows(tx.outputs, max)
    val leftCount = inRows.size + if (inMore != null) 1 else 0
    val rightCount = outRows.size + if (outMore != null) 1 else 0
    val rows = maxOf(leftCount, rightCount, 1)
    val height = RowHeight * rows

    val draw = remember(tx.txid) { Animatable(0f) }
    LaunchedEffect(tx.txid) { draw.animateTo(1f, tween(750, easing = FastOutSlowInEasing)) }

    Row(Modifier.fillMaxWidth()) {
        Text(tr("{0} inputs", TxFormat.count(tx.inputs.size)), color = EInk.Caption, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(
            tr("{0} outputs", TxFormat.count(tx.outputs.size)), color = EInk.Caption, fontSize = 11.sp, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End, modifier = Modifier.weight(1f)
        )
    }
    Row(Modifier.fillMaxWidth().height(height).padding(top = 2.dp)) {
        FlowColumn(inRows, inMore, leftCount, rows, start = true, onMore = { expanded = true }, modifier = Modifier.weight(1f))
        val inTotal = tx.inputs.sumOf { it.sats }.coerceAtLeast(1)
        val outTotal = tx.outputs.sumOf { it.sats }.coerceAtLeast(1)
        val inShares = inRows.map { it.sats.toFloat() / inTotal } + listOfNotNull(inMore?.let { it.sats.toFloat() / inTotal })
        val outShares = outRows.map { it.sats.toFloat() / outTotal } + listOfNotNull(outMore?.let { it.sats.toFloat() / outTotal })
        FlowLines(
            inShares = if (tx.isCoinbase) listOf(1f) else inShares,
            outShares = outShares,
            rows = rows,
            progress = draw.value,
            modifier = Modifier.width(54.dp).height(height)
        )
        FlowColumn(outRows, outMore, rightCount, rows, start = false, onMore = { expanded = true }, modifier = Modifier.weight(1f))
    }
}

/** One side's rows, centred against the taller side so the curves meet in the middle. */
@Composable
private fun FlowColumn(rows: List<TxIo>, more: TxFormat.More?, count: Int, of: Int, start: Boolean, onMore: () -> Unit, modifier: Modifier) {
    Column(modifier.padding(top = RowHeight * ((of - count) / 2f))) {
        rows.forEach { IoRow(it, start) }
        more?.let { m ->
            Column(
                Modifier.fillMaxWidth().height(RowHeight).clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClickLabel = tr("Show all")) { onMore() },
                horizontalAlignment = if (start) Alignment.Start else Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(tr("+{0} more", TxFormat.count(m.count)), color = EInk.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(TxFormat.amount(m.sats), color = EInk.Caption, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
            }
        }
    }
}

@Composable
private fun IoRow(io: TxIo, start: Boolean) {
    val align = if (start) Alignment.Start else Alignment.End
    Column(Modifier.fillMaxWidth().height(RowHeight), horizontalAlignment = align, verticalArrangement = Arrangement.Center) {
        Text(
            when (io.kind) {
                TxIo.Kind.ADDRESS -> TxFormat.shortAddress(io.address!!)
                TxIo.Kind.COINBASE -> tr("New coins (coinbase)")
                TxIo.Kind.OP_RETURN -> tr("OP_RETURN data")
                TxIo.Kind.SCRIPT -> tr("Script")
            },
            color = if (io.kind == TxIo.Kind.ADDRESS) EInk.Value else EInk.Label,
            fontSize = 12.sp, fontFamily = if (io.kind == TxIo.Kind.ADDRESS) FontFamily.Monospace else FontFamily.Default,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        if (io.kind != TxIo.Kind.COINBASE && !(io.kind == TxIo.Kind.OP_RETURN && io.sats == 0L)) Text(
            TxFormat.amount(io.sats), color = EInk.Caption, fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1
        )
    }
}

/**
 * The curves: from the middle of each input row to a node in the middle, and from there to each
 * output row. Thickness and strength follow each row's share of its side's total.
 */
@Composable
private fun FlowLines(inShares: List<Float>, outShares: List<Float>, rows: Int, progress: Float, modifier: Modifier) {
    val accent = EInk.Accent
    val ring = EInk.Background
    val thin = 1.5.dp
    val thick = 7.dp
    Canvas(modifier) {
        val rowH = size.height / rows
        val center = Offset(size.width / 2, size.height / 2)
        fun ys(n: Int) = (0 until n).map { i -> rowH * ((rows - n) / 2f + i + 0.5f) }
        fun stroke(share: Float) = lerp(thin, thick, share.coerceIn(0f, 1f).let { kotlin.math.sqrt(it) }).toPx()
        val inP = (progress * 2f).coerceIn(0f, 1f)
        val outP = (progress * 2f - 1f).coerceIn(0f, 1f)
        val measure = PathMeasure()
        fun drawCurve(from: Offset, to: Offset, share: Float, p: Float) {
            if (p <= 0f) return
            val full = Path().apply {
                moveTo(from.x, from.y)
                cubicTo((from.x + to.x) / 2, from.y, (from.x + to.x) / 2, to.y, to.x, to.y)
            }
            val part = if (p >= 1f) full else Path().also { out ->
                measure.setPath(full, false)
                measure.getSegment(0f, measure.length * p, out, true)
            }
            drawPath(
                part, accent.copy(alpha = 0.30f + 0.55f * share.coerceIn(0f, 1f)),
                style = Stroke(width = stroke(share), cap = StrokeCap.Round)
            )
        }
        ys(inShares.size).forEachIndexed { i, y -> drawCurve(Offset(0f, y), center, inShares[i], inP) }
        ys(outShares.size).forEachIndexed { i, y -> drawCurve(center, Offset(size.width, y), outShares[i], outP) }
        // The transaction itself.
        drawCircle(ring, radius = 7.dp.toPx() * progress.coerceAtLeast(0.01f), center = center)
        drawCircle(accent, radius = 4.5.dp.toPx() * progress.coerceAtLeast(0.01f), center = center)
    }
}

@Composable
private fun TxFooter(tx: TxDetails, showLink: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(tr("Total out"), color = EInk.Caption, fontSize = 11.sp)
            Text(TxFormat.amount(tx.totalOut), color = EInk.Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(if (tx.isCoinbase) tr("Block reward") else tr("Fee"), color = EInk.Caption, fontSize = 11.sp)
            Text(
                if (tx.isCoinbase) "—" else "%.1f sat/vB".format(tx.feeRate) + " · " + TxFormat.amount(tx.feeSats),
                color = EInk.Label, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1
            )
        }
    }
    if (showLink) Text(
        tr("View on mempool ›"), color = EInk.Caption, fontSize = 11.sp,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.End
    )
}

/** The sheet's way out to the explorer: a full-width accent button. */
@Composable
private fun ExplorerButton(onClick: () -> Unit) {
    Row(
        Modifier
            .padding(top = 14.dp)
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(50))
            .background(EInk.Accent)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(tr("View on mempool"), color = EInk.OnAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Icon(Glyphs.OpenInBrowser, contentDescription = null, tint = EInk.OnAccent, modifier = Modifier.padding(start = 8.dp).size(16.dp))
    }
}

// ---- Several transactions ----

/** How many a stack lists before "Show all". */
private const val STACK_ROWS = 3

/**
 * Several transactions: a header with how many and their total, one compact row each (txid,
 * amount, inputs → outputs, confirmations), and "Show all N" opening every one in full.
 * Tapping a row opens the sheet at that transaction.
 */
@Composable
private fun TxStack(txs: List<BitcoinEmbedRef.TxRef>, note: Event, actions: NoteActions, modifier: Modifier) {
    val base = mempoolBase()
    var sheetAt by remember { mutableStateOf<Int?>(null) }
    val accent = EInk.Accent
    val states = txs.map { rememberTx(it.txid, base, 0) }
    val loaded = states.filterIsInstance<TxState.Loaded>()
    Column(
        modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(CardShape)
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.10f), EInk.Body.copy(alpha = 0.55f))))
            .border(0.75.dp, accent.copy(alpha = 0.38f), CardShape)
            .animateContentSize()
    ) {
        Row(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(9.dp)).background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) { Icon(Glyphs.BlockCube, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp)) }
            Text(
                tr("{0} transactions", txs.size), color = EInk.Value, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 10.dp).weight(1f)
            )
            AnimatedVisibility(loaded.size == txs.size, enter = fadeIn()) {
                Text(
                    TxFormat.amount(loaded.sumOf { it.tx.totalOut }), color = accent, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
                )
            }
        }
        txs.take(STACK_ROWS).forEachIndexed { i, ref ->
            if (i > 0) HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke, modifier = Modifier.padding(horizontal = 14.dp))
            CompactTxRow(ref, states[i]) { sheetAt = i }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 10.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.12f))
                .clickable(role = Role.Button) { sheetAt = 0 }
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (txs.size > STACK_ROWS) tr("Show all {0} transactions", txs.size) else tr("Show details"),
                color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold
            )
        }
    }
    sheetAt?.let { start -> TxSheet(txs, start, note, actions) { sheetAt = null } }
}

@Composable
private fun CompactTxRow(ref: BitcoinEmbedRef.TxRef, state: TxState, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClickLabel = tr("Show details"), onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(TxFormat.shortTxid(ref.txid), color = EInk.Value, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1)
            Text(
                when (state) {
                    TxState.Loading -> tr("Checking the blockchain…")
                    TxState.Failed -> tr("Couldn't load")
                    is TxState.Loaded -> tr("{0} in → {1} out", TxFormat.count(state.tx.inputs.size), TxFormat.count(state.tx.outputs.size)) +
                        " · " + "%.1f sat/vB".format(state.tx.feeRate)
                },
                color = EInk.Caption, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (state is TxState.Loaded) {
            Column(horizontalAlignment = Alignment.End) {
                Text(TxFormat.amount(state.tx.totalOut), color = EInk.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1)
                Spacer(Modifier.height(3.dp))
                ConfirmationChip(state.confirmations)
            }
        }
        Icon(Glyphs.Chevron, contentDescription = null, tint = EInk.Caption, modifier = Modifier.padding(start = 6.dp).size(18.dp))
    }
}

/** Every transaction of a note in full, one after another, opened at [start]. */
@Composable
private fun TxSheet(txs: List<BitcoinEmbedRef.TxRef>, start: Int, note: Event, actions: NoteActions, onDismiss: () -> Unit) {
    BlockSheet(onDismiss = onDismiss) {
        Text(
            if (txs.size == 1) tr("Transaction") else tr("Transactions in this post"),
            color = EInk.Value, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            (if (txs.size == 1) "" else tr("{0} transactions", txs.size) + " · ") + tr("from {0}", mempoolBase().removePrefix("https://")),
            color = EInk.Caption, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        val list = rememberLazyListState(initialFirstVisibleItemIndex = start.coerceIn(0, txs.lastIndex))
        LazyColumn(state = list, modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp)) {
            itemsIndexed(txs, key = { _, t -> t.txid }) { i, ref ->
                if (txs.size > 1) Text(
                    tr("{0} of {1}", i + 1, txs.size), color = EInk.Caption, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 18.dp, top = 12.dp)
                )
                TxCard(ref, note, actions, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), inSheet = true)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ---- One row in a post ----

/**
 * Transactions as one thin row: the first one's txid, inputs → outputs, amount and
 * confirmations; for several, a second line "+N more transactions · total". Tapping opens
 * the sheet with every one in full.
 */
@Composable
private fun TxRowCard(txs: List<BitcoinEmbedRef.TxRef>, note: Event, actions: NoteActions, modifier: Modifier) {
    val base = mempoolBase()
    var sheetAt by remember { mutableStateOf<Int?>(null) }
    val states = txs.map { rememberTx(it.txid, base, 0, live = false) }
    val loaded = states.filterIsInstance<TxState.Loaded>()
    Column(modifier.padding(top = 8.dp).fillMaxWidth().embedFrame().animateContentSize()) {
        CompactTxRow(txs[0], states[0]) { sheetAt = 0 }
        if (txs.size > 1) {
            HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke, modifier = Modifier.padding(horizontal = 14.dp))
            Row(
                Modifier.fillMaxWidth().clickable(onClickLabel = tr("Show all")) { sheetAt = 1 }.padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    tr("+{0} more transactions", txs.size - 1), color = EInk.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (loaded.size == txs.size) Text(
                    tr("Total {0}", TxFormat.amount(loaded.sumOf { it.tx.totalOut })), color = EInk.Caption, fontSize = 11.sp, fontFamily = FontFamily.Monospace
                )
                Icon(Glyphs.Chevron, contentDescription = null, tint = EInk.Caption, modifier = Modifier.padding(start = 6.dp).size(16.dp))
            }
        }
    }
    sheetAt?.let { start -> TxSheet(txs, start, note, actions) { sheetAt = null } }
}

/** The accent-tinted frame every Bitcoin card shares. */
@Composable
private fun Modifier.embedFrame(): Modifier {
    val accent = EInk.Accent
    return this
        .clip(CardShape)
        .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.10f), EInk.Body.copy(alpha = 0.55f))))
        .border(0.75.dp, accent.copy(alpha = 0.38f), CardShape)
}

// ---- A block ----

/** A block as one thin row (height, transactions, size, miner); tapping opens its details. */
@Composable
private fun BlockRowCard(ref: BitcoinEmbedRef.BlockRef, note: Event, actions: NoteActions, modifier: Modifier) {
    val base = mempoolBase()
    var open by remember { mutableStateOf(false) }
    val block by produceState<com.example.btcwidget.BlockDetails?>(null, ref.key, base) { value = BitcoinContextDetector.block(ref, base) }
    val b = block
    Row(
        modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .embedFrame()
            .clickable(onClickLabel = tr("Show details")) { open = true }
            .heightIn(min = 56.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(EInk.Accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) { Icon(Glyphs.BlockCube, contentDescription = null, tint = EInk.Accent, modifier = Modifier.size(17.dp)) }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(blockTitle(ref, b), color = EInk.Value, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1)
            Text(
                if (b == null) tr("Checking the blockchain…")
                else listOfNotNull(
                    tr("{0} txs", TxFormat.count(b.txCount)),
                    com.example.btcwidget.formatBytes(b.sizeBytes),
                    b.extras?.pool
                ).joinToString(" · "),
                color = EInk.Caption, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        b?.let { Text(relativeTime(it.timestamp), color = EInk.Caption, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp)) }
        Icon(Glyphs.Chevron, contentDescription = null, tint = EInk.Caption, modifier = Modifier.padding(start = 4.dp).size(18.dp))
    }
    if (open) BlockDetailsSheet(ref, note, actions) { open = false }
}

private fun blockTitle(ref: BitcoinEmbedRef.BlockRef, b: com.example.btcwidget.BlockDetails?): String =
    tr("Block #{0}", b?.height?.let { "%,d".format(it) } ?: ref.heightOrHash.toIntOrNull()?.let { "%,d".format(it) } ?: TxFormat.shortTxid(ref.heightOrHash))

/**
 * A block in full: when it was found and how deep it is now, how full it is, then its numbers
 * (transactions, size, fees, reward, miner, difficulty; the ones the server gives), its hash to
 * copy, and "View on mempool".
 */
@Composable
private fun BlockDetailsSheet(ref: BitcoinEmbedRef.BlockRef, note: Event, actions: NoteActions, onDismiss: () -> Unit) {
    val base = mempoolBase()
    var retry by remember { mutableIntStateOf(0) }
    val state by produceState<Pair<com.example.btcwidget.BlockDetails?, Int?>?>(null, ref.key, base, retry) {
        val b = BitcoinContextDetector.block(ref, base)
        value = b to BitcoinContextDetector.tipHeight(base)
    }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    BlockSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp).animateContentSize()) {
            val b = state?.first
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(EInk.Accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Glyphs.BlockCube, contentDescription = null, tint = EInk.Accent, modifier = Modifier.size(22.dp)) }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(blockTitle(ref, b), color = EInk.Value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        b?.let {
                            java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it.timestamp * 1000)) +
                                " · " + relativeTime(it.timestamp)
                        } ?: if (state == null) tr("Checking the blockchain…") else "",
                        color = EInk.Caption, fontSize = 13.sp
                    )
                }
                if (b != null) ConfirmationChip(b.confirmations(state?.second))
            }
            when {
                state == null -> TxSkeleton()
                b == null -> Text(
                    tr("Couldn't load it from {0}. Tap to try again.", base.removePrefix("https://")),
                    color = EInk.Caption, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 16.dp).clickable { retry++ }
                )
                else -> {
                    // How full: weight against the 4 MWU limit.
                    Row(Modifier.padding(top = 18.dp)) {
                        Text(tr("Block space"), color = EInk.Caption, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text(tr("{0}% full", "%.1f".format(b.fullness * 100)), color = EInk.Label, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                    Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(EInk.Track)) {
                        Box(Modifier.fillMaxWidth(b.fullness).height(8.dp).clip(RoundedCornerShape(50)).background(EInk.Accent))
                    }
                    val x = b.extras
                    val stats = listOfNotNull(
                        tr("Transactions") to TxFormat.count(b.txCount),
                        tr("Size") to com.example.btcwidget.formatBytes(b.sizeBytes),
                        x?.medianFee?.let { tr("Median fee") to "%.1f sat/vB".format(it) },
                        x?.feeRange?.let { (lo, hi) -> tr("Fee range") to "%.0f–%.0f sat/vB".format(lo, hi) },
                        x?.totalFeesSats?.let { tr("Total fees") to TxFormat.amount(it) },
                        x?.rewardSats?.let { tr("Reward") to TxFormat.amount(it) },
                        x?.pool?.let { tr("Miner") to it },
                        tr("Difficulty") to com.example.btcwidget.formatDifficulty(b.difficulty)
                    )
                    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        stats.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { (label, value) -> StatTile(label, value, Modifier.weight(1f)) }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    // The hash, to copy.
                    Row(
                        Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(EInk.Body)
                            .clickable(onClickLabel = tr("Copy")) { clipboard.setText(androidx.compose.ui.text.AnnotatedString(b.hash)) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("Hash"), color = EInk.Caption, fontSize = 11.sp)
                            Text(b.hash.take(16) + "…" + b.hash.takeLast(12), color = EInk.Value, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                        }
                        Icon(Glyphs.Copy, contentDescription = tr("Copy"), tint = EInk.Caption, modifier = Modifier.size(18.dp))
                    }
                    ExplorerButton { actions.openLink(ref.explorerUrl(base), note) }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(EInk.Body).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(label, color = EInk.Caption, fontSize = 11.sp, maxLines = 1)
        Text(value, color = EInk.Value, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---- Settings ----

/**
 * Settings › Display › Bitcoin in posts: transaction and block cards on or off, and the
 * transaction style for timelines and for a post's page.
 */
@Composable
internal fun BitcoinCardsSettings() {
    val context = LocalContext.current
    SettingsHeader(tr("Bitcoin in posts"))
    SettingsNote(tr("Notes that link to a transaction or block get a card with live data from your mempool server."))
    SettingsSwitchRow(
        tr("Transaction cards"),
        summary = tr("Confirmations, amount, inputs and outputs, fee"),
        checked = BitcoinCards.showTx
    ) { BitcoinCards.setShowTx(context, it) }
    AnimatedVisibility(BitcoinCards.showTx) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 6.dp)) {
            StyleChoice(tr("In timelines"), BitcoinCards.feedStyle) { BitcoinCards.setFeedStyle(context, it) }
            StyleChoice(tr("On a post's page"), BitcoinCards.postStyle) { BitcoinCards.setPostStyle(context, it) }
        }
    }
    SettingsSwitchRow(
        tr("Block cards"),
        summary = tr("Height, transactions, size and miner; tap for the details"),
        checked = BitcoinCards.showBlocks
    ) { BitcoinCards.setShowBlocks(context, it) }
}

/** "Single row | Full card" for one place, with what the chosen one means under it. */
@Composable
private fun StyleChoice(label: String, selected: TxCardStyle, onPick: (TxCardStyle) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = EInk.Value, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(EInk.Body).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                TxCardStyle.entries.forEach { style ->
                    val on = style == selected
                    Text(
                        tr(style.label),
                        color = if (on) EInk.OnAccent else EInk.Label,
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (on) EInk.Accent else Color.Transparent)
                            .clickable(role = Role.RadioButton) { onPick(style) }
                            .semantics { this.selected = on }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
        Text(tr(selected.summary), color = EInk.Caption, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}
