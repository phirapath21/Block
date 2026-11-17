package com.example.btcwidget.ui.main

import com.example.btcwidget.ui.social.tr
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.BlockclockMetrics
import com.example.btcwidget.ChartMath
import com.example.btcwidget.ChartRange
import com.example.btcwidget.PricePoint
import com.example.btcwidget.alerts.PriceAlerts
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * BTC price over the last 24 hours or 7 days.
 *
 * A single series, so no legend: the header names it and carries the value. Drag (or
 * press) across the plot for a crosshair that snaps to the nearest hour; the header then
 * shows that hour's price and time. The line is 2dp with a faint area wash, an end dot
 * marks the latest price, and High/Low sit under the plot instead of gridlines.
 *
 * @param history hourly BTC/USD closes, oldest first.
 * @param usdToCurrency multiplier from USD to [currency] (1 for USD).
 */
@Composable
fun PriceChartPanel(
    history: List<PricePoint>,
    currency: String,
    usdToCurrency: Double,
    modifier: Modifier = Modifier,
    onRangeSelected: (ChartRange) -> Unit = {},
    historyLoading: Boolean = false
) {
    var range by rememberSaveable { mutableStateOf(ChartRange.DAY) }
    val points = remember(history, range, usdToCurrency) { ChartMath.slice(history, range, usdToCurrency) }
    val requiredSeconds = if (range == ChartRange.ALL) 0L else range.hours * 3600L
    val coverageSeconds = (points.lastOrNull()?.time ?: 0L) - (points.firstOrNull()?.time ?: 0L)
    val hasCoverage = points.isNotEmpty() && (range == ChartRange.ALL || coverageSeconds >= requiredSeconds * 0.8)
    val summary = remember(points, hasCoverage) { if (hasCoverage) ChartMath.summary(points) else null }
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    val timeFormat = remember { SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()) }
    fun price(value: Double) = PriceAlerts.formatPrice(value, currency)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(EInk.Body, EInk.BodyShape)
            .border(0.5.dp, EInk.BodyStroke, EInk.BodyShape)
            .padding(16.dp)
    ) {
        // Header: value leads, context follows. While scrubbing it shows the picked hour.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                val pick = selected?.let { points.getOrNull(it) }
                Text(
                    text = when {
                        pick != null -> price(pick.price)
                        summary != null -> price(summary.last)
                        else -> tr("BTC price")
                    },
                    color = EInk.Value,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when {
                        pick != null -> timeFormat.format(Date(pick.time * 1000))
                        summary != null -> listOfNotNull(
                            BlockclockMetrics.formatChange(summary.changePct),
                            tr("Past {0}", range.label)
                        ).joinToString(" · ")
                        else -> tr("History appears after the first refresh")
                    },
                    color = EInk.Caption,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ChartRange.entries.forEach { option ->
                EInkChip(text = option.label, selected = range == option, onClick = { range = option; onRangeSelected(option) })
            }
        }

        if (historyLoading) Text(tr("Loading price history…"), color = EInk.Caption, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        else if (!hasCoverage) Text(tr("Price history for this range is unavailable"), color = EInk.Caption, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        if (summary == null) return@Column

        val description = tr("BTC price, past {0}: from {1} to {2}, ", range.label, price(summary.first), price(summary.last)) +
            "high ${price(summary.high)}, low ${price(summary.low)}"

        Canvas(
            modifier = Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .height(124.dp)
                .semantics { contentDescription = description }
                .pointerInput(points) {
                    fun pick(x: Float) {
                        val index = ChartMath.nearestIndex(points, x, size.width.toFloat())
                        if (index != selected) selected = index
                    }
                    detectHorizontalDragGestures(
                        onDragStart = { pick(it.x) },
                        onDragEnd = { selected = null },
                        onDragCancel = { selected = null },
                        onHorizontalDrag = { change, _ -> pick(change.position.x) }
                    )
                }
                .pointerInput(points) {
                    detectTapGestures(onPress = { offset ->
                        selected = ChartMath.nearestIndex(points, offset.x, size.width.toFloat())
                        tryAwaitRelease()
                        selected = null
                    })
                }
        ) {
            val w = size.width
            val h = size.height
            fun at(i: Int) = Offset(
                ChartMath.x(points, i, w),
                ChartMath.y(points[i].price, summary.low, summary.high, h)
            )

            val line = Path().apply {
                moveTo(at(0).x, at(0).y)
                for (i in 1 until points.size) lineTo(at(i).x, at(i).y)
            }
            val area = Path().apply {
                addPath(line)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(area, color = EInk.Accent.copy(alpha = 0.14f))
            drawPath(
                line,
                color = EInk.Accent,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Latest price: end dot with a surface ring so it stands off the line.
            val end = at(points.lastIndex)
            drawCircle(EInk.Body, radius = 6.dp.toPx(), center = end)
            drawCircle(EInk.Accent, radius = 4.dp.toPx(), center = end)

            selected?.let { i ->
                val p = at(i)
                drawLine(EInk.Caption, Offset(p.x, 0f), Offset(p.x, h), strokeWidth = 1.dp.toPx())
                drawCircle(EInk.Body, radius = 6.dp.toPx(), center = p)
                drawCircle(EInk.Accent, radius = 4.dp.toPx(), center = p)
            }
        }

        Row(modifier = Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            ChartStat("H", price(summary.high))
            Spacer(modifier = Modifier.width(16.dp))
            ChartStat("L", price(summary.low))
            Spacer(modifier = Modifier.weight(1f))
            if (currency != "USD") {
                Text(tr("Converted from USD"), color = EInk.Caption, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ChartStat(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = EInk.Caption, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(
            text = " $value",
            color = EInk.Value,
            fontSize = 12.sp
        )
    }
}
