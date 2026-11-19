package com.smartvoices.keyboard.phonetic

/**
 * Android Bengali Phonetic Transliteration Engine
 * Transforms Romanized English phonetic tokens into authentic Bengali Unicode.
 */
object PhoneticEngine {

    private val commonWords = mapOf(
        "ami" to "আমি",
        "amra" to "আমরা",
        "amader" to "আমাদের",
        "tumi" to "তুমি",
        "tomra" to "তোমরা",
        "tomader" to "তোমাদের",
        "apni" to "আপনি",
        "apnara" to "আপনারা",
        "apnader" to "আপনাদের",
        "she" to "সে",
        "tara" to "তারা",
        "ei" to "এই",
        "oi" to "ওই",
        "shei" to "সেই",
        "ki" to "কী",
        "kemon" to "কেমন",
        "kothay" to "কোথায়",
        "kokhon" to "কখন",
        "keno" to "কেন",
        "achhi" to "আছি",
        "achhen" to "আছেন",
        "achho" to "আছো",
        "bhalo" to "ভালো",
        "dhonnobad" to "ধন্যবাদ",
        "bangla" to "বাংলা",
        "bangladesh" to "বাংলাদেশ",
        "shonar" to "সোনার",
        "bhalobashi" to "ভালোবাসি",
        "shobai" to "সবাই"
    )

    private val vowelMap = mapOf(
        "a" to "আ", "aa" to "আ", "i" to "ই", "ee" to "ঈ", "I" to "ঈ",
        "u" to "উ", "oo" to "ঊ", "U" to "ঊ", "e" to "এ", "oi" to "ঐ",
        "o" to "ও", "ou" to "ঔ", "rri" to "ঋ"
    )

    private val karMap = mapOf(
        "a" to "া", "aa" to "া", "i" to "ি", "ee" to "ী", "I" to "ী",
        "u" to "ু", "oo" to "ূ", "U" to "ূ", "e" to "ে", "oi" to "ৈ",
        "o" to "ো", "ou" to "ৌ"
    )

    private val consonantMap = mapOf(
        "k" to "ক", "kh" to "খ", "g" to "গ", "gh" to "ঘ", "ng" to "ঙ",
        "c" to "চ", "ch" to "ছ", "j" to "জ", "jh" to "ঝ", "T" to "ট",
        "Th" to "ঠ", "D" to "ড", "Dh" to "ঢ", "N" to "ণ", "t" to "ত",
        "th" to "থ", "d" to "দ", "dh" to "ধ", "n" to "ন", "p" to "প",
        "ph" to "ফ", "f" to "ফ", "b" to "ব", "bh" to "ভ", "v" to "ভ",
        "m" to "ম", "z" to "য", "r" to "র", "l" to "ল", "sh" to "শ",
        "Sh" to "ষ", "s" to "স", "h" to "হ", "R" to "ড়", "Rh" to "ঢ়",
        "y" to "য়"
    )

    private val conjunctMap = mapOf(
        "kkh" to "ক্ষ", "kt" to "ক্ত", "kn" to "ক্ন", "km" to "ক্ম",
        "ky" to "ক্য", "kr" to "ক্র", "kl" to "ক্ল", "gdh" to "গ্ধ",
        "gn" to "গ্ন", "gm" to "গ্ম", "gy" to "গ্য", "gr" to "গ্র",
        "chch" to "চ্চ", "chchh" to "চ্ছ", "jj" to "জ্জ", "jgn" to "জ্ঞ",
        "tt" to "ত্ত", "tth" to "ত্থ", "tm" to "ত্ম", "ty" to "ত্য",
        "tr" to "ত্র", "dd" to "দ্দ", "ddh" to "দ্ধ", "dv" to "দ্ব",
        "db" to "দ্ব", "dhy" to "ধ্য", "dhr" to "ধ্র", "nt" to "ন্ত",
        "nth" to "ন্থ", "nd" to "ন্দ", "ndh" to "ন্ধ", "nn" to "ন্ন",
        "pt" to "প্ত", "pp" to "প্প", "pl" to "প্ল", "bd" to "ব্দ",
        "bb" to "ব্ব", "bl" to "ব্ল", "by" to "ব্য", "br" to "ব্র",
        "mp" to "ম্প", "mb" to "ম্ব", "mbh" to "ম্ভ", "mm" to "ম্ম",
        "my" to "ম্য", "mr" to "ম্র", "st" to "স্ত", "sth" to "স্থ",
        "sp" to "স্প", "sn" to "স্ন", "sm" to "স্ম", "sy" to "স্য",
        "sr" to "স্র", "shch" to "শ্চ", "shn" to "শ্ন", "shm" to "শ্ম",
        "shy" to "শ্য", "shr" to "শ্র"
    )

    fun transliterateWord(rawWord: String): String {
        if (rawWord.isEmpty()) return ""
        val lower = rawWord.lowercase()
        if (commonWords.containsKey(lower)) {
            return commonWords[lower]!!
        }

        val result = StringBuilder()
        var i = 0
        val len = rawWord.length
        var isPrevConsonant = false

        while (i < len) {
            // Conjuncts match
            var matchedConjunct = false
            for (clen in 4 downTo 2) {
                if (i + clen <= len) {
                    val sub = rawWord.substring(i, i + clen)
                    if (conjunctMap.containsKey(sub)) {
                        result.append(conjunctMap[sub])
                        i += clen
                        isPrevConsonant = true
                        matchedConjunct = true
                        break
                    }
                }
            }
            if (matchedConjunct) continue

            // 2-char consonant
            if (i + 2 <= len) {
                val sub = rawWord.substring(i, i + 2)
                if (consonantMap.containsKey(sub)) {
                    result.append(consonantMap[sub])
                    i += 2
                    isPrevConsonant = true
                    continue
                }
            }

            // 1-char consonant
            val single = rawWord[i].toString()
            if (consonantMap.containsKey(single)) {
                result.append(consonantMap[single])
                i += 1
                isPrevConsonant = true
                continue
            }

            // Vowels
            var matchedVowel = false
            for (vlen in 2 downTo 1) {
                if (i + vlen <= len) {
                    val sub = rawWord.substring(i, i + vlen).lowercase()
                    if (isPrevConsonant && karMap.containsKey(sub)) {
                        result.append(karMap[sub])
                        i += vlen
                        isPrevConsonant = false
                        matchedVowel = true
                        break
                    } else if (!isPrevConsonant && vowelMap.containsKey(sub)) {
                        result.append(vowelMap[sub])
                        i += vlen
                        isPrevConsonant = false
                        matchedVowel = true
                        break
                    }
                }
            }
            if (matchedVowel) continue

            // Inherent vowel fallback
            if (single == "o" || single == "O") {
                if (!isPrevConsonant) result.append("অ")
                isPrevConsonant = false
                i += 1
                continue
            }

            result.append(single)
            isPrevConsonant = false
            i += 1
        }

        return result.toString()
    }

    fun transliterateSentence(text: String): String {
        val parts = text.split(" ")
        return parts.joinToString(" ") { token ->
            if (token.all { it.isLetter() }) transliterateWord(token) else token
        }
    }
}
