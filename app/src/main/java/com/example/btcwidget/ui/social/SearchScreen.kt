package com.example.btcwidget.ui.social

import androidx.compose.foundation.background
import com.example.btcwidget.ui.main.screenBackground
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import com.example.btcwidget.HashtagKey
import com.example.btcwidget.NoteKey
import com.example.btcwidget.ProfileKey
import com.example.btcwidget.nostr.FeedItem
import com.example.btcwidget.nostr.NoteStats
import com.example.btcwidget.nostr.NostrRepository
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.ui.main.EInk
import com.example.btcwidget.ui.main.EInkChip
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

/**
 * Search, as on X: before you type, Explore (trending topics, the latest posts, live streams,
 * articles, feeds, follow packs, who to follow); while you type, suggestions (people, topics,
 * and your recent searches and profiles); after you search, results in tabs (Top, Latest,
 * People, Media, Articles) with filters.
 */
@Composable
fun SearchScreen(
    vm: SocialViewModel,
    navigate: (NavKey) -> Unit,
    onBack: (() -> Unit)? = null,
    leadingMenu: (@Composable () -> Unit)? = null
) {
    val account by vm.account.collectAsState()
    val stats by vm.stats.collectAsState()
    val following by vm.following.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    // Results: 0 Top, 1 Latest, 2 People, 3 Media, 4 Articles.
    var tab by rememberSaveable { mutableStateOf(0) }
    val exploreTabs = remember(account) {
        buildList {
            add("Trending"); add("Latest"); add("Live"); add("Articles"); add("Feeds"); add("Follow packs")
            if (account != null) add("Who to follow")
        }
    }
    var exploreTab by rememberSaveable { mutableStateOf("Trending") }
    if (exploreTab !in exploreTabs) exploreTab = exploreTabs.first()
    var followsOnly by rememberSaveable { mutableStateOf(false) }
    var oldestFirst by rememberSaveable { mutableStateOf(false) }
    var filterSheet by remember { mutableStateOf(false) }
    // Results are kept across Back: open a post or a profile from them and come back to the same list.
    var searched by rememberKeptState<String?> { null }
    var peopleLoading by remember { mutableStateOf(false) }
    var notesLoading by remember { mutableStateOf(false) }
    var articlesLoading by remember { mutableStateOf(false) }
    var people by rememberKeptState<List<Profile>> { emptyList() }
    var notes by rememberKeptState<List<FeedItem>> { emptyList() }
    var articles by rememberKeptState<List<com.example.btcwidget.nostr.core.Article>> { emptyList() }
    var job by remember { mutableStateOf<Job?>(null) }
    val focus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    var recents by remember { mutableStateOf(com.example.btcwidget.AppPrefs.recentSearches(context)) }
    var recentProfiles by remember { mutableStateOf(com.example.btcwidget.AppPrefs.recentProfiles(context)) }
    var suggested by remember { mutableStateOf<List<Profile>>(emptyList()) }
    val scanQr = rememberQrScan(vm, navigate)
    // Opened fresh: start typing. Coming Back to results, leave the keyboard down.
    LaunchedEffect(Unit) { if (onBack != null && query.isEmpty() && searched == null) runCatching { focus.requestFocus() } }

    fun keepSearch(q: String) { com.example.btcwidget.AppPrefs.addRecentSearch(context, q); recents = com.example.btcwidget.AppPrefs.recentSearches(context) }
    fun openProfile(pubkey: String) {
        com.example.btcwidget.AppPrefs.addRecentProfile(context, pubkey)
        recentProfiles = com.example.btcwidget.AppPrefs.recentProfiles(context)
        navigate(ProfileKey(pubkey))
    }
    fun openTag(tag: String) { keepSearch("#$tag"); navigate(HashtagKey(tag)) }

    /** Searches [text]: an npub, note, article link or name@domain opens directly; the rest searches. */
    fun run(text: String = query) {
        val q = text.trim()
        if (q.isEmpty()) return
        query = q
        keyboard?.hide(); focusManager.clearFocus(); keepSearch(q)
        job?.cancel()
        searched = q
        tab = 0
        // People already seen show at once; relay results replace them when they arrive.
        val local = localPeople(profiles, q)
        people = local; notes = emptyList(); articles = emptyList()
        peopleLoading = true; notesLoading = true; articlesLoading = true
        job = scope.launch {
            when (val direct = vm.resolve(q)) {
                is NostrRepository.Direct.ProfileTarget -> { peopleLoading = false; notesLoading = false; articlesLoading = false; openProfile(direct.pubkey); return@launch }
                is NostrRepository.Direct.NoteTarget -> { peopleLoading = false; notesLoading = false; articlesLoading = false; navigate(NoteKey(direct.id)); return@launch }
                is NostrRepository.Direct.HashtagTarget -> { peopleLoading = false; notesLoading = false; articlesLoading = false; navigate(HashtagKey(direct.tag)); return@launch }
                is NostrRepository.Direct.ArticleTarget -> {
                    peopleLoading = false; notesLoading = false; articlesLoading = false
                    navigate(com.example.btcwidget.ArticleKey(direct.address.let { com.example.btcwidget.nostr.core.Nip19.naddr(it.kind, it.pubkey, it.identifier, it.relays) }))
                    return@launch
                }
                null -> Unit
            }
            // All at once; each list shows as soon as it arrives.
            val p = async { vm.searchPeople(q) }
            val n = async {
                // "#tag": notes with that hashtag (tag lookup on more relays), plus full-text matches.
                val tag = com.example.btcwidget.nostr.core.HomeFeed.cleanTag(q).takeIf { q.startsWith("#") }
                val tagged = if (tag != null) async { vm.hashtagFeed(tag) } else null
                ((tagged?.await().orEmpty()) + vm.searchNotes(q)).distinctBy { it.key }
            }
            val a = async { vm.searchArticles(q) }
            people = (p.await() + local).distinctBy { it.pubkey }
            peopleLoading = false
            notes = n.await()
            notesLoading = false
            articles = a.await()
            articlesLoading = false
        }
    }

    // Suggestions while typing: people already seen at once, then from the search relays.
    LaunchedEffect(query, focused) {
        val q = query.trim()
        suggested = localPeople(profiles, q).take(6)
        if (!focused || q.length < 2 || q.startsWith("#")) return@LaunchedEffect
        kotlinx.coroutines.delay(400)
        suggested = (localPeople(profiles, q) + vm.searchPeople(q)).distinctBy { it.pubkey }.take(8)
    }
    // Back: first leave typing, then leave the results for Explore.
    androidx.activity.compose.BackHandler(enabled = focused) { focusManager.clearFocus() }
    androidx.activity.compose.BackHandler(enabled = !focused && searched != null) { job?.cancel(); searched = null; query = "" }

    if (filterSheet) SearchFilters(
        followsOnly = followsOnly, onFollowsOnly = { followsOnly = it },
        oldestFirst = oldestFirst, onOldestFirst = { oldestFirst = it },
        onDismiss = { filterSheet = false }
    )

    Column(Modifier.fillMaxSize().screenBackground()) {
        // ← (or avatar menu) · the search box · QR (or Cancel while typing).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.then(if (onBack != null) Modifier.statusBarsPadding() else Modifier).fillMaxWidth().height(56.dp).padding(horizontal = 6.dp)
        ) {
            if (onBack != null) Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Back"), onClick = onBack),
                contentAlignment = Alignment.Center
            ) { androidx.compose.material3.Icon(Glyphs.Back, contentDescription = tr("Back"), tint = EInk.Value, modifier = Modifier.size(22.dp)) }
            else leadingMenu?.invoke()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp).height(40.dp).clip(RoundedCornerShape(50)).background(EInk.Body)
                    .then(if (focused) Modifier.border(1.dp, SettingsAccent, RoundedCornerShape(50)) else Modifier)
                    .padding(start = 14.dp, end = 4.dp)
            ) {
                androidx.compose.material3.Icon(NavIcons.Search.outline, contentDescription = null, tint = if (focused) SettingsAccent else EInk.Caption, modifier = Modifier.size(18.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = EInk.Value, fontSize = 16.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(SettingsAccent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onSearch = { run() }),
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp).focusRequester(focus).onFocusChanged { focused = it.isFocused },
                    decorationBox = { field ->
                        Box {
                            if (query.isEmpty()) Text(tr("Search Block"), color = EInk.Caption, fontSize = 16.sp, maxLines = 1)
                            field()
                        }
                    }
                )
                if (query.isNotEmpty()) Box(
                    Modifier.size(34.dp).clip(CircleShape).clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Clear")) {
                        query = ""; job?.cancel(); searched = null; runCatching { focus.requestFocus() }
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(EInk.Caption), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Icon(Glyphs.CloseLine, contentDescription = tr("Clear"), tint = EInk.Background, modifier = Modifier.size(12.dp))
                    }
                }
            }
            if (focused) Text(
                tr("Cancel"), color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(role = androidx.compose.ui.semantics.Role.Button) { query = searched.orEmpty(); focusManager.clearFocus() }.padding(horizontal = 10.dp, vertical = 8.dp)
            ) else Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Scan a QR code"), onClick = scanQr),
                contentAlignment = Alignment.Center
            ) { androidx.compose.material3.Icon(Glyphs.Qr, contentDescription = tr("Scan a QR code"), tint = EInk.Value, modifier = Modifier.size(22.dp)) }
        }
        HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)

        SocialScaffold(vm, navigate) { actions ->
            Box(Modifier.fillMaxSize()) {
                when {
                    // Typing: suggestions.
                    focused -> SearchSuggestions(
                        vm = vm,
                        query = query.trim(),
                        people = suggested,
                        following = following,
                        recents = recents,
                        recentProfiles = recentProfiles.map { pk -> pk to profiles[pk] },
                        onSearch = { run(it) },
                        onProfile = { openProfile(it) },
                        onTag = { openTag(it) },
                        onRemoveRecent = { com.example.btcwidget.AppPrefs.removeRecentSearch(context, it); recents = com.example.btcwidget.AppPrefs.recentSearches(context) },
                        onClearRecents = {
                            com.example.btcwidget.AppPrefs.clearRecentSearches(context); com.example.btcwidget.AppPrefs.clearRecentProfiles(context)
                            recents = emptyList(); recentProfiles = emptyList()
                        }
                    )
                    // Not searched yet: Explore.
                    searched == null -> Explore(vm, exploreTab, actions, navigate)
                    else -> {
                        val q = searched.orEmpty()
                        val shownNotes = notes.filter { !followsOnly || it.note.pubkey in following }
                        val latest = if (oldestFirst) shownNotes.sortedBy { it.sortTime } else shownNotes.sortedByDescending { it.sortTime }
                        val media = latest.filter { noteImageUrls(it.note).isNotEmpty() || com.example.btcwidget.nostr.core.Imeta.videoUrls(it.note).isNotEmpty() }
                        val shownPeople = people.filter { !followsOnly || it.pubkey in following }
                        val shownArticles = articles.filter { !followsOnly || it.pubkey in following }
                            .let { l -> if (oldestFirst) l.sortedBy { it.event.createdAt } else l.sortedByDescending { it.event.createdAt } }
                        Column(Modifier.fillMaxSize()) {
                            val tagLink: @Composable () -> Unit = {
                                com.example.btcwidget.nostr.core.HomeFeed.cleanTag(q)?.takeIf { q.startsWith("#") }?.let { tag ->
                                    TagRow(tag, tr("See every post with #{0}", tag)) { openTag(tag) }
                                }
                            }
                            when (tab) {
                                0 -> {
                                    val nothing = !peopleLoading && !notesLoading && !articlesLoading && shownPeople.isEmpty() && shownNotes.isEmpty() && shownArticles.isEmpty()
                                    if (nothing && !q.startsWith("#")) NoResults(q) else NoteList(
                                        shownNotes, stats, actions,
                                        header = {
                                            tagLink()
                                            if (shownPeople.isNotEmpty()) {
                                                SectionTitle(tr("People"), if (shownPeople.size > 3) tr("See all") else null) { tab = 2 }
                                                shownPeople.take(3).forEach { p -> PersonRow(p, isFollowing = p.pubkey in following, onClick = { openProfile(p.pubkey) }) }
                                            }
                                            if (shownArticles.isNotEmpty()) {
                                                SectionTitle(tr("Articles"), if (shownArticles.size > 2) tr("See all") else null) { tab = 4 }
                                                shownArticles.take(2).forEach { a ->
                                                    ArticleRow(a, onClick = { actions.openArticle(com.example.btcwidget.nostr.core.Nip19.Address(a.event.kind, a.pubkey, a.identifier)) })
                                                }
                                            }
                                            if (shownPeople.isNotEmpty() || shownArticles.isNotEmpty()) SectionTitle(tr("Posts"), null) {}
                                            if (notesLoading && shownNotes.isEmpty()) NotesSkeleton(3)
                                        }
                                    )
                                }
                                1 -> when {
                                    notesLoading && latest.isEmpty() -> NotesSkeleton()
                                    latest.isEmpty() -> NoResults(q)
                                    else -> NoteList(latest, stats, actions, header = tagLink)
                                }
                                2 -> when {
                                    peopleLoading && shownPeople.isEmpty() -> NotesSkeleton()
                                    shownPeople.isEmpty() -> NoResults(q)
                                    else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                                        items(shownPeople, key = { it.pubkey }) { p -> PersonRow(p, isFollowing = p.pubkey in following, onClick = { openProfile(p.pubkey) }) }
                                    }
                                }
                                3 -> when {
                                    notesLoading && media.isEmpty() -> NotesSkeleton()
                                    media.isEmpty() -> NoResults(q)
                                    else -> NoteList(media, stats, actions)
                                }
                                else -> when {
                                    articlesLoading && shownArticles.isEmpty() -> NotesSkeleton()
                                    shownArticles.isEmpty() -> NoResults(q)
                                    else -> ArticleList(shownArticles, actions)
                                }
                            }
                        }
                    }
                }

                // Floating filter capsule at the bottom; rides directly above the keyboard when typing (like ReplyBar).
                val showFloatingBar = LocalBarsVisible.current || focused
                val floatAlpha by androidx.compose.animation.core.animateFloatAsState(if (showFloatingBar) 1f else 0f, label = "search_float_bar")
                if (floatAlpha > 0.01f) {
                    FloatingSearchFilterBar(
                        searched = searched != null,
                        exploreTabs = exploreTabs,
                        selectedExploreTab = exploreTab,
                        onSelectExploreTab = { t ->
                            exploreTab = t
                            if (focused) {
                                query = ""
                                focusManager.clearFocus()
                                keyboard?.hide()
                            }
                        },
                        selectedResultTab = tab,
                        onSelectResultTab = { i ->
                            tab = i
                            if (focused) {
                                focusManager.clearFocus()
                                keyboard?.hide()
                            }
                        },
                        hasActiveFilters = followsOnly || oldestFirst,
                        onOpenFilters = {
                            focusManager.clearFocus()
                            keyboard?.hide()
                            filterSheet = true
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .then(if (onBack != null) Modifier.navigationBarsPadding() else Modifier)
                            .imePadding()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .graphicsLayer {
                                alpha = floatAlpha
                                translationY = (1f - floatAlpha) * 44f
                            }
                    )
                }
            }
        }
    }
}

