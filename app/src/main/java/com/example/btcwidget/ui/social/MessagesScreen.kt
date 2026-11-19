package com.example.btcwidget.ui.social

import com.example.btcwidget.ui.main.TextSize
import com.example.btcwidget.ui.main.Radius
import com.example.btcwidget.ui.main.screenBackground
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.example.btcwidget.ChatKey
import com.example.btcwidget.LoginKey
import com.example.btcwidget.ProfileKey
import com.example.btcwidget.nostr.DirectMessages
import com.example.btcwidget.nostr.core.Nip17
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.ui.main.EInk
import com.example.btcwidget.ui.main.BlockChip
import java.io.File

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

    Box(Modifier.fillMaxSize().screenBackground()) {
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
                        BlockChip(
                            text = if (unreadChatsCount > 0) tr("Chats ({0})", unreadChatsCount) else tr("Chats"),
                            selected = !showRequests,
                            onClick = { showRequests = false }
                        )
                        BlockChip(
                            text = if (requests.isNotEmpty()) tr("Requests ({0})", requests.size) else tr("Requests"),
                            selected = showRequests,
                            onClick = { showRequests = true }
                        )
                        Spacer(Modifier.weight(1f))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(Radius.Full)
                                .background(EInk.Body)
                                .border(1.dp, EInk.BodyStroke, Radius.Full)
                                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("New group chat")) { newGroup = true }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Glyphs.Group, contentDescription = null, tint = EInk.Value, modifier = Modifier.size(16.dp))
                            Text(
                                tr("New group"),
                                color = EInk.Value,
                                fontSize = TextSize.Caption,
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
                                .clip(Radius.Full)
                                .background(EInk.Body)
                                .border(0.5.dp, EInk.BodyStroke, Radius.Full)
                                .padding(horizontal = 14.dp)
                        ) {
                            Icon(Glyphs.Search, contentDescription = null, tint = EInk.Caption, modifier = Modifier.size(17.dp))
                            BasicTextField(
                                value = filterQuery,
                                onValueChange = { filterQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(color = EInk.Value, fontSize = TextSize.Body),
                                cursorBrush = SolidColor(EInk.Accent),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 10.dp),
                                decorationBox = { field ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (filterQuery.isEmpty()) {
                                            Text(tr("Search conversations…"), color = EInk.Caption, fontSize = TextSize.Body, maxLines = 1)
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
                                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Clear")) { filterQuery = "" },
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
                                fontSize = TextSize.Caption,
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
            fontSize = TextSize.Title,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            tr("End-to-end encrypted (NIP-17): relays can't see who you talk to or what you say."),
            color = EInk.Label,
            fontSize = TextSize.Body,
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
            fontSize = TextSize.Subtitle,
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
            fontSize = TextSize.Body,
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
            .clip(Radius.Large)
            .background(EInk.Body)
            .border(0.5.dp, EInk.Accent.copy(alpha = 0.45f), Radius.Large)
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
            Text(title, color = EInk.Value, fontSize = TextSize.Body, fontWeight = FontWeight.Bold)
            Text(text, color = EInk.Label, fontSize = TextSize.Caption, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp))
        }
        PillButton(action, onClick = onClick)
    }
}

internal fun attachmentSummaryLabel(m: Nip17.Message): String {
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
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
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
                        .screenBackground()
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
                        fontSize = TextSize.Body,
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
                                .clip(Radius.Full)
                                .background(EInk.Body)
                                .border(0.5.dp, EInk.BodyStroke, Radius.Full)
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Icon(Glyphs.Group, contentDescription = null, tint = EInk.Caption, modifier = Modifier.size(11.dp))
                            Text(
                                text = "${c.peers.size + 1}",
                                color = EInk.Caption,
                                fontSize = TextSize.Micro,
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
                    fontSize = TextSize.Caption,
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
                    fontSize = TextSize.Body,
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

