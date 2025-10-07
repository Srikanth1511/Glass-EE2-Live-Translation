package com.srikanth.glass.livetranslation.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.srikanth.glass.livetranslation.R
import com.srikanth.glass.livetranslation.audio.AudioEngine
import com.srikanth.glass.livetranslation.translate.ApertiumTranslator
import com.srikanth.glass.livetranslation.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class ForegroundSvc : Service() {
    private lateinit var audioEngine: AudioEngine
    private lateinit var pipelineBus: PipelineBus
    private lateinit var settings: Settings
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "glass_live_translation"

        const val ACTION_PAUSE = "com.srikanth.glass.livetranslation.action.PAUSE"
        const val ACTION_RESUME = "com.srikanth.glass.livetranslation.action.RESUME"
        const val ACTION_APPLY_PROFILE = "com.srikanth.glass.livetranslation.action.APPLY_PROFILE"
        const val EXTRA_PROFILE_ID = "profile_id"
    }

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Starting"))

        acquireWakeLock()

        pipelineBus = PipelineBus(this, serviceScope)
        audioEngine = AudioEngine(this, pipelineBus, settings)

        val profile = LangProfiles.setCurrentById(settings.lastLanguagePair)
        pipelineBus.setLanguageProfile(profile, ApertiumTranslator())
        audioEngine.start(profile)

        updateNotification("Live translation • ${profile.label}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                audioEngine.pause()
                pipelineBus.updateStatus(PipelineBus.StatusChannel.MIC, PipelineBus.StatusState.PAUSED)
                updateNotification("Paused • ${LangProfiles.current().label}")
            }
            ACTION_RESUME -> {
                audioEngine.resume()
                pipelineBus.updateStatus(PipelineBus.StatusChannel.MIC, PipelineBus.StatusState.ACTIVE)
                updateNotification("Listening • ${LangProfiles.current().label}")
            }
            ACTION_APPLY_PROFILE -> {
                val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
                val profile = if (profileId.isNullOrBlank()) {
                    LangProfiles.current()
                } else {
                    LangProfiles.setCurrentById(profileId)
                }
                settings.lastLanguagePair = profile.id
                pipelineBus.setLanguageProfile(profile, ApertiumTranslator())
                audioEngine.restartWithProfile(profile)
                updateNotification("Live translation • ${profile.label}")
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        audioEngine.stop()
        pipelineBus.shutdown()
        serviceScope.cancel()
        releaseWakeLock()
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GlassLiveTranslation::WakeLock").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
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
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentFlags()
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Glass Live Translation")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = createNotification(text)
        val manager = ContextCompat.getSystemService(this, NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun pendingIntentFlags(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
    }
}
