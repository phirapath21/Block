package com.example.btcwidget.ui.social

import com.example.btcwidget.ui.main.TextSize
import com.example.btcwidget.ui.main.Radius
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import com.example.btcwidget.LoginKey
import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.ui.main.EInk

/**
 * The chat of a live stream, in the style of YouTube Live chat:
 * compact rows (picture, name, words), the host marked in orange, zaps as orange cards with the
 * top zappers above, "↓ New messages" when you've scrolled up, and a round box to type in.
 */
@Composable
internal fun LiveChat(
    vm: SocialViewModel,
    navigate: (NavKey) -> Unit,
    onBack: () -> Unit,
    title: String,
    note: String,
    messages: List<Event>?,
    action: Pair<String, () -> Unit>? = null,
    /** Shown above the messages and kept in view (a live stream's video). */
    pinned: (@Composable () -> Unit)? = null,
    /** People to mark as Host (a live stream's streamer). */
    hosts: Set<String> = emptySet(),
    /** False when drawn over a full-screen video: no title bar, see-through. */
    showTopBar: Boolean = true,
    /** 0..1 while the pinned video is dragged down to the mini player: everything else fades with it. */
    fade: () -> Float = { 0f },
    /** Kept by the caller when what's being typed must outlive this (a live stream turning on its side). */
    draft: androidx.compose.runtime.MutableState<String>? = null,
    onSend: (String, (String?) -> Unit) -> Unit
) {
    val account by vm.account.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val ownDraft = remember { mutableStateOf("") }
    var text by (draft ?: ownDraft)
    var sending by remember { mutableStateOf(false) }
    val sendHaptics = rememberHaptics()
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // The keyboard is up: what's pinned above the chat (a live stream) shrinks so the box you
    // type in and the newest messages stay on screen.
    val keyboardOpen = androidx.compose.foundation.layout.WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    // Following the newest messages unless you've scrolled up to read; then a pill counts new ones.
    val atEnd by remember { androidx.compose.runtime.derivedStateOf { !listState.canScrollForward } }
    var unseen by remember { mutableStateOf(0) }
    var lastCount by remember { mutableStateOf(0) }
    LaunchedEffect(messages?.size, keyboardOpen) {
        val n = messages?.size ?: return@LaunchedEffect
        if (n == 0) return@LaunchedEffect
        if (atEnd || lastCount == 0 || keyboardOpen) { listState.animateScrollToItem(n); unseen = 0 } else unseen += n - lastCount
        lastCount = n
    }
    LaunchedEffect(atEnd) { if (atEnd) unseen = 0 }
    val zaps = remember(messages) {
        messages.orEmpty().filter { it.kind == com.example.btcwidget.nostr.core.Kind.ZAP_RECEIPT }.mapNotNull { com.example.btcwidget.nostr.core.Zaps.parseReceipt(it) }
    }
    val fill = if (showTopBar) EInk.Background else androidx.compose.ui.graphics.Color.Transparent
    Column(Modifier.fillMaxSize().background(fill).imePadding()) {
        if (showTopBar) Box(Modifier.graphicsLayer { alpha = 1f - fade() }) { SocialTopBar(title, onBack) }
        SocialScaffold(vm, navigate) { _ ->
            Column(Modifier.fillMaxSize()) {
                androidx.compose.runtime.CompositionLocalProvider(LocalChatCompact provides keyboardOpen) { pinned?.invoke() }
                Column(Modifier.weight(1f).fillMaxWidth().graphicsLayer { alpha = 1f - fade() }) {
                if (zaps.isNotEmpty()) TopZaps(zaps, profiles)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                        item(key = "head") {
                            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                                Text(note, color = EInk.Caption, fontSize = TextSize.Caption)
                                action?.let { (label, run) ->
                                    if (account != null) Row(Modifier.padding(top = 6.dp)) { ChoicePill(label, selected = false) { run() } }
                                }
                            }
                        }
                        if (messages == null) item(key = "loading") { NotesSkeleton(3) }
                        else if (messages.isEmpty()) item(key = "empty") { EmptyNote(tr("No messages yet. Say hi!")) }
                        else items(messages, key = { it.id }) { m ->
                            if (m.kind == com.example.btcwidget.nostr.core.Kind.ZAP_RECEIPT) {
                                com.example.btcwidget.nostr.core.Zaps.parseReceipt(m)?.let { z -> ZapChatCard(z, profiles) { pk -> navigate(com.example.btcwidget.ProfileKey(pk)) } }
                            } else ChatRow(
                                m, profiles[m.pubkey], mine = m.pubkey == account?.pubkey, host = m.pubkey in hosts,
                                onProfile = { navigate(com.example.btcwidget.ProfileKey(m.pubkey)) }
                            )
                        }
                    }
                    // Scrolled up and new messages came: a pill to jump down to them.
                    androidx.compose.animation.AnimatedVisibility(
                        visible = unseen > 0 && !atEnd,
                        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it },
                        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
                    ) {
                        Row(
                            Modifier.clip(Radius.Full).background(SettingsAccent)
                                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Show new messages")) { scope.launch { listState.animateScrollToItem(messages?.size ?: 0) }; unseen = 0 }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.Icon(Glyphs.ArrowDown, contentDescription = null, tint = androidx.compose.ui.graphics.Color.Black, modifier = Modifier.size(16.dp))
                            Text(
                                tr("{0} new message{1}", unseen, plural(unseen)), color = androidx.compose.ui.graphics.Color.Black,
                                fontSize = TextSize.Caption, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
                error?.let { Text(it, color = EInk.Alert, fontSize = TextSize.Caption, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) }
                if (account == null) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.Center) { PillButton(tr("Log in to chat")) { navigate(LoginKey) } }
                } else Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
                    account?.let { Box(Modifier.padding(bottom = 6.dp)) { Avatar(profiles[it.pubkey], it.pubkey, size = 30) } }
                    Box(
                        Modifier.weight(1f).padding(horizontal = 8.dp).clip(Radius.Large).background(EInk.Body)
                            .padding(horizontal = 16.dp, vertical = 11.dp)
                    ) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = text, onValueChange = { text = it; error = null }, maxLines = 4,
                            textStyle = androidx.compose.ui.text.TextStyle(color = EInk.Value, fontSize = TextSize.Body),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(SettingsAccent),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { field ->
                                Box {
                                    if (text.isEmpty()) Text(tr("Chat…"), color = EInk.Caption, fontSize = TextSize.Body)
                                    field()
                                }
                            }
                        )
                    }
                    val ready = text.isNotBlank() && !sending
                    Box(
                        Modifier.size(42.dp).clip(CircleShape).background(if (ready || sending) SettingsAccent else EInk.Track)
                            .clickable(role = androidx.compose.ui.semantics.Role.Button, enabled = ready, onClickLabel = tr("Send")) {
                                sending = true
                                sendHaptics.confirm()
                                val outgoing = text
                                text = ""
                                onSend(outgoing) { e -> sending = false; if (e != null) { error = e; text = outgoing } }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (sending) androidx.compose.material3.CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        else androidx.compose.material3.Icon(Glyphs.Send, contentDescription = tr("Send"), tint = if (ready) androidx.compose.ui.graphics.Color.Black else EInk.Caption, modifier = Modifier.size(20.dp))
                    }
                }
                }
            }
        }
    }
}

