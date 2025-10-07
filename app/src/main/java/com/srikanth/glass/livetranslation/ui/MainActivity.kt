package com.srikanth.glass.livetranslation.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.srikanth.glass.livetranslation.R
import com.srikanth.glass.livetranslation.core.ForegroundSvc
import com.srikanth.glass.livetranslation.core.LangProfiles
import com.srikanth.glass.livetranslation.core.PipelineBus
import com.srikanth.glass.livetranslation.core.RingBuffer
import com.srikanth.glass.livetranslation.core.Settings
import java.util.ArrayDeque

class MainActivity : AppCompatActivity() {
    private lateinit var sourceCommittedView: TextView
    private lateinit var sourcePartialView: TextView
    private lateinit var targetCommittedView: TextView
    private lateinit var sourceScroll: ScrollView
    private lateinit var targetScroll: ScrollView
    private lateinit var micChip: TextView
    private lateinit var vadChip: TextView
    private lateinit var sttChip: TextView
    private lateinit var translatorChip: TextView
    private lateinit var languageChip: TextView

    private val sourceLines = ArrayDeque<String>()
    private val targetLines = ArrayDeque<String>()
    private val ringBuffer = RingBuffer(240)
    private val maxVisibleLines = 4
    private val uiHandler = Handler(Looper.getMainLooper())
    private val exitHandler = Handler(Looper.getMainLooper())

    private var currentPartial = ""
    private var serviceRunning = false
    private var awaitingExitConfirmation = false
    private var lastUiUpdate = 0L
    private val minUiIntervalMs = 33L

    private lateinit var settings: Settings

    companion object {
        private const val REQUEST_RECORD_AUDIO = 1001
        const val ACTION_STT_PARTIAL = "com.srikanth.glass.livetranslation.STT_PARTIAL"
        const val ACTION_STT_FINAL = "com.srikanth.glass.livetranslation.STT_FINAL"
        const val ACTION_TRANSLATION = "com.srikanth.glass.livetranslation.TRANSLATION"
        const val ACTION_STATUS = "com.srikanth.glass.livetranslation.STATUS"
        const val ACTION_LANGUAGE_CHANGED = "com.srikanth.glass.livetranslation.LANGUAGE"

        const val EXTRA_TEXT = "text"
        const val EXTRA_STATUS_CHANNEL = "channel"
        const val EXTRA_STATUS_STATE = "state"
        const val EXTRA_LANGUAGE_LABEL = "label"
        const val EXTRA_LANGUAGE_ID = "id"
    }

