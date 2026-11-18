package com.example.btcwidget.ui.social

import androidx.lifecycle.viewModelScope
import com.example.btcwidget.nostr.core.Listing
import com.example.btcwidget.nostr.core.UnsignedEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The Store (NIP-99 marketplace): listings, your own shop, and orders. */
class MarketState(private val vm: SocialViewModel) {
    private val repo get() = vm.repo

    /** Listings for the store: everyone's, or only [seller]'s shop. Kept between visits. */
    private val _listings = MutableStateFlow<List<Listing>?>(null)
    val listings: StateFlow<List<Listing>?> = _listings.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun refresh() {
        if (_loading.value) return
        _loading.value = true
        vm.viewModelScope.launch {
            runCatching { repo.listings() }.onSuccess { _listings.value = it }
            if (_listings.value == null) _listings.value = emptyList()
            _loading.value = false
        }
    }

    suspend fun shopListings(seller: String) = runCatching { repo.listings(sellers = listOf(seller)) }.getOrDefault(emptyList())
    suspend fun listing(address: String) = runCatching { repo.listing(address) }.getOrNull()

    /** Publishes a listing (new or edited); [onResult] gets it, or null on failure. */
    fun publishListing(unsigned: UnsignedEvent, onResult: (Listing?) -> Unit) {
        var result: Listing? = null
        vm.act(tr("Couldn't publish the listing"), finally = { onResult(result) }) {
            result = repo.publishListing(unsigned)
            result?.let { l -> _listings.value = (listOf(l) + _listings.value.orEmpty().filter { it.address != l.address }) }
            vm.say(tr("Listing published"))
        }
    }

    fun deleteListing(listing: Listing, onDone: () -> Unit = {}) =
        vm.act(tr("Couldn't delete the listing"), finally = onDone) {
            repo.deleteListing(listing)
            _listings.value = _listings.value?.filter { it.address != listing.address }
            vm.say(tr("Listing deleted"))
        }

    /** Sends the order to the seller as a private message (NIP-17). */
    fun sendOrder(seller: String, text: String, onResult: (Boolean) -> Unit = {}) {
        var ok = false
        vm.act(tr("Couldn't send the order to the seller"), finally = { onResult(ok) }) {
            repo.dms.send(listOf(seller), text, null)
            ok = true
            vm.say(tr("Order sent to the seller"))
        }
    }

    /**
     * Pays the seller's Lightning address for a Store order: in the app with NWC, else [onInvoice]
     * opens a wallet. A plain payment, not a zap: no signed request and no public receipt, so the
     * purchase stays between buyer and seller (the order details go by encrypted message).
     */
    fun payLightning(seller: String, amountSat: Long, onInvoice: (String) -> Unit) =
        vm.act(tr("Couldn't pay")) {
            val invoice = repo.lnurlPayInvoice(seller, amountSat)
            if (repo.nwc.value != null) {
                vm.say(tr("⚡ Paying {0} sats…", amountSat))
                repo.payWithNwc(invoice)
                vm.say(tr("⚡ Paid {0} sats", amountSat))
                vm.wallet.refresh()
            } else {
                onInvoice(invoice)
            }
        }
}
