package com.srikanth.glass.livetranslation.core

import android.content.Context
import android.content.Intent
import com.srikanth.glass.livetranslation.stt.SttFinal
import com.srikanth.glass.livetranslation.stt.SttPartial
import com.srikanth.glass.livetranslation.translate.Translator
import com.srikanth.glass.livetranslation.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Central event bus for audio pipeline
 * Coordinates: PCM frames → VAD → STT → Translation → UI
 */
class PipelineBus(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val pcmChannel = Channel<PcmFrame>(Channel.BUFFERED)
    private val sttPartialChannel = Channel<SttPartial>(Channel.CONFLATED)
    private val sttFinalChannel = Channel<SttFinal>(Channel.UNLIMITED)

    private var translator: Translator? = null
    private var lastPartialTime = 0L
    private val partialDebounceMs = 300L

    init {
        // Process STT finals and translate them
        scope.launch {
            sttFinalChannel.receiveAsFlow().collect { final ->
                if (final.text.isNotBlank()) {
                    // Broadcast to UI
                    broadcastSttFinal(final.text)

                    // Translate
                    translator?.let { trans ->
                        val translated = withContext(Dispatchers.IO) {
                            trans.translate(final.text, "en", "es")
                        }
                        broadcastTranslation(translated)
                    }
                }
            }
        }

        // Process STT partials with debouncing
        scope.launch {
            sttPartialChannel.receiveAsFlow().collect { partial ->
                val now = System.currentTimeMillis()
                if (now - lastPartialTime > partialDebounceMs) {
                    lastPartialTime = now
                    broadcastSttPartial(partial.text)
                }
            }
        }
    }

    fun setTranslator(translator: Translator) {
        this.translator = translator
    }

    // Called by AudioEngine with PCM frames
    fun onPcmFrame(frame: ShortArray, length: Int) {
        scope.launch {
            pcmChannel.trySend(PcmFrame(frame.copyOf(length), length))
        }
    }

    // Called by STT engine with partial results
    fun onSttPartial(partial: SttPartial) {
        sttPartialChannel.trySend(partial)
    }

    // Called by STT engine with final results
    fun onSttFinal(final: SttFinal) {
        sttFinalChannel.trySend(final)
    }

    // Get PCM frames for STT processing
    fun getPcmFlow() = pcmChannel.receiveAsFlow()

    private fun broadcastSttPartial(text: String) {
        val intent = Intent(MainActivity.ACTION_STT_PARTIAL).apply {
            putExtra(MainActivity.EXTRA_TEXT, text)
        }
        context.sendBroadcast(intent)
    }

    private fun broadcastSttFinal(text: String) {
        val intent = Intent(MainActivity.ACTION_STT_FINAL).apply {
            putExtra(MainActivity.EXTRA_TEXT, text)
        }
        context.sendBroadcast(intent)
    }

    private fun broadcastTranslation(text: String) {
        val intent = Intent(MainActivity.ACTION_TRANSLATION).apply {
            putExtra(MainActivity.EXTRA_TEXT, text)
        }
        context.sendBroadcast(intent)
    }

    fun shutdown() {
        pcmChannel.close()
        sttPartialChannel.close()
        sttFinalChannel.close()
    }
}

data class PcmFrame(val data: ShortArray, val length: Int)