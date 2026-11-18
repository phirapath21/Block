package com.example.btcwidget.ui.social

import android.content.Context
import com.example.btcwidget.ui.main.screenBackground
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import coil3.compose.AsyncImage
import com.example.btcwidget.ChatKey
import com.example.btcwidget.ComposeKey
import com.example.btcwidget.EditListingKey
import com.example.btcwidget.ListingKey
import com.example.btcwidget.LoginKey
import com.example.btcwidget.PriceData
import com.example.btcwidget.ProfileKey
import com.example.btcwidget.ShopKey
import com.example.btcwidget.nostr.NoteStats
import com.example.btcwidget.nostr.core.Listing
import com.example.btcwidget.nostr.core.Markdown
import com.example.btcwidget.nostr.core.Marketplace
import com.example.btcwidget.nostr.core.Price
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.ui.main.EInk
import kotlinx.coroutines.launch
import java.util.UUID

private val StoreOrange get() = EInk.Accent

private data class CategoryChip(val id: String, val label: String, val tags: Set<String> = emptySet())

private val STORE_CATEGORIES = listOf(
    CategoryChip("all", "All"),
    CategoryChip("following", "Following"),
    CategoryChip("hardware", "Hardware", setOf("hardware", "electronics", "node", "miner", "bitaxe", "signing", "wallet", "coldcard", "jade", "trezor", "ledger")),
    CategoryChip("art", "Art", setOf("art", "collectibles", "painting", "poster", "print", "photography", "sculpture", "nft")),
    CategoryChip("apparel", "Apparel", setOf("apparel", "clothing", "shirt", "tshirt", "hoodie", "hat", "cap", "fashion", "wear", "shoes")),
    CategoryChip("books", "Books", setOf("books", "book", "magazine", "zine", "reading", "education")),
    CategoryChip("digital", "Digital", setOf("digital", "software", "voucher", "giftcard", "code", "course", "ebook", "music")),
    CategoryChip("services", "Services", setOf("services", "service", "consulting", "design", "dev", "freelance", "work", "job")),
    CategoryChip("local", "Local", setOf("local", "pickup", "food", "coffee", "beef", "honey", "thailand", "bangkok", "siamstr"))
)

private enum class StoreSort(val label: String) {
    NEWEST("Newest first"),
    PRICE_LOW("Price: Low to High"),
    PRICE_HIGH("Price: High to Low")
}

/**
 * Formats a listing's price according to user preference:
 * Primary line: Sats / BTC ("⚡ 63,800 sats" or "₿0.0125").
 * Secondary line: Fiat in parentheses ("(฿1,500)" or "($45.00)").
 */
internal data class FormattedListingPrice(val primary: String, val secondary: String?, val sats: Long?)

internal fun formatListingPrice(price: Price?, data: PriceData?, currency: String): FormattedListingPrice {
    if (price == null) return FormattedListingPrice(tr("Ask for price"), null, null)
    val btcUsd = data?.btcUsd ?: 0.0
    val btcThb = data?.btcThb ?: 0.0
    val sats = Marketplace.toSats(price, btcUsd, btcThb)
    if (sats != null) {
        val primary = "⚡ " + Marketplace.formatSats(sats)
        // Prefer the currency the seller wrote if it's already fiat (THB/USD), otherwise convert to user's preferred currency.
        val fiatCurrency = when (price.currency) {
            "THB", "USD" -> price.currency
            else -> if (currency == "THB") "THB" else "USD"
        }
        val fiatAmount = if (price.currency == fiatCurrency) price.amount else Marketplace.toFiat(sats, fiatCurrency, btcUsd, btcThb)
        val secondary = fiatAmount?.let { "(" + Marketplace.formatFiat(it, fiatCurrency) + ")" }
        return FormattedListingPrice(primary, secondary, sats)
    }
    return FormattedListingPrice(Marketplace.formatRaw(price), null, null)
}

