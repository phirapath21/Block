package com.example.btcwidget.ui.social

import com.example.btcwidget.ui.main.TextSize
import com.example.btcwidget.ui.main.Radius
import androidx.compose.foundation.background
import com.example.btcwidget.ui.main.screenBackground
import androidx.compose.ui.text.style.TextOverflow
import com.example.btcwidget.nostr.core.Event
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
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
import com.example.btcwidget.LoginKey
import com.example.btcwidget.nostr.FeedItem
import com.example.btcwidget.nostr.NoteStats
import com.example.btcwidget.nostr.NostrNotification
import com.example.btcwidget.nostr.NotificationGroup
import com.example.btcwidget.nostr.core.HomeFeed
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.ui.main.EInk
import kotlinx.coroutines.launch

// ---- Shared pieces ----

/** Title bar for pushed screens: back arrow and a title. */
@Composable
fun SocialTopBar(title: String, onBack: () -> Unit) {
    Column(modifier = Modifier.screenBackground().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            androidx.compose.material3.Icon(
                Glyphs.Back, contentDescription = tr("Back"), tint = EInk.Value,
                modifier = Modifier
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Back"), onClick = onBack)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .size(22.dp)
            )
            Text(title, color = EInk.Value, fontSize = TextSize.Subtitle, fontWeight = FontWeight.Bold)
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
            // One placeholder note at the end while more load: the list doesn't jump when they arrive.
            if (loadingMore) item(key = "more", contentType = "skeleton") { FeedSkeleton(rows = 1) }
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
                .size(48.dp)
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .background(SheetColor)
                .border(0.75.dp, EInk.BodyStroke, CircleShape)
                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Back to top")) {
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

/** Following fewer than this, an empty Following timeline offers follow packs. */
internal const val STARTER_FOLLOWS = 5

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
        fontSize = TextSize.Body,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(32.dp)
    )
}

/** Primary pill button, X-style with Block's Bitcoin Orange accent. */
@Composable
fun PillButton(text: String, filled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) =
    // The design system's button: filled is the screen's main action (text colour), else outlined.
    com.example.btcwidget.ui.main.BlockButton(
        text, modifier,
        kind = if (filled) com.example.btcwidget.ui.main.ButtonKind.Primary else com.example.btcwidget.ui.main.ButtonKind.Outline,
        onClick = onClick
    )

// ---- Home ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: SocialViewModel, navigate: (NavKey) -> Unit) {
    val account by vm.account.collectAsState()
    val home by vm.home.collectAsState()
    val stats by vm.stats.collectAsState()
    val newPosts by vm.newPosts.collectAsState()
    val feed by vm.homeFeed.collectAsState()
    val following by vm.following.collectAsState()
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
                    // An account made here whose key isn't saved yet.
                    KeyBackupBanner(vm)
                    // Right after signing up: hello, once.
                    FirstHomeWelcome(vm)
                    PullToRefreshBox(isRefreshing = home.refreshing, onRefresh = { pullHaptics.tick(); vm.refreshHome() }, modifier = Modifier.fillMaxSize()) {
                        when {
                            // The shape of the feed while it loads, not a spinner.
                            home.loading && home.items.isEmpty() -> LazyColumn(Modifier.fillMaxSize(), userScrollEnabled = false) { item { FeedSkeleton() } }
                            // A new account: follow packs to fill the timeline, instead of an empty page.
                            shown.isEmpty() && !home.loading && home.error == null &&
                                feed is com.example.btcwidget.nostr.core.HomeFeed.Following && following.size < STARTER_FOLLOWS ->
                                StarterFollows(vm, navigate, Modifier.fillMaxSize())
                            shown.isEmpty() && !home.loading -> LazyColumn(Modifier.fillMaxSize()) {
                                item {
                                    val err = home.error
                                    if (err != null) EmptyNote(err)
                                    else com.example.btcwidget.ui.main.EmptyState(NavIcons.Home.outline, tr("Your timeline is quiet"), tr("Follow people to fill it, or write your first post."))
                                }
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
                // "↑ N new posts": new notes wait here instead of shifting what you're reading.
                // It drops in with a little spring and a tick, and keeps its last count while leaving.
                val pillHaptics = rememberHaptics()
                val lastNew = remember { arrayOf(newPosts) }
                if (newPosts.isNotEmpty()) lastNew[0] = newPosts
                LaunchedEffect(newPosts.isNotEmpty()) { if (newPosts.isNotEmpty()) pillHaptics.tick() }
                androidx.compose.animation.AnimatedVisibility(
                    visible = newPosts.isNotEmpty(),
                    enter = androidx.compose.animation.slideInVertically(androidx.compose.animation.core.spring(dampingRatio = 0.65f, stiffness = 500f)) { -it * 2 } +
                        androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.slideOutVertically { -it * 2 } + androidx.compose.animation.fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = newPostsTop)
                ) {
                    val pending = lastNew[0]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .shadow(8.dp, Radius.Full)
                            .clip(Radius.Full)
                            .background(EInk.Accent)
                            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Show new posts")) {
                                pillHaptics.tick()
                                vm.showNewPosts()
                                scope.launch { listState.animateScrollToItem(0) }
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        pending.distinctBy { it.note.pubkey }.take(3).forEachIndexed { i, item ->
                            // Overlapping faces, ringed in the pill's colour.
                            Box(
                                // Overlap by pulling each face left (padding can't be negative).
                                Modifier.offset(x = (-6 * i).dp).clip(CircleShape)
                                    .border(1.5.dp, EInk.Accent, CircleShape)
                            ) { Avatar(actions.profileOf(item.note.pubkey), item.note.pubkey, size = 22) }
                        }
                        Text(
                            tr("  {0} new post{1}", pending.size, plural(pending.size)),
                            color = EInk.OnAccent, fontSize = TextSize.Caption, fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (feedMenu) FeedMenu(vm, onClose = { feedMenu = false })
            }
        }
    }
}