/**
 * Floating filter capsule at the bottom of Search (and riding above the keyboard when typing):
 * shows Explore categories (Trending, Latest, Live, Articles, Feeds, Follow packs) before a
 * search, or Result tabs (Top, Latest, People, Media, Articles + filter button) after searching.
 */
@Composable
private fun FloatingSearchFilterBar(
    searched: Boolean,
    exploreTabs: List<String>,
    selectedExploreTab: String,
    onSelectExploreTab: (String) -> Unit,
    selectedResultTab: Int,
    onSelectResultTab: (Int) -> Unit,
    hasActiveFilters: Boolean,
    onOpenFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(50)
    val resultTabs = remember { listOf("Top", "Latest", "People", "Media", "Articles") }
    val rowState = rememberLazyListState()
    val activeIndex = if (searched) selectedResultTab else exploreTabs.indexOf(selectedExploreTab)
    LaunchedEffect(searched, activeIndex) {
        if (activeIndex >= 0) rowState.animateScrollToItem((activeIndex - 1).coerceAtLeast(0))
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .shadow(12.dp, shape)
            .clip(shape)
            .background(EInk.Body.copy(alpha = 0.97f))
            .border(0.75.dp, EInk.BodyStroke, shape)
            .padding(horizontal = 6.dp)
    ) {
        LazyRow(
            state = rowState,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            if (!searched) {
                items(exploreTabs, key = { "exp:$it" }) { t ->
                    val icon = when (t) {
                        "Trending" -> Glyphs.Sparkle
                        "Latest" -> Glyphs.Clock
                        "Live" -> Glyphs.Play
                        "Articles" -> Glyphs.ListLine
                        "Feeds" -> Glyphs.Globe
                        "Follow packs" -> Glyphs.Following
                        else -> Glyphs.Person
                    }
                    FeedTab(
                        label = tr(t),
                        selected = selectedExploreTab == t,
                        icon = icon,
                        onClick = { onSelectExploreTab(t) }
                    )
                }
            } else {
                items(resultTabs.size, key = { "res:$it" }) { i ->
                    val label = resultTabs[i]
                    FeedTab(
                        label = tr(label),
                        selected = selectedResultTab == i,
                        onClick = { onSelectResultTab(i) }
                    )
                }
            }
        }
        if (searched) {
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .width(0.5.dp)
                    .height(22.dp)
                    .background(EInk.BodyStroke)
            )
            Box {
                CircleIconButton(Glyphs.Sliders, tr("Search filters"), Modifier.size(36.dp), onClick = onOpenFilters)
                if (hasActiveFilters) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp, end = 4.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(SettingsAccent)
                    )
                }
            }
        }
    }
}

