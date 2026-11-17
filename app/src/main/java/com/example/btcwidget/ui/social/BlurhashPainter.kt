package com.example.btcwidget.ui.social

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import com.example.btcwidget.nostr.core.Blurhash

private val blurhashCache = object : LruCache<String, BitmapPainter>(128) {}

/**
 * The blurred preview for [hash] (a note's imeta "blurhash"), to show while the image loads:
 * a 32 × 32 picture, stretched smoothly over the image's box. Null when there's no valid hash.
 * Cached in memory so scrolling up and down never re-decodes trigonometric math on the UI thread.
 */
@Composable
fun rememberBlurhashPainter(hash: String?): Painter? = remember(hash) {
    if (hash.isNullOrBlank()) return@remember null
    blurhashCache.get(hash) ?: run {
        val pixels = Blurhash.decode(hash, SIZE, SIZE) ?: return@remember null
        BitmapPainter(Bitmap.createBitmap(pixels, SIZE, SIZE, Bitmap.Config.ARGB_8888).asImageBitmap()).also {
            blurhashCache.put(hash, it)
        }
    }
}

private const val SIZE = 32
