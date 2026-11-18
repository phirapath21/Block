package com.example.btcwidget.ui.social

import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextOverflow
import com.example.btcwidget.nostr.core.Event
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import coil3.compose.AsyncImage
import com.example.btcwidget.ComposeKey
import com.example.btcwidget.EditProfileKey
import com.example.btcwidget.LoginKey
import com.example.btcwidget.nostr.FeedItem
import com.example.btcwidget.nostr.NoteStats
import com.example.btcwidget.nostr.NostrNotification
import com.example.btcwidget.nostr.NotificationGroup
import com.example.btcwidget.nostr.core.HomeFeed
import com.example.btcwidget.nostr.core.Nip19
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.ui.main.EInk
import kotlinx.coroutines.launch

// ---- Shared pieces ----

/** Title bar for pushed screens: back arrow and a title. */
@Composable
fun SocialTopBar(title: String, onBack: () -> Unit) {
    Column(modifier = Modifier.background(EInk.Background).statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(
                text = "←",
                color = EInk.Value,
                fontSize = 22.sp,
                modifier = Modifier
                    .clickable(onClickLabel = tr("Back"), onClick = onBack)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Text(title, color = EInk.Value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
    }
}

private val EmptyNoteStats = NoteStats()

/** A feed of notes with infinite scroll. */
@Composable
fun NoteList(
    items: List<FeedItem>,
    stats: Map<String, NoteStats>,
    actions: NoteActions,
    modifier: Modifier = Modifier,
    // Kept across Back, with the notes: you return to the post you left.
    state: LazyListState = rememberKept { LazyListState() },
    loadingMore: Boolean = false,
    onLoadMore: (() -> Unit)? = null,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(bottom = 84.dp),
    header: (@Composable () -> Unit)? = null
) {
    val nearEnd by remember {
        derivedStateOf {
            val last = state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.layoutInfo.totalItemsCount - 5
        }
    }
    if (onLoadMore != null) LaunchedEffect(nearEnd, items.size) { if (nearEnd && items.isNotEmpty()) onLoadMore() }

    Box(modifier.fillMaxSize()) {
        LazyColumn(state = state, contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
            if (header != null) item(key = "header", contentType = "header") { header() }
            items(items, key = { it.key }, contentType = { it.note.kind }) { item ->
                NoteCard(item.note, stats[item.note.id] ?: EmptyNoteStats, actions, repostedBy = item.repostedBy)
            }
            if (loadingMore) item(key = "more", contentType = "spinner") { Spinner() }
        }
        BackToTopButton(state, Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp))
    }
}

/**
 * "Back to top": a round ↑ that rises in once you're well down a list and start scrolling
 * back up (when the bars come back), and glides you to the top. From far down it first
 * jumps close to the top, so the trip is quick instead of a long scroll through everything.
 */
@Composable
internal fun BackToTopButton(state: LazyListState, modifier: Modifier = Modifier) {
    val farDown by remember { derivedStateOf { state.firstVisibleItemIndex >= BACK_TO_TOP_AFTER } }
    val visible = farDown && LocalBarsVisible.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(160)) +
            androidx.compose.animation.scaleIn(androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 500f), initialScale = 0.6f) +
            androidx.compose.animation.slideInVertically(androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 500f)) { it / 2 },
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)) +
            androidx.compose.animation.scaleOut(androidx.compose.animation.core.tween(120), targetScale = 0.6f),
        modifier = modifier
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .background(SheetColor)
                .border(0.75.dp, EInk.BodyStroke, CircleShape)
                .clickable(onClickLabel = tr("Back to top")) {
                    haptics.tick()
                    scope.launch {
                        if (state.firstVisibleItemIndex > 12) state.scrollToItem(6)
                        state.animateScrollToItem(0)
                    }
                }
                .testTag("back_to_top")
        ) {
            androidx.compose.material3.Icon(Glyphs.ArrowUp, contentDescription = tr("Back to top"), tint = SettingsAccent, modifier = Modifier.size(22.dp))
        }
    }
}

