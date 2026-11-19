package com.example.btcwidget.ui.social

import com.example.btcwidget.ui.main.TextSize
import com.example.btcwidget.ui.main.Radius
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import com.example.btcwidget.ui.main.EInk
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.btcwidget.ComposeKey
import com.example.btcwidget.HashtagKey
import com.example.btcwidget.LoginKey
import com.example.btcwidget.NoteKey
import com.example.btcwidget.ProfileKey
import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Nip19
import com.example.btcwidget.nostr.core.Profile
import androidx.navigation3.runtime.NavKey

/**
 * Wraps a Nostr screen: builds the shared [NoteActions], shows toasts from the view model,
 * and hosts the zap, report and confirm dialogs every screen needs.
 */
@Composable
fun SocialScaffold(
    vm: SocialViewModel,
    navigate: (NavKey) -> Unit,
    content: @Composable (NoteActions) -> Unit
) {
    val context = LocalContext.current
    val profilesState = vm.profiles.collectAsState()
    val account by vm.account.collectAsState()
    var zapTarget by remember { mutableStateOf<Event?>(null) }
    var reportTarget by remember { mutableStateOf<Event?>(null) }
    var deleteTarget by remember { mutableStateOf<Event?>(null) }
    var shareStudioTarget by remember { mutableStateOf<Event?>(null) }
    var viewImage by rememberKeptState<Pair<String, Event>?> { null }
    var viewVideo by rememberKeptState<Pair<String, Event>?> { null }
    var viewWeb by rememberKeptState<Pair<String, Event?>?> { null }
    val linksInAppState = vm.devicePrefs.linksInApp.collectAsState()
    val autoplay by vm.devicePrefs.videoAutoplay.collectAsState()
    val bookmarksState = vm.bookmarks.collectAsState()
    val pinsState = vm.pinned.collectAsState()
    val followingState = vm.following.collectAsState()
    val nwcWallet by vm.wallet.nwc.collectAsState()
    val customEmojis by vm.emojis.collectAsState()
    val navigateState = androidx.compose.runtime.rememberUpdatedState(navigate)
    val accountState = androidx.compose.runtime.rememberUpdatedState(account)

    LaunchedEffect(vm) {
        vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    // Actions that change something need an account: send logged-out users to log in.
    fun signedIn(block: () -> Unit) = if (accountState.value == null) navigateState.value(LoginKey) else block()

    // A light tap of vibration for likes, reposts and bookmarks, as on X.
    val haptics = rememberHaptics()
    val autoplayNow = autoplay.allowedNow(context)
    val openVideoUrlNow = viewVideo?.first ?: PipVideo.miniVideo?.url
    val mePubkey = account?.pubkey
    val actions = remember(vm, context, haptics, autoplayNow, openVideoUrlNow, customEmojis, mePubkey) {
        NoteActions(
            openNote = { navigateState.value(NoteKey(it.id)) },
            openProfile = { navigateState.value(ProfileKey(it)) },
            openHashtag = { navigateState.value(HashtagKey(it)) },
            reply = { n -> signedIn { navigateState.value(ComposeKey(replyTo = n.id)) } },
            repost = { n -> signedIn { haptics.tick(); vm.repost(n) } },
            quote = { n -> signedIn { navigateState.value(ComposeKey(quote = n.id)) } },
            like = { n -> signedIn { haptics.tick(); vm.like(n) } },
            zap = { n -> signedIn { zapTarget = n } },
            mute = { p -> signedIn { vm.mute(p, true) } },
            report = { n -> signedIn { reportTarget = n } },
            delete = { n -> deleteTarget = n },
            share = { n -> shareStudioTarget = n },
            bookmark = { n -> signedIn { haptics.tick(); vm.toggleBookmark(n) } },
            isBookmarked = { it in bookmarksState.value },
            pin = { n -> signedIn { vm.togglePin(n) } },
            isPinned = { it in pinsState.value },
            openImage = { url, note -> viewImage = url to note },
            openVideo = { url, note -> viewVideo = url to note },
            openLink = { url, note ->
                if (linksInAppState.value && (url.startsWith("https://") || url.startsWith("http://"))) viewWeb = url to note
                else openExternally(context, url)
            },
            autoplayVideos = autoplayNow,
            openVideoUrl = openVideoUrlNow,
            follow = { pk, on -> signedIn { vm.follow(pk, on) } },
            isFollowing = { it in followingState.value },
            openEngagement = { n, tab -> navigateState.value(com.example.btcwidget.EngagementKey(n.id, tab)) },
            profileOf = { profilesState.value[it] },
            resolveNote = { vm.note(it) },
            resolveQuote = { ref, quoting ->
                val hints = com.example.btcwidget.nostr.core.QuoteHints.of(quoting, ref.id, ref.relays, ref.author)
                vm.note(ref.id, hints.relays, hints.author, via = quoting.pubkey)
            },
            loadProfiles = { vm.loadProfiles(it) },
            react = { n, content, url -> signedIn { vm.react(n, content, url) } },
            muteThread = { n -> signedIn { vm.muteThread(n) } },
            customEmojis = customEmojis,
            pollTally = { vm.pollTally(it) },
            vote = { poll, ids, done -> if (accountState.value == null) { navigateState.value(LoginKey); done(false) } else vm.vote(poll, ids, done) },
            resolveArticle = { vm.article(it) },
            openArticle = { a ->
                // Addresses of live streams open the stream, else an article.
                when {
                    a.kind == com.example.btcwidget.nostr.core.LiveStreams.KIND ->
                        navigateState.value(com.example.btcwidget.LiveKey("${a.kind}:${a.pubkey}:${a.identifier}"))
                    else -> navigateState.value(com.example.btcwidget.ArticleKey(Nip19.naddr(a.kind, a.pubkey, a.identifier, a.relays)))
                }
            },
            linkPreview = { vm.linkPreview(it) },
            me = mePubkey
        )
    }

    content(actions)

    viewImage?.let { (url, note) ->
        val stats by vm.stats.collectAsState()
        ImageViewer(
            url, note, stats[note.id] ?: com.example.btcwidget.nostr.NoteStats(), actions,
            onDismiss = { viewImage = null },
            urls = noteImageUrls(note),
            statusBar = { PublishStatusBar(vm) },
            replyBar = { onActive ->
                ReplyBar(
                    vm, note, dark = true,
                    onLogin = { viewImage = null; navigate(com.example.btcwidget.LoginKey) },
                    onExpand = { viewImage = null; navigate(com.example.btcwidget.ComposeKey(replyTo = note.id)) },
                    onActiveChange = onActive
                )
            }
        )
    }

    // The PiP window was closed: the full-screen video is over.
    LaunchedEffect(PipVideo.closedInPip) { if (PipVideo.closedInPip) { viewVideo = null; PipVideo.closedInPip = false } }
    // In Picture-in-Picture the activity draws the video itself (see MainActivity); the viewer hides.
    if (!PipVideo.inPip) viewVideo?.let { (url, note) ->
        val stats by vm.stats.collectAsState()
        ImageViewer(
            url, note, stats[note.id] ?: com.example.btcwidget.nostr.NoteStats(), actions,
            onDismiss = { viewVideo = null },
            video = true,
            statusBar = { PublishStatusBar(vm) },
            replyBar = { onActive ->
                ReplyBar(
                    vm, note, dark = true,
                    onLogin = { viewVideo = null; navigate(com.example.btcwidget.LoginKey) },
                    onExpand = { viewVideo = null; navigate(com.example.btcwidget.ComposeKey(replyTo = note.id)) },
                    onActiveChange = onActive
                )
            }
        )
    }

    viewWeb?.let { (url, note) ->
        val stats by vm.stats.collectAsState()
        WebViewer(url, note, note?.let { stats[it.id] } ?: com.example.btcwidget.nostr.NoteStats(), actions, onDismiss = { viewWeb = null })
    }

    zapTarget?.let { note ->
        ZapDialog(
            inApp = nwcWallet != null,
            recipientName = profilesState.value[note.pubkey]?.bestName ?: "them",
            onDismiss = { zapTarget = null },
            onZap = { sats, comment ->
                zapTarget = null
                // A profile zap has no note (see fakeProfileNote).
                vm.zap(note.pubkey, sats, note.takeIf { it.id.isNotEmpty() }, comment) { invoice -> payInvoice(context, invoice) }
            }
        )
    }
    reportTarget?.let { note ->
        ReportDialog(onDismiss = { reportTarget = null }, onReport = { reason ->
            reportTarget = null
            vm.report(note, reason)
        })
    }
    deleteTarget?.let { note ->
        ConfirmDialog(
            title = tr("Delete post?"),
            text = tr("Relays are asked to delete it, and it's removed from your profile and timelines. Copies others already saved may remain."),
            confirmLabel = tr("Delete"),
            onConfirm = { deleteTarget = null; vm.delete(note) },
            onDismiss = { deleteTarget = null }
        )
    }
    shareStudioTarget?.let { note ->
        val author = profilesState.value[note.pubkey]
        val stats by vm.stats.collectAsState()
        val studioData = remember(note.id, author, stats) {
            ShareStudioData(
                authorName = author?.bestName ?: Profile.shortNpub(note.pubkey),
                authorHandle = author?.nip05 ?: Profile.shortNpub(note.pubkey),
                contentText = note.content.trim(),
                timestampSeconds = note.createdAt,
                shareUrl = "https://njump.me/" + Nip19.nevent(note.id, author = note.pubkey),
                blockStamp = BlockStamp.fromEventOrInline(note),
                stats = stats[note.id]
            )
        }
        NoteShareSheet(
            data = studioData,
            onDismiss = { shareStudioTarget = null }
        )
    }
}

@Composable
internal fun ZapDialog(recipientName: String, inApp: Boolean, onDismiss: () -> Unit, onZap: (Long, String) -> Unit) {
    var amount by remember { mutableStateOf("21") }
    var comment by remember { mutableStateOf("") }
    val sats = amount.toLongOrNull()?.takeIf { it > 0 }
    BlockDialog(
        onDismiss = onDismiss,
        title = tr("Zap {0}", recipientName),
        confirmLabel = sats?.let { "Zap ${"%,d".format(it)} sats" } ?: tr("Zap"),
        confirmEnabled = sats != null,
        onConfirm = { sats?.let { onZap(it, comment) } }
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(21L, 100L, 500L, 1000L, 5000L, 21000L).forEach { preset ->
                val on = sats == preset
                Text(
                    "⚡ ${"%,d".format(preset)}", fontSize = TextSize.Body, fontWeight = FontWeight.Bold,
                    color = if (on) EInk.OnAccent else EInk.Value,
                    modifier = Modifier.clip(Radius.Full).background(if (on) ZapOrange else EInk.Track)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) { amount = preset.toString() }.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
        SettingsTextField(
            value = amount,
            onValueChange = { v -> amount = v.filter { it.isDigit() }.take(9) },
            placeholder = tr("Amount (sats)"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
        )
        SettingsTextField(
            value = comment,
            onValueChange = { comment = it.take(140) },
            placeholder = tr("Comment (optional)"),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        Text(
            if (inApp) tr("Pays from your connected wallet (NWC).") else tr("Opens your Lightning wallet to pay. Connect a wallet in Settings › Nostr to zap in one tap."),
            color = EInk.Caption, fontSize = TextSize.Caption, modifier = Modifier.padding(top = 10.dp)
        )
    }
}

private val ZapOrange get() = EInk.Accent

@Composable
private fun ReportDialog(onDismiss: () -> Unit, onReport: (String) -> Unit) {
    // NIP-56 report types.
    val reasons = listOf("spam" to "Spam", "nudity" to "Nudity", "profanity" to "Hateful or abusive", "illegal" to "Illegal", "impersonation" to "Impersonation", "other" to "Other")
    BlockDialog(
        onDismiss = onDismiss,
        title = tr("Report post"),
        text = tr("Why are you reporting it? The report is public and goes to your relays (NIP-56).")
    ) {
        reasons.forEach { (code, label) -> ChoiceRow(tr(label)) { onReport(code) } }
    }
}

/** Opens a Lightning wallet for [invoice]; without one, copies it. */
fun payInvoice(context: Context, invoice: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("lightning:$invoice")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Lightning invoice", invoice))
        Toast.makeText(context, tr("No Lightning wallet found. Invoice copied."), Toast.LENGTH_LONG).show()
    }
}

/**
 * A video brought back to full screen from the mini player (it was dragged down out of the
 * viewer earlier): the same viewer, carrying on with the same player.
 */
@Composable
fun ReopenedVideo(vm: SocialViewModel, video: PipVideo.MiniVideo, navigate: (NavKey) -> Unit, onDismiss: () -> Unit) {
    SocialScaffold(vm, navigate) { actions ->
        val stats by vm.stats.collectAsState()
        LaunchedEffect(PipVideo.closedInPip) { if (PipVideo.closedInPip) { PipVideo.closedInPip = false; onDismiss() } }
        if (!PipVideo.inPip) ImageViewer(
            video.url, video.note, stats[video.note.id] ?: com.example.btcwidget.nostr.NoteStats(), actions,
            onDismiss = onDismiss,
            video = true,
            statusBar = { PublishStatusBar(vm) },
            replyBar = { onActive ->
                ReplyBar(
                    vm, video.note, dark = true,
                    onLogin = { onDismiss(); navigate(com.example.btcwidget.LoginKey) },
                    onExpand = { onDismiss(); navigate(com.example.btcwidget.ComposeKey(replyTo = video.note.id)) },
                    onActiveChange = onActive
                )
            }
        )
    }
}
