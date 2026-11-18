package com.example.btcwidget.nostr

import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.QuoteHints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Hints for finding a quoted note, from its link and the quoting note's tags. */
class QuoteHintsTest {
    private val quoted = "a".repeat(64)
    private val author = "b".repeat(64)

    private fun quoting(tags: List<List<String>>) =
        Event(id = "1".repeat(64), pubkey = "2".repeat(64), createdAt = 0, kind = 1, tags = tags, content = "nostr:note1…", sig = "3".repeat(128))

    @Test
    fun aBareNoteLinkGetsRelayAndAuthorFromTheQTag() {
        val h = QuoteHints.of(quoting(listOf(listOf("q", quoted, "wss://relay.example.com", author))), quoted)
        assertEquals(listOf("wss://relay.example.com"), h.relays)
        assertEquals(author, h.author)
    }

    @Test
    fun eTagsCarryTheAuthorFifth() {
        val h = QuoteHints.of(quoting(listOf(listOf("e", quoted, "wss://r.example", "mention", author))), quoted)
        assertEquals(listOf("wss://r.example"), h.relays)
        assertEquals(author, h.author)
    }

    @Test
    fun linkHintsComeFirstAndJunkIsDropped() {
        val h = QuoteHints.of(
            quoting(listOf(listOf("q", quoted, "", "not-a-key"), listOf("e", "other".padEnd(64, '0'), "wss://elsewhere"))),
            quoted, linkRelays = listOf("wss://from-link"), linkAuthor = null
        )
        assertEquals(listOf("wss://from-link"), h.relays)
        assertNull(h.author)
    }
}
