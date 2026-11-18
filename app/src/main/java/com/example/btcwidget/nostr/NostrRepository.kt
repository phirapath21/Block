package com.example.btcwidget.nostr

import android.content.Context
import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Events
import com.example.btcwidget.nostr.core.Filter
import com.example.btcwidget.nostr.core.ReplyTree
import com.example.btcwidget.nostr.core.Kind
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.nostr.core.ThreadRef
import com.example.btcwidget.nostr.core.UnsignedEvent
import com.example.btcwidget.nostr.core.Zaps
import com.example.btcwidget.nostr.core.normalizeRelayUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap

/** Likes, reposts, replies and zaps on a note, and whether the user did each. */
@androidx.compose.runtime.Immutable
data class NoteStats(
    val replies: Int = 0,
    val reposts: Int = 0,
    val likes: Int = 0,
    val zapMsat: Long = 0,
    val likedByMe: Boolean = false,
    val repostedByMe: Boolean = false,
    /** The user's own reaction ("+" for a like, or an emoji), when [likedByMe]. */
    val myReaction: String? = null
)

/** One row of a timeline: a note, or a repost of one. */
@androidx.compose.runtime.Immutable
data class FeedItem(val note: Event, val repostedBy: String? = null, val sortTime: Long = note.createdAt) {
    val key: String = (repostedBy ?: "") + note.id
}

/** A notification about the user's notes or account. */
/**
 * Put in the coroutine context of an action the user is waiting on (repost, reply, post) to
 * hear how [NostrRepository.publish] is getting on: signing, then relays answering.
 */
class PublishTracker(val onStage: (Stage) -> Unit) : kotlin.coroutines.AbstractCoroutineContextElement(PublishTracker) {
    companion object Key : kotlin.coroutines.CoroutineContext.Key<PublishTracker>

    /** Set to stop mining and publish the event without proof of work. */
    val skipPow = java.util.concurrent.atomic.AtomicBoolean(false)

    sealed interface Stage {
        /** Mining proof of work (NIP-13) for [difficulty] bits: [hashes] tried in [elapsedMs]. */
        data class Mining(val difficulty: Int, val hashes: Long, val elapsedMs: Long) : Stage {
            val hashesPerSecond: Double get() = if (elapsedMs <= 0) 0.0 else hashes * 1000.0 / elapsedMs
        }
        /** Waiting for the signature; [signer] says whose (an app or a remote signer can take a while). */
        data class Signing(val signer: SignerType?) : Stage
        /** Sent to [total] relays; [answered] have replied, [accepted] of them took it. */
        data class Sending(val answered: Int, val accepted: Int, val total: Int) : Stage
    }
}

data class NostrNotification(
    val event: Event,
    val type: Type,
    /** Who acted (the zapper for zaps). */
    val actor: String,
    /** The user's note it concerns, if any. */
    val noteId: String?,
    val zapMsat: Long = 0
) {
    enum class Type { REPLY, MENTION, REPOST, LIKE, ZAP }
}

/**
 * Nostr data for the app: the account, relays, an in-memory cache of notes, profiles
 * and stats, and every action the user can take. One instance for the process.
 */
class NostrRepository private constructor(private val context: Context) {
    val pool = RelayPool()

    /**
     * Indexer relays that collect profiles, follow lists and relay lists from everyone
     * (as Amethyst uses): profiles load even when people post to relays we don't use.
     */
    internal val indexers = RelayPool().also { it.setRelays(INDEXER_RELAYS) }

    /** Background work (profiles, counts, cache writes) that screens don't wait for. */
    internal val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    internal val cache = NostrCache(java.io.File(context.cacheDir, "nostr"))
    /** The local event database (SQLite): what was seen, for instant and offline screens. */
    internal val events = EventStore(context)
    private val toStore = java.util.concurrent.ConcurrentLinkedQueue<Event>()
    @Volatile private var storeJob: Job? = null

    /** Private messages (NIP-17). */
    val dms: DirectMessages by lazy { DirectMessages(this) }
    /** Communities, public chats and groups. */
    val spaces: Spaces by lazy { Spaces(this) }
    val wallet: NwcWallet by lazy { NwcWallet(this) }
    val linkPreviews: LinkPreviews by lazy { LinkPreviews(this) }
    val market: MarketListings by lazy { MarketListings(this) }
    val polls: Polls by lazy { Polls(this) }
    val highlights: ArticleHighlights by lazy { ArticleHighlights(this) }
    val badges: ProfileBadges by lazy { ProfileBadges(this) }
    val zaps: ZapInvoices by lazy { ZapInvoices(this) }
    val articles: Articles by lazy { Articles(this) }
    val dvm: DvmFeeds by lazy { DvmFeeds(this) }
    val peopleLists: PeopleLists by lazy { PeopleLists(this) }

    /** True once the account's lists were read from relays this session (not just the cache). */
    @Volatile private var accountFresh = false

    private val _account = MutableStateFlow(NostrAccountStore.account(context))
    val account: StateFlow<NostrAccount?> = _account.asStateFlow()

    private val _profiles = MutableStateFlow<Map<String, Profile>>(emptyMap())
    /** Every profile seen so far, by pubkey. */
    val profiles: StateFlow<Map<String, Profile>> = _profiles.asStateFlow()

    private val _stats = MutableStateFlow<Map<String, NoteStats>>(emptyMap())
    val stats: StateFlow<Map<String, NoteStats>> = _stats.asStateFlow()

    private val _following = MutableStateFlow<Set<String>>(emptySet())
    /** Follow changes shown already but not yet published: pubkey to follow (true) or unfollow. */
    private val pendingFollows = ConcurrentHashMap<String, Boolean>()
    private val contactsLock = kotlinx.coroutines.sync.Mutex()
    /** Pubkeys the user follows. */
    val following: StateFlow<Set<String>> = _following.asStateFlow()

    /** Words, hashtags and threads from the mute list (accounts are in [muted]). */
    private val _mutes = MutableStateFlow(com.example.btcwidget.nostr.core.MuteRules())
    val mutes: StateFlow<com.example.btcwidget.nostr.core.MuteRules> = _mutes.asStateFlow()
    /** Follows and their follows, when the web-of-trust filter is on and loaded. */
    @Volatile private var trust: com.example.btcwidget.nostr.core.TrustNetwork? = null
    private val _trustSize = MutableStateFlow(0)
    /** Number of trusted pubkeys in the 2-hop Web of Trust (0 when off or not yet loaded). */
    val trustSize: StateFlow<Int> = _trustSize.asStateFlow()
    private val _muted = MutableStateFlow<Set<String>>(emptySet())
    /** Pubkeys the user muted (NIP-51 kind 10000); hidden everywhere. */
    val muted: StateFlow<Set<String>> = _muted.asStateFlow()

