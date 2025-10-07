package com.srikanth.glass.livetranslation.core

import android.content.Context
import android.content.SharedPreferences

class Settings(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("glass_live_prefs", Context.MODE_PRIVATE)

    var useEdgeMode: Boolean
        get() = prefs.getBoolean("use_edge_mode", false)
        set(value) = prefs.edit().putBoolean("use_edge_mode", value).apply()

    var vadAggressiveness: Int
        get() = prefs.getInt("vad_aggressiveness", 2)
        set(value) = prefs.edit().putInt("vad_aggressiveness", value).apply()

    var maxSilenceMs: Int
        get() = prefs.getInt("max_silence_ms", 700)
        set(value) = prefs.edit().putInt("max_silence_ms", value).apply()

    var lastLanguagePair: String
        get() = prefs.getString("last_language_pair", "en-es") ?: "en-es"
        set(value) = prefs.edit().putString("last_language_pair", value).apply()
}