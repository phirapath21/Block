package com.example.btcwidget.ui.social

import android.content.Context
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.example.btcwidget.ui.main.screenBackground
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import coil3.compose.AsyncImage
import com.example.btcwidget.ArticleKey
import com.example.btcwidget.LoginKey
import com.example.btcwidget.nostr.NoteStats
import com.example.btcwidget.nostr.core.Article
import com.example.btcwidget.nostr.core.Markdown
import com.example.btcwidget.nostr.core.Nip19
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.ui.main.EInk
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** Reader typography settings for long-form articles. */
internal data class ReaderPrefs(
    val fontSizeSp: Int = 17,
    val useSerif: Boolean = false,
    val relaxedLeading: Boolean = true
) {
    val fontFamily: FontFamily get() = if (useSerif) FontFamily.Serif else FontFamily.SansSerif
    val lineHeightSp: Int get() = if (relaxedLeading) (fontSizeSp * 1.62f).toInt() else (fontSizeSp * 1.42f).toInt()
}

/** Persistent reading progress per article address (`blockIdx` and `pct` 0..100). */
internal object ArticleReadingStore {
    private const val PREF = "article_reading_prefs"

    fun savePosition(context: Context, address: String, blockIdx: Int, pct: Int) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putInt("idx:$address", blockIdx)
            .putInt("pct:$address", pct.coerceIn(0, 100))
            .apply()
    }

    fun savedBlockIndex(context: Context, address: String): Int =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt("idx:$address", 0)

    fun savedPercent(context: Context, address: String): Int =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt("pct:$address", 0)
}

/** A long-form article (NIP-23), rendered from its Markdown with reader controls, TTS, bookmarks & progress. */
@Composable
fun ArticleScreen(vm: SocialViewModel, naddr: String, navigate: (NavKey) -> Unit, onBack: () -> Unit) {
    val address = remember(naddr) { Nip19.decode(naddr) as? Nip19.Address }
    val stats by vm.stats.collectAsState()
    val account by vm.account.collectAsState()
    val savedArticles by vm.savedArticles.collectAsState()
    var reloadArticle by rememberSaveable { mutableIntStateOf(0) }
    val loaded by produceKept<Any?>(Unit, address, reloadArticle) { value = address?.let { vm.article(it) } }

    var showReaderSettings by rememberSaveable { mutableStateOf(false) }
    var showToc by remember { mutableStateOf(false) }
    var editingArticle by remember { mutableStateOf(false) }
    var fontSizeSp by rememberSaveable { mutableIntStateOf(17) }
    var useSerif by rememberSaveable { mutableStateOf(false) }
    var relaxedLeading by rememberSaveable { mutableStateOf(true) }
    val readerPrefs = remember(fontSizeSp, useSerif, relaxedLeading) {
        ReaderPrefs(fontSizeSp, useSerif, relaxedLeading)
    }

    val articleObj = loaded as? Article
    val isBookmarked = remember(articleObj?.address, savedArticles) {
        articleObj != null && savedArticles.any { it.address == articleObj.address }
    }
    val blocks = remember(articleObj?.event?.id) {
        articleObj?.let { Markdown.parse(it.content) }.orEmpty()
    }
    val headings = remember(blocks) {
        blocks.mapIndexedNotNull { idx, b ->
            (b as? Markdown.Block.Heading)?.let { h ->
                val title = com.example.btcwidget.nostr.core.spansText(h.spans).trim()
                if (title.isNotEmpty()) Triple(idx, h.level, title) else null
            }
        }
    }

    if (editingArticle && articleObj != null) {
        ArticleComposerDialog(
            vm = vm,
            initialArticle = articleObj,
            onDismiss = { editingArticle = false },
            onPublished = { updated ->
                editingArticle = false
                reloadArticle++
                navigate(ArticleKey(updated.naddr()))
            }
        )
    }

    Column(Modifier.fillMaxSize().screenBackground()) {
        ArticleTopBar(
            title = articleObj?.title ?: tr("Article"),
            hasToc = headings.size >= 2,
            isBookmarked = isBookmarked,
            canEdit = articleObj != null && account?.pubkey == articleObj.pubkey,
            showReaderSettings = showReaderSettings,
            onToggleBookmark = { articleObj?.let { vm.toggleSavedArticle(it) } },
            onEdit = { editingArticle = true },
            onToggleReaderSettings = { showReaderSettings = !showReaderSettings },
            onOpenToc = { showToc = true },
            onBack = onBack
        )

        // Collapsible Reader Typography Control Strip (Aa)
        AnimatedVisibility(
            visible = showReaderSettings,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            ReaderSettingsBar(
                prefs = readerPrefs,
                onFontSizeChange = { fontSizeSp = it.coerceIn(14, 24) },
                onSerifChange = { useSerif = it },
                onLeadingChange = { relaxedLeading = it }
            )
        }

        SocialScaffold(vm, navigate) { actions ->
            when (val a = loaded) {
                Unit -> Spinner(Modifier.padding(top = 40.dp))
                is Article -> ArticleBody(
                    vm = vm,
                    article = a,
                    blocks = blocks,
                    headings = headings,
                    readerPrefs = readerPrefs,
                    showToc = showToc,
                    onDismissToc = { showToc = false },
                    onOpenToc = { showToc = true },
                    stats = stats[a.event.id] ?: NoteStats(),
                    actions = actions,
                    navigate = navigate
                )
                else -> EmptyNote(tr("This article couldn't be found on your relays."))
            }
        }
    }
}