/** Nothing found: a magnifier, what was searched, and what to try. */
@Composable
private fun NoResults(q: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(EInk.Body), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(NavIcons.Search.outline, contentDescription = null, tint = EInk.Caption, modifier = Modifier.size(30.dp))
        }
        Text(
            tr("No results for \"{0}\"", q), color = EInk.Value, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            tr("Try another spelling, fewer words, or a #topic."), color = EInk.Caption, fontSize = 15.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/** A #topic row: the tag in bold, a line under it, and a chevron. */
@Composable
private fun TagRow(tag: String, detail: String?, caption: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            caption?.let { Text(it, color = EInk.Caption, fontSize = 13.sp) }
            Text("#$tag", color = EInk.Value, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            detail?.let { Text(it, color = EInk.Caption, fontSize = 13.sp, maxLines = 1) }
        }
        androidx.compose.material3.Icon(Glyphs.Chevron, contentDescription = null, tint = EInk.Caption, modifier = Modifier.size(20.dp))
    }
    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
}

/**
 * While typing: with nothing typed, profiles you opened lately and your recent searches; once
 * you type, a "Search for …" row, matching topics and matching people.
 */
@Composable
private fun SearchSuggestions(
    vm: SocialViewModel,
    query: String,
    people: List<Profile>,
    following: Set<String>,
    recents: List<String>,
    recentProfiles: List<Pair<String, Profile?>>,
    onSearch: (String) -> Unit,
    onProfile: (String) -> Unit,
    onTag: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecents: () -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        if (query.isEmpty()) {
            if (recents.isEmpty() && recentProfiles.isEmpty()) item(key = "hint") {
                Text(
                    tr("Try searching for people, #topics or keywords. You can also paste an npub, a note link or name@domain."),
                    color = EInk.Caption, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp)
                )
            } else item(key = "head") {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Recent"), color = EInk.Value, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(
                        tr("Clear all"), color = SettingsAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClearRecents).padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            if (recentProfiles.isNotEmpty()) item(key = "people") {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    recentProfiles.forEach { (pk, p) ->
                        Column(
                            Modifier.width(76.dp).clip(RoundedCornerShape(12.dp)).clickable(role = androidx.compose.ui.semantics.Role.Button) { onProfile(pk) }.padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Avatar(p, pk, size = 54)
                            Text(
                                p?.bestName ?: Profile.shortNpub(pk), color = EInk.Value, fontSize = 12.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp)
                            )
                        }
                    }
                }
            }
            items(recents, key = { "r:$it" }) { q ->
                Row(
                    Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Search")) { onSearch(q) }.padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Icon(Glyphs.Clock, contentDescription = null, tint = EInk.Caption, modifier = Modifier.size(20.dp))
                    Text(q, color = EInk.Value, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 14.dp))
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Remove")) { onRemoveRecent(q) },
                        contentAlignment = Alignment.Center
                    ) { androidx.compose.material3.Icon(Glyphs.CloseLine, contentDescription = tr("Remove"), tint = EInk.Caption, modifier = Modifier.size(16.dp)) }
                }
            }
            return@LazyColumn
        }
        item(key = "go") {
            Row(
                Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Button) { onSearch(query) }.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Icon(NavIcons.Search.outline, contentDescription = null, tint = SettingsAccent, modifier = Modifier.size(20.dp))
                Text(
                    tr("Search for \"{0}\"", query), color = EInk.Value, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 14.dp)
                )
            }
            HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
        }
        val tags = vm.hashtagSuggestions(query.removePrefix("#")).take(4)
        items(tags, key = { "t:$it" }) { tag ->
            Row(
                Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Button) { onTag(tag) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Icon(Glyphs.Hashtag, contentDescription = null, tint = EInk.Caption, modifier = Modifier.size(20.dp))
                Text("#$tag", color = EInk.Value, fontSize = 16.sp, modifier = Modifier.padding(start = 14.dp))
            }
        }
        items(people, key = { "p:" + it.pubkey }) { p ->
            Row(
                Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Button) { onProfile(p.pubkey) }.padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(p, p.pubkey, size = 42)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    DisplayName(p, p.pubkey, EInk.Value, 15.sp)
                    Text(handleText(p, p.pubkey), color = EInk.Caption, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (p.pubkey in following) Text(tr("Following"), color = EInk.Caption, fontSize = 12.sp)
            }
        }
    }
}

