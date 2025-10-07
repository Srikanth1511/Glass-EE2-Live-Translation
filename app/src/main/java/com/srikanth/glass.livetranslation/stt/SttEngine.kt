package com.srikanth.glass.livetranslation.stt

/**
 * Common interface for all STT engines (on-device and edge)
 */
interface SttEngine {
    /**
     * Initialize the engine with language model
     * @param modelPath Path to model directory or configuration
     */
    fun start(modelPath: String)

    /**
     * Process PCM audio frame
     * @param frame 16-bit PCM samples
     * @param length Number of valid samples
     * @return Partial transcription if available (null if no update)
     */
    fun acceptPcm(frame: ShortArray, length: Int): SttPartial?

    /**
     * Flush any pending audio and get final result
     * @return Final transcription with punctuation/capitalization
     */
    fun flush(): SttFinal?

    /**
     * Stop engine and release resources
     */
    fun stop()
}

/**
 * Partial (unstable) transcription result
 * Updates frequently as user speaks
 */
data class SttPartial(
    val text: String,
    val confidence: Float = 0.0f
)

/**
 * Final (committed) transcription result
 * Only emitted at utterance boundaries
 */
data class SttFinal(
    val text: String,
    val confidence: Float = 1.0f,
    val timestamp: Long = System.currentTimeMillis()
)