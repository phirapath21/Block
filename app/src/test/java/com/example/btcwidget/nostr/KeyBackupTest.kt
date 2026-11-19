package com.example.btcwidget.nostr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When Home reminds the user to save their key. */
class KeyBackupTest {
    private val now = 1_800_000_000_000L

    @Test fun remindsForAnUnsavedKeyOnThisPhone() {
        assertTrue(KeyBackup.shouldRemind(needed = true, keyOnPhone = true, snoozedUntil = 0, now = now))
    }

    @Test fun quietOnceSaved() {
        assertFalse(KeyBackup.shouldRemind(needed = false, keyOnPhone = true, snoozedUntil = 0, now = now))
    }

    @Test fun quietWhenASignerKeepsTheKey() {
        assertFalse(KeyBackup.shouldRemind(needed = true, keyOnPhone = false, snoozedUntil = 0, now = now))
    }

    @Test fun laterWaitsThreeDaysThenAsksAgain() {
        val until = now + KeyBackup.SNOOZE_MS
        assertFalse(KeyBackup.shouldRemind(needed = true, keyOnPhone = true, snoozedUntil = until, now = now))
        assertFalse(KeyBackup.shouldRemind(needed = true, keyOnPhone = true, snoozedUntil = until, now = until - 1))
        assertTrue(KeyBackup.shouldRemind(needed = true, keyOnPhone = true, snoozedUntil = until, now = until))
        assertTrue(KeyBackup.SNOOZE_MS == 3L * 24 * 60 * 60 * 1000)
    }
}
