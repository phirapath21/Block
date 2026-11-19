package com.example.btcwidget.ui.social

import com.example.btcwidget.ui.main.TextSize
import com.example.btcwidget.ui.main.Radius
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import com.example.btcwidget.nostr.core.Nwc
import com.example.btcwidget.nostr.core.WalletTip
import com.example.btcwidget.nostr.core.Zaps
import com.example.btcwidget.ui.main.EInk

/**
 * The Lightning wallet connected with Nostr Wallet Connect: the balance in sats, big Receive and
 * Send buttons, and recent payments. Pull down to refresh. Without a wallet it explains how to
 * connect one. Opened from the bottom bar and from Settings › Wallet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(vm: SocialViewModel, navigate: (NavKey) -> Unit) {
    val account by vm.account.collectAsState()
    val connection by vm.wallet.nwc.collectAsState()
    val balanceMsat by vm.wallet.balanceMsat.collectAsState()
    val transactions by vm.wallet.transactions.collectAsState()
    val loading by vm.wallet.loading.collectAsState()
    var receiving by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var disconnecting by remember { mutableStateOf(false) }

    if (account == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(tr("Log in to use a wallet"), color = EInk.Value, fontSize = TextSize.Title, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(
                tr("Connect your Lightning wallet to tip, send and receive sats from Block."),
                color = EInk.Caption, fontSize = TextSize.Body, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
            )
            SettingsButton(tr("Log in or create account"), primary = true) { navigate(com.example.btcwidget.LoginKey) }
        }
        return
    }
    if (connection == null) {
        ConnectWallet(vm)
        return
    }

    LaunchedEffect(connection) { vm.wallet.refresh() }
    val pull = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = loading,
        onRefresh = { vm.wallet.refresh() },
        state = pull,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pull, isRefreshing = loading, containerColor = EInk.Body, color = EInk.Accent,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            // The balance in an Aurora card: sats large, about how much that is, Send and Receive.
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp).clip(RoundedCornerShape(24.dp))
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(com.example.btcwidget.ui.main.Aurora.colors.take(3).map { it.copy(alpha = 0.18f) }))
                    .border(1.dp, com.example.btcwidget.ui.main.Aurora.colors[1].copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                    .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Balance"), color = EInk.Label, fontSize = TextSize.Caption, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(44.dp).clip(CircleShape)
                            .clickable(role = Role.Button, onClickLabel = tr("Wallet options")) { menu = true }
                            .semantics { contentDescription = tr("Wallet options") },
                        contentAlignment = Alignment.Center
                    ) { Icon(Glyphs.MoreHoriz, contentDescription = null, tint = EInk.Caption, modifier = Modifier.size(22.dp)) }
                }
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.semantics(mergeDescendants = true) {
                        contentDescription = balanceMsat?.let { tr("Balance {0} sats", formatSats(it / 1000)) } ?: tr("Balance not loaded")
                    }
                ) {
                    Text(
                        balanceMsat?.let { formatSats(it / 1000) } ?: if (loading) "…" else "–",
                        color = EInk.Value, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold
                    )
                    Text(tr("sats"), color = EInk.Label, fontSize = TextSize.Subtitle, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp, bottom = 6.dp))
                }
                val context = androidx.compose.ui.platform.LocalContext.current
                val about = remember(balanceMsat) { balanceMsat?.let { aboutInMoney(context, it / 1000) } }
                if (about != null) Text(tr("about {0}", about), color = EInk.Caption, fontSize = TextSize.Caption)
                Row(Modifier.fillMaxWidth().padding(top = 18.dp, end = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WalletAction(Glyphs.ArrowUp, tr("Send"), primary = true) { sending = true }
                    WalletAction(Glyphs.ArrowDown, tr("Receive"), primary = false) { receiving = true }
                }
            }

            Text(
                tr("Recent"), color = EInk.Value, fontSize = TextSize.Subtitle, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
            )
            if (transactions.isEmpty()) {
                Text(
                    if (loading) tr("Loading…") else tr("No payments yet."),
                    color = EInk.Caption, fontSize = TextSize.Body, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            } else {
                val profiles by vm.profiles.collectAsState()
                // Tips say who: "Tip from Dev", "You tipped Mali" (from the zap request in the invoice).
                val tips = remember(transactions) { transactions.associateWith { WalletTip.of(it.description, it.isIncoming) } }
                LaunchedEffect(tips) { vm.repo.loadProfilesLater(tips.values.mapNotNull { it?.person }.distinct()) }
                transactions.forEach { tx -> TransactionRow(tx, tips[tx], tips[tx]?.let { profiles[it.person] }) }
            }
        }
    }

    if (receiving) ReceiveSheet(vm, onDismiss = { receiving = false })
    if (sending) SendSheet(vm, onDismiss = { sending = false })
    if (menu) ActionSheet(
        onDismiss = { menu = false },
        actions = listOf(
            SheetAction(Glyphs.Delete, tr("Disconnect wallet"), destructive = true) { disconnecting = true }
        )
    )
    if (disconnecting) ConfirmDialog(
        title = tr("Disconnect wallet?"),
        text = tr("Tips will open your Lightning wallet app to pay instead. You can connect again any time."),
        confirmLabel = tr("Disconnect"),
        onConfirm = { disconnecting = false; vm.wallet.disconnect() },
        onDismiss = { disconnecting = false }
    )
}

/** One of the two large buttons in the balance card: Send (filled) and Receive. */
@Composable
private fun RowScope.WalletAction(icon: ImageVector, label: String, primary: Boolean, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    val fill = if (primary) EInk.Value else EInk.Background.copy(alpha = 0.35f)
    val ink = if (primary) EInk.Background else EInk.Value
    Row(
        Modifier.weight(1f).height(52.dp).clip(Radius.Full).background(fill)
            .then(if (primary) Modifier else Modifier.border(1.dp, EInk.BodyStroke, Radius.Full))
            .clickable(role = Role.Button, onClickLabel = label) { haptics.tick(); onClick() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
        Text(label, color = ink, fontSize = TextSize.Body, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
    }
}

/** A payment: a tip says who it was with and their message; others say Received or Sent. */
@Composable
private fun TransactionRow(tx: Nwc.Transaction, tip: WalletTip?, person: com.example.btcwidget.nostr.core.Profile?) {
    val incoming = tx.isIncoming
    val gold = com.example.btcwidget.ui.main.ActionColor.Zap
    val (icon, tint) = when {
        tip != null -> Glyphs.Zap to gold
        incoming -> Glyphs.ArrowDown to EInk.Success
        else -> Glyphs.ArrowUp to EInk.Value
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            val name = tip?.let { person?.bestName ?: com.example.btcwidget.nostr.core.Profile.shortNpub(it.person) }
            Text(
                when {
                    tip != null && incoming -> tr("Tip from {0}", name.orEmpty())
                    tip != null -> tr("You tipped {0}", name.orEmpty())
                    tx.description.isNotBlank() -> tx.description
                    incoming -> tr("Received")
                    else -> tr("Sent")
                },
                color = EInk.Value, fontSize = TextSize.Body, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            val at = (tx.settledAt.takeIf { it > 0 } ?: tx.createdAt).takeIf { it > 0 }
            val line = listOfNotNull(tip?.comment?.takeIf { it.isNotBlank() }?.let { "“$it”" }, at?.let { relativeTime(it) }).joinToString(" · ")
            if (line.isNotEmpty()) Text(line, color = EInk.Caption, fontSize = TextSize.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            (if (incoming) "+" else "−") + formatSats(tx.amountSat),
            color = if (incoming) EInk.Success else EInk.Value, fontSize = TextSize.Body, fontWeight = FontWeight.Bold
        )
    }
}

/** About how much [sats] is in the currency picked for prices (from the last price fetched), or null. */
private fun aboutInMoney(context: android.content.Context, sats: Long): String? {
    val data = com.example.btcwidget.PriceRepository.getFromPrefs(context) ?: return null
    val price = com.example.btcwidget.PriceFormat.price(data, data.currency)
    if (price <= 0) return null
    val value = sats / 100_000_000.0 * price
    val symbol = if (data.currency == "THB") "฿" else "$"
    return if (value < 10) symbol + String.format(java.util.Locale.US, "%.2f", value) else com.example.btcwidget.PriceFormat.format(value, data.currency)
}

/** Receive: an amount and an optional note, then the invoice as a QR code to copy or share. */
@Composable
private fun ReceiveSheet(vm: SocialViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var invoice by remember { mutableStateOf<String?>(null) }
    BlockSheet(onDismiss) {
        SheetTitle(tr("Receive"))
        val inv = invoice
        if (inv == null) {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsTextField(
                    value = amount, onValueChange = { amount = it.filter(Char::isDigit).take(10) },
                    placeholder = tr("Amount (sats)"),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                SettingsTextField(value = note, onValueChange = { note = it.take(80) }, placeholder = tr("Note (optional)"), modifier = Modifier.fillMaxWidth())
                val sats = amount.toLongOrNull() ?: 0L
                SheetButton(if (creating) tr("Creating…") else tr("Create invoice"), enabled = sats > 0 && !creating) {
                    creating = true
                    vm.wallet.makeInvoice(sats, note.trim()) { created -> creating = false; invoice = created }
                }
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.background(Color.White, Radius.Large).padding(12.dp)) {
                    QrCode("lightning:${inv.uppercase()}", Modifier.size(220.dp).semantics { contentDescription = tr("Invoice QR code") })
                }
                Zaps.invoiceAmountMsat(inv)?.let {
                    Text(formatSats(it / 1000) + " " + tr("sats"), color = EInk.Value, fontSize = TextSize.Title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                }
                Text(
                    inv.take(18) + "…" + inv.takeLast(10), color = EInk.Caption, fontSize = TextSize.Caption, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SettingsButton(tr("Copy invoice"), primary = true) {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("Lightning invoice", inv))
                        android.widget.Toast.makeText(context, tr("Invoice copied"), android.widget.Toast.LENGTH_SHORT).show()
                    }
                    SettingsButton(tr("Share")) {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "lightning:$inv")
                        context.startActivity(Intent.createChooser(send, tr("Share invoice")))
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Send: paste or scan an invoice; the amount it asks for shows before anything is paid, and
 * Pay asks once more. Invoices without an amount can't be paid here.
 */
@Composable
private fun SendSheet(vm: SocialViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var paying by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    val scan = QrScanner.rememberScanner { raw -> if (raw != null) input = cleanInvoice(raw) }
    val invoice = cleanInvoice(input)
    val looksLikeInvoice = invoice.startsWith("lnbc") || invoice.startsWith("lntb") || invoice.startsWith("lntbs") || invoice.startsWith("lnbcrt")
    val amountSat = if (looksLikeInvoice) Zaps.invoiceAmountMsat(invoice)?.let { it / 1000 } else null
    BlockSheet(onDismiss) {
        SheetTitle(tr("Send"))
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsTextField(
                value = input, onValueChange = { input = it.trim() }, placeholder = "lnbc1…",
                isError = input.isNotBlank() && !looksLikeInvoice, modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsButton(tr("Paste")) {
                    val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
                    clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.let { input = cleanInvoice(it) }
                }
                SettingsButton(tr("Scan QR code")) { scan() }
            }
            when {
                input.isBlank() -> Text(tr("Paste a Lightning invoice or scan its QR code."), color = EInk.Caption, fontSize = TextSize.Caption)
                !looksLikeInvoice -> Text(tr("That isn't a Lightning invoice."), color = EInk.Alert, fontSize = TextSize.Caption)
                amountSat == null -> Text(tr("This invoice has no amount. Ask for one with an amount."), color = EInk.Alert, fontSize = TextSize.Caption)
                else -> Text(
                    formatSats(amountSat) + " " + tr("sats"),
                    color = EInk.Value, fontSize = TextSize.Title, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                )
            }
            SheetButton(if (paying) tr("Paying…") else tr("Pay"), enabled = amountSat != null && !paying) { confirming = true }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (confirming && amountSat != null) ConfirmDialog(
        title = tr("Pay {0} sats?", formatSats(amountSat)),
        text = tr("This sends sats from your connected wallet. It can't be undone."),
        confirmLabel = tr("Pay"),
        destructive = false,
        onConfirm = {
            confirming = false
            paying = true
            vm.wallet.payInvoice(invoice) { ok -> paying = false; if (ok) onDismiss() }
        },
        onDismiss = { confirming = false }
    )
}

/** Connecting a wallet: paste the Nostr Wallet Connect link from Alby Hub, Primal, Coinos… */
/**
 * No wallet yet: what it's for ("Tip in one tap"), three plain steps, then Paste link or Scan.
 * The link is never shown on screen; it's kept encrypted on the phone.
 */
@Composable
private fun ConnectWallet(vm: SocialViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    fun connect(raw: String?) {
        val link = raw?.trim().orEmpty()
        error = if (link.isEmpty()) tr("Copy the link from your wallet app first.") else vm.wallet.connect(link)
    }
    val scan = QrScanner.rememberScanner { raw -> if (raw != null) connect(raw) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp).padding(top = 24.dp, bottom = 110.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(92.dp).clip(RoundedCornerShape(28.dp))
                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(com.example.btcwidget.ui.main.ActionColor.Zap.copy(alpha = 0.25f), com.example.btcwidget.ui.main.Aurora.colors[2].copy(alpha = 0.25f)))),
            contentAlignment = Alignment.Center
        ) { Icon(NavIcons.Wallet.outline, contentDescription = null, tint = com.example.btcwidget.ui.main.ActionColor.Zap, modifier = Modifier.size(46.dp)) }
        Text(tr("Tip in one tap"), color = EInk.Value, fontSize = TextSize.Title, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 18.dp))
        Text(
            tr("Connect the Lightning wallet you already use. Tips then go out with one tap, and tips to you land there."),
            color = EInk.Label, fontSize = TextSize.Body, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp, start = 14.dp, end = 14.dp)
        )
        Column(
            Modifier.fillMaxWidth().padding(top = 20.dp).clip(Radius.Large)
                .background(com.example.btcwidget.ui.main.CardColors.Fill).border(1.dp, com.example.btcwidget.ui.main.CardColors.Edge, Radius.Large)
        ) {
            listOf(
                tr("In your wallet app, find Connect an app (Nostr Wallet Connect)"),
                tr("Set a spending limit, then copy the link"),
                tr("Paste it here, or scan its QR code")
            ).forEachIndexed { i, step ->
                if (i > 0) HorizontalDivider(thickness = 0.5.dp, color = com.example.btcwidget.ui.main.CardColors.Edge)
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(SettingsAccent.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", color = SettingsAccent, fontSize = TextSize.Body, fontWeight = FontWeight.ExtraBold)
                    }
                    Text(step, color = EInk.Value, fontSize = TextSize.Body, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            com.example.btcwidget.ui.main.BlockButton(
                tr("Paste link"), Modifier.weight(1f), kind = com.example.btcwidget.ui.main.ButtonKind.Accent, size = com.example.btcwidget.ui.main.ButtonSize.Large
            ) {
                val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip
                connect(clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString())
            }
            com.example.btcwidget.ui.main.BlockButton(
                tr("Scan"), Modifier.weight(1f), kind = com.example.btcwidget.ui.main.ButtonKind.Outline, size = com.example.btcwidget.ui.main.ButtonSize.Large,
                icon = Glyphs.Qr, onClick = scan
            )
        }
        error?.let { Text(it, color = EInk.Alert, fontSize = TextSize.Caption, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp)) }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, start = 8.dp, end = 8.dp), verticalAlignment = Alignment.Top) {
            Icon(Glyphs.Lock, contentDescription = null, tint = EInk.Caption, modifier = Modifier.padding(top = 2.dp).size(15.dp))
            Text(
                tr("Block never holds your money. The link stays encrypted on this phone. Without a wallet here, tipping opens your wallet app."),
                color = EInk.Caption, fontSize = TextSize.Caption, modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun ColumnScope.SheetTitle(text: String) {
    Text(text, color = EInk.Value, fontSize = TextSize.Title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp))
}

/** A full-width pill button, the main action of a sheet. */
@Composable
private fun SheetButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = Radius.Full
    Box(
        Modifier.fillMaxWidth().height(50.dp).clip(shape)
            .background(if (enabled) EInk.Accent else EInk.Body)
            .then(if (enabled) Modifier else Modifier.border(1.dp, EInk.BodyStroke, shape))
            .clickable(role = Role.Button, enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, color = if (enabled) EInk.OnAccent else EInk.Caption, fontSize = TextSize.Body, fontWeight = FontWeight.Bold) }
}

/** "lightning:LNBC…" → "lnbc…", the way invoices are pasted and scanned. */
internal fun cleanInvoice(raw: String): String = raw.trim().removePrefix("lightning:").removePrefix("LIGHTNING:").lowercase()

internal fun formatSats(sats: Long): String = "%,d".format(sats)
