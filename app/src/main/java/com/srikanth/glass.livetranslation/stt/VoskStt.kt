package com.srikanth.glass.livetranslation.stt

import android.util.Log
import com.srikanth.glass.livetranslation.core.PipelineBus
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * On-device STT using Vosk
 * Lightweight, offline, streaming recognition
 */
class VoskStt(private val bus: PipelineBus) : SttEngine {
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private val sampleRate = 16000f

    override fun start(modelPath: String) {
        try {
            Log.d("VoskStt", "Loading model from: $modelPath")
            model = Model(modelPath)
            recognizer = Recognizer(model, sampleRate)

            // Configure recognizer for better partial results
            recognizer?.setMaxAlternatives(0)  // Disable alternatives for speed
            recognizer?.setWords(false)  // Disable word-level timestamps for speed

            Log.d("VoskStt", "Vosk initialized successfully")
        } catch (e: Exception) {
            Log.e("VoskStt", "Failed to initialize Vosk", e)
            throw e
        }
    }

    override fun acceptPcm(frame: ShortArray, length: Int): SttPartial? {
        val rec = recognizer ?: return null

        try {
            // Convert short[] to byte[] (little-endian PCM16)
            val byteBuffer = ByteBuffer.allocate(length * 2)
                .order(ByteOrder.LITTLE_ENDIAN)
            byteBuffer.asShortBuffer().put(frame, 0, length)
            val pcmBytes = byteBuffer.array()

            // Feed to recognizer
            val isFinal = rec.acceptWaveForm(pcmBytes, pcmBytes.size)

            if (isFinal) {
                // Vosk detected utterance boundary
                val result = rec.result()
                val text = extractText(result)

                if (text.isNotBlank()) {
                    Log.d("VoskStt", "Final: $text")
                    // Don't return partial here - will be handled by flush()
                    bus.onSttFinal(SttFinal(text))
                }
                return null
            } else {
                // Get partial result
                val partial = rec.partialResult()
                val text = extractPartialText(partial)

                return if (text.isNotBlank()) {
                    SttPartial(text)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("VoskStt", "Error processing frame", e)
            return null
        }
    }

    override fun flush(): SttFinal? {
        val rec = recognizer ?: return null

        try {
            val finalResult = rec.finalResult()
            val text = extractText(finalResult)

            return if (text.isNotBlank()) {
                Log.d("VoskStt", "Flush final: $text")
                SttFinal(text)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("VoskStt", "Error flushing", e)
            return null
        }
    }

    override fun stop() {
        try {
            recognizer?.close()
            model?.close()
            recognizer = null
            model = null
            Log.d("VoskStt", "Vosk stopped")
        } catch (e: Exception) {
            Log.e("VoskStt", "Error stopping Vosk", e)
        }
    }

    private fun extractText(jsonResult: String): String {
        return try {
            val json = JSONObject(jsonResult)
            json.optString("text", "").trim()
        } catch (e: Exception) {
            Log.e("VoskStt", "Error parsing result", e)
            ""
        }
    }

    private fun extractPartialText(jsonPartial: String): String {
        return try {
            val json = JSONObject(jsonPartial)
            json.optString("partial", "").trim()
        } catch (e: Exception) {
            Log.e("VoskStt", "Error parsing partial", e)
            ""
        }
    }
}