package com.example.btcwidget.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.btcwidget.ui.main.AnimateLook
import com.example.btcwidget.ui.main.EInk
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Bitcoin orange: Block's one accent colour. */
val BitcoinOrange = Color(0xFFF7931A)

/**
 * Block's colours for the Material components it uses (menus, dialogs, sheets, text fields,
 * switches, the bottom bar). Fixed and dark, matching the unified X-style dark palette
 * ([com.example.btcwidget.ui.main.EInk]) with [BitcoinOrange] as the accent.
 */
private val BlockColors = darkColorScheme(
    primary = BitcoinOrange,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF3A2306),
    onPrimaryContainer = Color(0xFFFFD9A8),
    inversePrimary = BitcoinOrange,
    secondary = Color(0xFFE7E9EA),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF16181C),
    onSecondaryContainer = Color(0xFFE7E9EA),
    tertiary = BitcoinOrange,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF3A2306),
    onTertiaryContainer = Color(0xFFFFD9A8),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE7E9EA),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFE7E9EA),
    surfaceVariant = Color(0xFF16181C),
    onSurfaceVariant = Color(0xFF71767B),
    surfaceTint = Color.Transparent, // no tinting of raised surfaces
    inverseSurface = Color(0xFFE7E9EA),
    inverseOnSurface = Color(0xFF000000),
    error = Color(0xFFF4212E),
    onError = Color.White,
    errorContainer = Color(0xFF3D1513),
    onErrorContainer = Color(0xFFFFB4AE),
    outline = Color(0xFF2F3336),
    outlineVariant = Color(0xFF202327),
    scrim = Color.Black,
    // Raised surfaces: menus, dialogs and sheets sit a step above the black background.
    surfaceBright = Color(0xFF202327),
    surfaceDim = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF16181C),
    surfaceContainer = Color(0xFF16181C),
    surfaceContainerHigh = Color(0xFF202327),
    surfaceContainerHighest = Color(0xFF2F3336),
)

/** The design system's two corner sizes: 8 for small things, 16 for cards, sheets and dialogs. */
private val BlockShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

/**
 * Material colours for a look other than Block's: its sheet colour for menus, dialogs and
 * containers, its divider colour for outlines, its accent as primary.
 */
internal fun schemeFor(c: LookColors) = darkColorScheme(
    primary = Color(c.accent),
    onPrimary = Color(c.onAccent),
    primaryContainer = Color(lerpArgb(c.background, c.accent, 0.22f)),
    onPrimaryContainer = Color(lerpArgb(c.accent, 0xFFFFFFFF.toInt(), 0.6f)),
    secondary = Color(c.value),
    onSecondary = Color(c.background),
    secondaryContainer = Color(c.sheet),
    onSecondaryContainer = Color(c.value),
    tertiary = Color(c.accent),
    onTertiary = Color(c.onAccent),
    tertiaryContainer = Color(lerpArgb(c.background, c.accent, 0.22f)),
    onTertiaryContainer = Color(lerpArgb(c.accent, 0xFFFFFFFF.toInt(), 0.6f)),
    background = Color(c.background),
    onBackground = Color(c.value),
    surface = Color(c.background),
    onSurface = Color(c.value),
    surfaceVariant = Color(c.sheet),
    onSurfaceVariant = Color(c.caption),
    surfaceTint = Color(c.accent),
    inverseSurface = Color(c.value),
    inverseOnSurface = Color(c.background),
    inversePrimary = Color(c.accent),
    error = Color(c.alert),
    onError = Color.White,
    errorContainer = Color(lerpArgb(c.background, c.alert, 0.25f)),
    onErrorContainer = Color(lerpArgb(c.alert, 0xFFFFFFFF.toInt(), 0.6f)),
    outline = Color(c.bodyStroke),
    outlineVariant = Color(lerpArgb(c.bodyStroke, c.background, 0.35f)),
    scrim = Color.Black,
    surfaceBright = Color(c.body),
    surfaceDim = Color(c.background),
    surfaceContainerLowest = Color(c.background),
    surfaceContainerLow = Color(c.sheet),
    surfaceContainer = Color(c.sheet),
    surfaceContainerHigh = Color(c.body),
    surfaceContainerHighest = Color(c.bodyStroke),
)

/**
 * The app's Material theme, in the chosen look ([EInk.look]): Block's own colours as before,
 * or the look's. Also runs the fade between looks.
 */
@Composable
fun BTCWidgetTheme(content: @Composable () -> Unit) {
    AnimateLook()
    val settled = EInk.lookProgress >= 1f
    val colors = if (settled && EInk.look == AppLook.BLOCK) BlockColors else {
        val c = EInk.current
        remember(c) { schemeFor(c) }
    }
    MaterialTheme(colorScheme = colors, shapes = BlockShapes, typography = Typography, content = content)
}
