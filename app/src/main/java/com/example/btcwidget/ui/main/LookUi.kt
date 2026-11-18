package com.example.btcwidget.ui.main

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.toSize
import com.example.btcwidget.theme.Glow
import com.example.btcwidget.theme.LookColors
import com.example.btcwidget.theme.glowPixels
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.roundToInt

/** How long a new look takes to fade in. */
const val LOOK_FADE_MS = 420

/**
 * Runs the fade when [EInk.look] changes. Put once near the root of each window's content;
 * until it first runs, a new look applies at once (the saved one at start-up).
 */
@Composable
fun AnimateLook() {
    LaunchedEffect(Unit) { EInk.animateLooks = true }
    val target = EInk.look
    LaunchedEffect(target) {
        if (EInk.lookProgress < 1f) {
            animate(EInk.lookProgress, 1f, animationSpec = tween(LOOK_FADE_MS, easing = FastOutSlowInEasing)) { v, _ ->
                EInk.lookProgress = v
            }
        }
        EInk.lookProgress = 1f
    }
}

/**
 * The screen's background: [EInk.Background], plus the look's glow (Ember's warm corner).
 * The glow is placed against the whole window, not this element, so a top bar, a list and the
 * rows in it each paint their own part of one continuous light, and it stays put while they
 * scroll. Use it wherever a screen or bar is filled with the background colour.
 */
fun Modifier.screenBackground(): Modifier = this then ScreenBackgroundElement

private object ScreenBackgroundElement : ModifierNodeElement<ScreenBackgroundNode>() {
    override fun create() = ScreenBackgroundNode()
    override fun update(node: ScreenBackgroundNode) {}
    override fun hashCode(): Int = 0x5C2EE7
    override fun equals(other: Any?): Boolean = other === this
    override fun InspectorInfo.inspectableProperties() { name = "screenBackground" }
}

private class ScreenBackgroundNode : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {
    private var offset = Offset.Zero
    private var window = Size.Zero

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val o = coordinates.positionInRoot()
        val w = coordinates.findRootCoordinates().size.toSize()
        if (o != offset || w != window) {
            offset = o
            window = w
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        // Snapshot reads in draw: a look change or fade step redraws without recomposing.
        drawLookBackground(EInk.current, EInk.glowAlpha, window, offset)
        drawContent()
    }

}

/**
 * Fills this draw scope with [colors]' background and glow, as the part of a [window]-sized
 * screen that starts at [offset] (pass this element's own size and zero for a stand-alone
 * preview).
 *
 * The glow is a small bitmap made once per look and window shape ([GlowBitmaps]) and drawn
 * stretched over the window, clipped to this element: no full-screen shader with a large
 * transform, which some GPUs (and the emulator's software one) handle badly, and much less
 * work per frame on small phones.
 */
internal fun DrawScope.drawLookBackground(colors: LookColors, glowAlpha: Float, window: Size, offset: Offset = Offset.Zero) {
    drawRect(Color(colors.background))
    val glow = colors.glow ?: return
    if (glowAlpha <= 0f || window.width < 1f || window.height < 1f) return
    val image = GlowBitmaps.of(glow, window.width / window.height)
    clipRect {
        drawImage(
            image,
            dstOffset = androidx.compose.ui.unit.IntOffset(-offset.x.roundToInt(), -offset.y.roundToInt()),
            dstSize = androidx.compose.ui.unit.IntSize(window.width.roundToInt(), window.height.roundToInt()),
            alpha = glowAlpha,
            filterQuality = androidx.compose.ui.graphics.FilterQuality.Low
        )
    }
}

/** Glow bitmaps, one per look and (rounded) window shape; a handful at most. */
private object GlowBitmaps {
    private const val WIDTH = 96
    private val cache = java.util.concurrent.ConcurrentHashMap<Pair<Glow, Int>, androidx.compose.ui.graphics.ImageBitmap>()

    fun of(glow: Glow, aspect: Float): androidx.compose.ui.graphics.ImageBitmap {
        // Shapes within 5% share a bitmap, so rotating or resizing doesn't keep making new ones.
        val key = glow to (aspect.coerceIn(0.2f, 5f) * 20).roundToInt()
        return cache.getOrPut(key) {
            val height = (WIDTH / (key.second / 20f)).roundToInt().coerceIn(16, 480)
            val pixels = glowPixels(glow, WIDTH, height)
            android.graphics.Bitmap.createBitmap(pixels, WIDTH, height, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
        }
    }
}
