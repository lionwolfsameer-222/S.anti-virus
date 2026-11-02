package com.smartguard.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (c.getSharedPreferences("sg", 0).getBoolean("auto", true)) {
            try { c.startForegroundService(Intent(c, GuardService::class.java)) } catch (e: Exception) {}
        }
    }
}
