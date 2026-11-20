package com.smartvoices.keyboard.voice

object VoiceCommandProcessor {

    data class VoiceResult(
        val isCommand: Boolean,
        val commandType: String, // "DIRECT_DICTATION", "TRANSLATE_TO_BN", "TRANSLATE_TO_EN", "CLEAR", "NEWLINE"
        val payload: String,
        val finalText: String
    )

    /**
     * Replaces spoken punctuation words, detects voice intent commands,
     * and applies contextual auto-punctuation rules for Bengali & English.
     */
    fun processVoiceText(rawText: String, langMode: String): VoiceResult {
        var text = rawText.trim()
        if (text.isBlank()) {
            return VoiceResult(false, "DIRECT_DICTATION", "", "")
        }

        // 1. Voice Editing Commands
        val lower = text.lowercase()
        if (lower == "মুছে দাও" || lower == "ক্লিয়ার" || lower == "সব মুছুন" || lower == "clear all" || lower == "delete all") {
            return VoiceResult(true, "CLEAR", "", "")
        }

        if (lower == "নতুন লাইন" || lower == "পরের লাইন" || lower == "new line") {
            return VoiceResult(true, "NEWLINE", "", "\n")
        }

        // 2. Natural Voice Translation Commands
        val translateBnPattern = Regex("^(বাংলা করো|বাংলায় বলো|translate to bangla|translate to bengali)[:\\s]+(.+)", RegexOption.IGNORE_CASE)
        val matchBn = translateBnPattern.find(text)
        if (matchBn != null) {
            val payload = matchBn.groupValues[2].trim()
            return VoiceResult(true, "TRANSLATE_TO_BN", payload, payload)
        }

        val translateEnPattern = Regex("^(ইংরেজি করো|ইংরেজিতে বলো|translate to english)[:\\s]+(.+)", RegexOption.IGNORE_CASE)
        val matchEn = translateEnPattern.find(text)
        if (matchEn != null) {
            val payload = matchEn.groupValues[2].trim()
            return VoiceResult(true, "TRANSLATE_TO_EN", payload, payload)
        }

        // 3. Spoken punctuation substitution (Unicode & word-boundary aware)
        text = text
            .replace(Regex("(?:^|\\s+)(দাড়ি|দাড়ি|পূর্ণচ্ছেদ)(?:\\s+|$)", RegexOption.IGNORE_CASE), "। ")
            .replace(Regex("(?:^|\\s+)কমা(?:\\s+|$)", RegexOption.IGNORE_CASE), ", ")
            .replace(Regex("(?:^|\\s+)(প্রশ্নবোধক|জিজ্ঞাসা চিহ্ন|প্রশ্ন চিহ্ন)(?:\\s+|$)", RegexOption.IGNORE_CASE), "? ")
            .replace(Regex("(?:^|\\s+)(বিস্ময়সূচক|আশ্চর্যবোধক)(?:\\s+|$)", RegexOption.IGNORE_CASE), "! ")
            .replace(Regex("\\b(period|full stop)\\b", RegexOption.IGNORE_CASE), ".")
            .replace(Regex("\\bcomma\\b", RegexOption.IGNORE_CASE), ",")
            .replace(Regex("\\bquestion mark\\b", RegexOption.IGNORE_CASE), "?")
            .replace(Regex("\\bexclamation mark\\b", RegexOption.IGNORE_CASE), "!")
            .replace(Regex("(?:^|\\s+)(নতুন লাইন|পরের লাইন|new line)(?:\\s+|$)", RegexOption.IGNORE_CASE), "\n")
            .trim()

        // 4. Contextual Auto-Punctuation logic
        val lastChar = text.last()
        val hasEndPunctuation = lastChar == '.' || lastChar == '।' || lastChar == '?' || lastChar == '!'

        if (!hasEndPunctuation) {
            if (langMode == "en") {
                // English auto punctuation
                val questionWords = listOf(
                    "what", "where", "when", "why", "who", "whom", "whose",
                    "how", "can", "could", "would", "should", "is", "are",
                    "do", "does", "did", "will", "shall"
                )
                val firstWord = text.split(" ").firstOrNull()?.lowercase() ?: ""
                text = if (questionWords.contains(firstWord)) {
                    "$text?"
                } else {
                    "$text."
                }
                // Capitalize first character
                text = text.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            } else {
                // Bengali auto punctuation
                val bnQuestionKeywords = listOf(
                    "কী", "কি", "কেন", "কোথায়", "কোথায়", "কেমন", "কখন",
                    "কাকে", "কার", "হবে কি", "আছো কি", "গেছো কি", "পারি কি"
                )
                val isQuestion = bnQuestionKeywords.any { text.contains(it) }
                text = if (isQuestion) {
                    "$text?"
                } else {
                    "$text।"
                }
            }
        }

        // 5. Typography formatting: attach punctuation without leading space
        text = text
            .replace(Regex("\\s+([,।!?.])"), "$1")
            .replace(Regex("([,।])([^\\s\\n])"), "$1 $2")
            .replace(Regex("\\s+"), " ")
            .trim()

        return VoiceResult(false, "DIRECT_DICTATION", text, text)
    }
}
