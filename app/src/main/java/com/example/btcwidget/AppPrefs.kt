package com.example.btcwidget

import android.content.Context
import android.content.SharedPreferences

/**
 * The single place for the app's settings and per-widget state. All keys live here;
 * nothing else should read or write this file.
 *
 * Storage map (each file has one owner):
 * - [FILE] "blockclock_prefs": user settings and widget state — this object.
 * - "btc_widget_prefs": cached market data — [PriceRepository].
 * Keeping the cache in its own file means clearing it never touches settings. File and key names are unchanged from earlier versions, so nothing migrates.
 */
object AppPrefs {
    const val FILE = "blockclock_prefs"

    private const val CURRENCY = "currency"
    private const val REFRESH_INTERVAL = "refresh_interval"
    private const val THEME = "app_theme"
    private const val MODE_ORDER = "mode_order"
    private const val COLLAPSED = "collapsed_categories"
    private const val PRICE_SOURCE = "price_source"
    private const val THB_SOURCE = "thb_source"
    private const val STACK_SATS = "stack_sats"
    private const val STACK_UNIT = "stack_unit"
    private const val HISTORY = "history_cache"
    private const val HISTORY_AT = "history_fetched_at"
    private const val MEMPOOL_URL = "mempool_url"
    private const val EVENT_RETENTION_DAYS = "event_retention_days"
    private const val VIDEO_AUTOPLAY = "video_autoplay"
    private const val VIDEO_QUALITY = "video_quality"
    private const val LANDING_DONE = "landing_done"
    private const val LINKS_IN_APP = "links_in_app"
    private const val INSTANT_PUSH = "instant_push"
    private const val APP_LOOK = "app_look"
    private const val BTC_CARDS = "btc_tx_cards"
    private const val POW_HASH_RATE = "pow_hash_rate"
    private const val LANGUAGE = "language"
    private const val RECENT_SEARCHES = "recent_searches"
    private const val RECENT_PROFILES = "recent_profiles"
    private fun modeEnabled(mode: Int) = "mode_enabled_$mode"
    private fun widgetMode(id: Int) = "mode_$id"
    private fun halvingMode(id: Int) = "halving_mode_$id"
    private fun quoteIndex(id: Int) = "quote_index_$id"
    private fun chartRange(id: Int) = "chart_range_$id"

    /** Keys written by older versions for features that no longer exist. */
    private val OBSOLETE = listOf(
        "haptic_enabled", "blockclock_theme",
        // The app lock (removed): its PIN hash and salt, failures and choices.
        "app_pin_salt", "app_pin_hash", "app_pin_length", "app_pin_failures", "app_pin_locked_until", "app_biometric_lock", "app_lock_preference", "app_lock_scope", "app_lock_after_ms",
        // The Bitcoin dashboard (removed): its large panel and whether the stack was hidden there.
        "hero_metric", "stack_hidden",
        // Proof of work settings (removed: it's now only what relays ask for, automatically).
        "pow_difficulty", "pow_match_relays",
        // The private/public choice for new bookmarks and mutes (removed: they're always private).
        "private_lists",
        // Separate block-card switch and transaction-card styles (now one switch, one style).
        "btc_block_cards", "btc_tx_style_feed", "btc_tx_style_post",
        // "What's new" and the one-time tips (removed).
        "whats_new_seen", "tips_seen"
    )

    const val DEFAULT_REFRESH_MINUTES = 30

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private inline fun edit(context: Context, block: SharedPreferences.Editor.() -> Unit) =
        prefs(context).edit().apply(block).apply()

    // ---- Display ----

    /** Open web links in Block's own browser (true, the default) or the phone's browser. */
    fun linksInApp(context: Context): Boolean = prefs(context).getBoolean(LINKS_IN_APP, true)
    fun setLinksInApp(context: Context, inApp: Boolean) = edit(context) { putBoolean(LINKS_IN_APP, inApp) }

