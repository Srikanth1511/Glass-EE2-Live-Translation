package com.srikanth.glass.livetranslation.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.srikanth.glass.livetranslation.R
import com.srikanth.glass.livetranslation.core.ForegroundSvc

class MainActivity : AppCompatActivity() {
    private lateinit var sourceView: TextView
    private lateinit var targetView: TextView
    private lateinit var statusView: TextView
    private var serviceRunning = false

    companion object {
        private const val REQUEST_RECORD_AUDIO = 1001
        const val ACTION_STT_PARTIAL = "com.srikanth.glass.livetranslation.STT_PARTIAL"
        const val ACTION_STT_FINAL = "com.srikanth.glass.livetranslation.STT_FINAL"
        const val ACTION_TRANSLATION = "com.srikanth.glass.livetranslation.TRANSLATION"
        const val EXTRA_TEXT = "text"
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
            }
        }
    }

    private val sourceLines = mutableListOf<String>()
    private val targetLines = mutableListOf<String>()
    private var currentPartial = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sourceView = findViewById(R.id.sourceView)
        targetView = findViewById(R.id.targetView)
        statusView = findViewById(R.id.status)

        // Request microphone permission
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

        // Setup gesture detector for swipe down to exit
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (velocityY > 1500) {
                    // Swipe down detected
                    stopService(Intent(this@MainActivity, ForegroundSvc::class.java))
                    finishAndRemoveTask()
                    return true
                }
                return false
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                // Tap to pause/resume
                toggleService()
                return true
            }
        })

        findViewById<View>(android.R.id.content).setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        // Register broadcast receiver
        val filter = IntentFilter().apply {
            addAction(ACTION_STT_PARTIAL)
            addAction(ACTION_STT_FINAL)
            addAction(ACTION_TRANSLATION)
        }
        registerReceiver(resultReceiver, filter, RECEIVER_NOT_EXPORTED)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
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

    private fun startTranslationService() {
        val intent = Intent(this, ForegroundSvc::class.java)
        ContextCompat.startForegroundService(this, intent)
        serviceRunning = true
        updateStatus("ACTIVE")
    }

    private fun toggleService() {
        val intent = Intent(this, ForegroundSvc::class.java).apply {
            action = if (serviceRunning) "PAUSE" else "RESUME"
        }
        startService(intent)
        serviceRunning = !serviceRunning
        updateStatus(if (serviceRunning) "ACTIVE" else "PAUSED")
    }

    private fun updateSourceText(text: String, isPartial: Boolean) {
        runOnUiThread {
            if (isPartial) {
                currentPartial = text
            } else {
                if (text.isNotBlank()) {
                    sourceLines.add(text)
                    // Keep only last 5 lines
                    if (sourceLines.size > 5) {
                        sourceLines.removeAt(0)
                    }
                }
                currentPartial = ""
            }

            val displayText = sourceLines.joinToString("\n") +
                    if (currentPartial.isNotBlank()) "\n$currentPartial" else ""
            sourceView.text = displayText
        }
    }

    private fun updateTargetText(text: String) {
        runOnUiThread {
            if (text.isNotBlank()) {
                targetLines.add(text)
                // Keep only last 5 lines
                if (targetLines.size > 5) {
                    targetLines.removeAt(0)
                }
            }
            targetView.text = targetLines.joinToString("\n")
        }
    }

    private fun updateStatus(status: String) {
        runOnUiThread {
            statusView.text = "🎤 $status"
        }
    }

    private fun checkAndDownloadModels() {
        val modelDir = File(filesDir, "models/vosk-model-small-en-us-0.15")
        if (!modelDir.exists()) {
            // Show download dialog
            // Download from https://alphacephei.com/vosk/models
            // Extract to app files directory
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(resultReceiver)
        } catch (e: Exception) {
            // Already unregistered
        }
    }
}