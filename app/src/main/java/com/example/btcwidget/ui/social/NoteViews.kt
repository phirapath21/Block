package com.example.btcwidget.ui.social

import com.example.btcwidget.ui.main.TextSize
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.btcwidget.nostr.NoteStats
import com.example.btcwidget.nostr.core.Content
import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.nostr.core.ThreadRef
import com.example.btcwidget.ui.main.EInk

/** Everything a note row can do; screens pass one shared instance. */
@androidx.compose.runtime.Stable
class NoteActions(
    val openNote: (Event) -> Unit,
    val openProfile: (String) -> Unit,
    val openHashtag: (String) -> Unit,
    val reply: (Event) -> Unit,
    val repost: (Event) -> Unit,
    val quote: (Event) -> Unit,
    val like: (Event) -> Unit,
    val zap: (Event) -> Unit,
    val mute: (String) -> Unit,
    val report: (Event) -> Unit,
    val delete: (Event) -> Unit,
    val share: (Event) -> Unit,
    val bookmark: (Event) -> Unit,
    val pin: (Event) -> Unit,
    val isPinned: (String) -> Boolean,
    val isBookmarked: (String) -> Boolean,
    /** Opens an image full screen, with its note's author and actions. */
    val openImage: (url: String, note: Event) -> Unit,
    /** Opens a web link: in Block's browser with [note] under it, or the phone's browser (a setting). */
    val openLink: (url: String, note: Event?) -> Unit,
    /** Opens a video full screen (it carries on from where the timeline left it). */
    val openVideo: (url: String, note: Event) -> Unit,
    /** Videos may start by themselves, muted (the user's setting and the current connection). */
    val autoplayVideos: Boolean,
    /** The video open full screen, if any: its timeline copy pauses meanwhile. */
    val openVideoUrl: String?,
    val follow: (pubkey: String, follow: Boolean) -> Unit,
    /** Opens who reposted (0), liked (1) or zapped (2) a note. */
    val openEngagement: (note: Event, tab: Int) -> Unit,
    val isFollowing: (String) -> Boolean,
    val profileOf: (String) -> Profile?,
    val resolveNote: suspend (String) -> Event?,
    /**
     * A note quoted in another (the [Event]): also asks the relays its link and the quoting
     * note's tags name, its author's relays, the quoter's relays and the search relays.
     */
    val resolveQuote: suspend (Content.Segment.NoteRef, Event) -> Event? = { ref, _ -> resolveNote(ref.id) },
    /** Fetches profiles not loaded yet (people @mentioned in a note). */
    val loadProfiles: (Collection<String>) -> Unit = {},
    /** Hides the conversation this note belongs to (NIP-51 mute list, by its root note). */
    val muteThread: (Event) -> Unit,
    /** Reacts with an emoji, or ":code:" plus its image URL for a custom emoji (NIP-30). */
    val react: (Event, String, String?) -> Unit,
    /** The user's own custom emoji (kind 10030), shortcode to image. */
    val customEmojis: Map<String, String>,
    val pollTally: suspend (com.example.btcwidget.nostr.core.Poll) -> com.example.btcwidget.nostr.core.Poll.Tally?,
    /** Votes; the callback gets whether it was sent. Logged out, goes to log in instead. */
    val vote: (com.example.btcwidget.nostr.core.Poll, List<String>, (Boolean) -> Unit) -> Unit,
    val resolveArticle: suspend (com.example.btcwidget.nostr.core.Nip19.Address) -> com.example.btcwidget.nostr.core.Article?,
    val openArticle: (com.example.btcwidget.nostr.core.Nip19.Address) -> Unit,
    val linkPreview: suspend (String) -> com.example.btcwidget.nostr.core.LinkPreview?,
    val me: String?
)

