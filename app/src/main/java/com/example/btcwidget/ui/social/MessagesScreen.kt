package com.example.btcwidget.ui.social

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.runtime.NavKey
import com.example.btcwidget.ChatKey
import com.example.btcwidget.LoginKey
import com.example.btcwidget.ProfileKey
import com.example.btcwidget.nostr.DirectMessages
import com.example.btcwidget.nostr.NostrRepository
import com.example.btcwidget.nostr.core.DmFile
import com.example.btcwidget.nostr.core.Nip17
import com.example.btcwidget.nostr.core.Nip19
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.ui.main.EInk
import com.example.btcwidget.ui.main.EInkChip
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Conversations list (the Messages tab). */
@Composable
fun MessagesScreen(vm: SocialViewModel, navigate: (NavKey) -> Unit) {
    val account by vm.account.collectAsState()
    val conversations by vm.dmConversations.collectAsState()
    val loading by vm.dmLoading.collectAsState()
    val locked by vm.dmLocked.collectAsState()
    val inbox by vm.dmInbox.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val unread by vm.unreadConversations.collectAsState()
    val following by vm.following.collectAsState()
    val allMessages by vm.dmMessages.collectAsState()
    val unsentIds by vm.dmUnsentIds.collectAsState()
    var newMessage by remember { mutableStateOf(false) }
    var newGroup by remember { mutableStateOf(false) }
    var showRequests by rememberSaveable { mutableStateOf(false) }
    var filterQuery by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(account) { vm.startDms() }

    val me = account?.pubkey
    if (me == null) {
        MessagesLoggedOutEmpty(onLogin = { navigate(LoginKey) })
        return
    }

    // Message requests: chats with people you don't follow, until you reply.
    val answered = remember(allMessages, me) { allMessages.filter { it.sender == me }.map { it.peers(me).joinToString(",") }.toSet() }
    val (requests, chats) = remember(conversations, following, answered) {
        conversations.partition { c -> c.key !in answered && c.peers.none { it in following } }
    }
    val baseList = if (showRequests) requests else chats
    val shown = remember(baseList, filterQuery, profiles) {
        val q = filterQuery.trim().lowercase()
        if (q.isEmpty()) baseList
        else baseList.filter { c ->
            c.last.content.lowercase().contains(q) ||
                c.peers.any { pk ->
                    val p = profiles[pk]
                    listOfNotNull(p?.displayName, p?.name, p?.nip05, Profile.shortNpub(pk)).any { it.lowercase().contains(q) }
                }
        }
    }

    // Tap Messages again: back to the top; at the top, check for new messages.
    val listState = vm.listState("messages")
    ScrollToTopOnTabTap("MESSAGES", listState) { vm.startDms() }

    Box(Modifier.fillMaxSize().background(EInk.Background)) {
        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            // Top bar: Chats / Requests chips + quick New Group pill
            item(key = "header_tabs") {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val unreadChatsCount = remember(chats, unread) { chats.count { it.key in unread } }
                        EInkChip(
                            text = if (unreadChatsCount > 0) tr("CHATS ({0})", unreadChatsCount) else tr("CHATS"),
                            selected = !showRequests,
                            onClick = { showRequests = false }
                        )
                        EInkChip(
                            text = if (requests.isNotEmpty()) tr("REQUESTS ({0})", requests.size) else tr("REQUESTS"),
                            selected = showRequests,
                            onClick = { showRequests = true }
                        )
                        Spacer(Modifier.weight(1f))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(EInk.Body)
                                .border(1.dp, EInk.BodyStroke, RoundedCornerShape(50))
                                .clickable(onClickLabel = tr("New group chat")) { newGroup = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Glyphs.Group, contentDescription = null, tint = EInk.Value, modifier = Modifier.size(16.dp))
                            Text(
                                tr("New group"),
                                color = EInk.Value,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }

                    // Filter search box when the user has conversations
                    if (conversations.size > 2 || filterQuery.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 14.dp, bottom = 10.dp)
                                .height(40.dp)
                                .clip(RoundedCornerShape(50))
                                .background(EInk.Body)
                                .border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(50))
                                .padding(horizontal = 14.dp)
                        ) {
                            Icon(Glyphs.Search, contentDescription = null, tint = EInk.Caption, modifier = Modifier.size(17.dp))
                            BasicTextField(
                                value = filterQuery,
                                onValueChange = { filterQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(color = EInk.Value, fontSize = 14.sp),
                                cursorBrush = SolidColor(EInk.Accent),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 10.dp),
                                decorationBox = { field ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (filterQuery.isEmpty()) {
                                            Text(tr("Search conversations…"), color = EInk.Caption, fontSize = 14.sp, maxLines = 1)
                                        }
                                        field()
                                    }
                                }
                            )
                            if (filterQuery.isNotEmpty()) {
                                Box(
                                    Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(EInk.Track)
                                        .clickable(onClickLabel = tr("Clear")) { filterQuery = "" },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Glyphs.CloseLine, contentDescription = tr("Clear"), tint = EInk.Value, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }

                    if (showRequests) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 14.dp, bottom = 8.dp)
                        ) {
                            Icon(Glyphs.Shield, contentDescription = null, tint = EInk.Caption, modifier = Modifier.size(15.dp))
                            Text(
                                tr("From people you don't follow. Replying moves a chat to Chats."),
                                color = EInk.Caption,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
                }
            }

            if (inbox?.isEmpty() == true) item(key = "inbox") {
                Banner(
                    icon = Glyphs.Relay,
                    title = tr("Set up your message inbox"),
                    text = tr("Publish which relays receive your NIP-17 encrypted messages so others can reach you."),
                    action = tr("Set up"),
                    onClick = vm::setUpDmInbox
                )
            }
            if (locked > 0) item(key = "locked") {
                Banner(
                    icon = Glyphs.Lock,
                    title = tr("Encrypted messages waiting"),
                    text = tr("{0} messages need your signer app's permission to decrypt.", locked),
                    action = tr("Unlock"),
                    onClick = vm::unlockDms
                )
            }
            if (shown.isEmpty()) item(key = "empty") {
                if (loading) {
                    Spinner(Modifier.padding(top = 48.dp))
                } else {
                    MessagesEmptyState(
                        showRequests = showRequests,
                        hasFilter = filterQuery.isNotBlank(),
                        onNewMessage = { newMessage = true },
                        onClearFilter = { filterQuery = "" }
                    )
                }
            }
            items(shown, key = { it.key }) { c ->
                ConversationRow(
                    c = c,
                    me = me,
                    profiles = profiles,
                    unread = c.key in unread,
                    isLastUnsent = c.last.id in unsentIds,
                    onProfileClick = { pubkey -> navigate(ProfileKey(pubkey)) },
                    onClick = { navigate(ChatKey(c.key)) }
                )
            }
        }

        // Fades away with the bars while you scroll down, matching HomeScreen.
        val fab by animateFloatAsState(if (LocalBarsVisible.current) 1f else 0f, label = "dm_fab")
        if (fab > 0.01f) {
            FloatingActionButton(
                onClick = { newMessage = true },
                containerColor = EInk.Accent,
                contentColor = Color.Black,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .graphicsLayer {
                        alpha = fab
                        scaleX = 0.6f + 0.4f * fab
                        scaleY = 0.6f + 0.4f * fab
                    }
                    .semantics { contentDescription = tr("New message") }
            ) {
                Icon(Glyphs.NewChat, contentDescription = tr("New message"), modifier = Modifier.size(25.dp))
            }
        }
    }

    if (newMessage) NewMessageDialog(
        vm = vm,
        onDismiss = { newMessage = false },
        onOpen = { pubkey ->
            newMessage = false
            navigate(ChatKey(pubkey))
        },
        onNewGroup = {
            newMessage = false
            newGroup = true
        }
    )
    if (newGroup) NewGroupDialog(
        vm = vm,
        onDismiss = { newGroup = false },
        onStart = { peers ->
            newGroup = false
            navigate(ChatKey(peers.sorted().joinToString(",")))
        }
    )
}

