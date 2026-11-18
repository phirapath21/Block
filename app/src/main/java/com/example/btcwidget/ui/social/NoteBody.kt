package com.example.btcwidget.ui.social

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.btcwidget.nostr.core.Content
import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.ui.main.EInk

/** Note text with tappable links, hashtags and mentions; images and quoted notes below it. */
@Composable
fun NoteBody(note: Event, actions: NoteActions, fontSize: Int = 15, depth: Int = 0, collapse: Boolean = true) {
    // NIP-36: hide behind a warning until the reader chooses to see it.
    val warning = remember(note.id) { note.firstTag("content-warning") ?: note.tags.firstOrNull { it.firstOrNull() == "content-warning" } }
    var revealed by remember(note.id) { mutableStateOf(false) }
    if (warning != null && !revealed) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(EInk.Body)
                .padding(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(tr("⚠ Sensitive content"), color = EInk.Value, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                warning.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { Text(it, color = EInk.Label, fontSize = 13.sp) }
            }
            Text(
                tr("Show"), color = EInk.Value, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Show sensitive content")) { revealed = true }.padding(8.dp)
            )
        }
        return
    }
    if (note.kind == com.example.btcwidget.nostr.core.Kind.HIGHLIGHT) { HighlightBody(note, actions, fontSize); return }
    val segments = remember(note.id) {
        // Links that imeta tags mark as images show as images even without a file extension.
        val images = com.example.btcwidget.nostr.core.Imeta.imageUrls(note)
        val videos = com.example.btcwidget.nostr.core.Imeta.videoUrls(note)
        val parsed = Content.parse(note.content.trim(), com.example.btcwidget.nostr.core.CustomEmoji.of(note)).map { seg ->
            when {
                seg is Content.Segment.Link && seg.url in images -> Content.Segment.Image(seg.url)
                seg is Content.Segment.Link && seg.url in videos -> Content.Segment.Video(seg.url)
                else -> seg
            }
        }
        // Picture and video posts (NIP-68, NIP-71) keep their media in imeta tags, not the text.
        val inText = parsed.mapNotNull { (it as? Content.Segment.Image)?.url ?: (it as? Content.Segment.Video)?.url }.toSet()
        val fromTags = when (note.kind) {
            com.example.btcwidget.nostr.core.Kind.PICTURE ->
                com.example.btcwidget.nostr.core.Imeta.urls(note).filter { it !in inText }.map { Content.Segment.Image(it) }
            com.example.btcwidget.nostr.core.Kind.VIDEO, com.example.btcwidget.nostr.core.Kind.SHORT_VIDEO ->
                com.example.btcwidget.nostr.core.Imeta.urls(note).filter { it !in inText }.take(1).map { Content.Segment.Video(it) }
            else -> emptyList()
        }
        parsed + fromTags
    }
    // People @mentioned whose profile isn't loaded yet (threads, quotes, profiles): fetch them.
    LaunchedEffect(note.id) {
        val missing = segments.mapNotNull { (it as? Content.Segment.ProfileRef)?.pubkey }.distinct().filter { actions.profileOf(it) == null }
        if (missing.isNotEmpty()) actions.loadProfiles(missing)
    }
    val mentionedNames by remember(note.id, segments, actions) {
        androidx.compose.runtime.derivedStateOf {
            segments.mapNotNull { (it as? Content.Segment.ProfileRef)?.pubkey }
                .distinct()
                .map { pk -> pk to (actions.profileOf(pk)?.bestName ?: Profile.shortNpub(pk)) }
        }
    }
    val text: AnnotatedString = remember(note.id, mentionedNames) {
        val nameByPk = mentionedNames.toMap()
        val linkStyle = TextLinkStyles(SpanStyle(color = EInk.Value, fontWeight = FontWeight.Bold))
        buildAnnotatedString {
            for (seg in segments) when (seg) {
                is Content.Segment.Text -> append(seg.text)
                is Content.Segment.Link -> withLink(LinkAnnotation.Clickable("url", linkStyle) { actions.openLink(seg.url, note) }) {
                    append(seg.url.removePrefix("https://").removePrefix("http://").let { if (it.length > 40) it.take(38) + "…" else it })
                }
                is Content.Segment.Hashtag -> withLink(LinkAnnotation.Clickable("tag", linkStyle) { actions.openHashtag(seg.tag) }) {
                    append("#" + seg.tag)
                }
                is Content.Segment.ProfileRef -> withLink(LinkAnnotation.Clickable("p", linkStyle) { actions.openProfile(seg.pubkey) }) {
                    append("@" + (nameByPk[seg.pubkey] ?: Profile.shortNpub(seg.pubkey)))
                }
                // Custom emoji (NIP-30) draw inline as small images.
                is Content.Segment.Emoji -> appendInlineContent("emoji:" + seg.url, ":" + seg.code + ":")
                // Images and quoted notes are shown as blocks below.
                is Content.Segment.Image, is Content.Segment.Video, is Content.Segment.NoteRef, is Content.Segment.AddressRef -> Unit
            }
        }
    }
    val emojiContent = remember(note.id) {
        segments.filterIsInstance<Content.Segment.Emoji>().distinctBy { it.url }.associate { e ->
            ("emoji:" + e.url) to androidx.compose.foundation.text.InlineTextContent(
                androidx.compose.ui.text.Placeholder(1.2.em, 1.2.em, androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter)
            ) { AsyncImage(model = e.url, contentDescription = ":" + e.code + ":", modifier = Modifier.fillMaxSize()) }
        }
    }
    // Without the blank lines the links shown as blocks below (photos, quotes) left behind.
    val tidy: AnnotatedString = remember(text) {
        val parts = TextTidy.keep(text.text)
        if (parts.size == 1 && parts[0].first == 0 && parts[0].last == text.length - 1) text
        else buildAnnotatedString {
            parts.forEachIndexed { i, r ->
                if (i > 0) append("\n\n")
                append(text.subSequence(r.first, r.last + 1))
            }
        }
    }
    val shown = remember(tidy) { tidy.text }
    // Long notes are cut in timelines, profiles and quotes, with "Show more" under them.
    val maxLines = when {
        !collapse -> Int.MAX_VALUE
        depth > 0 -> QUOTE_LINES
        else -> TIMELINE_LINES
    }
    if (shown.isNotEmpty()) {
        var cut by remember(note.id) { mutableStateOf(false) }
        // In a timeline the rest of the post opens in place (and stays open while you scroll).
        var expanded by androidx.compose.runtime.saveable.rememberSaveable(note.id) { mutableStateOf(false) }
        Text(
            text = tidy,
            inlineContent = emojiContent,
            color = EInk.Value,
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 1.35).sp,
            maxLines = if (expanded) Int.MAX_VALUE else maxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) cut = it.hasVisualOverflow },
            // The rest unfolds smoothly instead of the post jumping taller.
            modifier = Modifier.padding(top = 2.dp).animateContentSize(androidx.compose.animation.core.spring(dampingRatio = 0.9f, stiffness = 400f))
        )
        if (cut && !expanded) ShowMore { if (depth > 0) actions.openNote(note) else expanded = true }
        else if (cut && expanded && depth == 0) ShowMore(tr("Show less")) { expanded = false }
    }
    if (note.kind == com.example.btcwidget.nostr.core.Kind.POLL) {
        com.example.btcwidget.nostr.core.Poll.of(note)?.let { PollView(it, actions) }
    }
    // One photo at its own shape; several as an album grid (X / Facebook style).
    val photos = segments.filterIsInstance<Content.Segment.Image>().map { it.url }.distinct()
    if (photos.size == 1) NoteImage(note, photos[0], actions) else if (photos.size > 1) NoteAlbum(note, photos, actions)
    segments.filterIsInstance<Content.Segment.Video>().take(2).forEach { VideoPlayer(it.url, note, actions) }
    // A card for the first web link (not for posts that already show photos, video or a quote).
    if (depth == 0 && segments.none { it is Content.Segment.Image || it is Content.Segment.Video || it is Content.Segment.NoteRef || it is Content.Segment.AddressRef }) {
        val btcEmbeds = remember(note.id) { BitcoinContextDetector.detectAll(note) }
        if (btcEmbeds.isNotEmpty()) {
            BitcoinEmbeds(btcEmbeds, note, actions)
        } else {
            segments.filterIsInstance<Content.Segment.Link>().firstOrNull()?.let { LinkCard(it.url, note, actions) }
        }
    }
    if (depth == 0) {
        segments.filterIsInstance<Content.Segment.NoteRef>().distinctBy { it.id }.take(2).forEach { ref ->
            QuotedNote(ref, note, actions)
        }
        segments.filterIsInstance<Content.Segment.AddressRef>().filter { it.address.kind == com.example.btcwidget.nostr.core.Kind.ARTICLE }
            .distinctBy { it.address.identifier + it.address.pubkey }.take(2).forEach { ArticleCard(it.address, actions) }
        // A linked live stream (NIP-53): a button to watch it.
        segments.filterIsInstance<Content.Segment.AddressRef>().filter { it.address.kind == com.example.btcwidget.nostr.core.LiveStreams.KIND }
            .take(1).forEach { ref ->
                Row(
                    Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(14.dp))
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Watch the stream")) { actions.openArticle(ref.address) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(tr("● LIVE"), color = EInk.Alert, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(tr("  Live stream · tap to watch"), color = EInk.Value, fontSize = 14.sp)
                }
            }
    }
}

