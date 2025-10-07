package com.srikanth.glass.livetranslation.translate

/**
 * Common interface for translation engines
 */
interface Translator {
    /**
     * Initialize translator with language pair
     * @param sourceLang Source language code (ISO 639-1)
     * @param targetLang Target language code (ISO 639-1)
     */
    fun initialize(sourceLang: String, targetLang: String)

    /**
     * Translate text
     * @param text Source text to translate
     * @param sourceLang Source language (optional, uses initialized if null)
     * @param targetLang Target language (optional, uses initialized if null)
     * @return Translated text
     */
    fun translate(text: String, sourceLang: String? = null, targetLang: String? = null): String

    /**
     * Check if language pair is supported
     */
    fun isSupported(sourceLang: String, targetLang: String): Boolean

    /**
     * Release resources
     */
    fun close()
}

data class TranslationResult(
    val translatedText: String,
    val confidence: Float = 0.0f,
    val sourceLang: String,
    val targetLang: String
)