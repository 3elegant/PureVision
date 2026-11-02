package com.deenelife.purevison

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.deenelife.purevison.data.AppPreferences

class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val preferences = AppPreferences.getInstance(context)
        if (preferences.isServiceEnabled()) {
            val serviceIntent = Intent(context, OverlayService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
