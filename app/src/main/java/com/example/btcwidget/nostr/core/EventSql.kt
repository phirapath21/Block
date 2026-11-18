package com.example.btcwidget.nostr.core

/**
 * SQL for the local event database, kept free of Android so it can be tested against
 * SQLite on the JVM. Events are stored as their signed JSON, with the columns and tags
 * needed to answer NIP-01 filters locally.
 */
object EventSql {
    const val VERSION = 1

    val SCHEMA = listOf(
        """CREATE TABLE IF NOT EXISTS events (
            id TEXT PRIMARY KEY,
            pubkey TEXT NOT NULL,
            kind INTEGER NOT NULL,
            created_at INTEGER NOT NULL,
            d TEXT,
            json TEXT NOT NULL,
            stored_at INTEGER NOT NULL
        )""",
        "CREATE INDEX IF NOT EXISTS events_author ON events(pubkey, kind, created_at)",
        "CREATE INDEX IF NOT EXISTS events_kind ON events(kind, created_at)",
        """CREATE TABLE IF NOT EXISTS tags (
            event_id TEXT NOT NULL,
            name TEXT NOT NULL,
            value TEXT NOT NULL
        )""",
        "CREATE INDEX IF NOT EXISTS tags_value ON tags(name, value)",
        "CREATE INDEX IF NOT EXISTS tags_event ON tags(event_id)"
    )

    /** Single-letter tags are the ones relays index (NIP-01); only those are stored. */
    fun indexedTags(e: Event): List<Pair<String, String>> =
        e.tags.filter { it.size >= 2 && it[0].length == 1 && it[1].length <= 200 }.map { it[0] to it[1] }.distinct()

    /** Replaceable (0, 3, 10000–19999) and addressable (30000–39999) kinds keep only the newest. */
    fun isReplaceable(kind: Int) = kind == 0 || kind == 3 || kind in 10_000..19_999
    fun isAddressable(kind: Int) = kind in 30_000..39_999

    fun dTag(e: Event): String? = if (isAddressable(e.kind)) e.firstTag("d")?.get(1) ?: "" else null

    /** A statement with its arguments. */
    data class Statement(val sql: String, val args: List<Any>)

    /** Older versions an incoming replaceable or addressable event supersedes (to delete first). */
    fun olderVersions(e: Event): Statement? = when {
        isReplaceable(e.kind) -> Statement("SELECT id FROM events WHERE pubkey = ? AND kind = ? AND created_at < ?", listOf(e.pubkey, e.kind, e.createdAt))
        isAddressable(e.kind) -> Statement("SELECT id FROM events WHERE pubkey = ? AND kind = ? AND d = ? AND created_at < ?", listOf(e.pubkey, e.kind, dTag(e) ?: "", e.createdAt))
        else -> null
    }

    /** True when a newer version is already stored, so [e] should be skipped. */
    fun newerExists(e: Event): Statement? = when {
        isReplaceable(e.kind) -> Statement("SELECT 1 FROM events WHERE pubkey = ? AND kind = ? AND created_at >= ? LIMIT 1", listOf(e.pubkey, e.kind, e.createdAt))
        isAddressable(e.kind) -> Statement("SELECT 1 FROM events WHERE pubkey = ? AND kind = ? AND d = ? AND created_at >= ? LIMIT 1", listOf(e.pubkey, e.kind, dTag(e) ?: "", e.createdAt))
        else -> null
    }

    const val INSERT_EVENT = "INSERT OR IGNORE INTO events (id, pubkey, kind, created_at, d, json, stored_at) VALUES (?, ?, ?, ?, ?, ?, ?)"
    const val INSERT_TAG = "INSERT INTO tags (event_id, name, value) VALUES (?, ?, ?)"
    const val DELETE_EVENT = "DELETE FROM events WHERE id = ?"
    const val DELETE_TAGS = "DELETE FROM tags WHERE event_id = ?"

    /** Values bound to [INSERT_EVENT] for [e]. */
    fun insertArgs(e: Event, now: Long): List<Any?> = listOf(e.id, e.pubkey, e.kind, e.createdAt, dTag(e), e.toJson(), now)

