package com.example.btcwidget.nostr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request

/** Cards for web links in notes: title, description and picture, fetched once per link. */
class LinkPreviews internal constructor(private val repo: NostrRepository) {
    /** url → preview (null = page has none); newest 300 kept. */
    private val previews = object : LinkedHashMap<String, com.example.btcwidget.nostr.core.LinkPreview?>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, com.example.btcwidget.nostr.core.LinkPreview?>?) = size > 300
    }
    private val previewLock = kotlinx.coroutines.sync.Mutex()

    /**
     * The card for [url], fetched once: only HTML pages, only the first 256 KB, and only
     * https. Null if the page has no title or can't be read.
     */
    suspend fun get(url: String): com.example.btcwidget.nostr.core.LinkPreview? {
        if (!url.startsWith("https://")) return null
        previewLock.withLock { if (previews.containsKey(url)) return previews[url] }
        val preview = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(url).header("Accept", "text/html").header("User-Agent", "Mozilla/5.0 (Linux; Android) Block").build()
                RelayPool.defaultClient.newCall(request).execute().use { r ->
                    val type = r.header("Content-Type").orEmpty()
                    if (!r.isSuccessful || !type.contains("html", ignoreCase = true)) return@use null
                    val source = r.body.source()
                    source.request(256 * 1024L)
                    val html = source.buffer.readUtf8(minOf(source.buffer.size, 256 * 1024L))
                    com.example.btcwidget.nostr.core.LinkPreview.parse(html, r.request.url.toString())
                }
            }.getOrNull()
        }
        previewLock.withLock { previews[url] = preview }
        return preview
    }
}
