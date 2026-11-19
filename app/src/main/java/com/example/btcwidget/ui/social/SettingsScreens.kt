package com.example.btcwidget.ui.social

import com.example.btcwidget.ui.main.TextSize
import com.example.btcwidget.ui.main.screenBackground
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.ui.main.EInk

/**
 * The settings pages, in the order the Settings list shows them. Pages that also appear in the
 * bars or the side panel (your account, notifications, Bitcoin) use the same icon there.
 */
/** [topLevel] pages are listed on Settings; the others open from within one (Proof of work, from Advanced). */
enum class SettingsPage(val title: String, val summary: String, val icon: () -> androidx.compose.ui.graphics.vector.ImageVector, val topLevel: Boolean = true) {
    ACCOUNT("Your account", "Accounts on this phone, your key, scheduled posts, log out", { NavIcons.Profile.outline }),
    NOTIFICATIONS("Notifications", "What Block tells you about while it's closed", { NavIcons.Bell.outline }),
    PRIVACY("Privacy and safety", "Muted words, web of trust, community rules", { Glyphs.Shield }),
    DISPLAY("Display and language", "Theme, language, Bitcoin cards, videos, links", { Glyphs.Eye }),
    WALLET("Wallet and zaps", "Connect a Lightning wallet to zap in one tap", { Glyphs.Zap }),
    BITCOIN("Bitcoin and widgets", "Your stack, currency, price sources, refresh", { NavIcons.Bitcoin.outline }),
    ADVANCED("Advanced", "Relays, message inbox, media servers, storage and cache", { Glyphs.Server }),
    ABOUT("About Block", "Version, privacy policy, community rules", { Glyphs.Info }),
    POW("Proof of work", "Mine your posts so spam filters trust them (NIP-13)", { Glyphs.Pickaxe }, topLevel = false);

    companion object {
        /** A page by name; pages folded into Advanced (relays, storage) open it. */
        fun of(name: String) = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: if (name.uppercase() in setOf("RELAYS", "STORAGE")) ADVANCED else ACCOUNT
    }
}

/** Settings worth finding by name from the search box, and the page each is on. */
private val SEARCHABLE: List<Pair<String, SettingsPage>> = listOf(
    "Accounts on this phone" to SettingsPage.ACCOUNT, "Add account" to SettingsPage.ACCOUNT, "Log out" to SettingsPage.ACCOUNT,
    "Your private key (nsec)" to SettingsPage.ACCOUNT, "Scheduled posts" to SettingsPage.ACCOUNT, "Edit profile" to SettingsPage.ACCOUNT,
    "Phone notifications" to SettingsPage.NOTIFICATIONS, "Replies" to SettingsPage.NOTIFICATIONS, "Mentions" to SettingsPage.NOTIFICATIONS,
    "Zaps" to SettingsPage.NOTIFICATIONS, "Messages" to SettingsPage.NOTIFICATIONS,
    "Muted words and hashtags" to SettingsPage.PRIVACY, "Muted conversations" to SettingsPage.PRIVACY, "Web of trust" to SettingsPage.PRIVACY,
    "Private bookmarks and mutes" to SettingsPage.PRIVACY, "Community rules" to SettingsPage.PRIVACY,
    "Theme" to SettingsPage.DISPLAY, "Bitcoin in posts" to SettingsPage.DISPLAY, "Ember" to SettingsPage.DISPLAY, "Language" to SettingsPage.DISPLAY, "Open links in" to SettingsPage.DISPLAY, "Video autoplay" to SettingsPage.DISPLAY, "Video quality" to SettingsPage.DISPLAY,
    "Relays" to SettingsPage.ADVANCED, "Message inbox relays" to SettingsPage.ADVANCED, "Media servers" to SettingsPage.ADVANCED,
    "Wallet (NWC)" to SettingsPage.WALLET,
    "Proof of work" to SettingsPage.POW, "Difficulty" to SettingsPage.POW, "Match what my relays ask for" to SettingsPage.POW,
    "Storage and cache" to SettingsPage.ADVANCED, "Event database" to SettingsPage.ADVANCED, "Media cache" to SettingsPage.ADVANCED,
    "Your stack" to SettingsPage.BITCOIN, "Widgets" to SettingsPage.BITCOIN, "Currency" to SettingsPage.BITCOIN, "Widget theme" to SettingsPage.BITCOIN,
    "Refresh every" to SettingsPage.BITCOIN, "BTC price" to SettingsPage.BITCOIN, "THB price" to SettingsPage.BITCOIN,
    "Privacy policy" to SettingsPage.ABOUT, "Version" to SettingsPage.ABOUT,
)

/**
 * The Settings page: a search box and the categories, each opening its own page, as on X.
 * [accountName] shows under "Your account" (null when logged out).
 */