/** Search filters, as Amethyst has them: only people you follow, and newest or oldest first. */
@Composable
private fun SearchFilters(
    followsOnly: Boolean, onFollowsOnly: (Boolean) -> Unit,
    oldestFirst: Boolean, onOldestFirst: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    BlockSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(tr("Search filters"), color = EInk.Value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(12.dp)).clickable(role = androidx.compose.ui.semantics.Role.Button) { onFollowsOnly(!followsOnly) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Only people I follow"), color = EInk.Value, fontSize = 16.sp)
                    Text(tr("Posts, people and articles from your follows"), color = EInk.Caption, fontSize = 13.sp)
                }
                androidx.compose.material3.Switch(
                    checked = followsOnly, onCheckedChange = onFollowsOnly,
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = androidx.compose.ui.graphics.Color.Black, checkedTrackColor = SettingsAccent,
                        uncheckedThumbColor = EInk.Caption, uncheckedTrackColor = EInk.Body, uncheckedBorderColor = EInk.BodyStroke
                    )
                )
            }
            Text(tr("Order (Latest, Media, Articles)"), color = EInk.Caption, fontSize = 13.sp, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoicePill(tr("Newest first"), selected = !oldestFirst) { onOldestFirst(false) }
                ChoicePill(tr("Oldest first"), selected = oldestFirst) { onOldestFirst(true) }
            }
            if (followsOnly || oldestFirst) Text(
                tr("Reset"), color = SettingsAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 18.dp).clip(RoundedCornerShape(50)).clickable(role = androidx.compose.ui.semantics.Role.Button) { onFollowsOnly(false); onOldestFirst(false) }.padding(vertical = 6.dp, horizontal = 4.dp)
            )
        }
    }
}

