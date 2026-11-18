package com.example.btcwidget.ui.social

import android.content.Context
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.example.btcwidget.ui.main.screenBackground
import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.runtime.NavKey
import coil3.compose.AsyncImage
import com.example.btcwidget.ArticleKey
import com.example.btcwidget.LoginKey
import com.example.btcwidget.NoteKey
import com.example.btcwidget.ProfileKey
import com.example.btcwidget.nostr.NoteStats
import com.example.btcwidget.nostr.core.Article
import com.example.btcwidget.nostr.core.Markdown
import com.example.btcwidget.nostr.core.Nip19
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.ui.main.EInk
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
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
                val title = com.example.btcwidget.nostr.core.Highlights.textOf(h.spans).trim()
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
private fun ReaderSettingsBar(
    prefs: ReaderPrefs,
    onFontSizeChange: (Int) -> Unit,
    onSerifChange: (Boolean) -> Unit,
    onLeadingChange: (Boolean) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(EInk.Body)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ReaderChip(
                    label = tr("Sans"),
                    selected = !prefs.useSerif,
                    fontFamily = FontFamily.SansSerif,
                    onClick = { onSerifChange(false) }
                )
                ReaderChip(
                    label = tr("Serif"),
                    selected = prefs.useSerif,
                    fontFamily = FontFamily.Serif,
                    onClick = { onSerifChange(true) }
                )
                ReaderChip(
                    label = if (prefs.relaxedLeading) tr("Relaxed") else tr("Compact"),
                    selected = prefs.relaxedLeading,
                    fontFamily = FontFamily.SansSerif,
                    onClick = { onLeadingChange(!prefs.relaxedLeading) }
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .screenBackground()
                        .border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(50))
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) { onFontSizeChange(prefs.fontSizeSp - 2) }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("A−", color = EInk.Value, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    "${prefs.fontSizeSp}",
                    color = EInk.Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .screenBackground()
                        .border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(50))
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) { onFontSizeChange(prefs.fontSizeSp + 2) }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("A+", color = EInk.Value, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
}

