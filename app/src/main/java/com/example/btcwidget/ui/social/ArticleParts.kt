package com.example.btcwidget.ui.social

import com.example.btcwidget.ui.main.TextSize
import com.example.btcwidget.ui.main.Radius
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.example.btcwidget.ui.main.screenBackground
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import coil3.compose.AsyncImage
import com.example.btcwidget.LoginKey
import com.example.btcwidget.nostr.core.Article
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.ui.main.EInk

@Composable
internal fun ReaderSettingsBar(
    prefs: ReaderPrefs,
    onFontSizeChange: (Int) -> Unit,
    onSerifChange: (Boolean) -> Unit,
    onLeadingChange: (Boolean) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(EInk.Body)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ReaderChip(
                    label = tr("Sans"),
                    selected = !prefs.useSerif,
                    fontFamily = FontFamily.SansSerif,
                    onClick = { onSerifChange(false) }
                )
                ReaderChip(
                    label = tr("Serif"),
                    selected = prefs.useSerif,
                    fontFamily = FontFamily.Serif,
                    onClick = { onSerifChange(true) }
                )
                ReaderChip(
                    label = if (prefs.relaxedLeading) tr("Relaxed") else tr("Compact"),
                    selected = prefs.relaxedLeading,
                    fontFamily = FontFamily.SansSerif,
                    onClick = { onLeadingChange(!prefs.relaxedLeading) }
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    Modifier
                        .clip(Radius.Full)
                        .screenBackground()
                        .border(0.5.dp, EInk.BodyStroke, Radius.Full)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) { onFontSizeChange(prefs.fontSizeSp - 2) }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("A−", color = EInk.Value, fontSize = TextSize.Caption, fontWeight = FontWeight.Bold)
                }
                Text(
                    "${prefs.fontSizeSp}",
                    color = EInk.Accent,
                    fontSize = TextSize.Caption,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    Modifier
                        .clip(Radius.Full)
                        .screenBackground()
                        .border(0.5.dp, EInk.BodyStroke, Radius.Full)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) { onFontSizeChange(prefs.fontSizeSp + 2) }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("A+", color = EInk.Value, fontSize = TextSize.Caption, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
}

@Composable
internal fun ReaderChip(
    label: String,
    selected: Boolean,
    fontFamily: FontFamily,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .clip(Radius.Full)
            .background(if (selected) EInk.Accent else EInk.Background)
            .border(0.5.dp, if (selected) EInk.Accent else EInk.BodyStroke, Radius.Full)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = if (selected) Color.Black else EInk.Value,
            fontSize = TextSize.Caption,
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Sticky bottom audio player bar for Text-to-Speech article listening. */
@Composable
internal fun ArticleAudioPlayerBar(
    isPlaying: Boolean,
    currentIndex: Int,
    totalCount: Int,
    speed: Float,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onToggleSpeed: () -> Unit,
    onStop: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(EInk.Body)
            .navigationBarsPadding()
    ) {
        HorizontalDivider(thickness = 0.5.dp, color = EInk.Accent.copy(alpha = 0.5f))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Glyphs.SoundOn, contentDescription = null, tint = EInk.Accent, modifier = Modifier.size(18.dp))
                Text(
                    tr("Paragraph {0}/{1}", currentIndex, totalCount.coerceAtLeast(1)),
                    color = EInk.Value,
                    fontSize = TextSize.Caption,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .clip(Radius.Full)
                        .screenBackground()
                        .border(0.5.dp, EInk.BodyStroke, Radius.Full)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onPrev)
                        .semantics { contentDescription = tr("Previous paragraph") }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("⏮", color = EInk.Value, fontSize = TextSize.Caption)
                }
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(EInk.Accent)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onPlayPause),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPlaying) Glyphs.Pause else Glyphs.Play,
                        contentDescription = if (isPlaying) tr("Pause") else tr("Play"),
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Box(
                    Modifier
                        .clip(Radius.Full)
                        .screenBackground()
                        .border(0.5.dp, EInk.BodyStroke, Radius.Full)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onNext)
                        .semantics { contentDescription = tr("Next paragraph") }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text("⏭", color = EInk.Value, fontSize = TextSize.Caption)
                }
                Box(
                    Modifier
                        .clip(Radius.Full)
                        .screenBackground()
                        .border(0.5.dp, EInk.BodyStroke, Radius.Full)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onToggleSpeed)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        "${speed}x",
                        color = EInk.Accent,
                        fontSize = TextSize.Caption,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .screenBackground()
                        .border(0.5.dp, EInk.BodyStroke, CircleShape)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onStop),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Glyphs.Close, contentDescription = tr("Stop"), tint = EInk.Caption, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

