package com.example.btcwidget.nostr.core

/** What instant notifications listen for on the user's relays. */
object NotificationFilters {
    /** Gift wraps (NIP-59) are back-dated by up to two days to hide when they were sent. */
    const val WRAP_BACKDATE_SECONDS = 2 * 24 * 3600L

    /**
     * New replies, mentions, reposts, reactions and zaps to [me], and private messages, from
     * [now] on. `limit = 0` asks relays only for what arrives from now on: whatever came while
     * nothing was listening is caught up separately. Gift wraps' window starts two days back,
     * since a wrap sent now can carry an older timestamp.
     */
    fun live(me: String, now: Long): List<Filter> = listOf(
        Filter(kinds = listOf(Kind.TEXT_NOTE, Kind.REPOST, Kind.REACTION, Kind.ZAP_RECEIPT), tags = mapOf("p" to listOf(me)), since = now - 60, limit = 0),
        Filter(kinds = listOf(Nip17.GIFT_WRAP), tags = mapOf("p" to listOf(me)), since = now - WRAP_BACKDATE_SECONDS - 60, limit = 0)
    )
}
