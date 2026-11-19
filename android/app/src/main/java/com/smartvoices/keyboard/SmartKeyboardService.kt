package com.smartvoices.keyboard

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.MotionEvent
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

class SmartKeyboardService : InputMethodService() {

    enum class TypingMode {
        ENGLISH,
        BANGLA,
        BANGLISH
    }

    data class KeyboardTheme(
        val name: String,
        val background: Int,
        val toolbarBg: Int,
        val keyBg: Int,
        val specialKeyBg: Int,
        val actionKeyBg: Int,
        val textColor: Int,
        val accentColor: Int
    )

    private val themes = mapOf(
        "dark" to KeyboardTheme(
            name = "Dark Slate",
            background = Color.parseColor("#0F172A"),
            toolbarBg = Color.parseColor("#1E293B"),
            keyBg = Color.parseColor("#1E293B"),
            specialKeyBg = Color.parseColor("#334155"),
            actionKeyBg = Color.parseColor("#10B981"),
            textColor = Color.parseColor("#F8FAFC"),
            accentColor = Color.parseColor("#10B981")
        ),
        "amoled" to KeyboardTheme(
            name = "AMOLED Black",
            background = Color.parseColor("#000000"),
            toolbarBg = Color.parseColor("#121212"),
            keyBg = Color.parseColor("#1E1E1E"),
            specialKeyBg = Color.parseColor("#2C2C2C"),
            actionKeyBg = Color.parseColor("#059669"),
            textColor = Color.parseColor("#FFFFFF"),
            accentColor = Color.parseColor("#10B981")
        ),
        "light" to KeyboardTheme(
            name = "Modern Light",
            background = Color.parseColor("#F1F5F9"),
            toolbarBg = Color.parseColor("#E2E8F0"),
            keyBg = Color.parseColor("#FFFFFF"),
            specialKeyBg = Color.parseColor("#CBD5E1"),
            actionKeyBg = Color.parseColor("#10B981"),
            textColor = Color.parseColor("#0F172A"),
            accentColor = Color.parseColor("#10B981")
        ),
        "cyber" to KeyboardTheme(
            name = "Cyber Neon",
            background = Color.parseColor("#0B0F19"),
            toolbarBg = Color.parseColor("#111827"),
            keyBg = Color.parseColor("#1F2937"),
            specialKeyBg = Color.parseColor("#374151"),
            actionKeyBg = Color.parseColor("#6366F1"),
            textColor = Color.parseColor("#E0E7FF"),
            accentColor = Color.parseColor("#6366F1")
        )
    )

    private var currentMode = TypingMode.ENGLISH
    private var isShifted = false
    private var isListening = false
    private var speechRecognizer: SpeechRecognizer? = null

