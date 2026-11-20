package com.smartvoices.keyboard.prediction

object PredictionEngine {

    // Common English words for prefix completion
    private val englishDictionary = listOf(
        "the", "be", "to", "of", "and", "a", "in", "that", "have", "i",
        "it", "for", "not", "on", "with", "he", "as", "you", "do", "at",
        "this", "but", "his", "by", "from", "they", "we", "say", "her", "she",
        "or", "an", "will", "my", "one", "all", "would", "there", "their", "what",
        "so", "up", "out", "if", "about", "who", "get", "which", "go", "me",
        "when", "make", "can", "like", "time", "no", "just", "him", "know", "take",
        "people", "into", "year", "your", "good", "some", "could", "them", "see", "other",
        "than", "then", "now", "look", "only", "come", "its", "over", "think", "also",
        "back", "after", "use", "two", "how", "our", "work", "first", "well", "way",
        "even", "new", "want", "because", "any", "these", "give", "day", "most", "us",
        "hello", "thanks", "thank", "please", "morning", "night", "welcome", "happy",
        "meeting", "message", "important", "problem", "solution", "project", "awesome"
    )

    // English Next-Word N-Gram Associations
    private val englishNextWordMap = mapOf(
        "how" to listOf("are", "is", "was", "can", "do", "about"),
        "how are" to listOf("you", "things", "they", "we"),
        "are" to listOf("you", "we", "they", "there", "coming"),
        "thank" to listOf("you", "god", "everyone", "very"),
        "thanks" to listOf("a lot", "so much", "brother", "again"),
        "i" to listOf("am", "will", "have", "would", "want", "think", "can", "need"),
        "i am" to listOf("fine", "good", "ready", "happy", "sorry", "here", "doing"),
        "what" to listOf("is", "are", "do", "about", "happened", "time"),
        "where" to listOf("are", "is", "were", "did", "can"),
        "good" to listOf("morning", "night", "afternoon", "evening", "job", "luck"),
        "please" to listOf("let", "find", "help", "call", "send", "check"),
        "see" to listOf("you", "later", "soon", "tomorrow"),
        "have" to listOf("a good", "done", "been", "seen", "to"),
        "nice" to listOf("to meet", "day", "one", "picture"),
        "let" to listOf("me", "us", "it", "them")
    )

    // Bangla Next-Word N-Gram Associations
    private val banglaNextWordMap = mapOf(
        "আমি" to listOf("ভালো", "তোমাকে", "আছি", "এখন", "যাবো", "বলছি", "চাই", "করবো"),
        "তুমি" to listOf("কেমন", "কোথায়", "কি", "আছো", "যাবে", "করছো", "বলো"),
        "আপনি" to listOf("কেমন", "কোথায়", "আছেন", "বলেন", "শুনুন", "যাবেন"),
        "কেমন" to listOf("আছো", "আছেন", "হলো", "লাগে", "চলছে"),
        "ধন্যবাদ" to listOf("ভাই", "আপনাকে", "অনেক", "তোমাকে", "প্রিয়"),
        "শুভ" to listOf("সকাল", "রাত্রি", "কামনা", "দিন", "সন্ধ্যা", "জন্মদিন"),
        "আসসালামু" to listOf("আলাইকুম"),
        "ওয়া" to listOf("আলাইকুম"),
        "ইনশা" to listOf("আল্লাহ"),
        "মাশা" to listOf("আল্লাহ"),
        "আলহামদুলিল্লাহ" to listOf("ভালো", "আছি", "সব", "ঠিক"),
        "কি" to listOf("খবর", "করছো", "হয়েছে", "বলো", "অবস্থা"),
        "অনেক" to listOf("ধন্যবাদ", "ভালো", "সুন্দর", "দিন", "টাকা"),
        "খুব" to listOf("ভালো", "সুন্দর", "দ্রুত", "সহজ", "কঠিন"),
        "ঠিক" to listOf("আছে", "হয়েছে", "ঠিক", "কথা"),
        "কালকে" to listOf("দেখা", "হবে", "আসবো", "যাবো"),
        "এখন" to listOf("কোথায়", "আছো", "যাবো", "করছি")
    )

    // Common Bangla Vocabulary for prefix completion
    private val banglaVocabulary = listOf(
        "বাংলাদেশ", "ধন্যবাদ", "ভালোবাসা", "স্বাগতম", "অভিনন্দন", "শুভকামনা",
        "প্রয়োজন", "সমস্যা", "সমাধান", "যোগাযোগ", "পরিবার", "বিদ্যালয়", "অফিস",
        "গুরুত্বপূর্ণ", "সহজ", "কঠিন", "দ্রুত", "ধীরে", "অনুগ্রহ", "অনুরোধ"
    )

    /**
     * Get predictions based on current composing word and previous context
     */
    fun getPredictions(
        currentWord: String,
        previousText: String,
        isBangla: Boolean
    ): List<String> {
        val results = mutableListOf<String>()
        val cleanWord = currentWord.trim().lowercase()

        if (cleanWord.isEmpty()) {
            // Predict NEXT words based on previous word/words
            val lastTokens = previousText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (lastTokens.isNotEmpty()) {
                val lastWord = lastTokens.last()
                val lastTwoWords = if (lastTokens.size >= 2) "${lastTokens[lastTokens.size - 2]} $lastWord" else ""

                if (isBangla) {
                    banglaNextWordMap[lastWord]?.let { results.addAll(it) }
                } else {
                    val lowerLast = lastWord.lowercase()
                    val lowerTwo = lastTwoWords.lowercase()

                    englishNextWordMap[lowerTwo]?.let { results.addAll(it) }
                    if (results.isEmpty()) {
                        englishNextWordMap[lowerLast]?.let { results.addAll(it) }
                    }
                }
            }
        } else {
            // Autocomplete prefix for current in-progress word
            if (isBangla) {
                val matches = banglaVocabulary.filter { it.startsWith(cleanWord) && it != cleanWord }
                results.addAll(matches.take(4))
            } else {
                val matches = englishDictionary.filter { it.startsWith(cleanWord) && it != cleanWord }
                results.addAll(matches.take(4))
            }
        }

        return results.distinct().take(4)
    }
}
