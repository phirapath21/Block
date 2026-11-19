package com.example.btcwidget.nostr

import com.example.btcwidget.nostr.core.Filter
import com.example.btcwidget.nostr.core.Kind
import com.example.btcwidget.nostr.core.Nip05
import com.example.btcwidget.nostr.core.Nip19
import com.example.btcwidget.nostr.core.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** Search: what a search box entry points straight at, and people and notes matching text (NIP-50). */
class Search internal constructor(private val repo: NostrRepository) {

    /** What a search box entry points straight at. */
    sealed interface Direct {
        data class ProfileTarget(val pubkey: String) : Direct
        data class NoteTarget(val id: String) : Direct
        data class HashtagTarget(val tag: String) : Direct
        data class ArticleTarget(val address: Nip19.Address) : Direct
    }

    /** npub/nprofile/note/nevent/hex, "#tag" or a NIP-05 address; null for free text. */
    suspend fun resolve(query: String): Direct? {
        val q = query.trim()
        when (val e = Nip19.decode(q)) {
            is Nip19.Pubkey -> return Direct.ProfileTarget(e.hex)
            is Nip19.Note -> return Direct.NoteTarget(e.id)
            is Nip19.Address -> if (e.kind == Kind.ARTICLE) return Direct.ArticleTarget(e)
            else -> Unit
        }
        if (q.startsWith("#") && q.length > 1 && q.drop(1).all { it.isLetterOrDigit() || it == '_' }) return Direct.HashtagTarget(q.drop(1))
        if (Nip05.looks(q) && q.contains('@')) {
            val url = Nip05.url(q) ?: return null
            val name = Nip05.parts(q)!!.first
            val pubkey = runCatching { withContext(Dispatchers.IO) { Nip05.pubkeyFrom(repo.httpGet(url), name) } }.getOrNull()
            if (pubkey != null) return Direct.ProfileTarget(pubkey)
        }
        return null
    }

    /** People matching [query] (NIP-50 on profile metadata), most followed first per the relay. */
    suspend fun people(query: String): List<Profile> {
        val events = repo.searchPool.query(listOf(Filter(kinds = listOf(Kind.METADATA), search = query.trim(), limit = 30)), 6_000)
        events.forEach { repo.remember(it) }
        val wanted = events.map { it.pubkey }.distinct()
        val muted = repo.muted.value
        return wanted.mapNotNull { repo.profiles.value[it] }.filter { it.pubkey !in muted && repo.trusted(it.pubkey) }
    }

    /** Notes matching [query] (NIP-50 + local SQLite full-text search for instant/offline results). */
    suspend fun notes(query: String): List<FeedItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val found = coroutineScope {
            val remote = async { runCatching { repo.searchPool.query(listOf(Filter(kinds = listOf(Kind.TEXT_NOTE), search = q, limit = 40)), 6_000) }.getOrDefault(emptyList()) }
            val local = async(Dispatchers.IO) { repo.events.search(q, kinds = listOf(Kind.TEXT_NOTE), limit = 40) }
            (remote.await() + local.await()).distinctBy { it.id }
        }
        return repo.buildTimeline(found)
    }
}
