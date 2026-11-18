package com.example.btcwidget.ui.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Album grids: every tile inside the box, none overlapping, the box filled, and "+N" for the rest. */
class AlbumLayoutTest {

    @Test
    fun tilesFillTheAlbumWithoutOverlapping() {
        for (count in 2..9) {
            val layout = AlbumLayout.of(count)
            val tiles = layout.tiles
            assertEquals("tiles for $count", minOf(count, AlbumLayout.MAX_TILES), tiles.size)
            tiles.forEach { t ->
                assertTrue(t.x >= 0f && t.y >= 0f && t.x + t.w <= 1.0001f && t.y + t.h <= 1.0001f)
            }
            assertEquals("area for $count", 1f, tiles.sumOf { (it.w * it.h).toDouble() }.toFloat(), 1e-4f)
            for (i in tiles.indices) for (j in i + 1 until tiles.size) {
                val a = tiles[i]; val b = tiles[j]
                val overlapW = minOf(a.x + a.w, b.x + b.w) - maxOf(a.x, b.x)
                val overlapH = minOf(a.y + a.h, b.y + b.h) - maxOf(a.y, b.y)
                assertTrue("$count: tiles $i and $j overlap", overlapW <= 1e-4f || overlapH <= 1e-4f)
            }
        }
    }

    @Test
    fun theRestIsCountedOnTheLastTile() {
        assertEquals(0, AlbumLayout.of(4).more)
        assertEquals(0, AlbumLayout.of(5).more)
        assertEquals(3, AlbumLayout.of(8).more)
    }

    @Test
    fun threeIsOneTallAndTwoStacked() {
        val t = AlbumLayout.of(3).tiles
        assertEquals(1f, t[0].h, 0f)
        assertEquals(.5f, t[1].h, 0f)
        assertEquals(.5f, t[2].y, 0f)
    }
}
