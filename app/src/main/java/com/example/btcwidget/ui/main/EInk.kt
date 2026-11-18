package com.example.btcwidget.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btcwidget.AndroidDrawSurface
import com.example.btcwidget.AppTheme
import com.example.btcwidget.BlockclockDisplay
import com.example.btcwidget.BlockclockPainter
import com.example.btcwidget.BlockclockScene
import java.util.Locale

/**
 * App palette. The app's own colours (Nostr screens, menus, settings) come from the chosen
 * [look] ([com.example.btcwidget.theme.AppLook]); the widget [theme] only changes how
 * blockclock panels are drawn: the home-screen widgets, their previews in the app
 * ([EInkDevice]) and share cards. Both are snapshot state, so screens redraw when they change;
 * a new look fades in over [LOOK_FADE_MS] ([lookProgress] runs 0 → 1, see [AnimateLook]).
 */
object EInk {
    /** The widget theme. Not used for the app's own colours. */
    var theme: AppTheme by mutableStateOf(AppTheme.CLASSIC)

    /** The app's colour theme. Setting it starts a fade from the colours on screen now. */
    var look: com.example.btcwidget.theme.AppLook
        get() = target
        set(value) {
            if (value == target) return
            from = current
            target = value
            lookProgress = if (animateLooks && fadeLooks) 0f else 1f
        }
    private var target by mutableStateOf(com.example.btcwidget.theme.AppLook.DEFAULT)
    private var from by mutableStateOf(com.example.btcwidget.theme.AppLook.DEFAULT.colors)
    /** 0 → 1 while a new [look] fades in. */
    var lookProgress by mutableFloatStateOf(1f)
    /** Off until the UI is up, so the saved look applies at once when the app starts. */
    var animateLooks = false
    /** False switches looks at once (UI tests, which check the result rather than the fade). */
    var fadeLooks = true

    /** The colours on screen now (mid-fade while changing look). */
    val current: com.example.btcwidget.theme.LookColors
        get() = com.example.btcwidget.theme.lerpLook(from, target.colors, lookProgress)
    /** How strongly the look's glow shows (fades with the look). */
    val glowAlpha: Float get() = com.example.btcwidget.theme.glowStrength(from, target.colors, lookProgress)

    private val ui get() = current
    val Background get() = Color(ui.background)
    val Body get() = Color(ui.body)
    val BodyStroke get() = Color(ui.bodyStroke)
    val Value get() = Color(ui.value)
    val Label get() = Color(ui.label)
    val Caption get() = Color(ui.caption)
    val Track get() = Color(ui.track)
    val Alert get() = Color(ui.alert)
    val Accent get() = Color(ui.accent)
    /** Text and icons on [Accent]. */
    val OnAccent get() = Color(ui.onAccent)
    /** Bottom sheets, menus and dialogs. */
    val Sheet get() = Color(ui.sheet)
    val Success get() = Color(ui.success)

    /** Face for screen titles (unified X-style sans-serif across the app). */
    val TitleFont: FontFamily get() = FontFamily.Default

    val BodyShape = RoundedCornerShape(16.dp)
    val ChipShape = RoundedCornerShape(50)
}

/**
 * A blockclock panel drawn by [BlockclockPainter], the same code that paints the
 * home-screen widgets, so the app and widgets always match.
 *
 * @param aspectRatio width / height of the panel.
 */
@Composable
fun EInkDevice(
    display: BlockclockDisplay,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 2f,
    stale: Boolean = false,
    progress: Float? = null
) {
    val context = LocalContext.current
    val condensed = remember { AndroidDrawSurface.condensedTypeface(context) }
    val pixel = remember { AndroidDrawSurface.pixelTypeface(context) }
    val scene = BlockclockScene(display = display, stale = stale, progress = progress, palette = EInk.theme.palette)
    val description = listOf(display.labelTop, display.labelBottom, display.value, display.suffix)
        .filter { it.isNotEmpty() }
        .joinToString(" ") + ". " + display.caption
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .semantics { contentDescription = description }
    ) {
        drawIntoCanvas { canvas ->
            BlockclockPainter.paint(AndroidDrawSurface(canvas.nativeCanvas, condensed, pixel), size.width, size.height, scene)
        }
    }
}

/** Dark card container in the app's unified X-style theme. */
@Composable
fun EInkPanel(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(EInk.Body, EInk.BodyShape)
            .border(0.5.dp, EInk.BodyStroke, EInk.BodyShape)
            .padding(16.dp)
    ) {
        SectionLabel(title)
        Spacer(modifier = Modifier.height(10.dp))
        content()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = EInk.Caption,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
    )
}

/** Selectable pill chip in X style: orange fill when selected, subtle dark pill otherwise. */
@Composable
fun EInkChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(EInk.ChipShape)
            .background(if (selected) EInk.Accent else EInk.Body, EInk.ChipShape)
            .then(if (!selected) Modifier.border(1.dp, EInk.BodyStroke, EInk.ChipShape) else Modifier)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) Color.Black else EInk.Value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun EInkSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = EInk.Accent,
            checkedBorderColor = EInk.Accent,
            uncheckedThumbColor = EInk.Caption,
            uncheckedTrackColor = EInk.Background,
            uncheckedBorderColor = EInk.BodyStroke,
            disabledUncheckedThumbColor = EInk.BodyStroke,
            disabledUncheckedTrackColor = EInk.Background,
            disabledUncheckedBorderColor = EInk.BodyStroke
        )
    )
}
