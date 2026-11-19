package com.example.btcwidget.nostr.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext
import kotlin.math.exp
import kotlin.math.pow

/**
 * Proof of work (NIP-13): an event whose id starts with enough zero bits, found by trying
 * numbers in a `["nonce", "<n>", "<target>"]` tag. Relays and readers can use it to tell a
 * post someone spent effort on from cheap spam.
 *
 * The miner hashes the part of the event before the nonce once (the SHA-256 "midstate") and
 * only the nonce and what follows it for each try, on every core, and stops as soon as any
 * core finds one, the coroutine is cancelled, or [mine]'s `skip` says so.
 */
object Pow {
    const val NONCE = "nonce"

    /** The most this app will mine for: ~2^32 hashes is hours on a phone. */
    const val MAX_DIFFICULTY = 32

    /** Leading zero bits of a 32-byte hash. */
    fun leadingZeroBits(hash: ByteArray): Int {
        var bits = 0
        for (b in hash) {
            val v = b.toInt() and 0xff
            if (v == 0) { bits += 8; continue }
            return bits + Integer.numberOfLeadingZeros(v) - 24
        }
        return bits
    }

    /** Leading zero bits of a hex id (0 for anything that isn't hex). */
    fun leadingZeroBits(hexId: String): Int {
        var bits = 0
        for (c in hexId) {
            val v = Character.digit(c, 16)
            if (v < 0) return 0
            if (v == 0) { bits += 4; continue }
            return bits + Integer.numberOfLeadingZeros(v) - 28
        }
        return bits
    }

    /** The target the event's nonce tag commits to, if it has one. */
    fun committedTarget(tags: List<List<String>>): Int? =
        tags.lastOrNull { it.size >= 3 && it[0] == NONCE }?.get(2)?.trim()?.toIntOrNull()?.takeIf { it in 0..256 }

    /**
     * The work an event proves. A nonce tag that commits to a lower target than the id
     * happens to have counts only up to that target: a spammer mining many low-difficulty
     * notes can't claim the one that came out lucky. Events without a nonce tag prove
     * nothing (their zeros are chance), so they count 0.
     */
    fun difficultyOf(e: Event): Int {
        if (e.tags.none { it.firstOrNull() == NONCE }) return 0
        val zeros = leadingZeroBits(e.id)
        val target = committedTarget(e.tags) ?: return zeros
        return minOf(zeros, target)
    }

    /** Hashes expected, on average, to find [difficulty] zero bits. */
    fun expectedHashes(difficulty: Int): Double = 2.0.pow(difficulty)

    /** Expected seconds to mine [difficulty] at [hashesPerSecond]. */
    fun expectedSeconds(difficulty: Int, hashesPerSecond: Double): Double =
        if (difficulty <= 0) 0.0 else expectedHashes(difficulty) / hashesPerSecond.coerceAtLeast(1.0)

    /**
     * How likely a miner would have found one after [hashes] tries: 1 − e^(−hashes / 2^d).
     * Mining has no fixed length, so this is the honest "progress" to show.
     */
    fun chanceDone(hashes: Long, difficulty: Int): Double =
        if (difficulty <= 0) 1.0 else 1.0 - exp(-hashes / expectedHashes(difficulty))

    /** [template] without any nonce tag. */
    fun withoutNonce(template: UnsignedEvent): UnsignedEvent =
        template.copy(tags = template.tags.filterNot { it.firstOrNull() == NONCE })

    /** [template] with `["nonce", nonce, difficulty]` as its last tag. */
    fun withNonce(template: UnsignedEvent, nonce: Long, difficulty: Int): UnsignedEvent =
        withoutNonce(template).let { it.copy(tags = it.tags + listOf(listOf(NONCE, nonce.toString(), difficulty.toString()))) }

    /**
     * The id serialization of [template] with a nonce tag last, split around the nonce digits:
     * `…["nonce","` and `","<difficulty>"]],"<content>"]`.
     */
    internal fun split(template: UnsignedEvent, difficulty: Int): Pair<ByteArray, ByteArray> {
        val t = withoutNonce(template)
        val prefix = buildString {
            append("[0,")
            Json.string(this, t.pubkey); append(',')
            append(t.createdAt); append(',')
            append(t.kind); append(',')
            val tags = StringBuilder().also { Json.tags(it, t.tags) }
            append(tags, 0, tags.length - 1) // without the closing ]
            if (t.tags.isNotEmpty()) append(',')
            append("[\"").append(NONCE).append("\",\"")
        }
        val suffix = buildString {
            append("\",\"").append(difficulty).append("\"]],")
            Json.string(this, t.content)
            append(']')
        }
        return prefix.toByteArray(Charsets.UTF_8) to suffix.toByteArray(Charsets.UTF_8)
    }

