package com.example.btcwidget.nostr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Decrypted message files: named by their own bytes, never by what the sender wrote. */
class DmMediaCacheTest {

    @Test
    fun cacheNameIsOnlyTheHashAndASafeExtension() {
        val name = DmMediaCache.cacheName("video/mp4", "hello".toByteArray())
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824.mp4", name)
        assertFalse('/' in name)
    }

    @Test
    fun aCraftedMimeCantAddPathParts() {
        val name = DmMediaCache.cacheName("video/../../shared_prefs/x", "a".toByteArray())
        assertFalse('/' in name)
        assertFalse(".." in name)
        assertTrue(name.matches(Regex("[0-9a-f]{64}\\.[A-Za-z0-9]{1,6}")))
    }

    @Test
    fun differentFilesNeverShareAName() {
        assertNotEquals(
            DmMediaCache.cacheName("audio/mp4", ByteArray(10) { 1 }),
            DmMediaCache.cacheName("audio/mp4", ByteArray(10) { 2 })
        )
    }

    @Test
    fun displayNameLosesFoldersAndLeadingDots() {
        assertEquals("nostr_account.xml", DmMediaCache.safeDisplayName("../../shared_prefs/nostr_account.xml"))
        assertEquals("evil.txt", DmMediaCache.safeDisplayName("..\\..\\evil.txt"))
        assertEquals("bashrc", DmMediaCache.safeDisplayName(".bashrc"))
        assertEquals("report.pdf", DmMediaCache.safeDisplayName("report.pdf"))
    }

    @Test
    fun nothingUsableLeftGivesNull() {
        assertNull(DmMediaCache.safeDisplayName(null))
        assertNull(DmMediaCache.safeDisplayName("../"))
        assertNull(DmMediaCache.safeDisplayName("..."))
    }
}