/** How many notes down before "Back to top" can show. */
internal const val BACK_TO_TOP_AFTER = 8

@Composable
fun Spinner(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = EInk.Value, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun EmptyNote(text: String) {
    Text(
        text = text,
        color = EInk.Caption,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(32.dp)
    )
}

/** Primary pill button, X-style with Block's Bitcoin Orange accent. */
@Composable
fun PillButton(text: String, filled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .then(if (filled) Modifier.background(EInk.Accent) else Modifier.border(1.dp, EInk.BodyStroke, RoundedCornerShape(50)))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (filled) androidx.compose.ui.graphics.Color.Black else EInk.Value, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

// ---- Home ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: SocialViewModel, navigate: (NavKey) -> Unit) {
    val account by vm.account.collectAsState()
    val home by vm.home.collectAsState()
    val stats by vm.stats.collectAsState()
    val newPosts by vm.newPosts.collectAsState()
    val feed by vm.homeFeed.collectAsState()
    val listState = vm.listState("home:" + feed.id)
    val scope = rememberCoroutineScope()
    LaunchedEffect(account) { vm.start() }
    // Tap Home again: back to the top; again at the top: new posts (or a refresh).
    val pullHaptics = rememberHaptics()
    ScrollToTopOnTabTap("HOME", listState) { if (vm.newPosts.value.isNotEmpty()) vm.showNewPosts() else vm.refreshHome() }

    SocialScaffold(vm, navigate) { actions ->
        Box(Modifier.fillMaxSize()) {
            if (account == null) {
                WelcomeScreen(vm, actions, onLogin = { navigate(LoginKey) })
            } else {
                val shown = home.items
                var feedMenu by remember { mutableStateOf(false) }
                val feeds by vm.homeFeeds.collectAsState()
                val barsVisible = LocalBarsVisible.current
                Column(Modifier.fillMaxSize()) {
                    FeedTabBar(
                        feeds = feeds, current = feed,
                        // The open tab again: back to the top (and again: refresh), as on X.
                        onSelect = { f -> if (f == feed) TabTaps.tap("HOME") else vm.selectFeed(f) },
                        onManage = { feedMenu = true }
                    )
                    PullToRefreshBox(isRefreshing = home.refreshing, onRefresh = { pullHaptics.tick(); vm.refreshHome() }, modifier = Modifier.fillMaxSize()) {
                        when {
                            home.loading && home.items.isEmpty() -> Spinner(Modifier.padding(top = 40.dp))
                            shown.isEmpty() && !home.loading -> LazyColumn(Modifier.fillMaxSize()) {
                                item { EmptyNote(home.error ?: tr("Nothing here yet. Follow people to fill your timeline, or write your first note.")) }
                            }
                            else -> NoteList(
                                items = shown, stats = stats, actions = actions, state = listState,
                                loadingMore = home.loading, onLoadMore = vm::loadMoreHome
                            )
                        }
                    }
                }
                // Just under the feed tabs, or near the top once they've folded away.
                val newPostsTop by androidx.compose.animation.core.animateDpAsState(
                    if (barsVisible) FEED_TAB_HEIGHT + 12.dp else 12.dp, androidx.compose.animation.core.spring(dampingRatio = 0.9f, stiffness = 500f), label = "new_posts_top"
                )
                if (newPosts.isNotEmpty()) {
                    // "↑ N new posts": new notes wait here instead of shifting what you're reading.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = newPostsTop)
                            .clip(RoundedCornerShape(50))
                            .background(EInk.Accent)
                            .clickable(onClickLabel = tr("Show new posts")) {
                                vm.showNewPosts()
                                scope.launch { listState.animateScrollToItem(0) }
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        newPosts.distinctBy { it.note.pubkey }.take(3).forEach { item ->
                            Box(Modifier.padding(end = 2.dp)) { Avatar(actions.profileOf(item.note.pubkey), item.note.pubkey, size = 20) }
                        }
                        Text(
                            tr("  ↑ {0} new post{1}", newPosts.size, plural(newPosts.size)),
                            color = androidx.compose.ui.graphics.Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (feedMenu) FeedMenu(vm, onClose = { feedMenu = false })
                FirstTimeTip(
                    "home-tabs-v2", tr("Tap Home again to jump back to the top. Long-press your picture at the top left to switch accounts."),
                    Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 84.dp, bottom = 16.dp)
                )
                // Compose: bottom right, shrinking away with the bars while you scroll down.
                val fab by androidx.compose.animation.core.animateFloatAsState(if (barsVisible) 1f else 0f, label = "fab")
                if (fab > 0.01f) FloatingActionButton(
                    onClick = { navigate(ComposeKey()) },
                    containerColor = EInk.Accent,
                    contentColor = androidx.compose.ui.graphics.Color.Black,
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).size(56.dp)
                        .graphicsLayer { alpha = fab; scaleX = 0.6f + 0.4f * fab; scaleY = 0.6f + 0.4f * fab }
                ) { Text("+", fontSize = 26.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** Underlined tab row, as at the top of X's timeline. */
@Composable
fun FeedTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { i, label ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClickLabel = label) { onSelect(i) }
                    .padding(top = 12.dp)
            ) {
                Text(
                    label,
                    color = if (i == selected) EInk.Value else EInk.Caption,
                    fontSize = 15.sp,
                    fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal
                )
                Box(
                    Modifier
                        .padding(top = 10.dp)
                        .width(56.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i == selected) EInk.Accent else androidx.compose.ui.graphics.Color.Transparent)
                )
            }
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
}

/** Logged out: what this is, how to join, and a public #bitcoin feed to browse. */
@Composable
private fun WelcomeScreen(vm: SocialViewModel, actions: NoteActions, onLogin: () -> Unit) {
    val stats by vm.stats.collectAsState()
    val feed by produceKept<List<FeedItem>?>(null) { value = vm.hashtagFeed("bitcoin") }
    NoteList(items = feed.orEmpty(), stats = stats, actions = actions, header = {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(tr("Bitcoin people, on Nostr"), color = EInk.Value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                tr("An open social network with no company in charge. Your account is a key you own; post, follow, reply and zap sats."),
                color = EInk.Label, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
            )
            PillButton(tr("Log in or create account"), onClick = onLogin)
            Text("#bitcoin right now", color = EInk.Caption, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp))
        }
        HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
        if (feed == null) Spinner()
    })
}

// ---- Notifications ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(vm: SocialViewModel, navigate: (NavKey) -> Unit) {
    val account by vm.account.collectAsState()
    val state by vm.notifications.collectAsState()
    val pullHaptics = rememberHaptics()
    val profiles by vm.profiles.collectAsState()
    LaunchedEffect(account) { if (account != null && state.items.isEmpty()) vm.refreshNotifications() }

    SocialScaffold(vm, navigate) { actions ->
        Column(Modifier.fillMaxSize()) {
            if (account == null) {
                EmptyNote(tr("Log in to see replies, likes, reposts and zaps."))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PillButton(tr("Log in"), onClick = { navigate(LoginKey) }) }
                return@Column
            }
            // All, or only Mentions (replies and mentions of you), as on X.
            var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
            FeedTabs(listOf(tr("All"), tr("Mentions")), tab) { tab = it }
            HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
            val listState = vm.listState(if (tab == 0) "notifications" else "notifications:mentions")
            ScrollToTopOnTabTap("NOTIFICATIONS", listState) { vm.refreshNotifications() }
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { pullHaptics.tick(); vm.refreshNotifications() }, modifier = Modifier.fillMaxSize()) {
                val shown = remember(state.items, tab) {
                    if (tab == 0) state.items
                    else state.items.filter { it.type == NostrNotification.Type.REPLY || it.type == NostrNotification.Type.MENTION }
                }
                when {
                    state.loading -> Spinner(Modifier.padding(top = 40.dp))
                    shown.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                        item { EmptyNote(state.error ?: if (tab == 0) tr("No notifications yet.") else tr("When someone replies to or mentions you, it shows here.")) }
                    }
                    else -> {
                        val groups = remember(shown) { NotificationGroup.of(shown) }
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            items(groups, key = { it.key }) { g -> NotificationRow(g, profiles, actions) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One row: a reply or mention as the note itself; likes, reposts and zaps of one note folded
 * together, X-style: the icon, a row of faces, "Alice, Bob and 3 others liked your note",
 * and a line of the note they're about.
 */
@Composable
private fun NotificationRow(g: NotificationGroup, profiles: Map<String, Profile>, actions: NoteActions) {
    val n = g.latest
    if (n.type == NostrNotification.Type.REPLY || n.type == NostrNotification.Type.MENTION) {
        NoteCard(n.event, NoteStats(), actions)
        return
    }
    val orange = androidx.compose.ui.graphics.Color(0xFFF7931A)
    val (icon, tint) = when (n.type) {
        NostrNotification.Type.REPOST -> "⟲" to EInk.Value
        NostrNotification.Type.LIKE -> "♥" to orange
        else -> "⚡" to orange
    }
    val actors = g.actors
    val names = actors.take(2).map { profiles[it]?.bestName ?: Profile.shortNpub(it) }
    val verb = when (n.type) {
        NostrNotification.Type.REPOST -> "reposted your note"
        NostrNotification.Type.LIKE -> if (g.items.all { it.event.content == "+" || it.event.content.isBlank() }) "liked your note"
            else "reacted " + g.items.map { it.event.content }.filter { it != "+" && it.isNotBlank() && it.length <= 8 }.distinct().take(5).joinToString("")
        else -> "zapped ${formatSats(g.totalMsat / 1000)} sats"
    }
    val target by produceKept<Event?>(null, g.noteId) { value = g.noteId?.let { runCatching { actions.resolveNote(it) }.getOrNull() } }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = g.noteId != null) {
                val id = g.noteId ?: return@clickable
                actions.openNote(target ?: n.event.copy(id = id))
            }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(icon, color = tint, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp).padding(start = 6.dp))
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                actors.take(7).forEach { pk -> Avatar(profiles[pk], pk, size = 32, onClick = { actions.openProfile(pk) }) }
                if (actors.size > 7) Text("+${actors.size - 7}", color = EInk.Caption, fontSize = 13.sp, modifier = Modifier.padding(start = 2.dp))
            }
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(NotificationGroup.names(names, actors.size)) }
                    append(" $verb")
                    withStyle(SpanStyle(color = EInk.Caption)) { append(" · " + relativeTime(g.createdAt)) }
                },
                color = EInk.Value, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp)
            )
            if (n.type == NostrNotification.Type.ZAP) {
                // The newest zap comment, if any.
                val comment = g.items.firstNotNullOfOrNull { z ->
                    z.event.firstTag("description")?.let { com.example.btcwidget.nostr.core.Zaps.parseReceipt(z.event)?.comment?.takeIf { c -> c.isNotBlank() } }
                }
                if (comment != null) Text("“$comment”", color = EInk.Value, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            }
            target?.let {
                Text(
                    it.content.replace('\n', ' ').trim(), color = EInk.Caption, fontSize = 14.sp, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
}

private fun formatSats(sats: Long): String = "%,d".format(sats)

// ---- Home feeds: tabs and the sheet to manage them ----

/** The icon for a kind of feed, the same in the tabs' sheet and wherever feeds are listed. */
internal fun feedIcon(f: HomeFeed): androidx.compose.ui.graphics.vector.ImageVector = when (f) {
    is HomeFeed.Following -> if (f.replies) Glyphs.Chat else Glyphs.Following
    is HomeFeed.Global -> if (f.relay == null) Glyphs.Globe else Glyphs.Relay
    is HomeFeed.Hashtag -> Glyphs.Hashtag
    is HomeFeed.Dvm -> Glyphs.Sparkle
    is HomeFeed.Media -> if (f.videos) Glyphs.PlayLine else Glyphs.PhotoLine
    is HomeFeed.People -> Glyphs.ListLine
    else -> Glyphs.ListLine
}

/** A feed's name on its tab: short, since tabs sit side by side. */
internal fun feedTabTitle(f: HomeFeed): String = when (f) {
    is HomeFeed.Following -> tr(if (f.replies) "With replies" else "Latest")
    is HomeFeed.Global -> f.relay?.removePrefix("wss://")?.removePrefix("ws://")?.removeSuffix("/") ?: tr("Global")
    is HomeFeed.Hashtag -> "#" + f.tag
    is HomeFeed.Dvm -> f.name
    is HomeFeed.People -> f.name
    else -> feedTitle(f)
}

/**
 * Home's feeds as tabs under the top bar, as on X: the open feed in bold with an orange bar
 * under its name that slides to the tab you pick. Tabs share the width when they fit, and
 * scroll (keeping the open one in view, with soft fades at the cut edges) when they don't.
 * Manage feeds sits at the end. The bar folds away with the top bar while you scroll down.
 */
@Composable
internal fun FeedTabBar(
    feeds: List<HomeFeed>,
    current: HomeFeed,
    onSelect: (HomeFeed) -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = LocalBarsVisible.current,
        enter = androidx.compose.animation.expandVertically(androidx.compose.animation.core.spring(dampingRatio = 0.9f, stiffness = 500f)) +
            androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(160)),
        exit = androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 600f)) +
            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)),
        modifier = modifier
    ) {
        Column(Modifier.fillMaxWidth().background(EInk.Background)) {
            Row(Modifier.fillMaxWidth().height(FEED_TAB_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
                FeedTabStrip(feeds, current, onSelect, Modifier.weight(1f).fillMaxHeight().testTag("feed_tabs"))
                Box(Modifier.width(0.5.dp).height(20.dp).background(EInk.BodyStroke))
                CircleIconButton(Glyphs.Sliders, tr("Manage feeds"), Modifier.padding(horizontal = 6.dp), onClick = onManage)
            }
            HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
        }
    }
}

