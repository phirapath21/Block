package com.example.btcwidget.ui.main

import com.example.btcwidget.ui.social.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.BlockclockMetrics
import com.example.btcwidget.PriceData
import com.example.btcwidget.PricePoint
import com.example.btcwidget.ShareText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Metrics that can be pinned to the large panel at the top of the dashboard. */
private val HERO_CHOICES = listOf("BLOCK", "PRICE", "HALVING", "SATS", "FEES")

/**
 * The dashboard: one large panel for the chosen metric and the BTC price chart.
 * All metric panels (with reordering and widget switches) live in the Widgets tab.
 */
@Composable
fun DashboardContent(
    data: PriceData,
    heroMetric: String,
    onHeroMetricSelected: (String) -> Unit,
    currencySetting: String,
    stackSats: Long = 0,
    stackUnit: com.example.btcwidget.StackUnit = com.example.btcwidget.StackUnit.BTC,
    stackHidden: Boolean = false,
    onToggleStackHidden: () -> Unit = {},
    onStackChange: (Long, com.example.btcwidget.StackUnit) -> Unit = { _, _ -> },
    onSetUpStack: () -> Unit = {},
    history: List<PricePoint> = emptyList(),
    onChartRangeSelected: (com.example.btcwidget.ChartRange) -> Unit = {},
    historyLoading: Boolean = false,
    /** Shares the hero metric (key) through the system share sheet. */
    onShare: (String) -> Unit = {},
    /** Opens Block's own composer with the hero image and generated text. */
    onPostToNostr: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val hasStack = stackSats > 0
    // A cleared stack can't stay the hero.
    val hero = if (heroMetric == "STACK" && !hasStack) "PRICE" else heroMetric

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "header") { HeaderSection(title = "Block", subtitle = tr("Bitcoin at a glance")) }

        if (!data.lastFetchSuccessful) {
            item(key = "offline") {
                Text(
                    text = tr("Offline · showing cached data"),
                    color = EInk.Alert,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(EInk.Body, EInk.BodyShape)
                        .border(1.dp, EInk.Alert, EInk.BodyShape)
                        .padding(vertical = 10.dp, horizontal = 14.dp)
                )
            }
        }

        // Hero panel, same proportions as a 2x1 widget.
        item(key = "hero") {
            val isStackHero = hero == "STACK"
            val display = BlockclockMetrics.forKey(hero, data, currencySetting, stackSats)
            EInkDevice(
                display = if (isStackHero && stackHidden) display.copy(value = "••••••", suffix = "", caption = tr("Tap to reveal")) else display,
                aspectRatio = 2f,
                stale = !data.lastFetchSuccessful,
                progress = if (hero == "HALVING") BlockclockMetrics.halvingProgress(data.blockHeight).toFloat() else null,
                modifier = if (isStackHero) Modifier.clickable(
                    onClickLabel = tr(if (stackHidden) "Show stack value" else "Hide stack value"),
                    onClick = onToggleStackHidden
                ) else Modifier
            )
        }

        // Your stack is never shared: holdings stay private.
        if (hero in ShareText.SHAREABLE) {
            item(key = "share") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tr("Share ›"), color = EInk.Caption, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(onClickLabel = tr("Share {0}", BlockclockMetrics.MODE_NAMES.getOrElse(BlockclockMetrics.MODE_KEYS.indexOf(hero)) { hero })) { onShare(hero) }
                            .padding(vertical = 6.dp, horizontal = 4.dp)
                    )
                    Text(
                        text = tr("Post to Nostr →"), color = EInk.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(onClickLabel = tr("Prepare a Nostr post")) { onPostToNostr(hero) }
                            .padding(vertical = 6.dp, horizontal = 4.dp)
                    )
                }
            }
        }

        item(key = "hero_choices") {
            val choices = if (hasStack) listOf("STACK") + HERO_CHOICES else HERO_CHOICES
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                choices.forEach { key ->
                    EInkChip(text = key, selected = hero == key, onClick = { onHeroMetricSelected(key) })
                }
            }
        }

        item(key = "price_chart") {
            PriceChartPanel(
                history = history,
                currency = currencySetting,
                usdToCurrency = if (currencySetting == "THB" && data.btcUsd > 0) data.btcThb / data.btcUsd else 1.0,
                onRangeSelected = onChartRangeSelected,
                historyLoading = historyLoading
            )
        }

        item(key = "stack_portfolio") {
            PortfolioStackCard(
                data = data,
                currencySetting = currencySetting,
                stackSats = stackSats,
                stackUnit = stackUnit,
                stackHidden = stackHidden,
                onToggleStackHidden = onToggleStackHidden,
                onStackChange = onStackChange
            )
        }

        item(key = "updated") {
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            Text(
                text = tr("Last updated {0}", formatter.format(Date(data.timestamp))),
                color = EInk.Caption,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            )
        }
    }
}

