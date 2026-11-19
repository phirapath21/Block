package com.example.btcwidget.ui.main

import com.example.btcwidget.ui.social.tr
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.ripple
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey

/**
 * Connects [MainScreenViewModel] to [MainScreenContent]. State lives in the ViewModel;
 * this keeps only what needs the Activity: the selected tab and the notification
 * permission prompt.
 */
@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel()
) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    val social: com.example.btcwidget.ui.social.SocialViewModel = viewModel()
    val account by social.account.collectAsStateWithLifecycle()
    val unreadNotifications by social.unreadNotifications.collectAsStateWithLifecycle()
    val unreadChats by social.unreadConversations.collectAsStateWithLifecycle()
    val pushEnabled by social.pushEnabled.collectAsStateWithLifecycle()
    val nwcWallet by social.wallet.nwc.collectAsStateWithLifecycle()
    val notificationItems by social.notifications.collectAsStateWithLifecycle()

    // Badges: load notifications and messages when the account is known and on every return.
    val appContext = LocalContext.current.applicationContext
    LaunchedEffect(account) { social.refreshBadges(); social.startRealtimeNotifications(); com.example.btcwidget.nostr.InstantPush.sync(appContext) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        social.refreshBadges(); social.startRealtimeNotifications()
        // Instant notifications: (re)start the connection, or stop it after logging out.
        com.example.btcwidget.nostr.InstantPush.sync(appContext)
    }
    // Opening the Notifications tab marks them seen.
    LaunchedEffect(selectedTab, notificationItems) {
        if (selectedTab == MainTab.NOTIFICATIONS) social.markNotificationsSeen()
    }
    // A tapped phone notification opens its tab.
    val requestedTab by com.example.btcwidget.OpenTabRequest.tab.collectAsStateWithLifecycle()
    LaunchedEffect(requestedTab) {
        requestedTab?.let { name ->
            runCatching { MainTab.valueOf(name) }.getOrNull()?.let { selectedTab = it }
            com.example.btcwidget.OpenTabRequest.tab.value = null
        }
    }

    LaunchedEffect(Unit) { viewModel.start() }

    MainScreenContent(
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it },
        onItemClick = onItemClick,
        modifier = modifier,
        homeContent = { com.example.btcwidget.ui.social.HomeScreen(social, onItemClick) },
        searchContent = { leadingMenu -> com.example.btcwidget.ui.social.SearchScreen(social, onItemClick, onBack = null, leadingMenu = leadingMenu) },
        liveContent = { com.example.btcwidget.ui.social.LiveList(social, onItemClick) },
        walletContent = { com.example.btcwidget.ui.social.WalletScreen(social, onItemClick) },
        messagesContent = { com.example.btcwidget.ui.social.MessagesScreen(social, onItemClick) },
        bookmarksContent = { com.example.btcwidget.ui.social.BookmarksScreen(social, onItemClick) },
        listsContent = { com.example.btcwidget.ui.social.ListsScreen(social, onItemClick) },
        drawerHeader = { com.example.btcwidget.ui.social.DrawerAccountHeader(social, onLogin = { onItemClick(com.example.btcwidget.LoginKey) }, navigate = onItemClick) },
        menuButtonIcon = { com.example.btcwidget.ui.social.MenuAvatar(social) },
        // Long-press your avatar in the bottom bar to switch accounts, as on X.
        accountSwitcher = if (account != null) { dismiss -> com.example.btcwidget.ui.social.AccountSwitcherSheet(social, onItemClick, dismiss) } else null,
        badges = mapOf(MainTab.NOTIFICATIONS to unreadNotifications, MainTab.MESSAGES to unreadChats.size),
        notificationsContent = { com.example.btcwidget.ui.social.NotificationsScreen(social, onItemClick) },
        profileContent = {
            val me = account?.pubkey
            if (me != null) {
                com.example.btcwidget.ui.social.ProfileScreen(social, me, onItemClick, onBack = null)
            } else {
                com.example.btcwidget.ui.social.HomeScreen(social, onItemClick)
            }
        },
        settingsContent = {
            com.example.btcwidget.ui.social.SettingsHome(
                accountName = account?.let { a -> social.profiles.value[a.pubkey]?.bestName ?: com.example.btcwidget.nostr.core.Profile.shortNpub(a.pubkey) },
                onOpen = { page -> onItemClick(com.example.btcwidget.SettingsKey(page.name)) },
                needsAttention = if (social.keyBackup.needed.collectAsState().value) setOf(com.example.btcwidget.ui.social.SettingsPage.ACCOUNT) else emptySet()
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenContent(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    homeContent: @Composable () -> Unit = {},
    searchContent: @Composable (leadingMenu: (@Composable () -> Unit)?) -> Unit = {},
    notificationsContent: @Composable () -> Unit = {},
    profileContent: @Composable () -> Unit = {},
    /** The Settings page (its categories; each opens its own screen). */
    settingsContent: @Composable () -> Unit = {},
    liveContent: @Composable () -> Unit = {},
    /** The Lightning wallet (NWC): balance, receive, send, history. */
    walletContent: @Composable () -> Unit = {},
    messagesContent: @Composable () -> Unit = {},
    bookmarksContent: @Composable () -> Unit = {},
    listsContent: @Composable () -> Unit = {},
    /** Top of the side bar: the account (avatar, name) or a log-in prompt. */
    drawerHeader: @Composable () -> Unit = {},
    /** Unread counts shown on bottom-bar tabs. */
    badges: Map<MainTab, Int> = emptyMap(),
    /** The top-left button that opens the side bar (the user's avatar when logged in). */
    menuButtonIcon: @Composable () -> Unit = { Text("≡", color = EInk.Value, fontSize = TextSize.Title) },
    /** Long-pressing the side panel's button: switch accounts (null: nothing). */
    accountSwitcher: (@Composable (onDismiss: () -> Unit) -> Unit)? = null
) {
    // Your menu (Profile, Wallet, Bookmarks… Settings): a sheet from your avatar at the top right.
    var menuOpenSheet by remember { mutableStateOf(false) }
    val openPage: (MainTab) -> Unit = { tab ->
        onTabSelected(tab)
        menuOpenSheet = false
    }
    // Back returns from one of the menu's pages to Home.
    BackHandler(enabled = selectedTab !in BAR_TABS) { onTabSelected(MainTab.HOME) }

    Box(modifier) {
    // Wide (a phone on its side, a tablet, an unfolded foldable): the tabs move to a rail on the
    // left, as X does on tablets, so the screen's height goes to what you're reading.
    val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= WIDE_DP
    var switcher by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxSize().screenBackground()
            .then(if (wide) Modifier.displayCutoutPadding().navigationBarsPadding() else Modifier)
    ) {
    if (wide) SideRail(
        selectedTab = selectedTab,
        badges = badges,
        menuIcon = menuButtonIcon,
        menuOpen = selectedTab in DRAWER_PAGES,
        onMenu = { menuOpenSheet = true },
        onMenuLongPress = if (accountSwitcher != null) ({ switcher = true }) else null,
        onTab = { tab -> if (tab == selectedTab) com.example.btcwidget.ui.social.TabTaps.tap(tab.name) else onTabSelected(tab) },
        onCompose = { onItemClick(com.example.btcwidget.ComposeKey()) }
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .statusBarsPadding()
    ) {
        // On feeds the bars slide away while you scroll down and come back when you scroll up,
        // as on X. Every page starts with them showing.
        var barsVisible by remember { mutableStateOf(true) }
        androidx.compose.runtime.LaunchedEffect(selectedTab) { barsVisible = true }
        val hides = selectedTab in HIDING_PAGES
        val scrollWatch = remember {
            object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                    if (available.y < -8f) barsVisible = false else if (available.y > 8f) barsVisible = true
                    return androidx.compose.ui.geometry.Offset.Zero
                }
            }
        }
        val showBars = barsVisible || !hides
        val imeVisible = androidx.compose.foundation.layout.WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
        val menuOpen = selectedTab in DRAWER_PAGES
        val menuButtonSlot: (@Composable () -> Unit)? = if (wide) null else ({
            MenuIconButton(
                menuIcon = menuButtonIcon,
                menuOpen = menuOpen,
                onMenu = { menuOpenSheet = true },
                onMenuLongPress = if (accountSwitcher != null) ({ switcher = true }) else null
            )
        })
        if (selectedTab != MainTab.SEARCH) androidx.compose.animation.AnimatedVisibility(
            visible = showBars,
            enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
        ) {
            TopBar(
                title = tr(selectedTab.title),
                menuButton = menuButtonSlot
            )
        }
        androidx.compose.runtime.CompositionLocalProvider(com.example.btcwidget.ui.social.LocalBarsVisible provides showBars) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (hides) Modifier.nestedScroll(scrollWatch) else Modifier)
        ) {
            // Wide: pages in a readable column in the middle, as on X.
            com.example.btcwidget.ui.social.ReadableColumn(enabled = wide) {
            when (selectedTab) {
                MainTab.HOME -> homeContent()
                MainTab.SEARCH -> searchContent(menuButtonSlot)
                MainTab.LIVE -> liveContent()
                MainTab.WALLET -> walletContent()
                MainTab.MESSAGES -> messagesContent()
                MainTab.BOOKMARKS -> bookmarksContent()
                MainTab.LISTS -> listsContent()
                MainTab.NOTIFICATIONS -> notificationsContent()
                MainTab.PROFILE -> profileContent()
                MainTab.SETTINGS -> settingsContent()
            }
            }
        }

        }
        if (!wide) androidx.compose.animation.AnimatedVisibility(
            visible = showBars && !imeVisible,
            enter = androidx.compose.animation.expandVertically(expandFrom = Alignment.Top) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically(shrinkTowards = Alignment.Top) + androidx.compose.animation.fadeOut()
        ) {
            BottomBar(
                selectedTab = selectedTab,
                badges = badges,
                onCompose = { onItemClick(com.example.btcwidget.ComposeKey()) },
                // The tab you're on: back to the top, or refresh when already there.
                onTab = { tab -> if (tab == selectedTab) com.example.btcwidget.ui.social.TabTaps.tap(tab.name) else onTabSelected(tab) }
            )
        }
    }
    }
    if (switcher) accountSwitcher?.invoke { switcher = false }
    if (menuOpenSheet) AccountMenuSheet(
        selectedTab = selectedTab,
        header = drawerHeader,
        onOpen = openPage,
        onSwitchAccount = if (accountSwitcher != null) ({ menuOpenSheet = false; switcher = true }) else null,
        onDismiss = { menuOpenSheet = false }
    )
    }
}