/** A section heading in Top results, with an optional "See all" link. */
@Composable
private fun SectionTitle(title: String, more: String?, onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = EInk.Value, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        more?.let {
            Text(
                it, color = SettingsAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onMore).padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

/** Profiles already loaded whose name, display name or NIP-05 contains [q], best matches first. */
private fun localPeople(profiles: Map<String, Profile>, q: String): List<Profile> {
    val needle = q.lowercase().removePrefix("@")
    if (needle.length < 2 || needle.startsWith("#")) return emptyList()
    return profiles.values.filter { p -> listOfNotNull(p.name, p.displayName, p.nip05).any { it.lowercase().contains(needle) } }
        .sortedBy { p -> if (listOfNotNull(p.name, p.displayName).any { it.lowercase().startsWith(needle) }) 0 else 1 }
        .take(20)
}

/** Explore, before you search: tabs of things to find, controlled by the bottom FloatingSearchFilterBar. */
@Composable
private fun Explore(vm: SocialViewModel, tab: String, actions: NoteActions, navigate: (NavKey) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        when (tab) {
            "Trending" -> Trending(vm, navigate)
            "Latest" -> LatestPosts(vm, actions)
            "Live" -> LiveNow(vm, navigate)
            "Articles" -> RecentArticles(vm, actions)
            "Feeds" -> DvmFeeds(vm, actions)
            "Follow packs" -> FollowPackList(vm, navigate)
            else -> WhoToFollow(vm, actions)
        }
    }
}

/** Hashtags the most people on your relays used in the last day. */
@Composable
private fun Trending(vm: SocialViewModel, navigate: (NavKey) -> Unit) {
    val trends by produceKept<List<NostrRepository.Trend>?>(null) { value = vm.trendingHashtags() }
    when (val l = trends) {
        null -> NotesSkeleton(5)
        else -> if (l.isEmpty()) EmptyNote(tr("Nothing trending on your relays right now.")) else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
            item(key = "note") {
                Text(
                    tr("Topics the most people on your relays used in the last 24 hours."),
                    color = EInk.Caption, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
            items(l.size, key = { "t:" + l[it].tag }) { i ->
                val t = l[i]
                TagRow(
                    t.tag,
                    detail = tr("{0} people · {1} posts", compactCount(t.people), compactCount(t.notes)),
                    caption = tr("{0} · Trending", i + 1)
                ) { navigate(HashtagKey(t.tag)) }
            }
        }
    }
}

/** Live streams on now (and coming up). */
@Composable
private fun LiveNow(vm: SocialViewModel, navigate: (NavKey) -> Unit) {
    val profiles by vm.profiles.collectAsState()
    val list by produceKept<List<com.example.btcwidget.nostr.core.LiveStreams.Live>?>(null) { value = vm.liveStreams() }
    when (val l = list) {
        null -> NotesSkeleton(4)
        else -> if (l.isEmpty()) EmptyNote(tr("No one is live right now.")) else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
            items(l, key = { it.address }) { live -> LiveRow(live, profiles[live.host]) { navigate(com.example.btcwidget.LiveKey(live.address)) } }
        }
    }
}

/** The newest posts from everyone, or from one of your relays. */
@Composable
private fun LatestPosts(vm: SocialViewModel, actions: NoteActions) {
    val stats by vm.stats.collectAsState()
    val scope = rememberCoroutineScope()
    var relay by rememberSaveable { mutableStateOf<String?>(null) }
    var items by remember(relay) { mutableStateOf<List<FeedItem>?>(null) }
    var loadingMore by remember(relay) { mutableStateOf(false) }
    var end by remember(relay) { mutableStateOf(false) }
    LaunchedEffect(relay) { items = vm.globalFeed(relay) }
    Column(Modifier.fillMaxSize()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            ChoicePill(tr("Everyone"), selected = relay == null, icon = Glyphs.Globe) { relay = null }
            vm.relays.forEach { url ->
                ChoicePill(url.removePrefix("wss://").removePrefix("ws://").removeSuffix("/"), selected = relay == url, icon = Glyphs.Relay) { relay = url }
            }
        }
        when (val list = items) {
            null -> NotesSkeleton()
            else -> if (list.isEmpty()) EmptyNote(tr("Nothing here right now.")) else NoteList(
                list, stats, actions,
                loadingMore = loadingMore,
                onLoadMore = {
                    if (!loadingMore && !end) {
                        loadingMore = true
                        scope.launch {
                            val more = vm.globalFeed(relay, until = list.minOf { it.sortTime } - 1)
                            items = (list + more).distinctBy { it.key }
                            end = more.isEmpty()
                            loadingMore = false
                        }
                    }
                }
            )
        }
    }
}

