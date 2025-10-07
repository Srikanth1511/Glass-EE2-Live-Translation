package com.srikanth.glass.livetranslation.audio

import com.srikanth.glass.livetranslation.core.PipelineBus
import com.srikanth.glass.livetranslation.stt.SttEngine
import com.srikanth.glass.livetranslation.stt.SttFinal
import com.srikanth.glass.livetranslation.stt.SttPartial

/**
 * Lightweight VAD/segmenter used until the WebRTC VAD JNI binding is wired.
 * It tracks running energy and marks an utterance boundary after the configured
 * amount of trailing silence. Frames are 20 ms at 16 kHz.
 */
class VadProcessor(
    private val bus: PipelineBus,
    private val sttEngine: SttEngine,
    private val maxSilenceMs: Int,
    private val frameMs: Int = 20
) {
    private var energyThreshold = 1_000.0
    private val adaptationRate = 0.01
    private var runningEnergy = 0.0

    private val silenceFrameLimit = maxSilenceMs / frameMs
    private var silenceFrameCount = 0
    private var speechFrameCount = 0

    private var isSpeaking = false
    private var hasPendingUtterance = false

    fun processFrame(samples: ShortArray, length: Int) {
        val energy = calculateEnergy(samples, length)
        runningEnergy = runningEnergy * (1 - adaptationRate) + energy * adaptationRate
        val dynamicThreshold = runningEnergy * 2.0
        val isSpeech = energy > maxOf(dynamicThreshold, energyThreshold)

        if (isSpeech) {
            if (!isSpeaking) {
                isSpeaking = true
                bus.onVadStateChanged(true)
            }

            speechFrameCount++
            silenceFrameCount = 0

            val update = sttEngine.acceptPcm(samples, length)
            when {
                update == null -> return
                update.isFinal -> {
                    hasPendingUtterance = false
                    bus.onSttFinal(SttFinal(update.text, update.confidence))
                    isSpeaking = false
                    bus.onVadStateChanged(false)
                }
                else -> {
                    hasPendingUtterance = true
                    bus.onSttPartial(SttPartial(update.text, update.confidence))
                }
            }
        } else {
            if (isSpeaking) {
                silenceFrameCount++
                if (silenceFrameCount >= silenceFrameLimit) {
                    isSpeaking = false
                    bus.onVadStateChanged(false)
                    silenceFrameCount = 0

                    if (hasPendingUtterance) {
                        val final = sttEngine.flush()
                        if (final != null && final.text.isNotBlank()) {
                            bus.onSttFinal(final)
                        }
                        hasPendingUtterance = false
                    }
                }
            } else {
                speechFrameCount = 0
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
}