private val Accent get() = EInk.Accent

/** From this width (dp) the tabs move to a rail on the left and pages get a readable column. */
private const val WIDE_DP = 600

/** The bottom bar's height above the navigation bar. */
private val BOTTOM_BAR_HEIGHT = 62.dp

/**
 * The tabs on a rail at the left, for wide screens: your avatar on top (opens your menu,
 * long-press to switch accounts), then Home, Search, Notifications, Messages with the same
 * icons and unread bubbles as the bottom bar, and New post.
 */
@Composable
private fun SideRail(
    selectedTab: MainTab,
    badges: Map<MainTab, Int>,
    menuIcon: @Composable () -> Unit,
    menuOpen: Boolean,
    onMenu: () -> Unit,
    onMenuLongPress: (() -> Unit)?,
    onTab: (MainTab) -> Unit,
    onCompose: () -> Unit
) {
    val haptics = com.example.btcwidget.ui.social.rememberHaptics()
    Row(Modifier.fillMaxHeight().testTag("side_rail")) {
        Column(
            Modifier.width(72.dp).fillMaxHeight().statusBarsPadding().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .combinedClickable(
                        onClickLabel = tr("Open menu"),
                        onLongClickLabel = if (onMenuLongPress != null) tr("Switch accounts") else null,
                        onLongClick = onMenuLongPress?.let { lc -> { haptics.heavy(); lc() } },
                        onClick = onMenu
                    )
                    .semantics { contentDescription = tr("Open menu") },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape)
                        .then(if (menuOpen) Modifier.border(2.dp, Accent, CircleShape) else Modifier)
                        .padding(if (menuOpen) 3.dp else 1.dp),
                    contentAlignment = Alignment.Center
                ) { menuIcon() }
            }
            Spacer(Modifier.height(12.dp))
            BAR_TABS.forEach { tab ->
                val count = badges[tab] ?: 0
                val selected = selectedTab == tab
                Box(
                    Modifier.size(56.dp)
                        .clip(CircleShape)
                        .combinedClickable(role = Role.Tab, onClick = { haptics.tick(); onTab(tab) })
                        .semantics { contentDescription = if (count > 0) tr("{0}, {1} unread", tr(tab.label), count) else tr(tab.label); this.selected = selected },
                    contentAlignment = Alignment.Center
                ) {
                    Box {
                        TabIcon(tab, selected)
                        if (count > 0) CountBadge(count, Modifier.align(Alignment.TopEnd).offset(x = 9.dp, y = (-6).dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            NewPostButton(onCompose)
        }
        androidx.compose.material3.VerticalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
    }
}

/**
 * The bottom bar: a floating, rounded island with Home, Search, New post (the round + in the
 * middle), Notifications and Messages. The open tab sits in an aurora capsule; unread counts
 * are small accent bubbles on the icon.
 */
@Composable
private fun BottomBar(
    selectedTab: MainTab,
    badges: Map<MainTab, Int>,
    onCompose: () -> Unit,
    onTab: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxWidth().screenBackground().navigationBarsPadding().padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().height(BOTTOM_BAR_HEIGHT)
                .shadow(16.dp, Radius.Full)
                .clip(Radius.Full)
                .background(EInk.Sheet)
                .border(1.dp, EInk.BodyStroke, Radius.Full)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (left, right) = BAR_TABS.chunked(2).let { it[0] to it[1] }
            left.forEach { BarTab(it, selectedTab, badges, onTab) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { NewPostButton(onCompose) }
            right.forEach { BarTab(it, selectedTab, badges, onTab) }
        }
    }
}

@Composable
private fun RowScope.BarTab(tab: MainTab, selectedTab: MainTab, badges: Map<MainTab, Int>, onTab: (MainTab) -> Unit) {
    val count = badges[tab] ?: 0
    val selected = selectedTab == tab
    BarSlot(
        label = if (count > 0) tr("{0}, {1} unread", tr(tab.label), count) else tr(tab.label),
        selected = selected,
        onClick = { onTab(tab) }
    ) {
        Box {
            TabIcon(tab, selected)
            if (count > 0) CountBadge(count, Modifier.align(Alignment.TopEnd).offset(x = 9.dp, y = (-6).dp))
        }
    }
}

/** A tab's icon; the open one filled, in an aurora capsule. */
@Composable
private fun TabIcon(tab: MainTab, selected: Boolean) {
    Box(
        Modifier.size(width = 52.dp, height = 38.dp).clip(Radius.Full)
            .then(if (selected) Modifier.background(Aurora.brush) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(tab.icon.of(selected), contentDescription = null, tint = if (selected) Aurora.OnAurora else EInk.Value, modifier = Modifier.size(25.dp))
    }
}

/** "New post": the round + in the middle of the bar (and on the rail). */
@Composable
private fun NewPostButton(onCompose: () -> Unit) {
    val haptics = com.example.btcwidget.ui.social.rememberHaptics()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.9f else 1f, label = "compose_press")
    Box(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(44.dp)
            .clip(CircleShape)
            .background(EInk.Accent)
            .clickable(interactionSource = source, indication = null, role = Role.Button) { haptics.tick(); onCompose() }
            .semantics { contentDescription = tr("New post") }
            .testTag("compose_button"),
        contentAlignment = Alignment.Center
    ) {
        Icon(com.example.btcwidget.ui.social.Glyphs.Plus, contentDescription = null, tint = EInk.OnAccent, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun RowScope.BarSlot(label: String, selected: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val haptics = com.example.btcwidget.ui.social.rememberHaptics()
    Box(
        Modifier.weight(1f).fillMaxHeight()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 26.dp),
                role = Role.Tab,
                onLongClickLabel = if (onLongClick != null) tr("Switch accounts") else null,
                onLongClick = onLongClick?.let { lc -> { haptics.heavy(); lc() } },
                onClick = { haptics.tick(); onClick() }
            )
            .semantics { contentDescription = label; this.selected = selected },
        contentAlignment = Alignment.Center
    ) { content() }
}

/** An unread count: a small orange bubble, "99+" past 99. */
@Composable
private fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.height(18.dp).widthIn(min = 18.dp).clip(CircleShape).background(Accent).border(1.5.dp, EInk.Background, CircleShape)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) { Text(if (count > 99) "99+" else "$count", color = Color.Black, fontSize = TextSize.Micro, fontWeight = FontWeight.Bold, lineHeight = 10.sp) }
}