/** Height of the feed tabs, without their bottom line. */
internal val FEED_TAB_HEIGHT = 50.dp

@Composable
private fun FeedTabStrip(feeds: List<HomeFeed>, current: HomeFeed, onSelect: (HomeFeed) -> Unit, modifier: Modifier) {
    val labels = feeds.map { feedTabTitle(it) }
    val selected = feeds.indexOf(current).coerceAtLeast(0)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val haptics = rememberHaptics()
    val boldStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold)
    val tabPad = 16.dp
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        // Tabs share the width when every name fits at its bold width (two or three feeds, as
        // on X); otherwise they keep their own width and scroll.
        val widths = remember(labels) {
            labels.map { with(density) { measurer.measure(it, boldStyle, maxLines = 1).size.width.toDp().coerceAtMost(180.dp) } }
        }
        val fits = widths.fold(0.dp) { sum, w -> sum + w + tabPad * 2 } <= maxWidth
        val scroll = rememberScrollState()
        // Where each name sits in the row, for the sliding bar.
        var rowCoords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
        val textCoords = remember { mutableMapOf<Int, androidx.compose.ui.layout.LayoutCoordinates>() }
        var bounds by remember { mutableStateOf<Map<Int, Pair<Float, Float>>>(emptyMap()) }
        fun recompute() {
            val row = rowCoords ?: return
            if (!row.isAttached) return
            bounds = textCoords.filterValues { it.isAttached }.mapValues { (_, c) -> row.localBoundingBoxOf(c).let { it.left to it.right } }
        }
        val barLeft = remember { androidx.compose.animation.core.Animatable(0f) }
        val barWidth = remember { androidx.compose.animation.core.Animatable(0f) }
        var placed by remember { mutableStateOf(false) }
        val target = bounds[selected]
        LaunchedEffect(selected, target) {
            val (l, r) = target ?: return@LaunchedEffect
            val minW = with(density) { 28.dp.toPx() }
            val w = (r - l).coerceAtLeast(minW)
            val x = (l + r) / 2 - w / 2
            if (!placed) { barLeft.snapTo(x); barWidth.snapTo(w); placed = true }
            else {
                val spec = androidx.compose.animation.core.spring<Float>(dampingRatio = 0.82f, stiffness = 520f)
                kotlinx.coroutines.coroutineScope {
                    launch { barLeft.animateTo(x, spec) }
                    launch { barWidth.animateTo(w, spec) }
                }
            }
        }
        // The open tab stays in view, centred when it can be.
        LaunchedEffect(selected, target, fits) {
            if (fits) return@LaunchedEffect
            val (l, r) = target ?: return@LaunchedEffect
            val viewport = with(density) { maxWidth.toPx() }
            scroll.animateScrollTo(((l + r) / 2 - viewport / 2).toInt().coerceIn(0, scroll.maxValue))
        }
        val accent = SettingsAccent
        val barHeight = with(density) { 4.dp.toPx() }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxHeight()
                .then(if (fits) Modifier.fillMaxWidth() else Modifier.horizontalScroll(scroll))
                .onGloballyPositioned { rowCoords = it; recompute() }
                .drawBehind {
                    if (placed) drawRoundRect(
                        color = accent,
                        topLeft = androidx.compose.ui.geometry.Offset(barLeft.value, size.height - barHeight),
                        size = androidx.compose.ui.geometry.Size(barWidth.value, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barHeight / 2, barHeight / 2)
                    )
                }
        ) {
            labels.forEachIndexed { i, label ->
                val isSelected = i == selected
                val color by androidx.compose.animation.animateColorAsState(
                    if (isSelected) EInk.Value else EInk.Caption, androidx.compose.animation.core.tween(180), label = "tab_color"
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxHeight()
                        .then(if (fits) Modifier.weight(1f) else Modifier)
                        .tabSemantics(isSelected)
                        .clickable(onClickLabel = label) {
                            if (!isSelected) haptics.tick()
                            onSelect(feeds[i])
                        }
                        .padding(horizontal = tabPad)
                ) {
                    Text(
                        label, color = color, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.widthIn(max = 180.dp).onGloballyPositioned { textCoords[i] = it; recompute() }
                    )
                }
            }
        }
        // Soft fades where the tabs are cut off, so it reads as "there's more this way".
        if (!fits) {
            val fade = 28.dp
            if (scroll.canScrollBackward) Box(
                Modifier.align(Alignment.CenterStart).width(fade).fillMaxHeight()
                    .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(EInk.Background, EInk.Background.copy(alpha = 0f))))
            )
            if (scroll.canScrollForward) Box(
                Modifier.align(Alignment.CenterEnd).width(fade).fillMaxHeight()
                    .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(EInk.Background.copy(alpha = 0f), EInk.Background)))
            )
        }
    }
}

