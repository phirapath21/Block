package com.example.btcwidget.ui.social

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
            Text(tr("Log in to use a wallet"), color = EInk.Value, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(
                tr("Connect your Lightning wallet to zap, send and receive sats from Block."),
                color = EInk.Caption, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
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
            // The balance, large, with the wallet's menu at the right.
            Box(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tr("Balance"), color = EInk.Caption, fontSize = 13.sp)
                    Text(
                        balanceMsat?.let { "⚡ " + formatSats(it / 1000) } ?: if (loading) "⚡ …" else "⚡ –",
                        color = EInk.Value, fontSize = 36.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 2.dp).semantics {
                            contentDescription = balanceMsat?.let { tr("Balance {0} sats", formatSats(it / 1000)) } ?: tr("Balance not loaded")
                        }
                    )
                    Text(tr("sats"), color = EInk.Caption, fontSize = 14.sp)
                }
                Box(
                    Modifier.align(Alignment.TopEnd).padding(end = 8.dp).size(44.dp).clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = tr("Wallet options")) { menu = true }
                        .semantics { contentDescription = tr("Wallet options") },
                    contentAlignment = Alignment.Center
                ) { Icon(Glyphs.MoreHoriz, contentDescription = null, tint = EInk.Caption, modifier = Modifier.size(22.dp)) }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WalletAction(Glyphs.ArrowDown, tr("Receive")) { receiving = true }
                WalletAction(Glyphs.ArrowUp, tr("Send")) { sending = true }
            }

            HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
            Text(
                tr("Recent transactions"), color = EInk.Value, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
            )
            if (transactions.isEmpty()) {
                Text(
                    if (loading) tr("Loading…") else tr("No payments yet."),
                    color = EInk.Caption, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            } else {
                transactions.forEach { TransactionRow(it) }
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
        text = tr("Zaps will open your Lightning wallet app to pay instead. You can connect again any time."),
        confirmLabel = tr("Disconnect"),
        onConfirm = { disconnecting = false; vm.wallet.disconnect() },
        onDismiss = { disconnecting = false }
    )
}

/** One of the two large buttons under the balance. */
@Composable
private fun RowScope.WalletAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.weight(1f).height(56.dp).clip(shape).background(EInk.Accent)
            .clickable(role = Role.Button, onClickLabel = label) { haptics.tick(); onClick() },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = EInk.OnAccent, modifier = Modifier.size(20.dp))
        Text(label, color = EInk.OnAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun TransactionRow(tx: Nwc.Transaction) {
    val incoming = tx.isIncoming
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(EInk.Body),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (incoming) Glyphs.ArrowDown else Glyphs.ArrowUp, contentDescription = null,
                tint = if (incoming) EInk.Success else EInk.Value, modifier = Modifier.size(18.dp)
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                tx.description.takeIf { it.isNotBlank() } ?: if (incoming) tr("Received") else tr("Paid"),
                color = EInk.Value, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            val at = (tx.settledAt.takeIf { it > 0 } ?: tx.createdAt).takeIf { it > 0 }
            if (at != null) Text(relativeTime(at), color = EInk.Caption, fontSize = 12.sp)
        }
        Text(
            (if (incoming) "+" else "−") + formatSats(tx.amountSat),
            color = if (incoming) EInk.Success else EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold
        )
    }
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
                Box(Modifier.background(Color.White, RoundedCornerShape(16.dp)).padding(12.dp)) {
                    QrCode("lightning:${inv.uppercase()}", Modifier.size(220.dp).semantics { contentDescription = tr("Invoice QR code") })
                }
                Zaps.invoiceAmountMsat(inv)?.let {
                    Text("⚡ " + formatSats(it / 1000) + " " + tr("sats"), color = EInk.Value, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                }
                Text(
                    inv.take(18) + "…" + inv.takeLast(10), color = EInk.Caption, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
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
                input.isBlank() -> Text(tr("Paste a Lightning invoice or scan its QR code."), color = EInk.Caption, fontSize = 13.sp)
                !looksLikeInvoice -> Text(tr("That isn't a Lightning invoice."), color = EInk.Alert, fontSize = 13.sp)
                amountSat == null -> Text(tr("This invoice has no amount. Ask for one with an amount."), color = EInk.Alert, fontSize = 13.sp)
                else -> Text(
                    "⚡ " + formatSats(amountSat) + " " + tr("sats"),
                    color = EInk.Value, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
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
@Composable
private fun ConnectWallet(vm: SocialViewModel) {
    var uri by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("⚡", fontSize = 40.sp, modifier = Modifier.padding(top = 12.dp))
        Text(tr("Connect a wallet"), color = EInk.Value, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Text(
            tr("In your wallet (Alby Hub, Primal, Coinos, Mutiny…) create a Nostr Wallet Connect link and paste it here. ") +
                tr("Set a spending budget there: anyone with this link can spend up to it. It's stored encrypted on this phone."),
            color = EInk.Label, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        )
        SettingsTextField(
            value = uri, onValueChange = { uri = it.trim(); error = null }, placeholder = "nostr+walletconnect://…",
            isError = error != null, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
        )
        error?.let { Text(it, color = EInk.Alert, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
        Spacer(Modifier.height(12.dp))
        SheetButton(tr("Connect"), enabled = uri.isNotBlank()) {
            val e = vm.wallet.connect(uri)
            if (e == null) uri = "" else error = e
        }
        Text(
            tr("Without a wallet here, zapping opens your Lightning wallet app to pay."),
            color = EInk.Caption, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp)
        )
    }
}

@Composable
private fun ColumnScope.SheetTitle(text: String) {
    Text(text, color = EInk.Value, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 14.dp))
}

/** A full-width pill button, the main action of a sheet. */
@Composable
private fun SheetButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.fillMaxWidth().height(50.dp).clip(shape)
            .background(if (enabled) EInk.Accent else EInk.Body)
            .then(if (enabled) Modifier else Modifier.border(1.dp, EInk.BodyStroke, shape))
            .clickable(role = Role.Button, enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, color = if (enabled) EInk.OnAccent else EInk.Caption, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

/** "lightning:LNBC…" → "lnbc…", the way invoices are pasted and scanned. */
internal fun cleanInvoice(raw: String): String = raw.trim().removePrefix("lightning:").removePrefix("LIGHTNING:").lowercase()

private fun formatSats(sats: Long): String = "%,d".format(sats)