/** Articles, newest first; tap to read. */
@Composable
private fun ArticleList(list: List<com.example.btcwidget.nostr.core.Article>, actions: NoteActions, header: (@Composable () -> Unit)? = null) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
        header?.let { h -> item(key = "header") { h() } }
        items(list, key = { it.pubkey + ":" + it.identifier }) { a ->
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(actions.profileOf(a.pubkey), a.pubkey, size = 20, onClick = { actions.openProfile(a.pubkey) })
                    Text(
                        actions.profileOf(a.pubkey)?.bestName ?: Profile.shortNpub(a.pubkey), color = EInk.Label, fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(start = 6.dp)
                    )
                }
                ArticleRow(a, onClick = { actions.openArticle(com.example.btcwidget.nostr.core.Nip19.Address(a.event.kind, a.pubkey, a.identifier)) })
            }
        }
    }
}

/** Recent long-form articles: from people you follow, or from everyone, with an in-app Write Article button. */
@Composable
private fun RecentArticles(vm: SocialViewModel, actions: NoteActions) {
    val account by vm.account.collectAsState()
    var following by rememberSaveable { mutableStateOf(account != null) }
    var reload by rememberSaveable { mutableStateOf(0) }
    var composingArticle by remember { mutableStateOf(false) }
    val list by produceKept<List<com.example.btcwidget.nostr.core.Article>?>(null, following, reload) {
        value = null; value = vm.recentArticles(following)
    }
    if (composingArticle) {
        ArticleComposerDialog(
            vm = vm,
            onDismiss = { composingArticle = false },
            onPublished = { published ->
                composingArticle = false
                reload++
                actions.openArticle(com.example.btcwidget.nostr.core.Nip19.Address(published.event.kind, published.pubkey, published.identifier))
            }
        )
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (account != null) ChoicePill(tr("Following"), selected = following) { following = true }
            ChoicePill(tr("Everyone"), selected = !following) { following = false }
            Spacer(Modifier.weight(1f))
            if (account != null) {
                ChoicePill("+ " + tr("Write article"), selected = false, icon = Glyphs.Quote) { composingArticle = true }
            }
        }
        when (val l = list) {
            null -> Spinner(Modifier.padding(top = 30.dp))
            else -> if (l.isEmpty()) EmptyNote(if (following) tr("No articles from people you follow yet. Try Everyone.") else tr("No articles found on your relays."))
            else ArticleList(l, actions)
        }
    }
}