/**
 * The main Nostr Store tab: a polished 2-column shopping feed with sticky search,
 * category pills, sort/filter options, and a "+ Sell" button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketplaceScreen(
    vm: SocialViewModel,
    data: PriceData?,
    currency: String,
    navigate: (NavKey) -> Unit
) {
    val listings by vm.listings.collectAsState()
    val loading by vm.storeLoading.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val following by vm.following.collectAsState()
    val muted by vm.muted.collectAsState()
    val account by vm.account.collectAsState()
    val haptics = rememberHaptics()

    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("all") }
    var sort by rememberSaveable { mutableStateOf(StoreSort.NEWEST) }
    var inStockOnly by rememberSaveable { mutableStateOf(true) }
    var filterSheet by remember { mutableStateOf(false) }

    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (listings == null) vm.refreshStore()
    }
    LaunchedEffect(Unit) {
        TabTaps.flow.collect { tab ->
            if (tab == "STORE") {
                if (gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0) {
                    gridState.animateScrollToItem(0)
                } else {
                    vm.refreshStore()
                }
            }
        }
    }

    val btcUsd = data?.btcUsd ?: 0.0
    val btcThb = data?.btcThb ?: 0.0
    val filtered = remember(listings, query, category, sort, inStockOnly, following, muted, btcUsd, btcThb) {
        val raw = listings.orEmpty().filter { it.pubkey !in muted }
        val byStock = if (inStockOnly) raw.filter { !it.sold } else raw
        val byCat = when (category) {
            "all" -> byStock
            "following" -> byStock.filter { it.pubkey in following || it.pubkey == account?.pubkey }
            else -> {
                val chip = STORE_CATEGORIES.firstOrNull { it.id == category }
                if (chip == null) byStock
                else byStock.filter { l ->
                    l.categories.any { it in chip.tags } ||
                        chip.tags.any { tag -> l.title.contains(tag, ignoreCase = true) }
                }
            }
        }
        val byQuery = if (query.isBlank()) byCat else byCat.filter { Marketplace.matches(it, query) }
        when (sort) {
            StoreSort.NEWEST -> byQuery.sortedByDescending { it.publishedAt }
            StoreSort.PRICE_LOW -> byQuery.sortedBy { l -> l.price?.let { Marketplace.toSats(it, btcUsd, btcThb) } ?: Long.MAX_VALUE }
            StoreSort.PRICE_HIGH -> byQuery.sortedByDescending { l -> l.price?.let { Marketplace.toSats(it, btcUsd, btcThb) } ?: -1L }
        }
    }

    if (filterSheet) {
        StoreFilterSheet(
            sort = sort,
            inStockOnly = inStockOnly,
            onSort = { sort = it },
            onInStockChange = { inStockOnly = it },
            onDismiss = { filterSheet = false }
        )
    }

    Column(Modifier.fillMaxSize().screenBackground()) {
        // Sticky header: Search bar + Filter + My Shop + Sell CTA
        StoreTopHeader(
            query = query,
            onQueryChange = { query = it },
            hasActiveFilter = sort != StoreSort.NEWEST || !inStockOnly,
            onOpenFilter = { filterSheet = true },
            onOpenMyShop = account?.pubkey?.let { me -> { navigate(ShopKey(me)) } },
            onSell = {
                if (account == null) navigate(LoginKey) else navigate(EditListingKey(null))
            }
        )

        // Category pills
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(STORE_CATEGORIES, key = { it.id }) { chip ->
                val selected = category == chip.id
                ChoicePill(
                    label = tr(chip.label),
                    selected = selected,
                    onClick = {
                        haptics.tick()
                        category = chip.id
                        scope.launch { gridState.scrollToItem(0) }
                    }
                )
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)

        PullToRefreshBox(
            isRefreshing = loading && !listings.isNullOrEmpty(),
            onRefresh = { haptics.tick(); vm.refreshStore() },
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                listings == null || (loading && listings.isNullOrEmpty()) -> {
                    StoreGridSkeleton()
                }
                filtered.isEmpty() -> {
                    LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            Column(
                                Modifier.fillMaxWidth().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (category == "following") tr("No listings from people you follow yet.")
                                           else if (query.isNotBlank()) tr("No products match your search.")
                                           else tr("No products found on your relays right now."),
                                    color = EInk.Caption,
                                    fontSize = 15.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(14.dp))
                                if (category != "all" || query.isNotBlank() || !inStockOnly) {
                                    PillButton(tr("Show all products"), filled = false) {
                                        category = "all"
                                        query = ""
                                        inStockOnly = true
                                    }
                                } else {
                                    PillButton("+ " + tr("List an item for sale")) {
                                        if (account == null) navigate(LoginKey) else navigate(EditListingKey(null))
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 700
                    LazyVerticalGrid(
                        columns = if (wide) GridCells.Adaptive(minSize = 210.dp) else GridCells.Fixed(2),
                        state = gridState,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filtered, key = { it.address }) { item ->
                            ProductGridCard(
                                listing = item,
                                seller = profiles[item.pubkey],
                                data = data,
                                currency = currency,
                                isFollowed = item.pubkey in following,
                                onClick = { navigate(ListingKey(item.address)) },
                                onSellerClick = { navigate(ShopKey(item.pubkey)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreTopHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    hasActiveFilter: Boolean,
    onOpenFilter: () -> Unit,
    onOpenMyShop: (() -> Unit)?,
    onSell: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Search box
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .clip(RoundedCornerShape(50))
                .background(EInk.Body)
                .border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(50))
                .padding(horizontal = 12.dp)
        ) {
            Icon(NavIcons.Search.outline, contentDescription = null, tint = EInk.Caption, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = EInk.Value, fontSize = 14.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(StoreOrange),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(tr("Search products, #tags…"), color = EInk.Caption, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    inner()
                }
            )
            if (query.isNotEmpty()) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Clear search")) { onQueryChange("") },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Glyphs.CloseLine, contentDescription = tr("Clear search"), tint = EInk.Caption, modifier = Modifier.size(13.dp))
                }
            }
        }

        // Sort & filter icon button
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (hasActiveFilter) StoreOrange.copy(alpha = 0.16f) else EInk.Body)
                .border(0.5.dp, if (hasActiveFilter) StoreOrange else EInk.BodyStroke, CircleShape)
                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Sort and filter"), onClick = onOpenFilter),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Glyphs.Sliders,
                contentDescription = tr("Sort and filter"),
                tint = if (hasActiveFilter) StoreOrange else EInk.Value,
                modifier = Modifier.size(18.dp)
            )
        }

        if (onOpenMyShop != null) {
            Box(
                Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(EInk.Body)
                    .border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(50))
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("My Shop"), onClick = onOpenMyShop)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(tr("My Shop"), color = EInk.Value, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // + Sell pill button
        Box(
            Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(50))
                .background(StoreOrange)
                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Sell an item"), onClick = onSell)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("+ " + tr("Sell"), color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StoreFilterSheet(
    sort: StoreSort,
    inStockOnly: Boolean,
    onSort: (StoreSort) -> Unit,
    onInStockChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    BlockSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(tr("Sort & Filter"), color = EInk.Value, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
            StoreSort.entries.forEach { option ->
                ChoiceRow(label = tr(option.label), selected = sort == option) {
                    onSort(option)
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke, modifier = Modifier.padding(vertical = 10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { onInStockChange(!inStockOnly) }
                    .padding(vertical = 6.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tr("In stock only"), color = EInk.Value, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(tr("Hide items already marked as sold"), color = EInk.Caption, fontSize = 13.sp)
                }
                Switch(
                    checked = inStockOnly,
                    onCheckedChange = onInStockChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = StoreOrange,
                        uncheckedThumbColor = EInk.Caption,
                        uncheckedTrackColor = EInk.Track
                    )
                )
            }
        }
    }
}

/**
 * A single product card in the 2-column marketplace grid, inspired by Grailed/Depop/Shopee Mall:
 * 1:1 square image, multi-photo badge, SOLD badge, primary price in Sats/BTC and secondary fiat underneath,
 * title, and seller mini-row with NIP-05 verification badge.
 */
