package com.example.btcwidget.ui.social

import com.example.btcwidget.ui.main.TextSize
import com.example.btcwidget.ui.main.Radius
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import com.example.btcwidget.PackKey
import com.example.btcwidget.SearchKey
import com.example.btcwidget.nostr.core.FollowPack
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.ui.main.EInk

// ---- Loading: the shape of notes, not a spinner ----

/**
 * Placeholder notes while a feed loads: avatar, name, a few lines and the action row, in the
 * track colour with a soft light sweeping across, so the screen already looks like the feed it
 * will be. Read out once as "Loading".
 */
@Composable
fun FeedSkeleton(modifier: Modifier = Modifier, rows: Int = 6) {
    val sweep by rememberInfiniteTransition(label = "skeleton").animateFloat(
        -1f, 2f, infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "sweep"
    )
    val base = EInk.Track.copy(alpha = 0.55f)
    val light = EInk.Track.copy(alpha = 1f)
    Column(modifier.fillMaxWidth().semantics { contentDescription = tr("Loading") }) {
        repeat(rows) { i ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                Bone(Modifier.size(44.dp).clip(CircleShape), sweep, base, light)
                Column(Modifier.weight(1f).padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Bone(Modifier.width((96 + 24 * (i % 3)).dp).height(12.dp).clip(Radius.Full), sweep, base, light)
                        Spacer(Modifier.width(8.dp))
                        Bone(Modifier.width(36.dp).height(10.dp).clip(Radius.Full), sweep, base, light)
                    }
                    // Three, two or four lines, so the column doesn't look stamped out.
                    val lines = intArrayOf(3, 2, 4, 2, 3, 1)[i % 6]
                    repeat(lines) { l ->
                        Bone(
                            Modifier.fillMaxWidth(if (l == lines - 1) 0.6f else 1f).height(11.dp).clip(Radius.Full),
                            sweep, base, light
                        )
                    }
                    if (i % 3 == 1) Bone(Modifier.fillMaxWidth().height(150.dp).clip(Radius.Large), sweep, base, light)
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(0.85f).padding(top = 2.dp)) {
                        repeat(4) { Bone(Modifier.size(width = 22.dp, height = 10.dp).clip(Radius.Full), sweep, base, light) }
                    }
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
        }
    }
}

/** One placeholder shape with the shared sweep (−1 → 2 across the width) running over it. */
@Composable
private fun Bone(modifier: Modifier, sweep: Float, base: Color, light: Color) {
    Box(
        modifier.drawWithCache {
            onDrawBehind {
                drawRect(base)
                val w = size.width.coerceAtLeast(1f)
                val x = sweep * w
                drawRect(Brush.linearGradient(listOf(Color.Transparent, light, Color.Transparent), Offset(x - w * 0.6f, 0f), Offset(x, 0f)))
            }
        }
    )
}

// ---- A new account's empty timeline ----

/**
 * What a new account sees instead of an empty timeline: a welcome, follow packs to follow
 * in one tap (people someone on Nostr recommends), and a way to search for people. Once
 * anyone is followed, the timeline fills on the next refresh.
 */
