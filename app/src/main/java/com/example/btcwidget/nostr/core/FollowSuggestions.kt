package com.example.btcwidget.nostr.core

/**
 * Who to follow: accounts that many of the people you follow also follow, most shared first.
 * [contactLists] maps each followed account to whom they follow.
 */
object FollowSuggestions {
    fun rank(me: String, following: Set<String>, contactLists: Map<String, Collection<String>>, exclude: Set<String> = emptySet(), limit: Int = 30): List<Pair<String, Int>> {
        val counts = HashMap<String, Int>()
        contactLists.forEach { (owner, list) ->
            if (owner !in following) return@forEach
            list.toSet().forEach { pk ->
                if (pk != me && pk !in following && pk !in exclude && Hex.isKey(pk)) counts[pk] = (counts[pk] ?: 0) + 1
            }
        }
        return counts.entries.filter { it.value >= 2 }.sortedByDescending { it.value }.take(limit).map { it.key to it.value }
    }
}
