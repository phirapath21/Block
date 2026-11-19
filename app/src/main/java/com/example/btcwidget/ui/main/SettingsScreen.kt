package com.example.btcwidget.ui.main

import com.example.btcwidget.ui.social.tr
import com.example.btcwidget.ui.social.SettingsButton
import com.example.btcwidget.ui.social.SettingsDivider
import com.example.btcwidget.ui.social.SettingsHeader
import com.example.btcwidget.ui.social.SettingsNote
import com.example.btcwidget.ui.social.SettingsRadioRow
import com.example.btcwidget.ui.social.SettingsTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.AppTheme
import com.example.btcwidget.DataSources
import com.example.btcwidget.PriceSource
import com.example.btcwidget.Stack
import com.example.btcwidget.StackUnit
import com.example.btcwidget.ThbSource

/**
 * Settings › Bitcoin and widgets: your stack, display currency, widget theme, refresh cadence and
 * price sources. Its state lives in [MainScreenViewModel] with the price data.
 */
@Composable
fun BitcoinSettingsRoute(viewModel: MainScreenViewModel = androidx.lifecycle.viewmodel.compose.viewModel(), onOpenWidgets: () -> Unit = {}) {
    val settings by viewModel.settings.collectAsState()
    val state by viewModel.uiState.collectAsState()
    val data = (state as? MainScreenUiState.Success)?.data
    val stackValue = data?.takeIf { settings.stackSats > 0 }?.let {
        val btc = settings.stackSats / Stack.SATS_PER_BTC.toDouble()
        com.example.btcwidget.PriceFormat.format(btc * com.example.btcwidget.PriceFormat.price(it, settings.currency), settings.currency)
    }
    com.example.btcwidget.ui.social.SettingsLinkRow(
        tr("Widgets"), summary = tr("What the home-screen widget shows, and in which order"),
        icon = com.example.btcwidget.ui.social.NavIcons.Widgets.outline, onClick = onOpenWidgets
    )
    SettingsDivider()
    BitcoinSettings(
        stackSats = settings.stackSats, stackUnit = settings.stackUnit, stackValue = stackValue, onStackChange = viewModel::setStack,
        currency = settings.currency, onCurrency = viewModel::setCurrency,
        theme = settings.theme, onTheme = viewModel::setTheme,
        refreshInterval = settings.refreshInterval, onRefreshInterval = viewModel::setRefreshInterval,
        dataSources = settings.dataSources, onDataSources = viewModel::setDataSources,
        mempoolUrl = settings.mempoolUrl, onMempoolUrl = viewModel::setMempoolUrl
    )
}

