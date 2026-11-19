package com.example.btcwidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * After an update: redraws every widget, so placed widgets pick up new layouts at once. After an
 * update or a restart of the phone: brings instant notifications back, if they're on.
 */
class AppUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                WidgetUpdater.updateAllWidgets(context)
                WidgetRefreshScheduler.schedulePeriodic(context)
                com.example.btcwidget.nostr.InstantPush.sync(context)
            }
            Intent.ACTION_BOOT_COMPLETED -> com.example.btcwidget.nostr.InstantPush.sync(context)
        }
    }
}
