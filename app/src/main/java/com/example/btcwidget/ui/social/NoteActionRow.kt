package com.example.btcwidget.ui.social

import com.example.btcwidget.ui.main.TextSize
import com.example.btcwidget.ui.main.Radius
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.nostr.NoteStats
import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.ui.main.EInk

/**
 * Reply · Repost (menu: repost or quote) · Like · Tip · Share, with counts. Liked is pink and
 * reposted green; tips are gold, a capsule once a post has some. Bookmark is in the ⋯ menu.
 */
@Composable
internal fun ActionRow(note: Event, stats: NoteStats, actions: NoteActions) {
    var repostMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionButton(Glyphs.Comment, stats.replies, "Reply", tint = null) { actions.reply(note) }
        Box {
            ActionButton(Glyphs.Repost, stats.reposts, if (stats.repostedByMe) "Reposted" else "Repost or quote", tint = ActionColors.Repost.takeIf { stats.repostedByMe }) { repostMenu = true }
            if (repostMenu) RepostSheet(note, stats, actions, onDismiss = { repostMenu = false })
        }
        Box {
            // Like only. An emoji reaction sent from another app still shows as what they sent.
            val emoji = stats.myReaction?.takeIf { stats.likedByMe && it != "+" && com.example.btcwidget.nostr.core.CustomEmoji.code(it) == null }
            val likeHaptics = rememberHaptics()
            ActionButton(
                if (stats.likedByMe) Glyphs.Liked else Glyphs.Like, stats.likes, if (stats.likedByMe) "Liked" else "Like",
                tint = ActionColors.Like.takeIf { stats.likedByMe }, emoji = emoji, pop = true
            ) { if (!stats.likedByMe) likeHaptics.tick(); actions.like(note) }
        }
        TipButton((stats.zapMsat / 1000).toInt()) { actions.zap(note) }
        ActionButton(Glyphs.Share, 0, "Share post", tint = null) { actions.share(note) }
    }
}

/** Tip: gold; "Tip" while there are none, then a gold capsule with the sats. */
@Composable
private fun TipButton(sats: Int, onClick: () -> Unit) {
    val gold = com.example.btcwidget.ui.main.ActionColor.Zap
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(Radius.Full)
            .combinedClickable(onClickLabel = tr("Tip"), role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp)
            .then(if (sats > 0) Modifier.clip(Radius.Full).background(gold.copy(alpha = 0.14f)) else Modifier)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Icon(Glyphs.Zap, contentDescription = tr("Tip"), tint = gold, modifier = Modifier.size(17.dp))
        Text(
            if (sats > 0) compactCount(sats) else tr("Tip"),
            color = gold, fontSize = TextSize.Caption, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

/** Colours that mark what you did: liked pink, reposted green (the same in every look). */
private object ActionColors {
    val Like get() = com.example.btcwidget.ui.main.ActionColor.Like
    val Repost get() = com.example.btcwidget.ui.main.ActionColor.Repost
}

/** An icon with its count; [tint] colours it when it's something you did. */
@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    label: String,
    tint: androidx.compose.ui.graphics.Color?,
    emoji: String? = null,
    /** Turning on (liking) pops: the icon springs up and a ring spreads out and fades. */
    pop: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val color = tint ?: EInk.Caption
    val on = tint != null
    val scale = remember { androidx.compose.animation.core.Animatable(1f) }
    val ring = remember { androidx.compose.animation.core.Animatable(1f) }
    // Only when it turns on here, not for posts that were already liked when they appeared.
    val wasOn = remember { booleanArrayOf(on) }
    androidx.compose.runtime.LaunchedEffect(on) {
        if (pop && on && !wasOn[0]) {
            kotlinx.coroutines.coroutineScope {
                launch {
                    scale.snapTo(0.6f)
                    scale.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.35f, stiffness = 500f))
                }
                launch {
                    ring.snapTo(0f)
                    ring.animateTo(1f, androidx.compose.animation.core.tween(450))
                }
            }
        }
        wasOn[0] = on
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(Radius.Full)
            .combinedClickable(
                onClickLabel = label, role = Role.Button, onClick = onClick,
                onLongClickLabel = if (onLongClick != null) "React with an emoji" else null, onLongClick = onLongClick
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (ring.value < 1f) Box(
                Modifier.size(18.dp).graphicsLayer {
                    val grow = 1f + ring.value * 0.9f
                    scaleX = grow; scaleY = grow; alpha = 1f - ring.value
                }.border(2.dp, color, CircleShape)
            )
            Box(Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }) {
                if (emoji != null) Text(emoji, fontSize = TextSize.Body)
                else Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(18.dp))
            }
        }
        if (count > 0) {
            // The count rolls up as it grows, down as it shrinks.
            androidx.compose.animation.AnimatedContent(
                targetState = count,
                transitionSpec = {
                    val up = targetState > initialState
                    (androidx.compose.animation.slideInVertically { if (up) it else -it } + androidx.compose.animation.fadeIn()) togetherWith
                        (androidx.compose.animation.slideOutVertically { if (up) -it else it } + androidx.compose.animation.fadeOut())
                },
                label = "count"
            ) { n ->
                Text(
                    text = compactCount(n),
                    color = color,
                    fontSize = TextSize.Caption,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(start = 5.dp)
                )
            }
        }
    }
}

/** Repost or quote, as a small sheet like X's. */
@Composable
internal fun RepostSheet(note: Event, stats: NoteStats, actions: NoteActions, onDismiss: () -> Unit, beforeQuote: () -> Unit = {}) {
    ActionSheet(onDismiss, listOf(
        SheetAction(Glyphs.Repost, if (stats.repostedByMe) tr("Reposted") else tr("Repost"), enabled = !stats.repostedByMe) { actions.repost(note) },
        SheetAction(Glyphs.Quote, tr("Quote")) { beforeQuote(); actions.quote(note) }
    ))
}