/** Marks a tab for accessibility: a tab, selected or not. */
private fun Modifier.tabSemantics(isSelected: Boolean): Modifier =
    this.then(androidx.compose.ui.Modifier.semantics {
        role = androidx.compose.ui.semantics.Role.Tab
        selected = isSelected
    })

/** A rounded chip, orange when selected (Search's filters); with [icon], a soft chip. */
@Composable
internal fun FeedTab(
    label: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    val haptics = rememberHaptics()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(50))
            .background(
                when {
                    selected -> SettingsAccent
                    icon != null -> EInk.Track.copy(alpha = 0.55f)
                    else -> androidx.compose.ui.graphics.Color.Transparent
                }
            )
            .clickable(onClickLabel = label) { haptics.tick(); onClick() }
            .padding(horizontal = if (selected) 14.dp else 12.dp)
    ) {
        if (icon != null) {
            androidx.compose.material3.Icon(
                icon,
                contentDescription = null,
                tint = if (selected) androidx.compose.ui.graphics.Color.Black else SettingsAccent,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            label,
            color = if (selected) androidx.compose.ui.graphics.Color.Black else EInk.Value,
            fontSize = 13.5.sp,
            maxLines = 1,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.widthIn(max = 160.dp),
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** The feed's icon in a soft round tile. */
@Composable
private fun FeedIconTile(f: HomeFeed, selected: Boolean = false) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(if (selected) SettingsAccent.copy(alpha = 0.16f) else EInk.Track.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Icon(feedIcon(f), contentDescription = null, tint = if (selected) SettingsAccent else EInk.Value, modifier = Modifier.size(20.dp))
    }
}

/** A small round button in the sheet's edit mode (move up, move down, remove). */
@Composable
private fun SheetIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, tint: androidx.compose.ui.graphics.Color = EInk.Value, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = label, tint = if (enabled) tint else EInk.BodyStroke, modifier = Modifier.size(19.dp))
    }
}