@Composable
private fun ReaderChip(
    label: String,
    selected: Boolean,
    fontFamily: FontFamily,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) EInk.Accent else EInk.Background)
            .border(0.5.dp, if (selected) EInk.Accent else EInk.BodyStroke, RoundedCornerShape(50))
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else EInk.Value,
            fontSize = 12.sp,
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold
        )
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

    // ---- Phase 4: Interactive Highlights & Quote-Post to Feed ----
    var reload by rememberSaveable { mutableIntStateOf(0) }
    val highlights by produceKept(emptyList<com.example.btcwidget.nostr.core.Highlights.Highlight>(), article.event.id, reload) {
        value = vm.highlightsOf(article)
    }
    var menuFor by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var commentFor by remember { mutableStateOf<String?>(null) }
    var quoteToFeedText by remember { mutableStateOf<String?>(null) }
    var focusedBlockIdx by remember { mutableIntStateOf(-1) }
    var showShareStudio by remember { mutableStateOf(false) }

    LaunchedEffect(focusedBlockIdx) {
        if (focusedBlockIdx >= 0) {
            delay(2600)
            focusedBlockIdx = -1
        }
    }

    fun highlightNow(text: String, comment: String?) {
        if (account == null) { navigate(LoginKey); return }
        vm.highlight(article, text, comment) { reload++ }
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
                    val marked = text != null && highlights.any { h -> text.contains(h.text) || (h.text.length > 20 && h.text.contains(text)) }
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
                                    marked -> Modifier
                                        .padding(horizontal = 10.dp)
                                        .background(HighlightTint, RoundedCornerShape(8.dp))
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

                // ---- Phase 4: Interactive Highlights Section (Tap to Jump & Quote-Post) ----
                if (highlights.isNotEmpty()) item(key = "highlights") {
                    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke, modifier = Modifier.padding(top = 8.dp))
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(tr("Highlights ({0})", highlights.size), color = EInk.Value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(tr("Tap to jump"), color = EInk.Caption, fontSize = 12.sp)
                    }
                    highlights.take(30).forEach { h ->
                        val matchingBlockIdx = remember(h.text, blocks) {
                            blocks.indexOfFirst { b ->
                                val bt = blockText(b) ?: return@indexOfFirst false
                                bt.contains(h.text) || (h.text.length > 20 && h.text.contains(bt))
                            }
                        }
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 5.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(EInk.Body)
                                .border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(12.dp))
                                .clickable(role = androidx.compose.ui.semantics.Role.Button, enabled = matchingBlockIdx >= 0) {
                                    if (matchingBlockIdx >= 0) {
                                        focusedBlockIdx = matchingBlockIdx
                                        scope.launch {
                                            listState.animateScrollToItem(headerItemsCount + matchingBlockIdx)
                                        }
                                    }
                                }
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(profiles[h.event.pubkey], h.event.pubkey, size = 24, onClick = { actions.openProfile(h.event.pubkey) })
                                Text(
                                    profiles[h.event.pubkey]?.bestName ?: Profile.shortNpub(h.event.pubkey),
                                    color = EInk.Value,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                                )
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(50))
                                        .screenBackground()
                                        .border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(50))
                                        .clickable(role = androidx.compose.ui.semantics.Role.Button) { quoteToFeedText = h.text }
                                        .padding(horizontal = 9.dp, vertical = 3.dp)
                                ) {
                                    Text(tr("Share quote"), color = EInk.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Row(Modifier.padding(top = 8.dp)) {
                                Spacer(Modifier.width(3.dp).heightIn(min = 18.dp).background(HighlightOrange))
                                Text(
                                    h.text,
                                    color = EInk.Label,
                                    fontSize = 14.sp,
                                    fontStyle = FontStyle.Italic,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                            h.comment?.let {
                                Text(it, color = EInk.Value, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                }
                item(key = "end") {
                    Text(
                        tr("Long-press a paragraph to highlight, quote, or listen from there."),
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
            add(SheetAction(Glyphs.Quote, tr("Highlight")) { highlightNow(text, null) })
            add(SheetAction(Glyphs.Comment, tr("Highlight with a comment")) { commentFor = text })
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

    commentFor?.let { text ->
        var comment by remember { mutableStateOf("") }
        BlockDialog(
            onDismiss = { commentFor = null }, title = tr("Highlight with a comment"),
            text = "“" + text.take(160) + (if (text.length > 160) "…" else "") + "”",
            confirmLabel = tr("Highlight"), confirmEnabled = comment.isNotBlank(),
            onConfirm = { commentFor = null; highlightNow(text, comment) }
        ) {
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it.take(500) },
                placeholder = { Text(tr("Your comment")) },
                modifier = Modifier.fillMaxWidth()
            )
        }
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

/** Sticky bottom audio player bar for Text-to-Speech article listening. */
@Composable
private fun ArticleAudioPlayerBar(
    isPlaying: Boolean,
    currentIndex: Int,
    totalCount: Int,
    speed: Float,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onToggleSpeed: () -> Unit,
    onStop: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(EInk.Body)
            .navigationBarsPadding()
    ) {
        HorizontalDivider(thickness = 0.5.dp, color = EInk.Accent.copy(alpha = 0.5f))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Glyphs.SoundOn, contentDescription = null, tint = EInk.Accent, modifier = Modifier.size(18.dp))
                Text(
                    tr("Paragraph {0}/{1}", currentIndex, totalCount.coerceAtLeast(1)),
                    color = EInk.Value,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .screenBackground()
                        .border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(50))
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onPrev)
                        .semantics { contentDescription = tr("Previous paragraph") }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("⏮", color = EInk.Value, fontSize = 12.sp)
                }
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(EInk.Accent)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onPlayPause),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPlaying) Glyphs.Pause else Glyphs.Play,
                        contentDescription = if (isPlaying) tr("Pause") else tr("Play"),
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .screenBackground()
                        .border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(50))
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onNext)
                        .semantics { contentDescription = tr("Next paragraph") }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("⏭", color = EInk.Value, fontSize = 12.sp)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .screenBackground()
                        .border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(50))
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onToggleSpeed)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        "${speed}x",
                        color = EInk.Accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .screenBackground()
                        .border(0.5.dp, EInk.BodyStroke, CircleShape)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onStop),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Glyphs.Close, contentDescription = tr("Stop"), tint = EInk.Caption, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