/** A row of tabs: the same capsules as everywhere else ([ChipTabs]). */
@Composable
fun FeedTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) = ChipTabs(labels, selected, onSelect)

/** Logged out: what this is, how to join, and a public #bitcoin feed to browse. */
@Composable
private fun WelcomeScreen(vm: SocialViewModel, actions: NoteActions, onLogin: () -> Unit) {
    val stats by vm.stats.collectAsState()
    // Newcomers saying hello: a friendly first look, whoever you are.
    val feed by produceKept<List<FeedItem>?>(null) { value = vm.hashtagFeed("introductions") }
    NoteList(items = feed.orEmpty(), stats = stats, actions = actions, header = {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(tr("Your people.") + " " + tr("Your conversations."), color = EInk.Value, fontSize = TextSize.Title, fontWeight = FontWeight.Bold)
            Text(
                tr("Share what's on your mind and follow people you like. No ads, and nobody can take your account away."),
                color = EInk.Label, fontSize = TextSize.Body, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
            )
            PillButton(tr("Log in or create account"), onClick = onLogin)
            Text(tr("New here and saying hi"), color = EInk.Caption, fontSize = TextSize.Caption, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 22.dp))
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
                EmptyNote(tr("Log in to see replies, likes, reposts and tips."))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PillButton(tr("Log in"), onClick = { navigate(LoginKey) }) }
                return@Column
            }
            // All, Mentions (replies and mentions of you), or Tips.
            var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
            ChipTabs(listOf(tr("All"), tr("Mentions"), tr("Tips")), tab) { tab = it }
            // What was new when the page opened stays marked while you read.
            val seenAt = remember(account?.pubkey) { vm.notificationsSeenAt }
            val listState = vm.listState(listOf("notifications", "notifications:mentions", "notifications:tips")[tab])
            ScrollToTopOnTabTap("NOTIFICATIONS", listState) { vm.refreshNotifications() }
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { pullHaptics.tick(); vm.refreshNotifications() }, modifier = Modifier.fillMaxSize()) {
                val shown = remember(state.items, tab) {
                    when (tab) {
                        0 -> state.items
                        1 -> state.items.filter { it.type == NostrNotification.Type.REPLY || it.type == NostrNotification.Type.MENTION }
                        else -> state.items.filter { it.type == NostrNotification.Type.ZAP }
                    }
                }
                when {
                    state.loading -> LazyColumn(Modifier.fillMaxSize(), userScrollEnabled = false) { item { FeedSkeleton(rows = 8) } }
                    shown.isEmpty() -> LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            val err = state.error
                            if (err != null) EmptyNote(err) else when (tab) {
                                0 -> com.example.btcwidget.ui.main.EmptyState(NavIcons.Bell.outline, tr("Nothing new yet"), tr("When people like, reply to or tip your posts, you'll see it here."))
                                1 -> com.example.btcwidget.ui.main.EmptyState(Glyphs.Comment, tr("No mentions yet"), tr("When someone replies to or mentions you, it shows here."))
                                else -> com.example.btcwidget.ui.main.EmptyState(Glyphs.Zap, tr("No tips yet"), tr("When someone tips you, it shows here."))
                            }
                        }
                    }
                    else -> {
                        val groups = remember(shown) { NotificationGroup.of(shown) }
                        // Today, then Earlier.
                        val startOfToday = remember(groups) {
                            java.util.Calendar.getInstance().apply {
                                set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
                                set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
                            }.timeInMillis / 1000
                        }
                        val (today, earlier) = remember(groups, startOfToday) { groups.partition { it.createdAt >= startOfToday } }
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                            if (today.isNotEmpty()) item(key = "h:today") { NotificationDay(tr("Today")) }
                            items(today, key = { it.key }) { g -> NotificationRow(g, profiles, actions, unread = g.createdAt > seenAt) }
                            if (earlier.isNotEmpty()) item(key = "h:earlier") { NotificationDay(tr("Earlier")) }
                            items(earlier, key = { it.key }) { g -> NotificationRow(g, profiles, actions, unread = g.createdAt > seenAt) }
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
private fun NotificationDay(title: String) {
    Text(
        title, color = EInk.Caption, fontSize = TextSize.Caption, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
    )
}

/** What a kind of notification looks like: its drawn badge and colour. */
private fun notificationBadge(type: NostrNotification.Type): Pair<androidx.compose.ui.graphics.vector.ImageVector, androidx.compose.ui.graphics.Color> = when (type) {
    NostrNotification.Type.REPOST -> Glyphs.Repost to com.example.btcwidget.ui.main.ActionColor.Repost
    NostrNotification.Type.LIKE -> Glyphs.Liked to com.example.btcwidget.ui.main.ActionColor.Like
    NostrNotification.Type.ZAP -> Glyphs.Zap to com.example.btcwidget.ui.main.ActionColor.Zap
    else -> Glyphs.Comment to SettingsAccent
}

/**
 * One notification, or several about the same note folded together: the newest person's
 * face with a coloured badge for what they did, "Alice, Bob and 3 others liked your note",
 * and a line of the note. Replies and mentions show the whole reply, ready to answer.
 * New since you last looked: a soft tint and a dot.
 */
@Composable
private fun NotificationRow(g: NotificationGroup, profiles: Map<String, Profile>, actions: NoteActions, unread: Boolean) {
    val n = g.latest
    val tint = if (unread) SettingsAccent.copy(alpha = 0.08f) else androidx.compose.ui.graphics.Color.Transparent
    if (n.type == NostrNotification.Type.REPLY || n.type == NostrNotification.Type.MENTION) {
        Box(Modifier.background(tint)) {
            NoteCard(n.event, NoteStats(), actions, flat = true)
            if (unread) UnreadDot(Modifier.align(Alignment.TopEnd).padding(top = 18.dp, end = 16.dp))
        }
        return
    }
    val (icon, color) = notificationBadge(n.type)
    val actors = g.actors
    val names = actors.take(2).map { profiles[it]?.bestName ?: Profile.shortNpub(it) }
    val verb = when (n.type) {
        NostrNotification.Type.REPOST -> tr("reposted your note")
        NostrNotification.Type.LIKE -> if (g.items.all { it.event.content == "+" || it.event.content.isBlank() }) tr("liked your note")
            else tr("reacted") + " " + g.items.map { it.event.content }.filter { it != "+" && it.isNotBlank() && it.length <= 8 }.distinct().take(5).joinToString("")
        else -> tr("tipped you")
    }
    val target by produceKept<Event?>(null, g.noteId) { value = g.noteId?.let { runCatching { actions.resolveNote(it) }.getOrNull() } }
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .background(tint)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, enabled = g.noteId != null) {
                val id = g.noteId ?: return@clickable
                actions.openNote(target ?: n.event.copy(id = id))
            }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // The newest face, with what they did in a coloured badge on its corner.
        Box {
            Avatar(profiles[actors[0]], actors[0], size = 44, onClick = { actions.openProfile(actors[0]) })
            Box(
                Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp).size(22.dp).clip(CircleShape)
                    .background(EInk.Background).padding(2.dp).clip(CircleShape).background(color),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    icon, contentDescription = null,
                    tint = if (n.type == NostrNotification.Type.ZAP) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(NotificationGroup.names(names, actors.size)) }
                    append(" $verb")
                    if (n.type == NostrNotification.Type.ZAP) withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = com.example.btcwidget.ui.main.ActionColor.Zap)) {
                        append(" " + tr("{0} sats", formatSats(g.totalMsat / 1000)))
                    }
                    withStyle(SpanStyle(color = EInk.Caption)) { append(" · " + relativeTime(g.createdAt)) }
                },
                color = EInk.Value, fontSize = TextSize.Body, lineHeight = 20.sp
            )
            if (n.type == NostrNotification.Type.ZAP) {
                // The newest tip's message, if any.
                val comment = g.items.firstNotNullOfOrNull { z ->
                    z.event.firstTag("description")?.let { com.example.btcwidget.nostr.core.Zaps.parseReceipt(z.event)?.comment?.takeIf { c -> c.isNotBlank() } }
                }
                if (comment != null) Text("“$comment”", color = EInk.Value, fontSize = TextSize.Body, modifier = Modifier.padding(top = 2.dp))
            }
            target?.let {
                Text(
                    it.content.replace('\n', ' ').trim(), color = EInk.Caption, fontSize = TextSize.Body, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        if (unread) UnreadDot(Modifier.padding(start = 10.dp, top = 6.dp))
    }
}

