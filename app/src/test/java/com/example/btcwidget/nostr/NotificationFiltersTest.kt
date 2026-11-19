package com.example.btcwidget.nostr

import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Kind
import com.example.btcwidget.nostr.core.Nip17
import com.example.btcwidget.nostr.core.NotificationFilters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What instant notifications ask the relays for. */
class NotificationFiltersTest {
    private val me = "me"
    private val now = 1_800_000_000L
    private val filters = NotificationFilters.live(me, now)
    private fun event(kind: Int, at: Long, to: String = me) = Event("id$kind$at", "them", at, kind, listOf(listOf("p", to)), "", "")
    private fun wanted(e: Event) = filters.any { it.matches(e) }

    @Test fun everyKindOfActivityToMeFromNowOn() {
        for (kind in listOf(Kind.TEXT_NOTE, Kind.REPOST, Kind.REACTION, Kind.ZAP_RECEIPT)) assertTrue("kind $kind", wanted(event(kind, now)))
        // Not someone else's, and not old history.
        assertFalse(wanted(event(Kind.TEXT_NOTE, now, to = "someone")))
        assertFalse(wanted(event(Kind.TEXT_NOTE, now - 3600)))
    }

    @Test fun messagesEvenWhenTheirWrapIsBackDated() {
        assertTrue(wanted(event(Nip17.GIFT_WRAP, now - NotificationFilters.WRAP_BACKDATE_SECONDS)))
        assertFalse(wanted(event(Nip17.GIFT_WRAP, now - NotificationFilters.WRAP_BACKDATE_SECONDS - 3600)))
    }

    @Test fun onlyNewEventsAreSent() {
        // limit 0: relays send nothing stored, only what arrives from now on.
        filters.forEach { assertEquals(0, it.toJson().getInt("limit")) }
    }
}
