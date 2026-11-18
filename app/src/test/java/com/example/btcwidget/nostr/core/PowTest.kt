package com.example.btcwidget.nostr.core

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** NIP-13 proof of work: counting zero bits, the commitment rule, and the miner itself. */
class PowTest {

    private val me = "a".repeat(64)

    @Test
    fun countsLeadingZeroBitsOfHexIds() {
        // The NIP-13 example: 000000000e9d97a1… has 36 leading zero bits.
        assertEquals(36, Pow.leadingZeroBits("000000000e9d97a1ab09fc381030b346cdd7a142ad57e6df0b46dc9bef6c7e2d"))
        assertEquals(0, Pow.leadingZeroBits("f" + "0".repeat(63)))
        assertEquals(3, Pow.leadingZeroBits("1" + "f".repeat(63)))
        assertEquals(4, Pow.leadingZeroBits("0f" + "0".repeat(62)))
        assertEquals(256, Pow.leadingZeroBits("0".repeat(64)))
        assertEquals(0, Pow.leadingZeroBits("zz"))
    }

    @Test
    fun countsLeadingZeroBitsOfBytes() {
        assertEquals(8 + 7, Pow.leadingZeroBits(byteArrayOf(0, 1, 0)))
        assertEquals(0, Pow.leadingZeroBits(byteArrayOf(0x80.toByte())))
        assertEquals(16, Pow.leadingZeroBits(ByteArray(2)))
    }

    @Test
    fun theSplitSerializationIsTheRealId() {
        val withTags = UnsignedEvent(me, 1_700_000_000, 1, listOf(listOf("t", "nostr"), listOf("nonce", "9", "99")), "hi \"there\"\nยินดี")
        val noTags = withTags.copy(tags = emptyList())
        for (t in listOf(withTags, noTags)) {
            val (prefix, suffix) = Pow.split(t, 21)
            val joined = String(prefix, Charsets.UTF_8) + "123456" + String(suffix, Charsets.UTF_8)
            assertEquals(Pow.withNonce(t, 123_456, 21).serializeForId(), joined)
        }
    }

    @Test
    fun minedEventsReachTheTargetAndCommitToIt() = runBlocking {
        val template = UnsignedEvent(me, 1_700_000_000, 1, listOf(listOf("t", "pow")), "mined on a test")
        val mined = withTimeout(60_000) { Pow.mine(template, 12, threads = 2) }!!
        assertTrue(Pow.leadingZeroBits(mined.id) >= 12)
        val nonce = mined.tags.last()
        assertEquals("nonce", nonce[0])
        assertEquals("12", nonce[2])
        assertEquals(listOf("t", "pow"), mined.tags.first())
        assertEquals(template.content, mined.content)
        assertEquals(12, Pow.difficultyOf(mined.signed("0".repeat(128))))
    }

    @Test
    fun aMinedEventReplacesAnOldNonce() = runBlocking {
        val template = UnsignedEvent(me, 1_700_000_000, 1, listOf(listOf("nonce", "1", "40")), "again")
        val mined = Pow.mine(template, 8, threads = 1)!!
        assertEquals(1, mined.tags.count { it[0] == "nonce" })
        assertTrue(Pow.leadingZeroBits(mined.id) >= 8)
    }

    @Test
    fun skippingReturnsNull() = runBlocking {
        val template = UnsignedEvent(me, 1_700_000_000, 1, emptyList(), "never mind")
        assertNull(Pow.mine(template, Pow.MAX_DIFFICULTY, threads = 1, progressEveryMs = 10, skip = { true }))
    }

    @Test
    fun difficultyZeroJustDropsTheNonce() = runBlocking {
        val template = UnsignedEvent(me, 1, 1, listOf(listOf("nonce", "5", "8")), "")
        assertEquals(emptyList<List<String>>(), Pow.mine(template, 0)!!.tags)
    }

    @Test
    fun aLuckyIdCountsOnlyUpToItsCommitment() {
        val id = "0000" + "f".repeat(60) // 16 zero bits
        fun event(tags: List<List<String>>) = Event(id, me, 1, 1, tags, "", "0".repeat(128))
        assertEquals(16, Pow.difficultyOf(event(listOf(listOf("nonce", "7")))))
        assertEquals(8, Pow.difficultyOf(event(listOf(listOf("nonce", "7", "8")))))
        assertEquals(16, Pow.difficultyOf(event(listOf(listOf("nonce", "7", "20")))))
        // No nonce tag: zeros by chance prove nothing.
        assertEquals(0, Pow.difficultyOf(event(emptyList())))
    }

    @Test
    fun estimates() {
        assertEquals(65_536.0, Pow.expectedHashes(16), 0.0)
        assertEquals(2.0, Pow.expectedSeconds(16, 32_768.0), 1e-9)
        assertEquals(0.0, Pow.expectedSeconds(0, 1.0), 0.0)
        assertEquals(1 - Math.exp(-1.0), Pow.chanceDone(65_536, 16), 1e-9)
    }
}