/** The small accent dot on something new. */
@Composable
internal fun UnreadDot(modifier: Modifier = Modifier) {
    Box(modifier.size(9.dp).clip(CircleShape).background(SettingsAccent))
}

// ---- Home feeds: tabs and the sheet to manage them ----

/** The icon for a kind of feed, the same in the tabs' sheet and wherever feeds are listed. */
internal fun feedIcon(f: HomeFeed): androidx.compose.ui.graphics.vector.ImageVector = when (f) {
    is HomeFeed.Following -> if (f.replies) Glyphs.Chat else Glyphs.Following
    is HomeFeed.Hashtag -> Glyphs.Hashtag
    is HomeFeed.People -> Glyphs.ListLine
    else -> Glyphs.ListLine
}

/** A feed's name on its tab: short, since tabs sit side by side. */
internal fun feedTabTitle(f: HomeFeed): String = when (f) {
    is HomeFeed.Following -> tr(if (f.replies) "With replies" else "Latest")
    is HomeFeed.Hashtag -> "#" + f.tag
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
        Column(Modifier.fillMaxWidth().screenBackground()) {
            Row(Modifier.fillMaxWidth().height(FEED_TAB_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
                FeedTabStrip(feeds, current, onSelect, Modifier.weight(1f).testTag("feed_tabs"))
                CircleIconButton(Glyphs.Sliders, tr("Manage feeds"), Modifier.padding(horizontal = 6.dp), onClick = onManage)
            }
        }
    }
}

