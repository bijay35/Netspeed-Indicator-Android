package com.vzk.netspeed.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vzk.netspeed.data.PrefsManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = PrefsManager(context)
            if (prefs.serviceEnabled) {
                context.startForegroundService(
                    Intent(context, NetSpeedService::class.java)
                )
            }
        }
    }
}