/** A timeline row, X-style: avatar, name · handle · time, text, media, quoted note, actions. */
@Composable
fun NoteCard(
    note: Event,
    stats: NoteStats,
    actions: NoteActions,
    repostedBy: String? = null,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    large: Boolean = false
) {
    val author by remember(note.pubkey, actions) { androidx.compose.runtime.derivedStateOf { actions.profileOf(note.pubkey) } }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = androidx.compose.ui.semantics.Role.Button) { actions.openNote(note) }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        if (repostedBy != null) {
            val reposter by remember(repostedBy, actions) { androidx.compose.runtime.derivedStateOf { actions.profileOf(repostedBy) } }
            Text(
                text = tr("⟲ {0} reposted", reposter?.bestName ?: Profile.shortNpub(repostedBy)),
                color = EInk.Caption,
                fontSize = TextSize.Caption,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(start = 38.dp, bottom = 4.dp)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { actions.openProfile(repostedBy) }
            )
        }
        if (large) {
            // The opened post, as Amethyst shows it: name and handle beside the picture, then
            // the text, full date and buttons across the whole width.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(author, note.pubkey, size = 48, onClick = { actions.openProfile(note.pubkey) })
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    AuthorLine(note, author, actions, large = true)
                    Text(
                        handleText(author, note.pubkey), color = EInk.Caption, fontSize = TextSize.Body,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            val stamp = remember(note.id) { BlockStamp.fromEvent(note) }
            if (stamp != null) {
                BlockStampBadge(stamp, note, actions)
            }
            ReplyingTo(note, actions, Modifier.padding(top = 10.dp))
            // The opened post's text can be selected: long-press, drag the handles, Copy.
            androidx.compose.foundation.text.selection.SelectionContainer {
                Column { NoteBody(note, actions, fontSize = 17, collapse = false) }
            }
            Text(fullTimestamp(note.createdAt), color = EInk.Caption, fontSize = TextSize.Body, modifier = Modifier.padding(top = 10.dp))
            ActionRow(note, stats, actions)
            return@Column
        }
        Row {
            Avatar(author, note.pubkey, size = 44, onClick = { actions.openProfile(note.pubkey) })
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                AuthorLine(note, author, actions)
                val stamp = remember(note.id) { BlockStamp.fromEvent(note) }
                if (stamp != null) {
                    BlockStampBadge(stamp, note, actions)
                }
                val parentAuthor = remember(note.id) {
                    if (ThreadRef.of(note).parent != null) note.tagValues("p").firstOrNull() else null
                }
                if (parentAuthor != null) {
                    val parentProfile by remember(parentAuthor, actions) { androidx.compose.runtime.derivedStateOf { actions.profileOf(parentAuthor) } }
                    Text(
                        text = tr("Replying to @") + (parentProfile?.bestName ?: Profile.shortNpub(parentAuthor)),
                        color = EInk.Caption,
                        fontSize = TextSize.Caption,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
                NoteBody(note, actions, fontSize = 15)
                ActionRow(note, stats, actions)
            }
        }
    }
    if (showDivider) HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
}

/** "Replying to @name" under the author, for replies. */
@Composable
private fun ReplyingTo(note: Event, actions: NoteActions, modifier: Modifier = Modifier) {
    if (ThreadRef.of(note).parent == null) return
    val parentAuthor = note.tagValues("p").firstOrNull() ?: return
    Text(
        text = tr("Replying to @") + (actions.profileOf(parentAuthor)?.bestName ?: Profile.shortNpub(parentAuthor)),
        color = EInk.Caption, fontSize = TextSize.Caption, modifier = modifier
    )
}

/**
 * The author line, as Amethyst lays it out: the name in bold (with the verified badge when their NIP-05
 * address is verified), then the short time ("• 20m") and ⋮ at the far right. In the opened
 * post ([large]) the handle has its own line below and the full date sits under the text, so
 * only ⋮ is on the right.
 */
@Composable
internal fun AuthorLine(note: Event, author: Profile?, actions: NoteActions, large: Boolean = false, small: Boolean = false) {
    var menu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Name and badge take all the room the time and ⋮ leave; a long name ends in "…".
        Row(Modifier.weight(1f).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = author?.bestName ?: Profile.shortNpub(note.pubkey),
                color = EInk.Value,
                fontSize = if (small) 15.sp else 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (isVerified(author, note.pubkey)) VerifiedBadge(if (small) 15.dp else 17.dp, Modifier.padding(start = 3.dp))
            // Proof of work (NIP-13) the author mined into the note.
            val powBits = remember(note.id) { com.example.btcwidget.nostr.core.Pow.difficultyOf(note) }
            PowBadge(powBits, Modifier.padding(start = 5.dp))
        }
        if (!large) Text(
            "• " + relativeTime(note.createdAt), color = EInk.Caption, fontSize = if (small) 13.sp else 14.sp, maxLines = 1
        )
        Box {
            Box(
                Modifier.padding(start = 2.dp).size(if (small) 28.dp else 32.dp).clip(CircleShape).clickable(onClickLabel = tr("More options"), role = androidx.compose.ui.semantics.Role.Button) { menu = true }
                    .semantics { contentDescription = tr("More options") },
                contentAlignment = Alignment.Center
            ) { Icon(Glyphs.More, contentDescription = null, tint = EInk.Caption, modifier = Modifier.size(if (small) 16.dp else 18.dp)) }
            if (menu) NoteMenu(note, actions, onDismiss = { menu = false })
        }
    }
}

