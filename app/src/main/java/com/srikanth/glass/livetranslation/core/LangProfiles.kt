package com.srikanth.glass.livetranslation.core

/**
 * Central catalogue of supported language pairs.
 * Profiles keep track of the STT and translation assets that need to be
 * installed for each direction.
 */
object LangProfiles {
    data class LanguageProfile(
        val id: String,
        val label: String,
        val sourceLang: String,
        val targetLang: String,
        val sttModel: String,
        val translationPack: String
    )

    private val profileList = listOf(
        LanguageProfile("en-es", "English → Spanish", "en", "es", "vosk-model-small-en-us-0.15", "apertium-en-es"),
        LanguageProfile("es-en", "Spanish → English", "es", "en", "vosk-model-small-es-0.42", "apertium-es-en"),
        LanguageProfile("en-fr", "English → French", "en", "fr", "vosk-model-small-en-us-0.15", "apertium-en-fr"),
        LanguageProfile("fr-en", "French → English", "fr", "en", "vosk-model-small-fr-0.22", "apertium-fr-en"),
        LanguageProfile("en-ko", "English → Korean", "en", "ko", "vosk-model-small-en-us-0.15", "apertium-en-ko"),
        LanguageProfile("ko-en", "Korean → English", "ko", "en", "vosk-model-small-ko-0.22", "apertium-ko-en"),
        LanguageProfile("en-ja", "English → Japanese", "en", "ja", "vosk-model-small-en-us-0.15", "apertium-en-ja"),
        LanguageProfile("ja-en", "Japanese → English", "ja", "en", "vosk-model-small-ja-0.22", "apertium-ja-en"),
        LanguageProfile("en-hi", "English → Hindi", "en", "hi", "vosk-model-small-en-us-0.15", "apertium-en-hi"),
        LanguageProfile("hi-en", "Hindi → English", "hi", "en", "vosk-model-small-hi-0.22", "apertium-hi-en"),
        LanguageProfile("en-ta", "English → Tamil", "en", "ta", "vosk-model-small-en-us-0.15", "apertium-en-ta"),
        LanguageProfile("ta-en", "Tamil → English", "ta", "en", "vosk-model-small-ta-0.22", "apertium-ta-en")
    )

    private var currentIndex = 0

    fun current(): LanguageProfile = profileList[currentIndex]

    fun setCurrentById(profileId: String): LanguageProfile {
        val index = profileList.indexOfFirst { it.id == profileId }
        currentIndex = if (index >= 0) index else 0
        return profileList[currentIndex]
    }

    fun next(): LanguageProfile {
        currentIndex = (currentIndex + 1) % profileList.size
        return current()
    }

    fun previous(): LanguageProfile {
        currentIndex = if (currentIndex - 1 < 0) profileList.lastIndex else currentIndex - 1
        return current()
    }

    fun allProfiles(): List<LanguageProfile> = profileList
}
