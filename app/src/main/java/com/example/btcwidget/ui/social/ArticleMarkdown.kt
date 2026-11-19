package com.example.btcwidget.ui.social

import com.example.btcwidget.ui.main.TextSize
import com.example.btcwidget.ui.main.Radius
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import coil3.compose.AsyncImage
import com.example.btcwidget.NoteKey
import com.example.btcwidget.ProfileKey
import com.example.btcwidget.nostr.core.Markdown
import com.example.btcwidget.nostr.core.Nip19
import com.example.btcwidget.ui.main.EInk
import kotlinx.coroutines.withTimeoutOrNull

internal val HighlightTint = Color(0x26F7931A)

/** A block's plain text, to quote, copy or read aloud (null for images, code and rules). */
internal fun blockText(block: Markdown.Block): String? = when (block) {
    is Markdown.Block.Paragraph -> com.example.btcwidget.nostr.core.spansText(block.spans)
    is Markdown.Block.ListItem -> com.example.btcwidget.nostr.core.spansText(block.spans)
    is Markdown.Block.Quote -> com.example.btcwidget.nostr.core.spansText(block.spans)
    is Markdown.Block.Heading -> com.example.btcwidget.nostr.core.spansText(block.spans)
    else -> null
}?.takeIf { it.isNotBlank() }

/**
 * Calls [onLongPress] only when the finger is held stationary for the full long-press timeout,
 * without interfering with normal taps (so links inside [Text] work) and without misfiring when
 * the user scrolls or flings the [LazyColumn].
 */
internal fun Modifier.onLongPress(onLongPress: () -> Unit): Modifier = composed {
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
internal fun ArticleAction(label: String, onClick: () -> Unit) {
    Text(label, color = EInk.Value, fontSize = TextSize.Body, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick).padding(vertical = 4.dp))
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
            markdownSpans(block.spans, actions, navigate), color = EInk.Value, fontWeight = FontWeight.Bold,
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
            markdownSpans(block.spans, actions, navigate),
            color = EInk.Value,
            fontSize = bodySize,
            fontFamily = family,
            lineHeight = bodyLeading,
            modifier = pad
        )
        is Markdown.Block.ListItem -> Row(pad) {
            Text(block.marker, color = EInk.Accent, fontSize = bodySize, fontWeight = FontWeight.Bold, modifier = Modifier.width(24.dp))
            Text(markdownSpans(block.spans, actions, navigate), color = EInk.Value, fontSize = bodySize, fontFamily = family, lineHeight = bodyLeading)
        }
        is Markdown.Block.Quote -> Row(pad) {
            Spacer(Modifier.width(3.dp).heightIn(min = 20.dp).background(EInk.Accent))
            Text(
                markdownSpans(block.spans, actions, navigate),
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
            modifier = pad.fillMaxWidth().clip(Radius.Small).background(EInk.Body).border(0.5.dp, EInk.BodyStroke, Radius.Small).padding(10.dp)
        )
        is Markdown.Block.Image -> if (block.url.startsWith("https://")) {
            AsyncImage(
                model = block.url, contentDescription = block.alt.ifBlank { null }, contentScale = ContentScale.FillWidth,
                modifier = pad.fillMaxWidth().clip(Radius.Small)
            )
        }
        Markdown.Block.Rule -> HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
    }
}

/** Inline Markdown as styled text; nostr: links open in the app, web links in the browser. */
@Composable
internal fun markdownSpans(spans: List<Markdown.Span>, actions: NoteActions?, navigate: (NavKey) -> Unit): AnnotatedString {
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
