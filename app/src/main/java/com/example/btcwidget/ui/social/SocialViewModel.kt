package com.example.btcwidget.ui.social

import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.btcwidget.nostr.FeedItem
import com.example.btcwidget.nostr.NostrNotification
import com.example.btcwidget.nostr.NostrRepository
import com.example.btcwidget.nostr.SignerRejected
import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Filter
import com.example.btcwidget.nostr.core.Kind
import com.example.btcwidget.nostr.core.Profile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A paged list with its loading state. */
data class Paged<T>(
    val items: List<T> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val endReached: Boolean = false
)

/**
 * State and actions for the Nostr screens. Data comes from [NostrRepository]; each
 * action reports failures as a short message on [messages] (shown as a toast).
 */
class SocialViewModel(application: Application) : AndroidViewModel(application) {
    val repo = NostrRepository.get(application)

    val account = repo.account
    val profiles = repo.profiles
    val stats = repo.stats
    val following = repo.following
    val muted = repo.muted

    private val _home = MutableStateFlow(Paged<FeedItem>())
    val home: StateFlow<Paged<FeedItem>> = _home.asStateFlow()

    private val _notifications = MutableStateFlow(Paged<NostrNotification>())
    val notifications: StateFlow<Paged<NostrNotification>> = _notifications.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var accountLoaded = false
    private var stopRealtimeNotifications: (() -> Unit)? = null
    private var realtimeAccount: String? = null
    private val liveNotificationIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** Keeps a relay subscription only while this ViewModel (and its foreground Activity) lives. */
    fun startRealtimeNotifications() {
        val me = repo.pubkey ?: return
        if (realtimeAccount == me && stopRealtimeNotifications != null) return
        stopRealtimeNotifications?.invoke()
        realtimeAccount = me
        val since = System.currentTimeMillis() / 1000 - 60
        stopRealtimeNotifications = repo.pool.subscribe(
            listOf(Filter(kinds = listOf(Kind.TEXT_NOTE, Kind.REPOST, Kind.REACTION, Kind.ZAP_RECEIPT), tags = mapOf("p" to listOf(me)), since = since)),
            { event, _ ->
                if (event.pubkey == me || !liveNotificationIds.add(event.id)) return@subscribe
                repo.scope.launch {
                    delay(350)
                    com.example.btcwidget.nostr.NostrBackground.notifyLiveActivity(getApplication(), event)
                    refreshNotifications()
                }
            }
        )
    }

    // Scroll positions of the main lists. Kept here, not in the screen, so switching tabs,
    // opening a note and coming back, or the Activity being recreated leaves you where you were.
    private val listStates = HashMap<String, androidx.compose.foundation.lazy.LazyListState>()
    fun listState(key: String): androidx.compose.foundation.lazy.LazyListState =
        listStates.getOrPut(key) { androidx.compose.foundation.lazy.LazyListState() }
    /** A different account's lists start from the top. */
    private fun forgetScroll() = listStates.clear()

    // ---- Home feeds (Primal-style menu) ----

    private val _homeFeeds = MutableStateFlow(loadFeeds())
    /** The feeds in the Home menu, in order. */
    val homeFeeds: StateFlow<List<com.example.btcwidget.nostr.core.HomeFeed>> = _homeFeeds.asStateFlow()
    private val _homeFeed = MutableStateFlow(loadFeed(_homeFeeds.value))
    /** The feed Home shows. */
    val homeFeed: StateFlow<com.example.btcwidget.nostr.core.HomeFeed> = _homeFeed.asStateFlow()

    private fun loadFeeds() = com.example.btcwidget.nostr.NostrAccountStore.homeFeeds(getApplication())?.mapNotNull { com.example.btcwidget.nostr.core.HomeFeed.parse(it) }?.ifEmpty { null }
        ?: com.example.btcwidget.nostr.core.HomeFeed.DEFAULTS

    private fun loadFeed(feeds: List<com.example.btcwidget.nostr.core.HomeFeed>) =
        com.example.btcwidget.nostr.NostrAccountStore.homeFeed(getApplication())?.let { com.example.btcwidget.nostr.core.HomeFeed.parse(it) }?.takeIf { it in feeds } ?: feeds.first()

    /** Switches Home to [feed] and loads it. */
    fun selectFeed(feed: com.example.btcwidget.nostr.core.HomeFeed) {
        if (feed == _homeFeed.value) return
        _homeFeed.value = feed
        listStates.remove("home:" + feed.id) // a freshly loaded feed starts at the top
        store.setHomeFeed(getApplication(), feed.id)
        stopLive?.invoke(); stopLive = null
        _newPosts.value = emptyList()
        _home.value = Paged(loading = true)
        refreshHome()
    }

