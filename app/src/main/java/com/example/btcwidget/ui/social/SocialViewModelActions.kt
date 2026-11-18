package com.example.btcwidget.ui.social

import com.example.btcwidget.nostr.core.Event
import com.example.btcwidget.nostr.core.Profile
import com.example.btcwidget.ui.social.SocialViewModel.Attachment
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

// Reacting, reposting, following, muting, bookmarking, voting and uploads.
// Extensions on SocialViewModel, kept apart from the class: they use only what it shares.

/** Saves the new account's profile, showing its progress; [onDone] gets whether it was saved. */
fun SocialViewModel.saveProfile(name: String, about: String, picture: String?, onDone: (Boolean) -> Unit) {
    val me = repo.pubkey ?: return onDone(false)
    var ok = false
    tracked(tr("Saving your profile"), tr("Profile saved"), tr("Couldn't save your profile"), finally = { onDone(ok) }) {
        val current = repo.profiles.value[me] ?: Profile(me)
        repo.updateProfile(current.copy(name = name.trim().ifEmpty { current.name }, about = about.trim().ifEmpty { current.about }, picture = picture ?: current.picture))
        ok = true
    }
}

val SocialViewModel.mutes get() = repo.mutes

fun SocialViewModel.muteEntry(type: String, value: String, mute: Boolean) =
    act(tr("Couldn't update mutes")) { repo.setMuteEntry(type, value, mute); if (mute) say(tr("Muted")) }

fun SocialViewModel.unmuteAllThreads() = act(tr("Couldn't update mutes")) { repo.unmuteAllThreads() }

/** Mutes the conversation [note] belongs to (its root note). */
fun SocialViewModel.muteThread(note: Event) = muteEntry("e", com.example.btcwidget.nostr.core.ThreadRef.of(note).root ?: note.id, true)

val SocialViewModel.trustSize get() = repo.trustSize

val SocialViewModel.savedAccounts get() = repo.savedAccounts

fun SocialViewModel.removeSavedAccount(pubkey: String) = repo.removeSavedAccount(pubkey)

/** Uploads a picked video as-is (up to 100 MB). */
fun SocialViewModel.uploadVideo(uri: android.net.Uri, onResult: (Attachment?) -> Unit) {
    var result: Attachment? = null
    act(tr("Couldn't upload the video"), finally = { onResult(result) }) {
        val prepared = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.example.btcwidget.nostr.VideoPrep.prepare(getApplication(), uri)
        }
        val blob = repo.uploadVideo(prepared)
        result = Attachment(blob.url, prepared.mime, blob.sha256, prepared.width, prepared.height)
    }
}

/** Prepares (strips metadata) and uploads a picked image; [onResult] gets it, or null on failure. */
fun SocialViewModel.uploadImage(uri: android.net.Uri, onResult: (Attachment?) -> Unit) {
    var result: Attachment? = null
    act(tr("Couldn't upload the image"), finally = { onResult(result) }) {
        val prepared = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            com.example.btcwidget.nostr.ImagePrep.prepare(getApplication(), uri)
        }
        val blob = repo.uploadImage(prepared)
        result = Attachment(blob.url, prepared.mime, blob.sha256, prepared.width, prepared.height, prepared.blurhash)
    }
}

fun SocialViewModel.like(note: Event) = act(tr("Couldn't like")) { repo.like(note) }

fun SocialViewModel.react(note: Event, content: String, emojiUrl: String?) = act(tr("Couldn't react")) { repo.react(note, content, emojiUrl) }

val SocialViewModel.emojis get() = repo.emojis

fun SocialViewModel.repost(note: Event) = tracked(tr("Reposting"), tr("Reposted"), tr("Couldn't repost")) { repo.repost(note) }

fun SocialViewModel.follow(pubkey: String, follow: Boolean) = act(if (follow) tr("Couldn't follow") else tr("Couldn't unfollow")) { repo.setFollowing(pubkey, follow) }

fun SocialViewModel.report(note: Event, reason: String) = act(tr("Couldn't send the report")) { repo.report(note, reason); say(tr("Reported")) }

fun SocialViewModel.updateProfile(profile: Profile, onDone: () -> Unit) = act(tr("Couldn't save your profile")) { repo.updateProfile(profile); onDone() }

val SocialViewModel.bookmarks get() = repo.bookmarks

fun SocialViewModel.toggleBookmark(note: Event) {
    val on = note.id !in repo.bookmarks.value
    act(if (on) tr("Couldn't bookmark") else tr("Couldn't remove the bookmark")) {
        repo.setBookmarked(note, on)
        say(if (on) tr("Bookmarked") else tr("Bookmark removed"))
    }
}

val SocialViewModel.pinned get() = repo.pinned

fun SocialViewModel.vote(poll: com.example.btcwidget.nostr.core.Poll, optionIds: List<String>, onResult: (Boolean) -> Unit) {
    var ok = false
    act(tr("Couldn't vote"), finally = { onResult(ok) }) { repo.polls.vote(poll, optionIds); ok = true }
}

fun SocialViewModel.togglePin(note: Event) {
    val on = note.id !in repo.pinned.value
    act(if (on) tr("Couldn't pin") else tr("Couldn't unpin")) { repo.setPinned(note, on); say(if (on) tr("Pinned to your profile") else tr("Unpinned")) }
}
