package com.srikanth.glass.livetranslation.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.srikanth.glass.livetranslation.R
import com.srikanth.glass.livetranslation.audio.AudioEngine
import kotlinx.coroutines.*

class ForegroundSvc : Service() {
    private lateinit var audioEngine: AudioEngine
    private lateinit var pipelineBus: PipelineBus
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isPaused = false

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "glass_live_translation"
    }

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Starting..."))

        pipelineBus = PipelineBus(this, serviceScope)
        audioEngine = AudioEngine(this, pipelineBus)

        // Start audio capture
        audioEngine.start()

        updateNotification("Live Translation Active")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "PAUSE" -> {
                isPaused = true
                audioEngine.pause()
                updateNotification("Paused")
            }
            "RESUME" -> {
                isPaused = false
                audioEngine.resume()
                updateNotification("Live Translation Active")
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        audioEngine.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Live Translation",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Real-time speech translation service"
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Glass Live Translation")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_mic) // You'll need to add this icon
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }
}