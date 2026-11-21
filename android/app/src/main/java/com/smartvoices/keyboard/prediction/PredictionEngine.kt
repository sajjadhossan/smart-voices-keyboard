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
        "বাংঙ্গালী" to "বাঙালি",
        "আসি" to "আছি",
        "জানি" to "জানি",
        "করবো" to "করব",
        "কিসু" to "কিছু",
        "আসিতেছে" to "আসছে",
        "হইসে" to "হয়েছে"
    )

    // Bangla Orthographic & Phonetic Alternates (যেমন: ত ↔ ট, থ ↔ ঠ, দ ↔ ড, ধ ↔ ঢ, স ↔ শ ↔ ষ, ন ↔ ণ, ব ↔ ভ, র ↔ ড়)
    private val banglaCharAlternates = mapOf(
        'ত' to listOf('ট'),
        'ট' to listOf('ত'),
        'থ' to listOf('ঠ'),
        'ঠ' to listOf('থ'),
        'দ' to listOf('ড'),
        'ড' to listOf('দ'),
        'ধ' to listOf('ঢ'),
        'ঢ' to listOf('ধ'),
        'শ' to listOf('ষ', 'স'),
        'ষ' to listOf('শ', 'স'),
        'স' to listOf('শ', 'ষ'),
        'ন' to listOf('ণ'),
        'ণ' to listOf('ন'),
        'ব' to listOf('ভ'),
        'ভ' to listOf('ব'),
        'র' to listOf('ড়', 'ঢ়'),
        'ড়' to listOf('র'),
        'ঢ়' to listOf('র'),
        'ক' to listOf('খ'),
        'খ' to listOf('ক'),
        'গ' to listOf('ঘ'),
        'ঘ' to listOf('গ'),
        'জ' to listOf('য'),
        'য' to listOf('জ')
    )

    // Emoji Prediction mappings
    private val emojiMap = mapOf(
        "happy" to "😊", "খুশি" to "😊", "আনন্দ" to "😄",
        "love" to "❤️", "ভালোবাসা" to "❤️", "প্রেম" to "💖",
        "congrats" to "🎉", "অভিনন্দন" to "🎉", "মোবারক" to "🎊",
        "thanks" to "🙏", "ধন্যবাদ" to "🙏", "শুকরিয়া" to "🤲",
        "fire" to "🔥", "দারুণ" to "🔥", "অসাধারণ" to "🔥",
        "cool" to "😎", "সুন্দর" to "👍", "সেরা" to "👌",
        "sad" to "😢", "দুঃখিত" to "😔", "কষ্ট" to "🥺",
        "laugh" to "😂", "হাসি" to "😂", "মজা" to "🤣",
        "food" to "🍔", "খাবার" to "🍲", "ভাত" to "🍚", "চা" to "☕",
        "good" to "👍", "ভালো" to "👍", "মিটিং" to "📅", "টাকা" to "💰"
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

    // Comprehensive High-Frequency Bangla Vocabulary & Compounds
    private val banglaVocabulary = listOf(
        // চাল & সম্পর্কিত শব্দসমূহ
        "চাল", "চালতা", "চালাক", "চালাকি", "চালানো", "চালিয়ে", "চালচলন", "চালু", "চালডাল", "চালের", "চালাতে", "চালক",
        // ত & ট সম্পর্কিত ও বিকল্প রূপ
        "তা", "টা", "তার", "তাদের", "তাহলে", "তাই", "তাজা", "তাল", "তারিখ", "তাড়াতাড়ি", "তাপমাত্রা", "তাড়াহুড়ো",
        "টাকা", "টাকায়", "টাইম", "টাকার", "টাকাটা", "টুকরো", "টানা", "টাটকা", "টেলিফোন", "টেকনিক্যাল", "টেস্ট",
        // সাধারণ দৈনন্দিন শব্দ
        "বাংলাদেশ", "বাঙালি", "বাংলা", "বাণিজ্য", "ভালোবাসা", "ভালো", "ভালোই", "ভালোভাবে", "স্বাগতম", "অভিনন্দন", "শুভকামনা",
        "ধন্যবাদ", "ধন্যবাদ ভাই", "প্রয়োজন", "সমস্যা", "সমাধান", "যোগাযোগ", "পরিবার", "বিদ্যালয়", "অফিস", "ব্যবসা",
        "গুরুত্বপূর্ণ", "সহজ", "কঠিন", "দ্রুত", "ধীরে", "অনুগ্রহ", "অনুরোধ", "প্রতিদিন", "সাধারণ", "বিশেষ",
        "বর্তমান", "ভবিষ্যৎ", "সম্পর্ক", "বন্ধু", "ভাই", "আপু", "বোন", "বন্ধুগণ", "শ্রদ্ধেয়", "সম্মানিত",
        "খবর", "কেমন", "কোথায়", "কখন", "কীভাবে", "কেন", "কার", "কাদের", "সবাই", "সবাইকে",
        "সুন্দর", "চমৎকার", "অপূর্ব", "অসাধারণ", "অনবদ্য", "বিশ্বাস", "আস্থা", "নিরাপত্তা", "সুরক্ষা",
        "কথা", "বার্তা", "মেসেজ", "ইমেইল", "ফোন", "কল", "মিটিং", "কাজ", "কর্ম", "দায়িত্ব",
        "আজকে", "কালকে", "গতকাল", "আগামীকাল", "পরশু", "সকাল", "দুপুর", "বিকাল", "সন্ধ্যা", "রাত",
        "সময়", "ঘণ্টা", "মিনিট", "সেকেন্ড", "বছর", "মাস", "সপ্তাহ", "তারিখ", "দিন", "ছুটি",
        "শহর", "গ্রাম", "দেশ", "বিদেশ", "রাস্তা", "ঘাট", "গাড়ি", "বাস", "ট্রেন", "বিমান",
        "টাকা", "পয়সা", "হিসাব", "নিকাশ", "লাভ", "ক্ষতি", "দোকান", "বাজার", "কেনাকাটা", "বিক্রি",
        "পড়াশোনা", "বই", "খাতা", "কলম", "জ্ঞান", "বিজ্ঞান", "প্রযুক্তি", "মোবাইল", "কম্পিউটার", "ইন্টারনেট"
    )

    // Comprehensive High-frequency English Vocabulary
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
        "connect", "continue", "contact", "control", "condition", "confident", "confirm",
        "meeting", "message", "important", "problem", "solution", "project", "awesome",
        "tomorrow", "yesterday", "tonight", "today", "friend", "family", "office", "business",
        "call", "email", "send", "receive", "schedule", "update", "document", "keyboard"
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
     * Generate phonetic / orthographic alternate word candidates for Bengali
     * যেমন: 'তা' ইনপুট দিলে অল্টারনেট হিসেবে 'টা' জেনারেট করে
     */
    private fun getBanglaAlternatePrefixes(word: String): List<String> {
        if (word.isEmpty()) return emptyList()
        val firstChar = word[0]
        val alternates = banglaCharAlternates[firstChar] ?: return emptyList()
        val rest = word.substring(1)
        return alternates.map { "$it$rest" }
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
        val cleanWord = currentWord.trim()
        val cleanLower = cleanWord.lowercase()

        // 1. Emoji association check
        emojiMap[cleanLower]?.let { results.add(it) }

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
                    emojiMap[lastWord]?.let { results.add(it) }
                } else {
                    val lowerLast = lastWord.lowercase()
                    val lowerTwo = lastTwoWords.lowercase()

                    englishNextWordMap[lowerTwo]?.let { results.addAll(it) }
                    if (results.isEmpty()) {
                        englishNextWordMap[lowerLast]?.let { results.addAll(it) }
                    }
                    emojiMap[lowerLast]?.let { results.add(it) }
                }
            }
        } else {
            // 2. Direct exact candidate first
            results.add(cleanWord)

            // 3. Autocorrect match
            if (isBangla) {
                banglaAutocorrectMap[cleanWord]?.let { if (it != cleanWord) results.add(it) }
            } else {
                englishAutocorrectMap[cleanLower]?.let { if (it != cleanWord) results.add(it) }
            }

            // 4. Bangla Orthographic / Phonetic Alternates (যেমন: 'তা' দিলে 'টা')
            if (isBangla) {
                val altPrefixes = getBanglaAlternatePrefixes(cleanWord)
                for (alt in altPrefixes) {
                    results.add(alt)
                }
            }

            // 5. User learned words matching prefix
            val userMatches = userLearnedWords.filter { (w, _) ->
                w.lowercase().startsWith(cleanLower) && w.lowercase() != cleanLower
            }.entries.sortedByDescending { it.value }.map { it.key }
            results.addAll(userMatches.take(4))

            // 6. Vocabulary prefix and compound expansions (যেমন: 'চাল' -> 'চালতা', 'চালাক', 'চালানো')
            if (isBangla) {
                val directMatches = banglaVocabulary.filter { it.startsWith(cleanWord) && it != cleanWord }
                results.addAll(directMatches.take(8))

                // Also match vocabulary for alternate prefixes (যেমন: 'তা' এর বিপরীতে 'টা' দিয়ে 'টাকা', 'টাইম')
                val altPrefixes = getBanglaAlternatePrefixes(cleanWord)
                for (alt in altPrefixes) {
                    val altMatches = banglaVocabulary.filter { it.startsWith(alt) && it != alt }
                    results.addAll(altMatches.take(4))
                }
            } else {
                val matches = englishDictionary.filter { it.startsWith(cleanLower) && it != cleanLower }
                results.addAll(matches.take(8))
            }
        }

        // Return up to 12 distinct, high-precision candidates
        return results.distinct().take(12)
    }
}