/** Phase 5: Value-for-Value Quick Zap & Author Support Card at Article Footer. */
@Composable
internal fun AuthorSupportFooterCard(
    vm: SocialViewModel,
    article: Article,
    author: Profile?,
    isFollowing: Boolean,
    actions: NoteActions,
    navigate: (NavKey) -> Unit
) {
    val context = LocalContext.current
    val account by vm.account.collectAsState()
    val presets = listOf(21L, 100L, 500L, 1000L)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 18.dp)
            .clip(Radius.Large)
            .background(EInk.Body)
            .border(0.5.dp, EInk.BodyStroke, Radius.Large)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(author, article.pubkey, size = 44, onClick = { actions.openProfile(article.pubkey) })
            Column(Modifier.weight(1f).padding(start = 12.dp).clickable(role = androidx.compose.ui.semantics.Role.Button) { actions.openProfile(article.pubkey) }) {
                Text(
                    tr("Written by"),
                    color = EInk.Caption,
                    fontSize = TextSize.Micro
                )
                Text(
                    author?.bestName ?: Profile.shortNpub(article.pubkey),
                    color = EInk.Value,
                    fontSize = TextSize.Body,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (account?.pubkey != article.pubkey) {
                ChoicePill(
                    label = if (isFollowing) tr("Following") else tr("Follow"),
                    selected = !isFollowing
                ) {
                    if (account == null) navigate(LoginKey)
                    else actions.follow(article.pubkey, !isFollowing)
                }
            }
        }
        author?.about?.takeIf { it.isNotBlank() }?.let { bio ->
            Text(
                bio,
                color = EInk.Label,
                fontSize = TextSize.Caption,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke, modifier = Modifier.padding(vertical = 12.dp))
        Text(
            tr("Enjoyed this article? Value-for-Value zap the author:"),
            color = EInk.Value,
            fontSize = TextSize.Caption,
            fontWeight = FontWeight.Bold
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { sats ->
                Box(
                    Modifier
                        .clip(Radius.Full)
                        .background(EInk.Accent.copy(alpha = 0.14f))
                        .border(1.dp, EInk.Accent, Radius.Full)
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                            if (account == null) {
                                navigate(LoginKey)
                            } else {
                                vm.zap(
                                    recipient = article.pubkey,
                                    amountSat = sats,
                                    note = article.event,
                                    comment = "⚡ ${article.title}",
                                    address = author?.lud16
                                ) { invoice ->
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("lightning:$invoice"))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    }
                                }
                            }
                        }
                        .padding(horizontal = 13.dp, vertical = 7.dp)
                ) {
                    Text(
                        "⚡ $sats",
                        color = EInk.Accent,
                        fontSize = TextSize.Caption,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Box(
                Modifier
                    .clip(Radius.Full)
                    .screenBackground()
                    .border(0.5.dp, EInk.BodyStroke, Radius.Full)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { actions.zap(article.event) }
                    .padding(horizontal = 13.dp, vertical = 7.dp)
            ) {
                Text(
                    tr("Custom ⚡"),
                    color = EInk.Value,
                    fontSize = TextSize.Caption,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** An article in a list: cover, title, summary, date,reading time, and saved reading progress badge. */
@Composable
internal fun ArticleRow(article: Article, onClick: () -> Unit) {
    val context = LocalContext.current
    val savedPct = remember(article.address) { ArticleReadingStore.savedPercent(context, article.address) }
    val readingMinutes = remember(article.event.id) {
        val words = article.content.split(Regex("\\s+")).count { it.isNotBlank() }
        maxOf(1, (words + 120) / 200)
    }
    Row(Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(article.title, color = EInk.Value, fontSize = TextSize.Body, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            article.summary?.let { Text(it, color = EInk.Label, fontSize = TextSize.Body, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 6.dp)
            ) {
                Text(
                    relativeTime(article.publishedAt) + " · " + tr("{0} min read", readingMinutes),
                    color = EInk.Caption,
                    fontSize = TextSize.Caption
                )
                if (savedPct > 0) {
                    Box(
                        Modifier
                            .clip(Radius.Full)
                            .background(EInk.Accent.copy(alpha = 0.14f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            if (savedPct >= 95) tr("✓ Read") else tr("{0}% read", savedPct),
                            color = EInk.Accent,
                            fontSize = TextSize.Micro,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        article.image?.let { url ->
            Spacer(Modifier.width(10.dp))
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.width(72.dp).heightIn(min = 72.dp, max = 72.dp).clip(Radius.Small))
        }
    }
    HorizontalDivider(thickness = 0.5.dp, color = EInk.BodyStroke)
}
