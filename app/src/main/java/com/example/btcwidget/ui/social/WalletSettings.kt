package com.example.btcwidget.ui.social

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.ui.main.EInk

// ---- Wallet and zaps ----

@Composable
internal fun WalletPage(vm: SocialViewModel) {
    val context = LocalContext.current
    val wallet by vm.wallet.nwc.collectAsState()
    val balanceMsat by vm.wallet.balanceMsat.collectAsState()
    val transactions by vm.wallet.transactions.collectAsState()
    val loading by vm.wallet.loading.collectAsState()
    var uri by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var disconnecting by remember { mutableStateOf(false) }

    var recvAmount by remember { mutableStateOf("") }
    var recvDesc by remember { mutableStateOf("") }
    var createdInvoice by remember { mutableStateOf<String?>(null) }
    var creatingInvoice by remember { mutableStateOf(false) }
    var payInput by remember { mutableStateOf("") }
    var payingInvoice by remember { mutableStateOf(false) }

    LaunchedEffect(wallet) {
        if (wallet != null) vm.wallet.refresh()
    }

    SettingsHeader(tr("Wallet (NWC)"))
    if (wallet != null) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(tr("Balance"), color = EInk.Caption, fontSize = 13.sp)
                Text(
                    text = balanceMsat?.let { "⚡ %,d sats".format(it) } ?: if (loading) tr("Loading…") else "⚡ – sats",
                    color = EInk.Value, fontSize = 24.sp, fontWeight = FontWeight.Bold
                )
            }
            SettingsButton(if (loading) tr("Refreshing…") else tr("Refresh"), enabled = !loading) {
                vm.wallet.refresh()
            }
        }
        SettingsNote(tr("Connected. Zaps pay from this wallet in one tap, up to the budget you set in the wallet."))

        SettingsDivider()
        SettingsHeader(tr("Receive (Create invoice)"))
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsTextField(
                value = recvAmount,
                onValueChange = { recvAmount = it.filter { c -> c.isDigit() }.take(10) },
                placeholder = tr("Amount (sats)"),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            SettingsTextField(
                value = recvDesc,
                onValueChange = { recvDesc = it.take(80) },
                placeholder = tr("Note (optional)"),
                modifier = Modifier.fillMaxWidth()
            )
            val amt = recvAmount.toLongOrNull() ?: 0L
            SettingsButton(
                if (creatingInvoice) tr("Creating…") else tr("Create invoice"),
                primary = true,
                enabled = amt > 0 && !creatingInvoice
            ) {
                creatingInvoice = true
                vm.wallet.makeInvoice(amt, recvDesc.trim()) { inv ->
                    creatingInvoice = false
                    if (inv != null) createdInvoice = inv
                }
            }
            createdInvoice?.let { inv ->
                Column(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.padding(8.dp).background(androidx.compose.ui.graphics.Color.White, androidx.compose.foundation.shape.RoundedCornerShape(12.dp)).padding(10.dp)
                    ) {
                        QrCode("lightning:${inv.uppercase()}", Modifier.size(190.dp))
                    }
                    Text(
                        inv.take(18) + "…" + inv.takeLast(10),
                        color = EInk.Caption, fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettingsButton(tr("Copy invoice"), primary = true) {
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                .setPrimaryClip(ClipData.newPlainText("Lightning invoice", inv))
                            android.widget.Toast.makeText(context, tr("Invoice copied"), android.widget.Toast.LENGTH_SHORT).show()
                        }
                        SettingsButton(tr("Clear")) { createdInvoice = null }
                    }
                }
            }
        }

        SettingsDivider()
        SettingsHeader(tr("Send (Pay invoice)"))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsTextField(
                value = payInput,
                onValueChange = { payInput = it.trim() },
                placeholder = "lnbc1…",
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.size(8.dp))
            SettingsButton(
                if (payingInvoice) tr("Paying…") else tr("Pay"),
                primary = true,
                enabled = payInput.isNotBlank() && !payingInvoice
            ) {
                payingInvoice = true
                vm.wallet.payInvoice(payInput) { ok ->
                    payingInvoice = false
                    if (ok) payInput = ""
                }
            }
        }

        if (transactions.isNotEmpty()) {
            SettingsDivider()
            SettingsHeader(tr("Recent transactions"))
            transactions.forEach { tx ->
                val incoming = tx.isIncoming
                val sats = tx.amountSat
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            tx.description.takeIf { it.isNotBlank() } ?: if (incoming) tr("Received") else tr("Paid"),
                            color = EInk.Value, fontSize = 15.sp, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        val timeText = (tx.settledAt.takeIf { it > 0 } ?: tx.createdAt).takeIf { it > 0 }?.let { relativeTime(it) }.orEmpty()
                        if (timeText.isNotEmpty()) Text(timeText, color = EInk.Caption, fontSize = 12.sp)
                    }
                    Text(
                        text = (if (incoming) "+" else "-") + "%,d sats".format(sats),
                        color = if (incoming) SettingsAccent else EInk.Value,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        SettingsDivider()
        SettingsLinkRow(tr("Disconnect wallet"), destructive = true) { disconnecting = true }
    } else {
        SettingsNote(
            tr("In your wallet (Alby Hub, Primal, Coinos, Mutiny…) create a Nostr Wallet Connect link and paste it here. ") +
                tr("Set a spending budget there: anyone with this link can spend up to it. It's stored encrypted on this phone.")
        )
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            androidx.compose.material3.OutlinedTextField(
                value = uri, onValueChange = { uri = it.trim(); error = null },
                placeholder = { Text("nostr+walletconnect://…", color = EInk.Caption) },
                singleLine = true, isError = error != null,
                visualTransformation = PasswordVisualTransformation(),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SettingsAccent, unfocusedBorderColor = EInk.BodyStroke, cursorColor = SettingsAccent,
                    focusedTextColor = EInk.Value, unfocusedTextColor = EInk.Value
                ),
                modifier = Modifier.fillMaxWidth()
            )
            error?.let { Text(it, color = EInk.Alert, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)) }
            Spacer(Modifier.height(10.dp))
            SettingsButton(tr("Connect"), primary = true, enabled = uri.isNotBlank()) {
                val e = vm.wallet.connect(uri)
                if (e == null) uri = "" else error = e
            }
        }
        SettingsNote(tr("Without a wallet here, zapping opens your Lightning wallet app to pay."))
    }
    if (disconnecting) ConfirmDialog(
        title = tr("Disconnect wallet?"),
        text = tr("Zaps will open your Lightning wallet app to pay instead. You can connect again any time."),
        confirmLabel = tr("Disconnect"),
        onConfirm = { disconnecting = false; vm.wallet.disconnect() },
        onDismiss = { disconnecting = false }
    )
}