    /** What [mine] reports while working. */
    data class Progress(val hashes: Long, val elapsedMs: Long) {
        val hashesPerSecond: Double get() = if (elapsedMs <= 0) 0.0 else hashes * 1000.0 / elapsedMs
    }

    /**
     * Finds a nonce giving [template] at least [difficulty] leading zero bits, and returns the
     * event with it (the nonce tag last, committing to [difficulty]). Uses [threads] cores;
     * [onProgress] is called about every [progressEveryMs]. Returns null when [skip] turns
     * true (the caller then publishes without work); throws if cancelled.
     */
    suspend fun mine(
        template: UnsignedEvent,
        difficulty: Int,
        threads: Int = defaultThreads(),
        progressEveryMs: Long = 200,
        skip: () -> Boolean = { false },
        onProgress: (Progress) -> Unit = {}
    ): UnsignedEvent? {
        require(difficulty in 0..MAX_DIFFICULTY) { "difficulty $difficulty" }
        if (difficulty == 0) return withoutNonce(template)
        val (prefix, suffix) = split(template, difficulty)
        val base = MessageDigest.getInstance("SHA-256").apply { update(prefix) }
        val n = threads.coerceIn(1, 64)
        val found = AtomicReference<Long?>(null)
        val skipped = java.util.concurrent.atomic.AtomicBoolean(false)
        val hashes = AtomicLong()
        val start = System.nanoTime()
        fun elapsedMs() = (System.nanoTime() - start) / 1_000_000
        withContext(Dispatchers.Default) {
            coroutineScope {
                val reporter = launch {
                    while (true) {
                        delay(progressEveryMs)
                        if (skip()) skipped.set(true)
                        onProgress(Progress(hashes.get(), elapsedMs()))
                    }
                }
                (0 until n).map { lane ->
                    async {
                        val digest = base.clone() as MessageDigest
                        val nonceBuf = ByteArray(20)
                        val out = ByteArray(32)
                        var nonce = lane.toLong()
                        var sinceCheck = 0
                        while (found.get() == null && !skipped.get()) {
                            val len = writeDecimal(nonce, nonceBuf)
                            val md = digest.clone() as MessageDigest
                            md.update(nonceBuf, nonceBuf.size - len, len)
                            md.update(suffix)
                            md.digest(out, 0, 32)
                            if (meets(out, difficulty)) { found.compareAndSet(null, nonce); break }
                            nonce += n
                            if (++sinceCheck == 2048) {
                                hashes.addAndGet(sinceCheck.toLong()); sinceCheck = 0
                                coroutineContext.ensureActive()
                            }
                        }
                        hashes.addAndGet(sinceCheck.toLong())
                    }
                }.awaitAll()
                reporter.cancel()
            }
        }
        onProgress(Progress(hashes.get(), elapsedMs()))
        val nonce = found.get() ?: return null.also { if (!skipped.get()) throw CancellationException("mining stopped") }
        return withNonce(template, nonce, difficulty)
    }

    /** Leaves a core for the screen when the phone has more than two. */
    fun defaultThreads(): Int = Runtime.getRuntime().availableProcessors().let { if (it > 2) it - 1 else it }.coerceAtLeast(1)

    /**
     * Hashes per second on this device with [threads] cores, measured by mining a typical note
     * at an unreachable difficulty for about [millis].
     */
    suspend fun benchmark(threads: Int = defaultThreads(), millis: Long = 600): Double {
        val sample = UnsignedEvent("f".repeat(64), 1_700_000_000, Kind.TEXT_NOTE, emptyList(), "x".repeat(140))
        var last = Progress(0, 0)
        coroutineScope {
            val job = async { runCatching { mine(sample, MAX_DIFFICULTY, threads, progressEveryMs = 50) { last = it } } }
            delay(millis)
            job.cancel()
        }
        return last.hashesPerSecond
    }

    private fun meets(hash: ByteArray, difficulty: Int): Boolean {
        val full = difficulty ushr 3
        for (i in 0 until full) if (hash[i].toInt() != 0) return false
        val rest = difficulty and 7
        return rest == 0 || (hash[full].toInt() and 0xff) ushr (8 - rest) == 0
    }

    /** Writes [v] (≥ 0) as ASCII digits at the end of [buf]; returns how many. */
    private fun writeDecimal(v: Long, buf: ByteArray): Int {
        var x = v
        var i = buf.size
        do {
            buf[--i] = ('0'.code + (x % 10).toInt()).toByte()
            x /= 10
        } while (x != 0L)
        return buf.size - i
    }
}