@Composable
internal fun ProductGridCard(
    listing: Listing,
    seller: Profile?,
    data: PriceData?,
    currency: String,
    isFollowed: Boolean,
    onClick: () -> Unit,
    onSellerClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val formattedPrice = remember(listing.price, data?.btcUsd, data?.btcThb, currency) {
        formatListingPrice(listing.price, data, currency)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(EInk.Body)
            .border(0.5.dp, EInk.BodyStroke, shape)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
    ) {
        // 1:1 square product image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(EInk.Track)
        ) {
            val cover = listing.images.firstOrNull()
            if (cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = listing.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Clean minimalist placeholder when a listing has no photo
                Column(
                    Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(NavIcons.Store.outline, contentDescription = null, tint = EInk.Caption, modifier = Modifier.size(34.dp))
                    Text(
                        listing.categories.firstOrNull()?.let { "#$it" } ?: "NIP-99",
                        color = EInk.Caption,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            // Multi-image count badge (top-right)
            if (listing.images.size > 1) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.68f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        "1/${listing.images.size}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // SOLD or Following badge (top-left)
            if (listing.sold) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(EInk.Alert)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(tr("SOLD"), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            } else if (isFollowed) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(tr("Following"), color = StoreOrange, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Column(Modifier.padding(10.dp)) {
            // Title (fixed 2 lines height for clean grid alignment)
            Text(
                text = listing.title,
                color = EInk.Value,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 18.sp,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Primary Price: Sats / BTC
            Text(
                text = formattedPrice.primary,
                color = if (listing.sold) EInk.Caption else StoreOrange,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp)
            )
            // Secondary Price: Fiat in parentheses underneath
            Text(
                text = formattedPrice.secondary ?: listing.location?.let { "📍 $it" } ?: " ",
                color = EInk.Caption,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp)
            )

            HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke, modifier = Modifier.padding(vertical = 8.dp))

            // Seller mini-row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Open seller shop"), onClick = onSellerClick)
            ) {
                Avatar(seller, listing.pubkey, size = 20)
                Spacer(Modifier.width(6.dp))
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = seller?.bestName ?: Profile.shortNpub(listing.pubkey),
                        color = EInk.Label,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isVerified(seller, listing.pubkey)) {
                        VerifiedBadge(13.dp, Modifier.padding(start = 3.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreGridSkeleton() {
    Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(3) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(2) {
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(EInk.Body)
                            .border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(16.dp))
                    ) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).background(EInk.Track))
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.fillMaxWidth(0.85f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(EInk.Track))
                            Box(Modifier.fillMaxWidth(0.55f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(EInk.Track))
                            Box(Modifier.fillMaxWidth(0.4f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(EInk.Track))
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// Product Detail Page (ListingDetailScreen)
// ============================================================================

@Composable
fun ListingDetailScreen(
    vm: SocialViewModel,
    address: String,
    data: PriceData?,
    currency: String,
    navigate: (NavKey) -> Unit,
    onBack: () -> Unit
) {
    val cachedListings by vm.listings.collectAsState()
    val fromCache = remember(cachedListings, address) { cachedListings?.firstOrNull { it.address == address } }
    val loaded by produceKept<Listing?>(fromCache, address) {
        value = vm.listing(address) ?: fromCache
    }
    val listing = fromCache ?: loaded

    val profiles by vm.profiles.collectAsState()
    val following by vm.following.collectAsState()
    val account by vm.account.collectAsState()
    val stats by vm.stats.collectAsState()
    val nwcWallet by vm.nwc.collectAsState()
    val context = LocalContext.current

    var checkoutOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var fullScreenImage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(listing?.pubkey) {
        listing?.pubkey?.let { pk ->
            if (profiles[pk] == null) vm.loadProfiles(listOf(pk))
        }
    }

    if (confirmDelete && listing != null) {
        ConfirmDialog(
            title = tr("Delete listing?"),
            text = tr("Relays will be asked to delete this product listing."),
            confirmLabel = tr("Delete"),
            onConfirm = {
                confirmDelete = false
                vm.deleteListing(listing) { onBack() }
            },
            onDismiss = { confirmDelete = false }
        )
    }

    if (checkoutOpen && listing != null) {
        CheckoutSheet(
            vm = vm,
            listing = listing,
            seller = profiles[listing.pubkey],
            data = data,
            currency = currency,
            loggedIn = account != null,
            hasNwc = nwcWallet != null,
            onLogin = { checkoutOpen = false; navigate(LoginKey) },
            onOpenChat = { checkoutOpen = false; navigate(ChatKey(listing.pubkey)) },
            onDismiss = { checkoutOpen = false }
        )
    }

    SocialScaffold(vm, navigate) { actions ->
        if (fullScreenImage != null && listing != null) {
            ImageViewer(
                url = fullScreenImage!!,
                note = listing.event,
                stats = stats[listing.event.id] ?: NoteStats(),
                actions = actions,
                onDismiss = { fullScreenImage = null },
                urls = listing.images,
                statusBar = { PublishStatusBar(vm) }
            )
            return@SocialScaffold
        }

        Column(Modifier.fillMaxSize().screenBackground()) {
            // Top bar with Share & Edit/More
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .screenBackground()
                    .statusBarsPadding()
                    .height(52.dp)
                    .padding(horizontal = 4.dp)
            ) {
                Text(
                    text = "←",
                    color = EInk.Value,
                    fontSize = 22.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Back"), onClick = onBack)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
                Text(
                    text = listing?.title ?: tr("Product"),
                    color = EInk.Value,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (listing != null) {
                    CircleIconButton(
                        icon = Glyphs.Share,
                        label = tr("Share listing"),
                        modifier = Modifier.padding(end = 8.dp),
                        onClick = { shareListing(context, listing) }
                    )
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)

            if (listing == null) {
                Spinner(Modifier.padding(top = 48.dp))
                return@Column
            }

            val seller = profiles[listing.pubkey]
            val isMine = account?.pubkey == listing.pubkey
            val formattedPrice = remember(listing.price, data?.btcUsd, data?.btcThb, currency) {
                formatListingPrice(listing.price, data, currency)
            }
            val blocks = remember(listing.event.id, listing.description) {
                Markdown.parse(listing.description)
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 28.dp)
                ) {
                    // 1. Swipeable Image Carousel
                    if (listing.images.isNotEmpty()) {
                        item(key = "gallery") {
                            ListingImageGallery(
                                images = listing.images,
                                sold = listing.sold,
                                onOpenFullScreen = { url -> fullScreenImage = url }
                            )
                        }
                    }

                    // 2. Price Hero + Title + Badges
                    item(key = "header") {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (listing.sold) EInk.Alert else StoreOrange.copy(alpha = 0.16f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = if (listing.sold) tr("SOLD") else tr("IN STOCK"),
                                        color = if (listing.sold) Color.White else StoreOrange,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                listing.location?.let { loc ->
                                    Text("📍 $loc", color = EInk.Caption, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Spacer(Modifier.weight(1f))
                                Text(relativeTime(listing.publishedAt), color = EInk.Caption, fontSize = 12.sp)
                            }

                            Text(
                                text = listing.title,
                                color = EInk.Value,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 28.sp,
                                modifier = Modifier.padding(top = 10.dp)
                            )

                            // Dual Price Hero Card (Primary Sats/BTC + Fiat Underneath)
                            Column(
                                Modifier
                                    .padding(top = 12.dp)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(EInk.Body)
                                    .border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = formattedPrice.primary,
                                    color = if (listing.sold) EInk.Caption else StoreOrange,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                formattedPrice.secondary?.let { fiat ->
                                    Text(
                                        text = fiat,
                                        color = EInk.Label,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                listing.shipping?.let { ship ->
                                    Text(
                                        text = tr("Shipping: {0}", ship),
                                        color = EInk.Caption,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                            }

                            // Category tags
                            if (listing.categories.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(top = 12.dp)
                                ) {
                                    listing.categories.forEach { tag ->
                                        Box(
                                            Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(EInk.Body)
                                                .border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(50))
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("#$tag", color = EInk.Label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }
                            }

                            listing.summary?.takeIf { it != listing.description }?.let { sum ->
                                Text(
                                    text = sum,
                                    color = EInk.Label,
                                    fontSize = 15.sp,
                                    lineHeight = 21.sp,
                                    modifier = Modifier.padding(top = 12.dp)
                                )
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
                    }

                    // 3. Seller Trust Card
                    item(key = "seller") {
                        SellerTrustCard(
                            pubkey = listing.pubkey,
                            seller = seller,
                            isFollowed = listing.pubkey in following,
                            onOpenProfile = { navigate(ProfileKey(listing.pubkey)) },
                            onOpenShop = { navigate(ShopKey(listing.pubkey)) }
                        )
                        HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
                    }

                    // 4. Product Description (Markdown)
                    if (blocks.isNotEmpty()) {
                        item(key = "desc_title") {
                            Text(
                                text = tr("Product Details"),
                                color = EInk.Value,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)
                            )
                        }
                        items(blocks.size, key = { "md_$it" }) { i ->
                            MarkdownBlock(blocks[i], actions, navigate)
                        }
                    }

                    // 5. Quote / Repost / Zap social engagement row
                    item(key = "social") {
                        val noteStats = stats[listing.event.id] ?: NoteStats()
                        HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke, modifier = Modifier.padding(top = 12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = (if (noteStats.likedByMe) "♥ " else "♡ ") + noteStats.likes,
                                color = if (noteStats.likedByMe) StoreOrange else EInk.Value,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button) { actions.like(listing.event) }.padding(vertical = 4.dp)
                            )
                            Text(
                                text = "⚡ " + (noteStats.zapMsat / 1000),
                                color = EInk.Value,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button) { actions.zap(listing.event) }.padding(vertical = 4.dp)
                            )
                            Text(
                                text = tr("Quote in post"),
                                color = EInk.Label,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button) {
                                    if (account == null) navigate(LoginKey)
                                    else navigate(ComposeKey(quote = listing.event.id))
                                }.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Sticky Bottom Action Bar
            HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
            if (isMine && listing.editable) {
                SellerBottomBar(
                    listing = listing,
                    onEdit = { navigate(EditListingKey(listing.address)) },
                    onToggleSold = {
                        val me = account?.pubkey ?: return@SellerBottomBar
                        val updated = Marketplace.build(
                            pubkey = me,
                            identifier = listing.identifier,
                            title = listing.title,
                            summary = listing.summary,
                            description = listing.description,
                            images = listing.images,
                            price = listing.price ?: Price(0.0, "SATS"),
                            location = listing.location,
                            categories = listing.categories,
                            sold = !listing.sold,
                            shipping = listing.shipping,
                            publishedAt = listing.publishedAt
                        )
                        vm.publishListing(updated) {}
                    },
                    onDelete = { confirmDelete = true }
                )
            } else {
                BuyerBottomBar(
                    sold = listing.sold,
                    onAskSeller = {
                        if (account == null) navigate(LoginKey) else navigate(ChatKey(listing.pubkey))
                    },
                    onBuy = { checkoutOpen = true }
                )
            }
        }
    }
}

@Composable
private fun ListingImageGallery(
    images: List<String>,
    sold: Boolean,
    onOpenFullScreen: (String) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { images.size })
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(EInk.Track)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val url = images[page]
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("View image")) { onOpenFullScreen(url) }
                )
            }

            if (images.size > 1) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        "${pagerState.currentPage + 1} / ${images.size}",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (sold) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(EInk.Alert)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(tr("SOLD"), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Thumbnail strip
        if (images.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(images.size) { index ->
                    val selected = pagerState.currentPage == index
                    AsyncImage(
                        model = images[index],
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(if (selected) 2.dp else 0.5.dp, if (selected) StoreOrange else EInk.BodyStroke, RoundedCornerShape(10.dp))
                            .clickable(role = androidx.compose.ui.semantics.Role.Button) { scope.launch { pagerState.animateScrollToPage(index) } }
                    )
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
        }
    }
}

@Composable
private fun SellerTrustCard(
    pubkey: String,
    seller: Profile?,
    isFollowed: Boolean,
    onOpenProfile: () -> Unit,
    onOpenShop: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onOpenShop)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Avatar(seller, pubkey, size = 46, onClick = onOpenProfile)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = seller?.bestName ?: Profile.shortNpub(pubkey),
                    color = EInk.Value,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isVerified(seller, pubkey)) {
                    VerifiedBadge(16.dp, Modifier.padding(start = 4.dp))
                }
            }
            Text(
                text = when {
                    isFollowed -> tr("✓ Followed by you · Trusted in your network")
                    !seller?.nip05.isNullOrBlank() -> seller.nip05.orEmpty()
                    else -> Profile.shortNpub(pubkey)
                },
                color = if (isFollowed) StoreOrange else EInk.Caption,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        PillButton(tr("View Shop"), filled = false, onClick = onOpenShop)
    }
}

@Composable
private fun BuyerBottomBar(
    sold: Boolean,
    onAskSeller: () -> Unit,
    onBuy: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .screenBackground()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // Secondary: Ask Seller (NIP-17 encrypted DM)
        Box(
            Modifier
                .height(46.dp)
                .clip(RoundedCornerShape(50))
                .border(1.dp, EInk.BodyStroke, RoundedCornerShape(50))
                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onAskSeller)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Glyphs.Chat, contentDescription = null, tint = EInk.Value, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("Ask seller"), color = EInk.Value, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Primary: Buy (opens the checkout)
        Box(
            Modifier
                .weight(1f)
                .height(46.dp)
                .alpha(if (sold) 0.45f else 1f)
                .clip(RoundedCornerShape(50))
                .background(if (sold) EInk.Track else StoreOrange)
                .clickable(role = androidx.compose.ui.semantics.Role.Button, enabled = !sold, onClick = onBuy)
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (sold) tr("Sold out") else tr("Checkout"),
                color = if (sold) EInk.Caption else Color.Black,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SellerBottomBar(
    listing: Listing,
    onEdit: () -> Unit,
    onToggleSold: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .screenBackground()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        PillButton(
            text = tr("Edit"),
            filled = true,
            modifier = Modifier.weight(1f),
            onClick = onEdit
        )
        PillButton(
            text = if (listing.sold) tr("Mark available") else tr("Mark sold"),
            filled = false,
            modifier = Modifier.weight(1f),
            onClick = onToggleSold
        )
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .border(1.dp, EInk.BodyStroke, CircleShape)
                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Delete listing"), onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Icon(Glyphs.Delete, contentDescription = tr("Delete listing"), tint = EInk.Alert, modifier = Modifier.size(19.dp))
        }
    }
}

/**
 * Checkout Bottom Sheet:
 * - Item summary + live price in Sats/BTC (฿/$)
 * - Optional shipping address / note for the seller (sent over encrypted NIP-17 DM)
 * - "⚡ Pay via Lightning" when the seller has a Lightning address
 * - "Message seller" to send the order note first
 */
@Composable
private fun CheckoutSheet(
    vm: SocialViewModel,
    listing: Listing,
    seller: Profile?,
    data: PriceData?,
    currency: String,
    loggedIn: Boolean,
    hasNwc: Boolean,
    onLogin: () -> Unit,
    onOpenChat: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val formattedPrice = remember(listing.price, data?.btcUsd, data?.btcThb, currency) {
        formatListingPrice(listing.price, data, currency)
    }
    var deliveryNote by rememberSaveable { mutableStateOf("") }
    var sendDmWithOrder by rememberSaveable { mutableStateOf(true) }

    fun buildOrderMessage(paymentMethod: String): String = buildString {
        append("🛒 Order: ${listing.title}\n")
        append("Price: ${formattedPrice.primary}")
        formattedPrice.secondary?.let { append(" $it") }
        append("\nPayment: $paymentMethod")
        if (deliveryNote.isNotBlank()) {
            append("\n\nShipping / Note:\n${deliveryNote.trim()}")
        }
        append("\n\nnostr:${listing.naddr()}")
    }

    BlockSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = tr("Checkout"),
                color = EInk.Value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = tr("Direct peer-to-peer payment to the seller with zero platform fees."),
                color = EInk.Caption,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 14.dp)
            )

            // Item summary box
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(EInk.Body)
                    .border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                val cover = listing.images.firstOrNull()
                if (cover != null) {
                    AsyncImage(
                        model = cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(EInk.Track)
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(listing.title, color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = formattedPrice.primary + (formattedPrice.secondary?.let { " $it" } ?: ""),
                        color = StoreOrange,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    Text(
                        text = tr("Seller: {0}", seller?.bestName ?: Profile.shortNpub(listing.pubkey)),
                        color = EInk.Caption,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Shipping / order note (sent via encrypted NIP-17 DM)
            OutlinedTextField(
                value = deliveryNote,
                onValueChange = { deliveryNote = it.take(500) },
                label = { Text(tr("Shipping address or note to seller (optional)")) },
                placeholder = { Text(tr("Name, delivery address, or contact details…"), color = EInk.Caption) },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = StoreOrange,
                    unfocusedBorderColor = EInk.BodyStroke,
                    focusedTextColor = EInk.Value,
                    unfocusedTextColor = EInk.Value,
                    cursorColor = StoreOrange,
                    focusedLabelColor = EInk.Label,
                    unfocusedLabelColor = EInk.Caption
                ),
                modifier = Modifier.fillMaxWidth()
            )

            if (loggedIn) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) { sendDmWithOrder = !sendDmWithOrder }
                        .padding(vertical = 8.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Send encrypted order DM (NIP-17)"), color = EInk.Value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(tr("Notifies the seller in private chat with your order & note"), color = EInk.Caption, fontSize = 12.sp)
                    }
                    Switch(
                        checked = sendDmWithOrder,
                        onCheckedChange = { sendDmWithOrder = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = StoreOrange,
                            uncheckedThumbColor = EInk.Caption,
                            uncheckedTrackColor = EInk.Track
                        )
                    )
                }
            } else {
                Text(
                    text = tr("Log in to send your shipping note to the seller over encrypted Nostr chat ›"),
                    color = StoreOrange,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(vertical = 8.dp)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onLogin)
                )
            }

            Spacer(Modifier.height(10.dp))

            // Pay via Lightning / NWC when the seller has a Lightning address and the price is known
            val lud16 = seller?.lud16?.trim().orEmpty()
            val sats = formattedPrice.sats
            if (lud16.isNotEmpty() && sats != null && sats > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(50))
                        .background(StoreOrange)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                            if (loggedIn && (sendDmWithOrder || deliveryNote.isNotBlank())) {
                                vm.sendOrder(listing.pubkey, buildOrderMessage("Lightning ($lud16)"))
                            }
                            onDismiss()
                            vm.payLightning(seller = listing.pubkey, amountSat = sats) { invoice -> payInvoice(context, invoice) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (hasNwc) "⚡ " + tr("Pay via Lightning (NWC)") else "⚡ " + tr("Pay via Lightning"),
                        color = Color.Black,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Text(
                    tr("This seller hasn't set up a Lightning address. Message them to arrange payment."),
                    color = EInk.Caption, fontSize = 13.sp, lineHeight = 18.sp
                )
            }

            // Tertiary: Message seller first
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                        if (!loggedIn) onLogin()
                        else if (deliveryNote.isNotBlank()) {
                            vm.sendOrder(listing.pubkey, buildOrderMessage("Pending / Inquiry")) {
                                onOpenChat()
                            }
                        } else {
                            onOpenChat()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tr("Message seller on Nostr Chat"),
                    color = EInk.Label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun shareListing(context: Context, listing: Listing) {
    val link = "https://njump.me/" + listing.naddr()
    val priceLine = listing.price?.let { " (" + Marketplace.formatRaw(it) + ")" }.orEmpty()
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, "${listing.title}$priceLine\n$link")
    context.startActivity(Intent.createChooser(send, tr("Share listing")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

// ============================================================================
// Seller Storefront (ShopScreen)
// ============================================================================

@Composable
fun ShopScreen(
    vm: SocialViewModel,
    pubkey: String,
    data: PriceData?,
    currency: String,
    navigate: (NavKey) -> Unit,
    onBack: () -> Unit
) {
    val profiles by vm.profiles.collectAsState()
    val following by vm.following.collectAsState()
    val account by vm.account.collectAsState()
    val seller = profiles[pubkey]
    val isMine = account?.pubkey == pubkey

    var reload by rememberSaveable { mutableStateOf(0) }
    val shopItems by produceKept<List<Listing>?>(null, pubkey, reload) {
        value = vm.shopListings(pubkey)
    }

    LaunchedEffect(pubkey) {
        if (seller == null) vm.loadProfiles(listOf(pubkey))
    }

    Column(Modifier.fillMaxSize().screenBackground()) {
        SocialTopBar(
            title = seller?.bestName?.let { tr("{0}'s Shop", it) } ?: tr("Shop"),
            onBack = onBack
        )

        val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 700
        LazyVerticalGrid(
            columns = if (wide) GridCells.Adaptive(minSize = 210.dp) else GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "shop_header") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(EInk.Body)
                        .border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(seller, pubkey, size = 54, onClick = { navigate(ProfileKey(pubkey)) })
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            DisplayName(seller, pubkey, EInk.Value, 18.sp)
                            Text(
                                text = handleText(seller, pubkey),
                                color = EInk.Caption,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (isMine) {
                            PillButton("+ " + tr("Add item")) { navigate(EditListingKey(null)) }
                        } else {
                            PillButton(tr("Profile"), filled = false) { navigate(ProfileKey(pubkey)) }
                        }
                    }
                    seller?.about?.takeIf { it.isNotBlank() }?.let { bio ->
                        Text(
                            text = bio,
                            color = EInk.Label,
                            fontSize = 13.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }
            }

            when (val items = shopItems) {
                null -> item(span = { GridItemSpan(maxLineSpan) }, key = "loading") {
                    Spinner(Modifier.padding(top = 32.dp))
                }
                else -> if (items.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                        EmptyNote(
                            if (isMine) tr("You haven't listed any products yet. Tap '+ Add item' to start selling.")
                            else tr("This seller has no active listings on your relays.")
                        )
                    }
                } else {
                    items(items, key = { it.address }) { item ->
                        ProductGridCard(
                            listing = item,
                            seller = seller,
                            data = data,
                            currency = currency,
                            isFollowed = pubkey in following,
                            onClick = { navigate(ListingKey(item.address)) },
                            onSellerClick = { navigate(ProfileKey(pubkey)) }
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// Seller Studio: Create / Edit NIP-99 Listing (EditListingScreen)
// ============================================================================

@Composable
fun EditListingScreen(
    vm: SocialViewModel,
    address: String?,
    navigate: (NavKey) -> Unit,
    onBack: () -> Unit
) {
    val account by vm.account.collectAsState()
    val cachedListings by vm.listings.collectAsState()
    val existing by produceKept<Listing?>(
        address?.let { a -> cachedListings?.firstOrNull { it.address == a } },
        address
    ) {
        if (address != null && value == null) value = vm.listing(address)
    }

    var initialized by rememberSaveable(address) { mutableStateOf(address == null) }
    var title by rememberSaveable { mutableStateOf("") }
    var summary by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var priceAmount by rememberSaveable { mutableStateOf("") }
    var priceCurrency by rememberSaveable { mutableStateOf("SATS") }
    var location by rememberSaveable { mutableStateOf("") }
    var shipping by rememberSaveable { mutableStateOf("") }
    var selectedCategories by rememberSaveable { mutableStateOf(listOf<String>()) }
    var customTags by rememberSaveable { mutableStateOf("") }
    var sold by rememberSaveable { mutableStateOf(false) }
    var imageUrls by rememberSaveable { mutableStateOf(listOf<String>()) }
    var uploadingCount by remember { mutableStateOf(0) }
    var publishing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(existing) {
        val l = existing ?: return@LaunchedEffect
        if (!initialized) {
            title = l.title
            summary = l.summary.orEmpty()
            description = l.description
            priceAmount = l.price?.amount?.let { amt ->
                if (amt % 1.0 == 0.0) amt.toLong().toString() else amt.toBigDecimal().stripTrailingZeros().toPlainString()
            }.orEmpty()
            priceCurrency = l.price?.currency ?: "SATS"
            location = l.location.orEmpty()
            shipping = l.shipping.orEmpty()
            val presetIds = STORE_CATEGORIES.drop(2).map { it.id }.toSet()
            selectedCategories = l.categories.filter { it in presetIds }
            customTags = l.categories.filter { it !in presetIds }.joinToString(", ")
            sold = l.sold
            imageUrls = l.images
            initialized = true
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            uploadingCount++
            vm.uploadImage(uri) { attachment ->
                uploadingCount--
                if (attachment != null && attachment.url !in imageUrls) {
                    imageUrls = imageUrls + attachment.url
                }
            }
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = StoreOrange,
        unfocusedBorderColor = EInk.BodyStroke,
        focusedTextColor = EInk.Value,
        unfocusedTextColor = EInk.Value,
        cursorColor = StoreOrange,
        focusedLabelColor = EInk.Label,
        unfocusedLabelColor = EInk.Caption
    )

    Column(Modifier.fillMaxSize().screenBackground()) {
        SocialTopBar(
            title = if (address == null) tr("List an item") else tr("Edit listing"),
            onBack = onBack
        )

        if (account == null) {
            EmptyNote(tr("Log in with your Nostr account to list items for sale."))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PillButton(tr("Log in")) { navigate(LoginKey) }
            }
            return@Column
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Photos section
            Text(tr("Photos"), color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(imageUrls.size) { index ->
                    val url = imageUrls[index]
                    AttachmentTile(
                        attachment = SocialViewModel.Attachment(url, "image/jpeg", "", 1, 1),
                        single = false,
                        onMoveLeft = if (index > 0) ({
                            val m = imageUrls.toMutableList()
                            val t = m[index - 1]; m[index - 1] = m[index]; m[index] = t
                            imageUrls = m
                        }) else null,
                        onMoveRight = if (index < imageUrls.lastIndex) ({
                            val m = imageUrls.toMutableList()
                            val t = m[index + 1]; m[index + 1] = m[index]; m[index] = t
                            imageUrls = m
                        }) else null,
                        onRemove = { imageUrls = imageUrls.filterIndexed { i, _ -> i != index } }
                    )
                }
                if (uploadingCount > 0) {
                    item {
                        AttachmentTile(attachment = null, single = false, onRemove = {})
                    }
                }
                if (imageUrls.size < 8) {
                    item {
                        Box(
                            Modifier
                                .size(150.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(EInk.Body)
                                .border(1.dp, EInk.BodyStroke, RoundedCornerShape(16.dp))
                                .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Glyphs.Photo, contentDescription = null, tint = StoreOrange, modifier = Modifier.size(28.dp))
                                Text(
                                    tr("Add photo"),
                                    color = EInk.Value,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(120); error = null },
                label = { Text(tr("Title *")) },
                placeholder = { Text(tr("e.g. Bitaxe Supra 600 GH/s Solo Miner"), color = EInk.Caption) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )

            // 3. Price & Currency
            Text(tr("Price *"), color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = priceAmount,
                    onValueChange = { v -> priceAmount = v.filter { it.isDigit() || it == '.' }.take(14); error = null },
                    label = { Text(tr("Amount")) },
                    placeholder = { Text(if (priceCurrency == "SATS") "21000" else "1500", color = EInk.Caption) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("SATS", "BTC", "THB", "USD").forEach { cur ->
                        ChoicePill(
                            label = cur,
                            selected = priceCurrency == cur,
                            onClick = { priceCurrency = cur }
                        )
                    }
                }
            }

            // 4. Category pills
            Text(tr("Categories"), color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                STORE_CATEGORIES.drop(2).forEach { chip ->
                    val on = chip.id in selectedCategories
                    ChoicePill(
                        label = tr(chip.label),
                        selected = on,
                        onClick = {
                            selectedCategories = if (on) selectedCategories - chip.id else selectedCategories + chip.id
                        }
                    )
                }
            }

            OutlinedTextField(
                value = customTags,
                onValueChange = { customTags = it.take(120) },
                label = { Text(tr("Extra tags (comma-separated)")) },
                placeholder = { Text("bitcoin, nostr, siamstr", color = EInk.Caption) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )

            // 5. Short summary + Markdown description
            OutlinedTextField(
                value = summary,
                onValueChange = { summary = it.take(200) },
                label = { Text(tr("Short summary (optional)")) },
                placeholder = { Text(tr("Condition, highlights…"), color = EInk.Caption) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it.take(4000) },
                label = { Text(tr("Description (Markdown supported)")) },
                placeholder = { Text(tr("Full details, specs, what's included in the box…"), color = EInk.Caption) },
                minLines = 4,
                maxLines = 10,
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth()
            )

            // 6. Location & Shipping & On-chain BTC Address
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it.take(80) },
                    label = { Text(tr("Location")) },
                    placeholder = { Text("Bangkok, TH", color = EInk.Caption) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = shipping,
                    onValueChange = { shipping = it.take(80) },
                    label = { Text(tr("Shipping")) },
                    placeholder = { Text(tr("Free / Worldwide"), color = EInk.Caption) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = fieldColors,
                    modifier = Modifier.weight(1f)
                )
            }

            if (existing != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) { sold = !sold }
                        .padding(vertical = 4.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Mark as sold"), color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(tr("Keeps the listing visible as SOLD"), color = EInk.Caption, fontSize = 12.sp)
                    }
                    Switch(
                        checked = sold,
                        onCheckedChange = { sold = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = StoreOrange,
                            uncheckedThumbColor = EInk.Caption,
                            uncheckedTrackColor = EInk.Track
                        )
                    )
                }
            }

            error?.let {
                Text(it, color = EInk.Alert, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }

        // Bottom publish button
        HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
        Box(
            Modifier
                .fillMaxWidth()
                .screenBackground()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            val ready = title.isNotBlank() && (priceAmount.toDoubleOrNull() ?: -1.0) >= 0.0 && uploadingCount == 0 && !publishing
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .alpha(if (ready) 1f else 0.45f)
                    .clip(RoundedCornerShape(50))
                    .background(StoreOrange)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, enabled = ready) {
                        val me = account?.pubkey ?: return@clickable
                        val amt = priceAmount.toDoubleOrNull()
                        if (title.isBlank() || amt == null || amt < 0.0) {
                            error = tr("Please enter a title and valid price.")
                            return@clickable
                        }
                        val tags = (selectedCategories + customTags.split(',', ' ').map { it.trim().removePrefix("#").lowercase() })
                            .filter { it.isNotEmpty() }
                            .distinct()
                        val id = existing?.identifier?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString().take(12)
                        val unsigned = Marketplace.build(
                            pubkey = me,
                            identifier = id,
                            title = title,
                            summary = summary.ifBlank { null },
                            description = description,
                            images = imageUrls,
                            price = Price(amt, priceCurrency),
                            location = location.ifBlank { null },
                            categories = tags,
                            sold = sold,
                            shipping = shipping.ifBlank { null },
                            publishedAt = existing?.publishedAt ?: (System.currentTimeMillis() / 1000)
                        )
                        publishing = true
                        vm.publishListing(unsigned) { published ->
                            publishing = false
                            if (published != null) {
                                onBack()
                                if (address == null) navigate(ListingKey(published.address))
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        publishing -> tr("Publishing…")
                        uploadingCount > 0 -> tr("Uploading photo…")
                        existing != null -> tr("Save changes")
                        else -> tr("Publish listing")
                    },
                    color = Color.Black,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