/** Phase 5: Value-for-Value Quick Zap & Author Support Card at Article Footer. */
@Composable
private fun AuthorSupportFooterCard(
    vm: SocialViewModel,
    article: Article,
    author: Profile?,
    isFollowing: Boolean,
    actions: NoteActions,
    navigate: (NavKey) -> Unit
) {
    val context = LocalContext.current
    val account by vm.account.collectAsState()
    val presets = listOf(21L, 100L, 500L, 1000L)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 18.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(EInk.Body)
            .border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(author, article.pubkey, size = 44, onClick = { actions.openProfile(article.pubkey) })
            Column(Modifier.weight(1f).padding(start = 12.dp).clickable(role = androidx.compose.ui.semantics.Role.Button) { actions.openProfile(article.pubkey) }) {
                Text(
                    tr("Written by"),
                    color = EInk.Caption,
                    fontSize = 11.sp
                )
                Text(
                    author?.bestName ?: Profile.shortNpub(article.pubkey),
                    color = EInk.Value,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (account?.pubkey != article.pubkey) {
                ChoicePill(
                    label = if (isFollowing) tr("Following") else tr("Follow"),
                    selected = !isFollowing
                ) {
                    if (account == null) navigate(LoginKey)
                    else actions.follow(article.pubkey, !isFollowing)
                }
            }
        }
        author?.about?.takeIf { it.isNotBlank() }?.let { bio ->
            Text(
                bio,
                color = EInk.Label,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke, modifier = Modifier.padding(vertical = 12.dp))
        Text(
            tr("Enjoyed this article? Value-for-Value zap the author:"),
            color = EInk.Value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { sats ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(EInk.Accent.copy(alpha = 0.14f))
                        .border(1.dp, EInk.Accent, RoundedCornerShape(50))
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                            if (account == null) {
                                navigate(LoginKey)
                            } else {
                                vm.zap(
                                    recipient = article.pubkey,
                                    amountSat = sats,
                                    note = article.event,
                                    comment = "⚡ ${article.title}",
                                    address = author?.lud16
                                ) { invoice ->
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("lightning:$invoice"))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    }
                                }
                            }
                        }
                        .padding(horizontal = 13.dp, vertical = 7.dp)
                ) {
                    Text(
                        "⚡ $sats",
                        color = EInk.Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .screenBackground()
                    .border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(50))
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { actions.zap(article.event) }
                    .padding(horizontal = 13.dp, vertical = 7.dp)
            ) {
                Text(
                    tr("Custom ⚡"),
                    color = EInk.Value,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Phase 3: Full-screen In-App Markdown Article Composer & Drafts (`kind: 30023`).
 * Supports Write / Preview modes, Cover Image upload, Markdown formatting toolbar, and auto-saved local drafts.
 */
@Composable
internal fun ArticleComposerDialog(
    vm: SocialViewModel,
    initialArticle: Article? = null,
    onDismiss: () -> Unit,
    onPublished: (Article) -> Unit = {}
) {
    val context = LocalContext.current
    val account by vm.account.collectAsState()
    // The article being written is kept with the account's other drafts: per account, out of
    // Android backup, and deleted on logout.
    val draftKey = remember(account?.pubkey) { "${account?.pubkey ?: ""}/article" }

    val initialDraftJson = remember(initialArticle, draftKey) {
        if (initialArticle != null) null
        else {
            // A draft from before drafts were per account: it goes to whoever opens the composer next.
            val legacy = context.getSharedPreferences("article_draft_prefs", Context.MODE_PRIVATE)
            legacy.getString("current_draft", null)?.let { old ->
                if (com.example.btcwidget.nostr.Drafts.get(context, draftKey) == null) com.example.btcwidget.nostr.Drafts.put(context, draftKey, old)
                legacy.edit().clear().apply()
            }
            com.example.btcwidget.nostr.Drafts.get(context, draftKey)?.let { runCatching { JSONObject(it) }.getOrNull() }
        }
    }

    var title by rememberSaveable {
        mutableStateOf(initialArticle?.title ?: initialDraftJson?.optString("title").orEmpty())
    }
    var summary by rememberSaveable {
        mutableStateOf(initialArticle?.summary ?: initialDraftJson?.optString("summary").orEmpty())
    }
    var coverImage by rememberSaveable {
        mutableStateOf(initialArticle?.image ?: initialDraftJson?.optString("image").orEmpty())
    }
    var tagsInput by rememberSaveable {
        mutableStateOf(
            initialArticle?.event?.tags
                ?.filter { it.size >= 2 && it[0] == "t" }
                ?.joinToString(", ") { it[1] }
                ?: initialDraftJson?.optString("tags").orEmpty()
        )
    }
    var content by rememberSaveable {
        mutableStateOf(initialArticle?.content ?: initialDraftJson?.optString("content").orEmpty())
    }
    var previewMode by rememberSaveable { mutableStateOf(false) }
    var uploadingCover by remember { mutableStateOf(false) }
    var uploadingInline by remember { mutableStateOf(false) }
    var publishing by remember { mutableStateOf(false) }

    val coverPicker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) {
            uploadingCover = true
            vm.uploadImage(uri) { att ->
                uploadingCover = false
                if (att != null) coverImage = att.url
            }
        }
    }

    val inlineImagePicker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) {
            uploadingInline = true
            vm.uploadImage(uri) { att ->
                uploadingInline = false
                if (att != null) {
                    val snippet = "\n![](${att.url})\n"
                    content = (content + snippet).trimStart('\n')
                }
            }
        }
    }

    fun saveDraft(showToast: Boolean) {
        if (initialArticle != null) return
        val obj = JSONObject()
            .put("title", title)
            .put("summary", summary)
            .put("image", coverImage)
            .put("tags", tagsInput)
            .put("content", content)
        com.example.btcwidget.nostr.Drafts.put(context, draftKey, obj.toString())
        if (showToast) vm.say(tr("Draft saved"))
    }

    Dialog(
        onDismissRequest = {
            if (!publishing) {
                if (title.isNotBlank() || content.isNotBlank()) saveDraft(showToast = false)
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = EInk.Background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                // Top Bar
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable(role = androidx.compose.ui.semantics.Role.Button, enabled = !publishing) {
                                if (title.isNotBlank() || content.isNotBlank()) saveDraft(showToast = false)
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Glyphs.Close, contentDescription = tr("Close"), tint = EInk.Value, modifier = Modifier.size(22.dp))
                    }
                    Text(
                        if (initialArticle != null) tr("Edit article") else tr("Write article"),
                        color = EInk.Value,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    if (initialArticle == null) {
                        Box(
                            Modifier
                                .padding(end = 8.dp)
                                .clip(RoundedCornerShape(50))
                                .background(EInk.Body)
                                .border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(50))
                                .clickable(role = androidx.compose.ui.semantics.Role.Button) { saveDraft(showToast = true) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(tr("Save draft"), color = EInk.Value, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    val canPublish = !publishing && title.isNotBlank() && content.isNotBlank() && account != null
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (canPublish) EInk.Accent else EInk.Body)
                            .clickable(role = androidx.compose.ui.semantics.Role.Button, enabled = canPublish) {
                                val me = account?.pubkey ?: return@clickable
                                publishing = true
                                val hashtags = tagsInput.split(',', ' ')
                                    .map { it.trim().removePrefix("#") }
                                    .filter { it.isNotBlank() }
                                val unsigned = Article.create(
                                    pubkey = me,
                                    title = title,
                                    content = content,
                                    summary = summary.ifBlank { null },
                                    image = coverImage.ifBlank { null },
                                    tags = hashtags,
                                    identifier = initialArticle?.identifier
                                )
                                vm.publishArticle(unsigned) { published ->
                                    publishing = false
                                    if (published != null) {
                                        if (initialArticle == null) {
                                            com.example.btcwidget.nostr.Drafts.remove(context, draftKey)
                                        }
                                        onPublished(published)
                                    }
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            if (publishing) tr("Publishing…") else tr("Publish"),
                            color = if (canPublish) Color.Black else EInk.Caption,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)

                // Write / Preview Mode Toggle + Markdown Formatting Toolbar
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(EInk.Body)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ReaderChip(
                            label = tr("Write"),
                            selected = !previewMode,
                            fontFamily = FontFamily.SansSerif,
                            onClick = { previewMode = false }
                        )
                        ReaderChip(
                            label = tr("Preview"),
                            selected = previewMode,
                            fontFamily = FontFamily.SansSerif,
                            onClick = { previewMode = true }
                        )
                    }
                    if (!previewMode) {
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            MarkdownToolButton("H1") { content += (if (content.endsWith("\n") || content.isEmpty()) "" else "\n") + "# " }
                            MarkdownToolButton("H2") { content += (if (content.endsWith("\n") || content.isEmpty()) "" else "\n") + "## " }
                            MarkdownToolButton("B") { content += "**bold**" }
                            MarkdownToolButton("I") { content += "*italic*" }
                            MarkdownToolButton("“”") { content += (if (content.endsWith("\n") || content.isEmpty()) "" else "\n") + "> " }
                            MarkdownToolButton("<>") { content += "\n```\ncode\n```\n" }
                            MarkdownToolButton("🔗") { content += "[title](https://)" }
                            MarkdownToolButton(if (uploadingInline) "…" else "🖼️") {
                                if (!uploadingInline) {
                                    inlineImagePicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)

                if (previewMode) {
                    val previewBlocks = remember(content) { Markdown.parse(content) }
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 12.dp)
                    ) {
                        if (coverImage.isNotBlank()) {
                            AsyncImage(
                                model = coverImage,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp)
                            )
                        }
                        Text(
                            title.ifBlank { tr("Untitled article") },
                            color = EInk.Value,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                        if (summary.isNotBlank()) {
                            Text(
                                summary,
                                color = EInk.Label,
                                fontSize = 15.sp,
                                fontStyle = FontStyle.Italic,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke, modifier = Modifier.padding(vertical = 8.dp))
                        previewBlocks.forEach { b ->
                            MarkdownBlock(b, actions = null, navigate = {})
                        }
                    }
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it.take(160) },
                            label = { Text(tr("Article title")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = summary,
                            onValueChange = { summary = it.take(320) },
                            label = { Text(tr("Short summary / subtitle (optional)")) },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = coverImage,
                                onValueChange = { coverImage = it.trim() },
                                label = { Text(tr("Cover image URL (optional)")) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(EInk.Body)
                                    .border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(12.dp))
                                    .clickable(role = androidx.compose.ui.semantics.Role.Button, enabled = !uploadingCover) {
                                        coverPicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                                    }
                                    .padding(horizontal = 12.dp, vertical = 14.dp)
                            ) {
                                Text(
                                    if (uploadingCover) tr("Uploading…") else tr("Upload"),
                                    color = EInk.Accent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        OutlinedTextField(
                            value = tagsInput,
                            onValueChange = { tagsInput = it.take(160) },
                            label = { Text(tr("Topics (comma-separated, e.g. bitcoin, nostr)")) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = content,
                            onValueChange = { content = it },
                            label = { Text(tr("Article body (Markdown supported)")) },
                            placeholder = { Text(tr("# Heading\n\nWrite your story in Markdown…")) },
                            minLines = 14,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownToolButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .screenBackground()
            .border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(8.dp))
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = EInk.Value, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

private val HighlightOrange get() = EInk.Accent
private val HighlightTint = Color(0x26F7931A)

/** A block's plain text, for highlighting (null for images, code and rules). */
private fun blockText(block: Markdown.Block): String? = when (block) {
    is Markdown.Block.Paragraph -> com.example.btcwidget.nostr.core.Highlights.textOf(block.spans)
    is Markdown.Block.ListItem -> com.example.btcwidget.nostr.core.Highlights.textOf(block.spans)
    is Markdown.Block.Quote -> com.example.btcwidget.nostr.core.Highlights.textOf(block.spans)
    is Markdown.Block.Heading -> com.example.btcwidget.nostr.core.Highlights.textOf(block.spans)
    else -> null
}?.takeIf { it.isNotBlank() }

/**
 * Calls [onLongPress] only when the finger is held stationary for the full long-press timeout,
 * without interfering with normal taps (so links inside [Text] work) and without misfiring when
 * the user scrolls or flings the [LazyColumn].
 */
private fun Modifier.onLongPress(onLongPress: () -> Unit): Modifier = composed {
    val haptics = rememberHaptics()
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val touchSlop = viewConfiguration.touchSlop
            var timedOut = true
            val upOrCancel = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                var result: PointerInputChange? = null
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        result = change
                        break
                    }
                    if (change.isConsumed || (change.position - down.position).getDistance() > touchSlop) {
                        break
                    }
                    val finalEvent = awaitPointerEvent(PointerEventPass.Final)
                    val finalChange = finalEvent.changes.firstOrNull { it.id == down.id } ?: break
                    if (!finalChange.pressed) {
                        result = finalChange
                        break
                    }
                    if (finalChange.isConsumed || (finalChange.position - down.position).getDistance() > touchSlop) {
                        break
                    }
                }
                timedOut = false
                result
            }
            if (timedOut && upOrCancel == null) {
                haptics.tick()
                currentOnLongPress()
                // Swallow the rest of this press so letting go doesn't also tap a link.
                do {
                    val event = awaitPointerEvent()
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            }
        }
    }
}

@Composable
private fun ArticleAction(label: String, onClick: () -> Unit) {
    Text(label, color = EInk.Value, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick).padding(vertical = 4.dp))
}

@Composable
internal fun MarkdownBlock(
    block: Markdown.Block,
    actions: NoteActions? = null,
    navigate: (NavKey) -> Unit = {},
    prefs: ReaderPrefs = ReaderPrefs()
) {
    val pad = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
    val bodySize = prefs.fontSizeSp.sp
    val bodyLeading = prefs.lineHeightSp.sp
    val family = prefs.fontFamily
    when (block) {
        is Markdown.Block.Heading -> Text(
            spans(block.spans, actions, navigate), color = EInk.Value, fontWeight = FontWeight.Bold,
            fontFamily = family,
            fontSize = when (block.level) {
                1 -> (prefs.fontSizeSp + 6).sp
                2 -> (prefs.fontSizeSp + 3).sp
                else -> (prefs.fontSizeSp + 1).sp
            },
            lineHeight = when (block.level) {
                1 -> (prefs.lineHeightSp + 6).sp
                2 -> (prefs.lineHeightSp + 3).sp
                else -> (prefs.lineHeightSp + 1).sp
            },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
        )
        is Markdown.Block.Paragraph -> Text(
            spans(block.spans, actions, navigate),
            color = EInk.Value,
            fontSize = bodySize,
            fontFamily = family,
            lineHeight = bodyLeading,
            modifier = pad
        )
        is Markdown.Block.ListItem -> Row(pad) {
            Text(block.marker, color = EInk.Accent, fontSize = bodySize, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
            Text(spans(block.spans, actions, navigate), color = EInk.Value, fontSize = bodySize, fontFamily = family, lineHeight = bodyLeading)
        }
        is Markdown.Block.Quote -> Row(pad) {
            Spacer(Modifier.width(3.dp).heightIn(min = 20.dp).background(EInk.Accent))
            Text(
                spans(block.spans, actions, navigate),
                color = EInk.Label,
                fontSize = bodySize,
                fontFamily = family,
                fontStyle = FontStyle.Italic,
                lineHeight = bodyLeading,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        is Markdown.Block.Code -> Text(
            block.text, color = EInk.Value, fontSize = (prefs.fontSizeSp - 3).coerceAtLeast(12).sp, fontFamily = FontFamily.Monospace,
            modifier = pad.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(EInk.Body).border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(8.dp)).padding(10.dp)
        )
        is Markdown.Block.Image -> if (block.url.startsWith("https://")) {
            AsyncImage(
                model = block.url, contentDescription = block.alt.ifBlank { null }, contentScale = ContentScale.FillWidth,
                modifier = pad.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            )
        }
        Markdown.Block.Rule -> HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
    }
}

/** Inline Markdown as styled text; nostr: links open in the app, web links in the browser. */
@Composable
private fun spans(spans: List<Markdown.Span>, actions: NoteActions?, navigate: (NavKey) -> Unit): AnnotatedString {
    val uriHandler = LocalUriHandler.current
    val linkStyle = TextLinkStyles(SpanStyle(color = EInk.Value, fontWeight = FontWeight.Bold))
    return buildAnnotatedString {
        spans.forEach { s ->
            when (s) {
                is Markdown.Span.Plain -> append(s.text)
                is Markdown.Span.Bold -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s.text) }
                is Markdown.Span.Italic -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(s.text) }
                is Markdown.Span.Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = EInk.Body)) { append(s.text) }
                is Markdown.Span.Link -> withLink(LinkAnnotation.Clickable("link", linkStyle) { openLink(s.url, actions, navigate) { actions?.openLink?.invoke(it, null) ?: runCatching { uriHandler.openUri(it) } } }) {
                    append(s.text)
                }
            }
        }
    }
}

private fun openLink(url: String, actions: NoteActions?, navigate: (NavKey) -> Unit, openWeb: (String) -> Unit) {
    when (val e = if (url.startsWith("nostr:")) Nip19.decode(url) else null) {
        is Nip19.Pubkey -> navigate(ProfileKey(e.hex))
        is Nip19.Note -> navigate(NoteKey(e.id))
        is Nip19.Address -> actions?.openArticle?.invoke(e)
        else -> if (url.startsWith("https://") || url.startsWith("http://")) runCatching { openWeb(url) }
    }
}

/** Shares a link to the article (njump.me opens it in any browser or Nostr app). */
private fun shareArticle(context: Context, article: Article) {
    val link = "https://njump.me/" + article.naddr()
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, article.title + "\n" + link)
    context.startActivity(Intent.createChooser(send, "Share article").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** An article in a list: cover, title, summary, date,reading time, and saved reading progress badge. */
@Composable
internal fun ArticleRow(article: Article, onClick: () -> Unit) {
    val context = LocalContext.current
    val savedPct = remember(article.address) { ArticleReadingStore.savedPercent(context, article.address) }
    val readingMinutes = remember(article.event.id) {
        val words = article.content.split(Regex("\\s+")).count { it.isNotBlank() }
        maxOf(1, (words + 120) / 200)
    }
    Row(Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(article.title, color = EInk.Value, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            article.summary?.let { Text(it, color = EInk.Label, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 6.dp)
            ) {
                Text(
                    relativeTime(article.publishedAt) + " · " + tr("{0} min read", readingMinutes),
                    color = EInk.Caption,
                    fontSize = 12.sp
                )
                if (savedPct > 0) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(EInk.Accent.copy(alpha = 0.14f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            if (savedPct >= 95) tr("✓ Read") else tr("{0}% read", savedPct),
                            color = EInk.Accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        article.image?.let { url ->
            Spacer(Modifier.width(10.dp))
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.width(72.dp).heightIn(min = 72.dp, max = 72.dp).clip(RoundedCornerShape(8.dp)))
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
}
