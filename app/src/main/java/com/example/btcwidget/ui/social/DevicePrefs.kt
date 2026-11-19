package com.example.btcwidget.ui.social

import android.app.Application
import com.example.btcwidget.AppPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Settings kept on this phone (in [AppPrefs]), not in your Nostr account: video and links. */
class DevicePrefs(private val app: Application) {

    private val _videoAutoplay = MutableStateFlow(VideoAutoplay.fromName(AppPrefs.videoAutoplay(app)))
    val videoAutoplay: StateFlow<VideoAutoplay> = _videoAutoplay.asStateFlow()

    private val _videoQuality = MutableStateFlow(VideoQuality.fromName(AppPrefs.videoQuality(app)))
    val videoQuality: StateFlow<VideoQuality> = _videoQuality.asStateFlow()

    fun setVideoQuality(q: VideoQuality) {
        AppPrefs.setVideoQuality(app, q.name)
        _videoQuality.value = q
    }

    private val _linksInApp = MutableStateFlow(AppPrefs.linksInApp(app))
    /** Web links open in Block's browser (true) or the phone's. */
    val linksInApp: StateFlow<Boolean> = _linksInApp.asStateFlow()

    fun setLinksInApp(inApp: Boolean) {
        AppPrefs.setLinksInApp(app, inApp)
        _linksInApp.value = inApp
    }

    fun setVideoAutoplay(mode: VideoAutoplay) {
        AppPrefs.setVideoAutoplay(app, mode.name)
        _videoAutoplay.value = mode
    }
}
