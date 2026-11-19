package com.example.btcwidget.nostr

import com.example.btcwidget.nostr.core.Blossom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/** Photo and video uploads to the user's Blossom servers, with the public default as fallback. */
class Uploads internal constructor(private val repo: NostrRepository) {

    /**
     * Uploads [image] to the user's Blossom servers in order, with the public default as fallback.
     * Signing the upload permission goes through the user's signer.
     */
    suspend fun image(image: PreparedImage): Blossom.Blob {
        val sha = Blossom.sha256Hex(image.bytes)
        return blob(sha, image.bytes.size.toLong()) { image.bytes.toRequestBody(image.mime.toMediaType()) }
    }

    /** Uploads already-encrypted bytes (a photo for a private message). */
    suspend fun encrypted(bytes: ByteArray): Blossom.Blob {
        val sha = Blossom.sha256Hex(bytes)
        return blob(sha, bytes.size.toLong()) { bytes.toRequestBody("application/octet-stream".toMediaType()) }
    }

    /** Uploads a video as-is; the cached copy is deleted afterwards. */
    suspend fun video(video: PreparedVideo): Blossom.Blob = try {
        blob(video.sha256, video.size) { video.file.asRequestBody(video.mime.toMediaType()) }
    } finally {
        video.file.delete()
    }

    /**
     * Uploads a file to the user's Blossom servers in order, with the public default as fallback.
     * Signing the upload permission goes through the user's signer.
     */
    private suspend fun blob(sha: String, size: Long, body: () -> okhttp3.RequestBody): Blossom.Blob {
        val me = repo.requireMe()
        val auth = repo.requireSigner().sign(Blossom.uploadAuth(me, sha, size))
        val header = Blossom.authHeader(auth)
        val servers = (repo.mediaServers + Blossom.DEFAULT_SERVER).distinct()
        var failure: Exception? = null
        for (server in servers) {
            try {
                return withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url(Blossom.uploadUrl(server))
                        .header("Authorization", header)
                        .put(body())
                        .build()
                    RelayPool.uploadClient.newCall(request).execute().use { r ->
                        if (!r.isSuccessful) {
                            error(r.header("X-Reason") ?: "${server.removePrefix("https://")} refused the upload (HTTP ${r.code})")
                        }
                        Blossom.parseUpload(r.body.string(), sha).getOrThrow()
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
}