    /**
     * Instant notifications (a connection to the relays kept open): true or false once the user
     * chose, null if never asked. A device setting: it's about this phone's battery.
     */
    fun instantPush(context: Context): Boolean? =
        if (prefs(context).contains(INSTANT_PUSH)) prefs(context).getBoolean(INSTANT_PUSH, false) else null
    fun setInstantPush(context: Context, on: Boolean) = edit(context) { putBoolean(INSTANT_PUSH, on) }


    /** The app's colour theme (screens, menus); widgets keep their own [theme]. */
    fun appLook(context: Context): com.example.btcwidget.theme.AppLook =
        com.example.btcwidget.theme.AppLook.of(prefs(context).getString(APP_LOOK, null))
    fun setAppLook(context: Context, look: com.example.btcwidget.theme.AppLook) = edit(context) { putString(APP_LOOK, look.id) }

    /** Transaction and block cards under notes that link to them (Settings › Display); on by default. */
    fun btcCards(context: Context): Boolean = prefs(context).getBoolean(BTC_CARDS, true)
    fun setBtcCards(context: Context, on: Boolean) = edit(context) { putBoolean(BTC_CARDS, on) }

    /** This phone's last measured hashes per second (0 = not measured yet), for time estimates. */
    fun powHashRate(context: Context): Float = prefs(context).getFloat(POW_HASH_RATE, 0f)
    fun setPowHashRate(context: Context, rate: Float) = edit(context) { putFloat(POW_HASH_RATE, rate) }

    /** Whether the welcome page was passed (logged in, or chose to look around first). */
    fun landingDone(context: Context): Boolean = prefs(context).getBoolean(LANDING_DONE, false)
    fun setLandingDone(context: Context) = edit(context) { putBoolean(LANDING_DONE, true) }

    /** When videos in notes play by themselves (muted): a [com.example.btcwidget.ui.social.VideoAutoplay] name. */
    fun videoAutoplay(context: Context): String? = prefs(context).getString(VIDEO_AUTOPLAY, null)
    fun setVideoAutoplay(context: Context, value: String) = edit(context) { putString(VIDEO_AUTOPLAY, value) }

    /** Default video quality: a [com.example.btcwidget.ui.social.VideoQuality] name. */
    fun videoQuality(context: Context): String? = prefs(context).getString(VIDEO_QUALITY, null)
    fun setVideoQuality(context: Context, value: String) = edit(context) { putString(VIDEO_QUALITY, value) }

    /** The app's language: a [com.example.btcwidget.ui.social.Lang.Choice] name (the phone's by default). */
    fun language(context: Context): com.example.btcwidget.ui.social.Lang.Choice =
        com.example.btcwidget.ui.social.Lang.Choice.entries.firstOrNull { it.name == prefs(context).getString(LANGUAGE, null) }
            ?: com.example.btcwidget.ui.social.Lang.Choice.SYSTEM
    fun setLanguage(context: Context, value: com.example.btcwidget.ui.social.Lang.Choice) = edit(context) { putString(LANGUAGE, value.name) }

    /** What you searched for lately, newest first (kept only on this phone). */
    fun recentSearches(context: Context): List<String> =
        prefs(context).getString(RECENT_SEARCHES, null).orEmpty().split('\n').filter { it.isNotBlank() }
    fun addRecentSearch(context: Context, query: String) {
        val q = query.trim().take(100)
        if (q.isEmpty()) return
        val list = (listOf(q) + recentSearches(context).filterNot { it.equals(q, ignoreCase = true) }).take(10)
        edit(context) { putString(RECENT_SEARCHES, list.joinToString("\n")) }
    }
    fun removeRecentSearch(context: Context, query: String) =
        edit(context) { putString(RECENT_SEARCHES, recentSearches(context).filterNot { it == query }.joinToString("\n")) }
    fun clearRecentSearches(context: Context) = edit(context) { remove(RECENT_SEARCHES) }

