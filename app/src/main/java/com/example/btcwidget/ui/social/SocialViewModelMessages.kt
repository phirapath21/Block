package com.example.btcwidget.ui.social

import android.app.Application
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Private messages (NIP-17).
// Extensions on SocialViewModel, kept apart from the class: they use only what it shares.

fun SocialViewModel.setDmInbox(urls: List<String>, onDone: () -> Unit) =
    act(tr("Couldn't save inbox relays")) { repo.dms.setUpInbox(urls); say(tr("Inbox relays saved")); onDone() }

val SocialViewModel.dmConversations get() = repo.dms.conversations

val SocialViewModel.dmMessages get() = repo.dms.messages

val SocialViewModel.dmLoading get() = repo.dms.loading

val SocialViewModel.dmLocked get() = repo.dms.locked

val SocialViewModel.dmInbox get() = repo.dms.inbox

val SocialViewModel.dmUnsentIds get() = repo.dms.unsentIds

fun SocialViewModel.startDms() { if (repo.pubkey != null) viewModelScope.launch { runCatching { repo.dms.start() } } }

/** Sends a private message; [onResult] gets whether it was delivered. */
fun SocialViewModel.sendDm(peers: List<String>, text: String, replyTo: String? = null, onResult: (Boolean) -> Unit) {
    var ok = false
    act(tr("Couldn't send the message"), finally = { onResult(ok) }) { repo.dms.send(peers, text, replyTo); ok = true }
}

fun SocialViewModel.reactDm(peers: List<String>, target: com.example.btcwidget.nostr.core.Nip17.Message, emoji: String) =
    act(tr("Couldn't react")) { repo.dms.react(peers, target, emoji) }

fun SocialViewModel.deleteDmForMe(messageId: String) {
    repo.dms.deleteForMe(messageId)
    say(tr("Message deleted for you"))
}

fun SocialViewModel.unsendDm(peers: List<String>, target: com.example.btcwidget.nostr.core.Nip17.Message) =
    act(tr("Couldn't unsend the message")) {
        repo.dms.unsend(peers, target)
        say(tr("Message unsent"))
    }

/** Sends a photo, encrypted on the phone (location data removed first). */
fun SocialViewModel.sendDmImage(peers: List<String>, uri: android.net.Uri, replyTo: String? = null, onResult: (Boolean) -> Unit) {
    var ok = false
    act(tr("Couldn't send the photo"), finally = { onResult(ok) }) {
        val prepared = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            com.example.btcwidget.nostr.ImagePrep.prepare(getApplication(), uri)
        }
        repo.dms.sendImage(peers, prepared, replyTo)
        ok = true
    }
}

/** Sends an encrypted video (NIP-17 kind 15, AES-GCM encrypted up to 25 MB). */
fun SocialViewModel.sendDmVideo(peers: List<String>, uri: android.net.Uri, replyTo: String? = null, onResult: (Boolean) -> Unit) {
    var ok = false
    act(tr("Couldn't send the video"), finally = { onResult(ok) }) {
        val (bytes, mime, dims) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val prep = com.example.btcwidget.nostr.VideoPrep.prepare(getApplication(), uri)
            try {
                if (prep.size > com.example.btcwidget.nostr.DirectMessages.MAX_FILE) {
                    error(tr("Videos in encrypted chats can be up to 25 MB"))
                }
                Triple(prep.file.readBytes(), prep.mime, prep.width to prep.height)
            } finally {
                prep.file.delete()
            }
        }
        repo.dms.sendFile(peers, bytes, mime, fileName = null, width = dims.first, height = dims.second, replyTo = replyTo)
        ok = true
    }
}

/** Sends any encrypted file, audio, or document (NIP-17 kind 15, AES-GCM encrypted up to 25 MB). */
fun SocialViewModel.sendDmFile(peers: List<String>, uri: android.net.Uri, replyTo: String? = null, onResult: (Boolean) -> Unit) {
    var ok = false
    act(tr("Couldn't send the file"), finally = { onResult(ok) }) {
        val (bytes, mime, fileName) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val app = getApplication<android.app.Application>()
            val resolver = app.contentResolver
            val type = resolver.getType(uri) ?: "application/octet-stream"
            var name: String? = null
            runCatching {
                resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) name = c.getString(0)
                }
            }
            val raw = (resolver.openInputStream(uri) ?: error(tr("Couldn't open the file"))).use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > com.example.btcwidget.nostr.DirectMessages.MAX_FILE) {
                        error(tr("Files in encrypted chats can be up to 25 MB"))
                    }
                    out.write(buf, 0, n)
                }
                out.toByteArray()
            }
            Triple(raw, type, name)
        }
        repo.dms.sendFile(peers, bytes, mime, fileName = fileName, replyTo = replyTo)
        ok = true
    }
}

suspend fun SocialViewModel.openDmFile(info: com.example.btcwidget.nostr.core.DmFile.Info): ByteArray? =
    runCatching { repo.dms.openFile(info) }.getOrNull()

fun SocialViewModel.unlockDms() = act(tr("Couldn't open messages")) { repo.dms.unlock() }

fun SocialViewModel.setUpDmInbox() = act(tr("Couldn't set up your inbox")) { repo.dms.setUpInbox(); say(tr("Inbox ready: people can message you")) }