@Composable
private fun MessagesLoggedOutEmpty(onLogin: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(EInk.Body)
                .border(1.dp, EInk.BodyStroke, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Glyphs.Lock, contentDescription = null, tint = EInk.Accent, modifier = Modifier.size(30.dp))
        }
        Text(
            tr("Private messages"),
            color = EInk.Value,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            tr("End-to-end encrypted (NIP-17): relays can't see who you talk to or what you say."),
            color = EInk.Label,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
        )
        PillButton(tr("Log in"), onClick = onLogin)
    }
}

@Composable
private fun MessagesEmptyState(
    showRequests: Boolean,
    hasFilter: Boolean,
    onNewMessage: () -> Unit,
    onClearFilter: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(EInk.Body)
                .border(1.dp, EInk.BodyStroke, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (showRequests) Glyphs.Shield else Glyphs.Chat,
                contentDescription = null,
                tint = EInk.Accent,
                modifier = Modifier.size(28.dp)
            )
        }
        Text(
            text = when {
                hasFilter -> tr("No matching conversations")
                showRequests -> tr("No message requests")
                else -> tr("No messages yet")
            },
            color = EInk.Value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 14.dp)
        )
        Text(
            text = when {
                hasFilter -> tr("Try searching for another name or keyword.")
                showRequests -> tr("Messages from people you don't follow will appear here first.")
                else -> tr("Start an end-to-end encrypted NIP-17 chat with anyone on Nostr.")
            },
            color = EInk.Caption,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
        )
        if (hasFilter) {
            PillButton(tr("Clear search"), filled = false, onClick = onClearFilter)
        } else if (!showRequests) {
            PillButton(tr("New message"), onClick = onNewMessage)
        }
    }
}

