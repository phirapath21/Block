package com.example.btcwidget.ui.social

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import com.example.btcwidget.ProfileKey
import com.example.btcwidget.nostr.core.Nip17
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.ui.main.EInk
import com.example.btcwidget.ui.main.screenBackground
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Gap in seconds before showing a new centered Messenger-style timestamp divider (15 minutes). */
private const val TIME_GROUP_GAP_SEC = 15 * 60L

/** Emoji offered when reacting to a message. */
private val DM_REACTIONS = listOf("❤️", "😂", "👍", "🔥", "😮", "😢", "🙏")

/**
 * Formats a centered Messenger-style section timestamp (e.g. "TODAY AT 3:33 PM",
 * "YESTERDAY AT 4:15 PM", "FRI AT 3:33 PM", or "NOV 14 AT 3:33 PM").
 */
private fun formatChatSectionTime(epochSec: Long): String {
    val locale = Locale.getDefault()
    val date = Date(epochSec * 1000L)
    val nowCal = Calendar.getInstance()
    val msgCal = Calendar.getInstance().apply { time = date }
    val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val timeStr = SimpleDateFormat("h:mm a", locale).format(date)

    val sameDay = nowCal.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
        nowCal.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)
    val isYesterday = yesterdayCal.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
        yesterdayCal.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)
    val diffSec = (System.currentTimeMillis() / 1000L) - epochSec

    return when {
        sameDay -> tr("TODAY AT {0}", timeStr)
        isYesterday -> tr("YESTERDAY AT {0}", timeStr)
        diffSec in 0..(6 * 86_400L) -> {
            val dayStr = SimpleDateFormat("EEE", locale).format(date).uppercase(locale)
            tr("{0} AT {1}", dayStr, timeStr)
        }
        nowCal.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) -> {
            val dateStr = SimpleDateFormat("MMM d", locale).format(date).uppercase(locale)
            tr("{0} AT {1}", dateStr, timeStr)
        }
        else -> {
            val dateStr = SimpleDateFormat("MMM d, yyyy", locale).format(date).uppercase(locale)
            tr("{0} AT {1}", dateStr, timeStr)
        }
    }
}

/**
 * Formats the exact timestamp revealed when the user taps an individual speech bubble.
 */
private fun formatBubbleDetailTime(epochSec: Long): String {
    val locale = Locale.getDefault()
    val date = Date(epochSec * 1000L)
    val nowCal = Calendar.getInstance()
    val msgCal = Calendar.getInstance().apply { time = date }
    val sameDay = nowCal.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
        nowCal.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)
    return if (sameDay) {
        SimpleDateFormat("h:mm a", locale).format(date)
    } else if (nowCal.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR)) {
        SimpleDateFormat("EEE, MMM d · h:mm a", locale).format(date)
    } else {
        SimpleDateFormat("MMM d, yyyy · h:mm a", locale).format(date)
    }
}

