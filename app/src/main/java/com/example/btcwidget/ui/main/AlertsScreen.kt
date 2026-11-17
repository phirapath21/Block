package com.example.btcwidget.ui.main

import com.example.btcwidget.ui.social.tr
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.BlockclockMetrics
import com.example.btcwidget.PriceData
import com.example.btcwidget.alerts.AlertKind
import com.example.btcwidget.alerts.AlertRule
import com.example.btcwidget.alerts.PriceAlerts
import java.util.Locale

/** Price and fee alerts, with the current values shown for reference. */
@Composable
fun AlertsScreen(
    alerts: List<AlertRule>,
    onAlertChange: (AlertRule) -> Unit,
    currencySetting: String,
    refreshInterval: Int,
    notificationsAllowed: Boolean,
    data: PriceData?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
    ) {
        HeaderSection(title = tr("Price alerts"), subtitle = tr("Get notified on a crossing"))

        if (data != null) {
            EInkPanel(title = tr("Now")) {
                NowLine("BTC", PriceAlerts.formatPrice(PriceAlerts.price(data, currencySetting), currencySetting))
                NowLine("Fastest fee", "${data.feeFastest} sat/vB")
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        EInkPanel(title = tr("Price")) {
            alerts.filter { it.kind == AlertKind.PRICE_ABOVE || it.kind == AlertKind.PRICE_BELOW }.forEach { rule ->
                AlertRow(rule = rule, currency = currencySetting, onChange = onAlertChange)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        EInkPanel(title = tr("Fees")) {
            alerts.filter { it.kind == AlertKind.FEE_AT_OR_BELOW }.forEach { rule ->
                AlertRow(rule = rule, currency = currencySetting, onChange = onAlertChange)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        EInkPanel(title = tr("Network events")) {
            alerts.filter { it.kind.isNetworkEvent }.forEach { rule ->
                NetworkEventRow(rule = rule, data = data, onChange = onAlertChange)
            }
        }

        val blocked = alerts.any { it.enabled } && !notificationsAllowed
        Text(
            text = if (blocked) {
                tr("Notifications are off for Block. Allow them in system settings to get alerts.")
            } else {
                tr("Checked with each background refresh (every {0} min). ", refreshInterval) +
                    tr("One notification each time a line is crossed.")
            },
            color = if (blocked) EInk.Alert else EInk.Caption,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 24.dp)
        )
    }
}

@Composable
private fun NowLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = EInk.Caption, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(value, color = EInk.Value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * One alert: label, threshold field and on/off switch. Price thresholds use the
 * currency selected in Preferences; editing an enabled alert saves as you type.
 */
@Composable
private fun AlertRow(rule: AlertRule, currency: String, onChange: (AlertRule) -> Unit) {
    val isFee = rule.kind == AlertKind.FEE_AT_OR_BELOW
    val label = when (rule.kind) {
        AlertKind.PRICE_ABOVE -> "BTC rises above"
        AlertKind.PRICE_BELOW -> "BTC falls below"
        AlertKind.FEE_AT_OR_BELOW -> "Fees at or below"
        // Network events use NetworkEventRow.
        AlertKind.HALVING_SOON, AlertKind.RETARGET_SOON -> rule.kind.name
    }
    // A price alert set in the other currency is shown as such until edited.
    val ruleCurrency = if (isFee || !rule.enabled) currency else rule.currency
    var text by remember(rule.kind) {
        mutableStateOf(if (rule.threshold > 0) String.format(Locale.US, "%.0f", rule.threshold) else "")
    }
    fun updated(enabled: Boolean, input: String) = rule.copy(
        enabled = enabled,
        threshold = input.toDoubleOrNull() ?: 0.0,
        currency = if (isFee) rule.currency else currency
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = tr(label), color = EInk.Value, fontSize = 15.sp)
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    val cleaned = input.filter { it.isDigit() }.take(10)
                    text = cleaned
                    if (rule.enabled) onChange(updated(enabled = cleaned.isNotEmpty(), input = cleaned))
                },
                singleLine = true,
                placeholder = { Text(if (isFee) "e.g. 5" else "e.g. 100000") },
                prefix = if (isFee) null else ({ Text(if (ruleCurrency == "THB") "฿" else "$") }),
                suffix = if (isFee) ({ Text(tr("sat/vB")) }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(fontSize = 15.sp, color = EInk.Value),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EInk.Accent,
                    unfocusedBorderColor = EInk.BodyStroke,
                    focusedTextColor = EInk.Value,
                    unfocusedTextColor = EInk.Value,
                    cursorColor = EInk.Accent,
                    focusedPrefixColor = EInk.Caption,
                    unfocusedPrefixColor = EInk.Caption,
                    focusedSuffixColor = EInk.Caption,
                    unfocusedSuffixColor = EInk.Caption,
                    focusedPlaceholderColor = EInk.Caption,
                    unfocusedPlaceholderColor = EInk.Caption
                ),
                modifier = Modifier
                    .padding(top = 6.dp, end = 12.dp)
                    .fillMaxWidth()
            )
        }
        EInkSwitch(
            checked = rule.enabled,
            // Needs a threshold before it can be switched on.
            enabled = rule.enabled || text.isNotEmpty(),
            onCheckedChange = { on -> onChange(updated(enabled = on, input = text)) }
        )
    }
}

/** On/off alert for a network event, with how far away it is now. */
@Composable
private fun NetworkEventRow(rule: AlertRule, data: PriceData?, onChange: (AlertRule) -> Unit) {
    val (label, note) = when (rule.kind) {
        AlertKind.HALVING_SOON -> "A day before the halving" to data?.let {
            val left = BlockclockMetrics.blocksToHalving(it.blockHeight)
            String.format(Locale.US, "Next in %,d blocks (≈ %,.0f days)", left, left / AlertKind.BLOCKS_PER_DAY.toDouble())
        }
        else -> "A day before a difficulty adjustment" to data?.let {
            val left = BlockclockMetrics.blocksToRetarget(it.blockHeight)
            val change = BlockclockMetrics.formatChange(it.difficultyChange)
            String.format(Locale.US, "Next in %,d blocks", left) + (change?.let { c -> " · est. $c" } ?: "")
        }
    }
    SettingRow(
        label = label,
        note = note,
        modifier = Modifier.clickable { onChange(rule.copy(enabled = !rule.enabled)) }
    ) {
        EInkSwitch(checked = rule.enabled, onCheckedChange = { onChange(rule.copy(enabled = it)) })
    }
}

