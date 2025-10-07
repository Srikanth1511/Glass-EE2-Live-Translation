package com.srikanth.glass.livetranslation.translate

class ApertiumTranslator : Translator {
    private var sourceLang: String = "en"
    private var targetLang: String = "es"

    private val dictionaries: Map<String, Map<String, String>> = mapOf(
        "en-es" to mapOf(
            "hello" to "hola",
            "good" to "bueno",
            "morning" to "mañana",
            "thank" to "gracias",
            "thanks" to "gracias",
            "you" to "tú",
            "how" to "cómo",
            "are" to "estás",
            "i" to "yo",
            "am" to "estoy",
            "fine" to "bien",
            "please" to "por favor"
        ),
        "es-en" to mapOf(
            "hola" to "hello",
            "gracias" to "thanks",
            "buenos" to "good",
            "días" to "morning",
            "cómo" to "how",
            "como" to "how",
            "estás" to "are",
            "estoy" to "am",
            "bien" to "fine"
        ),
        "en-fr" to mapOf(
            "hello" to "bonjour",
            "good" to "bon",
            "morning" to "matin",
            "thank" to "merci",
            "thanks" to "merci",
            "you" to "toi",
            "how" to "comment",
            "are" to "es",
            "i" to "je",
            "am" to "suis",
            "fine" to "bien",
            "please" to "s'il te plaît"
        ),
        "fr-en" to mapOf(
            "bonjour" to "hello",
            "merci" to "thanks",
            "comment" to "how",
            "ça" to "that",
            "va" to "goes",
            "je" to "i",
            "suis" to "am",
            "bien" to "fine"
        )
    )

    override fun initialize(sourceLang: String, targetLang: String) {
        this.sourceLang = sourceLang.lowercase()
        this.targetLang = targetLang.lowercase()
    }

    override fun translate(text: String, sourceLang: String?, targetLang: String?): String {
        val src = (sourceLang ?: this.sourceLang).lowercase()
        val tgt = (targetLang ?: this.targetLang).lowercase()
        val key = "$src-$tgt"
        val dictionary = dictionaries[key] ?: return "$text [untranslated]"

        val builder = StringBuilder(text.length)
        var index = 0
        while (index < text.length) {
            val ch = text[index]
            if (ch.isLetter()) {
                val start = index
                while (index < text.length && text[index].isLetter()) {
                    index++
                }
                val token = text.substring(start, index)
                val translation = dictionary[token.lowercase()] ?: token
                builder.append(applyCase(token, translation))
            } else {
                builder.append(ch)
                index++
            }
        }

        return builder.toString()
    }

    override fun isSupported(sourceLang: String, targetLang: String): Boolean {
        val key = "${sourceLang.lowercase()}-${targetLang.lowercase()}"
        return dictionaries.containsKey(key)
    }

    override fun close() {
        // No native resources to release for the stub translator.
    }

    private fun applyCase(source: String, translation: String): String {
        return when {
            source.all { it.isUpperCase() } -> translation.uppercase()
            source.firstOrNull()?.isUpperCase() == true -> translation.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            else -> translation
        }
    }
}