/** Link preview: image, site and title, boxed like X's cards. Nothing shows until it loads. */
@Composable
private fun LinkCard(url: String, note: Event, actions: NoteActions) {
    val preview by produceState<com.example.btcwidget.nostr.core.LinkPreview?>(null, url) { value = actions.linkPreview(url) }
    val p = preview ?: return
    Column(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(0.5.dp, EInk.BodyStroke, RoundedCornerShape(14.dp))
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Open link")) { actions.openLink(url, note) }
    ) {
        p.image?.let {
            AsyncImage(
                model = it, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(1.91f).background(EInk.Track)
            )
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(p.site, color = EInk.Caption, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(p.title, color = EInk.Value, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            p.description?.let { Text(it, color = EInk.Label, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** The pictures in a note, in the order they appear (links imeta marks as images included). */
internal fun noteImageUrls(note: Event): List<String> {
    val images = com.example.btcwidget.nostr.core.Imeta.imageUrls(note)
    return Content.parse(note.content.trim()).mapNotNull { seg ->
        when {
            seg is Content.Segment.Image -> seg.url
            seg is Content.Segment.Link && seg.url in images -> seg.url
            else -> null
        }
    }.let { inText ->
        // Picture posts (NIP-68) list their photos in imeta tags.
        if (note.kind == com.example.btcwidget.nostr.core.Kind.PICTURE) inText + com.example.btcwidget.nostr.core.Imeta.urls(note) else inText
    }.distinct()
}

/** Lines a note shows before "Show more" in a timeline (about X's length), and in a quote. */
private const val TIMELINE_LINES = 10
private const val QUOTE_LINES = 5

/** "Show more" in the accent colour, under text that was cut short. */
@Composable
private fun ShowMore(label: String = tr("Show more"), onClick: () -> Unit) {
    Text(
        label, color = EInk.Accent, fontSize = 15.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 2.dp).clip(RoundedCornerShape(6.dp))
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Read the whole post"), onClick = onClick).padding(vertical = 4.dp)
    )
}

/** A highlight (NIP-84): the reader's comment, the passage with an accent bar, and where it's from. */
@Composable
private fun HighlightBody(note: Event, actions: NoteActions, fontSize: Int) {
    val h = remember(note.id) { com.example.btcwidget.nostr.core.Highlights.parse(note) } ?: return
    h.comment?.let { Text(it, color = EInk.Value, fontSize = fontSize.sp, lineHeight = (fontSize * 1.35).sp, modifier = Modifier.padding(top = 2.dp)) }
    Row(Modifier.padding(top = 6.dp).height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
        Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(EInk.Accent))
        Text(
            h.text, color = EInk.Value, fontSize = fontSize.sp, lineHeight = (fontSize * 1.35).sp,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, maxLines = 12, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
    // Where it's from: the article (opens it), else the web page.
    val address = remember(note.id) {
        h.articleAddress?.split(':', limit = 3)?.takeIf { it.size == 3 && it[0] == com.example.btcwidget.nostr.core.Kind.ARTICLE.toString() }
            ?.let { com.example.btcwidget.nostr.core.Nip19.Address(it[0].toInt(), it[1], it[2], emptyList()) }
    }
    when {
        address != null -> ArticleCard(address, actions)
        h.url != null -> Text(
            tr("From ") + h.url.substringAfter("://").substringBefore('/'), color = EInk.Caption, fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp).clickable(role = androidx.compose.ui.semantics.Role.Button) { actions.openLink(h.url, note) }
        )
    }
}

/** A note's words as plain text: mentions as names, links kept, media and quotes left out. */
internal fun plainText(note: Event, actions: NoteActions): String =
    Content.parse(note.content).joinToString("") { seg ->
        when (seg) {
            is Content.Segment.Text -> seg.text
            is Content.Segment.Link -> seg.url
            is Content.Segment.Hashtag -> "#" + seg.tag
            is Content.Segment.ProfileRef -> "@" + (actions.profileOf(seg.pubkey)?.bestName ?: Profile.shortNpub(seg.pubkey))
            else -> ""
        }
    }.let(TextTidy::tidy)