@Composable
private fun Banner(
    icon: ImageVector,
    title: String,
    text: String,
    action: String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(EInk.Body)
            .border(0.5.dp, EInk.Accent.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(EInk.Accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = EInk.Accent, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, color = EInk.Value, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(text, color = EInk.Label, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp))
        }
        PillButton(action, onClick = onClick)
    }
}

private fun attachmentSummaryLabel(m: Nip17.Message): String {
    val f = m.file ?: return tr("Encrypted file")
    return when {
        f.isImage -> tr("Encrypted photo")
        f.isVideo -> tr("Encrypted video")
        f.isAudio -> tr("Encrypted audio")
        !f.fileName.isNullOrBlank() -> f.fileName
        else -> tr("Encrypted file")
    }
}

private fun attachmentSummaryIcon(m: Nip17.Message): ImageVector {
    val f = m.file
    return when {
        f?.isImage == true -> Glyphs.PhotoLine
        f?.isVideo == true || f?.isAudio == true -> Glyphs.PlayLine
        else -> Glyphs.File
    }
}

@Composable
private fun ConversationRow(
    c: DirectMessages.Conversation,
    me: String,
    profiles: Map<String, Profile>,
    unread: Boolean,
    isLastUnsent: Boolean,
    onProfileClick: (String) -> Unit,
    onClick: () -> Unit
) {
    val isGroup = c.peers.size > 1
    val primaryPeer = c.peers.first()
    val primaryProfile = profiles[primaryPeer]
    val names = c.peers.joinToString(", ") { profiles[it]?.bestName ?: Profile.shortNpub(it) }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isGroup) {
            // Stacked dual avatar for group conversations
            Box(Modifier.size(48.dp)) {
                Box(Modifier.align(Alignment.TopStart)) {
                    Avatar(profiles[c.peers[0]], c.peers[0], size = 32)
                }
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(EInk.Background)
                        .padding(2.dp)
                ) {
                    Avatar(profiles[c.peers[1]], c.peers[1], size = 30)
                }
            }
        } else {
            Avatar(primaryProfile, primaryPeer, size = 48, onClick = { onProfileClick(primaryPeer) })
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = names,
                        color = EInk.Value,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!isGroup && isVerified(primaryProfile, primaryPeer)) {
                        VerifiedBadge(15.dp, Modifier.padding(start = 4.dp))
                    } else if (isGroup) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(EInk.Body)
                                .border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(50))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Icon(Glyphs.Group, contentDescription = null, tint = EInk.Caption, modifier = Modifier.size(11.dp))
                            Text(
                                text = "${c.peers.size + 1}",
                                color = EInk.Caption,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 3.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = relativeTime(c.last.createdAt),
                    color = if (unread) EInk.Accent else EInk.Caption,
                    fontSize = 12.sp,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal
                )
            }

            Spacer(Modifier.height(3.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                val prefix = when {
                    c.last.sender == me -> tr("You: ")
                    isGroup -> (profiles[c.last.sender]?.bestName ?: Profile.shortNpub(c.last.sender)) + ": "
                    else -> ""
                }
                val isFile = c.last.kind == Nip17.FILE && !isLastUnsent
                if (isLastUnsent) {
                    Icon(
                        Glyphs.Undo,
                        contentDescription = null,
                        tint = EInk.Caption,
                        modifier = Modifier.padding(end = 4.dp).size(14.dp)
                    )
                } else if (isFile) {
                    Icon(
                        attachmentSummaryIcon(c.last),
                        contentDescription = null,
                        tint = if (unread) EInk.Accent else EInk.Label,
                        modifier = Modifier.padding(end = 4.dp).size(15.dp)
                    )
                }
                val previewBody = when {
                    isLastUnsent -> tr("Message unsent")
                    isFile -> attachmentSummaryLabel(c.last)
                    else -> c.last.content.replace('\n', ' ')
                }
                Text(
                    text = prefix + previewBody,
                    color = if (isLastUnsent) EInk.Caption else if (unread) EInk.Value else EInk.Label,
                    fontSize = 14.sp,
                    fontStyle = if (isLastUnsent) FontStyle.Italic else FontStyle.Normal,
                    fontWeight = if (unread && !isLastUnsent) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (unread) {
                    Box(
                        Modifier
                            .padding(start = 8.dp)
                            .size(9.dp)
                            .background(EInk.Accent, CircleShape)
                    )
                }
            }
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
}

/** Start a conversation: pick someone you follow, or enter an npub, nprofile or name@domain. */
@Composable
private fun NewMessageDialog(
    vm: SocialViewModel,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
    onNewGroup: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val following by vm.following.collectAsState()
    val profiles by vm.profiles.collectAsState()
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(following) { vm.loadProfiles(following.take(200)) }

    val q = input.trim().lowercase()
    val suggestedFollows = remember(q, following, profiles) {
        following.filter { pk ->
            val p = profiles[pk]
            if (q.isEmpty()) p != null
            else p?.let { listOfNotNull(it.name, it.displayName, it.nip05).any { s -> s.lowercase().contains(q) } } == true
        }.sortedBy { profiles[it]?.bestName?.lowercase() ?: "~" }.take(20)
    }

    BlockDialog(
        onDismiss = onDismiss,
        title = tr("New message"),
        confirmLabel = if (busy) tr("Looking…") else tr("Open chat"),
        confirmEnabled = !busy && input.isNotBlank(),
        onConfirm = {
            busy = true
            scope.launch {
                val target = vm.resolve(input)
                busy = false
                if (target is NostrRepository.Direct.ProfileTarget) onOpen(target.pubkey)
                else error = tr("Couldn't find that person. Try their npub.")
            }
        }
    ) {
        // Quick action: New group chat
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(EInk.Background)
                .border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(14.dp))
                .clickable(onClick = onNewGroup)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(EInk.Accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Glyphs.Group, contentDescription = null, tint = EInk.Accent, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(tr("New group chat"), color = EInk.Value, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(tr("Up to {0} people · NIP-17 encrypted", MAX_GROUP), color = EInk.Caption, fontSize = 12.sp)
            }
            Icon(Glyphs.Chevron, contentDescription = null, tint = EInk.Caption, modifier = Modifier.size(20.dp))
        }

        Spacer(Modifier.height(12.dp))

        SettingsTextField(
            value = input,
            onValueChange = { input = it; error = null },
            placeholder = tr("Search follows, npub, or name@domain"),
            isError = error != null,
            modifier = Modifier.fillMaxWidth()
        )
        if (error != null) {
            Text(
                error!!,
                color = EInk.Alert,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        if (suggestedFollows.isNotEmpty()) {
            Text(
                tr("PEOPLE YOU FOLLOW"),
                color = EInk.Caption,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
            )
            Column {
                suggestedFollows.forEach { pk ->
                    val p = profiles[pk]
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onOpen(pk) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(p, pk, size = 36)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    p?.bestName ?: Profile.shortNpub(pk),
                                    color = EInk.Value,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (isVerified(p, pk)) {
                                    VerifiedBadge(14.dp, Modifier.padding(start = 4.dp))
                                }
                            }
                            Text(
                                handleText(p, pk),
                                color = EInk.Caption,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(Glyphs.Chat, contentDescription = null, tint = EInk.Caption, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/** Pick people you follow (or paste npubs) for a group chat. */
@Composable
private fun NewGroupDialog(vm: SocialViewModel, onDismiss: () -> Unit, onStart: (List<String>) -> Unit) {
    val following by vm.following.collectAsState()
    val profiles by vm.profiles.collectAsState()
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(listOf<String>()) }
    LaunchedEffect(following) { vm.loadProfiles(following.take(300)) }
    val q = query.trim().lowercase()
    val pasted = remember(q) { (Nip19.decode(q) as? Nip19.Pubkey)?.hex }
    val matches = remember(q, following, profiles) {
        following.filter { pk ->
            q.isEmpty() || profiles[pk]?.let { p -> listOfNotNull(p.name, p.displayName, p.nip05).any { it.lowercase().contains(q) } } == true
        }.sortedBy { profiles[it]?.bestName?.lowercase() ?: "~" }.take(40)
    }
    BlockDialog(
        onDismiss = onDismiss,
        title = tr("New group chat"),
        confirmLabel = if (picked.size >= 2) tr("Start with {0} people", picked.size) else tr("Pick at least 2 people"),
        confirmEnabled = picked.size >= 2,
        onConfirm = { onStart(picked) }
    ) {
        SettingsTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = tr("Search follows or paste an npub"),
            modifier = Modifier.fillMaxWidth()
        )
        if (picked.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                picked.forEach { pk ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(EInk.Accent.copy(alpha = 0.16f))
                            .border(0.5.dp, EInk.Accent, RoundedCornerShape(50))
                            .clickable { picked = picked - pk }
                            .padding(start = 4.dp, end = 8.dp, top = 3.dp, bottom = 3.dp)
                    ) {
                        Avatar(profiles[pk], pk, size = 20)
                        Text(
                            profiles[pk]?.bestName ?: Profile.shortNpub(pk),
                            color = EInk.Value,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                        Icon(Glyphs.CloseLine, contentDescription = tr("Remove"), tint = EInk.Accent, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
        // A plain column (at most 40 rows): the dialog itself scrolls.
        Column(Modifier.padding(top = 8.dp)) {
            (listOfNotNull(pasted) + matches).distinct().forEach { pk ->
                val on = pk in picked
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { picked = if (on) picked - pk else (picked + pk).take(MAX_GROUP) }
                        .padding(vertical = 7.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(profiles[pk], pk, size = 34)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(
                            profiles[pk]?.bestName ?: Profile.shortNpub(pk),
                            color = EInk.Value,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            handleText(profiles[pk], pk),
                            color = EInk.Caption,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (on) EInk.Accent else EInk.Track),
                        contentAlignment = Alignment.Center
                    ) {
                        if (on) Icon(Glyphs.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        Text(
            tr("Everyone in the group sees each other and every message. Up to {0} people.", MAX_GROUP),
            color = EInk.Caption,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/** Each message is wrapped once per member, so groups stay small. */
private const val MAX_GROUP = 10

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

    Column(Modifier.fillMaxSize().background(EInk.Background).imePadding()) {
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
                                    .clickable { navigate(ProfileKey(primaryPeer)) }
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

                        if (messages.isNotEmpty()) item(key = "tip") {
                            FirstTimeTip(
                                "swipe-reply",
                                tr("Swipe a message to the right to reply to it."),
                                Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                            )
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
                                                                        .clickable(onClickLabel = tr("React {0}", e)) {
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
                                .clickable(onClickLabel = tr("Cancel reply")) { replyTo = null },
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
                        .background(EInk.Background)
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
                            .clickable(enabled = !mediaSending, onClickLabel = tr("Attach photo, video, or file")) {
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
                            .clickable(enabled = ready, onClickLabel = tr("Send")) {
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

@Composable
private fun ChatTopBar(
    title: String,
    peers: List<String>,
    profiles: Map<String, Profile>,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val isGroup = peers.size > 1
    val primaryPeer = peers.first()
    val primaryProfile = profiles[primaryPeer]
    Column(Modifier.fillMaxWidth().background(EInk.Background).statusBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .clickable(onClickLabel = tr("Back"), onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Glyphs.Back, contentDescription = tr("Back"), tint = EInk.Value, modifier = Modifier.size(22.dp))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenProfile)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Avatar(primaryProfile, primaryPeer, size = 36)
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            color = EInk.Value,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (!isGroup && isVerified(primaryProfile, primaryPeer)) {
                            VerifiedBadge(15.dp, Modifier.padding(start = 4.dp))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Glyphs.Lock, contentDescription = null, tint = EInk.Accent, modifier = Modifier.size(11.dp))
                        Text(
                            text = if (isGroup) tr("{0} members · Encrypted", peers.size + 1) else tr("NIP-17 encrypted"),
                            color = EInk.Caption,
                            fontSize = 11.sp,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
    }
}

private fun defaultAttachmentName(info: DmFile.Info): String {
    if (!info.fileName.isNullOrBlank()) return info.fileName
    val ext = when (info.mime.lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        "audio/mpeg", "audio/mp3" -> "mp3"
        "audio/mp4", "audio/m4a", "audio/aac" -> "m4a"
        "audio/ogg" -> "ogg"
        "application/pdf" -> "pdf"
        "application/zip" -> "zip"
        else -> info.mime.substringAfter('/', "bin").take(6).filter { it.isLetterOrDigit() }.ifEmpty { "bin" }
    }
    val prefix = when {
        info.isImage -> "photo"
        info.isVideo -> "video"
        info.isAudio -> "audio"
        else -> "file"
    }
    val shortHash = info.sha256?.take(8) ?: "nip17"
    return "${prefix}_$shortHash.$ext"
}

private fun formatByteSize(bytes: Long?): String {
    if (bytes == null || bytes <= 0L) return ""
    return when {
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> "${bytes / 1024L} KB"
        else -> "$bytes B"
    }
}

/**
 * Renders any NIP-17 kind-15 encrypted attachment (photo, video, audio, or document):
 * downloaded, verified by SHA-256, and decrypted with AES-GCM on the phone.
 */
@Composable
private fun EncryptedAttachment(
    vm: SocialViewModel,
    info: DmFile.Info,
    mine: Boolean,
    onSaveFile: (ByteArray, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val decryptedBytes by produceState<ByteArray?>(null, info.url) {
        value = vm.openDmFile(info)
    }

    when {
        info.isImage -> {
            val bitmap by produceState<ImageBitmap?>(null, decryptedBytes) {
                val b = decryptedBytes ?: return@produceState
                value = withContext(Dispatchers.Default) {
                    runCatching { android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() }.getOrNull()
                }
            }
            var showFullscreen by remember { mutableStateOf(false) }
            val ratio = if ((info.width ?: 0) > 0 && (info.height ?: 0) > 0) info.width!!.toFloat() / info.height!! else 1f
            Box(
                Modifier
                    .width(244.dp)
                    .aspectRatio(ratio.coerceIn(0.55f, 1.9f))
                    .clip(RoundedCornerShape(14.dp))
                    .background(EInk.Track)
                    .then(
                        if (bitmap != null) Modifier.clickable { showFullscreen = true } else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                val b = bitmap
                if (b != null) {
                    Image(
                        b,
                        contentDescription = tr("Photo"),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    val raw = decryptedBytes
                    if (raw != null) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable(onClickLabel = tr("Save to device")) {
                                    onSaveFile(raw, defaultAttachmentName(info))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Glyphs.Download, contentDescription = tr("Save to device"), tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Glyphs.Lock, contentDescription = null, tint = EInk.Caption, modifier = Modifier.size(15.dp))
                        Text(tr("Encrypted photo"), color = EInk.Caption, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }

            if (showFullscreen && bitmap != null) {
                Dialog(
                    onDismissRequest = { showFullscreen = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.95f))
                            .clickable { showFullscreen = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap!!,
                            contentDescription = tr("Photo"),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(12.dp)
                        )
                        Row(
                            Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            decryptedBytes?.let { raw ->
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(EInk.Body)
                                        .border(0.5.dp, EInk.BodyStroke, CircleShape)
                                        .clickable { onSaveFile(raw, defaultAttachmentName(info)) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Glyphs.Download, contentDescription = tr("Save to device"), tint = EInk.Value, modifier = Modifier.size(20.dp))
                                }
                            }
                            Box(
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(EInk.Body)
                                    .border(0.5.dp, EInk.BodyStroke, CircleShape)
                                    .clickable { showFullscreen = false },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Glyphs.CloseLine, contentDescription = tr("Close"), tint = EInk.Value, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }

        info.isVideo -> {
            // Write decrypted video to cache file for thumbnail & inline playback
            val cachedVideo by produceState<Pair<File, ImageBitmap?>?>(null, decryptedBytes) {
                val b = decryptedBytes ?: return@produceState
                value = withContext(Dispatchers.IO) {
                    runCatching {
                        val dir = File(context.cacheDir, "dm_media").apply { mkdirs() }
                        val file = File(dir, defaultAttachmentName(info))
                        if (!file.exists() || file.length() != b.size.toLong()) {
                            file.writeBytes(b)
                        }
                        val thumb = runCatching {
                            val retriever = android.media.MediaMetadataRetriever()
                            try {
                                retriever.setDataSource(file.path)
                                retriever.getFrameAtTime(0)?.asImageBitmap()
                            } finally {
                                retriever.release()
                            }
                        }.getOrNull()
                        file to thumb
                    }.getOrNull()
                }
            }
            var playing by remember { mutableStateOf(false) }
            val ratio = if ((info.width ?: 0) > 0 && (info.height ?: 0) > 0) info.width!!.toFloat() / info.height!! else 16f / 9f
            Box(
                Modifier
                    .width(248.dp)
                    .aspectRatio(ratio.coerceIn(0.6f, 1.85f))
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                val cv = cachedVideo
                if (cv != null) {
                    if (playing) {
                        AndroidView(
                            factory = { ctx ->
                                android.widget.VideoView(ctx).apply {
                                    setVideoPath(cv.first.path)
                                    val controller = android.widget.MediaController(ctx)
                                    controller.setAnchorView(this)
                                    setMediaController(controller)
                                    setOnCompletionListener { playing = false }
                                    start()
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        cv.second?.let { thumb ->
                            Image(
                                thumb,
                                contentDescription = tr("Encrypted video"),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        // Center Play button
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(EInk.Accent)
                                .clickable(onClickLabel = tr("Play video")) { playing = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Glyphs.Play, contentDescription = tr("Play video"), tint = Color.Black, modifier = Modifier.size(26.dp))
                        }
                    }
                    // Save button in corner
                    decryptedBytes?.let { raw ->
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .clickable(onClickLabel = tr("Save to device")) {
                                    onSaveFile(raw, defaultAttachmentName(info))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Glyphs.Download, contentDescription = tr("Save to device"), tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = EInk.Accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Text(tr("Encrypted video"), color = EInk.Caption, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }

        else -> {
            // Encrypted Audio or Document/File Card (PDF, ZIP, MP3, APK, etc.)
            var isPlayingAudio by remember { mutableStateOf(false) }
            val mediaPlayer = remember { android.media.MediaPlayer() }
            DisposableEffect(info.url) {
                onDispose {
                    runCatching {
                        if (mediaPlayer.isPlaying) mediaPlayer.stop()
                        mediaPlayer.release()
                    }
                }
            }

            val fg = if (mine) Color.Black else EInk.Value
            val subFg = if (mine) Color.Black.copy(alpha = 0.7f) else EInk.Caption
            val iconBg = if (mine) Color.Black.copy(alpha = 0.14f) else EInk.Accent.copy(alpha = 0.16f)
            val iconTint = if (mine) Color.Black else EInk.Accent
            val displayName = defaultAttachmentName(info)
            val sizeLabel = formatByteSize(decryptedBytes?.size?.toLong() ?: info.size)
            val extBadge = displayName.substringAfterLast('.', "FILE").uppercase(Locale.US).take(5)

            Row(
                Modifier
                    .widthIn(min = 200.dp, max = 264.dp)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Action button: Play/Pause for Audio, or Download for Documents
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(iconBg)
                        .clickable(enabled = decryptedBytes != null) {
                            val raw = decryptedBytes ?: return@clickable
                            if (info.isAudio) {
                                if (isPlayingAudio) {
                                    runCatching { mediaPlayer.pause() }
                                    isPlayingAudio = false
                                } else {
                                    scope.launch(Dispatchers.IO) {
                                        runCatching {
                                            val dir = File(context.cacheDir, "dm_media").apply { mkdirs() }
                                            val f = File(dir, displayName)
                                            if (!f.exists() || f.length() != raw.size.toLong()) f.writeBytes(raw)
                                            withContext(Dispatchers.Main) {
                                                mediaPlayer.reset()
                                                mediaPlayer.setDataSource(f.path)
                                                mediaPlayer.setOnCompletionListener { isPlayingAudio = false }
                                                mediaPlayer.prepare()
                                                mediaPlayer.start()
                                                isPlayingAudio = true
                                            }
                                        }
                                    }
                                }
                            } else {
                                onSaveFile(raw, displayName)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (decryptedBytes == null) {
                        CircularProgressIndicator(color = iconTint, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    } else if (info.isAudio) {
                        Icon(
                            if (isPlayingAudio) Glyphs.Pause else Glyphs.Play,
                            contentDescription = tr("Play audio"),
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            Glyphs.File,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(
                        text = displayName,
                        color = fg,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOfNotNull(extBadge, sizeLabel.takeIf { it.isNotEmpty() }, "NIP-17").joinToString(" · "),
                        color = subFg,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }

                decryptedBytes?.let { raw ->
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(iconBg)
                            .clickable(onClickLabel = tr("Save to device")) {
                                onSaveFile(raw, displayName)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Glyphs.Download, contentDescription = tr("Save to device"), tint = iconTint, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

/**
 * Drag a message to the right to reply to it: it follows your finger (with some resistance), a
 * reply arrow fades in behind it, the phone ticks once it's far enough, and letting go there
 * replies. Vertical scrolling is untouched.
 */
private fun Modifier.swipeToReply(onReply: () -> Unit): Modifier = composed {
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val trigger = with(density) { 64.dp.toPx() }
    val max = with(density) { 96.dp.toPx() }
    val offset = remember { Animatable(0f) }
    var armed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val reply by rememberUpdatedState(onReply)
    val arrow = rememberVectorPainter(Glyphs.Reply)
    this
        .pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    if (armed) reply()
                    armed = false
                    scope.launch { offset.animateTo(0f, spring(dampingRatio = 0.7f)) }
                },
                onDragCancel = { armed = false; scope.launch { offset.animateTo(0f) } }
            ) { change, drag ->
                val next = (offset.value + drag * 0.6f).coerceIn(0f, max)
                if (next > 0f) change.consume()
                scope.launch { offset.snapTo(next) }
                val now = next >= trigger
                if (now && !armed) haptics.tick()
                armed = now
            }
        }
        // The arrow stays put at the left while the message slides over it.
        .drawBehind {
            val progress = (offset.value / trigger).coerceIn(0f, 1f)
            if (progress > 0f) {
                val size = 22.dp.toPx() * (0.7f + 0.3f * progress)
                translate(left = 16.dp.toPx(), top = (this.size.height - size) / 2) {
                    with(arrow) { draw(androidx.compose.ui.geometry.Size(size, size), alpha = progress, colorFilter = ColorFilter.tint(EInk.Accent)) }
                }
            }
        }
        .graphicsLayer { translationX = offset.value }
}
