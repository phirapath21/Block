package com.example.btcwidget.theme

import com.example.btcwidget.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The app's colour themes: Block unchanged, Ember as Zeus has it, and readable text in both. */
class AppLookTest {

    @Test
    fun blockKeepsTheColoursTheAppAlwaysHad() {
        val ui = Palette.CLASSIC.ui
        val c = AppLook.BLOCK.colors
        assertEquals(ui.background, c.background)
        assertEquals(ui.body, c.body)
        assertEquals(ui.bodyStroke, c.bodyStroke)
        assertEquals(ui.value, c.value)
        assertEquals(ui.label, c.label)
        assertEquals(ui.caption, c.caption)
        assertEquals(ui.track, c.track)
        assertEquals(ui.alert, c.alert)
        assertEquals(0xFFF7931A.toInt(), c.accent)
        assertNull(c.glow)
    }

    @Test
    fun emberIsZeusEmber() {
        val c = AppLook.EMBER.colors
        assertEquals(0xFF000000.toInt(), c.background)
        assertEquals(0xFF272012.toInt(), c.body)
        assertEquals(0xFF272012.toInt(), c.sheet)
        assertEquals(0xFF373020.toInt(), c.bodyStroke)
        assertEquals(0xFFFFA900.toInt(), c.accent)
        assertEquals(0xFFA3A09A.toInt(), c.caption)
        val glow = c.glow!!
        assertEquals(listOf(0xFF4D3300.toInt(), 0xFF332200.toInt(), 0xFF170F00.toInt(), 0xFF000000.toInt()), glow.colors)
        assertEquals(listOf(0f, 0.3f, 0.6f, 1f), glow.stops)
        assertEquals(listOf(0f, 0f, 1.4f, 0.8f), listOf(glow.cx, glow.cy, glow.rx, glow.ry))
    }

    @Test
    fun everyLookReads() {
        for (look in AppLook.entries) {
            val c = look.colors
            // Body text: AAA on the background and on cards; captions and buttons: AA.
            assertTrue("${look.name} text", contrast(c.value, c.background) >= 7.0)
            assertTrue("${look.name} text on cards", contrast(c.value, c.body) >= 7.0)
            assertTrue("${look.name} captions", contrast(c.caption, c.background) >= 4.5)
            assertTrue("${look.name} captions on sheets", contrast(c.caption, c.sheet) >= 4.5)
            assertTrue("${look.name} button text", contrast(c.onAccent, c.accent) >= 4.5)
            assertTrue("${look.name} accent", contrast(c.accent, c.background) >= 4.5)
            // The glow ends in the background, so there's no seam where it stops.
            c.glow?.let { assertEquals(c.background, it.colors.last()) }
        }
    }

    @Test
    fun savedNamesComeBack() {
        for (look in AppLook.entries) assertEquals(look, AppLook.of(look.id))
        // Nothing saved (or a name from a future version): the default, Aurora.
        assertEquals(AppLook.AURORA, AppLook.of(null))
        assertEquals(AppLook.AURORA, AppLook.of("no-such-theme"))
        // Graphite is the old Block look, under the id people already saved.
        assertEquals(AppLook.BLOCK, AppLook.of("block"))
    }

    @Test
    fun fadingMixesEveryColour() {
        val a = AppLook.BLOCK.colors
        val b = AppLook.EMBER.colors
        assertEquals(a, lerpLook(a, b, 0f))
        assertEquals(b, lerpLook(a, b, 1f))
        val mid = lerpLook(a, b, 0.5f)
        assertEquals(lerpArgb(a.accent, b.accent, 0.5f), mid.accent)
        assertEquals(b.glow, mid.glow)
        assertEquals(0xFF808080.toInt(), lerpArgb(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0.5f))
        // The glow fades in with Ember and out when leaving it.
        assertEquals(0.25f, glowStrength(a, b, 0.25f), 0f)
        assertEquals(0.75f, glowStrength(b, a, 0.25f), 0f)
        assertEquals(0f, glowStrength(a, a, 0.5f), 0f)
    }

    @Test
    fun theGlowBitmapFollowsTheGradient() {
        val glow = AppLook.EMBER.colors.glow!!
        // The centre is the first stop, past the ellipse is the last (the background).
        assertEquals(glow.colors.first(), glowColorAt(glow, 0f, 0f))
        assertEquals(glow.colors.last(), glowColorAt(glow, 1f, 1f))
        // Along the x axis, d = u / rx: at 0.3 × 1.4 it's exactly the second stop.
        assertEquals(glow.colors[1], glowColorAt(glow, 0.3f * 1.4f, 0f))
        val px = glowPixels(glow, 8, 16)
        assertEquals(128, px.size)
        assertEquals(glowColorAt(glow, 0.5f / 8, 0.5f / 16), px[0])
        // Fully opaque everywhere, and never brighter than the centre.
        assertTrue(px.all { (it ushr 24) == 0xFF })
        assertTrue(px.all { ((it shr 16) and 0xFF) <= ((glow.colors.first() shr 16) and 0xFF) })
    }

    @Test
    fun auroraIsTheNewDefault() {
        val c = AppLook.AURORA.colors
        assertEquals(AppLook.AURORA, AppLook.DEFAULT)
        assertEquals(0xFF000000.toInt(), c.background)
        assertEquals(0xFF16181C.toInt(), c.body)
        assertEquals(0xFFE7E9EA.toInt(), c.value)
        assertEquals(0xFF1877CC.toInt(), c.accent)
        assertEquals(0xFFFFFFFF.toInt(), c.onAccent)
        assertEquals(0xFF000000.toInt(), c.glow!!.colors.last())
    }

    @Test
    fun gildedAndKyriakiAreZeus() {
        assertEquals(0xFFD5BF8D.toInt(), AppLook.GILDED.colors.accent)
        assertEquals(0xFF4F473C.toInt(), AppLook.GILDED.colors.glow!!.colors.first())
        assertEquals(0xFF1E2022.toInt(), AppLook.KYRIAKI.colors.background)
        assertEquals(0xFFFFA900.toInt(), AppLook.KYRIAKI.colors.accent)
        assertNull(AppLook.KYRIAKI.colors.glow)
        assertEquals(AppLook.GILDED, AppLook.of("gilded"))
    }
}