    private fun saveFeeds(list: List<com.example.btcwidget.nostr.core.HomeFeed>) {
        _homeFeeds.value = list
        store.setHomeFeeds(getApplication(), list.map { it.id })
    }

    fun addFeed(feed: com.example.btcwidget.nostr.core.HomeFeed, select: Boolean = true) {
        if (feed !in _homeFeeds.value) saveFeeds(_homeFeeds.value + feed)
        if (select) selectFeed(feed)
    }

    fun removeFeed(feed: com.example.btcwidget.nostr.core.HomeFeed) {
        val next = _homeFeeds.value - feed
        if (next.isEmpty()) return
        saveFeeds(next)
        if (_homeFeed.value == feed) selectFeed(next.first())
    }

    fun moveFeed(feed: com.example.btcwidget.nostr.core.HomeFeed, by: Int) {
        val list = _homeFeeds.value.toMutableList()
        val i = list.indexOf(feed)
        val j = (i + by).coerceIn(0, list.lastIndex)
        if (i < 0 || i == j) return
        list.removeAt(i); list.add(j, feed)
        saveFeeds(list)
    }

    fun resetFeeds() {
        saveFeeds(com.example.btcwidget.nostr.core.HomeFeed.DEFAULTS)
        if (_homeFeed.value !in _homeFeeds.value) selectFeed(_homeFeeds.value.first())
    }

    private val followingFeed get() = _homeFeed.value is com.example.btcwidget.nostr.core.HomeFeed.Following

    /** Loads the account (follows, relays) once, then the home feed. */
    fun start() {
        if (repo.pubkey == null) return
        if (_home.value.items.isEmpty() && !_home.value.loading) {
            viewModelScope.launch {
                // Last session's timeline first, then fresh notes replace it.
                val cached = if (followingFeed) repo.cachedHome() else emptyList()
                if (cached.isNotEmpty() && _home.value.items.isEmpty()) _home.value = Paged(cached)
                refreshHome()
            }
        }
    }