    private lateinit var clipboardDb: ClipboardDbHelper
    private lateinit var geminiService: GeminiService
    private lateinit var prefs: SharedPreferences
    private lateinit var audioManager: AudioManager
    private var vibrator: Vibrator? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)

    private lateinit var rootLayout: LinearLayout
    private lateinit var toolbarLayout: LinearLayout
    private lateinit var suggestionBar: LinearLayout
    private lateinit var keysLayout: LinearLayout
    private lateinit var clipboardDrawer: HorizontalScrollView
    private lateinit var clipboardRow: LinearLayout
    private lateinit var btnMic: Button
    private lateinit var btnMode: Button

    // Phonetic word buffer for Banglish mode
    private val phoneticBuffer = StringBuilder()

    // Key secondary mappings for long-press
    private val secondaryKeyMap = mapOf(
        "q" to "1", "w" to "2", "e" to "3", "r" to "4", "t" to "5",
        "y" to "6", "u" to "7", "i" to "8", "o" to "9", "p" to "0",
        "a" to "@", "s" to "#", "d" to "$", "f" to "%", "g" to "&",
        "h" to "-", "j" to "+", "k" to "(", "l" to ")",
        "z" to "*", "x" to "\"", "c" to "'", "v" to ":", "b" to ";",
        "n" to "!", "m" to "?", "." to "?"
    )

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("smart_keyboard_prefs", Context.MODE_PRIVATE)
        clipboardDb = ClipboardDbHelper(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val apiKey = prefs.getString("gemini_api_key", "") ?: ""
        geminiService = GeminiService(apiKey)

        val defaultModePref = prefs.getString("default_mode", "english")
        currentMode = when (defaultModePref) {
            "banglish" -> TypingMode.BANGLISH
            "bangla" -> TypingMode.BANGLA
            else -> TypingMode.ENGLISH
        }

        setupClipboardListener()
    }

    private fun getCurrentTheme(): KeyboardTheme {
        val themeKey = prefs.getString("theme", "dark") ?: "dark"
        return themes[themeKey] ?: themes["dark"]!!
    }

    private fun setupClipboardListener() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.addPrimaryClipChangedListener {
            val clip = cm.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString()
                if (!text.isNullOrBlank()) {
                    clipboardDb.insertOrUpdateClip(text)
                    if (::clipboardDrawer.isInitialized && clipboardDrawer.visibility == View.VISIBLE) {
                        refreshClipboardDrawer()
                    }
                }
            }
        }
    }

    override fun onCreateInputView(): View {
        val currentTheme = getCurrentTheme()

        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(currentTheme.background)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        buildToolbar(currentTheme)
        buildSuggestionBar(currentTheme)
        buildClipboardDrawer(currentTheme)
        buildKeyboards()

        return rootLayout
    }

    private fun buildToolbar(theme: KeyboardTheme) {
        toolbarLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(theme.toolbarBg)
            setPadding(10, 8, 10, 8)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // 1. Mic / Voice Button
        btnMic = createToolbarButton("🎙️ ভয়েস", theme.actionKeyBg, theme.textColor) {
            toggleVoiceInput()
        }
        toolbarLayout.addView(btnMic)

        // 2. Mode Switch (EN / BN / Banglish)
        btnMode = createToolbarButton(getModeLabel(), theme.specialKeyBg, theme.textColor) {
            switchTypingMode()
        }
        toolbarLayout.addView(btnMode)

        // 3. Quick Translate Button
        val btnTranslate = createToolbarButton("🌐 অনুবাদ", theme.accentColor, Color.WHITE) {
            performQuickTranslate()
        }
        toolbarLayout.addView(btnTranslate)

        // 4. Clipboard Toggle
        val btnClip = createToolbarButton("📋 ক্লিপবোর্ড", theme.specialKeyBg, theme.textColor) {
            toggleClipboardDrawer()
        }
        toolbarLayout.addView(btnClip)

        // 5. Settings Activity
        val btnSettings = createToolbarButton("⚙️", theme.specialKeyBg, theme.textColor) {
            val intent = Intent(this, SettingsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        }
        toolbarLayout.addView(btnSettings)

        rootLayout.addView(toolbarLayout)
    }

    private fun buildSuggestionBar(theme: KeyboardTheme) {
        suggestionBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            setBackgroundColor(theme.toolbarBg)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 6, 12, 6)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(suggestionBar)
    }

    private fun updateSuggestions() {
        if (currentMode != TypingMode.BANGLISH || phoneticBuffer.isEmpty()) {
            suggestionBar.visibility = View.GONE
            suggestionBar.removeAllViews()
            return
        }

        suggestionBar.visibility = View.VISIBLE
        suggestionBar.removeAllViews()
        val currentTheme = getCurrentTheme()

        val rawInput = phoneticBuffer.toString()
        val transliterated = PhoneticEngine.transliterateWord(rawInput)

        val suggestions = listOf(transliterated, rawInput)

        for (cand in suggestions) {
            val chip = Button(this).apply {
                text = cand
                textSize = 13f
                setTextColor(currentTheme.textColor)
                setBackgroundColor(currentTheme.specialKeyBg)
                setPadding(20, 6, 20, 6)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(6, 0, 6, 0)
                }
                setOnClickListener {
                    triggerHapticAndSound()
                    val ic = currentInputConnection ?: return@setOnClickListener
                    ic.deleteSurroundingText(phoneticBuffer.length, 0)
                    ic.commitText("$cand ", 1)
                    phoneticBuffer.clear()
                    updateSuggestions()
                }
            }
            suggestionBar.addView(chip)
        }
    }

    private fun createToolbarButton(label: String, bgColor: Int, textColor: Int, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 12f
            setTextColor(textColor)
            setBackgroundColor(bgColor)
            setPadding(14, 4, 14, 4)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(4, 0, 4, 0)
            }
            setOnClickListener {
                triggerHapticAndSound()
                onClick()
            }
        }
    }

    private fun buildClipboardDrawer(theme: KeyboardTheme) {
        clipboardDrawer = HorizontalScrollView(this).apply {
            visibility = View.GONE
            setBackgroundColor(theme.background)
            setPadding(6, 6, 6, 6)
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
        val currentTheme = getCurrentTheme()
        val clips = clipboardDb.getAllClips().take(20)

        if (clips.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "ক্লিপবোর্ডে কোনো ডাটা সংরক্ষিত নেই"
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
                setTextColor(currentTheme.textColor)
                setBackgroundColor(if (clip.isPinned) currentTheme.accentColor else currentTheme.keyBg)
                setPadding(14, 6, 14, 6)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    setMargins(5, 0, 5, 0)
                }
                setOnClickListener {
                    triggerHapticAndSound()
                    currentInputConnection?.commitText(clip.text, 1)
                }
                setOnLongClickListener {
                    triggerHapticAndSound()
                    clipboardDb.togglePin(clip.id, !clip.isPinned)
                    refreshClipboardDrawer()
                    true
                }
            }
            clipboardRow.addView(btn)
        }
    }

    private fun buildKeyboards() {
        if (::keysLayout.isInitialized) {
            rootLayout.removeView(keysLayout)
        }

        val theme = getCurrentTheme()

        keysLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.background)
            setPadding(4, 6, 4, 12)
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
                    125
                ).apply {
                    setMargins(0, 3, 0, 3)
                }
            }

            for (key in rowKeys) {
                val keyBtn = Button(this).apply {
                    text = if (isShifted) key.uppercase() else key
                    textSize = 17f
                    setTextColor(theme.textColor)
                    setBackgroundColor(getKeyColor(key, theme))
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        getKeyWeight(key)
                    ).apply {
                        setMargins(2, 2, 2, 2)
                    }

                    // Click listener
                    setOnClickListener {
                        triggerHapticAndSound()
                        handleKeyPress(key)
                    }

                    // Long-press listener for symbols & numbers
                    setOnLongClickListener {
                        triggerHapticAndSound()
                        handleKeyLongPress(key)
                        true
                    }
                }
                rowView.addView(keyBtn)
            }
            keysLayout.addView(rowView)
        }

        rootLayout.addView(keysLayout)
    }

    private fun getKeyColor(key: String, theme: KeyboardTheme): Int {
        return when (key) {
            "⌫", "⇧", "123", "ABC" -> theme.specialKeyBg
            "↵" -> theme.actionKeyBg
            "Space" -> theme.keyBg
            else -> theme.keyBg
        }
    }

    private fun getKeyWeight(key: String): Float {
        return when (key) {
            "Space" -> 4.5f
            "⌫", "⇧", "↵" -> 1.5f
            else -> 1.0f
        }
    }

    private fun triggerHapticAndSound() {
        val enableVibration = prefs.getBoolean("enable_vibration", true)
        val enableSound = prefs.getBoolean("enable_sound", true)

        if (enableVibration && vibrator != null && vibrator!!.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(20)
            }
        }

        if (enableSound) {
            audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD)
        }
    }

    private fun handleKeyPress(key: String) {
        val ic = currentInputConnection ?: return

        when (key) {
            "⌫" -> {
                if (currentMode == TypingMode.BANGLISH && phoneticBuffer.isNotEmpty()) {
                    phoneticBuffer.deleteCharAt(phoneticBuffer.length - 1)
                    updateSuggestions()
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
                    ic.deleteSurroundingText(phoneticBuffer.length, 0)
                    ic.commitText("$transliterated ", 1)
                    phoneticBuffer.clear()
                    updateSuggestions()
                } else {
                    ic.commitText(" ", 1)
                }
            }
            else -> {
                val charToCommit = if (isShifted) key.uppercase() else key
                if (currentMode == TypingMode.BANGLISH) {
                    phoneticBuffer.append(charToCommit)
                    ic.commitText(charToCommit, 1)
                    updateSuggestions()
                } else {
                    ic.commitText(charToCommit, 1)
                }
            }
        }
    }

    private fun handleKeyLongPress(key: String) {
        val ic = currentInputConnection ?: return
        val lower = key.lowercase()

        if (secondaryKeyMap.containsKey(lower)) {
            val symbol = secondaryKeyMap[lower]!!
            if (currentMode == TypingMode.BANGLISH && phoneticBuffer.isNotEmpty()) {
                phoneticBuffer.append(symbol)
                updateSuggestions()
            }
            ic.commitText(symbol, 1)
        } else if (key == "⌫") {
            // Rapid erase on long backspace
            ic.deleteSurroundingText(phoneticBuffer.length.coerceAtLeast(5), 0)
            phoneticBuffer.clear()
            updateSuggestions()
        }
    }

    private fun flushPhoneticBuffer() {
        if (currentMode == TypingMode.BANGLISH && phoneticBuffer.isNotEmpty()) {
            val ic = currentInputConnection ?: return
            val transliterated = PhoneticEngine.transliterateWord(phoneticBuffer.toString())
            ic.deleteSurroundingText(phoneticBuffer.length, 0)
            ic.commitText(transliterated, 1)
            phoneticBuffer.clear()
            updateSuggestions()
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
        updateSuggestions()
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
            ic.getTextBeforeCursor(250, 0)?.toString() ?: ""
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
                Toast.makeText(this@SmartKeyboardService, "অনুবাদ ত্রুটি: ${err.message}", Toast.LENGTH_LONG).show()
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
                            currentInputConnection?.commitText("$recognized ", 1)
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
        val theme = getCurrentTheme()
        btnMic.text = "🎙️ ভয়েস"
        btnMic.setBackgroundColor(theme.actionKeyBg)
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
