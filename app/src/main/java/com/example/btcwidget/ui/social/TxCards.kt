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

/**
 * The Bitcoin card(s) under a note: one block; one transaction in full (confirmations, inputs
 * flowing to outputs, fee); or, for several, a compact list with "Show all" opening every one
 * in full.
 */
@Composable
fun BitcoinEmbeds(refs: List<BitcoinEmbedRef>, note: Event, actions: NoteActions, modifier: Modifier = Modifier) {
    val txs = refs.filterIsInstance<BitcoinEmbedRef.TxRef>()
    when {
        txs.size >= 2 -> TxStack(txs, note, actions, modifier)
        txs.size == 1 -> TxCard(txs[0], note, actions, modifier.padding(top = 8.dp))
        else -> refs.filterIsInstance<BitcoinEmbedRef.BlockRef>().firstOrNull()?.let { BlockCard(it, note, actions, modifier) }
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
 * deep, so a card watched from the mempool ticks up to "6 confirmations" by itself.
 * [retry] changing asks again at once.
 */
@Composable
private fun rememberTx(txid: String, base: String, retry: Int): TxState {
    val state by produceState<TxState>(TxState.Loading, txid, base, retry) {
        var first = true
        while (true) {
            val tx = BitcoinContextDetector.tx(txid, base, force = !first)
            val tip = BitcoinContextDetector.tipHeight(base)
            value = if (tx != null) TxState.Loaded(tx, tip) else if (value is TxState.Loaded) value else TxState.Failed
            first = false
            val loaded = value as? TxState.Loaded ?: break
            if (loaded.confirmations >= SETTLED) break
            delay(REFRESH_MS)
        }
    }
    return state
}

// ---- One transaction, in full ----

/** A transaction in full. [framed] = false for the sheet, where each sits on its own. */
@Composable
private fun TxCard(ref: BitcoinEmbedRef.TxRef, note: Event, actions: NoteActions, modifier: Modifier = Modifier, framed: Boolean = true) {
    val base = mempoolBase()
    var retry by remember { mutableIntStateOf(0) }
    val state = rememberTx(ref.txid, base, retry)
    val accent = EInk.Accent
    val loaded = state as? TxState.Loaded
    Column(
        modifier
            .fillMaxWidth()
            .then(
                if (framed) Modifier
                    .clip(CardShape)
                    .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.10f), EInk.Body.copy(alpha = 0.55f))))
                    .border(0.75.dp, accent.copy(alpha = 0.38f), CardShape)
                else Modifier
            )
            .clickable(onClickLabel = tr("View on Mempool")) {
                if (state is TxState.Failed) retry++ else actions.openLink(ref.explorerUrl(base), note)
            }
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
                TxFooter(state.tx)
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
private fun TxFooter(tx: TxDetails) {
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
    Text(
        tr("View on mempool ›"), color = EInk.Caption, fontSize = 11.sp,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.End
    )
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
            tr("Transactions in this post"), color = EInk.Value, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            tr("{0} transactions · from {1}", txs.size, mempoolBase().removePrefix("https://")),
            color = EInk.Caption, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        val list = rememberLazyListState(initialFirstVisibleItemIndex = start.coerceIn(0, txs.lastIndex))
        LazyColumn(state = list, modifier = Modifier.fillMaxWidth().heightIn(max = 640.dp)) {
            itemsIndexed(txs, key = { _, t -> t.txid }) { i, ref ->
                Text(
                    tr("{0} of {1}", i + 1, txs.size), color = EInk.Caption, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 18.dp, top = 12.dp)
                )
                TxCard(ref, note, actions, Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ---- A block ----

@Composable
private fun BlockCard(ref: BitcoinEmbedRef.BlockRef, note: Event, actions: NoteActions, modifier: Modifier) {
    val base = mempoolBase()
    val summary by produceState<MempoolSummary?>(null, ref.key, base) { value = BitcoinContextDetector.block(ref, base) }
    val accent = EInk.Accent
    val s = summary
    Row(
        modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(CardShape)
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.10f), EInk.Body.copy(alpha = 0.55f))))
            .border(0.75.dp, accent.copy(alpha = 0.38f), CardShape)
            .clickable(onClickLabel = tr("View on Mempool")) { actions.openLink(s?.explorerUrl ?: ref.explorerUrl(base), note) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) { Icon(Glyphs.BlockCube, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp)) }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                s?.title ?: tr("Block #{0}", ref.heightOrHash.toIntOrNull()?.let { "%,d".format(it) } ?: TxFormat.shortTxid(ref.heightOrHash)),
                color = EInk.Value, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, maxLines = 1
            )
            Text(s?.subtitle ?: tr("Checking the blockchain…"), color = EInk.Caption, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (s != null) Column(horizontalAlignment = Alignment.End) {
            Text(s.primaryMetric, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(s.secondaryMetric, color = EInk.Label, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
    }
}