    /**
     * Notes seen this session, the most recently used [MAX_NOTES] (about 10 MB). It used to keep
     * every note ever seen until the app was closed, so a long scroll through Global kept
     * growing it; older ones are still in the local database and are fetched again if needed.
     */
    internal val notes: MutableMap<String, Event> = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, Event>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Event>?) = size > MAX_NOTES
        }
    )
    private val profileEvents = ConcurrentHashMap<String, Event>()
    private var contactsEvent: Event? = null
    private var muteEvent: Event? = null
    private var bookmarkEvent: Event? = null
    private var pinEvent: Event? = null
    private var emojiListEvent: Event? = null
    /** The user's custom emoji (NIP-30 kind 10030 list), shortcode to image. */
    private val _emojis = MutableStateFlow<Map<String, String>>(emptyMap())
    val emojis: StateFlow<Map<String, String>> = _emojis.asStateFlow()

    private val _pinned = MutableStateFlow<List<String>>(emptyList())
    /** The user's pinned note ids (NIP-51 kind 10001), first shown first. */
    val pinned: StateFlow<List<String>> = _pinned.asStateFlow()

    private val _followedTags = MutableStateFlow<Set<String>>(emptySet())
    /** Hashtags the user follows (lowercase), from the "t" tags of their contact list. */
    val followedTags: StateFlow<Set<String>> = _followedTags.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<String>>(emptyList())
    /** Note ids the user bookmarked (NIP-51 kind 10003), newest first. */
    val bookmarks: StateFlow<List<String>> = _bookmarks.asStateFlow()
    /** The user's Blossom servers (BUD-03 kind 10063), empty for the default. */
    private var mediaServers: List<String> = emptyList()
    // Declared before init: the cache loader started there may set them.
    private var relayList: Event? = null
    private var mediaServerList: Event? = null
    private var signer: NostrSigner? = NostrAccountStore.signer(context)

    val pubkey: String? get() = _account.value?.pubkey

    internal val appContext: Context get() = context

    // Outbox state: declared before init, whose cache loader fills it.
    /** Connections to other people's write relays (separate from the user's own relays). */
    internal val outbox = RelayPool()

    /** pubkey → write relays from their kind-10002 list; an empty list means "has none". */
    private val writeRelays = ConcurrentHashMap<String, List<String>>()
    /** pubkey → read (inbox) relays from their kind-10002 list; an empty list means "has none". */
    private val readRelays = ConcurrentHashMap<String, List<String>>()
    private val relayListEvents = ConcurrentHashMap<String, Event>()

    /** Completes once the disk cache (profiles, the account's lists) is loaded. */
    val cacheReady: Job

    // Declared before init: the cached account is applied from a coroutine started there.
    internal val searchPool by lazy { RelayPool().also { it.setRelays(_searchRelays.value.ifEmpty { SEARCH_RELAYS }) } }

    // ---- Relay lists: NIP-65 (read/write), search (10007) and blocked (10006) ----

    @Volatile private var searchListEvent: Event? = null
    @Volatile private var blockedListEvent: Event? = null
    private val _relayEntries = MutableStateFlow<List<com.example.btcwidget.nostr.core.RelayLists.Entry>>(emptyList())
    /** The user's published NIP-65 relays, with read/write markers. */
    val relayEntries: StateFlow<List<com.example.btcwidget.nostr.core.RelayLists.Entry>> = _relayEntries.asStateFlow()
    private val _searchRelays = MutableStateFlow<List<String>>(emptyList())
    /** The user's search relays (empty: Block's defaults). */
    val searchRelays: StateFlow<List<String>> = _searchRelays.asStateFlow()
    private val _blockedRelays = MutableStateFlow<List<String>>(emptyList())
    val blockedRelays: StateFlow<List<String>> = _blockedRelays.asStateFlow()

    init {
        pool.setRelays(NostrAccountStore.relays(context) ?: RelayPool.DEFAULT_RELAYS)
        // Show what we knew last time straight away; relays refresh it.
        cacheReady = scope.launch(Dispatchers.IO) {
            // The database, plus the JSON files earlier versions wrote (moved into it once).
            val legacyProfiles = cache.loadEvents(PROFILES_CACHE)
            val legacyRelayLists = cache.loadEvents(RELAY_LIST_CACHE)
            if (legacyProfiles.isNotEmpty() || legacyRelayLists.isNotEmpty()) {
                events.insert(legacyProfiles + legacyRelayLists)
                cache.delete(PROFILES_CACHE); cache.delete(RELAY_LIST_CACHE)
            }
            rememberProfiles(events.query(listOf(Filter(kinds = listOf(Kind.METADATA), limit = 3_000))), save = false)
            events.query(listOf(Filter(kinds = listOf(Kind.RELAY_LIST), limit = 3_000))).forEach { e ->
                relayListEvents[e.pubkey] = e
                writeRelays[e.pubkey] = com.example.btcwidget.nostr.core.Outbox.writeRelays(e)
                readRelays[e.pubkey] = com.example.btcwidget.nostr.core.Outbox.readRelays(e)
            }
            pubkey?.let { me -> applyAccountEvents(cache.loadEvents(ACCOUNT_CACHE).filter { it.pubkey == me }, fromRelays = false) }
            events.prune(keep = 50_000, myPubkeys = ownedPubkeys(), protectedIds = _bookmarks.value.toSet())
            val retentionDays = com.example.btcwidget.AppPrefs.eventRetentionDays(context)
            if (retentionDays > 0) {
                events.pruneBefore(System.currentTimeMillis() / 1000 - retentionDays * 86_400L, ownedPubkeys(), _bookmarks.value.toSet())
            }
        }
    }

    /** Both the user's relays and the indexers, merged (for profiles and lists). */
    internal suspend fun queryEverywhere(filters: List<Filter>, timeoutMs: Long = 4_000): List<Event> = coroutineScope {
        val a = async { pool.query(filters, timeoutMs, graceMs = 700) }
        val b = async { indexers.query(filters, timeoutMs, graceMs = 700) }
        (a.await() + b.await()).distinctBy { it.id }
    }

    // ---- Account ----

    suspend fun loginWithAmber() {
        val (pubkey, pkg) = AmberSigner.connect()
        NostrAccountStore.saveAmber(context, pubkey, pkg)
        onLoggedIn()
    }

    /** Logs in through a NIP-46 bunker:// link; waits for the signer to approve. */
    suspend fun loginWithBunker(uri: String) {
        val bunker = com.example.btcwidget.nostr.core.Nip46.parseBunker(uri)
            ?: throw IllegalArgumentException("That isn't a bunker:// link")
        val client = com.example.btcwidget.nostr.core.Schnorr.newSecretKey()
        val pairing = BunkerSigner("", bunker.signerPubkey, bunker.relays, client)
        val user = try { pairing.connect(bunker.secret) } finally { pairing.close() }
        NostrAccountStore.saveBunker(context, user, bunker.signerPubkey, bunker.relays, client)
        onLoggedIn()
    }

    /** Accepts an nsec (or 64-char hex). Throws IllegalArgumentException if invalid. */
    fun loginWithSecret(input: String) {
        val trimmed = input.trim()
        val hex = when (val e = com.example.btcwidget.nostr.core.Nip19.decode(trimmed)) {
            is com.example.btcwidget.nostr.core.Nip19.SecretKey -> e.hex
            null -> trimmed.lowercase().takeIf { com.example.btcwidget.nostr.core.Hex.isKey(it) }
            else -> null
        } ?: throw IllegalArgumentException("That isn't an nsec private key")
        NostrAccountStore.saveLocal(context, hex)
        onLoggedIn()
    }

    /** Creates a fresh key pair and logs in with it. Returns the new nsec to show once for backup. */
    fun createAccount(): String {
        val seckey = com.example.btcwidget.nostr.core.Schnorr.newSecretKey()
        NostrAccountStore.saveLocal(context, seckey)
        onLoggedIn()
        return com.example.btcwidget.nostr.core.Nip19.nsec(seckey)
    }

    private fun onLoggedIn() {
        val account = NostrAccountStore.account(context)
        // Another account was active: drop everything loaded for it first.
        if (_account.value != null && _account.value?.pubkey != account?.pubkey) resetSession()
        _account.value = account
        (signer as? BunkerSigner)?.close()
        signer = NostrAccountStore.signer(context)
        wallet.reload()
        _savedAccounts.value = NostrAccountStore.savedAccounts(context)
    }

    private val _savedAccounts = MutableStateFlow(NostrAccountStore.savedAccounts(context))
    /** Other accounts on this phone that the user can switch to. */
    val savedAccounts: StateFlow<List<NostrAccount>> = _savedAccounts.asStateFlow()

    /** Switches to a saved account; the current one is saved aside. */
    fun switchAccount(pubkey: String) {
        NostrAccountStore.switchTo(context, pubkey)
        resetSession()
        onLoggedIn()
    }

    fun removeSavedAccount(pubkey: String) {
        NostrAccountStore.removeSaved(context, pubkey)
        Drafts.clear(context, pubkey)
        ScheduledPosts.clear(context, pubkey)
        DirectMessages.clearLocalModeration(context, pubkey)
        _savedAccounts.value = NostrAccountStore.savedAccounts(context)
    }

    fun logout() {
        pubkey?.let { Drafts.clear(context, it); ScheduledPosts.clear(context, it); DirectMessages.clearLocalModeration(context, it) }
        NostrAccountStore.clear(context)
        resetSession()
        _savedAccounts.value = NostrAccountStore.savedAccounts(context)
    }

    /** Forgets everything loaded for the active account (not what's stored for it). */
    private fun resetSession() {
        // Decrypted copies of the last account's message files go with it.
        DmMediaCache.clear(context)
        (signer as? BunkerSigner)?.close()
        dms.reset()
        spaces.reset()
        wallet.forget()
        pool.setRelays(NostrAccountStore.relays(context) ?: RelayPool.DEFAULT_RELAYS)
        relayList = null
        mediaServerList = null
        mediaServers = emptyList()
        cache.clear()
        toStore.clear()
        scope.launch(Dispatchers.IO) { this@NostrRepository.events.clear() }
        accountFresh = false
        _account.value = null
        signer = null
        contactsEvent = null
        muteEvent = null
        bookmarkEvent = null
        pinEvent = null
        peopleLists.clear()
        privateMutes = null
        privateBookmarks = null
        _followedTags.value = emptySet()
        searchListEvent = null
        blockedListEvent = null
        _relayEntries.value = emptyList()
        _searchRelays.value = emptyList()
        _blockedRelays.value = emptyList()
        RelayPool.block(emptySet())
        _pinned.value = emptyList()
        emojiListEvent = null
        _emojis.value = emptyMap()
        _bookmarks.value = emptyList()
        pendingFollows.clear()
        _following.value = emptySet()
        _muted.value = emptySet()
        _mutes.value = com.example.btcwidget.nostr.core.MuteRules()
        trust = null
        _trustSize.value = 0
        followsLists = null
        _stats.value = emptyMap()
    }

    /** True when follows are known (from relays or the cache), so the home feed can start. */
    val hasCachedAccount: Boolean get() = contactsEvent != null

    /** Loads the user's relay list, contacts, mutes and profile from relays and indexers. */
    suspend fun loadAccount() {
        val me = pubkey ?: return
        val events = queryEverywhere(
            listOf(Filter(authors = listOf(me), kinds = listOf(Kind.METADATA, Kind.CONTACTS, Kind.MUTE_LIST, Kind.BOOKMARKS, Kind.PINNED, Kind.RELAY_LIST, MEDIA_SERVER_LIST, Kind.EMOJI_LIST, com.example.btcwidget.nostr.core.RelayLists.SEARCH, com.example.btcwidget.nostr.core.RelayLists.BLOCKED))),
            timeoutMs = 6_000
        )
        applyAccountEvents(events, fromRelays = true)
        saveAccount()
        scope.launch { runCatching { loadTrust() } }
    }

    private fun applyAccountEvents(events: List<Event>, fromRelays: Boolean) {
        fun latest(kind: Int) = events.filter { it.kind == kind }.maxByOrNull { it.createdAt }
        latest(Kind.METADATA)?.let { remember(it) }
        latest(Kind.CONTACTS)?.let { if (it.createdAt >= (contactsEvent?.createdAt ?: 0)) { contactsEvent = it; _following.value = withPendingFollows(it.tagValues("p").toSet()); _followedTags.value = followedTagsOf(it) } }
        latest(Kind.PINNED)?.let { if (it.createdAt >= (pinEvent?.createdAt ?: 0)) { pinEvent = it; _pinned.value = it.tagValues("e") } }
        latest(Kind.EMOJI_LIST)?.let { if (it.createdAt >= (emojiListEvent?.createdAt ?: 0)) { emojiListEvent = it; _emojis.value = com.example.btcwidget.nostr.core.CustomEmoji.of(it) } }
        latest(Kind.BOOKMARKS)?.let { if (it.createdAt >= (bookmarkEvent?.createdAt ?: 0)) { if (it.content != bookmarkEvent?.content) privateBookmarks = null; bookmarkEvent = it; applyBookmarks() } }
        latest(Kind.MUTE_LIST)?.let { if (it.createdAt >= (muteEvent?.createdAt ?: 0)) { if (it.content != muteEvent?.content) privateMutes = null; muteEvent = it; applyMutes() } }
        // Their private parts are encrypted: open them without prompting a signer app.
        scope.launch { runCatching { openPrivateLists(interactive = false) } }
        latest(MEDIA_SERVER_LIST)?.let { mediaServerList = it; mediaServers = com.example.btcwidget.nostr.core.Blossom.serversFrom(it) }
        latest(com.example.btcwidget.nostr.core.RelayLists.SEARCH)?.let { if (it.createdAt >= (searchListEvent?.createdAt ?: 0)) { searchListEvent = it; applySearchRelays() } }
        latest(com.example.btcwidget.nostr.core.RelayLists.BLOCKED)?.let { if (it.createdAt >= (blockedListEvent?.createdAt ?: 0)) { blockedListEvent = it; applyBlockedRelays() } }
        latest(Kind.RELAY_LIST)?.let { list ->
            relayList = list
            _relayEntries.value = com.example.btcwidget.nostr.core.RelayLists.entries(list)
            if (NostrAccountStore.relays(context) == null) {
                // NIP-65: add the user's relays to the defaults (a slow personal relay alone would slow everything).
                val urls = list.tags.filter { it.size >= 2 && it[0] == "r" }.mapNotNull { normalizeRelayUrl(it[1]) }
                if (urls.isNotEmpty()) pool.setRelays(poolFor(urls))
            }
        }
        if (fromRelays) accountFresh = true
    }

    private fun saveAccount() {
        val me = pubkey ?: return
        val events = listOfNotNull(profileEvents[me], contactsEvent, muteEvent, bookmarkEvent, pinEvent, relayList, mediaServerList, emojiListEvent, searchListEvent, blockedListEvent)
        scope.launch(Dispatchers.IO) { cache.saveEvents(ACCOUNT_CACHE, events) }
        events.forEach { store(it) }
    }

    /** The home timeline saved last time, for an instant first screen. */
    suspend fun cachedHome(): List<FeedItem> = withContext(Dispatchers.IO) {
        cache.loadFeed(HOME_CACHE).filter { !hidden(it.note) }.also { items ->
            items.forEach { remember(it.note) }
        }
    }

    fun saveHome(items: List<FeedItem>) {
        scope.launch(Dispatchers.IO) { cache.saveFeed(HOME_CACHE, items.take(60)) }
    }

    fun setRelays(urls: List<String>?) {
        val clean = urls?.mapNotNull { normalizeRelayUrl(it) }?.distinct()?.takeIf { it.isNotEmpty() }
        NostrAccountStore.setRelays(context, clean)
        pool.setRelays(clean ?: RelayPool.DEFAULT_RELAYS)
    }

    // ---- Reading ----

    /** Home timeline: notes and reposts from people the user follows (or themselves), newest first. */
    suspend fun homeFeed(until: Long? = null, limit: Int = 40): List<FeedItem> {
        val me = pubkey ?: return emptyList()
        // Following nobody yet: show your own notes plus #bitcoin, so the timeline isn't empty.
        if (_following.value.isEmpty()) {
            return timeline(
                Filter(authors = listOf(me), kinds = Kind.POSTS + Kind.REPOST, until = until, limit = limit),
                Filter(kinds = listOf(Kind.TEXT_NOTE), tags = mapOf("t" to listOf("bitcoin")), until = until, limit = limit)
            )
        }
        val authors = (_following.value + me).toList()
        val tags = _followedTags.value.toList()
        if (tags.isEmpty()) return authoredTimeline(authors) { Filter(authors = it, kinds = Kind.POSTS + Kind.REPOST, until = until, limit = limit) }
        // Followed hashtags join the timeline, as in Amethyst.
        return coroutineScope {
            val people = async { authoredTimeline(authors) { Filter(authors = it, kinds = Kind.POSTS + Kind.REPOST, until = until, limit = limit) } }
            val topics = async { runCatching { timeline(Filter(kinds = Kind.POSTS, tags = mapOf("t" to tags), until = until, limit = limit / 2)) }.getOrDefault(emptyList()) }
            (people.await() + topics.await().filter { !hidden(it.note) }).distinctBy { it.key }.sortedByDescending { it.sortTime }
        }
    }

    /** One page of [feed] for Home; [until] pages back (DVM feeds come as one page). */
    suspend fun feed(feed: com.example.btcwidget.nostr.core.HomeFeed, until: Long? = null): List<FeedItem> = when (feed) {
        is com.example.btcwidget.nostr.core.HomeFeed.Following -> homeFeed(until).let { items ->
            if (feed.replies) items else items.filter { it.repostedBy != null || !ThreadRef.of(it.note).isReply }
        }
        is com.example.btcwidget.nostr.core.HomeFeed.Global -> globalFeed(feed.relay, until)
        is com.example.btcwidget.nostr.core.HomeFeed.Hashtag -> hashtagFeed(feed.tag, until)
        is com.example.btcwidget.nostr.core.HomeFeed.Media -> mediaFeed(feed, until)
        is com.example.btcwidget.nostr.core.HomeFeed.People -> peopleLists.get(feed.owner, feed.d)?.members.orEmpty().let { members ->
            if (members.isEmpty()) emptyList()
            else authoredTimeline(members) { Filter(authors = it, kinds = Kind.POSTS + Kind.REPOST, until = until, limit = 40) }
        }
        is com.example.btcwidget.nostr.core.HomeFeed.Dvm ->
            if (until != null) emptyList() else dvm.feed(com.example.btcwidget.nostr.core.Dvm.Service(feed.pubkey, feed.dvmId, feed.name, null, null, false))
    }

    /** Picture posts (kind 20) or video posts (21, 22): by the people you follow, or by everyone. */
    suspend fun mediaFeed(feed: com.example.btcwidget.nostr.core.HomeFeed.Media, until: Long? = null): List<FeedItem> {
        val kinds = if (feed.videos) listOf(Kind.VIDEO, Kind.SHORT_VIDEO) else listOf(Kind.PICTURE)
        if (feed.everyone) return timeline(Filter(kinds = kinds, until = until, limit = 40))
        val me = pubkey ?: return emptyList()
        val authors = (_following.value + me).toList()
        return authoredTimeline(authors) { Filter(authors = it, kinds = kinds, until = until, limit = 40) }
    }

    /** A profile's notes and reposts. */
    suspend fun profileFeed(pubkey: String, until: Long? = null, limit: Int = 30): List<FeedItem> =
        authoredTimeline(listOf(pubkey)) { Filter(authors = it, kinds = Kind.POSTS + Kind.REPOST, until = until, limit = limit) }

    // ---- Outbox model (NIP-65): read people from the relays they write to ----

    /** Fetches relay lists (from the indexers and our relays) for authors we don't know yet. */
    private suspend fun loadWriteRelays(authors: Collection<String>) {
        val missing = authors.distinct().filter { !writeRelays.containsKey(it) }
        if (missing.isEmpty()) return
        coroutineScope {
            missing.chunked(150).map { chunk ->
                async { queryEverywhere(listOf(Filter(authors = chunk, kinds = listOf(Kind.RELAY_LIST))), timeoutMs = 4_000) }
            }.awaitAll()
        }.flatten().groupBy { it.pubkey }.forEach { (pk, lists) ->
            val latest = lists.maxBy { it.createdAt }
            relayListEvents[pk] = latest
            writeRelays[pk] = com.example.btcwidget.nostr.core.Outbox.writeRelays(latest)
            readRelays[pk] = com.example.btcwidget.nostr.core.Outbox.readRelays(latest)
            store(latest)
        }
        missing.forEach {
            writeRelays.putIfAbsent(it, emptyList())
            readRelays.putIfAbsent(it, emptyList())
        }
    }

    /** NIP-65 inbox (read) relays for [authors] not already in our own relay pool. */
    private suspend fun inboxRelaysFor(authors: Collection<String>): Set<String> {
        val targets = authors.distinct().filter { it.isNotBlank() && it != pubkey }
        if (targets.isEmpty()) return emptySet()
        loadWriteRelays(targets)
        return com.example.btcwidget.nostr.core.Outbox.plan(targets, readRelays, perAuthor = 2, maxRelays = 6, avoid = pool.urls).keys
    }

    /** Sends [event] to the NIP-65 read (inbox) relays of [authors] in the background so they get the notification. */
    private fun publishToInbox(event: Event, authors: Collection<String>) {
        val targets = authors.distinct().filter { it.isNotBlank() && it != pubkey }
        if (targets.isEmpty()) return
        scope.launch {
            runCatching {
                val extra = inboxRelaysFor(targets)
                if (extra.isNotEmpty()) {
                    outbox.addRelays(extra)
                    outbox.publish(event, only = extra, untilFirstAccepted = true)
                }
            }
        }
    }

    /**
     * Notes by [authors], from our relays and, at the same time, from the relays each of
     * them writes to (at most 12 extra relays, two per person). Merged into one timeline.
     */
    private suspend fun authoredTimeline(authors: List<String>, filterFor: (List<String>) -> Filter): List<FeedItem> =
        buildTimeline(authoredEvents(authors, filterFor = filterFor))

    /** Events by [authors] from our relays, their write relays (outbox) and any [hints]. */
    internal suspend fun authoredEvents(authors: List<String>, hints: List<String> = emptyList(), filterFor: (List<String>) -> Filter): List<Event> =
        coroutineScope {
            val ours = async { pool.query(listOf(filterFor(authors))) }
            val theirs = async {
                runCatching {
                    loadWriteRelays(authors)
                    val plan = com.example.btcwidget.nostr.core.Outbox.plan(authors, writeRelays, avoid = pool.urls)
                    if (plan.isEmpty()) return@runCatching emptyList()
                    outbox.addRelays(plan.keys)
                    plan.map { (url, who) ->
                        async { outbox.query(listOf(filterFor(who)), timeoutMs = 5_000, only = setOf(url)) }
                    }.awaitAll().flatten()
                }.getOrDefault(emptyList())
            }
            val hinted = async {
                val urls = hints.filter { it.startsWith("wss://") && it !in pool.urls }.take(3).toSet()
                if (urls.isEmpty()) emptyList() else runCatching {
                    outbox.addRelays(urls)
                    outbox.query(listOf(filterFor(authors)), timeoutMs = 5_000, only = urls)
                }.getOrDefault(emptyList())
            }
            val stored = async(Dispatchers.IO) { events.query(listOf(filterFor(authors))) }
            (ours.await() + theirs.await() + hinted.await() + stored.await()).distinctBy { it.id }
        }

    // ---- Long-form articles (NIP-23) ----

    /**
     * Recent notes from everyone, for discovering people: from all our relays, or only from
     * [relay] (which needn't be one of ours).
     */
    suspend fun globalFeed(relay: String? = null, until: Long? = null): List<FeedItem> {
        val filter = Filter(kinds = listOf(Kind.TEXT_NOTE, Kind.POLL), until = until, limit = 40)
        if (relay == null) return timeline(filter)
        outbox.addRelays(listOf(relay))
        return buildTimeline(outbox.query(listOf(filter), timeoutMs = 6_000, only = setOf(relay)))
    }

    /** A hashtag people are using now: how many different people, in how many notes. */
    data class Trend(val tag: String, val people: Int, val notes: Int)

    /**
     * What's trending, worked out from our own relays (no outside service): the hashtags in
     * the last day's notes, ranked by how many different people used them (so one busy
     * account can't push a tag up), then by notes. Tags only one person used are left out.
     */
    suspend fun trendingHashtags(max: Int = 20): List<Trend> {
        val since = System.currentTimeMillis() / 1000 - 86_400
        val events = pool.query(listOf(Filter(kinds = listOf(Kind.TEXT_NOTE), since = since, limit = 500)))
        return trendsOf(events).take(max)
    }

    /**
     * Notes with #[hashtag]: from our relays and the search relays (which index far more),
     * matching the "t" tag in any common casing (not every app lowercases it), plus notes
     * that write the hashtag in their text without tagging it.
     */
    suspend fun hashtagFeed(hashtag: String, until: Long? = null): List<FeedItem> {
        val tag = hashtag.trim().removePrefix("#")
        if (tag.isEmpty()) return emptyList()
        val variants = listOf(tag, tag.lowercase(), tag.uppercase(), tag.lowercase().replaceFirstChar { it.uppercase() }).distinct()
        val byTag = Filter(kinds = listOf(Kind.TEXT_NOTE), tags = mapOf("t" to variants), until = until, limit = 50)
        val byText = Filter(kinds = listOf(Kind.TEXT_NOTE), search = "#$tag", until = until, limit = 30)
        val events = coroutineScope {
            val ours = async { pool.query(listOf(byTag)) }
            val indexed = async { runCatching { searchPool.query(listOf(byTag), 6_000) }.getOrDefault(emptyList()) }
            val text = async { runCatching { searchPool.query(listOf(byText), 6_000) }.getOrDefault(emptyList()) }
            (ours.await() + indexed.await() + text.await()).distinctBy { it.id }
        }
        // Full-text search is loose: keep only notes that really carry the hashtag.
        val needle = "#" + tag.lowercase()
        val pattern = Regex(Regex.escape(needle) + "(?![\\p{L}\\p{M}\\p{N}_])")
        val matching = events.filter { e ->
            e.tagValues("t").any { it.equals(tag, ignoreCase = true) } ||
                pattern.containsMatchIn(e.content.lowercase())
        }
        return buildTimeline(matching)
    }

    /**
     * Notes for a timeline. Returns as soon as the notes are in; names, avatars and counts
     * fill in afterwards (their flows update the screen), instead of holding it up.
     */
    private suspend fun timeline(vararg filters: Filter, source: RelayPool = pool): List<FeedItem> =
        buildTimeline(source.query(filters.toList()))

    internal suspend fun buildTimeline(events: List<Event>): List<FeedItem> {
        // Reposts carry the note as JSON; fetch the few that don't in one request.
        val embedded = events.filter { it.kind == Kind.REPOST }.associateWith { r ->
            runCatching { Event.fromJson(r.content) }.getOrNull()?.takeIf { it.kind in Kind.POSTS && it.verify() }
        }
        val missing = embedded.filterValues { it == null }.keys.mapNotNull { it.tagValues("e").firstOrNull() }
            .filter { !notes.containsKey(it) }.distinct()
        if (missing.isNotEmpty()) {
            pool.query(listOf(Filter(ids = missing)), 3_000, graceMs = 500).forEach { remember(it) }
        }
        events.filter { it.kind == Kind.REPOST }.forEach { store(it) }
        val items = events.mapNotNull { e ->
            when (e.kind) {
                in Kind.POSTS -> FeedItem(e)
                Kind.REPOST -> (embedded[e] ?: e.tagValues("e").firstOrNull()?.let { notes[it] })
                    ?.let { FeedItem(it, repostedBy = e.pubkey, sortTime = e.createdAt) }
                else -> null
            }
        }.filter { !hidden(it.note) && it.repostedBy !in _muted.value && (it.repostedBy != null || trusted(it.note.pubkey)) }
            .distinctBy { it.key }
            .sortedByDescending { it.sortTime }
        items.forEach { remember(it.note) }
        // Authors, reposters and the people @mentioned in the notes, so names show at once.
        enrich(items.flatMap { listOfNotNull(it.note.pubkey, it.repostedBy) + mentionedIn(it.note) }, items.map { it.note.id })
        return items
    }

    /** People a note @mentions in its text (nostr:npub / nprofile). */
    private fun mentionedIn(note: Event): List<String> =
        runCatching { com.example.btcwidget.nostr.core.Content.parse(note.content) }.getOrDefault(emptyList())
            .mapNotNull { (it as? com.example.btcwidget.nostr.core.Content.Segment.ProfileRef)?.pubkey }

    /** Loads [pubkeys]' profiles and [noteIds]' counts in the background. */
    internal fun enrich(pubkeys: List<String>, noteIds: List<String>) {
        scope.launch { runCatching { loadProfiles(pubkeys) } }
        scope.launch { runCatching { loadStats(noteIds) } }
    }

    /** A note by id, from cache or relays. */
    /**
     * A note by id, from cache or relays. When our relays don't have it: the relays the link
     * or quote tags name ([relays]), its [author]'s write relays (NIP-65), the write relays of
     * whoever quoted it ([via]: they saw it there), then the search relays, which aggregate
     * many others.
     */
    suspend fun note(id: String, relays: List<String> = emptyList(), author: String? = null, via: String? = null): Event? {
        notes[id]?.let { return it }
        withContext(Dispatchers.IO) { events.query(listOf(Filter(ids = listOf(id)))) }.firstOrNull()?.let {
            remember(it, save = false)
            return it
        }
        val found = pool.query(listOf(Filter(ids = listOf(id))), 4_000, graceMs = 400).firstOrNull()
            // Not on our relays: ask the relays the link names (nevent) and, when the author is
            // known, the relays they write to (NIP-65), as other clients do.
            ?: when {
                author != null -> authoredEvents(listOf(author), relays) { Filter(ids = listOf(id)) }.firstOrNull { it.id == id }
                else -> {
                    val urls = relays.filter { it.startsWith("wss://") && it !in pool.urls }.take(3).toSet()
                    if (urls.isEmpty()) null else runCatching {
                        outbox.addRelays(urls)
                        outbox.query(listOf(Filter(ids = listOf(id))), timeoutMs = 5_000, only = urls).firstOrNull { it.id == id }
                    }.getOrNull()
                }
            }
            ?: via?.takeIf { it != author }?.let { quoter ->
                runCatching { authoredEvents(listOf(quoter)) { Filter(ids = listOf(id)) }.firstOrNull { it.id == id } }.getOrNull()
            }
            ?: runCatching { searchPool.query(listOf(Filter(ids = listOf(id))), 6_000).firstOrNull { it.id == id } }.getOrNull()
        return found?.also {
            remember(it)
            enrich(listOf(it.pubkey) + mentionedIn(it), emptyList())
        }
    }

    fun cachedNote(id: String): Event? = notes[id]

    /** Hashtags starting with [prefix] in notes seen so far, most used first (for autocomplete). */
    fun hashtagSuggestions(prefix: String, limit: Int = 5): List<String> {
        val p = prefix.lowercase()
        return synchronized(notes) { notes.values.toList() }.asSequence()
            .flatMap { it.tagValues("t").asSequence() }
            .map { it.lowercase() }
            .filter { it.startsWith(p) && it != p && it.length <= 40 }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .take(limit).map { it.key }
            .ifEmpty { DEFAULT_TAGS.filter { it.startsWith(p) && it != p }.take(limit) }
    }

    /** A thread around [noteId]: ancestors (oldest first), the note, and direct and nested replies. */
    suspend fun thread(noteId: String): Thread? {
        val focus = note(noteId) ?: return null
        val ancestors = ArrayList<Event>()
        var cursor = ThreadRef.of(focus).parent
        while (cursor != null && ancestors.size < 20) {
            val parent = note(cursor) ?: break
            ancestors.add(0, parent)
            cursor = ThreadRef.of(parent).parent
        }
        val rootId = ThreadRef.of(focus).root ?: focus.id
        val replyFilter = Filter(kinds = listOf(Kind.TEXT_NOTE), tags = mapOf("e" to listOf(rootId, focus.id).distinct()), limit = 200)
        val stored = withContext(Dispatchers.IO) { events.query(listOf(replyFilter)) }
        val threadAuthors = (ancestors.map { it.pubkey } + focus.pubkey).distinct()
        val fetchedReplies = coroutineScope {
            val ours = async { pool.query(listOf(replyFilter)) }
            val inbox = async {
                runCatching {
                    val extra = inboxRelaysFor(threadAuthors)
                    if (extra.isEmpty()) emptyList() else {
                        outbox.addRelays(extra)
                        outbox.query(listOf(replyFilter), timeoutMs = 4_500, only = extra)
                    }
                }.getOrDefault(emptyList())
            }
            ours.await() + inbox.await()
        }
        val replies = (fetchedReplies + stored).distinctBy { it.id }
            .filter { !hidden(it, threadMuteApplies = false) && trusted(it.pubkey) }
        replies.forEach { remember(it) }
        val below = ReplyTree.flatten(focus.id, replies)
        val all = ancestors + focus + below.map { it.event }
        enrich(all.map { it.pubkey }, all.map { it.id })
        return Thread(ancestors, focus, below)
    }

    /** [replies] holds every reply below [focus], nested, in reading order. */
    data class Thread(val ancestors: List<Event>, val focus: Event, val replies: List<ReplyTree.Row>)

    /** Replies, mentions, reposts, likes and zaps involving the user. */
    suspend fun notifications(limit: Int = 100): List<NostrNotification> {
        val me = pubkey ?: return emptyList()
        val events = pool.query(listOf(Filter(
            kinds = listOf(Kind.TEXT_NOTE, Kind.REPOST, Kind.REACTION, Kind.ZAP_RECEIPT),
            tags = mapOf("p" to listOf(me)), limit = limit
        )))
        val out = events.mapNotNull { e ->
            when (e.kind) {
                Kind.TEXT_NOTE -> if (e.pubkey == me) null else {
                    val thread = ThreadRef.of(e)
                    val parent = thread.parent
                    val type = if (parent != null) NostrNotification.Type.REPLY else NostrNotification.Type.MENTION
                    remember(e)
                    NostrNotification(e, type, e.pubkey, parent)
                }
                Kind.REPOST -> if (e.pubkey == me) null else NostrNotification(e, NostrNotification.Type.REPOST, e.pubkey, e.tagValues("e").firstOrNull())
                Kind.REACTION -> if (e.pubkey == me || e.content == "-") null
                    else NostrNotification(e, NostrNotification.Type.LIKE, e.pubkey, e.tagValues("e").lastOrNull())
                Kind.ZAP_RECEIPT -> Zaps.parseReceipt(e)?.let { r ->
                    NostrNotification(e, NostrNotification.Type.ZAP, r.sender ?: e.pubkey, r.noteId, r.amountMsat)
                }
                else -> null
            }
        }.filter { it.actor !in _muted.value && !hidden(it.event) && trusted(it.actor) }
        enrich(out.map { it.actor }, emptyList())
        return out.sortedByDescending { it.event.createdAt }
    }

    // ---- Search ----

    /** Relays that support NIP-50 search; opened on first search. */
    private fun applySearchRelays() {
        _searchRelays.value = com.example.btcwidget.nostr.core.RelayLists.relays(searchListEvent)
        searchPool.setRelays(_searchRelays.value.ifEmpty { SEARCH_RELAYS })
    }

    private fun applyBlockedRelays() {
        _blockedRelays.value = com.example.btcwidget.nostr.core.RelayLists.relays(blockedListEvent)
        RelayPool.block(_blockedRelays.value.toSet())
    }

    /** Publishes the user's NIP-65 list; with no relays of their own set in Settings, Block uses it too. */
    suspend fun saveRelayList(entries: List<com.example.btcwidget.nostr.core.RelayLists.Entry>) {
        val me = requireMe()
        if (!accountFresh) loadAccount()
        val event = publish(com.example.btcwidget.nostr.core.RelayLists.relayList(me, entries))
        relayList = event
        _relayEntries.value = com.example.btcwidget.nostr.core.RelayLists.entries(event)
        if (NostrAccountStore.relays(context) == null) {
            pool.setRelays(poolFor(_relayEntries.value.map { it.url }))
        }
        saveAccount()
    }

    /**
     * The relays Block connects to from the user's NIP-65 list: all of theirs (up to
     * [MAX_OWN_RELAYS]), then the defaults to make up at least 8. It used to keep only the
     * first 5, so a relay added later was in the list but never connected.
     */
    private fun poolFor(urls: List<String>): List<String> {
        val own = urls.mapNotNull { normalizeRelayUrl(it) }.distinct().take(MAX_OWN_RELAYS)
        return (own + RelayPool.DEFAULT_RELAYS).distinct().take(maxOf(8, own.size))
    }

    // ---- Check and tidy relays ----

    /** The user's relays: their NIP-65 list, else the relays set in Settings, else what Block uses now. */
    fun myRelayUrls(): List<String> =
        _relayEntries.value.map { it.url }.ifEmpty { NostrAccountStore.relays(context) ?: pool.urls.toList() }
            .mapNotNull { normalizeRelayUrl(it) }.distinct()

    /**
     * Opens a fresh connection to each of [urls] at once, separate from the ones in use, and
     * reports how long each took to connect (or that it didn't) within [timeoutMs].
     */
    suspend fun probeRelays(urls: List<String>, timeoutMs: Long = 8_000): List<com.example.btcwidget.nostr.core.RelayCheckup.Probe> {
        if (urls.isEmpty()) return emptyList()
        val probe = RelayPool()
        return try {
            probe.setRelays(urls)
            val deadline = System.currentTimeMillis() + timeoutMs
            fun settled(st: RelayStatus) = st.state == RelayStatus.State.CONNECTED || st.failures > 0 || st.state == RelayStatus.State.FAILED
            while (System.currentTimeMillis() < deadline && !probe.status().all(::settled)) delay(250)
            val byUrl = probe.status().associateBy { it.url }
            urls.map { u ->
                val st = byUrl[u]
                com.example.btcwidget.nostr.core.RelayCheckup.Probe(
                    u,
                    st?.takeIf { it.state == RelayStatus.State.CONNECTED }?.let { it.connectMs ?: 0L },
                    st?.lastError ?: if (st == null) "Blocked" else null
                )
            }
        } finally {
            probe.closeAll()
        }
    }

    /** Relays most of the people the user follows write to (NIP-65), not already theirs. */
    suspend fun suggestedRelays(limit: Int = 8): List<Pair<String, Int>> {
        val follows = _following.value.take(300)
        if (follows.isEmpty()) return emptyList()
        loadWriteRelays(follows)
        return com.example.btcwidget.nostr.core.RelayCheckup.suggestions(
            follows.associateWith { writeRelays[it].orEmpty() },
            mine = (myRelayUrls() + pool.urls).toSet(),
            blocked = _blockedRelays.value.toSet(),
            limit = limit
        )
    }

    /** Takes [urls] out of the user's relays: their NIP-65 list (published) and the list set in Settings. */
    suspend fun removeRelays(urls: Collection<String>) {
        val gone = urls.map { it.trimEnd('/') }.toSet()
        NostrAccountStore.relays(context)?.let { local -> setRelays(local.filter { it.trimEnd('/') !in gone }) }
        val entries = _relayEntries.value.ifEmpty {
            pool.urls.map { com.example.btcwidget.nostr.core.RelayLists.Entry(it, read = true, write = true) }
        }
        val kept = entries.filter { it.url.trimEnd('/') !in gone }
        if (kept.size != entries.size || _relayEntries.value.isEmpty()) saveRelayList(kept)
    }

    /** Adds [url] to the relays the user set in Settings (when they set some), so Block connects to it. */
    fun addToLocalRelays(url: String) {
        val local = NostrAccountStore.relays(context) ?: return
        if (local.none { it.trimEnd('/') == url.trimEnd('/') }) setRelays(local + url)
    }

    suspend fun saveSearchRelays(urls: List<String>) {
        val me = requireMe()
        if (!accountFresh) loadAccount()
        searchListEvent = publish(com.example.btcwidget.nostr.core.RelayLists.relaySet(me, com.example.btcwidget.nostr.core.RelayLists.SEARCH, urls, searchListEvent))
        applySearchRelays()
        saveAccount()
    }

    suspend fun saveBlockedRelays(urls: List<String>) {
        val me = requireMe()
        if (!accountFresh) loadAccount()
        blockedListEvent = publish(com.example.btcwidget.nostr.core.RelayLists.relaySet(me, com.example.btcwidget.nostr.core.RelayLists.BLOCKED, urls, blockedListEvent))
        applyBlockedRelays()
        saveAccount()
    }

    private val relayInfos = ConcurrentHashMap<String, com.example.btcwidget.nostr.core.RelayInfo>()

    /** What [relay] says about itself (NIP-11), or null if it doesn't answer. Remembered for the session. */
    suspend fun relayInfo(relay: String): com.example.btcwidget.nostr.core.RelayInfo? {
        relayInfos[relay]?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = okhttp3.Request.Builder().url(com.example.btcwidget.nostr.core.RelayInfo.httpUrl(relay))
                    .header("Accept", "application/nostr+json").build()
                RelayPool.defaultClient.newCall(request).execute().use { r ->
                    if (!r.isSuccessful) null else r.body.string().take(200_000).let { com.example.btcwidget.nostr.core.RelayInfo.parse(it) }
                }
            }.getOrNull()
        }?.also { relayInfos[relay] = it }
    }

    /**
     * The most proof of work (NIP-11 `min_pow_difficulty`) any relay a post goes to asks for,
     * and which relay that is. Asks each relay once per session, waiting at most [timeoutMs].
     */
    suspend fun relaysMinPow(timeoutMs: Long = 1_500): Pair<Int, String?> = coroutineScope {
        val asks = pool.urls.toList().map { url ->
            async {
                // Relays that don't answer count as asking nothing, and aren't asked again.
                url to (relayMinPow[url] ?: (kotlinx.coroutines.withTimeoutOrNull(timeoutMs) { relayInfo(url) }?.minPowDifficulty ?: 0)
                    .also { relayMinPow[url] = it })
            }
        }.awaitAll()
        val top = asks.maxByOrNull { it.second }
        val bits = top?.second ?: 0
        bits to top?.first?.takeIf { bits > 0 }
    }
    private val relayMinPow = ConcurrentHashMap<String, Int>()

    /** What a search box entry points straight at, if anything. */
    sealed interface Direct {
        data class ProfileTarget(val pubkey: String) : Direct
        data class NoteTarget(val id: String) : Direct
        data class HashtagTarget(val tag: String) : Direct
        data class ArticleTarget(val address: com.example.btcwidget.nostr.core.Nip19.Address) : Direct
    }

    /** npub/nprofile/note/nevent/hex, "#tag" or a NIP-05 address; null for free text. */
    suspend fun resolve(query: String): Direct? {
        val q = query.trim()
        when (val e = com.example.btcwidget.nostr.core.Nip19.decode(q)) {
            is com.example.btcwidget.nostr.core.Nip19.Pubkey -> return Direct.ProfileTarget(e.hex)
            is com.example.btcwidget.nostr.core.Nip19.Note -> return Direct.NoteTarget(e.id)
            is com.example.btcwidget.nostr.core.Nip19.Address -> if (e.kind == Kind.ARTICLE) return Direct.ArticleTarget(e)
            else -> Unit
        }
        if (q.startsWith("#") && q.length > 1 && q.drop(1).all { it.isLetterOrDigit() || it == '_' }) return Direct.HashtagTarget(q.drop(1))
        if (com.example.btcwidget.nostr.core.Nip05.looks(q) && q.contains('@')) {
            val url = com.example.btcwidget.nostr.core.Nip05.url(q) ?: return null
            val name = com.example.btcwidget.nostr.core.Nip05.parts(q)!!.first
            val pubkey = runCatching { withContext(Dispatchers.IO) { com.example.btcwidget.nostr.core.Nip05.pubkeyFrom(httpGet(url), name) } }.getOrNull()
            if (pubkey != null) return Direct.ProfileTarget(pubkey)
        }
        return null
    }

    /** People matching [query] (NIP-50 on profile metadata), most followed first per the relay. */
    suspend fun searchPeople(query: String): List<Profile> {
        val events = searchPool.query(listOf(Filter(kinds = listOf(Kind.METADATA), search = query.trim(), limit = 30)), 6_000)
        events.forEach { remember(it) }
        val wanted = events.map { it.pubkey }.distinct()
        return wanted.mapNotNull { _profiles.value[it] }.filter { it.pubkey !in _muted.value && trusted(it.pubkey) }
    }

    /** Notes matching [query] (NIP-50 + local SQLite full-text search for instant/offline results). */
    suspend fun searchNotes(query: String): List<FeedItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val found = coroutineScope {
            val remote = async { runCatching { searchPool.query(listOf(Filter(kinds = listOf(Kind.TEXT_NOTE), search = q, limit = 40)), 6_000) }.getOrDefault(emptyList()) }
            val local = async(Dispatchers.IO) { events.search(q, kinds = listOf(Kind.TEXT_NOTE), limit = 40) }
            (remote.await() + local.await()).distinctBy { it.id }
        }
        return buildTimeline(found)
    }

    // ---- Who engaged with a note ----

    /** Who reposted, liked (with their reaction) and zapped a note. */
    data class Engagement(
        val reposts: List<String>,
        val likes: List<Pair<String, String>>,
        val zaps: List<Zaps.Receipt>
    )

    suspend fun engagement(noteId: String): Engagement {
        val filter = Filter(kinds = listOf(Kind.REPOST, Kind.REACTION, Kind.ZAP_RECEIPT), tags = mapOf("e" to listOf(noteId)), limit = 500)
        val noteAuthor = notes[noteId]?.pubkey
        val rawEvents = coroutineScope {
            val ours = async { pool.query(listOf(filter), 6_000) }
            val inbox = async {
                if (noteAuthor == null) emptyList() else runCatching {
                    val extra = inboxRelaysFor(listOf(noteAuthor))
                    if (extra.isEmpty()) emptyList() else {
                        outbox.addRelays(extra)
                        outbox.query(listOf(filter), timeoutMs = 4_500, only = extra)
                    }
                }.getOrDefault(emptyList())
            }
            (ours.await() + inbox.await()).distinctBy { it.id }
        }
        val events = rawEvents
            .filter { it.pubkey !in _muted.value && trusted(it.pubkey) }
        val reposts = events.filter { it.kind == Kind.REPOST && it.tagValues("e").firstOrNull() == noteId }
            .sortedByDescending { it.createdAt }.map { it.pubkey }.distinct()
        // NIP-25: the reaction targets the last "e" tag; "-" is a dislike, not a like.
        val likes = events.filter { it.kind == Kind.REACTION && it.tagValues("e").lastOrNull() == noteId && it.content != "-" }
            .sortedByDescending { it.createdAt }.distinctBy { it.pubkey }
            .map { it.pubkey to it.content.ifEmpty { "+" } }
        val zaps = events.filter { it.kind == Kind.ZAP_RECEIPT }.mapNotNull { Zaps.parseReceipt(it) }
            .filter { it.noteId == noteId }.sortedByDescending { it.amountMsat }
        loadProfiles(reposts + likes.map { it.first } + zaps.mapNotNull { it.sender })
        return Engagement(reposts, likes, zaps)
    }

    // ---- NIP-05 verification ----

    private val _nip05 = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    /** "pubkey|name@domain" → whether the domain's nostr.json really maps the name to that key. */
    val nip05Verified: StateFlow<Map<String, Boolean>> = _nip05.asStateFlow()
    private val nip05InFlight = ConcurrentHashMap.newKeySet<String>()

    /**
     * Checks [identifier] against https://domain/.well-known/nostr.json once per session.
     * Until it's confirmed, the app doesn't show the address as verified.
     */
    fun verifyNip05(pubkey: String, identifier: String) {
        val key = "$pubkey|$identifier"
        if (_nip05.value.containsKey(key) || !nip05InFlight.add(key)) return
        scope.launch(Dispatchers.IO) {
            val ok = runCatching {
                val url = com.example.btcwidget.nostr.core.Nip05.url(identifier) ?: return@runCatching false
                val name = com.example.btcwidget.nostr.core.Nip05.parts(identifier)?.first ?: return@runCatching false
                com.example.btcwidget.nostr.core.Nip05.pubkeyFrom(httpGet(url), name) == pubkey
            }.getOrDefault(false)
            _nip05.update { it + (key to ok) }
            nip05InFlight.remove(key)
        }
    }

    /** Pubkeys whose profiles are being fetched, so concurrent screens don't ask twice. */
    private val profilesInFlight = ConcurrentHashMap.newKeySet<String>()

    /** Fetches kind-0 profiles not yet known, from the user's relays and the indexers at once. */
    /** [loadProfiles] in the background, for people seen in a live stream. */
    fun loadProfilesLater(pubkeys: Collection<String>) {
        val missing = pubkeys.filter { it !in _profiles.value }
        if (missing.isNotEmpty()) scope.launch { runCatching { loadProfiles(missing) } }
    }

    suspend fun loadProfiles(pubkeys: Collection<String>) {
        val missing = pubkeys.distinct().filter { !profileEvents.containsKey(it) && profilesInFlight.add(it) }
        if (missing.isEmpty()) return
        try {
            coroutineScope {
                missing.chunked(100).map { chunk ->
                    async { rememberProfiles(queryEverywhere(listOf(Filter(authors = chunk, kinds = listOf(Kind.METADATA))))) }
                }.awaitAll()
            }
            // Still unknown: their profile may only be on the relays they write to (NIP-65).
            val stillMissing = missing.filter { !profileEvents.containsKey(it) }
            if (stillMissing.isNotEmpty()) runCatching {
                rememberProfiles(authoredEvents(stillMissing.take(50)) { Filter(authors = it, kinds = listOf(Kind.METADATA)) })
            }
        } finally {
            profilesInFlight.removeAll(missing.toSet())
        }
    }

    /** Fetches a profile even if cached (for the profile screen), from their own relays too. */
    suspend fun refreshProfile(pubkey: String): Profile? {
        queryEverywhere(listOf(Filter(authors = listOf(pubkey), kinds = listOf(Kind.METADATA), limit = 1))).forEach { remember(it) }
        if (_profiles.value[pubkey] == null) runCatching {
            authoredEvents(listOf(pubkey)) { Filter(authors = it, kinds = listOf(Kind.METADATA), limit = 1) }.forEach { remember(it) }
        }
        return _profiles.value[pubkey]
    }

    /** [pubkey]'s published relays (NIP-65): where they write (outbox) and read (inbox). */
    suspend fun relayListOf(pubkey: String): List<com.example.btcwidget.nostr.core.RelayLists.Entry> {
        loadWriteRelays(listOf(pubkey))
        return relayListEvents[pubkey]?.let { com.example.btcwidget.nostr.core.RelayLists.entries(it) }.orEmpty()
    }

    /** The relays Block reads from and writes to now (for adding one to the user's list). */
    fun currentRelayUrls(): List<String> = pool.urls.toList()

    /** Whom [pubkey] follows (their latest contact list). */
    suspend fun followingOf(pubkey: String): Set<String> =
        queryEverywhere(listOf(Filter(authors = listOf(pubkey), kinds = listOf(Kind.CONTACTS), limit = 1)))
            .maxByOrNull { it.createdAt }?.tagValues("p")?.toSet().orEmpty()

    /**
     * Who follows [pubkey]: authors whose latest contact list includes them, from the
     * user's relays and the indexers. Capped by what relays return ([limit] lists), so
     * for popular accounts this is a lower bound.
     */
    suspend fun followersOf(pubkey: String, limit: Int = 500): List<String> =
        queryEverywhere(listOf(Filter(kinds = listOf(Kind.CONTACTS), tags = mapOf("p" to listOf(pubkey)), limit = limit)), timeoutMs = 6_000)
            .groupBy { it.pubkey }
            .mapNotNull { (author, lists) -> author.takeIf { lists.maxBy { it.createdAt }.tagValues("p").contains(pubkey) } }
            .filter { it !in _muted.value }

    /** Counts likes, reposts, replies and zaps for [noteIds]; marks the user's own. */
    suspend fun loadStats(noteIds: List<String>) {
        if (noteIds.isEmpty()) return
        val me = pubkey
        val events = coroutineScope {
            noteIds.distinct().chunked(40).map { chunk ->
                async { pool.query(listOf(Filter(kinds = listOf(Kind.TEXT_NOTE, Kind.REPOST, Kind.REACTION, Kind.ZAP_RECEIPT), tags = mapOf("e" to chunk), limit = 500)), 5_000) }
            }.awaitAll().flatten().distinctBy { it.id }
        }
        val update = noteIds.associateWith { NoteStats() }.toMutableMap()
        for (e in events) {
            val targets = e.tagValues("e").filter { it in update }
            val target = when (e.kind) {
                Kind.REACTION -> targets.lastOrNull()
                Kind.TEXT_NOTE -> ThreadRef.of(e).parent?.takeIf { it in update }
                else -> targets.firstOrNull()
            } ?: continue
            val s = update.getValue(target)
            update[target] = when (e.kind) {
                Kind.TEXT_NOTE -> s.copy(replies = s.replies + 1)
                Kind.REPOST -> s.copy(reposts = s.reposts + 1, repostedByMe = s.repostedByMe || e.pubkey == me)
                Kind.REACTION -> if (e.content == "-") s else s.copy(
                    likes = s.likes + 1, likedByMe = s.likedByMe || e.pubkey == me,
                    myReaction = if (e.pubkey == me) e.content else s.myReaction
                )
                Kind.ZAP_RECEIPT -> s.copy(zapMsat = s.zapMsat + (Zaps.parseReceipt(e)?.amountMsat ?: 0))
                else -> s
            }
        }
        _stats.update { old ->
            // Updated notes move to the end, so the ones dropped below are those not seen for longest.
            val merged = (old - update.keys) + update.mapValues { (id, new) -> mergeStats(old[id], new) }
            // Counts for recent notes only: each update copies the whole map.
            if (merged.size <= MAX_STATS) merged else merged.entries.drop(merged.size - MAX_STATS * 3 / 4).associate { it.key to it.value }
        }
    }

    /** Keeps optimistic local actions (a like just sent) when relays haven't caught up. */
    private fun mergeStats(old: NoteStats?, new: NoteStats): NoteStats = if (old == null) new else new.copy(
        likes = maxOf(new.likes, if (old.likedByMe && !new.likedByMe) new.likes + 1 else new.likes),
        likedByMe = new.likedByMe || old.likedByMe,
        reposts = if (old.repostedByMe && !new.repostedByMe) new.reposts + 1 else new.reposts,
        repostedByMe = new.repostedByMe || old.repostedByMe,
        myReaction = new.myReaction ?: old.myReaction
    )

    private fun rememberProfiles(events: Collection<Event>, save: Boolean = true) {
        if (events.isEmpty()) return
        val batch = HashMap<String, Profile>()
        for (e in events) {
            if (e.kind != Kind.METADATA) continue
            val old = profileEvents[e.pubkey]
            if (old == null || e.createdAt > old.createdAt) {
                profileEvents[e.pubkey] = e
                Profile.fromEvent(e)?.let { batch[e.pubkey] = it }
                if (save) store(e)
            }
        }
        if (batch.isNotEmpty()) _profiles.update { it + batch }
    }

    internal fun remember(e: Event, save: Boolean = true) {
        when (e.kind) {
            Kind.METADATA -> {
                val old = profileEvents[e.pubkey]
                if (old == null || e.createdAt > old.createdAt) {
                    profileEvents[e.pubkey] = e
                    Profile.fromEvent(e)?.let { p -> _profiles.update { it + (e.pubkey to p) } }
                    if (save) store(e)
                }
            }
            else -> {
                val isNew = notes.put(e.id, e) == null
                if (save && isNew) store(e)
            }
        }
    }

    /** Queues [e] for the local database; written in batches a moment later. */
    internal fun store(e: Event) {
        toStore.add(e)
        if (storeJob?.isActive == true) return
        storeJob = scope.launch(Dispatchers.IO) {
            delay(1_500)
            while (toStore.isNotEmpty()) {
                events.insert(generateSequence { toStore.poll() }.take(2_000).toList())
            }
        }
    }

    // ---- Writing ----

    internal fun requireSigner(): NostrSigner = signer ?: throw IllegalStateException("Log in to Nostr first")

    /**
     * Signs and publishes; succeeds if at least one relay accepted it. Inside a [PublishTracker]
     * (an action the user is watching, like a repost or reply) it reports each step, and is done
     * as soon as one relay has it while the rest answer in the background.
     */
    /**
     * Signs and sends [unsigned]. With [pow] > 0 it first mines that many bits of proof of work
     * (NIP-13) on every core but one; a [PublishTracker] in the context sees the progress and
     * can skip the work ([PublishTracker.skipPow]) to send the event without it.
     */
    suspend fun publish(unsigned: UnsignedEvent, fast: Boolean = false, pow: Int = 0): Event {
        val tracker = kotlin.coroutines.coroutineContext[PublishTracker]
        val ready = if (pow <= 0) unsigned else {
            val difficulty = pow.coerceAtMost(com.example.btcwidget.nostr.core.Pow.MAX_DIFFICULTY)
            tracker?.onStage?.invoke(PublishTracker.Stage.Mining(difficulty, 0, 0))
            // Fresh time: the post is "made" when the work starts, not when the composer opened.
            com.example.btcwidget.nostr.core.Pow.mine(
                unsigned.copy(createdAt = maxOf(unsigned.createdAt, com.example.btcwidget.nostr.core.Events.now())),
                difficulty,
                skip = { tracker?.skipPow?.get() == true }
            ) { p -> tracker?.onStage?.invoke(PublishTracker.Stage.Mining(difficulty, p.hashes, p.elapsedMs)) }
                ?: com.example.btcwidget.nostr.core.Pow.withoutNonce(unsigned)
        }
        tracker?.onStage?.invoke(PublishTracker.Stage.Signing(_account.value?.type))
        val signed = requireSigner().sign(ready)
        // [fast]: done once one relay has it (lists the UI already shows as changed).
        var total = 0
        val answered = java.util.concurrent.atomic.AtomicInteger()
        val accepted = java.util.concurrent.atomic.AtomicInteger()
        val results = pool.publish(
            signed,
            untilFirstAccepted = fast || tracker != null,
            onSent = { n -> total = n; tracker?.onStage?.invoke(PublishTracker.Stage.Sending(0, 0, n)) },
            onAnswer = { r ->
                val a = if (r.accepted) accepted.incrementAndGet() else accepted.get()
                tracker?.onStage?.invoke(PublishTracker.Stage.Sending(answered.incrementAndGet(), a, total))
            }
        )
        if (results.none { it.accepted }) {
            throw com.example.btcwidget.nostr.core.PublishRejected(com.example.btcwidget.nostr.core.RelayRejection.mostUseful(results.map { it.message }))
        }
        remember(signed)
        return signed
    }

    suspend fun post(content: String, extraTags: List<List<String>> = emptyList(), pow: Int = 0): Event =
        publish(Events.note(requireMe(), content, extraTags), pow = pow).also { e ->
            publishToInbox(e, e.tagValues("p"))
        }
    suspend fun reply(content: String, parent: Event, extraTags: List<List<String>> = emptyList(), pow: Int = 0): Event =
        publish(Events.reply(requireMe(), content, parent, relayHint(), extraTags), pow = pow).also { e ->
            publishToInbox(e, (e.tagValues("p") + parent.pubkey).distinct())
        }
    suspend fun quote(content: String, quoted: Event, extraTags: List<List<String>> = emptyList(), pow: Int = 0): Event =
        publish(Events.quote(requireMe(), content, quoted, relayHint(), extraTags), pow = pow).also { e ->
            publishToInbox(e, (e.tagValues("p") + quoted.pubkey).distinct())
        }

    // ---- Media servers (Blossom, kind 10063) ----

    /** The user's media servers, or empty when they use the default. */
    fun mediaServerUrls(): List<String> = mediaServers

    fun storageStats(): StorageStats {
        val dbStats = events.stats()
        fun bytes(file: java.io.File): Long = if (file.isFile) file.length() else file.listFiles()?.sumOf(::bytes) ?: 0L
        val mediaBytes = listOf("image_cache", "video_cache").sumOf { bytes(java.io.File(context.cacheDir, it)) }
        return dbStats.copy(mediaCacheBytes = mediaBytes)
    }

    fun eventRetentionDays(): Int = com.example.btcwidget.AppPrefs.eventRetentionDays(context)

    fun setEventRetentionDays(days: Int) {
        com.example.btcwidget.AppPrefs.setEventRetentionDays(context, days)
        if (days > 0) pruneOldEvents(days)
    }

    fun pruneOldEvents(days: Int) {
        if (days <= 0) return
        events.pruneBefore(System.currentTimeMillis() / 1000 - days * 86_400L, ownedPubkeys(), _bookmarks.value.toSet())
    }

    private fun ownedPubkeys(): Set<String> =
        (NostrAccountStore.savedAccounts(context).map { it.pubkey } + listOfNotNull(pubkey)).toSet()

    /** Publishes the media server list; empty goes back to the default server. */
    suspend fun setMediaServers(urls: List<String>) {
        val me = requireMe()
        val clean = urls.map { it.trim().trimEnd('/') }.filter { it.startsWith("https://") }.distinct()
        val event = publish(UnsignedEvent(me, Events.now(), MEDIA_SERVER_LIST, clean.map { listOf("server", it) }, ""))
        mediaServerList = event
        mediaServers = com.example.btcwidget.nostr.core.Blossom.serversFrom(event)
        saveAccount()
    }

    /**
     * Uploads [image] to the user's Blossom servers in order, with the public default as fallback.
     * Signing the upload permission goes through the user's signer.
     */
    suspend fun uploadImage(image: PreparedImage): com.example.btcwidget.nostr.core.Blossom.Blob {
        val sha = com.example.btcwidget.nostr.core.Blossom.sha256Hex(image.bytes)
        return uploadBlob(sha, image.bytes.size.toLong()) { image.bytes.toRequestBody(image.mime.toMediaType()) }
    }

    /** Uploads already-encrypted bytes (a photo for a private message). */
    suspend fun uploadEncrypted(bytes: ByteArray): com.example.btcwidget.nostr.core.Blossom.Blob {
        val sha = com.example.btcwidget.nostr.core.Blossom.sha256Hex(bytes)
        return uploadBlob(sha, bytes.size.toLong()) { bytes.toRequestBody("application/octet-stream".toMediaType()) }
    }

    /** Uploads a video as-is; the cached copy is deleted afterwards. */
    suspend fun uploadVideo(video: PreparedVideo): com.example.btcwidget.nostr.core.Blossom.Blob = try {
        uploadBlob(video.sha256, video.size) { video.file.asRequestBody(video.mime.toMediaType()) }
    } finally {
        video.file.delete()
    }

    /**
     * Uploads a file to the user's Blossom servers in order, with the public default as fallback.
     * Signing the upload permission goes through the user's signer.
     */
    private suspend fun uploadBlob(sha: String, size: Long, body: () -> okhttp3.RequestBody): com.example.btcwidget.nostr.core.Blossom.Blob {
        val me = requireMe()
        val blossom = com.example.btcwidget.nostr.core.Blossom
        val auth = requireSigner().sign(blossom.uploadAuth(me, sha, size))
        val header = blossom.authHeader(auth)
        val servers = (mediaServers + blossom.DEFAULT_SERVER).distinct()
        var failure: Exception? = null
        for (server in servers) {
            try {
                return withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url(blossom.uploadUrl(server))
                        .header("Authorization", header)
                        .put(body())
                        .build()
                    RelayPool.uploadClient.newCall(request).execute().use { r ->
                        if (!r.isSuccessful) {
                            error(r.header("X-Reason") ?: "${server.removePrefix("https://")} refused the upload (HTTP ${r.code})")
                        }
                        blossom.parseUpload(r.body.string(), sha).getOrThrow()
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e
            }
        }
        throw failure ?: IllegalStateException("Upload failed")
    }

    suspend fun repost(note: Event) {
        val e = publish(Events.repost(requireMe(), note, relayHint()))
        publishToInbox(e, listOf(note.pubkey))
        bump(note.id) { it.copy(reposts = it.reposts + 1, repostedByMe = true) }
    }

    suspend fun like(note: Event) = react(note, "+")

    /** Reacts with "+" (a like), an emoji, or ":code:" with [emojiUrl] (NIP-30). One reaction per note. */
    suspend fun react(note: Event, content: String, emojiUrl: String? = null) {
        if (_stats.value[note.id]?.likedByMe == true) return
        bump(note.id) { it.copy(likes = it.likes + 1, likedByMe = true, myReaction = content) }
        runCatching {
            val e = publish(Events.reaction(requireMe(), note, content, emojiUrl))
            publishToInbox(e, listOf(note.pubkey))
        }.onFailure { bump(note.id) { s -> s.copy(likes = (s.likes - 1).coerceAtLeast(0), likedByMe = false, myReaction = null) }; throw it }
    }

    private fun withPendingFollows(base: Set<String>): Set<String> =
        base + pendingFollows.filterValues { it }.keys - pendingFollows.filterValues { !it }.keys

    /**
     * Follows or unfollows [target]. The change shows at once; publishing the contact list
     * (loading it first if needed, signing, sending) happens after, one change at a time so
     * quick taps on several people don't overwrite each other. On failure it's undone.
     */
    suspend fun setFollowing(target: String, follow: Boolean) {
        val me = requireMe()
        pendingFollows[target] = follow
        _following.value = withPendingFollows(_following.value - target)
        try {
            contactsLock.withLock {
                // Never overwrite a contact list we haven't read from relays this session: a missing or
                // stale list (e.g. from the cache) would wipe follows made in other apps.
                if (!accountFresh) loadAccount()
                if (contactsEvent == null && (_following.value - pendingFollows.keys).isNotEmpty()) error("Couldn't load your follow list; try again")
                val want = pendingFollows[target] ?: follow
                val event = publish(Events.contacts(me, contactsEvent, target, want), fast = true)
                contactsEvent = event
                _followedTags.value = followedTagsOf(event)
                if (pendingFollows[target] == want) pendingFollows.remove(target)
                _following.value = withPendingFollows(event.tagValues("p").toSet())
                saveAccount()
            }
        } catch (e: Throwable) {
            pendingFollows.remove(target)
            _following.value = withPendingFollows(contactsEvent?.tagValues("p")?.toSet() ?: (_following.value - target))
            throw e
        }
    }

    /** Follows everyone in [targets] with one contact list update (a follow pack's "Follow all"). */
    suspend fun followAll(targets: Collection<String>) {
        val me = requireMe()
        val add = targets.filter { it != me && it !in _following.value }.distinct()
        if (add.isEmpty()) return
        val before = _following.value
        _following.value = before + add
        try {
            contactsLock.withLock {
                if (!accountFresh) loadAccount()
                if (contactsEvent == null && before.isNotEmpty()) error("Couldn't load your follow list; try again")
                val event = publish(com.example.btcwidget.nostr.core.Events.contactsAdding(me, contactsEvent, add), fast = true)
                contactsEvent = event
                _following.value = withPendingFollows(event.tagValues("p").toSet())
                saveAccount()
            }
        } catch (e: Throwable) {
            _following.value = withPendingFollows(contactsEvent?.tagValues("p")?.toSet() ?: before)
            throw e
        }
    }

    /** Follow packs (kind 39089) from our relays and the indexers. */
    suspend fun followPacks(): List<com.example.btcwidget.nostr.core.FollowPack> =
        com.example.btcwidget.nostr.core.FollowPack.latest(
            queryEverywhere(listOf(Filter(kinds = listOf(com.example.btcwidget.nostr.core.FollowPack.KIND), limit = 80)), timeoutMs = 6_000)
        ).filter { it.owner !in _muted.value }.also { packs -> loadProfilesLater(packs.map { it.owner } + packs.flatMap { it.members.take(4) }) }

    suspend fun followPack(address: String): com.example.btcwidget.nostr.core.FollowPack? {
        val p = address.split(':', limit = 3).takeIf { it.size == 3 } ?: return null
        val events = queryEverywhere(listOf(Filter(kinds = listOf(com.example.btcwidget.nostr.core.FollowPack.KIND), authors = listOf(p[1]), tags = mapOf("d" to listOf(p[2])))), timeoutMs = 6_000)
        return com.example.btcwidget.nostr.core.FollowPack.latest(events).firstOrNull()?.also { loadProfiles(it.members + it.owner) }
    }

    private fun followedTagsOf(contacts: Event): Set<String> =
        contacts.tagValues("t").map { it.trim().removePrefix("#").lowercase() }.filter { it.isNotEmpty() }.toSet()

    /**
     * Follows or unfollows a hashtag, kept in the contact list ("t" tags) so other apps see it
     * too. Shown at once; published like a follow, one change at a time; undone on failure.
     */
    suspend fun setHashtagFollowed(tag: String, follow: Boolean) {
        val me = requireMe()
        val t = tag.trim().removePrefix("#").lowercase().takeIf { it.isNotEmpty() } ?: return
        val before = _followedTags.value
        _followedTags.value = if (follow) before + t else before - t
        try {
            contactsLock.withLock {
                if (!accountFresh) loadAccount()
                if (contactsEvent == null && _following.value.isNotEmpty()) error("Couldn't load your follow list; try again")
                val event = publish(com.example.btcwidget.nostr.core.Events.contactsHashtag(me, contactsEvent, t, follow), fast = true)
                contactsEvent = event
                _followedTags.value = followedTagsOf(event)
                saveAccount()
            }
        } catch (e: Throwable) {
            _followedTags.value = contactsEvent?.let { followedTagsOf(it) } ?: before
            throw e
        }
    }

    // ---- Private list entries (NIP-51: tags encrypted to yourself in the list's content) ----

    /** Decrypted private tags of the mute and bookmark lists; null while they couldn't be read. */
    @Volatile private var privateMutes: List<List<String>>? = null
    @Volatile private var privateBookmarks: List<List<String>>? = null

    /** New mutes and bookmarks go in the private part (the default), or the public tags. */
    private val privateByDefault get() = com.example.btcwidget.AppPrefs.privateLists(context)

    private fun applyMutes() {
        val list = muteEvent
        val priv = privateMutes.orEmpty()
        _muted.value = (list?.tagValues("p").orEmpty() + priv.filter { it.size >= 2 && it[0] == "p" }.map { it[1] }).toSet()
        _mutes.value = com.example.btcwidget.nostr.core.MuteRules.of(list, priv)
    }

    private fun applyBookmarks() {
        // Newest first: public then private entries, each in the order added.
        val all = bookmarkEvent?.tagValues("e").orEmpty() + privateBookmarks.orEmpty().filter { it.size >= 2 && it[0] == "e" }.map { it[1] }
        _bookmarks.value = all.distinct().reversed()
    }

    /** The private tags of [list]: empty if it has none, null if they can't be decrypted (yet). */
    private suspend fun readPrivate(list: Event?, interactive: Boolean): List<List<String>>? {
        val content = list?.content?.takeIf { it.isNotBlank() } ?: return emptyList()
        val me = pubkey ?: return null
        val s = signer ?: return null
        val plain = runCatching {
            if (com.example.btcwidget.nostr.core.PrivateTags.isNip04(content)) s.nip04Decrypt(content, me, interactive)
            else s.nip44Decrypt(content, me, interactive)
        }.getOrNull() ?: return null
        return com.example.btcwidget.nostr.core.PrivateTags.parse(plain)
    }

    /** Opens the private parts of the mute and bookmark lists, then applies them. */
    private suspend fun openPrivateLists(interactive: Boolean) {
        if (privateMutes == null) readPrivate(muteEvent, interactive)?.let { privateMutes = it; applyMutes() }
        if (privateBookmarks == null) readPrivate(bookmarkEvent, interactive)?.let { privateBookmarks = it; applyBookmarks() }
    }

    /**
     * New tags and content for a list after removing entries matching [matches] from both its
     * public and private parts, and adding [entry] to one of them. The private part is only
     * re-encrypted when it changes, and never written over when it couldn't be read.
     */
    private suspend fun editList(
        list: Event?,
        knownPrivate: List<List<String>>?,
        matches: (List<String>) -> Boolean,
        entry: List<String>?,
        private: Boolean
    ): Triple<List<List<String>>, String, List<List<String>>?> {
        val me = requireMe()
        val publicTags = list?.tags.orEmpty().filterNot { it.size >= 2 && matches(it) }
        var priv = knownPrivate
        val needsPrivate = (entry != null && private) || (entry == null && priv == null && list?.content?.isNotBlank() == true)
        if (priv == null && needsPrivate) {
            priv = readPrivate(list, interactive = true)
            if (priv == null && entry != null) throw IllegalStateException("Couldn't open your private list (the signer didn't decrypt it)")
        }
        if (priv == null) {
            // Unreadable private part left exactly as it was.
            return Triple(if (entry != null) publicTags + listOf(entry) else publicTags, list?.content.orEmpty(), null)
        }
        val keptPrivate = priv.filterNot { it.size >= 2 && matches(it) }
        val newPrivate = if (entry != null && private) keptPrivate + listOf(entry) else keptPrivate
        val newPublic = if (entry != null && !private) publicTags + listOf(entry) else publicTags
        val content = when {
            newPrivate == priv && !list?.content.isNullOrBlank() -> list.content
            newPrivate.isEmpty() -> ""
            else -> requireSigner().nip44Encrypt(com.example.btcwidget.nostr.core.PrivateTags.serialize(newPrivate), me)
        }
        return Triple(newPublic, content, newPrivate)
    }

    /** Mutes (or unmutes) [target]: privately by default (NIP-51 encrypted entry). */
    suspend fun setMuted(target: String, mute: Boolean) = setMuteEntry("p", target, mute)

    /**
     * Adds or removes one entry ("p" account, "word", "t" hashtag or "e" thread) on the mute
     * list, keeping everything else on it (including other apps' private entries).
     */
    suspend fun setMuteEntry(type: String, value: String, mute: Boolean, private: Boolean = privateByDefault) {
        val me = requireMe()
        if (!accountFresh) loadAccount()
        val v = if (type == "word" || type == "t") value.trim().lowercase().removePrefix("#") else value
        if (v.isEmpty()) return
        val (tags, content, priv) = editList(
            muteEvent, privateMutes,
            matches = { it[0] == type && it[1].equals(v, ignoreCase = true) },
            entry = if (mute) listOf(type, v) else null, private = private
        )
        val event = publish(UnsignedEvent(me, Events.now(), Kind.MUTE_LIST, tags, content))
        muteEvent = event
        privateMutes = priv
        applyMutes()
        saveAccount()
    }

    /** Removes every muted conversation ("e" entries) in one update. */
    suspend fun unmuteAllThreads() {
        val me = requireMe()
        if (!accountFresh) loadAccount()
        val (tags, content, priv) = editList(muteEvent, privateMutes, matches = { it[0] == "e" }, entry = null, private = false)
        val event = publish(UnsignedEvent(me, Events.now(), Kind.MUTE_LIST, tags, content))
        muteEvent = event
        privateMutes = priv
        applyMutes()
        saveAccount()
    }

    /** Hidden by the mute list. [threadMuteApplies] is false inside a thread the user opened on purpose. */
    internal fun hidden(e: Event, threadMuteApplies: Boolean = true): Boolean {
        if (e.pubkey in _muted.value) return true
        val rules = _mutes.value
        return if (threadMuteApplies) rules.hides(e) else rules.copy(threads = emptySet()).hides(e)
    }

    /** False for strangers when the web-of-trust filter is on (always true until it has loaded). */
    fun trusted(pubkey: String): Boolean {
        if (pubkey == this.pubkey) return true
        val net = trust ?: return true
        return pubkey in _following.value || net.contains(pubkey)
    }

    /**
     * Loads the web of trust: the user's follows and whom they follow (up to 400 follows'
     * lists), when the filter is on. Cheap to call again.
     */
    suspend fun loadTrust() {
        val me = pubkey ?: return
        if (!NostrAccountStore.wotFilter(context)) { trust = null; _trustSize.value = 0; return }
        if (trust != null) { _trustSize.value = trust?.size ?: 0; return }
        val lists = followsContactLists()
        val net = com.example.btcwidget.nostr.core.TrustNetwork(me, _following.value, lists.values.flatten())
        trust = net
        _trustSize.value = net.size
    }

    /** Whom each followed account follows (up to 400 of them), loaded once per session. */
    @Volatile private var followsLists: Map<String, List<String>>? = null

    private suspend fun followsContactLists(): Map<String, List<String>> {
        followsLists?.let { return it }
        val follows = _following.value.toList()
        val lists = coroutineScope {
            follows.take(400).chunked(100).map { chunk ->
                async { queryEverywhere(listOf(Filter(authors = chunk, kinds = listOf(Kind.CONTACTS))), timeoutMs = 6_000) }
            }.awaitAll().flatten()
        }
        val out = lists.groupBy { it.pubkey }.mapValues { (_, versions) -> versions.maxBy { it.createdAt }.tagValues("p") }
        if (out.isNotEmpty()) followsLists = out
        return out
    }

    /** People followed by several of the people you follow, most shared first, with that count. */
    suspend fun followSuggestions(): List<Pair<Profile, Int>> {
        val me = pubkey ?: return emptyList()
        if (_following.value.isEmpty()) return emptyList()
        val ranked = com.example.btcwidget.nostr.core.FollowSuggestions.rank(me, _following.value, followsContactLists(), exclude = _muted.value)
        loadProfiles(ranked.map { it.first })
        return ranked.mapNotNull { (pk, n) -> _profiles.value[pk]?.let { it to n } }
    }

    // ---- Badges (NIP-58) ----

    fun setWotFilter(on: Boolean) {
        NostrAccountStore.setWotFilter(context, on)
        if (!on) {
            trust = null
            _trustSize.value = 0
        } else {
            scope.launch { runCatching { loadTrust() } }
        }
    }

    /**
     * Adds or removes [note] from the user's public bookmark list (NIP-51 kind 10003),
     * keeping everything else on it (other apps' entries, hashtags, links).
     */
    suspend fun setBookmarked(note: Event, on: Boolean) {
        val me = requireMe()
        if (!accountFresh) loadAccount()
        val (tags, content, priv) = editList(
            bookmarkEvent, privateBookmarks,
            matches = { it[0] == "e" && it[1] == note.id },
            entry = if (on) listOf("e", note.id, relayHint()) else null, private = privateByDefault
        )
        val event = publish(UnsignedEvent(me, Events.now(), Kind.BOOKMARKS, tags, content))
        bookmarkEvent = event
        privateBookmarks = priv
        applyBookmarks()
        saveAccount()
    }

    /** Pins [note] to the top of the user's profile (first in their kind-10001 list), or unpins it. */
    suspend fun setPinned(note: Event, on: Boolean) {
        val me = requireMe()
        require(note.pubkey == me) { "Only your own notes can be pinned" }
        if (!accountFresh) loadAccount()
        val others = pinEvent?.tags.orEmpty().filterNot { it.size >= 2 && it[0] == "e" && it[1] == note.id }
        val tags = if (on) listOf(listOf("e", note.id, relayHint())) + others else others
        val event = publish(UnsignedEvent(me, Events.now(), Kind.PINNED, tags, pinEvent?.content.orEmpty()))
        pinEvent = event
        _pinned.value = event.tagValues("e")
        saveAccount()
    }

    /** The note [pubkey] pinned first, if any. */
    suspend fun pinnedNoteOf(pubkey: String): Event? {
        val ids = if (pubkey == this.pubkey) _pinned.value
            else queryEverywhere(listOf(Filter(authors = listOf(pubkey), kinds = listOf(Kind.PINNED), limit = 1)))
                .maxByOrNull { it.createdAt }?.tagValues("e").orEmpty()
        val id = ids.firstOrNull() ?: return null
        return note(id)?.takeIf { it.pubkey == pubkey }
    }

    /** Authors of recent notes with any of [tags], most active first: one query to our relays and the search relays. */
    suspend fun topicAuthors(tags: List<String>): List<String> = coroutineScope {
        if (tags.isEmpty()) return@coroutineScope emptyList()
        val f = Filter(kinds = listOf(Kind.TEXT_NOTE), tags = mapOf("t" to tags.map { it.lowercase() }.distinct()), limit = 150)
        val ours = async { runCatching { pool.query(listOf(f), timeoutMs = 4_000) }.getOrDefault(emptyList()) }
        val indexed = async { runCatching { searchPool.query(listOf(f), timeoutMs = 4_000) }.getOrDefault(emptyList()) }
        (ours.await() + indexed.await()).distinctBy { it.id }.filter { !hidden(it) }
            .groupingBy { it.pubkey }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
    }

    /** The bookmarked notes, in bookmark order (newest first). */
    suspend fun bookmarkedNotes(): List<FeedItem> {
        val ids = _bookmarks.value
        if (ids.isEmpty()) return emptyList()
        val missing = ids.filter { !notes.containsKey(it) }
        if (missing.isNotEmpty()) pool.query(listOf(Filter(ids = missing)), 5_000).forEach { remember(it) }
        val items = ids.mapNotNull { notes[it] }.map { FeedItem(it) }
        enrich(items.map { it.note.pubkey }, items.map { it.note.id })
        return items
    }

    /**
     * New notes and reposts from [authors] after [since], delivered as they arrive (for the
     * "new posts" pill). Call the returned function to stop.
     */
    fun liveFeed(authors: List<String>, since: Long, onItem: (FeedItem) -> Unit): () -> Unit =
        pool.subscribe(listOf(Filter(authors = authors, kinds = Kind.POSTS + Kind.REPOST, since = since + 1)), { e, _ ->
            if (hidden(e)) return@subscribe
            val item = when (e.kind) {
                Kind.REPOST -> runCatching { Event.fromJson(e.content) }.getOrNull()
                    ?.takeIf { it.kind in Kind.POSTS && it.verify() }
                    ?.let { FeedItem(it, repostedBy = e.pubkey, sortTime = e.createdAt) }
                else -> FeedItem(e)
            } ?: return@subscribe
            remember(item.note)
            onItem(item)
            enrich(listOfNotNull(item.note.pubkey, item.repostedBy), listOf(item.note.id))
        })

    /** Whom the home feed is built from (follows plus the user). */
    fun homeAuthors(): List<String> = pubkey?.let { (_following.value + it).toList() }.orEmpty()

    /** Requests deletion of the user's own note (NIP-09). */
    suspend fun delete(note: Event) {
        publish(Events.deletion(requireMe(), listOf(note.id)))
        notes.remove(note.id)
    }

    suspend fun updateProfile(profile: Profile) {
        val me = requireMe()
        publish(Events.metadata(me, profile, profileEvents[me]))
    }

    /** NIP-56 report of [note] (e.g. "spam", "illegal"): public, so relays and others can act on it. */
    suspend fun report(note: Event, reason: String) {
        publish(UnsignedEvent(requireMe(), Events.now(), 1984, listOf(listOf("e", note.id, reason), listOf("p", note.pubkey)), ""))
    }

    internal fun httpGet(url: String): String {
        require(url.startsWith("https://")) { "Refusing non-HTTPS request" }
        RelayPool.defaultClient.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            val source = r.body.source()
            source.request(256 * 1024L)
            if (source.buffer.size > 256 * 1024L) error("Response too large")
            return source.buffer.readUtf8()
        }
    }

    internal fun requireMe(): String = pubkey ?: throw IllegalStateException("Log in to Nostr first")
    internal fun relayHint(): String = pool.urls.firstOrNull().orEmpty()

    private fun bump(id: String, change: (NoteStats) -> NoteStats) =
        _stats.update { it + (id to change(it[id] ?: NoteStats())) }

    companion object {
        /** Notes kept in memory, and notes whose counts are kept (see [notes], [stats]). */
        private const val MAX_NOTES = 5_000
        private const val MAX_STATS = 4_000
        /** Relays that index profiles, follow lists and relay lists (also used by Amethyst). */
        const val MAX_OWN_RELAYS = 10
        val INDEXER_RELAYS = listOf("wss://purplepag.es", "wss://user.kindpag.es", "wss://indexer.coracle.social")

        private const val PROFILES_CACHE = "profiles"
        private const val ACCOUNT_CACHE = "account"
        private const val HOME_CACHE = "home"
        private const val RELAY_LIST_CACHE = "relay_lists"

        /** BUD-03 list of the user's Blossom media servers. */
        const val MEDIA_SERVER_LIST = 10063

        /** Public relays that implement NIP-50 search. */
        /** Hashtags ranked by distinct people, then notes; lowercased; single-person tags dropped. */
        fun trendsOf(events: List<Event>): List<Trend> {
            val people = HashMap<String, MutableSet<String>>()
            val notes = HashMap<String, Int>()
            events.distinctBy { it.id }.forEach { e ->
                e.tagValues("t").map { it.trim().removePrefix("#").lowercase() }.filter { it.length in 2..40 }.distinct().forEach { t ->
                    people.getOrPut(t) { HashSet() }.add(e.pubkey)
                    notes[t] = (notes[t] ?: 0) + 1
                }
            }
            return people.filter { it.value.size >= 2 }
                .map { (t, who) -> Trend(t, who.size, notes[t] ?: 0) }
                .sortedWith(compareByDescending<Trend> { it.people }.thenByDescending { it.notes }.thenBy { it.tag })
        }

        val SEARCH_RELAYS = listOf("wss://relay.nostr.band", "wss://search.nos.today")

        @Volatile private var instance: NostrRepository? = null

        fun get(context: Context): NostrRepository =
            instance ?: synchronized(this) {
                instance ?: NostrRepository(context.applicationContext).also { instance = it }
            }
    }
}

/** Suggested when no seen note has a matching hashtag yet. */
private val DEFAULT_TAGS = listOf("bitcoin", "nostr", "lightning", "zap", "photography", "art", "music", "news", "plebchain", "grownostr", "thailand", "food", "travel", "memes", "introductions")