    /** Profiles opened from Search lately (pubkeys), newest first, kept only on this phone. */
    fun recentProfiles(context: Context): List<String> =
        prefs(context).getString(RECENT_PROFILES, null).orEmpty().split('\n').filter { it.isNotBlank() }
    fun addRecentProfile(context: Context, pubkey: String) {
        val list = (listOf(pubkey) + recentProfiles(context).filterNot { it == pubkey }).take(12)
        edit(context) { putString(RECENT_PROFILES, list.joinToString("\n")) }
    }
    fun clearRecentProfiles(context: Context) = edit(context) { remove(RECENT_PROFILES) }

    fun currency(context: Context): String = prefs(context).getString(CURRENCY, "USD") ?: "USD"
    fun setCurrency(context: Context, value: String) = edit(context) { putString(CURRENCY, value) }

    fun refreshInterval(context: Context): Int = prefs(context).getInt(REFRESH_INTERVAL, DEFAULT_REFRESH_MINUTES)
    fun setRefreshInterval(context: Context, minutes: Int) = edit(context) { putInt(REFRESH_INTERVAL, minutes) }

    fun theme(context: Context): AppTheme = AppTheme.fromName(prefs(context).getString(THEME, null))
    fun setTheme(context: Context, value: AppTheme) = edit(context) { putString(THEME, value.name) }

    /** Colors and effects for widgets, from [theme]. */
    fun palette(context: Context): Palette = theme(context).palette

    // ---- Metrics on the widget ----

    fun isModeEnabled(context: Context, mode: Int): Boolean = prefs(context).getBoolean(modeEnabled(mode), true)
    fun setModeEnabled(context: Context, mode: Int, enabled: Boolean) = edit(context) { putBoolean(modeEnabled(mode), enabled) }

    /** On/off state of every mode, indexed by mode. */
    fun enabledModes(context: Context): List<Boolean> {
        val p = prefs(context)
        return (0 until BlockclockMetrics.MODE_COUNT).map { p.getBoolean(modeEnabled(it), true) }
    }

    fun modeOrder(context: Context): List<String> = BlockclockMetrics.parseOrder(prefs(context).getString(MODE_ORDER, null))
    fun setModeOrder(context: Context, order: List<String>) =
        edit(context) { putString(MODE_ORDER, BlockclockMetrics.serializeOrder(order)) }

    fun collapsedCategories(context: Context): Set<MetricCategory> =
        prefs(context).getStringSet(COLLAPSED, emptySet()).orEmpty()
            .mapNotNull { name -> MetricCategory.entries.firstOrNull { it.name == name } }
            .toSet()

    fun setCollapsedCategories(context: Context, value: Set<MetricCategory>) =
        edit(context) { putStringSet(COLLAPSED, value.map { it.name }.toSet()) }

    // ---- Data sources ----

    fun dataSources(context: Context): DataSources {
        val p = prefs(context)
        return DataSources(
            price = PriceSource.from(p.getString(PRICE_SOURCE, null)),
            thb = ThbSource.from(p.getString(THB_SOURCE, null))
        )
    }

    fun setDataSources(context: Context, value: DataSources) = edit(context) {
        putString(PRICE_SOURCE, value.price.name)
        putString(THB_SOURCE, value.thb.name)
    }

    /** Custom base URL for the mempool.space compatible Bitcoin API; empty uses public mempool.space. */
    fun mempoolUrl(context: Context): String = prefs(context).getString(MEMPOOL_URL, "").orEmpty()
    fun setMempoolUrl(context: Context, value: String) = edit(context) {
        val normalized = value.trim().trimEnd('/')
        if (normalized.isEmpty()) remove(MEMPOOL_URL) else putString(MEMPOOL_URL, normalized)
    }

    /** Automatic Nostr event retention; zero disables age-based cleanup. */
    fun eventRetentionDays(context: Context): Int = prefs(context).getInt(EVENT_RETENTION_DAYS, 30)
    fun setEventRetentionDays(context: Context, days: Int) = edit(context) {
        putInt(EVENT_RETENTION_DAYS, days.takeIf { it in setOf(0, 30, 90) } ?: 30)
    }

