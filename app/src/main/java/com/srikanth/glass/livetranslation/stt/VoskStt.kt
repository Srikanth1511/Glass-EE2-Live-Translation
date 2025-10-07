package com.srikanth.glass.livetranslation.stt

import android.util.Log
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * On-device STT using Vosk
 * Lightweight, offline, streaming recognition
 */
class VoskStt : SttEngine {
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private val sampleRate = 16_000f

    override fun start(modelPath: String) {
        try {
            Log.d("VoskStt", "Loading model from: $modelPath")
            model = Model(modelPath)
            recognizer = Recognizer(model, sampleRate).apply {
                setMaxAlternatives(0)
                setWords(false)
            }
            Log.d("VoskStt", "Vosk initialized successfully")
        } catch (e: Exception) {
            Log.e("VoskStt", "Failed to initialize Vosk", e)
            throw e
        }
    }

    override fun acceptPcm(frame: ShortArray, length: Int): SttPartial? {
        val rec = recognizer ?: return null
        return try {
            val pcmBytes = frameToBytes(frame, length)
            val isFinal = rec.acceptWaveForm(pcmBytes, pcmBytes.size)
            if (isFinal) {
                val result = rec.result()
                val text = extractText(result)
                if (text.isNotBlank()) {
                    SttPartial(text, confidence = 1.0f, isFinal = true)
                } else {
                    null
                }
            } else {
                val partial = rec.partialResult()
                val text = extractPartialText(partial)
                if (text.isNotBlank()) {
                    SttPartial(text, confidence = 0.6f)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("VoskStt", "Error processing frame", e)
            null
        }
    }

    override fun flush(): SttFinal? {
        val rec = recognizer ?: return null
        return try {
            val finalResult = rec.finalResult()
            val text = extractText(finalResult)
            if (text.isNotBlank()) {
                SttFinal(text)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("VoskStt", "Error flushing", e)
            null
        }
    }

    override fun stop() {
        try {
            recognizer?.close()
            model?.close()
        } catch (e: Exception) {
            Log.e("VoskStt", "Error stopping Vosk", e)
        } finally {
            recognizer = null
            model = null
        }
    }

    private fun frameToBytes(frame: ShortArray, length: Int): ByteArray {
        val byteBuffer = ByteBuffer.allocate(length * 2).order(ByteOrder.LITTLE_ENDIAN)
        byteBuffer.asShortBuffer().put(frame, 0, length)
        return byteBuffer.array()
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
