package com.example.btcwidget.theme

/**
 * The app's own colour themes (screens, menus, sheets), separate from the widget themes
 * ([com.example.btcwidget.AppTheme]) which only change how the blockclock panels are drawn.
 *
 * Plain ARGB ints so the colours can be checked in JVM tests; [com.example.btcwidget.ui.main.EInk]
 * turns them into Compose colours and animates between themes.
 */
enum class AppLook(val id: String, val title: String, val summary: String, val colors: LookColors) {
    /**
     * The default: X-like black and greys with a sky-blue accent, and a faint aurora (teal into
     * indigo) from the top-left corner, like the app icon. Captions are a shade lighter than X's
     * so they stay readable on sheets.
     */
    AURORA(
        "aurora", "Aurora", "Black with a soft aurora and sky blue",
        LookColors(
            background = 0xFF000000.toInt(),
            body = 0xFF16181C.toInt(),
            bodyStroke = 0xFF2F3336.toInt(),
            value = 0xFFE7E9EA.toInt(),
            label = 0xFFAEB7C0.toInt(),
            caption = 0xFF8B98A5.toInt(),
            track = 0xFF333639.toInt(),
            alert = 0xFFF4212E.toInt(),
            accent = 0xFF1D9BF0.toInt(),
            // Dark ink on the blue: white would be too faint to read there (under 4.5:1).
            onAccent = 0xFF04131F.toInt(),
            sheet = 0xFF16181C.toInt(),
            success = 0xFF00BA7C.toInt(),
            glow = Glow(
                // Teal, then indigo, fading into black.
                colors = listOf(0xFF0C2A2A.toInt(), 0xFF141638.toInt(), 0xFF09091A.toInt(), 0xFF000000.toInt()),
                stops = listOf(0f, 0.3f, 0.6f, 1f),
                cx = 0f, cy = 0f, rx = 1.4f, ry = 0.8f
            )
        )
    ),

    /** The original look (kept under its saved id "block"), now called Graphite. */
    BLOCK(
        "block", "Graphite", "Graphite with Bitcoin orange",
        LookColors(
            background = 0xFF0E0F11.toInt(),
            body = 0xFF1A1B1F.toInt(),
            bodyStroke = 0xFF2A2C31.toInt(),
            value = 0xFFD7DCDF.toInt(),
            label = 0xFFA3A8AC.toInt(),
            caption = 0xFF8C9196.toInt(),
            track = 0xFF34363B.toInt(),
            alert = 0xFFE5534B.toInt(),
            accent = 0xFFF7931A.toInt(),
            onAccent = 0xFF000000.toInt(),
            sheet = 0xFF16181C.toInt(),
            success = 0xFF3FB950.toInt(),
            glow = null
        )
    ),

    /**
     * Zeus wallet's Ember: black, with a warm #FFA900 glow from the top-left corner, and
     * surfaces and dividers tinted toward the same orange so rows share the glow's hue.
     * Colours from Zeus's `Ember` (built on its `Gilded`) theme.
     */
    EMBER(
        "ember", "Ember", "Black with a warm orange glow, from Zeus",
        LookColors(
            background = 0xFF000000.toInt(),
            body = 0xFF272012.toInt(),
            bodyStroke = 0xFF373020.toInt(),
            value = 0xFFFFFFFF.toInt(),
            label = 0xFFCFC9BE.toInt(),
            caption = 0xFFA3A09A.toInt(),
            track = 0xFF3A3122.toInt(),
            alert = 0xFFE14C4C.toInt(),
            accent = 0xFFFFA900.toInt(),
            onAccent = 0xFF1A1814.toInt(),
            sheet = 0xFF272012.toInt(),
            success = 0xFF46BE43.toInt(),
            glow = Glow(
                // #FFA900 at 30%, 20% and 9% over black.
                colors = listOf(0xFF4D3300.toInt(), 0xFF332200.toInt(), 0xFF170F00.toInt(), 0xFF000000.toInt()),
                stops = listOf(0f, 0.3f, 0.6f, 1f),
                cx = 0f, cy = 0f, rx = 1.4f, ry = 0.8f
            )
        )
    ),

    /** Zeus's Gilded: black with a soft champagne glow, gold accents and near-black surfaces. */
    GILDED(
        "gilded", "Gilded", "Black with a soft gold glow, from Zeus",
        LookColors(
            background = 0xFF000000.toInt(),
            body = 0xFF141414.toInt(),
            bodyStroke = 0xFF262523.toInt(),
            value = 0xFFFFFFFF.toInt(),
            label = 0xFFCFC9BE.toInt(),
            caption = 0xFFA3A09A.toInt(),
            track = 0xFF2C2A26.toInt(),
            alert = 0xFFE14C4C.toInt(),
            accent = 0xFFD5BF8D.toInt(),
            onAccent = 0xFF1A1814.toInt(),
            sheet = 0xFF141414.toInt(),
            success = 0xFF46BE43.toInt(),
            glow = Glow(
                colors = listOf(0xFF4F473C.toInt(), 0xFF3A342B.toInt(), 0xFF1C1A16.toInt(), 0xFF000000.toInt()),
                stops = listOf(0f, 0.3f, 0.6f, 1f),
                cx = 0f, cy = 0f, rx = 1.4f, ry = 0.8f
            )
        )
    ),

