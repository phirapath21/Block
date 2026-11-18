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

/** "Light", "Medium"… for [bits] of proof of work. */
fun powLevelName(bits: Int): String = tr(
    when {
        bits <= 0 -> "Off"
        bits <= 10 -> "Light"
        bits <= 17 -> "Medium"
        bits <= 21 -> "Strong"
        bits <= 25 -> "Heavy"
        else -> "Extreme"
    }
)

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

/** "1.2M", "65K": tries so far. */
private fun countText(n: Long): String = when {
    n >= 1_000_000_000 -> "%.1fB".format(n / 1e9)
    n >= 1_000_000 -> "%.1fM".format(n / 1e6)
    n >= 1_000 -> "%.0fK".format(n / 1e3)
    else -> n.toString()
}

/** "≈ 2 s on this phone", or how many tries it takes before the speed is known. */
private fun estimateText(bits: Int, rate: Double): String = when {
    bits <= 0 -> tr("Posts are sent right away")
    rate > 0 -> tr("≈ {0} per post on this phone", powTimeText(Pow.expectedSeconds(bits, rate)))
    else -> tr("≈ {0} tries per post", countText(Pow.expectedHashes(bits).toLong()))
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

// ---- Choosing a difficulty ----

/**
 * The levels with how long each takes here: a bar per level on a log scale (each step is
 * 16× the work), the chosen one highlighted. Tapping a row picks it.
 */
@Composable
private fun PowLevels(selected: Int, rate: Double, onPick: (Int) -> Unit) {
    val top = Pow.CHOICES.max().toFloat()
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Pow.CHOICES.forEach { bits ->
            val isOn = bits == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isOn) EInk.Accent.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onPick(bits) }
                    .semantics { this.selected = isOn }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.width(96.dp)) {
                    Text(
                        if (bits == 0) tr("Off") else tr("{0} bits", bits),
                        color = if (isOn) EInk.Accent else EInk.Value, fontSize = 15.sp, fontWeight = if (isOn) FontWeight.Bold else FontWeight.Medium
                    )
                    Text(powLevelName(bits), color = EInk.Caption, fontSize = 12.sp)
                }
                // Work grows 2× per bit; the bar grows evenly per bit, so it reads as "harder".
                Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(50)).background(EInk.Track)) {
                    if (bits > 0) Box(
                        Modifier.fillMaxWidth(bits / top).fillMaxHeight().clip(RoundedCornerShape(50))
                            .background(if (isOn) EInk.Accent else EInk.Caption.copy(alpha = 0.55f))
                    )
                }
                Text(
                    when {
                        bits == 0 -> "—"
                        rate > 0 -> powTimeText(Pow.expectedSeconds(bits, rate))
                        else -> countText(Pow.expectedHashes(bits).toLong())
                    },
                    color = if (isOn) EInk.Value else EInk.Caption, fontSize = 13.sp,
                    modifier = Modifier.width(92.dp).padding(start = 12.dp), maxLines = 1
                )
            }
        }
    }
}

/** Settings → Proof of work. */
@Composable
internal fun PowSettingsPage(vm: SocialViewModel) {
    val bits by vm.pow.difficulty.collectAsState()
    val rate by vm.pow.hashRate.collectAsState()
    val measuring by vm.pow.benchmarking.collectAsState()
    val match by vm.pow.matchRelays.collectAsState()
    val relayPow by vm.pow.relayPow.collectAsState()
    LaunchedEffect(Unit) {
        vm.pow.benchmark()
        vm.pow.checkRelayPow()
    }
    // The slider moves freely; the setting is saved when it's let go.
    var dragging by remember { mutableFloatStateOf(-1f) }
    val shown = if (dragging >= 0) dragging.roundToInt() else bits

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp)) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(22.dp),
            color = EInk.Body,
            border = androidx.compose.foundation.BorderStroke(1.dp, EInk.BodyStroke)
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(15.dp))
                            .background(if (shown > 0) EInk.Accent.copy(alpha = .14f) else EInk.Track),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Glyphs.Pickaxe, contentDescription = null, tint = if (shown > 0) EInk.Accent else EInk.Caption, modifier = Modifier.size(26.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(
                            if (shown == 0) tr("Off") else tr("{0} bits", shown) + " · " + powLevelName(shown),
                            color = EInk.Value, fontSize = 20.sp, fontWeight = FontWeight.Bold
                        )
                        Text(estimateText(shown, rate), color = if (shown > 0) EInk.Accent else EInk.Caption, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Slider(
                    value = shown.toFloat(),
                    onValueChange = { dragging = it },
                    onValueChangeFinished = { vm.pow.setDifficulty(dragging.roundToInt()); dragging = -1f },
                    valueRange = 0f..Pow.CHOICES.max().toFloat(),
                    steps = Pow.CHOICES.max() - 1,
                    colors = SliderDefaults.colors(
                        thumbColor = EInk.Accent, activeTrackColor = EInk.Accent, inactiveTrackColor = EInk.Track,
                        activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent
                    ),
                    modifier = Modifier.semantics { contentDescription = tr("Proof of work difficulty") }
                )
                Text(
                    tr("Before a post is signed, your phone tries numbers until its ID starts with enough zero bits (NIP-13). Readers and relays can see the post took real work, so it's less likely to be treated as spam. It costs nothing but time and battery."),
                    color = EInk.Caption, fontSize = 13.sp, lineHeight = 18.sp
                )
            }
        }
    }

    SettingsHeader(tr("How long it takes here"))
    PowLevels(selected = shown, rate = rate) { vm.pow.setDifficulty(it) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (rate > 0) tr("This phone: {0} on {1} cores", powRateText(rate), Pow.defaultThreads())
            else if (measuring) tr("Measuring this phone…") else tr("Not measured yet"),
            color = EInk.Caption, fontSize = 13.sp, modifier = Modifier.weight(1f)
        )
        if (measuring) CircularProgressIndicator(color = EInk.Accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        else SettingsButton(tr("Measure again")) { vm.pow.benchmark(force = true) }
    }

    SettingsDivider()
    SettingsSwitchRow(
        tr("Match what my relays ask for"),
        summary = tr("Some relays only take posts with proof of work. Block mines at least that much, up to 24 bits, even when this is off for you.") +
            (relayPow.second?.let { "\n" + tr("{0} asks for {1} bits.", it.removePrefix("wss://").removeSuffix("/"), relayPow.first) } ?: ""),
        checked = match
    ) { vm.pow.setMatchRelays(it) }
    SettingsNote(tr("Used for notes, replies and quotes. Likes, reposts, messages and lists go out at once. You can change it for one post in the composer, or skip the work while it runs. Mining uses all but one core: long ones warm the phone."))
}

/** The composer's sheet: proof of work for this post only. */
@Composable
internal fun PowPostSheet(selected: Int, default: Int, rate: Double, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    BlockSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Glyphs.Pickaxe, contentDescription = null, tint = EInk.Accent, modifier = Modifier.size(22.dp))
                Text(tr("Proof of work for this post"), color = EInk.Value, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
            }
            Text(
                tr("More bits take longer but stand out more to relays and readers that filter spam. Your default is {0}.", if (default == 0) tr("Off") else tr("{0} bits", default)),
                color = EInk.Caption, fontSize = 13.sp, lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)
            )
            PowLevels(selected = selected, rate = rate) { onPick(it); onDismiss() }
        }
    }
}

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
