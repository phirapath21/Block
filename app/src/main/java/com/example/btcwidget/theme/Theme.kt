package com.example.btcwidget.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
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

private val BlockShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/** The app's Material theme: always Block's own dark colours. */
@Composable
fun BTCWidgetTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = BlockColors, shapes = BlockShapes, typography = Typography, content = content)
}