@Composable
fun StarterFollows(vm: SocialViewModel, navigate: (NavKey) -> Unit, modifier: Modifier = Modifier) {
    val profiles by vm.profiles.collectAsState()
    val following by vm.following.collectAsState()
    val packs by produceKept<List<FollowPack>?>(null) { value = vm.followPacks() }
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 8.dp)) {
                Box(
                    Modifier.size(52.dp).clip(Radius.Large).background(EInk.Accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Glyphs.Following, contentDescription = null, tint = EInk.Accent, modifier = Modifier.size(28.dp)) }
                Text(
                    tr("Your timeline starts with who you follow"), color = EInk.Value, fontSize = TextSize.Title, fontWeight = FontWeight.Bold,
                    lineHeight = 28.sp, modifier = Modifier.padding(top = 14.dp)
                )
                Text(
                    tr("Follow a pack to get going: people someone on Nostr recommends, all at once. You can unfollow anyone later."),
                    color = EInk.Label, fontSize = TextSize.Body, lineHeight = 21.sp, modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
        item {
            when (val l = packs) {
                null -> LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(3) { PackCardSkeleton() }
                }
                else -> if (l.isEmpty()) Text(
                    tr("No follow packs found on your relays. Search for people you know instead."),
                    color = EInk.Caption, fontSize = TextSize.Body, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                ) else LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Bigger packs first: more of a timeline from one tap.
                    items(l.sortedByDescending { it.members.size }.take(12), key = { it.address }) { pack ->
                        PackCard(
                            pack, profiles,
                            followed = pack.members.isNotEmpty() && pack.members.all { it in following },
                            onOpen = { navigate(PackKey(pack.address)) },
                            onFollowAll = { vm.followAll(pack.members.filter { it !in following }) }
                        )
                    }
                }
            }
        }
        item {
            Row(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(Radius.Full)
                    .border(1.dp, EInk.BodyStroke, Radius.Full)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { navigate(SearchKey) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Glyphs.Search, contentDescription = null, tint = EInk.Value, modifier = Modifier.size(18.dp))
                Text(tr("Find people you know"), color = EInk.Value, fontSize = TextSize.Body, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            }
            if (following.isNotEmpty()) Text(
                tr("Following {0}. Pull down to refresh your timeline.", following.size),
                color = EInk.Caption, fontSize = TextSize.Caption, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            )
        }
    }
}

private val PackCardWidth: Dp = 236.dp

@Composable
private fun PackCard(pack: FollowPack, profiles: Map<String, Profile>, followed: Boolean, onOpen: () -> Unit, onFollowAll: () -> Unit) {
    val shape = Radius.Large
    var tapped by remember(pack.address) { mutableStateOf(false) }
    val done = followed || tapped
    Column(
        Modifier
            .width(PackCardWidth)
            .clip(shape)
            .background(com.example.btcwidget.ui.main.CardColors.Fill)
            .border(0.5.dp, EInk.BodyStroke, shape)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = tr("See who's in it"), onClick = onOpen)
            .padding(14.dp)
    ) {
        // Faces, overlapping, with a ring of the card's colour so they read as separate.
        Box(Modifier.height(44.dp).fillMaxWidth()) {
            pack.members.take(5).forEachIndexed { i, pk ->
                Box(Modifier.padding(start = (i * 26).dp).clip(CircleShape).border(2.dp, EInk.Body, CircleShape)) { Avatar(profiles[pk], pk, size = 40) }
            }
        }
        Text(pack.title, color = EInk.Value, fontSize = TextSize.Body, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
        Text(
            tr("{0} people · by ", pack.members.size) + (profiles[pack.owner]?.bestName ?: Profile.shortNpub(pack.owner)),
            color = EInk.Caption, fontSize = TextSize.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Text(
            pack.description.orEmpty(), color = EInk.Label, fontSize = TextSize.Caption, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp).heightIn(min = 36.dp)
        )
        Box(
            Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .clip(Radius.Full)
                .background(if (done) Color.Transparent else EInk.Accent)
                .border(1.dp, if (done) EInk.BodyStroke else Color.Transparent, Radius.Full)
                .clickable(role = androidx.compose.ui.semantics.Role.Button, enabled = !done) { tapped = true; onFollowAll() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (done) tr("Following") else tr("Follow all {0}", pack.members.size),
                color = if (done) EInk.Value else EInk.OnAccent, fontSize = TextSize.Body, fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PackCardSkeleton() {
    Box(
        Modifier.width(PackCardWidth).height(206.dp).clip(Radius.Large).background(EInk.Body)
            .border(0.5.dp, EInk.BodyStroke, Radius.Large)
    )
}