@Composable
private fun MenuIconButton(
    menuIcon: @Composable () -> Unit,
    menuOpen: Boolean,
    onMenu: () -> Unit,
    onMenuLongPress: (() -> Unit)?
) {
    val haptics = com.example.btcwidget.ui.social.rememberHaptics()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .combinedClickable(
                onClickLabel = tr("Open menu"),
                onLongClickLabel = if (onMenuLongPress != null) tr("Switch accounts") else null,
                onLongClick = onMenuLongPress?.let { lc -> { haptics.heavy(); lc() } },
                onClick = onMenu
            )
            .semantics { contentDescription = tr("Open menu") }
    ) {
        // Your picture, in an aurora ring (brighter while one of the menu's pages is open).
        Box(
            Modifier.size(36.dp).clip(CircleShape).auroraRing(),
            contentAlignment = Alignment.Center
        ) { menuIcon() }
    }
}

@Composable
private fun TopBar(title: String, menuButton: (@Composable () -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(start = 16.dp, end = 8.dp)
    ) {
        Text(
            text = title,
            color = EInk.Value,
            fontSize = TextSize.Display,
            lineHeight = LineHeight.Display,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        menuButton?.invoke()
    }
}

/**
 * Your menu, a sheet from your avatar: who you are, three big tiles (Profile, Wallet,
 * Bookmarks), then Lists, Live, Settings and switching accounts.
 */
@Composable
private fun AccountMenuSheet(
    selectedTab: MainTab,
    header: @Composable () -> Unit,
    onOpen: (MainTab) -> Unit,
    onSwitchAccount: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    com.example.btcwidget.ui.social.BlockSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.XL).padding(bottom = Space.XXL)) {
            Box(Modifier.fillMaxWidth().padding(bottom = Space.L)) { header() }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(bottom = Space.S)) {
                MENU_TILES.forEach { tab -> MenuTile(tab, selectedTab == tab, Modifier.weight(1f)) { onOpen(tab) } }
            }
            MENU_ROWS.forEach { tab -> MenuRow(tr(tab.title), tab.icon.of(selectedTab == tab), selectedTab == tab) { onOpen(tab) } }
            if (onSwitchAccount != null) Text(
                tr("Switch account"), color = EInk.Caption, fontSize = TextSize.Body,
                modifier = Modifier.fillMaxWidth().clip(Radius.Small).clickable(role = Role.Button, onClick = onSwitchAccount).padding(horizontal = 4.dp, vertical = 14.dp)
            )
        }
    }
}