@Composable
private fun ArticleTopBar(
    title: String,
    hasToc: Boolean,
    isBookmarked: Boolean,
    canEdit: Boolean,
    showReaderSettings: Boolean,
    onToggleBookmark: () -> Unit,
    onEdit: () -> Unit,
    onToggleReaderSettings: () -> Unit,
    onOpenToc: () -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxWidth().screenBackground().statusBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Back"), onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Glyphs.Back, contentDescription = tr("Back"), tint = EInk.Value, modifier = Modifier.size(22.dp))
            }
            Text(
                text = title,
                color = EInk.Value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp)
            )
            if (canEdit) {
                Box(
                    Modifier
                        .padding(end = 4.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(EInk.Body)
                        .border(0.5.dp, EInk.BodyStroke, CircleShape)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Edit article"), onClick = onEdit),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Glyphs.Quote, contentDescription = tr("Edit article"), tint = EInk.Value, modifier = Modifier.size(16.dp))
                }
            }
            Box(
                Modifier
                    .padding(end = 4.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isBookmarked) EInk.Accent.copy(alpha = 0.16f) else EInk.Body)
                    .border(0.5.dp, if (isBookmarked) EInk.Accent else EInk.BodyStroke, CircleShape)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Save to Reading List"), onClick = onToggleBookmark),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isBookmarked) Glyphs.Bookmarked else Glyphs.Bookmark,
                    contentDescription = tr("Save to Reading List"),
                    tint = if (isBookmarked) EInk.Accent else EInk.Value,
                    modifier = Modifier.size(17.dp)
                )
            }
            if (hasToc) {
                Box(
                    Modifier
                        .padding(end = 4.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(EInk.Body)
                        .border(0.5.dp, EInk.BodyStroke, CircleShape)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Table of contents"), onClick = onOpenToc),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Glyphs.ListLine, contentDescription = tr("Table of contents"), tint = EInk.Value, modifier = Modifier.size(17.dp))
                }
            }
            Box(
                Modifier
                    .padding(end = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (showReaderSettings) EInk.Accent else EInk.Body)
                    .border(0.5.dp, if (showReaderSettings) EInk.Accent else EInk.BodyStroke, RoundedCornerShape(50))
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Reading settings"), onClick = onToggleReaderSettings)
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Aa",
                    color = if (showReaderSettings) Color.Black else EInk.Value,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
    }
}

