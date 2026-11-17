package com.example.btcwidget.nostr

import android.content.Context
import com.example.btcwidget.nostr.core.DmFile
import java.io.File
import java.security.MessageDigest

/**
 * Decrypted files from private messages (videos, audio) that need a real file to play.
 *
 * Each one is named after the SHA-256 of its own decrypted bytes, never after the name the
 * sender gave it, so a crafted name (`../shared_prefs/…`) can't write outside this folder and
 * two different files can't share a name. The folder is emptied when the app starts and
 * when the account changes or logs out, so decrypted copies don't stay on the phone.
 */
object DmMediaCache {
    private const val DIR = "dm_media"

    /** A file holding [bytes], written if it isn't there yet. Call off the main thread. */
    fun fileFor(context: Context, info: DmFile.Info, bytes: ByteArray): File {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val file = File(dir, cacheName(info.mime, bytes))
        if (!file.exists() || file.length() != bytes.size.toLong()) {
            val tmp = File(dir, file.name + ".tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(file)) { tmp.delete(); file.writeBytes(bytes) }
        }
        return file
    }

    /** Deletes every decrypted copy. */
    fun clear(context: Context) {
        runCatching { File(context.cacheDir, DIR).deleteRecursively() }
    }

    /** `<sha256 of bytes>.<extension from the mime type>`: only hex, a dot and letters/digits. */
    fun cacheName(mime: String, bytes: ByteArray): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return "$hash.${extensionFor(mime)}"
    }

    /** A short, safe file extension for [mime]. */
    fun extensionFor(mime: String): String = when (mime.lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        "audio/mpeg", "audio/mp3" -> "mp3"
        "audio/mp4", "audio/m4a", "audio/aac" -> "m4a"
        "audio/ogg" -> "ogg"
        "application/pdf" -> "pdf"
        "application/zip" -> "zip"
        else -> mime.substringAfter('/', "bin").filter { it.isLetterOrDigit() }.take(6).ifEmpty { "bin" }
    }

    /**
     * The sender's file name made safe to show and to suggest when saving: folders and
     * control characters taken off, no leading dots, at most 80 characters. Null when
     * nothing usable is left.
     */
    fun safeDisplayName(name: String?): String? {
        val base = name?.substringAfterLast('/')?.substringAfterLast('\\') ?: return null
        val clean = base.filter { !it.isISOControl() }.trim().trimStart('.').take(80).trim()
        return clean.takeIf { it.isNotEmpty() }
    }
}
