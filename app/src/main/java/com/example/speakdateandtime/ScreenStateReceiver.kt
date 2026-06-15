package com.example.speakdateandtime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class ScreenStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, PowerListenerService::class.java)
        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> {
                serviceIntent.putExtra("action", "SCREEN_ON")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
            Intent.ACTION_SCREEN_OFF -> {
                serviceIntent.putExtra("action", "SCREEN_OFF")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}