package com.srikanth.glass.livetranslation.core

import android.content.Context
import android.content.Intent
import android.util.Log
import com.srikanth.glass.livetranslation.translate.Translator
import com.srikanth.glass.livetranslation.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Central event bus that connects the audio/STT pipeline with the UI.
 * It owns the translator instance and serialises translation requests on a
 * background coroutine dispatcher so that UI updates are smooth.
 */
class PipelineBus(
    context: Context,
    private val scope: CoroutineScope
) {
    enum class StatusChannel { MIC, VAD, STT, TRANSLATOR }
    enum class StatusState { IDLE, ACTIVE, PAUSED, BUSY, ERROR }

    private val appContext = context.applicationContext

    private val sttPartialChannel = Channel<String>(Channel.CONFLATED)
    private val sttFinalChannel = Channel<String>(Channel.UNLIMITED)
    private val translationChannel = Channel<String>(Channel.UNLIMITED)

    private var translationJob: Job? = null
    private var translator: Translator? = null
    private var currentProfile: LangProfiles.LanguageProfile? = null
    private var translatorSupported = false
    private val hasActiveStt = AtomicBoolean(false)
    private val hasActiveTranslation = AtomicBoolean(false)
    private val translationAccumulator = StringBuilder()
    private val translationLock = Any()
    private val maxBatchLength = 60

    init {
        scope.launch {
            sttPartialChannel.consumeAsFlow().collect { partial ->
                broadcastSttPartial(partial)
            }
        }

        scope.launch {
            sttFinalChannel.consumeAsFlow().collect { final ->
                broadcastSttFinal(final)
                enqueueForTranslation(final)
                hasActiveStt.set(false)
                updateStatus(StatusChannel.STT, StatusState.IDLE)
            }
        }

        launchTranslationWorker()
    }

    fun setLanguageProfile(
        profile: LangProfiles.LanguageProfile,
        translator: Translator
    ) {
        this.currentProfile = profile
        this.translator?.close()
        this.translator = translator

        synchronized(translationLock) {
            translationAccumulator.clear()
        }
        translationChannel.trySend("__flush__") // clears queue downstream

        try {
            translator.initialize(profile.sourceLang, profile.targetLang)
            translatorSupported = translator.isSupported(profile.sourceLang, profile.targetLang)
            updateStatus(
                StatusChannel.TRANSLATOR,
                if (translatorSupported) StatusState.IDLE else StatusState.ERROR
            )
        } catch (t: Throwable) {
            Log.e("PipelineBus", "Translator init failed", t)
            translatorSupported = false
            updateStatus(StatusChannel.TRANSLATOR, StatusState.ERROR)
        }

        updateStatus(StatusChannel.STT, StatusState.IDLE)
        broadcastLanguage(profile)
    }

    fun onSttPartial(text: String) {
        if (text.isBlank()) return
        sttPartialChannel.trySend(text)
        if (hasActiveStt.compareAndSet(false, true)) {
            updateStatus(StatusChannel.STT, StatusState.BUSY)
        }
    }

    fun onSttFinal(text: String) {
        if (text.isBlank()) return
        sttFinalChannel.trySend(text)
    }

    fun onVadStateChanged(isSpeech: Boolean) {
        updateStatus(StatusChannel.VAD, if (isSpeech) StatusState.ACTIVE else StatusState.IDLE)
    }

    fun updateStatus(channel: StatusChannel, state: StatusState) {
        val intent = Intent(MainActivity.ACTION_STATUS).apply {
            putExtra(MainActivity.EXTRA_STATUS_CHANNEL, channel.name)
            putExtra(MainActivity.EXTRA_STATUS_STATE, state.name)
        }
        appContext.sendBroadcast(intent)
    }

    fun shutdown() {
        translationChannel.close()
        sttPartialChannel.close()
        sttFinalChannel.close()
        translator?.close()
        translationJob?.cancel()
        translator = null
        translatorSupported = false
    }

    private fun broadcastSttPartial(text: String) {
        val intent = Intent(MainActivity.ACTION_STT_PARTIAL).apply {
            putExtra(MainActivity.EXTRA_TEXT, text)
        }
        appContext.sendBroadcast(intent)
    }

    private fun broadcastSttFinal(text: String) {
        val intent = Intent(MainActivity.ACTION_STT_FINAL).apply {
            putExtra(MainActivity.EXTRA_TEXT, text)
        }
        appContext.sendBroadcast(intent)
    }

    private fun broadcastTranslation(text: String) {
        val intent = Intent(MainActivity.ACTION_TRANSLATION).apply {
            putExtra(MainActivity.EXTRA_TEXT, text)
        }
        appContext.sendBroadcast(intent)
    }

    private fun broadcastLanguage(profile: LangProfiles.LanguageProfile) {
        val intent = Intent(MainActivity.ACTION_LANGUAGE_CHANGED).apply {
            putExtra(MainActivity.EXTRA_LANGUAGE_LABEL, profile.label)
            putExtra(MainActivity.EXTRA_LANGUAGE_ID, profile.id)
        }
        appContext.sendBroadcast(intent)
    }

    private fun enqueueForTranslation(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        var snapshot: String? = null

        synchronized(translationLock) {
            if (translationAccumulator.isEmpty()) {
                translationAccumulator.append(trimmed)
            } else {
                val candidate = translationAccumulator.toString() + " " + trimmed
                if (candidate.length <= maxBatchLength) {
                    translationAccumulator.clear()
                    translationAccumulator.append(candidate)
                } else {
                    snapshot = translationAccumulator.toString()
                    translationAccumulator.clear()
                    translationAccumulator.append(trimmed)
                }
            }

            if (trimmed.endsWith('.') || trimmed.endsWith('?') || trimmed.endsWith('!')) {
                snapshot = translationAccumulator.toString()
                translationAccumulator.clear()
            }
        }

        snapshot?.let { translationChannel.trySend(it) }
    }

    private fun launchTranslationWorker() {
        translationJob?.cancel()
        translationJob = scope.launch {
            translationChannel.consumeAsFlow().collect { segment ->
                if (segment == "__flush__") {
                    synchronized(translationLock) {
                        translationAccumulator.clear()
                    }
                    return@collect
                }

                val activeTranslator = translator
                val profile = currentProfile

                if (activeTranslator == null || profile == null || !translatorSupported) {
                    broadcastTranslation("$segment [untranslated]")
                    updateStatus(StatusChannel.TRANSLATOR, StatusState.ERROR)
                    return@collect
                }

                updateStatus(StatusChannel.TRANSLATOR, StatusState.BUSY)
                if (hasActiveTranslation.compareAndSet(false, true)) {
                    // No-op, just mark busy
                }

                var success = true
                val translated = try {
                    withContext(Dispatchers.IO) {
                        activeTranslator.translate(segment, profile.sourceLang, profile.targetLang)
                    }
                } catch (t: Throwable) {
                    Log.e("PipelineBus", "Translation failed", t)
                    updateStatus(StatusChannel.TRANSLATOR, StatusState.ERROR)
                    success = false
                    "$segment [untranslated]"
                }

                broadcastTranslation(translated)
                hasActiveTranslation.set(false)
                if (success) {
                    updateStatus(StatusChannel.TRANSLATOR, StatusState.IDLE)
                }
            }
        }
    }
}
