package com.example.btcwidget

import android.app.Application
import android.content.Context
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.disk.DiskCache
import java.io.File
import okio.Path.Companion.toOkioPath

/** Installs the crash log before anything else runs, and sets up image loading. */
class BlockApp : Application(), SingletonImageLoader.Factory {
    companion object {
        fun clearImageCache(context: Context): Boolean = runCatching {
            val loader = SingletonImageLoader.get(context.applicationContext)
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
            true
        }.getOrDefault(false)
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        CrashLog.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(RelaySleeper)
        com.example.btcwidget.ui.social.Lang.load(this)
    }

    /**
     * Puts relay connections to sleep 30 s after the app leaves the screen (a quick switch to
     * another app and back keeps them), and wakes them when it returns.
     */
    private object RelaySleeper : ActivityLifecycleCallbacks {
        private val main = android.os.Handler(android.os.Looper.getMainLooper())
        private var started = 0
        private val sleep = Runnable { if (started == 0) com.example.btcwidget.nostr.RelayPool.enterBackground() }

        override fun onActivityStarted(activity: android.app.Activity) {
            started++
            main.removeCallbacks(sleep)
            if (started == 1) com.example.btcwidget.nostr.RelayPool.enterForeground()
        }

        override fun onActivityStopped(activity: android.app.Activity) {
            started = (started - 1).coerceAtLeast(0)
            if (started == 0) main.postDelayed(sleep, 30_000)
        }

        override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
        override fun onActivityResumed(activity: android.app.Activity) {}
        override fun onActivityPaused(activity: android.app.Activity) {}
        override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
        override fun onActivityDestroyed(activity: android.app.Activity) {}
    }

    /** Coil with animated GIF/WebP support (the network fetcher is added automatically). */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .diskCache {
                DiskCache.Builder()
                    .directory(File(context.cacheDir, "image_cache").toOkioPath())
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .components {
                if (Build.VERSION.SDK_INT >= 28) add(AnimatedImageDecoder.Factory()) else add(GifDecoder.Factory())
            }
            .build()
}