@Composable
fun BitcoinSettings(
    stackSats: Long, stackUnit: StackUnit, stackValue: String?, onStackChange: (Long, StackUnit) -> Unit,
    currency: String, onCurrency: (String) -> Unit,
    theme: AppTheme, onTheme: (AppTheme) -> Unit,
    refreshInterval: Int, onRefreshInterval: (Int) -> Unit,
    dataSources: DataSources, onDataSources: (DataSources) -> Unit,
    mempoolUrl: String = "", onMempoolUrl: (String) -> Unit = {},
) {
    SettingsHeader(tr("Currency"))
    SettingsNote(tr("Prices and sats"))
    listOf("USD" to "US dollar (USD)", "THB" to "Thai baht (THB)").forEach { (code, name) ->
        SettingsRadioRow(tr(name), selected = currency == code) { onCurrency(code) }
    }

    SettingsDivider()
    SettingsHeader(tr("Refresh every"))
    SettingsNote(tr("Widgets and phone notifications check in the background this often."))
    listOf(15, 30, 60).forEach { m -> SettingsRadioRow(tr("{0} minutes", m), selected = refreshInterval == m) { onRefreshInterval(m) } }

    SettingsDivider()
    SettingsHeader(tr("Widget theme"))
    SettingsNote(tr("Home-screen widgets and their previews; the app keeps its own look"))
    AppTheme.entries.forEach { t -> SettingsRadioRow(tr(t.label), selected = theme == t) { onTheme(t) } }

    SettingsDivider()
    SettingsHeader(tr("BTC price"))
    SettingsNote(tr("Spot price in USD"))
    PriceSource.entries.forEach { src -> SettingsRadioRow(src.label, selected = dataSources.price == src) { onDataSources(dataSources.copy(price = src)) } }
    SettingsHeader(tr("THB price"))
    SettingsRadioRow(tr("USD price × exchange rate"), selected = dataSources.thb == ThbSource.CONVERTED) { onDataSources(dataSources.copy(thb = ThbSource.CONVERTED)) }
    SettingsRadioRow(tr("Bitkub's BTC/THB market"), selected = dataSources.thb == ThbSource.BITKUB) { onDataSources(dataSources.copy(thb = ThbSource.BITKUB)) }
    SettingsNote(tr("If a source is unreachable, Block falls back to Coinbase or the last known price."))

    SettingsDivider()
    SettingsHeader(tr("Mempool API"))
    SettingsNote(tr("Use your own mempool.space compatible node. Leave blank for the public server."))
    var mempoolDraft by remember(mempoolUrl) { mutableStateOf(mempoolUrl) }
    val cleanMempoolUrl = mempoolDraft.trim().trimEnd('/')
    val mempoolValid = cleanMempoolUrl.isEmpty() || runCatching {
        val uri = java.net.URI(cleanMempoolUrl)
        uri.scheme == "https" && !uri.host.isNullOrBlank()
    }.getOrDefault(false)
    SettingsTextField(
        value = mempoolDraft,
        onValueChange = { mempoolDraft = it.take(300) },
        placeholder = "https://mempool.example.com",
        isError = !mempoolValid,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    )
    SettingsButton(tr("Save Mempool URL"), primary = true, enabled = mempoolValid && cleanMempoolUrl != mempoolUrl) {
        onMempoolUrl(cleanMempoolUrl)
    }
    if (!mempoolValid) SettingsNote(tr("Enter an https:// URL or leave it empty."))
}

/** A labelled settings row with controls on the right and an optional note under the label. */
@Composable
internal fun SettingRow(
    label: String,
    modifier: Modifier = Modifier,
    note: String? = null,
    dimmed: Boolean = false,
    controls: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = label,
                color = if (dimmed) EInk.Caption else EInk.Value,
                fontSize = 15.sp
            )
            if (note != null) {
                Text(
                    text = note,
                    color = EInk.Caption,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = controls
        )
    }
}

/** Published copy of docs/privacy-policy.md (also the URL for the Play listing). */
const val PRIVACY_POLICY_URL = "https://github.com/phirapath21/Block/blob/main/docs/privacy-policy.md"

/**
 * Settings › Bitcoin and widgets › Widgets: every panel the blockclock widget can show, which ones
 * it cycles through and in what order. Its state lives in [MainScreenViewModel] with the price data.
 */
@Composable
fun WidgetsRoute(onBack: () -> Unit, onSetUpStack: () -> Unit, viewModel: MainScreenViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val settings by viewModel.settings.collectAsState()
    val state by viewModel.uiState.collectAsState()
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.start() }
    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().screenBackground()) {
        com.example.btcwidget.ui.social.SocialTopBar(tr("Widgets"), onBack)
        WidgetsScreen(
            data = (state as? MainScreenUiState.Success)?.data,
            currencySetting = settings.currency,
            modeStates = settings.modeStates,
            onModeToggle = viewModel::setModeEnabled,
            order = settings.modeOrder,
            onOrderChange = viewModel::setModeOrder,
            collapsedCategories = settings.collapsedCategories,
            onToggleCategory = viewModel::toggleCategory,
            stackSats = settings.stackSats,
            onSetUpStack = onSetUpStack,
            modifier = Modifier.navigationBarsPadding()
        )
    }
}
