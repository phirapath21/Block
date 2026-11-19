package com.example.btcwidget.nostr

import android.content.Context

/**
 * Whether the user has saved the private key of an account created in Block, so the app can
 * remind them until they do: on Nostr a lost key can't be reset, and the account is gone.
 *
 * Only accounts created here are tracked (a key typed in to log in is one the user already has;
 * a signer app or bunker keeps the key itself). Kept in its own prefs file, "key_backup": two
 * flags per account, nothing secret. Excluded from backup like the account itself.
 */
object KeyBackup {
    private const val FILE = "key_backup"
    /** "Later" hides the reminder for this long. */
    const val SNOOZE_MS = 3L * 24 * 60 * 60 * 1000

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private fun neededKey(pubkey: String) = "needed_$pubkey"
    private fun snoozeKey(pubkey: String) = "snoozed_until_$pubkey"
    private fun graceKey(pubkey: String) = "grace_$pubkey"

    /** A new account isn't asked on day one: the reminder starts after this, or after the first post. */
    const val GRACE_MS = 24L * 60 * 60 * 1000

    /** A new account's key was just made here and shown once. */
    fun markNeeded(context: Context, pubkey: String) = prefs(context).edit().putBoolean(neededKey(pubkey), true).remove(snoozeKey(pubkey)).apply()

    /** A key made without showing it (the new sign-up): remind after [GRACE_MS], or once they've posted. */
    fun markNeededLater(context: Context, pubkey: String, now: Long = System.currentTimeMillis()) = prefs(context).edit()
        .putBoolean(neededKey(pubkey), true).putLong(snoozeKey(pubkey), now + GRACE_MS).putBoolean(graceKey(pubkey), true).apply()

    /** They posted: a new account's first-day grace ends now (a "Later" they chose still stands). */
    fun posted(context: Context, pubkey: String) {
        val p = prefs(context)
        if (p.getBoolean(graceKey(pubkey), false)) p.edit().remove(graceKey(pubkey)).remove(snoozeKey(pubkey)).apply()
    }

    /** The user copied the key or said they saved it. */
    fun markSaved(context: Context, pubkey: String) = forget(context, pubkey)

    fun needed(context: Context, pubkey: String): Boolean = prefs(context).getBoolean(neededKey(pubkey), false)

    fun snoozedUntil(context: Context, pubkey: String): Long = prefs(context).getLong(snoozeKey(pubkey), 0L)

    fun snooze(context: Context, pubkey: String, now: Long = System.currentTimeMillis()) =
        prefs(context).edit().putLong(snoozeKey(pubkey), now + SNOOZE_MS).remove(graceKey(pubkey)).apply()

    /** The account left this phone (logged out or removed): nothing to remind about. */
    fun forget(context: Context, pubkey: String) = prefs(context).edit().remove(neededKey(pubkey)).remove(snoozeKey(pubkey)).remove(graceKey(pubkey)).apply()

    /** Whether to show the reminder now: the key is on this phone, not saved, and not snoozed. */
    fun shouldRemind(needed: Boolean, keyOnPhone: Boolean, snoozedUntil: Long, now: Long): Boolean =
        needed && keyOnPhone && now >= snoozedUntil
}