    /** Zeus's Kyriaki: charcoal grey with Zeus orange, no glow. */
    KYRIAKI(
        "kyriaki", "Kyriaki", "Charcoal with Zeus orange",
        LookColors(
            background = 0xFF1E2022.toInt(),
            body = 0xFF2A2E35.toInt(),
            bodyStroke = 0xFF31363F.toInt(),
            value = 0xFFFFFFFF.toInt(),
            label = 0xFFCDCFD2.toInt(),
            caption = 0xFFA7A9AC.toInt(),
            track = 0xFF3B414B.toInt(),
            alert = 0xFFE14C4C.toInt(),
            accent = 0xFFFFA900.toInt(),
            onAccent = 0xFF000000.toInt(),
            sheet = 0xFF2A2E35.toInt(),
            success = 0xFF46BE43.toInt(),
            glow = null
        )
    );

    companion object {
        val DEFAULT = AURORA
        fun of(id: String?): AppLook = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** A theme's colours (ARGB). */
data class LookColors(
    /** Behind everything. */
    val background: Int,
    /** Cards and filled fields. */
    val body: Int,
    /** Dividers and outlines. */
    val bodyStroke: Int,
    /** Main text. */
    val value: Int,
    /** Secondary text that still needs to read well. */
    val label: Int,
    /** Captions, times, hints. */
    val caption: Int,
    /** Empty part of bars and switches. */
    val track: Int,
    /** Errors and warnings. */
    val alert: Int,
    /** Buttons, links, selection. */
    val accent: Int,
    /** Text and icons on [accent]. */
    val onAccent: Int,
    /** Bottom sheets, menus and dialogs. */
    val sheet: Int,
    /** Done, connected, sent. */
    val success: Int,
    /** A light behind the screen, or null for a flat [background]. */
    val glow: Glow?
)

/**
 * An elliptical radial gradient over the whole window, as fractions of its size: centred at
 * ([cx], [cy]) with radii [rx] × width and [ry] × height, through [colors] at [stops].
 */
data class Glow(val colors: List<Int>, val stops: List<Float>, val cx: Float, val cy: Float, val rx: Float, val ry: Float)

/** Mixes two ARGB colours: [t] = 0 gives [a], 1 gives [b]. */
fun lerpArgb(a: Int, b: Int, t: Float): Int {
    val f = t.coerceIn(0f, 1f)
    fun ch(shift: Int): Int {
        val x = (a ushr shift) and 0xFF
        val y = (b ushr shift) and 0xFF
        return (x + (y - x) * f + 0.5f).toInt().coerceIn(0, 255) shl shift
    }
    return ch(24) or ch(16) or ch(8) or ch(0)
}

/** Every colour of [a] mixed toward [b] by [t]; the glow comes from whichever side has one. */
fun lerpLook(a: LookColors, b: LookColors, t: Float): LookColors = if (t <= 0f) a else if (t >= 1f) b else LookColors(
    background = lerpArgb(a.background, b.background, t),
    body = lerpArgb(a.body, b.body, t),
    bodyStroke = lerpArgb(a.bodyStroke, b.bodyStroke, t),
    value = lerpArgb(a.value, b.value, t),
    label = lerpArgb(a.label, b.label, t),
    caption = lerpArgb(a.caption, b.caption, t),
    track = lerpArgb(a.track, b.track, t),
    alert = lerpArgb(a.alert, b.alert, t),
    accent = lerpArgb(a.accent, b.accent, t),
    onAccent = lerpArgb(a.onAccent, b.onAccent, t),
    sheet = lerpArgb(a.sheet, b.sheet, t),
    success = lerpArgb(a.success, b.success, t),
    glow = b.glow ?: a.glow
)

/** How strongly a glow shows while moving from [a] to [b] by [t] (fades in or out). */
fun glowStrength(a: LookColors, b: LookColors, t: Float): Float = when {
    a.glow != null && b.glow != null -> 1f
    b.glow != null -> t.coerceIn(0f, 1f)
    a.glow != null -> 1f - t.coerceIn(0f, 1f)
    else -> 0f
}

/** WCAG contrast ratio between two opaque colours (1–21). */
fun contrast(a: Int, b: Int): Double {
    fun lum(c: Int): Double {
        fun lin(v: Int): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4) }
        return 0.2126 * lin((c ushr 16) and 0xFF) + 0.7152 * lin((c ushr 8) and 0xFF) + 0.0722 * lin(c and 0xFF)
    }
    val (hi, lo) = lum(a).let { x -> lum(b).let { y -> if (x > y) x to y else y to x } }
    return (hi + 0.05) / (lo + 0.05)
}

/**
 * The glow's colour at ([u], [v]), fractions of the window (0–1): the gradient's stops along the
 * distance from its centre, the ellipse's radii counting as 1. Pure, so the bitmap the app
 * draws from can be checked in tests.
 */
fun glowColorAt(glow: Glow, u: Float, v: Float): Int {
    val dx = (u - glow.cx) / glow.rx
    val dy = (v - glow.cy) / glow.ry
    val d = kotlin.math.sqrt(dx * dx + dy * dy).coerceIn(0f, 1f)
    val stops = glow.stops
    if (d <= stops.first()) return glow.colors.first()
    for (i in 1 until stops.size) {
        if (d <= stops[i]) {
            val t = (d - stops[i - 1]) / (stops[i] - stops[i - 1]).coerceAtLeast(1e-6f)
            return lerpArgb(glow.colors[i - 1], glow.colors[i], t)
        }
    }
    return glow.colors.last()
}

/**
 * The glow as a small [width] × [height] picture (ARGB, row by row), for a window [aspect]
 * (width ÷ height) wide. Drawn stretched over the window it's indistinguishable from the
 * gradient, and costs one bitmap draw instead of a full-screen shader.
 */
fun glowPixels(glow: Glow, width: Int, height: Int): IntArray = IntArray(width * height) { i ->
    val x = i % width
    val y = i / width
    glowColorAt(glow, (x + 0.5f) / width, (y + 0.5f) / height)
}