    // ---- Your stack (stored only on this device, in satoshis) ----

    fun stackSats(context: Context): Long = prefs(context).getLong(STACK_SATS, 0L)

    fun stackUnit(context: Context): StackUnit =
        prefs(context).getString(STACK_UNIT, null)
            ?.let { name -> StackUnit.entries.firstOrNull { it.name == name } } ?: StackUnit.BTC

    fun setStack(context: Context, sats: Long, unit: StackUnit) = edit(context) {
        putLong(STACK_SATS, sats.coerceAtLeast(0))
        putString(STACK_UNIT, unit.name)
    }

    /** Forgets settings from features that were removed (the stack's cost basis, for P/L). */
    fun dropRetiredSettings(context: Context) {
        val p = prefs(context)
        val retired = listOf("stack_cost_USD", "stack_cost_THB").filter { p.contains(it) }
        if (retired.isNotEmpty()) edit(context) { retired.forEach { remove(it) } }
    }

    // ---- Price history cache (for charts, works offline) ----

    fun history(context: Context): List<PricePoint> = ChartMath.decode(prefs(context).getString(HISTORY, null))

    /** When the cached history was fetched (epoch ms), 0 if never. */
    fun historyFetchedAt(context: Context): Long = prefs(context).getLong(HISTORY_AT, 0L)

    fun setHistory(context: Context, points: List<PricePoint>) = edit(context) {
        putString(HISTORY, ChartMath.encode(points))
        putLong(HISTORY_AT, System.currentTimeMillis())
    }

    // ---- Per-widget state ----

    fun widgetMode(context: Context, appWidgetId: Int): Int = prefs(context).getInt(widgetMode(appWidgetId), 0)
    fun setWidgetMode(context: Context, appWidgetId: Int, mode: Int) = edit(context) { putInt(widgetMode(appWidgetId), mode) }

    fun halvingMode(context: Context, appWidgetId: Int): Int = prefs(context).getInt(halvingMode(appWidgetId), 0)
    fun setHalvingMode(context: Context, appWidgetId: Int, mode: Int) = edit(context) { putInt(halvingMode(appWidgetId), mode) }

    fun quoteIndex(context: Context, appWidgetId: Int): Int = prefs(context).getInt(quoteIndex(appWidgetId), 0)
    fun setQuoteIndex(context: Context, appWidgetId: Int, index: Int) = edit(context) { putInt(quoteIndex(appWidgetId), index) }

    fun chartRange(context: Context, appWidgetId: Int): ChartRange {
        val name = prefs(context).getString(chartRange(appWidgetId), null)
        return ChartRange.entries.firstOrNull { it.name == name } ?: ChartRange.DAY
    }

    fun setChartRange(context: Context, appWidgetId: Int, range: ChartRange) =
        edit(context) { putString(chartRange(appWidgetId), range.name) }

    /** Removes the state of widgets the user has deleted from the home screen. */
    fun forgetWidgets(context: Context, appWidgetIds: IntArray) = edit(context) {
        for (id in appWidgetIds) {
            remove(widgetMode(id))
            remove(halvingMode(id))
            remove(quoteIndex(id))
            remove(chartRange(id))
        }
    }

    /** Drops keys and files left by features removed in earlier versions. */
    fun removeObsoleteKeys(context: Context) {
        val p = prefs(context)
        if (OBSOLETE.any { p.contains(it) }) edit(context) { OBSOLETE.forEach { remove(it) } }
        // Relay groups (removed): the ones the user had opened.
        runCatching { context.getSharedPreferences("nostr_account", Context.MODE_PRIVATE).edit().remove("groups").apply() }
        // Price alerts (removed): their rules file and their notification channel.
        runCatching { context.deleteSharedPreferences("alert_prefs") }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) runCatching {
            context.getSystemService(android.app.NotificationManager::class.java)?.deleteNotificationChannel("price_alerts")
        }
    }
}