/** Pick a feed; Edit to reorder, remove, or add topic, relay and DVM feeds. */
@Composable
private fun FeedMenu(vm: SocialViewModel, onClose: () -> Unit) {
    val feeds by vm.homeFeeds.collectAsState()
    val current by vm.homeFeed.collectAsState()
    var editing by remember { mutableStateOf(false) }
    BlockSheet(onDismiss = onClose) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Feeds"), color = EInk.Value, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (editing) Text(
                    tr("Reset"), color = EInk.Caption, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable { vm.resetFeeds() }.padding(horizontal = 12.dp, vertical = 8.dp)
                )
                Box(
                    Modifier.clip(RoundedCornerShape(50))
                        .then(if (editing) Modifier.background(SettingsAccent) else Modifier.border(1.dp, EInk.BodyStroke, RoundedCornerShape(50)))
                        .clickable { editing = !editing }
                        .padding(horizontal = 16.dp, vertical = 7.dp)
                ) {
                    Text(
                        if (editing) tr("Done") else tr("Edit"), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        color = if (editing) androidx.compose.ui.graphics.Color.Black else EInk.Value
                    )
                }
            }
            feeds.forEachIndexed { i, f ->
                val selected = f == current
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (selected && !editing) EInk.Body else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable(enabled = !editing) { vm.selectFeed(f); onClose() }.padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FeedIconTile(f, selected = selected)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(feedTitle(f), color = EInk.Value, fontSize = 16.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(feedSubtitle(f), color = EInk.Caption, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (editing) {
                        SheetIconButton(Glyphs.ArrowUp, tr("Move up"), enabled = i > 0) { vm.moveFeed(f, -1) }
                        SheetIconButton(Glyphs.ArrowDown, tr("Move down"), enabled = i < feeds.lastIndex) { vm.moveFeed(f, 1) }
                        SheetIconButton(Glyphs.Trash, tr("Remove"), enabled = feeds.size > 1, tint = EInk.Alert) { vm.removeFeed(f) }
                    } else if (selected) {
                        androidx.compose.material3.Icon(Glyphs.Check, contentDescription = tr("Selected"), tint = SettingsAccent, modifier = Modifier.size(22.dp))
                    }
                }
            }
            if (editing) AddFeed(vm, feeds)
        }
    }
}

