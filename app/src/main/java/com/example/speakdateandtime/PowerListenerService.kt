package com.example.speakdateandtime

import android.app.*
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import java.util.*

class PowerListenerService : Service(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var audioManager: AudioManager? = null
    private var originalVolume = 0

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "power_tts_channel"
        private var lastSpeakTime = 0L
        private const val DEBOUNCE_MS = 1000L
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        tts = TextToSpeech(this, this)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "电源键播报服务", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        
        startForeground(NOTIFICATION_ID, NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("电源键播报")
            .setContentText("服务运行中，按电源键将播报时间")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build())
    }

    override fun onInit(status: Int) {
        isTtsReady = status == TextToSpeech.SUCCESS
        if (isTtsReady) {
            tts?.language = Locale.CHINESE
            tts?.setSpeechRate(0.5f)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val currentStatus = intent?.getStringExtra("action")
        if (currentStatus == "SCREEN_ON") {
            val now = System.currentTimeMillis()
            if (now - lastSpeakTime >= DEBOUNCE_MS) {
                lastSpeakTime = now
                speakCurrentTime()
            }
        }
        return START_REDELIVER_INTENT
    }

    private fun speakCurrentTime() {
        if (!isTtsReady) {
            tts?.language = Locale.CHINESE
            tts?.setSpeechRate(0.5f)
        }
        
        setMaxVolume()
        tts?.speak(getCurrentTimeText(), TextToSpeech.QUEUE_FLUSH, null, null)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
        }, 3000)
    }

    private fun setMaxVolume() {
        originalVolume = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
        audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
    }

    private fun getCurrentTimeText(): String {
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val period = when (hour) {
            in 0..4 -> "深夜"
            in 5..11 -> "上午"
            in 12..13 -> "中午"
            in 14..17 -> "下午"
            in 18..21 -> "晚上"
            else -> "深夜"
        }
        val hour12 = when (hour) {
            0 -> 12
            in 13..23 -> hour - 12
            else -> hour
        }
        return "现在是${now.get(Calendar.YEAR)}年${now.get(Calendar.MONTH) + 1}月${now.get(Calendar.DAY_OF_MONTH)}日。${period}${hour12}点${now.get(Calendar.MINUTE)}分"
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}