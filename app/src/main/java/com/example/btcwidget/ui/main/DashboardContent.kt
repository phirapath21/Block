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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
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

        item(key = "tx_tracker") {
            MempoolTxTrackerCard(currentBlockHeight = data.blockHeight)
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
 * Stack & DCA / Portfolio P&L card directly on the Bitcoin page:
 * edit total holdings (BTC or sats), set total cost basis, or log a new DCA purchase to add to both.
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
        EInkPanel(tr("Your stack & DCA")) {
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
    val context = androidx.compose.ui.platform.LocalContext.current
    var unit by remember(stackUnit) { mutableStateOf(stackUnit) }
    var stackText by remember(stackSats, unit) { mutableStateOf(com.example.btcwidget.Stack.toInput(stackSats, unit)) }
    var costBasis by remember(currencySetting) { mutableStateOf(com.example.btcwidget.AppPrefs.stackCostBasis(context, currencySetting)) }
    var costText by remember(currencySetting) {
        val saved = com.example.btcwidget.AppPrefs.stackCostBasis(context, currencySetting)
        mutableStateOf(if (saved > 0.0) saved.toBigDecimal().stripTrailingZeros().toPlainString() else "")
    }
    var showDcaAdd by rememberSaveable { mutableStateOf(false) }
    var dcaAmountText by rememberSaveable { mutableStateOf("") }
    var dcaCostText by rememberSaveable { mutableStateOf("") }

    val btcPrice = com.example.btcwidget.alerts.PriceAlerts.price(data, currencySetting)
    val fmt = { v: Double -> com.example.btcwidget.alerts.PriceAlerts.formatPrice(kotlin.math.abs(v), currencySetting) }
    val currencySymbol = if (currencySetting == "THB") "฿" else "$"
    val stackWorth = if (stackSats > 0 && btcPrice > 0) {
        fmt((stackSats.toDouble() / com.example.btcwidget.Stack.SATS_PER_BTC) * btcPrice)
    } else null
    val pnl = com.example.btcwidget.Stack.profitLoss(stackSats, btcPrice, costBasis)
    val invalidStack = stackText.isNotBlank() && com.example.btcwidget.Stack.parse(stackText, unit) == null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EInk.Body, EInk.BodyShape)
            .border(0.5.dp, EInk.BodyStroke, EInk.BodyShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionLabel(tr("Your stack & DCA"))
            EInkChip(
                text = if (showDcaAdd) tr("Close DCA") else tr("+ DCA lot"),
                selected = showDcaAdd,
                onClick = { showDcaAdd = !showDcaAdd }
            )
        }

        if (stackWorth != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stackWorth,
                        color = EInk.Value,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (pnl != null) {
                            tr("Avg buy {0} · Cost {1}", fmt(pnl.avgBuyPrice), fmt(pnl.costBasis))
                        } else {
                            tr("Enter total cost below to track P/L")
                        },
                        color = EInk.Caption,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (pnl != null) {
                    val sign = if (pnl.profitAmount >= 0) "+" else "-"
                    val pnlColor = if (pnl.profitAmount >= 0) EInk.Accent else EInk.Alert
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = "$sign${"%.2f".format(kotlin.math.abs(pnl.profitPercent))}%",
                            color = pnlColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$sign${fmt(pnl.profitAmount)}",
                            color = pnlColor,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
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

        EInkOutlinedField(
            value = costText,
            onValueChange = { input ->
                costText = input.filter { it.isDigit() || it == '.' }.take(18)
                val v = costText.toDoubleOrNull() ?: 0.0
                costBasis = v
                com.example.btcwidget.AppPrefs.setStackCostBasis(context, currencySetting, v)
            },
            placeholder = "0",
            prefix = tr("Cost ({0})", currencySymbol),
            suffix = currencySetting,
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.fillMaxWidth()
        )

        if (showDcaAdd) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EInk.Background, RoundedCornerShape(12.dp))
                    .border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = tr("Add DCA purchase (adds to your stack and total cost)"),
                    color = EInk.Caption,
                    fontSize = 12.sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    EInkOutlinedField(
                        value = dcaAmountText,
                        onValueChange = { dcaAmountText = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(18) },
                        placeholder = "0",
                        prefix = "+",
                        suffix = if (unit == com.example.btcwidget.StackUnit.BTC) "BTC" else "sats",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f)
                    )
                    EInkOutlinedField(
                        value = dcaCostText,
                        onValueChange = { dcaCostText = it.filter { c -> c.isDigit() || c == '.' }.take(18) },
                        placeholder = "0",
                        prefix = "+$currencySymbol",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f)
                    )
                    val addedSats = com.example.btcwidget.Stack.parse(dcaAmountText, unit) ?: 0L
                    val addedCost = dcaCostText.toDoubleOrNull() ?: 0.0
                    EInkChip(
                        text = tr("Add"),
                        selected = addedSats > 0 && addedCost > 0.0,
                        onClick = {
                            if (addedSats <= 0 || addedCost <= 0.0) return@EInkChip
                            val newSats = (stackSats + addedSats).coerceAtMost(21_000_000L * com.example.btcwidget.Stack.SATS_PER_BTC)
                            val newCost = costBasis + addedCost
                            onStackChange(newSats, unit)
                            stackText = com.example.btcwidget.Stack.toInput(newSats, unit)
                            costBasis = newCost
                            costText = newCost.toBigDecimal().stripTrailingZeros().toPlainString()
                            com.example.btcwidget.AppPrefs.setStackCostBasis(context, currencySetting, newCost)
                            dcaAmountText = ""
                            dcaCostText = ""
                            showDcaAdd = false
                        }
                    )
                }
            }
        }
    }
}

