package com.example.btcwidget.ui.main

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.toSize
import com.example.btcwidget.theme.LookColors

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
 */
internal fun DrawScope.drawLookBackground(colors: LookColors, glowAlpha: Float, window: Size, offset: Offset = Offset.Zero) {
    drawRect(Color(colors.background))
    val glow = colors.glow ?: return
    if (glowAlpha <= 0f || window.width <= 0f || window.height <= 0f) return
    clipRect {
        // The ellipse's centre in this element's coordinates.
        val c = Offset(glow.cx * window.width, glow.cy * window.height) - offset
        val sx = glow.rx * window.width / BASE_RADIUS
        val sy = glow.ry * window.height / BASE_RADIUS
        // A circle of BASE_RADIUS scaled about its centre gives the ellipse; the rectangle
        // drawn is this element's bounds in the unscaled space.
        scale(sx, sy, pivot = c) {
            val left = c.x - c.x / sx
            val top = c.y - c.y / sy
            val right = c.x + (size.width - c.x) / sx
            val bottom = c.y + (size.height - c.y) / sy
            drawRect(
                brush = Brush.radialGradient(
                    *glow.stops.indices.map { i -> glow.stops[i] to Color(glow.colors[i]) }.toTypedArray(),
                    center = c,
                    radius = BASE_RADIUS
                ),
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                alpha = glowAlpha
            )
        }
    }
}

private const val BASE_RADIUS = 1000f
