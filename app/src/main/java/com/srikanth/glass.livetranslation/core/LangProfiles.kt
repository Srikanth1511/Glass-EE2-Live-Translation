package com.srikanth.glass.livetranslation.core

data class LanguageProfile(
    val name: String,
    val sourceLang: String,
    val targetLang: String,
    val sttModel: String,
    val translationModel: String
)

object LangProfiles {
    val profiles = listOf(
        LanguageProfile("English → Spanish", "en", "es",
            "vosk-model-small-en-us-0.15", "apertium-en-es"),
        LanguageProfile("Spanish → English", "es", "en",
            "vosk-model-small-es-0.42", "apertium-es-en"),
        LanguageProfile("English → French", "en", "fr",
            "vosk-model-small-en-us-0.15", "apertium-en-fr")
    )

    var currentProfile = profiles[0]

    fun cycleProfile() {
        val currentIndex = profiles.indexOf(currentProfile)
        currentProfile = profiles[(currentIndex + 1) % profiles.size]
    }
}