@Composable
fun SettingsHome(accountName: String?, onOpen: (SettingsPage) -> Unit, needsAttention: Set<SettingsPage> = emptySet()) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().screenBackground().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            SettingsTextField(
                value = query, onValueChange = { query = it.take(40) }, placeholder = tr("Search settings"),
                modifier = Modifier.fillMaxWidth()
            )
        }
        val q = query.trim()
        if (q.isNotEmpty()) {
            val hits = SEARCHABLE.filter { (title, page) -> tr(title).contains(q, ignoreCase = true) || title.contains(q, ignoreCase = true) || tr(page.title).contains(q, ignoreCase = true) }
            if (hits.isEmpty()) SettingsNote(tr("No settings match \"{0}\".", q))
            hits.forEach { (title, page) -> SettingsLinkRow(tr(title), summary = tr(page.title)) { onOpen(page) } }
            return@Column
        }
        SettingsPage.entries.filter { it.topLevel }.forEach { page ->
            val summary = if (page == SettingsPage.ACCOUNT && accountName != null) accountName + " · " + tr(page.summary) else tr(page.summary)
            SettingsLinkRow(tr(page.title), summary = summary, icon = page.icon(), badge = page in needsAttention) { onOpen(page) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * One settings page, full screen with a back arrow. [bitcoin] draws the Bitcoin and widgets
 * page (its settings live with the price data); [appVersion] shows on About.
 */
@Composable
fun SettingsPageScreen(
    vm: SocialViewModel,
    page: SettingsPage,
    navigate: (NavKey) -> Unit,
    onBack: () -> Unit,
    appVersion: String? = null,
    bitcoin: @Composable () -> Unit = {},
) {
    val account by vm.account.collectAsState()
    Column(Modifier.fillMaxSize().screenBackground()) {
        SocialTopBar(tr(page.title), onBack)
        // The wallet is its own screen (also a tab in the bottom bar), with its own scrolling.
        if (page == SettingsPage.WALLET) {
            WalletScreen(vm, navigate)
            return@Column
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            val needsAccount = page in setOf(SettingsPage.NOTIFICATIONS, SettingsPage.PRIVACY)
            if (needsAccount && account == null) {
                SettingsNote(tr("Log in to change these settings."))
                SettingsLinkRow(tr("Log in or create account")) { navigate(com.example.btcwidget.LoginKey) }
                return@Column
            }
            when (page) {
                SettingsPage.ACCOUNT -> AccountPage(vm, navigate)
                SettingsPage.NOTIFICATIONS -> NotificationsPage(vm)
                SettingsPage.PRIVACY -> PrivacyPage(vm, navigate)
                SettingsPage.DISPLAY -> DisplayPage(vm)
                SettingsPage.WALLET -> Unit // shown above
                SettingsPage.BITCOIN -> bitcoin()
                SettingsPage.ADVANCED -> {
                    RelaysPage(vm, navigate)
                    SettingsDivider()
                    PowLinkRow(vm) { navigate(com.example.btcwidget.SettingsKey(SettingsPage.POW.name)) }
                    SettingsDivider()
                    StoragePage(vm)
                }
                SettingsPage.POW -> PowSettingsPage(vm)
                SettingsPage.ABOUT -> AboutPage(appVersion, navigate)
            }
        }
    }
}

// ---- About ----

@Composable
private fun AboutPage(appVersion: String?, navigate: (NavKey) -> Unit) {
    val uri = LocalUriHandler.current
    SettingsLinkRow(tr("Version"), value = appVersion ?: "–", chevron = false) {}
    SettingsLinkRow(tr("Privacy policy")) { uri.openUri(com.example.btcwidget.ui.main.PRIVACY_POLICY_URL) }
    SettingsLinkRow(tr("Community rules")) { navigate(com.example.btcwidget.RulesKey) }
    SettingsDivider()
    SettingsHeader(tr("Data and credits"))
    SettingsNote(tr("Prices: Coinbase, Kraken, Bitkub · Network: mempool.space · Supply: blockchain.info · MSTR, ASST: Yahoo Finance, CoinGecko"))
    SettingsNote(tr("Type: Inter, Noto Sans Thai, Oswald, VT323 (SIL Open Font License)"))
}

/** A saved account with its actions, as listed when adding or switching accounts at login. */
@Composable
internal fun AccountRow(profile: Profile?, pubkey: String, note: String, actions: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(profile, pubkey, size = 36)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(profile?.bestName ?: Profile.shortNpub(pubkey), color = EInk.Value, fontSize = TextSize.Body, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(note, color = EInk.Caption, fontSize = TextSize.Caption)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { actions() }
    }
}