/** A note's ⋯ menu, as a sheet from the bottom like X's. */
@Composable
private fun NoteMenu(note: Event, actions: NoteActions, onDismiss: () -> Unit) {
    val mine = note.pubkey == actions.me
    val name = actions.profileOf(note.pubkey)?.bestName ?: Profile.shortNpub(note.pubkey)
    val items = buildList {
        add(SheetAction(if (actions.isBookmarked(note.id)) Glyphs.Bookmarked else Glyphs.Bookmark, if (actions.isBookmarked(note.id)) tr("Remove bookmark") else tr("Bookmark")) { actions.bookmark(note) })
        add(SheetAction(Glyphs.Stats, tr("Reposts, likes and zaps")) { actions.openEngagement(note, 1) })
        add(SheetAction(Glyphs.Share, tr("Share post")) { actions.share(note) })
        if (mine) {
            add(SheetAction(Glyphs.Pin, if (actions.isPinned(note.id)) tr("Unpin from profile") else tr("Pin to profile")) { actions.pin(note) })
            add(SheetAction(Glyphs.Delete, tr("Delete post"), destructive = true) { actions.delete(note) })
        } else {
            add(SheetAction(Glyphs.SoundOff, tr("Mute {0}", name)) { actions.mute(note.pubkey) })
            add(SheetAction(Glyphs.MuteThread, tr("Mute this conversation")) { actions.muteThread(note) })
            add(SheetAction(Glyphs.Flag, tr("Report post"), destructive = true) { actions.report(note) })
        }
    }
    ActionSheet(onDismiss, items)
}

/** Round profile picture, or the first letter of the name on a tinted disc. */
@Composable
fun Avatar(profile: Profile?, pubkey: String, size: Int, onClick: (() -> Unit)? = null) {
    val base = Modifier
        .size(size.dp)
        .clip(CircleShape)
        .background(EInk.Track)
        // Tappable: named after the person, so TalkBack says whose profile it opens.
        .let {
            if (onClick != null) it.clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Open profile"), onClick = onClick)
                .semantics { contentDescription = tr("{0}'s profile", profile?.bestName ?: Profile.shortNpub(pubkey)) }
            else it
        }
    // Block only loads over HTTPS: most hosts given as http:// answer on https:// too.
    val picture = profile?.picture?.let { if (it.startsWith("http://")) "https://" + it.removePrefix("http://") else it }
        ?.takeIf { it.startsWith("https://") }
    var failed by remember(picture) { mutableStateOf(false) }
    Box(base, contentAlignment = Alignment.Center) {
        // The initial shows until the picture loads, and stays if it can't.
        Text(
            text = (profile?.bestName ?: pubkey).take(1).uppercase(),
            color = EInk.Value,
            fontSize = (size * 0.42).sp,
            fontWeight = FontWeight.Bold
        )
        if (picture != null && !failed) {
            AsyncImage(
                model = picture, contentDescription = null, contentScale = ContentScale.Crop,
                onError = { failed = true }, modifier = Modifier.matchParentSize()
            )
        }
    }
}

/** "Tue, Nov 10, 2026 - 2:10 PM" in the phone's time zone (English, 12-hour). */
fun fullTimestamp(createdAt: Long, zone: java.util.TimeZone = java.util.TimeZone.getDefault()): String =
    java.text.SimpleDateFormat("EEE, MMM d, yyyy - h:mm a", java.util.Locale.US)
        .apply { timeZone = zone }
        .format(java.util.Date(createdAt * 1000))

private val yearFormat = ThreadLocal.withInitial { java.text.SimpleDateFormat("yyyy", java.util.Locale.US) }
private val dayMonthFormat = ThreadLocal.withInitial { java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()) }
private val dayMonthYearFormat = ThreadLocal.withInitial { java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()) }

/** "now", "5m", "3h", "2d", then "12 Mar" / "12 Mar 2025". */
fun relativeTime(createdAt: Long, now: Long = System.currentTimeMillis() / 1000): String {
    val s = (now - createdAt).coerceAtLeast(0)
    return when {
        s < 60 -> "now"
        s < 3600 -> "${s / 60}m"
        s < 86_400 -> "${s / 3600}h"
        s < 7 * 86_400 -> "${s / 86_400}d"
        else -> {
            val date = java.util.Date(createdAt * 1000)
            val yf = yearFormat.get()!!
            val sameYear = yf.format(date) == yf.format(java.util.Date(now * 1000))
            (if (sameYear) dayMonthFormat.get()!! else dayMonthYearFormat.get()!!).format(date)
        }
    }
}

/** 999, 1.2K, 3.4M. */
fun compactCount(n: Int): String = when {
    n < 1000 -> n.toString()
    n < 1_000_000 -> String.format(java.util.Locale.US, "%.1fK", n / 1000.0).replace(".0K", "K")
    else -> String.format(java.util.Locale.US, "%.1fM", n / 1_000_000.0).replace(".0M", "M")
}
