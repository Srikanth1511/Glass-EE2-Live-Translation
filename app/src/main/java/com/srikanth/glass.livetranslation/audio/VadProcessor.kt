package com.srikanth.glass.livetranslation.audio

import android.util.Log
import com.srikanth.glass.livetranslation.core.PipelineBus
import com.srikanth.glass.livetranslation.stt.SttEngine

/**
 * Simple energy-based Voice Activity Detection
 * Replaces WebRTC VAD for initial implementation simplicity
 */
class VadProcessor(
    private val bus: PipelineBus,
    private val sttEngine: SttEngine
) {
    // Energy threshold for speech detection
    private var energyThreshold = 1000.0
    private val adaptationRate = 0.01
    private var runningEnergy = 0.0

    // Silence detection for sentence boundaries
    private val maxSilenceMs = 700
    private val frameMs = 20
    private var silenceFrameCount = 0
    private var speechFrameCount = 0

    private var isSpeaking = false
    private var hasPendingUtterance = false

    fun processFrame(samples: ShortArray, length: Int) {
        // Calculate frame energy (RMS)
        val energy = calculateEnergy(samples, length)

        // Adapt threshold using exponential moving average
        runningEnergy = runningEnergy * (1 - adaptationRate) + energy * adaptationRate

        // Simple hysteresis: threshold is 2x running average
        val threshold = runningEnergy * 2.0

        val isSpeech = energy > maxOf(threshold, energyThreshold)

        if (isSpeech) {
            silenceFrameCount = 0
            speechFrameCount++

            if (!isSpeaking && speechFrameCount > 3) {
                // Start of speech detected
                isSpeaking = true
                Log.d("VAD", "Speech START")
            }

            // Send frame to STT
            val partial = sttEngine.acceptPcm(samples, length)
            partial?.let {
                hasPendingUtterance = true
                bus.onSttPartial(it)
            }

        } else {
            speechFrameCount = 0
            silenceFrameCount++

            val silenceMs = silenceFrameCount * frameMs

            if (isSpeaking && silenceMs >= maxSilenceMs) {
                // End of speech detected
                isSpeaking = false
                Log.d("VAD", "Speech END (${silenceMs}ms silence)")

                // Flush STT for final result
                if (hasPendingUtterance) {
                    val final = sttEngine.flush()
                    final?.let { bus.onSttFinal(it) }
                    hasPendingUtterance = false
                }

                silenceFrameCount = 0
            }
        }
    }

    private fun calculateEnergy(samples: ShortArray, length: Int): Double {
        var sum = 0.0
        for (i in 0 until length) {
            val sample = samples[i].toDouble()
            sum += sample * sample
        }
        return Math.sqrt(sum / length)
    }

    /**
     * Alternative: WebRTC VAD integration (if library available)
     * Uncomment and use if you integrate actual WebRTC VAD
     */
    /*
    private val webrtcVad = WebRtcVad().apply {
        setSampleRate(WebRtcVad.SampleRate.SAMPLE_RATE_16KHZ)
        setMode(WebRtcVad.Mode.VERY_AGGRESSIVE)
    }

    fun processFrameWebRTC(samples: ShortArray, length: Int) {
        val isSpeech = webrtcVad.isSpeech(samples)
        // ... rest of logic
    }
    */
}