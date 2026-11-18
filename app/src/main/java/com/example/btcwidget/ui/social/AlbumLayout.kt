package com.example.btcwidget.ui.social

/**
 * Where each photo of a post goes in its album grid, as on X and Facebook: the box's shape and
 * every tile as fractions of it. Pure, so it can be tested on the JVM.
 *
 * - 2: side by side;
 * - 3: one tall on the left, two stacked on the right;
 * - 4: a 2 × 2 grid;
 * - 5 or more: two across the top, three under them, the last with "+N" for the rest.
 */
object AlbumLayout {
    /** A tile: left, top, width and height as fractions (0–1) of the album. */
    data class Tile(val x: Float, val y: Float, val w: Float, val h: Float)

    data class Layout(
        /** Width ÷ height of the whole album. */
        val aspect: Float,
        val tiles: List<Tile>,
        /** Photos not shown, counted on the last tile as "+N" (0 = none). */
        val more: Int
    )

    const val MAX_TILES = 5

    /** The album for [count] photos (2 or more; 1 photo is shown on its own, at its own shape). */
    fun of(count: Int): Layout = when {
        count <= 1 -> Layout(1f, listOf(Tile(0f, 0f, 1f, 1f)), 0)
        count == 2 -> Layout(1.5f, listOf(Tile(0f, 0f, .5f, 1f), Tile(.5f, 0f, .5f, 1f)), 0)
        count == 3 -> Layout(1.5f, listOf(Tile(0f, 0f, .5f, 1f), Tile(.5f, 0f, .5f, .5f), Tile(.5f, .5f, .5f, .5f)), 0)
        count == 4 -> Layout(
            1.5f,
            listOf(Tile(0f, 0f, .5f, .5f), Tile(.5f, 0f, .5f, .5f), Tile(0f, .5f, .5f, .5f), Tile(.5f, .5f, .5f, .5f)),
            0
        )
        else -> {
            val top = .55f
            val third = 1f / 3
            Layout(
                1.1f,
                listOf(
                    Tile(0f, 0f, .5f, top), Tile(.5f, 0f, .5f, top),
                    Tile(0f, top, third, 1 - top), Tile(third, top, third, 1 - top), Tile(2 * third, top, 1 - 2 * third, 1 - top)
                ),
                count - MAX_TILES
            )
        }
    }
}
