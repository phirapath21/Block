package com.example.btcwidget.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Block's design system: the few sizes, spacings and shapes every screen uses, and the shared
 * pieces built from them (buttons, chips, rows, cards, empty states). Colours come from the
 * chosen look ([EInk]); these never change with it.
 */

/** Six text sizes. Body is X's 15; Display only for a screen's big hello. */
object TextSize {
    val Micro: TextUnit = 11.sp
    val Caption: TextUnit = 13.sp
    val Body: TextUnit = 15.sp
    val Subtitle: TextUnit = 17.sp
    val Title: TextUnit = 20.sp
    val Display: TextUnit = 28.sp
}

/** Line heights to match [TextSize]. */
object LineHeight {
    val Micro: TextUnit = 14.sp
    val Caption: TextUnit = 18.sp
    val Body: TextUnit = 20.sp
    val Subtitle: TextUnit = 22.sp
    val Title: TextUnit = 26.sp
    val Display: TextUnit = 34.sp
}

/** Spacing in steps of 4 dp: 16 at the screen's edge, 12 between rows, 24 between sections. */
object Space {
    val XS: Dp = 4.dp
    val S: Dp = 8.dp
    val M: Dp = 12.dp
    val L: Dp = 16.dp
    val XL: Dp = 20.dp
    val XXL: Dp = 24.dp
    val XXXL: Dp = 32.dp
}

/** Three corner shapes: small (fields, thumbnails, filter chips), large (cards, sheets, media), full (pills, avatars). */
object Radius {
    val Small = RoundedCornerShape(8.dp)
    val Large = RoundedCornerShape(16.dp)
    val Full = RoundedCornerShape(50)
}

/** What a button is for; one [Primary] per screen. */
enum class ButtonKind { Primary, Accent, Outline, Text, Danger }

/** Small (32 dp, in rows), Medium (40 dp) and Large (52 dp, full width). */
enum class ButtonSize(val height: Dp, val text: TextUnit, val padding: Dp) {
    Small(32.dp, 14.sp, 14.dp), Medium(40.dp, TextSize.Body, 18.dp), Large(52.dp, 16.sp, 20.dp)
}

/**
 * A pill button. [ButtonKind.Primary] is filled with the text colour (white on dark), as on X;
 * [ButtonKind.Accent] with the look's accent; [ButtonKind.Outline] is for second choices;
 * [ButtonKind.Text] for Cancel; [ButtonKind.Danger] for deleting.
 */
@Composable
fun BlockButton(
    text: String,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    size: ButtonSize = ButtonSize.Medium,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    val bg = when (kind) {
        ButtonKind.Primary -> EInk.Value
        ButtonKind.Accent -> EInk.Accent
        ButtonKind.Danger -> EInk.Alert
        ButtonKind.Outline, ButtonKind.Text -> Color.Transparent
    }
    val fg = when (kind) {
        ButtonKind.Primary -> EInk.Background
        ButtonKind.Accent -> EInk.OnAccent
        ButtonKind.Danger -> Color.White
        ButtonKind.Outline, ButtonKind.Text -> EInk.Value
    }
    Row(
        modifier
            .heightIn(min = size.height)
            .clip(Radius.Full)
            .background(bg.copy(alpha = if (enabled) bg.alpha else bg.alpha * 0.4f))
            .then(if (kind == ButtonKind.Outline) Modifier.border(1.dp, EInk.BodyStroke, Radius.Full) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = text, onClick = onClick)
            .padding(horizontal = if (kind == ButtonKind.Text) Space.S else size.padding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val color = if (enabled) fg else fg.copy(alpha = 0.5f)
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.S))
        }
        Text(text, color = color, fontSize = size.text, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center)
    }
}

/** A [BlockButton] across the whole width, 52 dp tall: a screen's main action. */
@Composable
fun BlockButtonFull(text: String, kind: ButtonKind = ButtonKind.Primary, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) =
    BlockButton(text, modifier.fillMaxWidth(), kind, ButtonSize.Large, enabled, onClick = onClick)

/**
 * A chip. [filter] chips (a page's own filters and tabs-as-chips) are small-cornered and fill
 * with the accent when on; choice chips are pills that fill with the text colour, with ✓.
 */
@Composable
fun BlockChip(text: String, selected: Boolean, modifier: Modifier = Modifier, filter: Boolean = true, onClick: () -> Unit) {
    val shape = if (filter) Radius.Small else Radius.Full
    val bg = when {
        filter && selected -> EInk.Accent
        filter -> EInk.Body
        selected -> EInk.Value
        else -> Color.Transparent
    }
    val fg = when {
        filter && selected -> EInk.OnAccent
        filter -> EInk.Label
        selected -> EInk.Background
        else -> EInk.Value
    }
    Box(
        modifier
            .clip(shape)
            .background(bg, shape)
            .then(if (!filter) Modifier.border(1.dp, if (selected) EInk.Value else EInk.BodyStroke, shape) else Modifier)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = if (filter) Space.M else 14.dp, vertical = if (filter) 6.dp else 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            (if (!filter && selected) "✓ " else "") + text, color = fg, fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold, maxLines = 1, softWrap = false
        )
    }
}

/** A card: the surface colour, a hairline edge, large corners. */
@Composable
fun BlockCard(modifier: Modifier = Modifier, padding: Dp = 14.dp, content: @Composable () -> Unit) {
    Box(
        modifier.clip(Radius.Large).background(EInk.Body).border(1.dp, EInk.BodyStroke, Radius.Large).padding(padding)
    ) { content() }
}

/**
 * One row of a list: an optional leading icon or picture, a title with an optional line under
 * it, and something on the right (a chevron, a switch, a button).
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = title, onClick = onClick) else Modifier)
            .padding(horizontal = Space.L, vertical = Space.M),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(14.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, color = EInk.Value, fontSize = TextSize.Body, lineHeight = LineHeight.Body)
            if (subtitle != null) Text(subtitle, color = EInk.Caption, fontSize = TextSize.Caption, lineHeight = LineHeight.Caption, modifier = Modifier.padding(top = 2.dp))
        }
        if (trailing != null) { Spacer(Modifier.width(Space.M)); trailing() }
    }
}

/** An empty page: an emoji, what's missing, what it'll show, and maybe a way forward. */
@Composable
fun EmptyState(emoji: String, title: String, body: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = Space.XXXL, vertical = Space.XXXL),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 36.sp)
        Text(title, color = EInk.Value, fontSize = TextSize.Title, lineHeight = LineHeight.Title, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Space.M))
        Text(body, color = EInk.Caption, fontSize = TextSize.Body, lineHeight = LineHeight.Body, textAlign = TextAlign.Center, modifier = Modifier.padding(top = Space.XS))
        if (action != null && onAction != null) {
            Spacer(Modifier.height(Space.L))
            BlockButton(action, kind = ButtonKind.Outline, onClick = onAction)
        }
    }
}

/** A grey bar where text will be, while it loads. */
@Composable
fun SkeletonLine(width: Dp, height: Dp = 12.dp, modifier: Modifier = Modifier) {
    Box(modifier.width(width).height(height).clip(RoundedCornerShape(6.dp)).background(EInk.Track))
}

/** Colours of the actions on a post, the same in every look. */
object ActionColor {
    val Like = Color(0xFFF91880)
    val Repost = Color(0xFF00BA7C)
    val Zap = Color(0xFFFFB300)
}
