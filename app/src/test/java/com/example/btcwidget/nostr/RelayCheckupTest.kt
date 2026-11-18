package com.example.btcwidget.nostr

import com.example.btcwidget.nostr.core.RelayCheckup
import com.example.btcwidget.nostr.core.RelayCheckup.Probe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayCheckupTest {

    @Test
    fun downRelaysAreTickedAndTheListIsSorted() {
        val r = RelayCheckup.report(listOf(
            Probe("wss://slow", 2_400), Probe("wss://down", null, "timeout"), Probe("wss://fast", 120), Probe("wss://ok", 600)
        ))
        assertEquals(listOf("wss://fast", "wss://ok", "wss://slow", "wss://down"), r.probes.map { it.url })
        assertFalse(r.offline)
        assertEquals(setOf("wss://down"), r.suggestedRemovals)
    }

    @Test
    fun nothingConnectingMeansOfflineAndNothingIsRemoved() {
        val r = RelayCheckup.report(listOf(Probe("wss://a", null), Probe("wss://b", null), Probe("wss://c", null)))
        assertTrue(r.offline)
        assertTrue(r.suggestedRemovals.isEmpty())
    }

    @Test
    fun alwaysKeepsTwo() {
        val r = RelayCheckup.report(listOf(Probe("wss://up", 100), Probe("wss://d1", null), Probe("wss://d2", null)))
        assertEquals(1, r.suggestedRemovals.size)
        assertFalse(RelayCheckup.canRemove(listOf("wss://a", "wss://b"), listOf("wss://a")))
        assertTrue(RelayCheckup.canRemove(listOf("wss://a", "wss://b", "wss://c"), listOf("wss://a")))
        assertFalse(RelayCheckup.canRemove(listOf("wss://a", "wss://b", "wss://c"), emptyList()))
    }

    @Test
    fun suggestsWhatFollowsUseMost() {
        val writes = mapOf(
            "p1" to listOf("wss://popular/", "wss://mine", "wss://rare"),
            "p2" to listOf("wss://POPULAR", "wss://blocked"),
            "p3" to listOf("wss://popular", "ws://insecure", "wss://x.onion"),
            "p4" to listOf("wss://second", "wss://second")
        )
        val s = RelayCheckup.suggestions(writes, mine = setOf("wss://mine"), blocked = setOf("wss://blocked"))
        assertEquals("wss://popular" to 3, s.first())
        assertEquals(listOf("wss://popular", "wss://rare", "wss://second"), s.map { it.first })
    }
}