/** Height of the feed tabs, without their bottom line. */
internal val FEED_TAB_HEIGHT = 50.dp

/**
 * The feeds as capsules (design direction A): the open one filled with the text colour, the
 * others outlined; they scroll sideways when there are many, keeping the open one in view.
 */
@Composable
private fun FeedTabStrip(feeds: List<HomeFeed>, current: HomeFeed, onSelect: (HomeFeed) -> Unit, modifier: Modifier) {
    val haptics = rememberHaptics()
    val scroll = rememberScrollState()
    val selected = feeds.indexOf(current).coerceAtLeast(0)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.horizontalScroll(scroll).padding(horizontal = 12.dp)
    ) {
        feeds.forEachIndexed { i, feed ->
            com.example.btcwidget.ui.main.BlockChip(feedTabTitle(feed), selected = i == selected, filter = false, tick = false) {
                if (i != selected) haptics.tick()
                onSelect(feed)
            }
        }
    }
}

/** Tabs as a row of capsules (a profile's Posts · Replies · Media · Articles). */
@Composable
fun ChipTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val haptics = rememberHaptics()
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        labels.forEachIndexed { i, label ->
            com.example.btcwidget.ui.main.BlockChip(label, selected = i == selected, filter = false, tick = false) {
                if (i != selected) haptics.tick()
                onSelect(i)
            }
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
            .clip(Radius.Full)
            .background(
                when {
                    selected -> SettingsAccent
                    icon != null -> EInk.Track.copy(alpha = 0.55f)
                    else -> androidx.compose.ui.graphics.Color.Transparent
                }
            )
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = label) { haptics.tick(); onClick() }
            .padding(horizontal = if (selected) 14.dp else 12.dp)
    ) {
        if (icon != null) {
            androidx.compose.material3.Icon(
                icon,
                contentDescription = null,
                tint = if (selected) EInk.OnAccent else SettingsAccent,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            label,
            color = if (selected) EInk.OnAccent else EInk.Value,
            fontSize = TextSize.Caption,
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
        Modifier.size(36.dp).clip(CircleShape).clickable(role = androidx.compose.ui.semantics.Role.Button, enabled = enabled, onClickLabel = label, onClick = onClick),
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
                Text(tr("Feeds"), color = EInk.Value, fontSize = TextSize.Title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (editing) Text(
                    tr("Reset"), color = EInk.Caption, fontSize = TextSize.Body, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(Radius.Full).clickable(role = androidx.compose.ui.semantics.Role.Button) { vm.resetFeeds() }.padding(horizontal = 12.dp, vertical = 8.dp)
                )
                Box(
                    Modifier.clip(Radius.Full)
                        .then(if (editing) Modifier.background(SettingsAccent) else Modifier.border(1.dp, EInk.BodyStroke, Radius.Full))
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) { editing = !editing }
                        .padding(horizontal = 16.dp, vertical = 7.dp)
                ) {
                    Text(
                        if (editing) tr("Done") else tr("Edit"), fontSize = TextSize.Body, fontWeight = FontWeight.Bold,
                        color = if (editing) androidx.compose.ui.graphics.Color.Black else EInk.Value
                    )
                }
            }
            feeds.forEachIndexed { i, f ->
                val selected = f == current
                Row(
                    Modifier.fillMaxWidth().clip(Radius.Large).background(if (selected && !editing) EInk.Body else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, enabled = !editing) { vm.selectFeed(f); onClose() }.padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FeedIconTile(f, selected = selected)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(feedTitle(f), color = EInk.Value, fontSize = TextSize.Body, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(feedSubtitle(f), color = EInk.Caption, fontSize = TextSize.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

/** A feed you can add with one tap: its icon, name, and a + button. */
@Composable
private fun AddableFeedRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String?, onAdd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(Radius.Small).clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Add"), onClick = onAdd).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = EInk.Label, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, color = EInk.Value, fontSize = TextSize.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = EInk.Caption, fontSize = TextSize.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(30.dp).clip(CircleShape).background(EInk.Track), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(Glyphs.Plus, contentDescription = tr("Add"), tint = EInk.Value, modifier = Modifier.size(16.dp))
        }
    }
}

/** Adds a topic (a hashtag), or brings back a built-in feed. Lists are added from their own page. */
@Composable
private fun AddFeed(vm: SocialViewModel, existing: List<HomeFeed>) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().padding(top = 16.dp).clip(Radius.Large).background(EInk.Body).padding(14.dp)) {
        Text(tr("Add a topic"), color = EInk.Value, fontSize = TextSize.Body, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.OutlinedTextField(
                value = input, onValueChange = { input = it; error = null },
                placeholder = { Text("siamstr", color = EInk.Caption) },
                leadingIcon = { androidx.compose.material3.Icon(Glyphs.Hashtag, contentDescription = null, tint = EInk.Caption, modifier = Modifier.size(18.dp)) },
                singleLine = true, isError = error != null,
                shape = Radius.Large,
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedBorderColor = SettingsAccent, unfocusedBorderColor = EInk.BodyStroke, focusedTextColor = EInk.Value, unfocusedTextColor = EInk.Value, cursorColor = SettingsAccent),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            val ready = input.isNotBlank()
            Box(
                Modifier.clip(Radius.Full).background(if (ready) SettingsAccent else EInk.Track)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, enabled = ready) {
                        val feed = HomeFeed.cleanTag(input)?.let { HomeFeed.Hashtag(it) }
                        if (feed == null) error = tr("One word, letters and numbers only")
                        else { vm.addFeed(feed); input = "" }
                    }
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Text(tr("Add"), color = if (ready) androidx.compose.ui.graphics.Color.Black else EInk.Caption, fontSize = TextSize.Body, fontWeight = FontWeight.Bold)
            }
        }
        error?.let { Text(it, color = EInk.Alert, fontSize = TextSize.Caption, modifier = Modifier.padding(top = 4.dp)) }
        // Built-in feeds that were removed can come back.
        val suggested = HomeFeed.DEFAULTS.filter { it !in existing }
        if (suggested.isNotEmpty()) {
            Text(tr("Suggested"), color = EInk.Caption, fontSize = TextSize.Caption, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
            suggested.forEach { f -> AddableFeedRow(feedIcon(f), feedTitle(f), feedSubtitle(f)) { vm.addFeed(f) } }
        }
    }
}

