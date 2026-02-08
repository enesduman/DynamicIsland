package com.dynamicisland.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dynamicisland.service.OverlayService
import com.dynamicisland.util.PrefsManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && PrefsManager.isEnabled(context)) {
            OverlayService.start(context)
        }
    }
}
