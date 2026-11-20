package com.smartvoices.keyboard.prediction

object PredictionEngine {

    // User learned words memory (word -> frequency)
    private val userLearnedWords = mutableMapOf<String, Int>()

    // Common English Typo / Autocorrect map
    private val englishAutocorrectMap = mapOf(
        "teh" to "the",
        "recieve" to "receive",
        "seperate" to "separate",
        "definately" to "definitely",
        "untill" to "until",
        "occured" to "occurred",
        "truely" to "truly",
        "dont" to "don't",
        "cant" to "can't",
        "wont" to "won't",
        "im" to "I'm",
        "ive" to "I've",
        "youre" to "you're",
        "theyre" to "they're",
        "didnt" to "didn't",
        "isnt" to "isn't",
        "arent" to "aren't",
        "couldnt" to "couldn't",
        "wouldnt" to "wouldn't",
        "shouldnt" to "shouldn't",
        "pls" to "please",
        "thx" to "thanks",
        "u" to "you",
        "ur" to "your",
        "r" to "are"
    )

    // Common Bangla Typo / Autocorrect map
    private val banglaAutocorrectMap = mapOf(
        "করছি" to "করছি",
        "গেসিলাম" to "গিয়েছিলাম",
        "খাইসি" to "খেয়েছি",
        "বলসি" to "বলেছি",
        "ধন্নবাদ" to "ধন্যবাদ",
        "অভিননদন" to "অভিনন্দন",
        "পরিক্ষা" to "পরীক্ষা",
        "পাখি" to "পাখি",
        "পানি" to "পানি",
        "বাংঙ্গালী" to "বাঙালি"
    )

    // Emoji Prediction mappings
    private val emojiMap = mapOf(
        "happy" to "😊", "খুশি" to "😊",
        "love" to "❤️", "ভালোবাসা" to "❤️",
        "congrats" to "🎉", "অভিনন্দন" to "🎉",
        "thanks" to "🙏", "ধন্যবাদ" to "🙏",
        "fire" to "🔥", "দারুণ" to "🔥",
        "cool" to "😎", "সুন্দর" to "👍",
        "sad" to "😢", "দুঃখিত" to "😔",
        "laugh" to "😂", "হাসি" to "😂"
    )

