package com.example.btcwidget.ui.social

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.btcwidget.nostr.core.DmFile
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.ui.main.EInk
import com.example.btcwidget.ui.main.screenBackground
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ChatTopBar(
    title: String,
    peers: List<String>,
    profiles: Map<String, Profile>,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val isGroup = peers.size > 1
    val primaryPeer = peers.first()
    val primaryProfile = profiles[primaryPeer]
    Column(Modifier.fillMaxWidth().screenBackground().statusBarsPadding()) {
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
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Back"), onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Glyphs.Back, contentDescription = tr("Back"), tint = EInk.Value, modifier = Modifier.size(22.dp))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onOpenProfile)
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

/**
 * The name shown for an attachment and suggested when saving it: the sender's name made safe
 * (no folders), else one made up from its kind and hash. Never used as a path on the phone:
 * decrypted copies live in [com.example.btcwidget.nostr.DmMediaCache], named by their own hash.
 */
internal fun defaultAttachmentName(info: DmFile.Info): String {
    com.example.btcwidget.nostr.DmMediaCache.safeDisplayName(info.fileName)?.let { return it }
    val ext = com.example.btcwidget.nostr.DmMediaCache.extensionFor(info.mime)
    val prefix = when {
        info.isImage -> "photo"
        info.isVideo -> "video"
        info.isAudio -> "audio"
        else -> "file"
    }
    val shortHash = info.sha256?.filter { it.isLetterOrDigit() }?.take(8)?.ifEmpty { null } ?: "nip17"
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
internal fun EncryptedAttachment(
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
                        if (bitmap != null) Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button) { showFullscreen = true } else Modifier
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
                                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Save to device")) {
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
                            .clickable(role = androidx.compose.ui.semantics.Role.Button) { showFullscreen = false },
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
                                        .clickable(role = androidx.compose.ui.semantics.Role.Button) { onSaveFile(raw, defaultAttachmentName(info)) },
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
                                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { showFullscreen = false },
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
                        val file = com.example.btcwidget.nostr.DmMediaCache.fileFor(context, info, b)
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
                                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Play video")) { playing = true },
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
                                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Save to device")) {
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
            // Only audio needs a player; documents get none.
            val mediaPlayer = remember(info.isAudio) { if (info.isAudio) android.media.MediaPlayer() else null }
            DisposableEffect(mediaPlayer) {
                onDispose {
                    runCatching {
                        if (mediaPlayer?.isPlaying == true) mediaPlayer?.stop()
                        mediaPlayer?.release()
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
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, enabled = decryptedBytes != null) {
                            val raw = decryptedBytes ?: return@clickable
                            if (info.isAudio) {
                                if (isPlayingAudio) {
                                    runCatching { mediaPlayer?.pause() }
                                    isPlayingAudio = false
                                } else {
                                    scope.launch(Dispatchers.IO) {
                                        runCatching {
                                            val f = com.example.btcwidget.nostr.DmMediaCache.fileFor(context, info, raw)
                                            val player = mediaPlayer ?: return@runCatching
                                            withContext(Dispatchers.Main) {
                                                player.reset()
                                                player.setDataSource(f.path)
                                                player.setOnCompletionListener { isPlayingAudio = false }
                                                player.prepare()
                                                player.start()
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
                            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("Save to device")) {
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
internal fun Modifier.swipeToReply(onReply: () -> Unit): Modifier = composed {
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
