package com.example.btcwidget.ui.social

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.theme.AppLook
import com.example.btcwidget.theme.LookColors
import com.example.btcwidget.ui.main.EInk
import com.example.btcwidget.ui.main.drawLookBackground

/**
 * Settings › Display › Theme: a card per look, each a small drawing of a timeline in that
 * look's own colours (so you see it before choosing). Tapping one fades the app into it.
 */
@Composable
internal fun LookPicker() {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val chosen = EInk.look
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppLook.entries.forEach { look ->
            LookCard(look, selected = look == chosen) {
                if (look != EInk.look) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    com.example.btcwidget.AppPrefs.setAppLook(context, look)
                    EInk.look = look
                }
            }
        }
    }
}

@Composable
private fun LookCard(look: AppLook, selected: Boolean, onClick: () -> Unit) {
    val c = look.colors
    val shape = RoundedCornerShape(18.dp)
    val ring by animateDpAsState(if (selected) 2.dp else 1.dp, label = "ring")
    val press by animateFloatAsState(if (selected) 1f else 0.97f, spring(dampingRatio = 0.6f), label = "scale")
    Column(
        Modifier
            .width(148.dp)
            .scale(press)
            .clip(shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = tr(look.title) + ". " + tr(look.summary)
            }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .clip(shape)
                .border(ring, if (selected) EInk.Accent else EInk.BodyStroke, shape)
        ) {
            Canvas(Modifier.fillMaxSize()) { drawMiniTimeline(c) }
            if (selected) Box(
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp).clip(CircleShape).background(Color(c.accent)),
                contentAlignment = Alignment.Center
            ) { Icon(Glyphs.Check, contentDescription = null, tint = Color(c.onAccent), modifier = Modifier.size(14.dp)) }
        }
        Text(
            tr(look.title), color = if (selected) EInk.Accent else EInk.Value,
            fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, start = 4.dp)
        )
        Text(tr(look.summary), color = EInk.Caption, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp))
    }
}

/**
 * A timeline in miniature: the look's background and glow, a top bar, three notes (avatar,
 * name and text lines, a liked heart in the accent), a card, and the round post button.
 */
private fun DrawScope.drawMiniTimeline(c: LookColors) {
    drawLookBackground(c, 1f, size)
    val w = size.width
    val u = w / 100f // one unit: 1% of the width
    val value = Color(c.value)
    val caption = Color(c.caption)
    val stroke = Color(c.bodyStroke)
    val accent = Color(c.accent)
    fun line(x: Float, y: Float, len: Float, color: Color, h: Float = 3.2f * u) =
        drawRoundRect(color, Offset(x, y), Size(len, h), CornerRadius(h / 2))

    // Top bar: title and a divider.
    line(8 * u, 9 * u, 30 * u, value, 4.2f * u)
    drawRect(stroke, Offset(0f, 19 * u), Size(w, 0.8f * u))

    // Notes.
    var y = 25 * u
    repeat(3) { i ->
        drawCircle(if (i == 1) accent.copy(alpha = 0.85f) else caption.copy(alpha = 0.55f), radius = 6 * u, center = Offset(14 * u, y + 6 * u))
        line(25 * u, y + 1 * u, 26 * u, value)
        line(53 * u, y + 1 * u, 12 * u, caption)
        line(25 * u, y + 7 * u, 64 * u, caption.copy(alpha = 0.8f))
        line(25 * u, y + 12.5f * u, 44 * u, caption.copy(alpha = 0.8f))
        // Actions: a reply bubble, and a heart, liked (accent) on the second note.
        drawCircle(caption.copy(alpha = 0.6f), radius = 1.6f * u, center = Offset(27 * u, y + 20 * u))
        drawHeart(Offset(44 * u, y + 20 * u), 2.6f * u, if (i == 1) accent else caption.copy(alpha = 0.6f))
        y += 27 * u
        drawRect(stroke, Offset(0f, y - 3 * u), Size(w, 0.6f * u))
    }

    // A card in the look's surface colour.
    drawRoundRect(Color(c.body), Offset(8 * u, y + 1 * u), Size(84 * u, 18 * u), CornerRadius(4 * u))
    line(13 * u, y + 6 * u, 40 * u, value)
    line(13 * u, y + 12 * u, 60 * u, caption)

    // The post button.
    drawCircle(accent, radius = 8.5f * u, center = Offset(w - 15 * u, size.height - 15 * u))
    val oc = Color(c.onAccent)
    drawRect(oc, Offset(w - 15 * u - 3.6f * u, size.height - 15 * u - 0.6f * u), Size(7.2f * u, 1.2f * u))
    drawRect(oc, Offset(w - 15 * u - 0.6f * u, size.height - 15 * u - 3.6f * u), Size(1.2f * u, 7.2f * u))
}

private fun DrawScope.drawHeart(center: Offset, r: Float, color: Color) {
    drawCircle(color, r * 0.55f, Offset(center.x - r * 0.45f, center.y - r * 0.2f))
    drawCircle(color, r * 0.55f, Offset(center.x + r * 0.45f, center.y - r * 0.2f))
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(center.x - r, center.y - r * 0.05f)
        lineTo(center.x + r, center.y - r * 0.05f)
        lineTo(center.x, center.y + r)
        close()
    }
    drawPath(path, color)
}
