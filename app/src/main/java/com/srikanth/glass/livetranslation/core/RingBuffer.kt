package com.srikanth.glass.livetranslation.core

import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class RingBuffer(private val maxLines: Int = 100) {
    private val sourceLines = mutableListOf<TimestampedLine>()
    private val targetLines = mutableListOf<TimestampedLine>()

    data class TimestampedLine(
        val text: String,
        val timestamp: Long = System.currentTimeMillis()
    )

    fun addSource(text: String) {
        sourceLines.add(TimestampedLine(text))
        if (sourceLines.size > maxLines) {
            sourceLines.removeAt(0)
        }
    }

    fun addTarget(text: String) {
        targetLines.add(TimestampedLine(text))
        if (targetLines.size > maxLines) {
            targetLines.removeAt(0)
        }
    }

    fun exportSession(outputDir: File): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(outputDir, "session_$timestamp.txt")

        file.bufferedWriter().use { writer ->
            writer.write("Glass Live Translation Session\n")
            writer.write("Date: ${Date()}\n")
            writer.write("=" * 40 + "\n\n")

            sourceLines.zip(targetLines).forEach { (source, target) ->
                writer.write("[${formatTime(source.timestamp)}] SOURCE: ${source.text}\n")
                writer.write("[${formatTime(target.timestamp)}] TARGET: ${target.text}\n\n")
            }
        }
        return file
    }

    private fun formatTime(timestamp: Long): String {
        return SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(timestamp))
    }
}