/** Tracks a Bitcoin transaction (TXID) on mempool.space: confirmation status, block height, and fee rate. */
@Composable
private fun MempoolTxTrackerCard(currentBlockHeight: Int) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var txid by rememberSaveable { mutableStateOf("") }
    var statusText by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EInk.Body, EInk.BodyShape)
            .border(0.5.dp, EInk.BodyStroke, EInk.BodyShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionLabel(tr("Mempool TX tracker"))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EInkOutlinedField(
                value = txid,
                onValueChange = { txid = it.trim().lowercase().filter { c -> c in '0'..'9' || c in 'a'..'f' }.take(64) },
                placeholder = tr("Paste 64-char TXID…"),
                modifier = Modifier.weight(1f)
            )
            EInkChip(
                text = if (loading) "…" else tr("Check"),
                selected = txid.length == 64 && !loading,
                onClick = {
                    if (txid.length != 64 || loading) return@EInkChip
                    loading = true
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val baseUrl = com.example.btcwidget.AppPrefs.mempoolUrl(context).ifBlank { "https://mempool.space" }
                        val summary = fetchTxStatus(txid, currentBlockHeight, baseUrl)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            statusText = summary
                            loading = false
                        }
                    }
                }
            )
        }
        statusText?.let { msg ->
            Text(
                text = msg,
                color = EInk.Value,
                fontSize = 13.sp
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

private fun fetchTxStatus(txid: String, currentBlockHeight: Int, baseUrl: String): String = runCatching {
    val req = okhttp3.Request.Builder()
        .url("${baseUrl.trimEnd('/')}/api/tx/$txid")
        .header("Accept", "application/json")
        .build()
    com.example.btcwidget.nostr.RelayPool.defaultClient.newCall(req).execute().use { r ->
        if (r.code == 404) return@runCatching "Not found in mempool or blockchain"
        if (!r.isSuccessful) return@runCatching "HTTP ${r.code}"
        val json = org.json.JSONObject(r.body.string())
        val fee = json.optLong("fee", 0L)
        val weight = json.optLong("weight", 0L)
        val feeRate = if (weight > 0) "%.1f sat/vB".format(fee.toDouble() / (weight / 4.0)) else "? sat/vB"
        val status = json.optJSONObject("status")
        val confirmed = status?.optBoolean("confirmed", false) ?: false
        if (confirmed) {
            val bh = status.optInt("block_height", 0)
            val confs = if (currentBlockHeight >= bh && bh > 0) (currentBlockHeight - bh + 1) else 1
            "CONFIRMED · Block #$bh ($confs conf) · $feeRate"
        } else {
            "UNCONFIRMED (in mempool) · $feeRate"
        }
    }
}.getOrElse { "Lookup failed: ${it.message ?: "network error"}" }
