package com.example.btcwidget.ui.social

import com.example.btcwidget.nostr.ScheduledPosts
import com.example.btcwidget.nostr.core.Events
import com.example.btcwidget.nostr.core.Imeta
import com.example.btcwidget.ui.social.SocialViewModel.Attachment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Posts saved to publish later, for the active account. */
class ScheduledPostsState(private val vm: SocialViewModel) {
    private val repo get() = vm.repo

    private val _posts = MutableStateFlow<List<ScheduledPosts.Post>>(emptyList())
    /** The active account's posts waiting to be published. */
    val posts: StateFlow<List<ScheduledPosts.Post>> = _posts.asStateFlow()

    fun refresh() {
        _posts.value = repo.pubkey?.let { ScheduledPosts.forAccount(vm.app, it) }.orEmpty()
    }

    /** Saves a new post to publish at [at] (seconds), with the same tags [SocialViewModel.send] would add. */
    fun schedule(
        text: String,
        images: List<Attachment>,
        contentWarning: String?,
        at: Long,
        extraTags: List<List<String>> = emptyList()
    ): Boolean {
        val me = repo.pubkey ?: return false
        val body = (listOf(text.trim()) + images.map { it.url }).filter { it.isNotEmpty() }.joinToString("\n")
        val tags = images.map { Imeta.tag(it.url, it.mime, it.sha256, it.width, it.height, it.blurhash) } +
            listOfNotNull(contentWarning?.let { listOf("content-warning", it) }) +
            extraTags
        ScheduledPosts.schedule(vm.app, me, at, body, tags)
        refresh()
        vm.say(tr("Scheduled for ") + java.text.SimpleDateFormat("EEE, MMM d - h:mm a", java.util.Locale.ENGLISH).format(java.util.Date(at * 1000)))
        return true
    }

    fun cancel(id: String) {
        ScheduledPosts.cancel(vm.app, id)
        refresh()
    }

    /** Publishes a scheduled post right away (e.g. one the signer couldn't approve in the background). */
    fun postNow(post: ScheduledPosts.Post) = vm.act(tr("Couldn't post")) {
        val event = repo.publish(Events.note(post.pubkey, post.content, post.tags))
        ScheduledPosts.cancel(vm.app, post.id)
        vm.showOnHome(event)
        refresh()
        vm.say(tr("Posted"))
    }
}
