package com.example.btcwidget.nostr.core

import org.junit.Assert.assertEquals
import org.junit.Test

/** Turning relays' "OK false" messages (NIP-01 prefixes) into reasons the app can explain. */
class RelayRejectionTest {

    @Test
    fun readsTheStandardPrefixes() {
        assertEquals(RelayRejection.Reason.POW, RelayRejection.explain("pow: difficulty 20 required").reason)
        assertEquals("difficulty 20 required", RelayRejection.explain("pow: difficulty 20 required").detail)
        assertEquals(RelayRejection.Reason.RATE_LIMITED, RelayRejection.explain("rate-limited: slow down").reason)
        assertEquals(RelayRejection.Reason.BLOCKED, RelayRejection.explain("blocked: you are banned").reason)
        assertEquals(RelayRejection.Reason.AUTH, RelayRejection.explain("auth-required: sign in").reason)
        assertEquals(RelayRejection.Reason.PAYMENT, RelayRejection.explain("payment-required: 1000 sats").reason)
        assertEquals(RelayRejection.Reason.RESTRICTED, RelayRejection.explain("restricted: members only").reason)
        assertEquals(RelayRejection.Reason.INVALID, RelayRejection.explain("invalid: bad signature").reason)
    }

    @Test
    fun unknownTextAndSilence() {
        assertEquals(RelayRejection.Explained(RelayRejection.Reason.OTHER, "something broke"), RelayRejection.explain("something broke"))
        assertEquals(RelayRejection.Reason.NO_ANSWER, RelayRejection.explain("").reason)
        assertEquals(RelayRejection.Reason.NO_ANSWER, RelayRejection.explain(null).reason)
    }

    @Test
    fun picksWhatTheUserCanFix() {
        val why = RelayRejection.mostUseful(listOf("blocked: no", "", "pow: difficulty 16", "error: x"))
        assertEquals(RelayRejection.Reason.POW, why.reason)
        assertEquals(RelayRejection.Reason.NO_ANSWER, RelayRejection.mostUseful(listOf("", "")).reason)
        assertEquals(RelayRejection.Reason.NO_ANSWER, RelayRejection.mostUseful(emptyList()).reason)
    }
}
