package com.smartvoices.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.smartvoices.keyboard.clipboard.ClipboardDbHelper
import com.smartvoices.keyboard.gemini.GeminiService
import com.smartvoices.keyboard.phonetic.PhoneticEngine
import com.smartvoices.keyboard.ui.SettingsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

class SmartKeyboardService : InputMethodService() {

    enum class TypingMode {
        ENGLISH,
        BANGLA,
        BANGLISH
    }

    private var currentMode = TypingMode.ENGLISH
    private var isShifted = false
    private var isListening = false
    private var speechRecognizer: SpeechRecognizer? = null

    private lateinit var clipboardDb: ClipboardDbHelper
    private lateinit var geminiService: GeminiService
    private lateinit var prefs: SharedPreferences
    private val serviceScope = CoroutineScope(Dispatchers.Main)

    private lateinit var rootLayout: LinearLayout
    private lateinit var toolbarLayout: LinearLayout
    private lateinit var keysLayout: LinearLayout
    private lateinit var clipboardDrawer: HorizontalScrollView
    private lateinit var clipboardRow: LinearLayout
    private lateinit var btnMic: Button
    private lateinit var btnMode: Button

    // Phonetic word buffer for Banglish mode
    private val phoneticBuffer = StringBuilder()

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("smart_keyboard_prefs", Context.MODE_PRIVATE)
        clipboardDb = ClipboardDbHelper(this)
        val apiKey = prefs.getString("gemini_api_key", "") ?: ""
        geminiService = GeminiService(apiKey)