    /** SELECT json for a NIP-01 filter, newest first. A search term can't be answered locally. */
    fun select(f: Filter, defaultLimit: Int = 500): Statement? {
        if (f.search != null) return null
        val where = ArrayList<String>()
        val args = ArrayList<Any>()
        fun inList(column: String, values: List<Any>) {
            where += "$column IN (" + values.joinToString(",") { "?" } + ")"
            args.addAll(values)
        }
        f.ids?.let { if (it.isEmpty()) return null; inList("e.id", it) }
        f.authors?.let { if (it.isEmpty()) return null; inList("e.pubkey", it) }
        f.kinds?.let { if (it.isEmpty()) return null; inList("e.kind", it) }
        f.since?.let { where += "e.created_at >= ?"; args += it }
        f.until?.let { where += "e.created_at <= ?"; args += it }
        f.tags.forEach { (name, values) ->
            if (values.isEmpty()) return null
            where += "e.id IN (SELECT event_id FROM tags WHERE name = ? AND value IN (" + values.joinToString(",") { "?" } + "))"
            args += name
            args.addAll(values)
        }
        val limit = (f.limit ?: defaultLimit).coerceIn(1, 5_000)
        val sql = "SELECT e.json FROM events e" + (if (where.isEmpty()) "" else " WHERE " + where.joinToString(" AND ")) +
            " ORDER BY e.created_at DESC LIMIT $limit"
        return Statement(sql, args)
    }

    /**
     * Full-text search over locally stored events of [kinds], newest first. Each word in
     * [query] must appear in the event JSON; caller further checks [matchesSearch] on content.
     */
    fun search(query: String, kinds: List<Int> = Kind.POSTS, limit: Int = 50): Statement? {
        val terms = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(5)
        if (terms.isEmpty() || kinds.isEmpty()) return null
        val where = ArrayList<String>()
        val args = ArrayList<Any>()
        where += "e.kind IN (" + kinds.joinToString(",") { "?" } + ")"
        args.addAll(kinds)
        for (term in terms) {
            val escaped = term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            where += "e.json LIKE ? ESCAPE '\\'"
            args += "%$escaped%"
        }
        val cap = limit.coerceIn(1, 500)
        val sql = "SELECT e.json FROM events e WHERE " + where.joinToString(" AND ") + " ORDER BY e.created_at DESC LIMIT $cap"
        return Statement(sql, args)
    }

    /** True when every word in [query] appears in [e]'s content or title/summary/t tags. */
    fun matchesSearch(e: Event, query: String): Boolean {
        val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return false
        val haystack = buildString {
            append(e.content.lowercase())
            for (tag in e.tags) {
                if (tag.size >= 2 && tag[0] in setOf("t", "title", "summary", "subject")) {
                    append(' ').append(tag[1].lowercase())
                }
            }
        }
        return terms.all { it in haystack }
    }

    /**
     * Keeps the database to about [keep] events: the oldest are dropped first, except the
     * user's own events and the newest of every replaceable list (profiles, follows).
     */
    fun prune(keep: Int, me: String?): List<Statement> = prune(keep, setOfNotNull(me), emptySet())

    /**
     * Kinds never pruned: profiles, follow and relay lists (the newest of each is needed), and
     * private messages (legacy DMs and gift wraps: their sender's key is a throwaway, so "mine"
     * can't protect them, and deleting them would empty chats while offline).
     */
    const val KEPT_KINDS = "0, 3, 4, 1059, 10002"

    /**
     * Like [prune] for several accounts, also keeping [protectedIds] (bookmarks). Those go
     * through a temporary table rather than one SQL parameter each, which would fail past
     * SQLite's 999-parameter limit (and the database would then never be pruned).
     */
    fun prune(keep: Int, myPubkeys: Set<String>, protectedIds: Set<String>): List<Statement> {
        val where = mutableListOf("kind NOT IN ($KEPT_KINDS)")
        val args = mutableListOf<Any>()
        if (myPubkeys.isNotEmpty()) {
            where += "pubkey NOT IN (${myPubkeys.joinToString(",") { "?" }})"
            args.addAll(myPubkeys)
        }
        where += "id NOT IN (SELECT id FROM prune_keep)"
        args += keep
        return listOf(
            Statement("CREATE TEMP TABLE IF NOT EXISTS prune_keep (id TEXT PRIMARY KEY)", emptyList()),
            Statement("DELETE FROM prune_keep", emptyList())
        ) + protectedIds.map { Statement("INSERT OR IGNORE INTO prune_keep (id) VALUES (?)", listOf(it)) } + listOf(
            Statement(
                "DELETE FROM events WHERE id IN (SELECT id FROM events WHERE ${where.joinToString(" AND ")} " +
                    "ORDER BY created_at DESC LIMIT -1 OFFSET ?)",
                args
            ),
            Statement("DELETE FROM tags WHERE event_id NOT IN (SELECT id FROM events)", emptyList()),
            Statement("DELETE FROM prune_keep", emptyList())
        )
    }
}