    // High-frequency English words dictionary
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
        "meeting", "message", "important", "problem", "solution", "project", "awesome",
        "tomorrow", "yesterday", "tonight", "today", "friend", "family", "office", "business",
        "call", "email", "send", "receive", "confirm", "schedule", "update", "document"
    )

    // English Bigram & Trigram Associations
    private val englishNextWordMap = mapOf(
        "how" to listOf("are", "is", "was", "can", "do", "about"),
        "how are" to listOf("you", "things", "they", "we"),
        "are" to listOf("you", "we", "they", "there", "coming"),
        "thank" to listOf("you", "god", "everyone", "very"),
        "thanks" to listOf("a lot", "so much", "brother", "again"),
        "i" to listOf("am", "will", "have", "would", "want", "think", "can", "need"),
        "i am" to listOf("fine", "good", "ready", "happy", "sorry", "here", "doing"),
        "what" to listOf("is", "are", "do", "about", "happened", "time"),
        "what is" to listOf("your", "the", "that", "this"),
        "where" to listOf("are", "is", "were", "did", "can"),
        "good" to listOf("morning", "night", "afternoon", "evening", "job", "luck"),
        "please" to listOf("let", "find", "help", "call", "send", "check"),
        "see" to listOf("you", "later", "soon", "tomorrow"),
        "see you" to listOf("soon", "later", "tomorrow", "again"),
        "have" to listOf("a good", "done", "been", "seen", "to"),
        "nice" to listOf("to meet", "day", "one", "picture"),
        "let" to listOf("me", "us", "it", "them"),
        "let me" to listOf("know", "check", "see", "help")
    )

    // Bangla Next-Word Associations
    private val banglaNextWordMap = mapOf(
        "আমি" to listOf("ভালো", "তোমাকে", "আছি", "এখন", "যাবো", "বলছি", "চাই", "করবো"),
        "তুমি" to listOf("কেমন", "কোথায়", "কি", "আছো", "যাবে", "করছো", "বলো"),
        "আপনি" to listOf("কেমন", "কোথায়", "আছেন", "বলেন", "শুনুন", "যাবেন"),
        "কেমন" to listOf("আছো", "আছেন", "হলো", "লাগে", "চলছে"),
        "কেমন আছো" to listOf("বন্ধু", "ভাই", "প্রিয়", "কেমন"),
        "ধন্যবাদ" to listOf("ভাই", "আপনাকে", "অনেক", "তোমাকে", "প্রিয়"),
        "শুভ" to listOf("সকাল", "রাত্রি", "কামনা", "দিন", "সন্ধ্যা", "জন্মদিন"),
        "আসসালামু" to listOf("আলাইকুম"),
        "ওয়া" to listOf("আলাইকুম"),
        "ওয়া আলাইকুম" to listOf("আসসালাম"),
        "ইনশা" to listOf("আল্লাহ"),
        "ইনশা আল্লাহ" to listOf("দেখা", "হবে", "আসবো", "ভালো"),
        "মাশা" to listOf("আল্লাহ"),
        "আলহামদুলিল্লাহ" to listOf("ভালো", "আছি", "সব", "ঠিক"),
        "কি" to listOf("খবর", "করছো", "হয়েছে", "বলো", "অবস্থা"),
        "অনেক" to listOf("ধন্যবাদ", "ভালো", "সুন্দর", "দিন", "টাকা"),
        "খুব" to listOf("ভালো", "সুন্দর", "দ্রুত", "সহজ", "কঠিন"),
        "ঠিক" to listOf("আছে", "হয়েছে", "ঠিক", "কথা"),
        "কালকে" to listOf("দেখা", "হবে", "আসবো", "যাবো", "মিটিং"),
        "এখন" to listOf("কোথায়", "আছো", "যাবো", "করছি", "না")
    )

    // High-frequency Bangla Vocabulary for prefix completion
    private val banglaVocabulary = listOf(
        "বাংলাদেশ", "ধন্যবাদ", "ভালোবাসা", "স্বাগতম", "অভিনন্দন", "শুভকামনা",
        "প্রয়োজন", "সমস্যা", "সমাধান", "যোগাযোগ", "পরিবার", "বিদ্যালয়", "অফিস",
        "গুরুত্বপূর্ণ", "সহজ", "কঠিন", "দ্রুত", "ধীরে", "অনুগ্রহ", "অনুরোধ",
        "প্রতিদিন", "সাধারণ", "বিশেষ", "বর্তমান", "ভবিষ্যৎ", "সম্পর্ক", "বন্ধু"
    )

    /**
     * Learn a word typed by user to prioritize it in future typing
     */
    fun learnWord(word: String) {
        val clean = word.trim()
        if (clean.length >= 2) {
            val count = userLearnedWords.getOrDefault(clean, 0)
            userLearnedWords[clean] = count + 1
        }
    }

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

        // 1. Emoji association check
        emojiMap[cleanWord]?.let { results.add(it) }

        if (cleanWord.isEmpty()) {
            // Predict NEXT words based on previous word/context
            val lastTokens = previousText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (lastTokens.isNotEmpty()) {
                val lastWord = lastTokens.last()
                val lastTwoWords = if (lastTokens.size >= 2) "${lastTokens[lastTokens.size - 2]} $lastWord" else ""

                if (isBangla) {
                    banglaNextWordMap[lastTwoWords]?.let { results.addAll(it) }
                    if (results.isEmpty()) {
                        banglaNextWordMap[lastWord]?.let { results.addAll(it) }
                    }
                    // Emoji for last word
                    emojiMap[lastWord]?.let { results.add(it) }
                } else {
                    val lowerLast = lastWord.lowercase()
                    val lowerTwo = lastTwoWords.lowercase()

                    englishNextWordMap[lowerTwo]?.let { results.addAll(it) }
                    if (results.isEmpty()) {
                        englishNextWordMap[lowerLast]?.let { results.addAll(it) }
                    }
                    // Emoji for last word
                    emojiMap[lowerLast]?.let { results.add(it) }
                }
            }
        } else {
            // 2. Autocorrect match first
            if (isBangla) {
                banglaAutocorrectMap[cleanWord]?.let { results.add(it) }
            } else {
                englishAutocorrectMap[cleanWord]?.let { results.add(it) }
            }

            // 3. User learned words matching prefix
            val userMatches = userLearnedWords.filter { (w, _) ->
                w.lowercase().startsWith(cleanWord) && w.lowercase() != cleanWord
            }.entries.sortedByDescending { it.value }.map { it.key }
            results.addAll(userMatches.take(2))

            // 4. Vocabulary prefix matching
            if (isBangla) {
                val matches = banglaVocabulary.filter { it.startsWith(cleanWord) && it != cleanWord }
                results.addAll(matches.take(4))
            } else {
                val matches = englishDictionary.filter { it.startsWith(cleanWord) && it != cleanWord }
                results.addAll(matches.take(4))
            }
        }

        return results.distinct().take(5)
    }
}
