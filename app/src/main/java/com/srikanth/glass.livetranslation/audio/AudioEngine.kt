package com.srikanth.glass.livetranslation.audio

import android.content.Context
import android.media.*
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.srikanth.glass.livetranslation.core.LangProfiles
import com.srikanth.glass.livetranslation.core.ModelManager
import com.srikanth.glass.livetranslation.core.PipelineBus
import com.srikanth.glass.livetranslation.core.Settings
import com.srikanth.glass.livetranslation.stt.SttEngine
import com.srikanth.glass.livetranslation.stt.VoskStt
import java.io.File

class AudioEngine(
    private val context: Context,
    private val bus: PipelineBus,
    private val settings: Settings
) {
    private val sampleRate = 16_000
    private val frameMs = 20
    private val samplesPerFrame = sampleRate * frameMs / 1000
    private val bufferSize = samplesPerFrame * 4

    private var audioRecord: AudioRecord? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var sttEngine: SttEngine? = null
    private var vadProcessor: VadProcessor? = null
    private val readBuffer = ShortArray(samplesPerFrame)
    private val modelManager = ModelManager(context)

    private var isRunning = false
    private var isPaused = false

    fun start(profile: LangProfiles.LanguageProfile) {
        if (isRunning) {
            restartWithProfile(profile)
            return
        }

        val stt = VoskStt()
        val modelPath = prepareModel(profile)
        if (modelPath == null) {
            Log.e("AudioEngine", "Model not found for ${profile.id}")
            bus.updateStatus(PipelineBus.StatusChannel.STT, PipelineBus.StatusState.ERROR)
        } else {
            stt.start(modelPath.absolutePath)
        }

        sttEngine = stt
        vadProcessor = VadProcessor(bus, stt, settings.maxSilenceMs)

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

        setupAudioEffects()

        handlerThread = HandlerThread("AudioCapture").apply { start() }
        handler = Handler(handlerThread!!.looper)

        audioRecord?.startRecording()
        isRunning = true
        isPaused = false
        bus.updateStatus(PipelineBus.StatusChannel.MIC, PipelineBus.StatusState.ACTIVE)
        bus.updateStatus(PipelineBus.StatusChannel.VAD, PipelineBus.StatusState.IDLE)

        handler?.post(captureRunnable)
    }

    fun restartWithProfile(profile: LangProfiles.LanguageProfile) {
        stop()
        start(profile)
    }

    private val captureRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            val recorder = audioRecord ?: return
            val vad = vadProcessor ?: return

            if (!isPaused) {
                val read = recorder.read(readBuffer, 0, samplesPerFrame)
                if (read > 0) {
                    vad.processFrame(readBuffer, read)
                } else if (read < 0) {
                    Log.e("AudioEngine", "Audio read error: $read")
                }
            }

            handler?.postDelayed(this, frameMs.toLong())
        }
    }

    private fun setupAudioEffects() {
        try {
            val sessionId = audioRecord?.audioSessionId ?: return
            if (NoiseSuppressor.isAvailable()) {
                NoiseSuppressor.create(sessionId)?.enabled = true
            }
            if (AutomaticGainControl.isAvailable()) {
                AutomaticGainControl.create(sessionId)?.enabled = true
            }
            if (AcousticEchoCanceler.isAvailable()) {
                AcousticEchoCanceler.create(sessionId)?.enabled = false
            }
        } catch (e: Exception) {
            Log.e("AudioEngine", "Error setting up audio effects", e)
        }
    }

    fun pause() {
        if (!isRunning) return
        isPaused = true
        bus.updateStatus(PipelineBus.StatusChannel.MIC, PipelineBus.StatusState.PAUSED)
    }

    fun resume() {
        if (!isRunning) return
        isPaused = false
        bus.updateStatus(PipelineBus.StatusChannel.MIC, PipelineBus.StatusState.ACTIVE)
    }

    fun stop() {
        isRunning = false
        isPaused = false

        handler?.removeCallbacks(captureRunnable)
        handlerThread?.quitSafely()
        handlerThread = null
        handler = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e("AudioEngine", "Error stopping audio", e)
        }
        audioRecord = null

        sttEngine?.stop()
        sttEngine = null
        vadProcessor = null

        bus.updateStatus(PipelineBus.StatusChannel.MIC, PipelineBus.StatusState.IDLE)
        bus.updateStatus(PipelineBus.StatusChannel.VAD, PipelineBus.StatusState.IDLE)
    }

    private fun prepareModel(profile: LangProfiles.LanguageProfile): File? {
        val modelDir = modelManager.getModelDir(profile.sttModel)
        if (modelDir.exists()) {
            return modelDir
        }

        val assetsPath = "models/${profile.sttModel}"
        return try {
            if (modelManager.assetExists(assetsPath)) {
                modelManager.installFromAssets(assetsPath, profile.sttModel)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("AudioEngine", "Failed to copy model", e)
            null
        }
    }
}