@Composable
private fun MenuTile(tab: MainTab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(Radius.Large).background(EInk.Body)
            .then(if (selected) Modifier.border(1.5.dp, EInk.Accent, Radius.Large) else Modifier)
            .clickable(role = Role.Button, onClickLabel = tr("Open {0}", tr(tab.title)), onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(tab.icon.of(selected), contentDescription = null, tint = EInk.Value, modifier = Modifier.size(24.dp))
        Text(tr(tab.title), color = EInk.Value, fontSize = TextSize.Caption, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun MenuRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(Radius.Small)
            .clickable(role = Role.Button, onClickLabel = tr("Open {0}", title), onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 13.dp)
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) EInk.Accent else EInk.Value, modifier = Modifier.size(24.dp))
        Text(title, color = EInk.Value, fontSize = TextSize.Subtitle, fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, modifier = Modifier.padding(start = Space.L))
    }
}

/**
 * Pages. The floating bottom bar holds Home, Search, Notifications and Messages (with New post
 * in the middle); the menu from your avatar at the top right holds Profile, Wallet, Bookmarks,
 * Lists, Live and Settings. Widgets are in Settings › Bitcoin and widgets. [label] names the bar
 * icon for TalkBack, [title] is shown as the page's heading and in the menu.
 */
enum class MainTab(val label: String, val icon: com.example.btcwidget.ui.social.NavIcon, val title: String = label) {
    HOME("Home", com.example.btcwidget.ui.social.NavIcons.Home),
    SEARCH("Search", com.example.btcwidget.ui.social.NavIcons.Search),
    LIVE("Live", com.example.btcwidget.ui.social.NavIcons.Live),
    WALLET("Wallet", com.example.btcwidget.ui.social.NavIcons.Wallet),
    NOTIFICATIONS("Notifications", com.example.btcwidget.ui.social.NavIcons.Bell),
    MESSAGES("Messages", com.example.btcwidget.ui.social.NavIcons.Messages),
    PROFILE("Profile", com.example.btcwidget.ui.social.NavIcons.Profile),
    BOOKMARKS("Bookmarks", com.example.btcwidget.ui.social.NavIcons.Bookmarks),
    LISTS("Lists", com.example.btcwidget.ui.social.NavIcons.Lists),
    SETTINGS("Settings", com.example.btcwidget.ui.social.NavIcons.Settings)
}

// Getters, not stored values: a top-level val that touches MainTab would initialize the
// enum while this file's other top-level vals are still null (that crashed the app once).
private val BAR_TABS get() = listOf(MainTab.HOME, MainTab.SEARCH, MainTab.NOTIFICATIONS, MainTab.MESSAGES)

/** The menu from your avatar: three tiles, then rows. */
private val MENU_TILES get() = listOf(MainTab.PROFILE, MainTab.WALLET, MainTab.BOOKMARKS)
private val MENU_ROWS get() = listOf(MainTab.LISTS, MainTab.LIVE, MainTab.SETTINGS)
private val DRAWER_PAGES get() = MENU_TILES + MENU_ROWS

/** Feeds, where the bars slide away while you scroll down (not forms, settings or the dashboard). */
private val HIDING_PAGES get() = setOf(MainTab.HOME, MainTab.SEARCH, MainTab.LIVE, MainTab.NOTIFICATIONS, MainTab.MESSAGES, MainTab.PROFILE, MainTab.BOOKMARKS, MainTab.LISTS)

