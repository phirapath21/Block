package com.example.btcwidget.ui.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The blank lines links leave behind when they're shown as photos, videos or quoted notes. */
class TextTidyTest {

    @Test
    fun trimsTheEnds() {
        // "Bruh\n\nnostr:nevent…" with the quote taken out of the text.
        assertEquals("Bruh", TextTidy.tidy("Bruh\n\n"))
        assertEquals("BLUESKY LMFAO", TextTidy.tidy("\n BLUESKY LMFAO \n\n\n"))
    }

    @Test
    fun keepsOneParagraphBreakBetweenParagraphs() {
        assertEquals("one\n\ntwo", TextTidy.tidy("one\n\n\n\ntwo"))
        assertEquals("one\n\ntwo", TextTidy.tidy("one\n \n\t\n\ntwo"))
        // Single line breaks and one blank line are the writer's: kept as they are.
        assertEquals("one\ntwo\n\nthree", TextTidy.tidy("one\ntwo\n\nthree"))
    }

    @Test
    fun nothingLeft() {
        assertTrue(TextTidy.keep("\n\n  \n").isEmpty())
        assertEquals("", TextTidy.tidy(""))
    }

    @Test
    fun rangesPointIntoTheOriginal() {
        val text = "  a\n\n\n\nb  "
        val parts = TextTidy.keep(text)
        assertEquals(listOf("a", "b"), parts.map { text.substring(it) })
    }
}