    fun refreshHome() {
        if (repo.pubkey == null) return
        _home.update { it.copy(refreshing = it.items.isNotEmpty(), loading = it.items.isEmpty(), error = null) }
        viewModelScope.launch {
            try {
                if (!accountLoaded) {
                    repo.cacheReady.join()
                    val load = async { repo.loadAccount() }
                    // With follows from the cache, fetch the feed now and refresh the lists
                    // alongside; on the very first run the follows are needed first.
                    if (!repo.hasCachedAccount) load.await()
                    accountLoaded = true
                }
                val feed = _homeFeed.value
                val items = repo.feed(feed)
                if (feed != _homeFeed.value) return@launch // switched meanwhile
                _home.value = Paged(items, endReached = items.isEmpty() || feed is com.example.btcwidget.nostr.core.HomeFeed.Dvm)
                if (feed is com.example.btcwidget.nostr.core.HomeFeed.Following) {
                    startLiveHome()
                    if (items.isNotEmpty()) repo.saveHome(items)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _home.update { it.copy(loading = false, refreshing = false, error = tr("Couldn't load the feed.") + " " + friendlyError(e)) }
            }
        }
    }

    fun loadMoreHome() {
        val state = _home.value
        if (state.loading || state.endReached || state.items.isEmpty()) return
        _home.update { it.copy(loading = true) }
        viewModelScope.launch {
            val until = state.items.minOf { it.sortTime } - 1
            val feed = _homeFeed.value
            val more = runCatching { repo.feed(feed, until = until) }.getOrDefault(emptyList())
            if (feed != _homeFeed.value) return@launch
            _home.update { s ->
                val merged = (s.items + more).distinctBy { it.key }
                s.copy(items = merged, loading = false, endReached = more.isEmpty())
            }
        }
    }

    fun refreshNotifications() {
        if (repo.pubkey == null) return
        _notifications.update { it.copy(loading = it.items.isEmpty(), refreshing = it.items.isNotEmpty(), error = null) }
        viewModelScope.launch {
            try {
                _notifications.value = Paged(repo.notifications(), endReached = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _notifications.update { it.copy(loading = false, refreshing = false, error = e.message) }
            }
        }
    }

    // ---- Account ----

    fun loginWithAmber(onDone: () -> Unit) = act(tr("Couldn't connect to the signer")) {
        repo.loginWithAmber()
        afterLogin(); onDone()
    }

    /** Logs in with a bunker:// link; [onResult] gets an error message, or null on success. */
    fun loginWithBunker(uri: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val error = try {
                repo.loginWithBunker(uri); afterLogin(); null
            } catch (e: CancellationException) {
                throw e
            } catch (e: SignerRejected) {
                "The signer declined"
            } catch (e: Exception) {
                Log.d("SocialViewModel", "bunker login failed", e)
                e.message ?: "Couldn't connect to the signer"
            }
            onResult(error)
        }
    }

    /** Returns an error message, or null on success. */
    fun loginWithSecret(input: String): String? = try {
        repo.loginWithSecret(input)
        afterLogin()
        null
    } catch (e: IllegalArgumentException) {
        e.message ?: "That isn't a valid nsec"
    } catch (e: Exception) {
        // Keystore or crypto failure on this device.
        Log.w("SocialViewModel", "login failed", e)
        "Couldn't store the key on this phone: ${e.javaClass.simpleName}"
    } catch (e: LinkageError) {
        "This phone can't load the signing library"
    }

    /** Creates a new key; returns the nsec to show once, or null (with a message) on failure. */
    fun createAccount(name: String): String? {
        val nsec = try {
            repo.createAccount()
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Log.w("SocialViewModel", "create account failed", e)
            say(tr("Couldn't create the account on this phone: {0}", e.javaClass.simpleName))
            return null
        }
        afterLogin()
        val me = repo.pubkey
        if (name.isNotBlank() && me != null) act(tr("Couldn't publish your profile")) {
            repo.updateProfile(Profile(me, name = name.trim()))
        }
        return nsec
    }

    // ---- Unread state and phone notifications ----

    private val store = com.example.btcwidget.nostr.NostrAccountStore
    internal val app get() = getApplication<Application>()

    private val _notifSeen = MutableStateFlow(store.notificationsSeen(application))
    private val _dmRead = MutableStateFlow(store.dmRead(application))
    private val _push = MutableStateFlow(store.pushEnabled(application))

    /** Phone notifications for replies, zaps and messages while the app is closed. */
    val pushEnabled: StateFlow<Boolean> = _push.asStateFlow()

    // ---- Account settings ----

    private val _notifOff = MutableStateFlow(store.notificationsOff(application))
    /** Phone notification types turned off ("reply", "mention", "repost", "like", "zap", "dm"). */
    val notificationsOff: StateFlow<Set<String>> = _notifOff.asStateFlow()

    fun setNotificationType(type: String, on: Boolean) {
        val next = if (on) _notifOff.value - type else _notifOff.value + type
        store.setNotificationsOff(getApplication(), next)
        _notifOff.value = next
    }

    /** The nsec of an account whose key is on this phone; null for signer-app accounts. */
    fun exportNsec(): String? = store.secretKey(getApplication())
        ?.takeIf { store.account(getApplication())?.type == com.example.btcwidget.nostr.SignerType.LOCAL }
        ?.let { com.example.btcwidget.nostr.core.Nip19.nsec(it) }

    // ---- Safety: muted words, hashtags, threads; web of trust ----

    private val _wot = MutableStateFlow(store.wotFilter(application))
    val wotFilter: StateFlow<Boolean> = _wot.asStateFlow()

    fun setWotFilter(on: Boolean) {
        repo.setWotFilter(on)
        _wot.value = on
        if (on) act(tr("Couldn't load your network")) { repo.loadTrust(); say(tr("Showing replies and mentions from your network only")) }
    }

    // ---- Settings kept on this phone: video and links ----

    val devicePrefs = DevicePrefs(application)

    fun clearMediaCache(onDone: (Boolean) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val imageCleared = com.example.btcwidget.BlockApp.clearImageCache(app)
            val videoCleared = VideoCache.clear(app)
            withContext(kotlinx.coroutines.Dispatchers.Main) { onDone(imageCleared && videoCleared) }
        }
    }

