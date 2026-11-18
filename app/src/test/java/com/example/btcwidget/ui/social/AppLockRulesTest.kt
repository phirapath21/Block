package com.example.btcwidget.ui.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** App lock: what it locks and when it asks again. */
class AppLockRulesTest {

    @Test
    fun wholeAppLocksAtStartOnlyWithAPin() {
        assertTrue(LockRules.lockedAtStart(hasPin = true, LockScope.APP))
        assertFalse(LockRules.lockedAtStart(hasPin = false, LockScope.APP))
        assertFalse(LockRules.lockedAtStart(hasPin = true, LockScope.SENSITIVE))
    }

    @Test
    fun noGraceTimeLocksOnLeaving() {
        assertTrue(LockRules.lockOnLeave(true, LockScope.APP, LockAfter.NOW))
        assertFalse(LockRules.lockOnLeave(true, LockScope.APP, LockAfter.MIN5))
        assertFalse(LockRules.lockOnLeave(true, LockScope.SENSITIVE, LockAfter.NOW))
    }

    @Test
    fun graceTimeCountsFromLeaving() {
        assertFalse(LockRules.expired(null, 1_000_000, LockAfter.MIN1))
        assertFalse(LockRules.expired(1_000_000, 1_000_000 + 59_999, LockAfter.MIN1))
        assertTrue(LockRules.expired(1_000_000, 1_000_000 + 60_000, LockAfter.MIN1))
        assertTrue(LockRules.expired(1_000_000, 1_000_000, LockAfter.NOW))
    }

    @Test
    fun privateThingsAskOncePerSession() {
        assertTrue(LockRules.needsUnlock(true, LockScope.SENSITIVE, unlockedThisSession = false))
        assertFalse(LockRules.needsUnlock(true, LockScope.SENSITIVE, unlockedThisSession = true))
        // The whole-app lock already asked; no PIN, nothing to ask.
        assertFalse(LockRules.needsUnlock(true, LockScope.APP, unlockedThisSession = false))
        assertFalse(LockRules.needsUnlock(false, LockScope.SENSITIVE, unlockedThisSession = false))
    }

    @Test
    fun savedChoicesComeBack() {
        LockScope.entries.forEach { assertEquals(it, LockScope.of(it.id)) }
        LockAfter.entries.forEach { assertEquals(it, LockAfter.of(it.ms)) }
        assertEquals(LockScope.APP, LockScope.of(null))
        assertEquals(LockAfter.NOW, LockAfter.of(12345))
    }
}