        setupClipboardListener()
    }

    private fun setupClipboardListener() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.addPrimaryClipChangedListener {
            val clip = cm.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString()
                if (!text.isNullOrBlank()) {
                    clipboardDb.insertOrUpdateClip(text)
                    refreshClipboardDrawer()
                }
            }
        }
    }

    override fun onCreateInputView(): View {
        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F172A"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        buildToolbar()
        buildClipboardDrawer()
        buildKeyboards()

        return rootLayout
    }

    private fun buildToolbar() {
        toolbarLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#1E293B"))
            setPadding(12, 10, 12, 10)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // 1. Mic / Voice Button
        btnMic = createToolbarButton("🎙️ ভয়েস", Color.parseColor("#10B981")) {
            toggleVoiceInput()
        }
        toolbarLayout.addView(btnMic)

        // 2. Mode Switch (EN / BN / Banglish)
        btnMode = createToolbarButton(getModeLabel(), Color.parseColor("#334155")) {
            switchTypingMode()
        }
        toolbarLayout.addView(btnMode)

        // 3. Quick Translate Button
        val btnTranslate = createToolbarButton("🌐 অনুবাদ", Color.parseColor("#6366F1")) {
            performQuickTranslate()
        }
        toolbarLayout.addView(btnTranslate)

        // 4. Clipboard Toggle
        val btnClip = createToolbarButton("📋 ক্লিপ", Color.parseColor("#334155")) {
            toggleClipboardDrawer()
        }
        toolbarLayout.addView(btnClip)

        // 5. Settings
        val btnSettings = createToolbarButton("⚙️", Color.parseColor("#334155")) {
            val intent = Intent(this, SettingsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        }
        toolbarLayout.addView(btnSettings)

        rootLayout.addView(toolbarLayout)
    }

    private fun createToolbarButton(label: String, bgColor: Int, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(bgColor)
            setPadding(16, 6, 16, 6)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(6, 0, 6, 0)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun buildClipboardDrawer() {
        clipboardDrawer = HorizontalScrollView(this).apply {
            visibility = View.GONE
            setBackgroundColor(Color.parseColor("#1E293B"))
            setPadding(8, 8, 8, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                110
            )
        }

        clipboardRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        clipboardDrawer.addView(clipboardRow)
        rootLayout.addView(clipboardDrawer)
    }

    private fun toggleClipboardDrawer() {
        if (clipboardDrawer.visibility == View.VISIBLE) {
            clipboardDrawer.visibility = View.GONE
        } else {
            clipboardDrawer.visibility = View.VISIBLE
            refreshClipboardDrawer()
        }
    }

    private fun refreshClipboardDrawer() {
        clipboardRow.removeAllViews()
        val clips = clipboardDb.getAllClips().take(15)
        if (clips.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "কোনো ক্লিপ পাওয়া যায়নি"
                setTextColor(Color.GRAY)
                textSize = 12f
                setPadding(16, 16, 16, 16)
            }
            clipboardRow.addView(emptyTv)
            return
        }

        for (clip in clips) {
            val btn = Button(this).apply {
                text = if (clip.text.length > 25) clip.text.take(25) + "..." else clip.text
                textSize = 11f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#334155"))
                setPadding(16, 8, 16, 8)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    setMargins(6, 0, 6, 0)
                }
                setOnClickListener {
                    currentInputConnection?.commitText(clip.text, 1)
                }
            }
            clipboardRow.addView(btn)
        }
    }

    private fun buildKeyboards() {
        if (::keysLayout.isInitialized) {
            rootLayout.removeView(keysLayout)
        }

        keysLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(4, 8, 4, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val rows = when (currentMode) {
            TypingMode.ENGLISH, TypingMode.BANGLISH -> getEnglishKeyRows()
            TypingMode.BANGLA -> getBanglaKeyRows()
        }

        for (rowKeys in rows) {
            val rowView = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    130
                ).apply {
                    setMargins(0, 4, 0, 4)
                }
            }

            for (key in rowKeys) {
                val keyBtn = Button(this).apply {
                    text = if (isShifted) key.uppercase() else key
                    textSize = 17f
                    setTextColor(Color.WHITE)
                    setBackgroundColor(getKeyColor(key))
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        getKeyWeight(key)
                    ).apply {
                        setMargins(3, 2, 3, 2)
                    }
                    setOnClickListener {
                        handleKeyPress(key)
                    }
                }
                rowView.addView(keyBtn)
            }
            keysLayout.addView(rowView)
        }

        rootLayout.addView(keysLayout)
    }

    private fun getKeyColor(key: String): Int {
        return when (key) {
            "⌫", "⇧", "123", "ABC" -> Color.parseColor("#475569")
            "↵" -> Color.parseColor("#10B981")
            "Space" -> Color.parseColor("#334155")
            else -> Color.parseColor("#1E293B")
        }
    }

    private fun getKeyWeight(key: String): Float {
        return when (key) {
            "Space" -> 4.5f
            "⌫", "⇧", "↵" -> 1.5f
            else -> 1.0f
        }
    }

    private fun handleKeyPress(key: String) {
        val ic = currentInputConnection ?: return

        when (key) {
            "⌫" -> {
                if (currentMode == TypingMode.BANGLISH && phoneticBuffer.isNotEmpty()) {
                    phoneticBuffer.deleteCharAt(phoneticBuffer.length - 1)
                }
                ic.deleteSurroundingText(1, 0)
            }
            "↵" -> {
                flushPhoneticBuffer()
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER))
                ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER))
            }
            "⇧" -> {
                isShifted = !isShifted
                buildKeyboards()
            }
            "Space" -> {
                if (currentMode == TypingMode.BANGLISH && phoneticBuffer.isNotEmpty()) {
                    val transliterated = PhoneticEngine.transliterateWord(phoneticBuffer.toString())
                    // Delete the roman letters previously typed
                    ic.deleteSurroundingText(phoneticBuffer.length, 0)
                    // Commit authentic Bengali unicode
                    ic.commitText(transliterated + " ", 1)
                    phoneticBuffer.clear()
                } else {
                    ic.commitText(" ", 1)
                }
            }
            else -> {
                val charToCommit = if (isShifted) key.uppercase() else key
                if (currentMode == TypingMode.BANGLISH) {
                    phoneticBuffer.append(charToCommit)
                    ic.commitText(charToCommit, 1)
                } else {
                    ic.commitText(charToCommit, 1)
                }
            }
        }
    }

    private fun flushPhoneticBuffer() {
        if (currentMode == TypingMode.BANGLISH && phoneticBuffer.isNotEmpty()) {
            val ic = currentInputConnection ?: return
            val transliterated = PhoneticEngine.transliterateWord(phoneticBuffer.toString())
            ic.deleteSurroundingText(phoneticBuffer.length, 0)
            ic.commitText(transliterated, 1)
            phoneticBuffer.clear()
        }
    }

    private fun switchTypingMode() {
        flushPhoneticBuffer()
        currentMode = when (currentMode) {
            TypingMode.ENGLISH -> TypingMode.BANGLISH
            TypingMode.BANGLISH -> TypingMode.BANGLA
            TypingMode.BANGLA -> TypingMode.ENGLISH
        }
        btnMode.text = getModeLabel()
        buildKeyboards()
    }

    private fun getModeLabel(): String {
        return when (currentMode) {
            TypingMode.ENGLISH -> "⌨️ EN"
            TypingMode.BANGLISH -> "⌨️ বাংলিশ"
            TypingMode.BANGLA -> "⌨️ বাংলা"
        }
    }

    private fun performQuickTranslate() {
        val ic = currentInputConnection ?: return
        val selectedText = ic.getSelectedText(0)?.toString()
        val textToTranslate = if (!selectedText.isNullOrBlank()) {
            selectedText
        } else {
            // Get text before cursor
            ic.getTextBeforeCursor(200, 0)?.toString() ?: ""
        }

        if (textToTranslate.isBlank()) {
            Toast.makeText(this, "অনুবাদ করার জন্য কিছু লিখুন বা সিলেক্ট করুন", Toast.LENGTH_SHORT).show()
            return
        }

        val isEnglish = textToTranslate.any { it in 'a'..'z' || it in 'A'..'Z' }
        val sourceLang = if (isEnglish) "en" else "bn"
        val targetLang = if (isEnglish) "bn" else "en"

        Toast.makeText(this, "Gemini অনুবাদ হচ্ছে...", Toast.LENGTH_SHORT).show()

        serviceScope.launch {
            val result = geminiService.translateText(textToTranslate, sourceLang, targetLang)
            result.onSuccess { translated ->
                if (!selectedText.isNullOrBlank()) {
                    ic.commitText(translated, 1)
                } else {
                    ic.deleteSurroundingText(textToTranslate.length, 0)
                    ic.commitText(translated, 1)
                }
            }.onFailure { err ->
                Toast.makeText(this@SmartKeyboardService, "ত্রুটি: ${err.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun toggleVoiceInput() {
        if (isListening) {
            stopVoiceListening()
        } else {
            startVoiceListening()
        }
    }

    private fun startVoiceListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "স্পিচ রিকগনিশন অ্যান্ড্রয়েডে সক্রিয় নয়", Toast.LENGTH_SHORT).show()
            return
        }

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        stopVoiceListening()
                    }

                    override fun onError(error: Int) {
                        stopVoiceListening()
                        Toast.makeText(this@SmartKeyboardService, "Voice Error: $error", Toast.LENGTH_SHORT).show()
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val recognized = matches[0]
                            currentInputConnection?.commitText(recognized + " ", 1)
                        }
                        stopVoiceListening()
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }

        val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            val langCode = if (currentMode == TypingMode.ENGLISH) "en-US" else "bn-BD"
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langCode)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "কথা বলুন...")
        }

        try {
            speechRecognizer?.startListening(speechIntent)
            isListening = true
            btnMic.text = "🔴 শুনছি..."
            btnMic.setBackgroundColor(Color.parseColor("#EF4444"))
        } catch (e: Exception) {
            Toast.makeText(this, "Voice error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopVoiceListening() {
        isListening = false
        btnMic.text = "🎙️ ভয়েস"
        btnMic.setBackgroundColor(Color.parseColor("#10B981"))
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {}
    }

    private fun getEnglishKeyRows(): List<List<String>> {
        return listOf(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
            listOf("⇧", "z", "x", "c", "v", "b", "n", "m", "⌫"),
            listOf("123", ",", "Space", ".", "↵")
        )
    }

    private fun getBanglaKeyRows(): List<List<String>> {
        return listOf(
            listOf("১", "২", "৩", "৪", "৫", "৬", "৭", "৮", "৯", "০"),
            listOf("ক", "খ", "গ", "ঘ", "ঙ", "চ", "ছ", "জ", "ঝ", "ঞ"),
            listOf("ট", "ঠ", "ড", "ঢ", "ণ", "ত", "থ", "দ", "ধ", "ন"),
            listOf("প", "ফ", "ব", "ভ", "ম", "য", "র", "ল", "শ", "⌫"),
            listOf("া", "ি", "ী", "ু", "ে", "Space", "্", "।", "↵")
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
    }
}