    private val resultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_STT_PARTIAL -> {
                    val partial = intent.getStringExtra(EXTRA_TEXT) ?: return
                    updateSourceText(partial, isPartial = true)
                }
                ACTION_STT_FINAL -> {
                    val final = intent.getStringExtra(EXTRA_TEXT) ?: return
                    updateSourceText(final, isPartial = false)
                }
                ACTION_TRANSLATION -> {
                    val translation = intent.getStringExtra(EXTRA_TEXT) ?: return
                    updateTargetText(translation)
                }
                ACTION_STATUS -> {
                    val channel = intent.getStringExtra(EXTRA_STATUS_CHANNEL) ?: return
                    val state = intent.getStringExtra(EXTRA_STATUS_STATE) ?: return
                    updateStatusChip(channel, state)
                }
                ACTION_LANGUAGE_CHANGED -> {
                    val label = intent.getStringExtra(EXTRA_LANGUAGE_LABEL)
                    val id = intent.getStringExtra(EXTRA_LANGUAGE_ID)
                    if (!label.isNullOrBlank() && !id.isNullOrBlank()) {
                        LangProfiles.setCurrentById(id)
                        settings.lastLanguagePair = id
                        updateLanguageChip(label)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settings = Settings(this)
        LangProfiles.setCurrentById(settings.lastLanguagePair)

        bindViews()
        setupGestureDetector()
        updateLanguageChip(LangProfiles.current().label)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO
            )
        } else {
            startTranslationService()
        }
    }

    override fun onStart() {
        super.onStart()
        registerPipelineReceiver()
    }

    override fun onStop() {
        super.onStop()
        unregisterPipelineReceiver()
    }

    override fun onDestroy() {
        super.onDestroy()
        uiHandler.removeCallbacksAndMessages(null)
        exitHandler.removeCallbacksAndMessages(null)
    }

    private fun bindViews() {
        sourceCommittedView = findViewById(R.id.sourceCommitted)
        sourcePartialView = findViewById(R.id.sourcePartial)
        targetCommittedView = findViewById(R.id.targetCommitted)
        sourceScroll = findViewById(R.id.sourceScroll)
        targetScroll = findViewById(R.id.targetScroll)
        micChip = findViewById(R.id.micChip)
        vadChip = findViewById(R.id.vadChip)
        sttChip = findViewById(R.id.sttChip)
        translatorChip = findViewById(R.id.translationChip)
        languageChip = findViewById(R.id.languageChip)

        setChipState(micChip, "MIC", PipelineBus.StatusState.IDLE)
        setChipState(vadChip, "VAD", PipelineBus.StatusState.IDLE)
        setChipState(sttChip, "STT", PipelineBus.StatusState.IDLE)
        setChipState(translatorChip, "TR", PipelineBus.StatusState.IDLE)
    }

    private fun setupGestureDetector() {
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent?, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null || e2 == null) return false
                val deltaX = e2.x - e1.x
                val deltaY = e2.y - e1.y

                if (Math.abs(deltaY) > Math.abs(deltaX) && deltaY > 100 && velocityY > 800) {
                    promptExit()
                    return true
                }

                if (Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > 80 && Math.abs(velocityX) > 600) {
                    if (deltaX > 0) {
                        cycleProfile(forward = true)
                    } else {
                        cycleProfile(forward = false)
                    }
                    return true
                }
                return false
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                toggleService()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (awaitingExitConfirmation) {
                    exitHandler.removeCallbacksAndMessages(null)
                    stopService(Intent(this@MainActivity, ForegroundSvc::class.java))
                    serviceRunning = false
                    finishAndRemoveTask()
                }
                return true
            }
        })

        findViewById<View>(android.R.id.content).setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun registerPipelineReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_STT_PARTIAL)
            addAction(ACTION_STT_FINAL)
            addAction(ACTION_TRANSLATION)
            addAction(ACTION_STATUS)
            addAction(ACTION_LANGUAGE_CHANGED)
        }
        registerReceiver(resultReceiver, filter)
    }

    private fun unregisterPipelineReceiver() {
        try {
            unregisterReceiver(resultReceiver)
        } catch (_: Exception) {
        }
    }

    private fun startTranslationService() {
        val intent = Intent(this, ForegroundSvc::class.java)
        ContextCompat.startForegroundService(this, intent)
        serviceRunning = true
        setChipState(micChip, "MIC", PipelineBus.StatusState.ACTIVE)
    }

    private fun toggleService() {
        if (!serviceRunning) {
            startTranslationService()
            return
        }
        val intent = Intent(this, ForegroundSvc::class.java).apply {
            action = if (micChip.tag == PipelineBus.StatusState.PAUSED.name) {
                ForegroundSvc.ACTION_RESUME
            } else {
                ForegroundSvc.ACTION_PAUSE
            }
        }
        startService(intent)
    }

    private fun cycleProfile(forward: Boolean) {
        val profile = if (forward) LangProfiles.next() else LangProfiles.previous()
        settings.lastLanguagePair = profile.id
        updateLanguageChip(profile.label)
        val intent = Intent(this, ForegroundSvc::class.java).apply {
            action = ForegroundSvc.ACTION_APPLY_PROFILE
            putExtra(ForegroundSvc.EXTRA_PROFILE_ID, profile.id)
        }
        startService(intent)
        Toast.makeText(this, profile.label, Toast.LENGTH_SHORT).show()
    }

    private fun promptExit() {
        if (!awaitingExitConfirmation) {
            awaitingExitConfirmation = true
            Toast.makeText(this, "Double tap to exit", Toast.LENGTH_SHORT).show()
            exitHandler.postDelayed({ awaitingExitConfirmation = false }, 2000)
        } else {
            stopService(Intent(this, ForegroundSvc::class.java))
            serviceRunning = false
            finishAndRemoveTask()
        }
    }

    private fun updateSourceText(text: String, isPartial: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (isPartial) {
            currentPartial = text
        } else if (text.isNotBlank()) {
            sourceLines.addLast(text)
            ringBuffer.addSource(text)
            if (sourceLines.size > maxVisibleLines) {
                sourceLines.removeFirst()
            }
            currentPartial = ""
        }

        val shouldThrottle = isPartial && (now - lastUiUpdate < minUiIntervalMs)
        if (shouldThrottle) {
            return
        }
        lastUiUpdate = now

        uiHandler.post {
            sourceCommittedView.text = sourceLines.joinToString("\n")
            sourcePartialView.visibility = if (currentPartial.isNotBlank()) View.VISIBLE else View.INVISIBLE
            sourcePartialView.text = currentPartial
            scrollToBottom(sourceScroll)
        }
    }

    private fun updateTargetText(text: String) {
        if (text.isBlank()) return
        targetLines.addLast(text)
        ringBuffer.addTarget(text)
        if (targetLines.size > maxVisibleLines) {
            targetLines.removeFirst()
        }

        uiHandler.post {
            targetCommittedView.text = targetLines.joinToString("\n")
            scrollToBottom(targetScroll)
        }
    }

    private fun updateStatusChip(channel: String, state: String) {
        val statusState = try {
            PipelineBus.StatusState.valueOf(state)
        } catch (_: Exception) {
            PipelineBus.StatusState.IDLE
        }
        when (channel) {
            PipelineBus.StatusChannel.MIC.name -> {
                setChipState(micChip, "MIC", statusState)
                serviceRunning = statusState != PipelineBus.StatusState.IDLE
            }
            PipelineBus.StatusChannel.VAD.name -> setChipState(vadChip, "VAD", statusState)
            PipelineBus.StatusChannel.STT.name -> setChipState(sttChip, "STT", statusState)
            PipelineBus.StatusChannel.TRANSLATOR.name -> setChipState(translatorChip, "TR", statusState)
        }
    }

    private fun setChipState(chip: TextView, label: String, state: PipelineBus.StatusState) {
        chip.tag = state.name
        chip.text = when (state) {
            PipelineBus.StatusState.ACTIVE -> "$label ON"
            PipelineBus.StatusState.PAUSED -> "$label PAUSED"
            PipelineBus.StatusState.BUSY -> "$label …"
            PipelineBus.StatusState.ERROR -> "$label ERR"
            else -> "$label IDLE"
        }
        val baseDrawable = chip.background ?: ContextCompat.getDrawable(this, R.drawable.chip_background)
        val background = DrawableCompat.wrap(baseDrawable!!.mutate())
        val colorRes = when (state) {
            PipelineBus.StatusState.ACTIVE -> R.color.chipActive
            PipelineBus.StatusState.BUSY -> R.color.chipBusy
            PipelineBus.StatusState.PAUSED -> R.color.chipPaused
            PipelineBus.StatusState.ERROR -> R.color.chipError
            else -> R.color.chipIdle
        }
        DrawableCompat.setTint(background, ContextCompat.getColor(this, colorRes))
        chip.background = background
    }

    private fun updateLanguageChip(label: String) {
        languageChip.text = label
    }

    private fun scrollToBottom(scrollView: ScrollView) {
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startTranslationService()
            } else {
                Toast.makeText(this, "Microphone permission required", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }
}