@Composable
private fun ArticleBody(
    vm: SocialViewModel,
    article: Article,
    blocks: List<Markdown.Block>,
    headings: List<Triple<Int, Int, String>>,
    readerPrefs: ReaderPrefs,
    showToc: Boolean,
    onDismissToc: () -> Unit,
    onOpenToc: () -> Unit,
    stats: NoteStats,
    actions: NoteActions,
    navigate: (NavKey) -> Unit
) {
    val author = actions.profileOf(article.pubkey)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val account by vm.account.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val following by vm.following.collectAsState()
    val listState = rememberLazyListState()

    val headerItemsCount = if (article.image != null) 2 else 1

    // Estimated reading time (~200 words/min)
    val readingMinutes = remember(article.event.id) {
        val words = article.content.split(Regex("\\s+")).count { it.isNotBlank() }
        maxOf(1, (words + 120) / 200)
    }

    // Smooth reading progress (0f..1f)
    val rawProgress by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total <= 1) 0f
            else {
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                if (lastVisible >= total - 1) 1f
                else (listState.firstVisibleItemIndex.toFloat() / (total - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
            }
        }
    }
    val progress by animateFloatAsState(rawProgress, label = "reading_progress")

    // ---- Phase 2: Save & Resume Reading Position ----
    val initialSavedBlockIdx = remember(article.address) {
        ArticleReadingStore.savedBlockIndex(context, article.address)
    }
    val initialSavedPct = remember(article.address) {
        ArticleReadingStore.savedPercent(context, article.address)
    }
    var showResumeBanner by remember(article.address) {
        mutableStateOf(initialSavedBlockIdx >= 2 && initialSavedPct in 5..95)
    }
    val currentBlockIndex by remember {
        derivedStateOf {
            (listState.firstVisibleItemIndex - headerItemsCount).coerceAtLeast(0)
        }
    }
    LaunchedEffect(currentBlockIndex, rawProgress) {
        if (currentBlockIndex >= 1 || rawProgress > 0.1f) {
            val pct = (rawProgress * 100f).toInt().coerceIn(0, 100)
            ArticleReadingStore.savePosition(context, article.address, currentBlockIndex, pct)
        }
        if (currentBlockIndex >= initialSavedBlockIdx && initialSavedBlockIdx > 0) {
            showResumeBanner = false
        }
    }

    // ---- Phase 1: Text-to-Speech (TTS) Audio Reader ----
    val speakableBlocks = remember(blocks) {
        blocks.mapIndexedNotNull { idx, b -> blockText(b)?.let { idx to it } }
    }
    var ttsReady by remember { mutableStateOf(false) }
    var ttsPlaying by remember { mutableStateOf(false) }
    var ttsActive by remember { mutableStateOf(false) }
    var ttsSpeed by rememberSaveable { mutableFloatStateOf(1.0f) }
    var ttsCursor by rememberSaveable { mutableIntStateOf(0) } // index into speakableBlocks
    val currentSayingBlockIdx = remember(ttsActive, ttsCursor, speakableBlocks) {
        if (ttsActive && ttsCursor in speakableBlocks.indices) speakableBlocks[ttsCursor].first else -1
    }

    val ttsRef = remember { mutableStateOf<TextToSpeech?>(null) }

    fun speakAtCursor(cursor: Int) {
        val engine = ttsRef.value ?: return
        if (speakableBlocks.isEmpty()) return
        val clamped = cursor.coerceIn(0, speakableBlocks.lastIndex)
        ttsCursor = clamped
        ttsActive = true
        ttsPlaying = true
        val (_, paragraph) = speakableBlocks[clamped]
        val hasThai = paragraph.any { it in '\u0E00'..'\u0E7F' }
        val targetLocale = if (hasThai) Locale("th", "TH") else Locale.US
        runCatching { engine.language = targetLocale }
        runCatching { engine.setSpeechRate(ttsSpeed) }
        engine.speak(paragraph, TextToSpeech.QUEUE_FLUSH, null, "utt_$clamped")
    }

    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        val idx = utteranceId?.removePrefix("utt_")?.toIntOrNull() ?: return
                        scope.launch {
                            if (idx + 1 < speakableBlocks.size) {
                                speakAtCursor(idx + 1)
                            } else {
                                ttsPlaying = false
                                ttsActive = false
                            }
                        }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        scope.launch { ttsPlaying = false }
                    }
                })
            }
        }
        ttsRef.value = engine
        onDispose {
            runCatching {
                engine.stop()
                engine.shutdown()
            }
            ttsRef.value = null
        }
    }

    // Auto-scroll to currently spoken paragraph
    LaunchedEffect(currentSayingBlockIdx) {
        if (currentSayingBlockIdx >= 0) {
            runCatching {
                listState.animateScrollToItem((headerItemsCount + currentSayingBlockIdx).coerceAtLeast(0))
            }
        }
    }

    // Long-press a paragraph: share it as a quote, read aloud from there, or copy it.
    var menuFor by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var quoteToFeedText by remember { mutableStateOf<String?>(null) }
    var focusedBlockIdx by remember { mutableIntStateOf(-1) }
    var showShareStudio by remember { mutableStateOf(false) }

    LaunchedEffect(focusedBlockIdx) {
        if (focusedBlockIdx >= 0) {
            delay(2600)
            focusedBlockIdx = -1
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Top Reading Progress Bar (Bitcoin Orange)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .background(EInk.Track)
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .background(EInk.Accent)
                )
            }

            // Phase 2: Resume where you left off banner
            AnimatedVisibility(
                visible = showResumeBanner,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(EInk.Body)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        tr("Resume where you left off ({0}%)", initialSavedPct),
                        color = EInk.Value,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(EInk.Accent)
                                .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                                    showResumeBanner = false
                                    scope.launch {
                                        listState.animateScrollToItem(headerItemsCount + initialSavedBlockIdx)
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(tr("Jump"), color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .clickable(role = androidx.compose.ui.semantics.Role.Button) { showResumeBanner = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Glyphs.Close, contentDescription = tr("Dismiss"), tint = EInk.Caption, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = listState
            ) {
                article.image?.let { url ->
                    item(key = "cover") {
                        AsyncImage(
                            model = url, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)
                        )
                    }
                }
                item(key = "head") {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(
                            article.title,
                            color = EInk.Value,
                            fontSize = 24.sp,
                            fontFamily = readerPrefs.fontFamily,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 31.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 12.dp).clickable(role = androidx.compose.ui.semantics.Role.Button) { actions.openProfile(article.pubkey) }
                        ) {
                            Avatar(author, article.pubkey, size = 36)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    author?.bestName ?: Profile.shortNpub(article.pubkey),
                                    color = EInk.Value,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    fullTimestamp(article.publishedAt) + " · " + tr("{0} min read", readingMinutes),
                                    color = EInk.Caption,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        article.summary?.let {
                            Text(
                                it,
                                color = EInk.Label,
                                fontSize = (readerPrefs.fontSizeSp - 1).sp,
                                fontFamily = readerPrefs.fontFamily,
                                fontStyle = FontStyle.Italic,
                                lineHeight = (readerPrefs.lineHeightSp - 2).sp,
                                modifier = Modifier.padding(top = 12.dp)
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 14.dp).horizontalScroll(rememberScrollState())
                        ) {
                            ArticleAction((if (stats.likedByMe) "♥ " else "♡ ") + stats.likes) { actions.like(article.event) }
                            ArticleAction("⚡ " + stats.zapMsat / 1000) { actions.zap(article.event) }
                            if (speakableBlocks.isNotEmpty() && ttsReady) {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(if (ttsActive) EInk.Accent else EInk.Body)
                                        .border(0.5.dp, if (ttsActive) EInk.Accent else EInk.BodyStroke, RoundedCornerShape(50))
                                        .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                                            if (ttsPlaying) {
                                                ttsRef.value?.stop()
                                                ttsPlaying = false
                                            } else {
                                                speakAtCursor(if (ttsActive) ttsCursor else 0)
                                            }
                                        }
                                        .padding(horizontal = 11.dp, vertical = 5.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Icon(
                                            if (ttsPlaying) Glyphs.Pause else Glyphs.SoundOn,
                                            contentDescription = tr("Listen"),
                                            tint = if (ttsActive) Color.Black else EInk.Accent,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            if (ttsPlaying) tr("Listening…") else tr("Listen"),
                                            color = if (ttsActive) Color.Black else EInk.Value,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            if (headings.size >= 2) {
                                ArticleAction(tr("Contents ({0})", headings.size)) { onOpenToc() }
                            }
                            ArticleAction(tr("Share")) { showShareStudio = true }
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
                }
                items(blocks.size, key = { "b$it" }) { i ->
                    val block = blocks[i]
                    val text = remember(block) { blockText(block) }
                    val isSpoken = currentSayingBlockIdx == i
                    val isFocused = focusedBlockIdx == i
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .then(
                                when {
                                    isSpoken -> Modifier
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                        .background(HighlightTint, RoundedCornerShape(10.dp))
                                        .border(1.dp, EInk.Accent, RoundedCornerShape(10.dp))
                                    isFocused -> Modifier
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                        .background(HighlightTint, RoundedCornerShape(10.dp))
                                        .border(1.5.dp, EInk.Accent, RoundedCornerShape(10.dp))
                                    else -> Modifier
                                }
                            )
                            .then(if (text != null) Modifier.onLongPress { menuFor = i to text } else Modifier)
                    ) { MarkdownBlock(block, actions, navigate, readerPrefs) }
                }

                // ---- Phase 5: Value-for-Value Author Support Card at Article Footer ----
                item(key = "v4v_footer") {
                    AuthorSupportFooterCard(
                        vm = vm,
                        article = article,
                        author = author,
                        isFollowing = article.pubkey in following,
                        actions = actions,
                        navigate = navigate
                    )
                }

                item(key = "end") {
                    Text(
                        tr("Long-press a paragraph to quote it, copy it, or listen from there."),
                        color = EInk.Caption,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                    )
                    Spacer(Modifier.padding(bottom = if (ttsActive) 84.dp else 40.dp))
                }
            }

            // ---- Phase 1: Sticky Audio Player Bar when TTS is active ----
            AnimatedVisibility(
                visible = ttsActive,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                ArticleAudioPlayerBar(
                    isPlaying = ttsPlaying,
                    currentIndex = ttsCursor + 1,
                    totalCount = speakableBlocks.size,
                    speed = ttsSpeed,
                    onPrev = { if (ttsCursor > 0) speakAtCursor(ttsCursor - 1) },
                    onPlayPause = {
                        if (ttsPlaying) {
                            ttsRef.value?.stop()
                            ttsPlaying = false
                        } else {
                            speakAtCursor(ttsCursor)
                        }
                    },
                    onNext = { if (ttsCursor + 1 < speakableBlocks.size) speakAtCursor(ttsCursor + 1) },
                    onToggleSpeed = {
                        val nextSpeed = when (ttsSpeed) {
                            1.0f -> 1.25f
                            1.25f -> 1.5f
                            1.5f -> 2.0f
                            else -> 1.0f
                        }
                        ttsSpeed = nextSpeed
                        if (ttsPlaying) speakAtCursor(ttsCursor)
                    },
                    onStop = {
                        ttsRef.value?.stop()
                        ttsPlaying = false
                        ttsActive = false
                    }
                )
            }
        }
    }

    // Table of Contents Jump Sheet
    if (showToc && headings.isNotEmpty()) {
        ActionSheet(
            onDismiss = onDismissToc,
            actions = headings.map { (blockIdx, level, title) ->
                val indentPrefix = when (level) {
                    1 -> ""
                    2 -> "  · "
                    else -> "      ◦ "
                }
                SheetAction(Glyphs.Hashtag, indentPrefix + title) {
                    scope.launch {
                        listState.animateScrollToItem(headerItemsCount + blockIdx)
                    }
                }
            },
            header = {
                Text(
                    tr("Table of contents"),
                    color = EInk.Value,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
                HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke, modifier = Modifier.padding(bottom = 4.dp))
            }
        )
    }

    menuFor?.let { (blockIdx, text) ->
        val speakIdx = speakableBlocks.indexOfFirst { it.first == blockIdx }
        ActionSheet(onDismiss = { menuFor = null }, actions = buildList {
            add(SheetAction(Glyphs.Share, tr("Share quote to feed")) { quoteToFeedText = text })
            if (speakIdx >= 0 && ttsReady) {
                add(SheetAction(Glyphs.SoundOn, tr("Read aloud from here")) { speakAtCursor(speakIdx) })
            }
            add(SheetAction(Glyphs.Copy, tr("Copy text")) {
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                    .setPrimaryClip(android.content.ClipData.newPlainText("text", text))
            })
        }) {
            Text(
                "“" + text.take(200) + (if (text.length > 200) "…" else "") + "”",
                color = EInk.Label,
                fontSize = 14.sp,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp)
            )
        }
    }

    if (showShareStudio) {
        val studioData = remember(article.address, author, stats) {
            ShareStudioData(
                authorName = author?.bestName ?: Profile.shortNpub(article.pubkey),
                authorHandle = author?.nip05 ?: Profile.shortNpub(article.pubkey),
                contentText = article.summary?.takeIf { it.isNotBlank() } ?: article.content.trim(),
                timestampSeconds = article.publishedAt,
                shareUrl = "https://njump.me/" + article.naddr(),
                blockStamp = BlockStamp.fromEventOrInline(article.event),
                stats = stats,
                articleTitle = article.title
            )
        }
        NoteShareSheet(
            data = studioData,
            onDismiss = { showShareStudio = false }
        )
    }


    quoteToFeedText?.let { quoteText ->
        var userThought by remember { mutableStateOf("") }
        var posting by remember { mutableStateOf(false) }
        BlockDialog(
            onDismiss = { if (!posting) quoteToFeedText = null },
            title = tr("Share quote to feed"),
            text = "“" + quoteText.take(180) + (if (quoteText.length > 180) "…" else "") + "”",
            confirmLabel = if (posting) tr("Posting…") else tr("Post quote"),
            confirmEnabled = !posting,
            onConfirm = {
                if (account == null) {
                    quoteToFeedText = null
                    navigate(LoginKey)
                    return@BlockDialog
                }
                posting = true
                val formattedBody = buildString {
                    if (userThought.isNotBlank()) {
                        append(userThought.trim())
                        append("\n\n")
                    }
                    append("“").append(quoteText.trim()).append("”\n")
                    append("— ").append(article.title).append("\n")
                    append("nostr:").append(article.naddr())
                }
                vm.send(formattedBody, replyTo = null, quote = null) {
                    posting = false
                    quoteToFeedText = null
                }
            }
        ) {
            OutlinedTextField(
                value = userThought,
                onValueChange = { userThought = it.take(500) },
                placeholder = { Text(tr("Add your thoughts (optional)…")) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