    /** Notifications newer than the last time the tab was open. */
    val unreadNotifications: StateFlow<Int> = combine(_notifications, _notifSeen) { n, seen ->
        n.items.count { it.event.createdAt > seen }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Conversations whose newest message is from someone else and not read yet. */
    val unreadConversations: StateFlow<Set<String>> = combine(repo.dms.conversations, _dmRead, repo.account) { convs, read, account ->
        val me = account?.pubkey
        convs.filter { it.last.sender != me && it.last.createdAt > (read[it.key] ?: 0) }.map { it.key }.toSet()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    fun markNotificationsSeen() {
        val newest = _notifications.value.items.maxOfOrNull { it.event.createdAt } ?: return
        if (newest <= _notifSeen.value) return
        _notifSeen.value = newest
        store.setNotificationsSeen(app, newest)
        // Seen in the app: don't announce them again from the background.
        if (newest > store.notificationsNotified(app)) store.setNotificationsNotified(app, newest)
    }

    fun markConversationRead(key: String, newest: Long) {
        if (newest <= (_dmRead.value[key] ?: 0)) return
        _dmRead.update { it + (key to newest) }
        store.setDmRead(app, _dmRead.value)
        if (newest > store.dmNotified(app)) store.setDmNotified(app, newest)
    }

    fun setPushEnabled(on: Boolean) {
        store.setPushEnabled(app, on)
        _push.value = on
        if (on) com.example.btcwidget.WidgetRefreshScheduler.schedulePeriodic(app)
        else com.example.btcwidget.WidgetRefreshScheduler.cancelIfUnused(app)
    }

    /** Loads what the badges need when the app opens (and on resume). */
    fun refreshBadges() {
        if (repo.pubkey == null) return
        startDms()
        refreshNotifications()
    }

    /** Re-reads per-account state after logging in or out (the store is cleared then). */
    private fun reloadReadState() {
        _notifSeen.value = store.notificationsSeen(app)
        _dmRead.value = store.dmRead(app)
        _push.value = store.pushEnabled(app)
        _notifOff.value = store.notificationsOff(app)
        _wot.value = store.wotFilter(app)
        scheduledPosts.refresh()
        _homeFeeds.value = loadFeeds()
        _homeFeed.value = loadFeed(_homeFeeds.value)
    }

    private fun afterLogin() {
        // Another account may have been in use (Add account, Create account): nothing of its
        // feeds, live audio or scroll positions carries over.
        forgetScroll()
        stopLive?.invoke(); stopLive = null
        _newPosts.value = emptyList()
        reloadReadState()
        accountLoaded = false
        _home.value = Paged()
        _notifications.value = Paged()
        refreshHome()
    }

    // ---- Multiple accounts ----

    fun switchAccount(pubkey: String) {
        forgetScroll()
        try {
            stopLive?.invoke(); stopLive = null
            _newPosts.value = emptyList()
            repo.switchAccount(pubkey)
            afterLogin()
            repo.pubkey?.let { me -> viewModelScope.launch { runCatching { repo.refreshProfile(me) } } }
            say(tr("Switched account"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("SocialViewModel", "switch failed", e)
            say(tr("Couldn't switch account"))
        }
    }

    fun logout() {
        forgetScroll()
        repo.logout()
        reloadReadState()
        stopLive?.invoke(); stopLive = null
        _newPosts.value = emptyList()
        _home.value = Paged()
        _notifications.value = Paged()
    }

    // ---- Reading for detail screens ----

    private val savedArticlesPrefs by lazy {
        getApplication<Application>().getSharedPreferences("saved_articles_prefs", android.content.Context.MODE_PRIVATE)
    }
    private val _savedArticles = MutableStateFlow(loadSavedArticles())
    /** Articles saved to the user's offline Reading List, newest saved first. */
    val savedArticles: StateFlow<List<com.example.btcwidget.nostr.core.Article>> = _savedArticles.asStateFlow()

    private fun loadSavedArticles(): List<com.example.btcwidget.nostr.core.Article> = runCatching {
        val raw = savedArticlesPrefs.getString("events_json", null) ?: return emptyList()
        val arr = org.json.JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            runCatching { com.example.btcwidget.nostr.core.Event.fromJson(arr.getString(i)) }.getOrNull()
                ?.let { com.example.btcwidget.nostr.core.Article.of(it) }
        }
    }.getOrDefault(emptyList())

    private fun persistSavedArticles(list: List<com.example.btcwidget.nostr.core.Article>) {
        val arr = org.json.JSONArray()
        list.forEach { arr.put(it.event.toJson()) }
        savedArticlesPrefs.edit().putString("events_json", arr.toString()).apply()
    }

    fun toggleSavedArticle(article: com.example.btcwidget.nostr.core.Article) {
        val cur = _savedArticles.value
        val exists = cur.any { it.address == article.address }
        val next = if (exists) cur.filterNot { it.address == article.address } else listOf(article) + cur
        _savedArticles.value = next
        persistSavedArticles(next)
        say(if (exists) tr("Removed from Reading List") else tr("Saved to Reading List (available offline)"))
    }

    suspend fun article(address: com.example.btcwidget.nostr.core.Nip19.Address): com.example.btcwidget.nostr.core.Article? {
        val key = "${address.kind}:${address.pubkey}:${address.identifier}"
        return runCatching { repo.articles.get(address) }.getOrNull()
            ?: _savedArticles.value.firstOrNull { it.address == key }
    }

    // The side panel (wide screens) is on every page: ask the relays now and then, not on each one.
    private var trendsCache: Pair<Long, List<com.example.btcwidget.nostr.NostrRepository.Trend>>? = null
    private var livesCache: Pair<Long, List<com.example.btcwidget.nostr.core.LiveStreams.Live>>? = null
    suspend fun trendingCached(): List<com.example.btcwidget.nostr.NostrRepository.Trend> {
        trendsCache?.let { (at, l) -> if (System.currentTimeMillis() - at < 10 * 60_000 && l.isNotEmpty()) return l }
        return trendingHashtags().also { trendsCache = System.currentTimeMillis() to it }
    }
    suspend fun liveStreamsCached(): List<com.example.btcwidget.nostr.core.LiveStreams.Live> {
        livesCache?.let { (at, l) -> if (System.currentTimeMillis() - at < 2 * 60_000) return l }
        return liveStreams().also { livesCache = System.currentTimeMillis() to it }
    }

    // ---- Images ----

    /** An uploaded image waiting to be posted. */
    data class Attachment(val url: String, val mime: String, val sha256: String, val width: Int, val height: Int, val blurhash: String? = null) {
        val isVideo get() = mime.startsWith("video/")
    }

    // ---- Actions ----

    fun mute(pubkey: String, mute: Boolean) = act(tr("Couldn't update mutes")) {
        repo.setMuted(pubkey, mute)
        if (mute) _home.update { s -> s.copy(items = s.items.filter { it.note.pubkey != pubkey && it.repostedBy != pubkey }) }
        say(if (mute) tr("Muted") else tr("Unmuted"))
    }
    fun delete(note: Event) = act(tr("Couldn't delete")) {
        repo.delete(note)
        _home.update { s -> s.copy(items = s.items.filter { it.note.id != note.id }) }
        say(tr("Deletion requested"))
    }

    /** Posts a note, reply or quote with any uploaded images; [onResult] gets whether it was published. */
    fun send(
        text: String,
        replyTo: Event?,
        quote: Event?,
        images: List<Attachment> = emptyList(),
        contentWarning: String? = null,
        extraTags: List<List<String>> = emptyList(),
        /** Proof of work bits for this post; null uses the setting. */
        pow: Int? = null,
        /** Called once if mining runs long, so the composer can close while it carries on. */
        onQueued: (() -> Unit)? = null,
        onResult: (Boolean) -> Unit
    ) {
        var ok = false
        val (working, done) = when {
            replyTo != null -> tr("Replying") to tr("Replied")
            quote != null -> tr("Quoting") to tr("Quoted")
            else -> tr("Posting") to tr("Posted")
        }
        val queued = java.util.concurrent.atomic.AtomicBoolean(onQueued == null)
        tracked(working, done, tr("Couldn't post"), finally = { onResult(ok) }, onStage = { stage ->
            if (stage is com.example.btcwidget.nostr.PublishTracker.Stage.Mining && stage.elapsedMs >= QUEUE_AFTER_MS && queued.compareAndSet(false, true)) {
                viewModelScope.launch { onQueued?.invoke() }
            }
        }) {
            val bits = this.pow.bitsFor(pow ?: this.pow.difficulty.value)
            // Image URLs go in the text (so every client shows them) with imeta tags (NIP-92).
            val body = (listOf(text.trim()) + images.map { it.url }).filter { it.isNotEmpty() }.joinToString("\n")
            val tags = images.map { com.example.btcwidget.nostr.core.Imeta.tag(it.url, it.mime, it.sha256, it.width, it.height, it.blurhash) } +
                // NIP-36: other apps hide the note behind a warning until tapped.
                listOfNotNull(contentWarning?.let { listOf("content-warning", it) }) +
                extraTags
            val event = when {
                replyTo != null -> repo.reply(body, replyTo, tags, bits)
                quote != null -> repo.quote(body, quote, tags, bits)
                else -> repo.post(body, tags, bits)
            }
            if (replyTo == null) _home.update { it.copy(items = listOf(FeedItem(event)) + it.items) }
            ok = true
        }
    }

    // ---- Proof of work (NIP-13) ----

    val pow = PowSettings(application, repo, viewModelScope)

    /** Stops mining the publish [id] and sends it without proof of work. */
    fun skipPow(id: Long) {
        val tracker = trackers[id] ?: return
        tracker.skipPow.set(true)
        _activity.update { if (it?.id == id) it.copy(status = tr("Sending without proof of work…"), mining = null) else it }
    }

    /** Stops the publish [id] before it's signed: nothing is sent. */
    fun cancelPublish(id: Long) {
        cancelledByUser.add(id)
        jobs[id]?.cancel()
    }

    // ---- Scheduled posts ----

    val scheduledPosts = ScheduledPostsState(this)

    // ---- New posts while reading (the "↑ N new posts" pill) ----

    private val _newPosts = MutableStateFlow<List<FeedItem>>(emptyList())
    val newPosts: StateFlow<List<FeedItem>> = _newPosts.asStateFlow()
    private var stopLive: (() -> Unit)? = null

    private fun startLiveHome() {
        stopLive?.invoke()
        val authors = repo.homeAuthors()
        if (authors.isEmpty()) return
        val newest = _home.value.items.maxOfOrNull { it.sortTime } ?: (System.currentTimeMillis() / 1000)
        _newPosts.value = emptyList()
        stopLive = repo.liveFeed(authors, newest) { item ->
            val shown = _home.value.items.any { it.key == item.key }
            // At most the newest 300 wait (it kept growing while the app sat on another screen).
            if (!shown) _newPosts.update { pending -> (pending + item).distinctBy { it.key }.takeLast(300) }
        }
    }

    /** Puts the waiting new posts at the top of the timeline. */
    fun showNewPosts() {
        val fresh = _newPosts.value
        if (fresh.isEmpty()) return
        _newPosts.value = emptyList()
        _home.update { s -> s.copy(items = (fresh + s.items).distinctBy { it.key }.sortedByDescending { it.sortTime }) }
        repo.saveHome(_home.value.items)
    }

    override fun onCleared() {
        stopLive?.invoke()
        stopRealtimeNotifications?.invoke()
        super.onCleared()
    }

    // ---- Lists of people (NIP-51) ----

    fun renameList(list: com.example.btcwidget.nostr.core.PeopleList, title: String, description: String?) {
        val name = title.trim().ifBlank { return }
        act(tr("Couldn't rename the list")) { repo.peopleLists.update(list) { it.copy(title = name.take(80), description = description?.trim()?.ifBlank { null }) } }
        // Its Home feed carries the name too.
        val renamed = { f: com.example.btcwidget.nostr.core.HomeFeed ->
            if (f is com.example.btcwidget.nostr.core.HomeFeed.People && f.owner == list.owner && f.d == list.d) f.copy(name = name.take(80)) else f
        }
        if (_homeFeeds.value.any { renamed(it) != it }) {
            saveFeeds(_homeFeeds.value.map(renamed))
            _homeFeed.value = renamed(_homeFeed.value)
            store.setHomeFeed(getApplication(), _homeFeed.value.id)
        }
    }

    fun deleteList(list: com.example.btcwidget.nostr.core.PeopleList) {
        // Out of the Home feed menu too.
        _homeFeeds.value.filter { it is com.example.btcwidget.nostr.core.HomeFeed.People && it.owner == list.owner && it.d == list.d }.forEach { removeFeed(it) }
        act(tr("Couldn't delete the list")) { repo.peopleLists.delete(list); say(tr("List deleted")) }
    }

    // ---- Polls (NIP-88) ----

    fun createPoll(question: String, options: List<String>, multipleChoice: Boolean, durationSeconds: Long?, onResult: (Boolean) -> Unit) {
        var ok = false
        act(tr("Couldn't post the poll"), finally = { onResult(ok) }) {
            val endsAt = durationSeconds?.let { System.currentTimeMillis() / 1000 + it }
            val event = repo.polls.create(question, options, multipleChoice, endsAt)
            _home.update { it.copy(items = listOf(FeedItem(event)) + it.items) }
            ok = true
        }
    }

    // ---- Wallet (NWC) and zaps ----

    val wallet = WalletState(this)

    /**
     * Zaps [amountSat]: with a connected wallet (NWC) it pays right here; otherwise the
     * invoice goes to [onInvoice], which opens a wallet app.
     */
    fun zap(recipient: String, amountSat: Long, note: Event?, comment: String, address: String? = null, onInvoice: (String) -> Unit) {
        act(tr("Couldn't zap")) {
            val invoice = repo.zaps.invoice(recipient, amountSat, note, comment, address)
            if (repo.wallet.connection.value != null) {
                say(tr("⚡ Paying {0} sats…", amountSat))
                repo.wallet.pay(invoice)
                say(tr("⚡ Zapped {0} sats", amountSat))
                note?.let { n -> runCatching { repo.loadStats(listOf(n.id)) } }
                wallet.refresh()
            } else {
                onInvoice(invoice)
            }
        }
    }

    internal fun say(message: String) { _messages.tryEmit(message) }

    /** Puts a note you just published at the top of the Home feed. */
    internal fun showOnHome(event: Event) { _home.update { it.copy(items = listOf(FeedItem(event)) + it.items) } }

    // ---- Progress of what's being sent (repost, reply, post) ----

    /** What the status bar at the top shows while something is being published. */
    data class PublishActivity(
        val id: Long,
        val title: String,
        val status: String,
        /** 0..1, or null while waiting with no way to tell how long (signing). */
        val progress: Float?,
        val state: State,
        /** Set while proof of work is being mined: the card shows speed, time and Skip / Cancel. */
        val mining: Mining? = null
    ) {
        enum class State { WORKING, DONE, FAILED }

        data class Mining(val difficulty: Int, val hashes: Long, val hashesPerSecond: Double, val elapsedMs: Long) {
            /** Average time this difficulty takes at the current speed. */
            val expectedSeconds: Double get() = com.example.btcwidget.nostr.core.Pow.expectedSeconds(difficulty, hashesPerSecond)
            /** How likely it would have been found by now (mining has no fixed length). */
            val chanceDone: Double get() = com.example.btcwidget.nostr.core.Pow.chanceDone(hashes, difficulty)
        }
    }

    private val _activity = MutableStateFlow<PublishActivity?>(null)
    /** The latest publish in progress (or just finished), or null when there's nothing to show. */
    val activity: StateFlow<PublishActivity?> = _activity.asStateFlow()
    private val nextActivity = java.util.concurrent.atomic.AtomicLong()

    /**
     * Runs [block] like [act], showing its progress in the status bar: signing (and with which
     * signer), then relays answering, then [done] with a tick, or [failure] in red.
     */
    private val trackers = java.util.concurrent.ConcurrentHashMap<Long, com.example.btcwidget.nostr.PublishTracker>()
    private val jobs = java.util.concurrent.ConcurrentHashMap<Long, kotlinx.coroutines.Job>()
    private val cancelledByUser = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

    internal fun tracked(
        working: String,
        done: String,
        failure: String,
        finally: () -> Unit = {},
        onStage: (com.example.btcwidget.nostr.PublishTracker.Stage) -> Unit = {},
        block: suspend () -> Unit
    ) {
        val id = nextActivity.incrementAndGet()
        // A newer action replaces an older one in the bar; an older one never covers a newer.
        fun show(a: PublishActivity) = _activity.update { cur -> if (cur == null || cur.id <= id) a else cur }
        fun clearLater(ms: Long) = viewModelScope.launch { delay(ms); _activity.update { if (it?.id == id) null else it } }
        val finishedFlag = java.util.concurrent.atomic.AtomicBoolean(false)
        // The last long-enough mining report: its speed is saved once, when the publish ends.
        val lastMining = java.util.concurrent.atomic.AtomicReference<com.example.btcwidget.nostr.PublishTracker.Stage.Mining?>(null)
        val tracker = com.example.btcwidget.nostr.PublishTracker { stage ->
            onStage(stage)
            when (stage) {
                is com.example.btcwidget.nostr.PublishTracker.Stage.Mining -> {
                    if (stage.elapsedMs >= 1_000) lastMining.set(stage)
                    val rate = stage.hashesPerSecond.takeIf { stage.elapsedMs >= 300 } ?: pow.hashRate.value
                    val m = PublishActivity.Mining(stage.difficulty, stage.hashes, rate, stage.elapsedMs)
                    if (!finishedFlag.get() && trackers[id]?.skipPow?.get() != true) show(PublishActivity(
                        id, working, tr("Mining proof of work · {0} bits", stage.difficulty),
                        // Most of the bar is the work; signing and sending fill the rest.
                        (0.75 * m.chanceDone).toFloat().coerceAtMost(0.75f), PublishActivity.State.WORKING, m
                    ))
                }
                is com.example.btcwidget.nostr.PublishTracker.Stage.Signing -> if (!finishedFlag.get()) show(
                    PublishActivity(id, working, when (stage.signer) {
                        com.example.btcwidget.nostr.SignerType.AMBER -> tr("Waiting for your signer app…")
                        com.example.btcwidget.nostr.SignerType.BUNKER -> tr("Waiting for your remote signer…")
                        else -> tr("Signing…")
                    }, null, PublishActivity.State.WORKING)
                )
                is com.example.btcwidget.nostr.PublishTracker.Stage.Sending -> {
                    val total = stage.total.coerceAtLeast(1)
                    if (!finishedFlag.get()) show(PublishActivity(
                        id, working,
                        if (stage.answered == 0) tr("Sending to {0} relays…", stage.total) else tr("Sending · {0} of {1} relays answered", stage.answered, stage.total),
                        // Never quite full until it's done: the first acceptance finishes it.
                        (0.1f + 0.85f * stage.answered / total).coerceAtMost(0.95f), PublishActivity.State.WORKING
                    ))
                    // Done already: keep counting the relays that have it while the tick shows.
                    else _activity.update { cur ->
                        if (cur?.id == id && cur.state == PublishActivity.State.DONE)
                            cur.copy(status = tr("On {0} of {1} relays", stage.accepted, stage.total)) else cur
                    }
                }
            }
        }
        show(PublishActivity(id, working, tr("Preparing…"), null, PublishActivity.State.WORKING))
        trackers[id] = tracker
        jobs[id] = viewModelScope.launch {
            try {
                withContext(tracker) { block() }
                finishedFlag.set(true)
                show(PublishActivity(id, "$done ✓", tr("Sent"), 1f, PublishActivity.State.DONE))
                clearLater(1_800)
            } catch (e: CancellationException) {
                finishedFlag.set(true)
                if (cancelledByUser.remove(id)) {
                    show(PublishActivity(id, tr("Cancelled"), tr("Nothing was sent. Your text is kept as a draft."), 1f, PublishActivity.State.FAILED))
                    clearLater(3_000)
                } else _activity.update { if (it?.id == id) null else it }
                throw e
            } catch (e: SignerRejected) {
                finishedFlag.set(true)
                show(PublishActivity(id, failure, tr("Cancelled in the signer"), 1f, PublishActivity.State.FAILED))
                clearLater(3_000)
            } catch (e: com.example.btcwidget.nostr.core.PublishRejected) {
                finishedFlag.set(true)
                show(PublishActivity(id, failure, rejectionText(e.why), 1f, PublishActivity.State.FAILED))
                // Long enough to read what to do about it.
                clearLater(7_000)
            } catch (e: Exception) {
                finishedFlag.set(true)
                Log.d("SocialViewModel", failure, e)
                show(PublishActivity(id, failure, friendlyError(e), 1f, PublishActivity.State.FAILED))
                clearLater(5_000)
            } finally {
                lastMining.get()?.let { pow.rememberHashRate(it.hashesPerSecond) }
                trackers.remove(id)
                jobs.remove(id)
                finally()
            }
        }
    }

    internal fun act(failure: String, finally: () -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: SignerRejected) {
                say(tr("Cancelled in the signer"))
            } catch (e: Exception) {
                Log.d("SocialViewModel", failure, e)
                say(failure + " · " + if (e is com.example.btcwidget.nostr.core.PublishRejected) rejectionText(e.why) else friendlyError(e))
            } finally {
                finally()
            }
        }
    }
}

/** Why relays turned a post down, in the user's language, with what to do about it. */
internal fun rejectionText(why: com.example.btcwidget.nostr.core.RelayRejection.Explained): String = when (why.reason) {
    com.example.btcwidget.nostr.core.RelayRejection.Reason.POW ->
        tr("A relay asks for proof of work ({0}). Turn it on in Settings › Proof of work and post again.", why.detail)
    com.example.btcwidget.nostr.core.RelayRejection.Reason.RATE_LIMITED -> tr("Relays are limiting how fast you post. Wait a minute and try again.")
    com.example.btcwidget.nostr.core.RelayRejection.Reason.AUTH -> tr("Your relays want you to sign in to them first. Try again in a moment, or add other relays in Settings › Relays.")
    com.example.btcwidget.nostr.core.RelayRejection.Reason.PAYMENT -> tr("These are paid relays. Pay for one, or add free relays in Settings › Relays.")
    com.example.btcwidget.nostr.core.RelayRejection.Reason.RESTRICTED -> tr("Your relays only take posts from their members. Add other relays in Settings › Relays.")
    com.example.btcwidget.nostr.core.RelayRejection.Reason.BLOCKED -> tr("Your relays won't take this post ({0}). Try other relays in Settings › Relays.", why.detail)
    com.example.btcwidget.nostr.core.RelayRejection.Reason.INVALID -> tr("Relays say the post is invalid ({0}).", why.detail)
    com.example.btcwidget.nostr.core.RelayRejection.Reason.DUPLICATE -> tr("Your relays already have this post.")
    com.example.btcwidget.nostr.core.RelayRejection.Reason.NO_ANSWER -> tr("No relay answered. Check your connection and try again.")
    com.example.btcwidget.nostr.core.RelayRejection.Reason.OTHER -> tr("Relays turned it down: {0}", why.detail)
}

/** Any other failure, worded for people: no connection, timed out, or what it said. */
internal fun friendlyError(e: Throwable): String = when (e) {
    is java.net.UnknownHostException, is java.net.ConnectException -> tr("No internet connection. Check it and try again.")
    is java.net.SocketTimeoutException, is kotlinx.coroutines.TimeoutCancellationException -> tr("That took too long. Check your connection and try again.")
    else -> e.message?.takeIf { it.isNotBlank() }?.let { tr("Something went wrong: {0}", it) } ?: tr("Check your connection and try again.")
}

/** Mining longer than this lets the composer close while the post carries on in the status bar. */
private const val QUEUE_AFTER_MS = 700L

