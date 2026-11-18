package com.example.btcwidget.ui.social

import androidx.lifecycle.viewModelScope
import com.example.btcwidget.nostr.core.Nwc
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The wallet connected with Nostr Wallet Connect (NIP-47): balance, history, invoices and payments. */
class WalletState(private val vm: SocialViewModel) {
    private val repo get() = vm.repo

    val nwc get() = repo.wallet.connection

    private val _balanceMsat = MutableStateFlow<Long?>(null)
    val balanceMsat: StateFlow<Long?> = _balanceMsat.asStateFlow()

    private val _transactions = MutableStateFlow<List<Nwc.Transaction>>(emptyList())
    val transactions: StateFlow<List<Nwc.Transaction>> = _transactions.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** Refreshes balance and transaction history from the connected NWC wallet. */
    fun refresh() {
        if (repo.wallet.connection.value == null || _loading.value) return
        _loading.value = true
        vm.viewModelScope.launch {
            try {
                val bal = async { runCatching { repo.wallet.balance() }.getOrNull() }
                val txs = async { runCatching { repo.wallet.transactions(20) }.getOrNull() }
                bal.await()?.let { _balanceMsat.value = it }
                txs.await()?.let { _transactions.value = it }
            } finally {
                _loading.value = false
            }
        }
    }

    /** Creates an invoice via NWC `make_invoice` and passes the BOLT11 string to [onResult]. */
    fun makeInvoice(amountSat: Long, description: String, onResult: (String?) -> Unit) {
        var inv: String? = null
        vm.act(tr("Couldn't create invoice"), finally = { onResult(inv) }) {
            inv = repo.wallet.makeInvoice(amountSat, description)
        }
    }

    /** Pays a BOLT11 invoice via NWC and refreshes wallet balance/history. */
    fun payInvoice(invoice: String, onDone: (Boolean) -> Unit = {}) {
        var ok = false
        vm.act(tr("Payment failed"), finally = { onDone(ok) }) {
            vm.say(tr("⚡ Paying invoice…"))
            repo.wallet.pay(invoice.trim().removePrefix("lightning:").removePrefix("LIGHTNING:"))
            ok = true
            vm.say(tr("⚡ Payment sent"))
            refresh()
        }
    }

    /** Returns an error message, or null when connected. */
    fun connect(uri: String): String? = try {
        repo.wallet.connect(uri); vm.say(tr("Wallet connected")); refresh(); null
    } catch (e: Exception) {
        e.message ?: "Couldn't connect the wallet"
    }

    fun disconnect() {
        repo.wallet.disconnect()
        _balanceMsat.value = null
        _transactions.value = emptyList()
        vm.say(tr("Wallet disconnected"))
    }
}
