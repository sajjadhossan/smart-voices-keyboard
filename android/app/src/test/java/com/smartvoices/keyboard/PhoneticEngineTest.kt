package com.smartvoices.keyboard

import com.smartvoices.keyboard.phonetic.PhoneticEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneticEngineTest {

    @Test
    fun testCommonWordsTransliteration() {
        assertEquals("আমি", PhoneticEngine.transliterateWord("ami"))
        assertEquals("বাংলা", PhoneticEngine.transliterateWord("bangla"))
        assertEquals("ধন্যবাদ", PhoneticEngine.transliterateWord("dhonnobad"))
        assertEquals("বাংলাদেশ", PhoneticEngine.transliterateWord("bangladesh"))
    }

    @Test
    fun testSentenceTransliteration() {
        val sentence = "ami bangla bhalobashi"
        val result = PhoneticEngine.transliterateSentence(sentence)
        assertTrue(result.contains("আমি"))
        assertTrue(result.contains("বাংলা"))
        assertTrue(result.contains("ভালোবাসি"))
    }

    @Test
    fun testConjunctsTransliteration() {
        val result = PhoneticEngine.transliterateWord("kkh")
        assertEquals("ক্ষ", result)
    }
}
