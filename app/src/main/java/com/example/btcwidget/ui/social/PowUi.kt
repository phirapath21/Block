package com.example.btcwidget.ui.social

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.nostr.core.Pow
import com.example.btcwidget.ui.main.EInk
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// ---- Words and numbers ----

/** Hashes per second, short: "850 kH/s", "1.4 MH/s". */
fun powRateText(hashesPerSecond: Double): String = when {
    hashesPerSecond >= 1e9 -> "%.1f GH/s".format(hashesPerSecond / 1e9)
    hashesPerSecond >= 1e6 -> "%.1f MH/s".format(hashesPerSecond / 1e6)
    hashesPerSecond >= 1e3 -> "%.0f kH/s".format(hashesPerSecond / 1e3)
    else -> "%.0f H/s".format(hashesPerSecond)
}

/** A rough length of time: "under a second", "8 s", "3 min", "2 h", "4 days". */
fun powTimeText(seconds: Double): String = when {
    seconds < 1 -> tr("under a second")
    seconds < 90 -> tr("{0} s", seconds.roundToInt())
    seconds < 90 * 60 -> tr("{0} min", (seconds / 60).roundToInt())
    seconds < 48 * 3600 -> tr("{0} h", (seconds / 3600).roundToInt())
    else -> tr("{0} days", (seconds / 86_400).roundToLong())
}

// ---- The badge on notes ----

/**
 * A small "⛏ 16" pill for a note whose id proves [bits] of work. Shown from 8 bits up: less
 * than that happens by chance too often to mean much.
 */
@Composable
fun PowBadge(bits: Int, modifier: Modifier = Modifier) {
    if (bits < MIN_BADGE_BITS) return
    val strong = bits >= 20
    val tint = if (strong) EInk.Accent else EInk.Caption
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = if (strong) 0.14f else 0.10f))
            .padding(start = 5.dp, end = 6.dp, top = 1.dp, bottom = 1.dp)
            .clearAndSetSemantics { contentDescription = tr("Proof of work: {0} bits", bits) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Glyphs.Pickaxe, contentDescription = null, tint = tint, modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(3.dp))
        Text(bits.toString(), color = tint, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

const val MIN_BADGE_BITS = 8

// ---- While it mines ----

/**
 * The status card's middle while mining: a swinging pickaxe, the speed, how long it's been
 * against how long it usually takes, and Skip / Cancel.
 */
@Composable
internal fun MiningPanel(m: SocialViewModel.PublishActivity.Mining, onSkip: () -> Unit, onCancel: () -> Unit) {
    val swing by rememberInfiniteTransition(label = "pickaxe").animateFloat(
        initialValue = -28f, targetValue = 14f,
        animationSpec = infiniteRepeatable(tween(260, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "swing"
    )
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Glyphs.Pickaxe, contentDescription = null, tint = EInk.Accent,
                modifier = Modifier.size(16.dp).graphicsLayer {
                    rotationZ = swing
                    transformOrigin = TransformOrigin(0.2f, 0.8f)
                }
            )
            Text(
                tr("{0} bits", m.difficulty) + " · " + (if (m.hashesPerSecond > 0) powRateText(m.hashesPerSecond) else tr("starting…")),
                color = EInk.Value, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 8.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                powTimeText(m.elapsedMs / 1000.0) + if (m.hashesPerSecond > 0) " / ~" + powTimeText(m.expectedSeconds) else "",
                color = EInk.Caption, fontSize = 12.sp, maxLines = 1
            )
        }
        // Past twice the usual time, say it's just bad luck: there's no "almost done" in mining.
        if (m.hashesPerSecond > 0 && m.elapsedMs / 1000.0 > 2 * m.expectedSeconds && m.expectedSeconds >= 2) {
            Text(
                tr("Taking longer than usual: that's luck, every try has the same chance."),
                color = EInk.Caption, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            CardAction(tr("Cancel"), primary = false, onClick = onCancel)
            CardAction(tr("Post without it"), primary = true, onClick = onSkip)
        }
    }
}

@Composable
private fun CardAction(label: String, primary: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (primary) Color.Black else EInk.Value,
        fontSize = 13.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .then(if (primary) Modifier.background(EInk.Accent) else Modifier.border(1.dp, EInk.BodyStroke, RoundedCornerShape(50)))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    )
}
