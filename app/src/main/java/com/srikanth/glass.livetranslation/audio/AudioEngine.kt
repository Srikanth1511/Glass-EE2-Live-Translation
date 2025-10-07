package com.srikanth.glass.livetranslation.audio

import android.content.Context
import android.media.*
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.srikanth.glass.livetranslation.core.PipelineBus
import com.srikanth.glass.livetranslation.stt.SttEngine
import com.srikanth.glass.livetranslation.stt.VoskStt
import java.io.File

class AudioEngine(
    private val context: Context,
    private val bus: PipelineBus
) {
    private val sampleRate = 16000
    private val frameMs = 20
    private val samplesPerFrame = sampleRate * frameMs / 1000  // 320 samples
    private val bufferSize = samplesPerFrame * 4  // 4 frames buffer

    private lateinit var audioRecord: AudioRecord
    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler
    private lateinit var sttEngine: SttEngine
    private lateinit var vadProcessor: VadProcessor

    private var isRunning = false
    private var isPaused = false
    private val readBuffer = ShortArray(samplesPerFrame)

    fun start() {
        Log.d("AudioEngine", "Starting audio engine")

        // Initialize STT engine
        sttEngine = VoskStt(bus)
        val modelPath = getModelPath()
        if (modelPath.exists()) {
            sttEngine.start(modelPath.absolutePath)
        } else {
            Log.e("AudioEngine", "Model not found at: ${modelPath.absolutePath}")
            // TODO: Trigger model download
        }

        // Initialize VAD
        vadProcessor = VadProcessor(bus, sttEngine)

        // Create audio record
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val actualBufferSize = maxOf(minBufferSize, bufferSize * 2)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            actualBufferSize
        )

        // Enable audio effects
        setupAudioEffects()

        // Start recording on separate thread
        handlerThread = HandlerThread("AudioCapture").apply { start() }
        handler = Handler(handlerThread.looper)

        audioRecord.startRecording()
        isRunning = true

        handler.post(captureRunnable)
    }

    private val captureRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return

            if (!isPaused) {
                val read = audioRecord.read(readBuffer, 0, samplesPerFrame)

                if (read > 0) {
                    // Send to VAD and STT pipeline
                    vadProcessor.processFrame(readBuffer, read)
                } else if (read < 0) {
                    Log.e("AudioEngine", "Audio read error: $read")
                }
            }

            // Schedule next read (every 20ms)
            handler.postDelayed(this, frameMs.toLong())
        }
    }

    private fun setupAudioEffects() {
        try {
            val sessionId = audioRecord.audioSessionId

            // Noise suppression
            if (NoiseSuppressor.isAvailable()) {
                val ns = NoiseSuppressor.create(sessionId)
                ns?.enabled = true
                Log.d("AudioEngine", "Noise suppressor enabled")
            }

            // Automatic gain control
            if (AutomaticGainControl.isAvailable()) {
                val agc = AutomaticGainControl.create(sessionId)
                agc?.enabled = true
                Log.d("AudioEngine", "AGC enabled")
            }

            // Acoustic echo canceler (usually not needed for Glass)
            if (AcousticEchoCanceler.isAvailable()) {
                val aec = AcousticEchoCanceler.create(sessionId)
                aec?.enabled = false  // No speaker output on Glass
            }
        } catch (e: Exception) {
            Log.e("AudioEngine", "Error setting up audio effects", e)
        }
    }

    fun pause() {
        isPaused = true
        Log.d("AudioEngine", "Audio paused")
    }

    fun resume() {
        isPaused = false
        Log.d("AudioEngine", "Audio resumed")
    }

    fun stop() {
        Log.d("AudioEngine", "Stopping audio engine")
        isRunning = false
        isPaused = false

        handler.removeCallbacks(captureRunnable)
        handlerThread.quitSafely()

        try {
            audioRecord.stop()
            audioRecord.release()
        } catch (e: Exception) {
            Log.e("AudioEngine", "Error stopping audio", e)
        }

        sttEngine.stop()
    }

    private fun getModelPath(): File {
        // Check for downloaded model
        val modelsDir = File(context.filesDir, "models")
        val modelFile = File(modelsDir, "vosk-model-small-en-us-0.15")

        if (modelFile.exists()) {
            return modelFile
        }

        // Fall back to assets (if bundled)
        val assetsModel = File(context.filesDir, "vosk-model-en-us")
        return assetsModel
    }
}