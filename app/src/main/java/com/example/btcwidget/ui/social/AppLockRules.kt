package com.example.btcwidget.ui.social

/** What the app lock guards. */
enum class LockScope(val id: String, val title: String, val summary: String) {
    APP("app", "Whole app", "Block asks each time it opens"),
    SENSITIVE("sensitive", "Only private things", "Your private key, wallet payments and zaps, and private messages. The rest of Block opens freely.");

    companion object {
        fun of(id: String?) = entries.firstOrNull { it.id == id } ?: APP
    }
}

/** How long Block can be away (in the background) before it asks again. */
enum class LockAfter(val ms: Long, val label: String) {
    NOW(0, "Immediately"),
    MIN1(60_000, "After 1 minute"),
    MIN5(5 * 60_000, "After 5 minutes"),
    MIN30(30 * 60_000, "After 30 minutes");

    companion object {
        fun of(ms: Long) = entries.firstOrNull { it.ms == ms } ?: NOW
    }
}

/** When to lock and what to ask for. Pure, so the timing can be tested on the JVM. */
object LockRules {
    /** Whole-app lock: locked as Block starts. */
    fun lockedAtStart(hasPin: Boolean, scope: LockScope) = hasPin && scope == LockScope.APP

    /**
     * Whole-app lock with no grace time: lock as soon as Block goes to the background, so its
     * content doesn't show in Recents. With a grace time it's decided on return instead.
     */
    fun lockOnLeave(hasPin: Boolean, scope: LockScope, after: LockAfter) = lockedAtStart(hasPin, scope) && after == LockAfter.NOW

    /** Back after being away since [leftAt]: has the grace time run out? */
    fun expired(leftAt: Long?, now: Long, after: LockAfter): Boolean = leftAt != null && now - leftAt >= after.ms

    /** Private-things lock: does opening one need the PIN (or face) first? */
    fun needsUnlock(hasPin: Boolean, scope: LockScope, unlockedThisSession: Boolean) =
        hasPin && scope == LockScope.SENSITIVE && !unlockedThisSession
}
