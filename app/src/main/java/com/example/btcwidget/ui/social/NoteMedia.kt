package com.example.btcwidget.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.ui.main.EInk

private val imageRatioCache = java.util.concurrent.ConcurrentHashMap<String, Float>()

/**
 * A picture (or GIF) in a note, shown whole at its own shape: the size comes from the note's
 * imeta "dim" when given, else from the image once loaded (and cached in [imageRatioCache] so
 * scrolling back and forth never jumps height). Only very tall or very wide pictures are trimmed.
 */
@Composable
internal fun NoteImage(note: Event, url: String, actions: NoteActions) {
    var ratio by remember(note.id, url) {
        mutableStateOf(com.example.btcwidget.nostr.core.Imeta.aspectRatio(note, url) ?: imageRatioCache[url])
    }
    val shape = RoundedCornerShape(14.dp)
    // A soft blur of the picture, at its size, until it has loaded (when the note gives one).
    val blur = rememberBlurhashPainter(remember(note.id, url) { com.example.btcwidget.nostr.core.Imeta.blurhash(note, url) })
    AsyncImage(
        model = url,
        contentDescription = tr("Image"),
        contentScale = ContentScale.Crop,
        placeholder = blur,
        onSuccess = { state ->
            val size = state.painter.intrinsicSize
            if (size.width > 0f && size.height > 0f) {
                val r = size.width / size.height
                imageRatioCache[url] = r
                if (ratio == null) ratio = r
            }
        },
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .then(ratio?.let { Modifier.aspectRatio(mediaRatio(it, MIN_RATIO, MAX_RATIO)) } ?: Modifier.heightIn(min = 160.dp, max = 320.dp))
            .clip(shape)
            .background(EInk.Body)
            .border(0.5.dp, EInk.BodyStroke, shape)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("View image")) { actions.openImage(url, note) }
    )
}

/**
 * Several photos as one album ([AlbumLayout]): a rounded box split into tiles with thin gaps,
 * each photo cropped to its tile over its blurhash, the fifth showing "+N" for the rest.
 * Tapping a tile opens the viewer at that photo, where every one can be swiped through.
 */
@Composable
internal fun NoteAlbum(note: Event, urls: List<String>, actions: NoteActions) {
    val layout = remember(urls.size) { AlbumLayout.of(urls.size) }
    val shape = RoundedCornerShape(14.dp)
    val gap = 2.dp
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            // A phone on its side or a tablet: never taller than most of the screen.
            .heightIn(max = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.7f).dp)
            .aspectRatio(layout.aspect)
            .clip(shape)
            .border(0.5.dp, EInk.BodyStroke, shape)
    ) {
        val w = maxWidth
        val h = maxHeight
        layout.tiles.forEachIndexed { i, t ->
            val url = urls[i]
            // Half a gap on each inner edge, none on the album's own edges.
            val left = if (t.x > 0f) gap / 2 else 0.dp
            val top = if (t.y > 0f) gap / 2 else 0.dp
            val right = if (t.x + t.w < 0.999f) gap / 2 else 0.dp
            val bottom = if (t.y + t.h < 0.999f) gap / 2 else 0.dp
            val blur = rememberBlurhashPainter(remember(note.id, url) { com.example.btcwidget.nostr.core.Imeta.blurhash(note, url) })
            val last = i == layout.tiles.lastIndex && layout.more > 0
            Box(
                Modifier
                    .offset(x = w * t.x + left, y = h * t.y + top)
                    .size(width = w * t.w - left - right, height = h * t.h - top - bottom)
                    .background(EInk.Body)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("View image")) { actions.openImage(url, note) }
                    .semantics(mergeDescendants = true) {
                        contentDescription = if (last) tr("Image {0} of {1}, {2} more", i + 1, urls.size, layout.more)
                        else tr("Image {0} of {1}", i + 1, urls.size)
                    }
            ) {
                AsyncImage(
                    model = url, contentDescription = null, contentScale = ContentScale.Crop, placeholder = blur,
                    modifier = Modifier.fillMaxSize()
                )
                if (last) Box(
                    Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+${layout.more}", color = androidx.compose.ui.graphics.Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Taller than 4:5 portrait or wider than 3:1 is trimmed; everything else shows whole. */
private const val MIN_RATIO = 0.8f
private const val MAX_RATIO = 3f