/** A conversation with [peers]: bubbles, newest at the bottom, and a modern composer. */
@Composable
fun ChatScreen(vm: SocialViewModel, peers: List<String>, navigate: (NavKey) -> Unit, onBack: () -> Unit) {
    val account by vm.account.collectAsState()
    val all by vm.dmMessages.collectAsState()
    val unsentIds by vm.dmUnsentIds.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val me = account?.pubkey
    val key = remember(peers) { peers.sorted() }
    val inChat = remember(all, me, key) { if (me == null) emptyList() else all.filter { it.peers(me) == key } }
    val messages = remember(inChat) { inChat.filter { it.isBubble } }
    // Reactions grouped under the message they react to (each person's latest counts).
    val reactions = remember(inChat) {
        inChat.filter { it.kind == Nip17.REACTION && it.replyTo != null }
            .groupBy { it.replyTo!! }
            .mapValues { (_, rs) -> rs.groupBy { it.sender }.map { (_, mine) -> mine.maxBy { it.createdAt }.content } }
    }
    val byId = remember(messages) { messages.associateBy { it.id } }
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val sendHaptics = rememberHaptics()
    var replyTo by remember { mutableStateOf<Nip17.Message?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var selectedMessageId by remember { mutableStateOf<String?>(null) }
    var showAttachSheet by remember { mutableStateOf(false) }
    var mediaSending by remember { mutableStateOf(false) }
    var pendingSaveBytes by remember { mutableStateOf<ByteArray?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            mediaSending = true
            val parentId = replyTo?.id
            replyTo = null
            vm.sendDmImage(peers, uri, parentId) { mediaSending = false }
        }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            mediaSending = true
            val parentId = replyTo?.id
            replyTo = null
            vm.sendDmVideo(peers, uri, parentId) { mediaSending = false }
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            mediaSending = true
            val parentId = replyTo?.id
            replyTo = null
            vm.sendDmFile(peers, uri, parentId) { mediaSending = false }
        }
    }
    val saveFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { destUri ->
        val bytes = pendingSaveBytes
        pendingSaveBytes = null
        if (destUri != null && bytes != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(destUri)?.use { it.write(bytes) }
                }.onSuccess {
                    vm.say(tr("Saved to device"))
                }.onFailure {
                    vm.say(tr("Couldn't save the file"))
                }
            }
        }
    }

    fun requestSaveFile(bytes: ByteArray, defaultName: String) {
        pendingSaveBytes = bytes
        saveFileLauncher.launch(defaultName)
    }

    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        vm.startDms()
        vm.loadProfiles(peers)
    }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size)
            // Open on screen: everything here is read.
            vm.markConversationRead(key.joinToString(","), messages.maxOf { it.createdAt })
        }
    }

    val isGroup = peers.size > 1
    val primaryPeer = peers.first()
    val primaryProfile = profiles[primaryPeer]
    val title = peers.joinToString(", ") { profiles[it]?.bestName ?: Profile.shortNpub(it) }

    Column(Modifier.fillMaxSize().screenBackground().imePadding()) {
        // Polished Chat Top Bar with avatar, verified badge & encryption status
        ChatTopBar(
            title = title,
            peers = peers,
            profiles = profiles,
            onBack = onBack,
            onOpenProfile = { navigate(ProfileKey(primaryPeer)) }
        )

        SocialScaffold(vm, navigate) { _ ->
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)
                    ) {
                        item(key = "head") {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 20.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { navigate(ProfileKey(primaryPeer)) }
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Avatar(primaryProfile, primaryPeer, size = 60)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 10.dp)
                                ) {
                                    Text(
                                        title,
                                        color = EInk.Value,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (!isGroup && isVerified(primaryProfile, primaryPeer)) {
                                        VerifiedBadge(16.dp, Modifier.padding(start = 4.dp))
                                    }
                                }
                                if (!isGroup) {
                                    Text(
                                        handleText(primaryProfile, primaryPeer),
                                        color = EInk.Caption,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(EInk.Body)
                                        .border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(50))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Glyphs.Lock, contentDescription = null, tint = EInk.Accent, modifier = Modifier.size(13.dp))
                                    Text(
                                        tr("End-to-end encrypted (NIP-17)"),
                                        color = EInk.Label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(start = 5.dp)
                                    )
                                }
                                if (messages.any { it.isLegacy }) {
                                    Text(
                                        tr("Older messages marked \"old format\" use NIP-04: relays could see who talked and when."),
                                        color = EInk.Caption,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(top = 6.dp, start = 20.dp, end = 20.dp)
                                    )
                                }
                            }
                        }

                        itemsIndexed(messages, key = { _, m -> m.id }) { index, m ->
                            val mine = m.sender == me
                            val isUnsent = m.id in unsentIds
                            val fg = if (mine) Color.Black else EInk.Value
                            val prev = messages.getOrNull(index - 1)
                            val next = messages.getOrNull(index + 1)

                            val showTimeHeader = prev == null || (m.createdAt - prev.createdAt) >= TIME_GROUP_GAP_SEC
                            val nextStartsNewTimeGroup = next == null || (next.createdAt - m.createdAt) >= TIME_GROUP_GAP_SEC
                            val isFirstInCluster = showTimeHeader || prev.sender != m.sender
                            val isLastInCluster = nextStartsNewTimeGroup || next.sender != m.sender

                            // Messenger-style dynamic bubble corners: outer corners are 18.dp,
                            // inner corners where consecutive bubbles in a cluster meet are 4.dp.
                            val bubbleShape = if (mine) {
                                RoundedCornerShape(
                                    topStart = 18.dp,
                                    bottomStart = 18.dp,
                                    topEnd = if (isFirstInCluster) 18.dp else 4.dp,
                                    bottomEnd = if (isLastInCluster) 18.dp else 4.dp
                                )
                            } else {
                                RoundedCornerShape(
                                    topEnd = 18.dp,
                                    bottomEnd = 18.dp,
                                    topStart = if (isFirstInCluster) 18.dp else 4.dp,
                                    bottomStart = if (isLastInCluster) 18.dp else 4.dp
                                )
                            }

                            val isTimeExpanded = selectedMessageId == m.id
                            val senderName = profiles[m.sender]?.bestName ?: Profile.shortNpub(m.sender)

                            Column(Modifier.fillMaxWidth()) {
                                // Centered Messenger-style time section header
                                if (showTimeHeader) {
                                    Text(
                                        text = formatChatSectionTime(m.createdAt),
                                        color = EInk.Caption,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 14.dp, bottom = 8.dp)
                                    )
                                }

                                // Swipe right to reply, as on Messenger / X (disabled on unsent pills).
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .then(if (!isUnsent) Modifier.swipeToReply { replyTo = m } else Modifier)
                                        .padding(
                                            top = if (isFirstInCluster && !showTimeHeader) 8.dp else 1.dp,
                                            bottom = if (isLastInCluster) 3.dp else 1.dp
                                        ),
                                    horizontalAlignment = if (mine) Alignment.End else Alignment.Start
                                ) {
                                    // Group sender name above the first incoming bubble in a cluster
                                    if (!mine && isGroup && isFirstInCluster) {
                                        Text(
                                            text = senderName,
                                            color = EInk.Caption,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(start = 40.dp, bottom = 3.dp)
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.Bottom) {
                                        if (!mine) {
                                            if (isLastInCluster) {
                                                Avatar(
                                                    profiles[m.sender],
                                                    m.sender,
                                                    size = 28,
                                                    onClick = { navigate(ProfileKey(m.sender)) }
                                                )
                                            } else {
                                                Spacer(Modifier.width(28.dp))
                                            }
                                            Spacer(Modifier.width(8.dp))
                                        }
                                        Box {
                                            if (isUnsent) {
                                                // Messenger-style Unsent pill
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier
                                                        .clip(bubbleShape)
                                                        .background(EInk.Background, bubbleShape)
                                                        .border(0.5.dp, EInk.BodyStroke, bubbleShape)
                                                        .combinedClickable(
                                                            onClickLabel = tr("Show message time"),
                                                            onClick = {
                                                                selectedMessageId = if (selectedMessageId == m.id) null else m.id
                                                            },
                                                            onLongClickLabel = tr("Message options"),
                                                            onLongClick = { menuFor = m.id }
                                                        )
                                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                                ) {
                                                    Icon(
                                                        Glyphs.Undo,
                                                        contentDescription = null,
                                                        tint = EInk.Caption,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Text(
                                                        text = if (mine) tr("You unsent a message") else tr("{0} unsent a message", senderName),
                                                        color = EInk.Caption,
                                                        fontSize = 13.sp,
                                                        fontStyle = FontStyle.Italic,
                                                        modifier = Modifier.padding(start = 6.dp)
                                                    )
                                                }
                                            } else {
                                                val file = m.file
                                                val isVisualAttachment = file != null && (file.isImage || file.isVideo)
                                                Column(
                                                    Modifier
                                                        .widthIn(max = 284.dp)
                                                        .clip(bubbleShape)
                                                        .background(if (mine) EInk.Accent else EInk.Body, bubbleShape)
                                                        .then(
                                                            if (!mine) Modifier.border(0.5.dp, EInk.BodyStroke, bubbleShape)
                                                            else Modifier
                                                        )
                                                        .combinedClickable(
                                                            onClickLabel = tr("Show message time"),
                                                            onClick = {
                                                                selectedMessageId = if (selectedMessageId == m.id) null else m.id
                                                            },
                                                            onLongClickLabel = tr("Message options"),
                                                            onLongClick = { menuFor = m.id }
                                                        )
                                                        .padding(
                                                            horizontal = if (isVisualAttachment) 4.dp else 14.dp,
                                                            vertical = if (isVisualAttachment) 4.dp else 9.dp
                                                        )
                                                ) {
                                                    m.replyTo?.let { byId[it] }?.let { parent ->
                                                        val parentUnsent = parent.id in unsentIds
                                                        Row(
                                                            Modifier
                                                                .padding(
                                                                    bottom = 6.dp,
                                                                    start = if (isVisualAttachment) 8.dp else 0.dp,
                                                                    end = if (isVisualAttachment) 8.dp else 0.dp,
                                                                    top = if (isVisualAttachment) 4.dp else 0.dp
                                                                )
                                                                .height(IntrinsicSize.Min)
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(
                                                                    if (mine) Color.Black.copy(alpha = 0.12f)
                                                                    else EInk.Background.copy(alpha = 0.45f)
                                                                )
                                                                .padding(end = 8.dp)
                                                        ) {
                                                            Box(
                                                                Modifier
                                                                    .width(3.dp)
                                                                    .fillMaxHeight()
                                                                    .background(if (mine) Color.Black.copy(alpha = 0.6f) else EInk.Accent)
                                                            )
                                                            Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                                                Text(
                                                                    profiles[parent.sender]?.bestName ?: Profile.shortNpub(parent.sender),
                                                                    color = if (mine) Color.Black else EInk.Accent,
                                                                    fontSize = 11.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    maxLines = 1
                                                                )
                                                                Text(
                                                                    text = when {
                                                                        parentUnsent -> tr("Message unsent")
                                                                        parent.kind == Nip17.FILE -> attachmentSummaryLabel(parent)
                                                                        else -> parent.content.replace('\n', ' ')
                                                                    },
                                                                    color = fg.copy(alpha = 0.78f),
                                                                    fontSize = 12.sp,
                                                                    fontStyle = if (parentUnsent) FontStyle.Italic else FontStyle.Normal,
                                                                    maxLines = 2,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }
                                                        }
                                                    }
                                                    if (file != null) {
                                                        EncryptedAttachment(
                                                            vm = vm,
                                                            info = file,
                                                            mine = mine,
                                                            onSaveFile = ::requestSaveFile
                                                        )
                                                    } else {
                                                        Text(m.content, color = fg, fontSize = 15.sp, lineHeight = 20.sp)
                                                    }
                                                }
                                            }

                                            // Polished Long-Press ActionSheet (Reactions, Reply, Copy/Save, Unsend, Delete for me)
                                            if (menuFor == m.id) ActionSheet(
                                                onDismiss = { menuFor = null },
                                                actions = buildList {
                                                    if (!isUnsent) {
                                                        add(SheetAction(Glyphs.Reply, tr("Reply")) { replyTo = m })
                                                        val file = m.file
                                                        if (file != null) {
                                                            add(SheetAction(Glyphs.Download, tr("Save to device")) {
                                                                scope.launch {
                                                                    val bytes = vm.openDmFile(file)
                                                                    if (bytes != null) {
                                                                        requestSaveFile(bytes, defaultAttachmentName(file))
                                                                    } else {
                                                                        vm.say(tr("Couldn't download the file"))
                                                                    }
                                                                }
                                                            })
                                                        } else {
                                                            add(SheetAction(Glyphs.Copy, tr("Copy text")) {
                                                                (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                                                                    .setPrimaryClip(android.content.ClipData.newPlainText("message", m.content))
                                                                vm.say(tr("Copied to clipboard"))
                                                            })
                                                        }
                                                        if (mine && !m.isLegacy) {
                                                            add(SheetAction(Glyphs.Undo, tr("Unsend message")) {
                                                                vm.unsendDm(peers, m)
                                                            })
                                                        }
                                                    }
                                                    add(SheetAction(Glyphs.Trash, tr("Delete for me"), destructive = true) {
                                                        vm.deleteDmForMe(m.id)
                                                    })
                                                },
                                                header = if (!isUnsent) {
                                                    {
                                                        // Quick reactions, as on Messenger / X: a row of emoji above the actions.
                                                        Row(
                                                            Modifier
                                                                .fillMaxWidth()
                                                                .padding(horizontal = 16.dp, vertical = 6.dp),
                                                            horizontalArrangement = Arrangement.SpaceEvenly
                                                        ) {
                                                            DM_REACTIONS.forEach { e ->
                                                                Text(
                                                                    e,
                                                                    fontSize = 26.sp,
                                                                    modifier = Modifier
                                                                        .clip(CircleShape)
                                                                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("React {0}", e)) {
                                                                            menuFor = null
                                                                            vm.reactDm(peers, m, e)
                                                                        }
                                                                        .padding(6.dp)
                                                                )
                                                            }
                                                        }
                                                        HorizontalDivider(
                                                            thickness = 0.5.dp,
                                                            color = EInk.BodyStroke,
                                                            modifier = Modifier.padding(vertical = 4.dp)
                                                        )
                                                    }
                                                } else null
                                            )
                                        }
                                    }

                                    if (!isUnsent) {
                                        reactions[m.id]?.takeIf { it.isNotEmpty() }?.let { rs ->
                                            Text(
                                                rs.groupingBy { it }.eachCount().entries.joinToString(" ") { (e, n) -> if (n > 1) "$e $n" else e },
                                                fontSize = 13.sp,
                                                color = EInk.Value,
                                                modifier = Modifier
                                                    .padding(top = 3.dp, start = if (!mine) 36.dp else 0.dp)
                                                    .background(EInk.Body, RoundedCornerShape(50))
                                                    .border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(50))
                                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                    }

                                    // Tap-to-reveal per-bubble timestamp (and NIP-04 indicator if legacy)
                                    AnimatedVisibility(
                                        visible = isTimeExpanded,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically()
                                    ) {
                                        Text(
                                            text = formatBubbleDetailTime(m.createdAt) +
                                                if (m.isLegacy) tr(" · old format") else "",
                                            color = EInk.Caption,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(
                                                top = 3.dp,
                                                bottom = 2.dp,
                                                start = if (!mine) 38.dp else 4.dp,
                                                end = 4.dp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        item(key = "bottom_spacer") { Spacer(Modifier.height(8.dp)) }
                    }
                }


                HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)

                // Reply preview bar above composer
                replyTo?.let { r ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(EInk.Body)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Glyphs.Reply, contentDescription = null, tint = EInk.Accent, modifier = Modifier.size(16.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                            Text(
                                tr("Replying to ") + (if (r.sender == me) tr("yourself") else profiles[r.sender]?.bestName ?: Profile.shortNpub(r.sender)),
                                color = EInk.Accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                if (r.kind == Nip17.FILE) attachmentSummaryLabel(r) else r.content.replace('\n', ' '),
                                color = EInk.Label,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Cancel reply")) { replyTo = null },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Glyphs.CloseLine, contentDescription = tr("Cancel reply"), tint = EInk.Caption, modifier = Modifier.size(15.dp))
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
                }

                // Polished modern composer bar (with full encrypted attachment picker)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .screenBackground()
                        .navigationBarsPadding()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Attachment picker button (Photo, Video, Audio, or Document)
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(EInk.Body)
                            .border(0.5.dp, EInk.BodyStroke, CircleShape)
                            .clickable(role = androidx.compose.ui.semantics.Role.Button, enabled = !mediaSending, onClickLabel = tr("Attach photo, video, or file")) {
                                showAttachSheet = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (mediaSending) {
                            CircularProgressIndicator(
                                color = EInk.Accent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                Glyphs.Attach,
                                contentDescription = tr("Attach photo, video, or file"),
                                tint = EInk.Accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Pill text input
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(EInk.Body)
                            .border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(22.dp))
                            .padding(horizontal = 16.dp, vertical = 11.dp)
                    ) {
                        BasicTextField(
                            value = text,
                            onValueChange = { text = it },
                            maxLines = 5,
                            textStyle = TextStyle(color = EInk.Value, fontSize = 15.sp, lineHeight = 20.sp),
                            cursorBrush = SolidColor(EInk.Accent),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { field ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (text.isEmpty()) {
                                        Text(tr("Message…"), color = EInk.Caption, fontSize = 15.sp)
                                    }
                                    field()
                                }
                            }
                        )
                    }

                    // Paper-plane Send button
                    val ready = text.isNotBlank() && !sending && me != null
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (ready || sending) EInk.Accent else EInk.Track)
                            .clickable(role = androidx.compose.ui.semantics.Role.Button, enabled = ready, onClickLabel = tr("Send")) {
                                sending = true
                                sendHaptics.confirm()
                                val outgoing = text.trim()
                                text = ""
                                val parent = replyTo
                                replyTo = null
                                vm.sendDm(peers, outgoing, parent?.id) { ok ->
                                    sending = false
                                    if (!ok) {
                                        text = outgoing
                                        replyTo = parent
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (sending) {
                            CircularProgressIndicator(
                                color = Color.Black,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                Glyphs.Send,
                                contentDescription = tr("Send"),
                                tint = if (ready) Color.Black else EInk.Caption,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Encrypted Attachment Picker Sheet (Photo, Video, or File/Audio)
    if (showAttachSheet) {
        ActionSheet(
            onDismiss = { showAttachSheet = false },
            actions = listOf(
                SheetAction(Glyphs.PhotoLine, tr("Photo (EXIF stripped & encrypted)")) {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                SheetAction(Glyphs.PlayLine, tr("Video (AES-GCM encrypted)")) {
                    videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                },
                SheetAction(Glyphs.File, tr("File or audio (PDF, ZIP, MP3…)")) {
                    filePicker.launch(arrayOf("*/*"))
                }
            ),
            header = {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Glyphs.Lock, contentDescription = null, tint = EInk.Accent, modifier = Modifier.size(15.dp))
                    Text(
                        tr("Send encrypted attachment (NIP-17 · up to 25 MB)"),
                        color = EInk.Label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
                HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke, modifier = Modifier.padding(bottom = 4.dp))
            }
        )
    }
}