/**
 * Your stack on the Bitcoin page: what it's worth now, and the amount to edit (BTC or sats).
 * Kept on the phone only.
 */
@Composable
private fun PortfolioStackCard(
    data: PriceData,
    currencySetting: String,
    stackSats: Long,
    stackUnit: com.example.btcwidget.StackUnit,
    stackHidden: Boolean,
    onToggleStackHidden: () -> Unit,
    onStackChange: (Long, com.example.btcwidget.StackUnit) -> Unit
) {
    if (stackHidden) {
        EInkPanel(tr("Your stack")) {
            Text(
                tr("Stack amounts are hidden. Tap to reveal."),
                color = EInk.Caption,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = tr("Show stack value"), onClick = onToggleStackHidden)
                    .padding(vertical = 4.dp)
            )
        }
        return
    }
    var unit by remember(stackUnit) { mutableStateOf(stackUnit) }
    var stackText by remember(stackSats, unit) { mutableStateOf(com.example.btcwidget.Stack.toInput(stackSats, unit)) }

    val btcPrice = com.example.btcwidget.alerts.PriceAlerts.price(data, currencySetting)
    val stackWorth = if (stackSats > 0 && btcPrice > 0) {
        com.example.btcwidget.alerts.PriceAlerts.formatPrice((stackSats.toDouble() / com.example.btcwidget.Stack.SATS_PER_BTC) * btcPrice, currencySetting)
    } else null
    val invalidStack = stackText.isNotBlank() && com.example.btcwidget.Stack.parse(stackText, unit) == null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EInk.Body, EInk.BodyShape)
            .border(0.5.dp, EInk.BodyStroke, EInk.BodyShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionLabel(tr("Your stack"))

        if (stackWorth != null) {
            Text(
                text = stackWorth,
                color = EInk.Value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            EInkOutlinedField(
                value = stackText,
                onValueChange = { input ->
                    stackText = input.filter { it.isDigit() || it == '.' || it == ',' }.take(20)
                    if (stackText.isBlank()) onStackChange(0L, unit)
                    else com.example.btcwidget.Stack.parse(stackText, unit)?.let { onStackChange(it, unit) }
                },
                placeholder = if (unit == com.example.btcwidget.StackUnit.BTC) "0.05" else "5000000",
                prefix = tr("Stack"),
                suffix = if (unit == com.example.btcwidget.StackUnit.BTC) "BTC" else "sats",
                isError = invalidStack,
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f)
            )
            com.example.btcwidget.StackUnit.entries.forEach { option ->
                EInkChip(
                    text = if (option == com.example.btcwidget.StackUnit.BTC) "BTC" else "SATS",
                    selected = unit == option,
                    onClick = {
                        if (unit != option) {
                            val sats = com.example.btcwidget.Stack.parse(stackText, unit) ?: stackSats
                            unit = option
                            stackText = com.example.btcwidget.Stack.toInput(sats, option)
                            onStackChange(sats, option)
                        }
                    }
                )
            }
        }
        if (invalidStack) {
            Text(
                text = if (unit == com.example.btcwidget.StackUnit.BTC) tr("Up to 8 decimals, max 21,000,000") else tr("Whole sats only"),
                color = EInk.Alert,
                fontSize = 12.sp
            )
        }
    }
}

/** Unified X-style input field with optional prefix and suffix labels. */
@Composable
private fun EInkOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    suffix: String? = null,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        isError = isError,
        placeholder = { Text(placeholder, fontSize = 14.sp) },
        prefix = prefix?.let { p -> { Text("$p ", fontSize = 13.sp) } },
        suffix = suffix?.let { s -> { Text(" $s", fontSize = 13.sp) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = TextStyle(fontSize = 15.sp, color = EInk.Value),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = if (isError) EInk.Alert else EInk.Accent,
            unfocusedBorderColor = if (isError) EInk.Alert else EInk.BodyStroke,
            errorBorderColor = EInk.Alert,
            focusedTextColor = EInk.Value,
            unfocusedTextColor = EInk.Value,
            errorTextColor = EInk.Value,
            cursorColor = EInk.Accent,
            focusedPrefixColor = EInk.Caption,
            unfocusedPrefixColor = EInk.Caption,
            focusedSuffixColor = EInk.Caption,
            unfocusedSuffixColor = EInk.Caption,
            focusedPlaceholderColor = EInk.Caption,
            unfocusedPlaceholderColor = EInk.Caption
        ),
        modifier = modifier
    )
}
