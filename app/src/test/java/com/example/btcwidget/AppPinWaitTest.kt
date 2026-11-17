package com.example.btcwidget

import com.example.btcwidget.ui.social.AppPin
import org.junit.Assert.assertEquals
import org.junit.Test

/** The wait after wrong PINs: free tries first, then 30 s doubling, never past an hour. */
class AppPinWaitTest {

    @Test
    fun firstFewWrongPinsCostNothing() {
        for (n in 0..4) assertEquals(0L, AppPin.waitAfter(n))
    }

    @Test
    fun thenTheWaitDoubles() {
        assertEquals(30_000L, AppPin.waitAfter(5))
        assertEquals(60_000L, AppPin.waitAfter(6))
        assertEquals(120_000L, AppPin.waitAfter(7))
    }

    @Test
    fun neverLongerThanAnHour() {
        assertEquals(3_600_000L, AppPin.waitAfter(12))
        assertEquals(3_600_000L, AppPin.waitAfter(1_000))
    }
}