/** Feeds made by Data Vending Machines (NIP-90): pick one to see what it suggests. */
@Composable
private fun DvmFeeds(vm: SocialViewModel, actions: NoteActions) {
    val stats by vm.stats.collectAsState()
    val services by produceKept<List<com.example.btcwidget.nostr.core.Dvm.Service>?>(null) { value = vm.feedServices() }
    var chosen by remember { mutableStateOf<com.example.btcwidget.nostr.core.Dvm.Service?>(null) }
    var reload by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }
    val c = chosen
    if (c == null) {
        when (val l = services) {
            null -> Spinner(Modifier.padding(top = 30.dp))
            else -> if (l.isEmpty()) EmptyNote(tr("No feeds found on your relays.")) else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
                item(key = "note") {
                    Text(
                        tr("Feeds from Data Vending Machines: services that pick notes for you. Asking one doesn't reveal who you are, except feeds marked personalized."),
                        color = EInk.Caption, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
                items(l, key = { it.key }) { svc ->
                    Row(Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Button) { chosen = svc }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.layout.Box(
                            Modifier.size(40.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).background(EInk.Body),
                            contentAlignment = Alignment.Center
                        ) {
                            if (svc.picture != null) coil3.compose.AsyncImage(model = svc.picture, contentDescription = null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else androidx.compose.material3.Icon(Glyphs.Sparkle, contentDescription = null, tint = EInk.Value, modifier = Modifier.size(20.dp))
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(svc.name + if (svc.personalized) tr("  · personalized") else "", color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            svc.about?.let { Text(it, color = EInk.Label, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
                }
            }
        }
        return
    }
    val result by produceKept<Result<List<FeedItem>>?>(null, c, reload) { value = null; value = vm.dvmFeed(c) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ChoicePill(tr("All feeds"), selected = false, icon = Glyphs.Back) { chosen = null }
            Text(c.name, color = EInk.Value, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
            ChoicePill(tr("Refresh"), selected = false) { reload++ }
        }
        when (val r = result) {
            null -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spinner(Modifier.padding(top = 30.dp))
                Text(tr("Asking {0}…", c.name), color = EInk.Caption, fontSize = 12.sp)
            }
            else -> r.fold(
                onSuccess = { items -> if (items.isEmpty()) EmptyNote(tr("This feed has nothing right now.")) else NoteList(items, stats, actions) },
                onFailure = { EmptyNote(it.message ?: tr("Couldn't load this feed.")) }
            )
        }
    }
}

/** People followed by several of the people you follow, with a Follow button. */
@Composable
private fun WhoToFollow(vm: SocialViewModel, actions: NoteActions) {
    val following by vm.following.collectAsState()
    val list by produceKept<List<Pair<Profile, Int>>?>(null) { value = vm.followSuggestions() }
    when (val l = list) {
        null -> Spinner(Modifier.padding(top = 30.dp))
        else -> if (l.isEmpty()) EmptyNote(tr("No suggestions yet. Follow a few people first.")) else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 88.dp)) {
            items(l, key = { it.first.pubkey }) { (p, shared) ->
                val on = p.pubkey in following
                Column {
                    Text(
                        tr("Followed by {0} people you follow", shared), color = EInk.Caption, fontSize = 12.sp,
                        modifier = Modifier.padding(start = 68.dp, top = 8.dp)
                    )
                    PersonRow(p, isFollowing = on, onClick = { actions.openProfile(p.pubkey) }) {
                        ChoicePill(if (on) tr("Following") else tr("Follow"), selected = !on) { actions.follow(p.pubkey, !on) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PersonRow(profile: Profile, isFollowing: Boolean, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Avatar(profile, profile.pubkey, size = 44)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DisplayName(profile, profile.pubkey, EInk.Value, 15.sp, Modifier.weight(1f, fill = false))
                if (isFollowing) Text(tr("  · Following"), color = EInk.Caption, fontSize = 12.sp)
            }
            Text(handleText(profile, profile.pubkey), color = EInk.Caption, fontSize = 13.sp, maxLines = 1)
            profile.about?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, color = EInk.Label, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
}
