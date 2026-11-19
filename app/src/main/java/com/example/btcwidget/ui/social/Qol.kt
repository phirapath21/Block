package com.example.btcwidget.ui.social

import android.os.Build
import com.example.btcwidget.ui.main.screenBackground
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Tapping the tab you're already on, as on X: the first tap scrolls back to the top, a tap
 * when you're already at the top refreshes. The bars send the tab's name; its screen reacts.
 */
object TabTaps {
    private val taps = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val flow = taps.asSharedFlow()
    fun tap(tab: String) { taps.tryEmit(tab) }
}

/**
 * Scrolls [list] to the top when [tab] is tapped again, or calls [onRefresh] when it's already
 * there. Far down, it jumps most of the way first so the animation stays short.
 */
@Composable
fun ScrollToTopOnTabTap(tab: String, list: LazyListState, onRefresh: (() -> Unit)? = null) {
    val refresh by rememberUpdatedState(onRefresh)
    LaunchedEffect(tab, list) {
        TabTaps.flow.collect { tapped ->
            if (tapped != tab) return@collect
            if (list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0) refresh?.invoke()
            else {
                if (list.firstVisibleItemIndex > 12) list.scrollToItem(8)
                list.animateScrollToItem(0)
            }
        }
    }
}

/** Whether the top and bottom bars are showing (they slide away while you scroll down a feed). */
val LocalBarsVisible = compositionLocalOf { true }

/**
 * Small taps of the phone's vibration, as X gives them: [tick] for a like or a toggle,
 * [confirm] when something is sent, [heavy] for a long press. Follows the phone's
 * touch-feedback setting.
 */
class Haptics(private val view: View) {
    fun tick() { view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 27) HapticFeedbackConstants.KEYBOARD_TAP else HapticFeedbackConstants.VIRTUAL_KEY) }
    fun confirm() { view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY) }
    fun heavy() { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}

/** The widest a column of posts gets on a wide screen, as on X (more makes lines hard to read). */
val READABLE_WIDTH = 560.dp

/** The side panel beside the posts on a wide screen (trending, live now), as X's right column. */
private val SIDE_PANEL_WIDTH = 340.dp
private val SIDE_PANEL_MIN = 220.dp
/** How narrow the posts may get to make room for the side panel (a phone on its side). */
private val READABLE_MIN = 460.dp

/** What goes in the side panel on wide screens; provided at the top of the app (null: none). */
val LocalSidePanel = androidx.compose.runtime.compositionLocalOf<(@Composable () -> Unit)?> { null }

/**
 * The shape (width over height) a photo or video in a post is shown at: its own [ratio] kept
 * between [min] and [max], and never taller than about half the screen, so more posts fit,
 * above all on a phone on its side. Taller ones are trimmed to fit, as X does.
 */
@Composable
fun mediaRatio(ratio: Float, min: Float, max: Float): Float {
    val c = androidx.compose.ui.platform.LocalConfiguration.current
    return mediaRatioFor(ratio, min, max, c.screenWidthDp, c.screenHeightDp)
}

/** [mediaRatio] for a screen of [screenWidthDp] × [screenHeightDp]. */
internal fun mediaRatioFor(ratio: Float, min: Float, max: Float, screenWidthDp: Int, screenHeightDp: Int): Float {
    val width = minOf(screenWidthDp.toFloat(), READABLE_WIDTH.value) - 70f
    val maxHeight = (screenHeightDp * 0.55f).coerceAtLeast(1f)
    // On a short screen the height cap can ask for a wider shape than [max] allows: [max] wins
    // (an empty range here crashed the app with a video on a phone on its side).
    val low = maxOf(min, width / maxHeight).coerceAtMost(max)
    return ratio.coerceIn(low, max)
}

/**
 * On a wide screen (landscape, tablet, unfolded), [content] in a column of [READABLE_WIDTH] in
 * the middle with hairlines either side, as X lays out its timeline, and the side panel beside
 * it when there's room; otherwise the full width.
 *
 * The content sits at the same place in the composition either way: turning the phone only
 * changes sizes, so what's open inside (a full-screen video, a half-typed reply, scroll
 * positions) stays. (It used to be composed afresh on every turn, which closed the video.)
 */
@Composable
fun ReadableColumn(enabled: Boolean = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600, content: @Composable () -> Unit) {
    val panel = LocalSidePanel.current
    androidx.compose.foundation.layout.BoxWithConstraints(
        androidx.compose.ui.Modifier.fillMaxSize().then(if (enabled) androidx.compose.ui.Modifier.screenBackground() else androidx.compose.ui.Modifier),
        contentAlignment = androidx.compose.ui.Alignment.TopCenter
    ) {
        // Room beside the posts: the side panel (trending, live now), as X has on the right.
        // A phone on its side has ~750dp once the rail, the system's buttons and the camera cut-out
        // are taken: the posts give up some width (down to READABLE_MIN) so the panel still fits.
        val side = if (enabled && panel != null && maxWidth >= READABLE_MIN + SIDE_PANEL_MIN)
            (maxWidth - READABLE_WIDTH).coerceIn(SIDE_PANEL_MIN, SIDE_PANEL_WIDTH) else 0.dp
        val posts = if (side > 0.dp) minOf(READABLE_WIDTH, maxWidth - side) else READABLE_WIDTH
        androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.fillMaxHeight()) {
            androidx.compose.foundation.layout.Box(
                androidx.compose.ui.Modifier
                    .then(if (enabled) androidx.compose.ui.Modifier.widthIn(max = READABLE_WIDTH) else androidx.compose.ui.Modifier)
                    .fillMaxHeight()
                    .then(if (side > 0.dp) androidx.compose.ui.Modifier.width(posts) else androidx.compose.ui.Modifier.fillMaxWidth())
                    .then(if (!enabled) androidx.compose.ui.Modifier else androidx.compose.ui.Modifier.drawBehind {
                        val c = com.example.btcwidget.ui.main.EInk.BodyStroke
                        val w = 0.5.dp.toPx()
                        drawLine(c, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(0f, size.height), w)
                        drawLine(c, androidx.compose.ui.geometry.Offset(size.width, 0f), androidx.compose.ui.geometry.Offset(size.width, size.height), w)
                    })
            ) { content() }
            if (side > 0.dp && panel != null) androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.width(side).fillMaxHeight()) { panel() }
        }
    }
}