/** One chat message: picture, name (orange with HOST for the host), time and words. */
@Composable
private fun ChatRow(m: Event, author: Profile?, mine: Boolean, host: Boolean, onProfile: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Avatar(author, m.pubkey, size = 28, onClick = onProfile)
        Column(Modifier.padding(start = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (mine) tr("You") else author?.bestName ?: Profile.shortNpub(m.pubkey),
                    color = if (host) SettingsAccent else EInk.Label, fontSize = TextSize.Caption, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                )
                if (host) Text(
                    tr("HOST"), color = androidx.compose.ui.graphics.Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(4.dp)).background(SettingsAccent).padding(horizontal = 4.dp, vertical = 1.dp)
                )
                Text("  " + relativeTime(m.createdAt), color = EInk.Caption, fontSize = TextSize.Micro)
            }
            Text(m.content, color = EInk.Value, fontSize = TextSize.Body, lineHeight = 20.sp)
        }
    }
}

/** A zap to the stream, like a Super Chat: an orange card with who, how much and what they said. */
@Composable
private fun ZapChatCard(z: com.example.btcwidget.nostr.core.Zaps.Receipt, profiles: Map<String, Profile>, onProfile: (String) -> Unit) {
    val sender = z.sender
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(Radius.Large)
            .background(SettingsAccent.copy(alpha = 0.14f)).border(1.dp, SettingsAccent.copy(alpha = 0.5f), Radius.Large)
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (sender != null) Avatar(profiles[sender], sender, size = 30, onClick = { onProfile(sender) })
        else Box(Modifier.size(30.dp).clip(CircleShape).background(SettingsAccent), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(Glyphs.Zap, contentDescription = null, tint = androidx.compose.ui.graphics.Color.Black, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    sender?.let { profiles[it]?.bestName ?: Profile.shortNpub(it) } ?: tr("Someone"), color = EInk.Value, fontSize = TextSize.Caption,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                )
                androidx.compose.material3.Icon(Glyphs.Zap, contentDescription = null, tint = SettingsAccent, modifier = Modifier.padding(start = 6.dp).size(14.dp))
                Text(
                    tr("{0} sats", "%,d".format(z.amountMsat / 1000)), color = SettingsAccent, fontSize = TextSize.Caption, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
            if (z.comment.isNotBlank()) Text(z.comment, color = EInk.Value, fontSize = TextSize.Body, lineHeight = 20.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** The biggest zappers of the stream so far, above the chat. */
@Composable
private fun TopZaps(zaps: List<com.example.btcwidget.nostr.core.Zaps.Receipt>, profiles: Map<String, Profile>) {
    val top = remember(zaps) {
        zaps.groupBy { it.sender }.map { (who, list) -> who to list.sumOf { it.amountMsat } / 1000 }.sortedByDescending { it.second }.take(8)
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Text(tr("Top tips"), color = EInk.Caption, fontSize = TextSize.Caption, fontWeight = FontWeight.Bold)
        top.forEach { (who, sats) ->
            Row(
                Modifier.clip(Radius.Full).background(SettingsAccent.copy(alpha = 0.14f)).padding(start = 3.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (who != null) Avatar(profiles[who], who, size = 20)
                androidx.compose.material3.Icon(Glyphs.Zap, contentDescription = null, tint = SettingsAccent, modifier = Modifier.padding(start = 4.dp).size(12.dp))
                Text(compactCount(sats.toInt()), color = EInk.Value, fontSize = TextSize.Caption, fontWeight = FontWeight.Bold)
            }
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
}

/** True while the keyboard is up in a chat: what's pinned above it should take little room. */
internal val LocalChatCompact = androidx.compose.runtime.compositionLocalOf { false }