/** A choice of what kind of feed to add: icon and name, orange when chosen. */
@Composable
private fun KindChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50))
            .then(if (selected) Modifier.background(SettingsAccent) else Modifier.border(1.dp, EInk.BodyStroke, RoundedCornerShape(50)))
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val ink = if (selected) androidx.compose.ui.graphics.Color.Black else EInk.Value
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(16.dp))
        Text(label, color = ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
    }
}

/** A feed you can add with one tap: its icon, name, and a + button. */
@Composable
private fun AddableFeedRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String?, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = tr("Add"), onClick = onAdd).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = EInk.Label, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, color = EInk.Value, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = EInk.Caption, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(30.dp).clip(CircleShape).background(EInk.Track), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(Glyphs.Plus, contentDescription = tr("Add"), tint = EInk.Value, modifier = Modifier.size(16.dp))
        }
    }
}

/** Adds a topic (hashtag), relay (global on one relay) or DVM feed, or brings back a built-in one. */
@Composable
private fun AddFeed(vm: SocialViewModel, existing: List<HomeFeed>) {
    var kind by remember { mutableStateOf(0) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(18.dp)).background(EInk.Body).padding(14.dp)) {
        Text(tr("Add a feed"), color = EInk.Value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple(Glyphs.Hashtag, tr("Topic"), 0), Triple(Glyphs.Relay, tr("Relay"), 1), Triple(Glyphs.Sparkle, "DVM", 2)).forEach { (icon, label, i) ->
                KindChip(icon, label, selected = kind == i) { kind = i; input = ""; error = null }
            }
        }
        if (kind == 2) {
            val services by produceKept<List<com.example.btcwidget.nostr.core.Dvm.Service>?>(null) { value = vm.feedServices() }
            when (val l = services) {
                null -> Spinner()
                else -> if (l.isEmpty()) Text(tr("No feed services found on your relays."), color = EInk.Caption, fontSize = 13.sp)
                else Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    l.forEach { svc ->
                        AddableFeedRow(Glyphs.Sparkle, svc.name, svc.about) { vm.addFeed(HomeFeed.Dvm(svc.pubkey, svc.id, svc.name)) }
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.OutlinedTextField(
                    value = input, onValueChange = { input = it; error = null },
                    placeholder = { Text(if (kind == 0) "siamstr" else "relay.example.com", color = EInk.Caption) },
                    leadingIcon = { androidx.compose.material3.Icon(if (kind == 0) Glyphs.Hashtag else Glyphs.Relay, contentDescription = null, tint = EInk.Caption, modifier = Modifier.size(18.dp)) },
                    singleLine = true, isError = error != null,
                    shape = RoundedCornerShape(14.dp),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedBorderColor = SettingsAccent, unfocusedBorderColor = EInk.BodyStroke, focusedTextColor = EInk.Value, unfocusedTextColor = EInk.Value, cursorColor = SettingsAccent),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                val ready = input.isNotBlank()
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(if (ready) SettingsAccent else EInk.Track)
                        .clickable(enabled = ready) {
                            val feed = if (kind == 0) HomeFeed.cleanTag(input)?.let { HomeFeed.Hashtag(it) }
                            else com.example.btcwidget.nostr.core.normalizeRelayUrl(input.trim().let { if (it.startsWith("ws")) it else "wss://$it" })?.let { HomeFeed.Global(it) }
                            if (feed == null) error = if (kind == 0) tr("One word, letters and numbers only") else tr("Not a relay address")
                            else { vm.addFeed(feed); input = "" }
                        }
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Text(tr("Add"), color = if (ready) androidx.compose.ui.graphics.Color.Black else EInk.Caption, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
            error?.let { Text(it, color = EInk.Alert, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
        }
        // Built-in feeds that were removed can come back.
        val suggested = (HomeFeed.DEFAULTS + HomeFeed.SUGGESTED).filter { it !in existing }
        if (suggested.isNotEmpty()) {
            Text(tr("Suggested"), color = EInk.Caption, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
            suggested.forEach { f -> AddableFeedRow(feedIcon(f), feedTitle(f), feedSubtitle(f)) { vm.addFeed(f) } }
        }
    }
}

