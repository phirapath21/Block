package com.example.btcwidget.nostr.core

/** A feed the user can pick for Home: who they follow, a topic, or a list. Saved as a short string. */
sealed interface HomeFeed {
    val title: String
    val subtitle: String
    val id: String

    /** Notes by the people you follow; [replies] includes their replies. */
    data class Following(val replies: Boolean) : HomeFeed {
        override val title get() = if (replies) "Latest with replies" else "Latest"
        override val subtitle get() = if (replies) "Notes and replies by your follows" else "Latest notes by your follows"
        override val id get() = if (replies) "following+replies" else "following"
    }

    data class Hashtag(val tag: String) : HomeFeed {
        override val title get() = "Topic: $tag"
        override val subtitle get() = "All notes tagged #$tag"
        override val id get() = "tag:$tag"
    }

    /** Notes by the people in a list (NIP-51 kind 30000), yours or someone else's. */
    data class People(val owner: String, val d: String, val name: String) : HomeFeed {
        override val title get() = "List: $name"
        override val subtitle get() = "Notes by the people in this list"
        override val id get() = "list:$owner:$d:$name"
    }

    companion object {
        val DEFAULTS: List<HomeFeed> = listOf(Following(false), Following(true))

        fun parse(id: String): HomeFeed? = when {
            id == "following" -> Following(false)
            id == "following+replies" -> Following(true)
            id.startsWith("tag:") -> cleanTag(id.removePrefix("tag:"))?.let { Hashtag(it) }
            id.startsWith("list:") -> id.removePrefix("list:").split(':', limit = 3).takeIf { it.size == 3 && Hex.isKey(it[0]) && it[1].isNotEmpty() }
                ?.let { People(it[0], it[1], it[2].ifBlank { "List" }) }
            // Feeds that no longer exist (global, relay, DVM, pictures, videos) are dropped.
            else -> null
        }

        /** "#SiamStr " → "siamstr"; null if it isn't a usable hashtag. */
        fun cleanTag(input: String): String? =
            input.trim().removePrefix("#").lowercase().takeIf { it.isNotEmpty() && it.length <= 60 && it.all { c -> c.isLetterOrDigit() || c == '_' || Character.getType(c) == Character.NON_SPACING_MARK.toInt() || Character.getType(c) == Character.COMBINING_SPACING_MARK.toInt() } }
    }
}
