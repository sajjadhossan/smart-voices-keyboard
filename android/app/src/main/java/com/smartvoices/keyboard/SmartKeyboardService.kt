package com.smartvoices.keyboard

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import androidx.core.content.ContextCompat
import com.smartvoices.keyboard.clipboard.ClipEntry
import com.smartvoices.keyboard.clipboard.ClipboardDbHelper
import com.smartvoices.keyboard.gemini.GeminiService
import com.smartvoices.keyboard.phonetic.PhoneticEngine
import com.smartvoices.keyboard.prediction.PredictionEngine
import com.smartvoices.keyboard.ui.SettingsActivity
import com.smartvoices.keyboard.ui.VoiceInputDialogActivity
import com.smartvoices.keyboard.ui.VoicePermissionActivity
import com.smartvoices.keyboard.voice.GeminiAudioRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmartKeyboardService : InputMethodService() {

    enum class TypingMode {
        ENGLISH,
        BANGLA,
        BANGLISH
    }

    enum class ActiveViewMode {
        KEYBOARD,
        CLIPBOARD
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
        ),
        "ocean" to KeyboardTheme(
            name = "Ocean Blue",
            background = Color.parseColor("#0C1A30"),
            toolbarBg = Color.parseColor("#152C4F"),
            keyBg = Color.parseColor("#1A365D"),
            specialKeyBg = Color.parseColor("#2A4365"),
            actionKeyBg = Color.parseColor("#0284C7"),
            textColor = Color.parseColor("#F0F9FF"),
            accentColor = Color.parseColor("#38BDF8")
        ),
        "forest" to KeyboardTheme(
            name = "Forest Emerald",
            background = Color.parseColor("#06281E"),
            toolbarBg = Color.parseColor("#0A3D2E"),
            keyBg = Color.parseColor("#0F4D3A"),
            specialKeyBg = Color.parseColor("#135E47"),
            actionKeyBg = Color.parseColor("#10B981"),
            textColor = Color.parseColor("#ECFDF5"),
            accentColor = Color.parseColor("#34D399")
        ),
        "sunset" to KeyboardTheme(
            name = "Sunset Flame",
            background = Color.parseColor("#29120B"),
            toolbarBg = Color.parseColor("#3E1C12"),
            keyBg = Color.parseColor("#542417"),
            specialKeyBg = Color.parseColor("#6A2F1F"),
            actionKeyBg = Color.parseColor("#F97316"),
            textColor = Color.parseColor("#FFF7ED"),
            accentColor = Color.parseColor("#FB923C")
        )
    )

    private var currentMode = TypingMode.ENGLISH
    private var activeView = ActiveViewMode.KEYBOARD
    private var isShifted = false
    private var isListening = false
    private var isGeminiVoiceActive = false
    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var geminiAudioRecorder: GeminiAudioRecorder

    // Gboard-Style Live Inline Translation
    private var isLiveTranslateActive = false
    private var liveTranslateSourceLang = "bn"
    private var liveTranslateTargetLang = "en"
    private val liveTranslateBuffer = StringBuilder()
    private var lastTranslatedPreview = ""
    private var liveTranslateDebounceJob: kotlinx.coroutines.Job? = null
    private lateinit var liveTranslateBar: LinearLayout
    private lateinit var tvLiveTranslatePreview: TextView
    private lateinit var btnLiveTranslateLangPill: Button

    private lateinit var clipboardDb: ClipboardDbHelper
    private lateinit var geminiService: GeminiService
    private lateinit var prefs: SharedPreferences
    private lateinit var audioManager: AudioManager
    private var vibrator: Vibrator? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var rootLayout: LinearLayout
    private lateinit var toolbarLayout: LinearLayout
    private lateinit var suggestionBar: LinearLayout
    private lateinit var contentContainer: FrameLayout
    private lateinit var keysLayout: LinearLayout
    private lateinit var clipboardPanel: LinearLayout
    private lateinit var btnMic: Button
    private lateinit var btnVoiceLang: Button
    private lateinit var btnMode: Button
    private lateinit var btnTranslate: Button
    private var explicitVoiceLang: String? = null

    private fun getEffectiveVoiceLang(): String {
        return explicitVoiceLang ?: if (currentMode == TypingMode.ENGLISH) "en" else "bn"
    }

    private fun toggleVoiceLanguage() {
        explicitVoiceLang = if (getEffectiveVoiceLang() == "bn") "en" else "bn"
        if (::btnVoiceLang.isInitialized) {
            btnVoiceLang.text = if (getEffectiveVoiceLang() == "en") "🇺🇸 EN" else "🇧🇩 BN"
        }
        val name = if (getEffectiveVoiceLang() == "en") "English (en-US)" else "বাংলা (bn-BD)"
        Toast.makeText(this, "ভয়েস ভাষা: $name 🎙️", Toast.LENGTH_SHORT).show()
    }

    // Phonetic word buffer for Banglish mode
    private val phoneticBuffer = StringBuilder()

    // Secondary key mappings for long-press
    private val secondaryKeyMap = mapOf(
        "q" to "1", "w" to "2", "e" to "3", "r" to "4", "t" to "5",
        "y" to "6", "u" to "7", "i" to "8", "o" to "9", "p" to "0",
        "a" to "@", "s" to "#", "d" to "$", "f" to "%", "g" to "&",
        "h" to "-", "j" to "+", "k" to "(", "l" to ")",
        "z" to "*", "x" to "\"", "c" to "'", "v" to ":", "b" to ";",
        "n" to "!", "m" to "?", "." to "?"
    )

    // Broadcast receiver for dynamic settings updates and runtime permissions
    private val keyboardReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                SettingsActivity.ACTION_SETTINGS_UPDATED -> {
                    val keyFromIntent = intent.getStringExtra("gemini_api_key")
                    val effectiveKey = if (!keyFromIntent.isNullOrBlank()) {
                        keyFromIntent
                    } else {
                        prefs.getString("gemini_api_key", "") ?: ""
                    }
                    geminiService = GeminiService(effectiveKey)
                    if (::rootLayout.isInitialized) {
                        rebuildEntireUI()
                    }
                }
                VoicePermissionActivity.ACTION_PERMISSION_RESULT -> {
                    val granted = intent.getBooleanExtra("granted", false)
                    if (granted) {
                        startVoiceListening()
                    }
                }
                VoiceInputDialogActivity.ACTION_VOICE_RESULT -> {
                    val voiceText = intent.getStringExtra(VoiceInputDialogActivity.EXTRA_VOICE_TEXT)
                    if (!voiceText.isNullOrBlank()) {
                        val voiceResult = com.smartvoices.keyboard.voice.VoiceCommandProcessor.processVoiceText(voiceText, getEffectiveVoiceLang())
                        if (voiceResult.isCommand) {
                            handleVoiceCommandResult(voiceResult)
                        } else {
                            currentInputConnection?.commitText("${voiceResult.finalText} ", 1)
                        }
                        stopVoiceListening()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("smart_keyboard_prefs", Context.MODE_PRIVATE)
        clipboardDb = ClipboardDbHelper(this)
        geminiAudioRecorder = GeminiAudioRecorder(this)
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

        val filter = IntentFilter().apply {
            addAction(SettingsActivity.ACTION_SETTINGS_UPDATED)
            addAction(VoicePermissionActivity.ACTION_PERMISSION_RESULT)
            addAction(VoiceInputDialogActivity.ACTION_VOICE_RESULT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(keyboardReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(keyboardReceiver, filter)
        }
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
                    if (activeView == ActiveViewMode.CLIPBOARD && ::clipboardPanel.isInitialized) {
                        buildClipboardPanelContent()
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

        buildGboardToolbar(currentTheme)
        buildLiveTranslateBar(currentTheme)
        buildSuggestionBar(currentTheme)

        contentContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(contentContainer)

        buildKeyboards()
        return rootLayout
    }

    private fun rebuildEntireUI() {
        val currentTheme = getCurrentTheme()
        rootLayout.setBackgroundColor(currentTheme.background)
        rootLayout.removeAllViews()

        buildGboardToolbar(currentTheme)
        buildLiveTranslateBar(currentTheme)
        buildSuggestionBar(currentTheme)

        contentContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(contentContainer)

        if (activeView == ActiveViewMode.CLIPBOARD) {
            showClipboardView()
        } else {
            showKeyboardKeysView()
        }
    }

    /**
     * Gboard-style Sleek Toolbar
     * Height: 44dp, Left: Quick Tools (Translate, Clipboard, Mode Switch, Settings) | Right: Microphone
     */
    private fun buildGboardToolbar(theme: KeyboardTheme) {
        toolbarLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(theme.toolbarBg)
            setPadding(10, 4, 10, 4)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(44)
            )
        }

        // Left Container for tool icons
        val toolsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f)
        }

        // 1. Quick Translate Button with Live Green Signal Badge (Gboard style)
        val isVerified = prefs.getBoolean("gemini_api_verified", false)
        val hasKey = (prefs.getString("gemini_api_key", "") ?: "").isNotBlank()
        val translateLabel = when {
            hasKey && isVerified -> "🌐 অনুবাদ 🟢"
            hasKey -> "🌐 অনুবাদ 🟡"
            else -> "🌐 অনুবাদ"
        }

        btnTranslate = createGboardIconButton(translateLabel, theme.toolbarBg, theme.textColor) {
            toggleLiveTranslate()
        }.apply {
            setOnLongClickListener {
                triggerHapticAndSound()
                performQuickTranslate()
                true
            }
        }
        toolsContainer.addView(btnTranslate)

        // 2. Clipboard Panel Button
        val btnClip = createGboardIconButton("📋 ক্লিপবোর্ড", theme.toolbarBg, theme.textColor) {
            toggleClipboardView()
        }
        toolsContainer.addView(btnClip)

        // 3. Typing Mode Switch Pill (EN / বাংলিশ / বাংলা)
        btnMode = Button(this).apply {
            text = getModeLabel()
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(theme.textColor)
            background = createRoundedDrawable(theme.specialKeyBg, dpToPx(14))
            setPadding(dpToPx(10), dpToPx(3), dpToPx(10), dpToPx(3))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dpToPx(30)
            ).apply {
                setMargins(dpToPx(4), 0, dpToPx(4), 0)
            }
            setOnClickListener {
                triggerHapticAndSound()
                switchTypingMode()
            }
        }
        toolsContainer.addView(btnMode)

        // 4. Settings Icon Button
        val btnSettings = createGboardIconButton("⚙️", theme.toolbarBg, theme.textColor) {
            val intent = Intent(this, SettingsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        }
        toolsContainer.addView(btnSettings)

        toolbarLayout.addView(toolsContainer)

        // Dedicated Voice Language Switcher Pill (BN <-> EN)
        btnVoiceLang = Button(this).apply {
            text = if (getEffectiveVoiceLang() == "en") "🇺🇸 EN" else "🇧🇩 BN"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(theme.textColor)
            background = createRoundedDrawable(theme.keyBg, dpToPx(14))
            setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dpToPx(28)
            ).apply {
                gravity = Gravity.CENTER_VERTICAL
                setMargins(dpToPx(2), 0, dpToPx(4), 0)
            }
            setOnClickListener {
                triggerHapticAndSound()
                toggleVoiceLanguage()
            }
        }
        toolbarLayout.addView(btnVoiceLang)

        // Right side: Dedicated Gboard-style Voice Dictation Mic Button with "শুনতেছি..." status
        btnMic = Button(this).apply {
            text = if (isListening) "🔴 শুনতেছি..." else "🎙️"
            textSize = if (isListening) 12f else 16f
            setTextColor(Color.WHITE)
            background = createRoundedDrawable(if (isListening) Color.parseColor("#EF4444") else theme.actionKeyBg, dpToPx(18))
            val btnWidth = if (isListening) ViewGroup.LayoutParams.WRAP_CONTENT else dpToPx(38)
            layoutParams = LinearLayout.LayoutParams(btnWidth, dpToPx(38)).apply {
                gravity = Gravity.CENTER_VERTICAL
                if (isListening) {
                    setMargins(dpToPx(4), 0, dpToPx(4), 0)
                }
            }
            setPadding(dpToPx(10), 0, dpToPx(10), 0)
            setOnClickListener {
                triggerHapticAndSound()
                toggleVoiceInput()
            }
            setOnLongClickListener {
                triggerHapticAndSound()
                launchVoiceInputDialog()
                true
            }
        }
        toolbarLayout.addView(btnMic)

        rootLayout.addView(toolbarLayout)
    }

    private fun createGboardIconButton(label: String, bgColor: Int, textColor: Int, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            textSize = 12f
            setTextColor(textColor)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setOnClickListener {
                triggerHapticAndSound()
                onClick()
            }
        }
    }

    private fun createRoundedDrawable(color: Int, cornerRadiusPx: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = cornerRadiusPx.toFloat()
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    /**
     * Gboard-style Live Inline Translation Bar
     */
    private fun buildLiveTranslateBar(theme: KeyboardTheme) {
        liveTranslateBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = if (isLiveTranslateActive) View.VISIBLE else View.GONE
            setBackgroundColor(theme.specialKeyBg)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(8), dpToPx(3), dpToPx(8), dpToPx(3))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(38)
            )
        }

        btnLiveTranslateLangPill = Button(this).apply {
            text = if (liveTranslateSourceLang == "bn") "বাংলা ➔ English" else "English ➔ বাংলা"
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = createRoundedDrawable(theme.actionKeyBg, dpToPx(10))
            setPadding(dpToPx(8), dpToPx(2), dpToPx(8), dpToPx(2))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dpToPx(28)
            )
            setOnClickListener {
                triggerHapticAndSound()
                val temp = liveTranslateSourceLang
                liveTranslateSourceLang = liveTranslateTargetLang
                liveTranslateTargetLang = temp
                text = if (liveTranslateSourceLang == "bn") "বাংলা ➔ English" else "English ➔ বাংলা"
                tvLiveTranslatePreview.text = if (liveTranslateSourceLang == "bn") "বাংলা লিখুন (Space দিলে ইংরেজিতে যাবে)..." else "Type English (Space to translate to Bangla)..."
                scheduleLiveTranslateDebounce()
            }
        }
        liveTranslateBar.addView(btnLiveTranslateLangPill)

        tvLiveTranslatePreview = TextView(this).apply {
            text = if (liveTranslateSourceLang == "bn") "বাংলা লিখুন (Space দিলে ইংরেজিতে যাবে)..." else "Type English (Space to translate to Bangla)..."
            textSize = 11f
            setTextColor(theme.textColor)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(8), 0, dpToPx(4), 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f)
        }
        liveTranslateBar.addView(tvLiveTranslatePreview)

        // Instant commit / Send button: "✓ পাঠান"
        val btnCommit = TextView(this).apply {
            text = "✓ পাঠান"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#10B981"))
            setPadding(dpToPx(8), dpToPx(4), dpToPx(6), dpToPx(4))
            setOnClickListener {
                triggerHapticAndSound()
                commitCurrentLiveTranslation()
            }
        }
        liveTranslateBar.addView(btnCommit)

        val btnClose = TextView(this).apply {
            text = "✕"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            setOnClickListener {
                triggerHapticAndSound()
                toggleLiveTranslate()
            }
        }
        liveTranslateBar.addView(btnClose)

        rootLayout.addView(liveTranslateBar)
    }

    private fun scheduleLiveTranslateDebounce() {
        liveTranslateDebounceJob?.cancel()
        val textToTranslate = if (currentMode == TypingMode.BANGLISH && phoneticBuffer.isNotEmpty()) {
            PhoneticEngine.transliterateWord(phoneticBuffer.toString())
        } else {
            liveTranslateBuffer.toString()
        }.trim()

        if (textToTranslate.isEmpty()) {
            tvLiveTranslatePreview.text = if (liveTranslateSourceLang == "bn") "বাংলা লিখুন (Space দিলে ইংরেজিতে যাবে)..." else "Type English (Space to translate to Bangla)..."
            lastTranslatedPreview = ""
            return
        }

        liveTranslateDebounceJob = serviceScope.launch {
            kotlinx.coroutines.delay(450)
            val res = geminiService.translateText(textToTranslate, liveTranslateSourceLang, liveTranslateTargetLang)
            res.onSuccess { translated ->
                mainHandler.post {
                    if (isLiveTranslateActive) {
                        lastTranslatedPreview = translated
                        tvLiveTranslatePreview.text = "$textToTranslate ➔ $translated"
                    }
                }
            }
        }
    }

    private fun commitCurrentLiveTranslation() {
        val ic = currentInputConnection ?: return
        val rawInput = if (currentMode == TypingMode.BANGLISH && phoneticBuffer.isNotEmpty()) {
            val translit = PhoneticEngine.transliterateWord(phoneticBuffer.toString())
            phoneticBuffer.clear()
            translit
        } else {
            val buf = liveTranslateBuffer.toString()
            liveTranslateBuffer.clear()
            buf
        }.trim()

        if (rawInput.isEmpty() && lastTranslatedPreview.isEmpty()) return

        if (lastTranslatedPreview.isNotEmpty()) {
            ic.commitText("$lastTranslatedPreview ", 1)
            tvLiveTranslatePreview.text = "✓ সম্পন্ন: $lastTranslatedPreview"
            lastTranslatedPreview = ""
            updateSuggestions()
            return
        }

        tvLiveTranslatePreview.text = "⏳ অনুবাদ হচ্ছে: $rawInput..."
        serviceScope.launch {
            val res = geminiService.translateText(rawInput, liveTranslateSourceLang, liveTranslateTargetLang)
            res.onSuccess { translated ->
                mainHandler.post {
                    ic.commitText("$translated ", 1)
                    tvLiveTranslatePreview.text = "✓ সম্পন্ন: $translated"
                    updateSuggestions()
                }
            }.onFailure {
                mainHandler.post {
                    ic.commitText("$rawInput ", 1)
                    updateSuggestions()
                }
            }
        }
    }

    private fun toggleLiveTranslate() {
        val apiKey = prefs.getString("gemini_api_key", "") ?: ""
        if (apiKey.isBlank()) {
            Toast.makeText(this, "⚠️ লাইভ অনুবাদের জন্য সেটিংসে Gemini API Key দিন", Toast.LENGTH_LONG).show()
            val intent = Intent(this, SettingsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            return
        }

        isLiveTranslateActive = !isLiveTranslateActive
        liveTranslateBuffer.clear()
        lastTranslatedPreview = ""
        liveTranslateDebounceJob?.cancel()

        if (::liveTranslateBar.isInitialized) {
            liveTranslateBar.visibility = if (isLiveTranslateActive) View.VISIBLE else View.GONE
            if (isLiveTranslateActive) {
                tvLiveTranslatePreview.text = if (liveTranslateSourceLang == "bn") "বাংলা লিখুন (Space দিলে ইংরেজিতে যাবে)..." else "Type English (Space to translate to Bangla)..."
                btnTranslate.setTextColor(Color.parseColor("#10B981"))
                Toast.makeText(this, "🌐 লাইভ অনুবাদ চালু হয়েছে", Toast.LENGTH_SHORT).show()
            } else {
                val theme = getCurrentTheme()
                btnTranslate.setTextColor(theme.textColor)
            }
        }
    }

    private fun buildSuggestionBar(theme: KeyboardTheme) {
        suggestionBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            setBackgroundColor(theme.toolbarBg)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(suggestionBar)
    }

    /**
     * Smart Suggestion Bar with PredictionEngine & Next-Word Completion
     */
    private fun updateSuggestions() {
        suggestionBar.removeAllViews()
        val currentTheme = getCurrentTheme()
        val ic = currentInputConnection

        val candidates = mutableListOf<String>()

        if (currentMode == TypingMode.BANGLISH && phoneticBuffer.isNotEmpty()) {
            val rawInput = phoneticBuffer.toString()
            val transliterated = PhoneticEngine.transliterateWord(rawInput)
            candidates.add(transliterated)
            if (rawInput != transliterated) {
                candidates.add(rawInput)
            }
            if (transliterated.isNotEmpty()) {
                val lastChar = transliterated.last()
                if (lastChar !in listOf('া', 'ি', 'ী', 'ু', 'ূ', 'ে', 'ৈ', 'ো', 'ৌ', '্')) {
                    candidates.add(transliterated + "া")
                    candidates.add(transliterated + "ের")
                }
            }
            val preds = PredictionEngine.getPredictions(transliterated, "", true)
            candidates.addAll(preds)
        } else if (currentMode == TypingMode.ENGLISH) {
            val prevText = ic?.getTextBeforeCursor(40, 0)?.toString() ?: ""
            val preds = PredictionEngine.getPredictions("", prevText, false)
            candidates.addAll(preds)
        } else if (currentMode == TypingMode.BANGLA) {
            val prevText = ic?.getTextBeforeCursor(40, 0)?.toString() ?: ""
            val preds = PredictionEngine.getPredictions("", prevText, true)
            candidates.addAll(preds)
        }

        val distinctList = candidates.distinct().take(5)
        if (distinctList.isEmpty()) {
            suggestionBar.visibility = View.GONE
            return
        }

        suggestionBar.visibility = View.VISIBLE
        for (cand in distinctList) {
            val chip = Button(this).apply {
                text = cand
                textSize = 13f
                setTextColor(currentTheme.textColor)
                background = createRoundedDrawable(currentTheme.specialKeyBg, dpToPx(8))
                setPadding(dpToPx(14), dpToPx(4), dpToPx(14), dpToPx(4))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(dpToPx(4), 0, dpToPx(4), 0)
                }
                setOnClickListener {
                    triggerHapticAndSound()
                    val conn = currentInputConnection ?: return@setOnClickListener
                    PredictionEngine.learnWord(cand)
                    if (currentMode == TypingMode.BANGLISH && phoneticBuffer.isNotEmpty()) {
                        conn.commitText("$cand ", 1)
                        phoneticBuffer.clear()
                    } else {
                        conn.commitText("$cand ", 1)
                    }
                    updateSuggestions()
                }
            }
            suggestionBar.addView(chip)
        }
    }

    /**
     * Gboard-style Dedicated Clipboard Panel
     * Full-keyboard replacement showing Pinned and Recent clips serial by serial.
     */
    private fun toggleClipboardView() {
        if (activeView == ActiveViewMode.CLIPBOARD) {
            showKeyboardKeysView()
        } else {
            showClipboardView()
        }
    }

    private fun showKeyboardKeysView() {
        activeView = ActiveViewMode.KEYBOARD
        contentContainer.removeAllViews()
        if (::keysLayout.isInitialized) {
            contentContainer.addView(keysLayout)
        } else {
            buildKeyboards()
        }
    }

    private fun showClipboardView() {
        activeView = ActiveViewMode.CLIPBOARD
        contentContainer.removeAllViews()
        buildClipboardPanel()
        contentContainer.addView(clipboardPanel)
    }

    private fun buildClipboardPanel() {
        val theme = getCurrentTheme()

        clipboardPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.background)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dpToPx(260)
            )
        }

        // 1. Header Bar: [← কীবোর্ডে ফিরে যান] | "ক্লিপবোর্ড" | [🗑️ সব মুছুন]
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(theme.toolbarBg)
            setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(38)
            )
        }

        val btnBack = TextView(this).apply {
            text = "← ফিরে যান"
            setTextColor(theme.accentColor)
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setPadding(dpToPx(4), dpToPx(4), dpToPx(8), dpToPx(4))
            setOnClickListener {
                triggerHapticAndSound()
                showKeyboardKeysView()
            }
        }
        header.addView(btnBack)

        val title = TextView(this).apply {
            text = "📋 আনলিমিটেড ক্লিপবোর্ড"
            setTextColor(theme.textColor)
            textSize = 13f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
        }
        header.addView(title)

        val btnClear = TextView(this).apply {
            text = "🗑️ মুছুন"
            setTextColor(Color.parseColor("#EF4444"))
            textSize = 12f
            setPadding(dpToPx(8), dpToPx(4), dpToPx(4), dpToPx(4))
            setOnClickListener {
                triggerHapticAndSound()
                val deleted = clipboardDb.clearUnpinned()
                Toast.makeText(this@SmartKeyboardService, "$deleted টি অপিনকৃত ক্লিপ মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                buildClipboardPanelContent()
            }
        }
        header.addView(btnClear)

        clipboardPanel.addView(header)

        // 2. Scrollable Container for serial-by-serial clips
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            )
        }

        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            tag = "CLIP_LIST_CONTAINER"
            setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        scrollView.addView(listContainer)
        clipboardPanel.addView(scrollView)

        buildClipboardPanelContent()
    }

    private fun buildClipboardPanelContent() {
        if (!::clipboardPanel.isInitialized) return
        val listContainer = clipboardPanel.findViewWithTag<LinearLayout>("CLIP_LIST_CONTAINER") ?: return
        listContainer.removeAllViews()

        val theme = getCurrentTheme()
        val allClips = clipboardDb.getAllClips()

        if (allClips.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "ক্লিপবোর্ডে কোনো ডাটা সংরক্ষিত নেই।\nযেকোনো অ্যাপ থেকে লেখা কপি করলেই এখানে অটোমেটিক সিরিয়াল অনুযায়ী জমা হবে।"
                setTextColor(Color.GRAY)
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(dpToPx(20), dpToPx(40), dpToPx(20), dpToPx(40))
            }
            listContainer.addView(emptyTv)
            return
        }

        val pinnedClips = allClips.filter { it.isPinned }
        val recentClips = allClips.filter { !it.isPinned }

        // Section 1: Pinned Clips
        if (pinnedClips.isNotEmpty()) {
            val pinnedHeader = TextView(this).apply {
                text = "📌 পিন করা ক্লিপসমূহ (${pinnedClips.size})"
                setTextColor(theme.accentColor)
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setPadding(dpToPx(4), dpToPx(6), dpToPx(4), dpToPx(4))
            }
            listContainer.addView(pinnedHeader)

            for (clip in pinnedClips) {
                listContainer.addView(createGboardClipCard(clip, theme))
            }
        }

        // Section 2: Recent Clips (Serial by serial)
        if (recentClips.isNotEmpty()) {
            val recentHeader = TextView(this).apply {
                text = "🕒 সাম্প্রতিক ক্লিপসমূহ (${recentClips.size})"
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setPadding(dpToPx(4), dpToPx(10), dpToPx(4), dpToPx(4))
            }
            listContainer.addView(recentHeader)

            for (clip in recentClips) {
                listContainer.addView(createGboardClipCard(clip, theme))
            }
        }
    }

    private fun createGboardClipCard(clip: ClipEntry, theme: KeyboardTheme): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createRoundedDrawable(
                if (clip.isPinned) theme.specialKeyBg else theme.keyBg,
                dpToPx(8)
            )
            setPadding(dpToPx(12), dpToPx(10), dpToPx(8), dpToPx(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dpToPx(4), 0, dpToPx(4))
            }
        }

        // Clip Text (Tap anywhere on text to instantly paste!)
        val tvText = TextView(this).apply {
            text = clip.text
            textSize = 13f
            maxLines = 3
            setTextColor(theme.textColor)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f)
        }

        card.setOnClickListener {
            triggerHapticAndSound()
            currentInputConnection?.commitText(clip.text, 1)
            showKeyboardKeysView()
        }

        card.setOnLongClickListener {
            triggerHapticAndSound()
            clipboardDb.togglePin(clip.id, !clip.isPinned)
            buildClipboardPanelContent()
            Toast.makeText(this, if (!clip.isPinned) "পিন করা হয়েছে 📌" else "আনপিন করা হয়েছে", Toast.LENGTH_SHORT).show()
            true
        }

        card.addView(tvText)

        // Pin Toggle Button (📌 / 📍)
        val btnPin = TextView(this).apply {
            text = if (clip.isPinned) "📌" else "📍"
            textSize = 16f
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            setOnClickListener {
                triggerHapticAndSound()
                clipboardDb.togglePin(clip.id, !clip.isPinned)
                buildClipboardPanelContent()
            }
        }
        card.addView(btnPin)

        // Delete Button (🗑️)
        val btnDelete = TextView(this).apply {
            text = "🗑️"
            textSize = 14f
            setPadding(dpToPx(6), dpToPx(4), dpToPx(6), dpToPx(4))
            setOnClickListener {
                triggerHapticAndSound()
                clipboardDb.deleteClip(clip.id)
                buildClipboardPanelContent()
            }
        }
        card.addView(btnDelete)

        return card
    }

    private fun buildKeyboards() {
        val theme = getCurrentTheme()

        keysLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.background)
            setPadding(dpToPx(3), dpToPx(4), dpToPx(3), dpToPx(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val rows = when (currentMode) {
            TypingMode.ENGLISH, TypingMode.BANGLISH -> getEnglishKeyRows()
            TypingMode.BANGLA -> getBanglaKeyRows()
        }

        for (row in rows) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(48)
                ).apply {
                    setMargins(0, dpToPx(2), 0, dpToPx(2))
                }
            }

            for (key in row) {
                val keyWeight = when (key) {
                    "Space" -> 4.0f
                    "⌫", "↵", "⇧", "123" -> 1.5f
                    else -> 1.0f
                }

                val keyView = createKeyView(key, theme, keyWeight)
                rowLayout.addView(keyView)
            }

            keysLayout.addView(rowLayout)
        }

        if (activeView == ActiveViewMode.KEYBOARD) {
            contentContainer.removeAllViews()
            contentContainer.addView(keysLayout)
        }
    }

    /**
     * Key View with Continuous Auto-Repeat Backspace on Hold
     */
    private fun createKeyView(key: String, theme: KeyboardTheme, weight: Float): View {
        val isSpecial = key in listOf("⌫", "↵", "⇧", "123", ",", ".")
        val isAction = key == "↵"

        val bgColor = when {
            isAction -> theme.actionKeyBg
            isSpecial -> theme.specialKeyBg
            else -> theme.keyBg
        }

        val keyLayout = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).apply {
                setMargins(dpToPx(2), 0, dpToPx(2), 0)
            }
            background = createRoundedDrawable(bgColor, dpToPx(6))
        }

        val displayKey = when {
            key == "Space" -> if (currentMode == TypingMode.BANGLISH) "বাংলা (Space)" else "Space"
            key == "⇧" && isShifted -> "⬆"
            isShifted && key.length == 1 -> key.uppercase()
            else -> key
        }

        val mainLabel = TextView(this).apply {
            text = displayKey
            textSize = when {
                key == "Space" -> 12f
                key.length > 2 -> 13f
                else -> 17f
            }
            setTypeface(null, if (isSpecial || isAction) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(if (isAction) Color.WHITE else theme.textColor)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        keyLayout.addView(mainLabel)

        // Secondary hint symbol on top right of the key
        val secondary = secondaryKeyMap[key.lowercase()]
        if (secondary != null && key.length == 1) {
            val hintLabel = TextView(this).apply {
                text = secondary
                textSize = 9f
                setTextColor(Color.parseColor("#94A3B8"))
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.END
                    setMargins(0, dpToPx(2), dpToPx(4), 0)
                }
            }
            keyLayout.addView(hintLabel)
        }

        // Auto-repeat backspace on hold with Phase 6 Acceleration & Swipe-to-Delete
        if (key == "⌫") {
            var pressStartTime = 0L
            var startX = 0f
            var isSwipeMode = false

            val repeatRunnable = object : Runnable {
                override fun run() {
                    val elapsed = SystemClock.uptimeMillis() - pressStartTime
                    triggerHapticAndSound()

                    if (elapsed >= 1200L) {
                        // Rapid word/chunk deletion phase (> 1.2s)
                        handleWordOrChunkDelete()
                        mainHandler.postDelayed(this, 75L)
                    } else {
                        // Character deletion phase with acceleration (70ms -> 30ms)
                        handleKeyPress("⌫")
                        val progress = ((elapsed - 350L).toFloat() / 850f).coerceIn(0f, 1f)
                        val interval = (70L - (progress * 40L)).toLong().coerceIn(30L, 70L)
                        mainHandler.postDelayed(this, interval)
                    }
                }
            }

            keyLayout.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        pressStartTime = SystemClock.uptimeMillis()
                        startX = event.x
                        isSwipeMode = false
                        triggerHapticAndSound()
                        handleKeyPress("⌫")
                        mainHandler.postDelayed(repeatRunnable, 350L)
                        v.isPressed = true
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.x - startX
                        if (deltaX < -dpToPx(45) && !isSwipeMode) {
                            // User swiped left on delete key! Delete previous word with haptic feedback
                            isSwipeMode = true
                            mainHandler.removeCallbacks(repeatRunnable)
                            handleWordOrChunkDelete()
                            triggerHapticAndSound()
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        mainHandler.removeCallbacks(repeatRunnable)
                        v.isPressed = false
                        true
                    }
                    else -> false
                }
            }
        } else if (key == "Space") {
            // Spacebar with Tap & Cursor Slider Trackpad
            var spaceStartX = 0f
            var isSpaceDragging = false

            keyLayout.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        spaceStartX = event.x
                        isSpaceDragging = false
                        v.isPressed = true
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.x - spaceStartX
                        val threshold = dpToPx(16).toFloat()
                        if (Math.abs(deltaX) > threshold) {
                            isSpaceDragging = true
                            if (deltaX > threshold) {
                                moveCursor(1)
                                spaceStartX = event.x
                                triggerHapticAndSound()
                            } else if (deltaX < -threshold) {
                                moveCursor(-1)
                                spaceStartX = event.x
                                triggerHapticAndSound()
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.isPressed = false
                        if (!isSpaceDragging) {
                            triggerHapticAndSound()
                            handleKeyPress("Space")
                        }
                        true
                    }
                    else -> false
                }
            }
        } else {
            // Normal Tap & Long-Press handling
            keyLayout.setOnClickListener {
                triggerHapticAndSound()
                handleKeyPress(key)
            }

            keyLayout.setOnLongClickListener {
                triggerHapticAndSound()
                handleKeyLongPress(key)
                true
            }
        }

        return keyLayout
    }

    private fun triggerHapticAndSound() {
        val enableVibration = prefs.getBoolean("enable_vibration", true)
        val enableSound = prefs.getBoolean("enable_sound", true)

        if (enableVibration) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(20)
            }
        }

        if (enableSound) {
            audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD)
        }
    }

    /**
     * Key Press Handler with Composing Text Architecture & Gboard Live Translate
     */
    private fun handleKeyPress(key: String) {
        val ic = currentInputConnection ?: return

        when (key) {
            "⌫" -> {
                if (isLiveTranslateActive && liveTranslateBuffer.isNotEmpty()) {
                    liveTranslateBuffer.deleteCharAt(liveTranslateBuffer.length - 1)
                    tvLiveTranslatePreview.text = if (liveTranslateBuffer.isEmpty()) "বাংলা লিখুন (Space দিলে ইংরেজিতে যাবে)..." else liveTranslateBuffer.toString()
                    scheduleLiveTranslateDebounce()
                } else if (isLiveTranslateActive && currentMode == TypingMode.BANGLISH && phoneticBuffer.isNotEmpty()) {
                    phoneticBuffer.deleteCharAt(phoneticBuffer.length - 1)
                    val bengali = if (phoneticBuffer.isEmpty()) "" else PhoneticEngine.transliterateWord(phoneticBuffer.toString())
                    tvLiveTranslatePreview.text = if (bengali.isEmpty()) "বাংলা লিখুন (Space দিলে ইংরেজিতে যাবে)..." else bengali
                    scheduleLiveTranslateDebounce()
                } else if (currentMode == TypingMode.BANGLISH && phoneticBuffer.isNotEmpty()) {
                    phoneticBuffer.deleteCharAt(phoneticBuffer.length - 1)
                    if (phoneticBuffer.isEmpty()) {
                        ic.setComposingText("", 1)
                        ic.finishComposingText()
                    } else {
                        val bengali = PhoneticEngine.transliterateWord(phoneticBuffer.toString())
                        ic.setComposingText(bengali, 1)
                    }
                    updateSuggestions()
                } else {
                    ic.deleteSurroundingText(1, 0)
                    updateSuggestions()
                }
            }
            "↵" -> {
                if (isLiveTranslateActive && (liveTranslateBuffer.isNotEmpty() || phoneticBuffer.isNotEmpty())) {
                    commitCurrentLiveTranslation()
                    ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER))
                    ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER))
                } else {
                    flushPhoneticBuffer()
                    ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ENTER))
                    ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_ENTER))
                    updateSuggestions()
                }
            }
            "⇧" -> {
                isShifted = !isShifted
                buildKeyboards()
            }
            "Space" -> {
                if (isLiveTranslateActive && (liveTranslateBuffer.isNotEmpty() || phoneticBuffer.isNotEmpty())) {
                    commitCurrentLiveTranslation()
                } else if (currentMode == TypingMode.BANGLISH && phoneticBuffer.isNotEmpty()) {
                    val transliterated = PhoneticEngine.transliterateWord(phoneticBuffer.toString())
                    ic.commitText("$transliterated ", 1)
                    phoneticBuffer.clear()
                    updateSuggestions()
                } else {
                    ic.commitText(" ", 1)
                    updateSuggestions()
                }
            }
            else -> {
                val charToCommit = if (isShifted) key.uppercase() else key
                if (isLiveTranslateActive) {
                    if (currentMode == TypingMode.BANGLISH) {
                        phoneticBuffer.append(charToCommit)
                        val bengali = PhoneticEngine.transliterateWord(phoneticBuffer.toString())
                        tvLiveTranslatePreview.text = bengali
                    } else {
                        liveTranslateBuffer.append(charToCommit)
                        tvLiveTranslatePreview.text = liveTranslateBuffer.toString()
                    }
                    scheduleLiveTranslateDebounce()
                } else if (currentMode == TypingMode.BANGLISH) {
                    phoneticBuffer.append(charToCommit)
                    ic.setComposingText(phoneticBuffer.toString(), 1)
                    updateSuggestions()
                } else {
                    ic.commitText(charToCommit, 1)
                    updateSuggestions()
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
                ic.setComposingText(phoneticBuffer.toString(), 1)
                updateSuggestions()
            } else {
                ic.commitText(symbol, 1)
            }
        }
    }

    /**
     * Phase 6: Rapid Continuous Word / Chunk Deletion Engine
     * Safely deletes preceding words or whitespace blocks in one motion.
     */
    private fun handleWordOrChunkDelete() {
        val ic = currentInputConnection ?: return

        if (isLiveTranslateActive && (liveTranslateBuffer.isNotEmpty() || phoneticBuffer.isNotEmpty())) {
            liveTranslateBuffer.clear()
            phoneticBuffer.clear()
            tvLiveTranslatePreview.text = "বাংলা লিখুন (Space দিলে ইংরেজিতে যাবে)..."
            scheduleLiveTranslateDebounce()
            return
        }

        if (currentMode == TypingMode.BANGLISH && phoneticBuffer.isNotEmpty()) {
            phoneticBuffer.clear()
            ic.setComposingText("", 1)
            ic.finishComposingText()
            updateSuggestions()
            return
        }

        val textBefore = ic.getTextBeforeCursor(150, 0)?.toString() ?: ""
        if (textBefore.isEmpty()) {
            ic.deleteSurroundingText(1, 0)
            updateSuggestions()
            return
        }

        var index = textBefore.length

        // 1. Skip trailing whitespace if present
        while (index > 0 && Character.isWhitespace(textBefore[index - 1])) {
            index--
        }

        if (index == 0) {
            ic.deleteSurroundingText(textBefore.length, 0)
            updateSuggestions()
            return
        }

        val isPunct = { ch: Char -> ",.!?;:-–—\"'`()[]{}।॥".indexOf(ch) >= 0 }

        if (isPunct(textBefore[index - 1])) {
            while (index > 0 && isPunct(textBefore[index - 1])) {
                index--
            }
        } else {
            while (index > 0 && !Character.isWhitespace(textBefore[index - 1]) && !isPunct(textBefore[index - 1])) {
                index--
            }
        }

        val deleteCount = textBefore.length - index
        if (deleteCount > 0) {
            ic.deleteSurroundingText(deleteCount, 0)
            updateSuggestions()
        } else {
            ic.deleteSurroundingText(1, 0)
            updateSuggestions()
        }
    }

    /**
     * Phase 6: Spacebar Cursor Slider Trackpad
     * Moves cursor left (-1) or right (+1) cleanly.
     */
    private fun moveCursor(direction: Int) {
        val ic = currentInputConnection ?: return
        val keyCode = if (direction < 0) {
            android.view.KeyEvent.KEYCODE_DPAD_LEFT
        } else {
            android.view.KeyEvent.KEYCODE_DPAD_RIGHT
        }
        ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode))
        ic.sendKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode))
    }

    private fun flushPhoneticBuffer() {
        if (currentMode == TypingMode.BANGLISH && phoneticBuffer.isNotEmpty()) {
            val ic = currentInputConnection ?: return
            val transliterated = PhoneticEngine.transliterateWord(phoneticBuffer.toString())
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
            TypingMode.ENGLISH -> "EN"
            TypingMode.BANGLISH -> "বাংলিশ"
            TypingMode.BANGLA -> "বাংলা"
        }
    }

    /**
     * Gemini Quick Translation with dynamic API Key detection
     */
    private fun performQuickTranslate() {
        val latestApiKey = prefs.getString("gemini_api_key", "") ?: ""
        if (latestApiKey.isBlank()) {
            Toast.makeText(this, "⚠️ Gemini API Key দেওয়া হয়নি! অনুগ্রহ করে সেটিংসে API Key দিন।", Toast.LENGTH_LONG).show()
            val intent = Intent(this, SettingsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            return
        }

        // Ensure service uses latest key
        geminiService = GeminiService(latestApiKey)

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

    /**
     * Voice Dictation Handling with "🔴 শুনতেছি..." Live Waveform & Dual-Engine Fallback
     */
    private fun toggleVoiceInput() {
        if (isListening || isGeminiVoiceActive) {
            stopVoiceListening()
        } else {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                Toast.makeText(this, "ভয়েস টাইপিংয়ের জন্য মাইক্রোফোন পারমিশন দিন", Toast.LENGTH_SHORT).show()
                val permIntent = Intent(this, VoicePermissionActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(permIntent)
                return
            }

            val apiKey = prefs.getString("gemini_api_key", "") ?: ""
            if (apiKey.isNotBlank()) {
                startGeminiDirectVoice(apiKey)
            } else {
                startVoiceListening()
            }
        }
    }

    private fun startGeminiDirectVoice(apiKey: String) {
        val started = geminiAudioRecorder.startRecording()
        if (!started) {
            Toast.makeText(this, "⚠️ মাইক্রোফোন সক্রিয় করা যায়নি", Toast.LENGTH_SHORT).show()
            launchVoiceInputDialog()
            return
        }

        isGeminiVoiceActive = true
        isListening = true
        updateMicButtonState(true, "🔴 শুনতেছি...")
        Toast.makeText(this, "🎙️ শুনতেছি... কথা বলুন (থেমে গেলে অটোমেটিক লিখা হবে)", Toast.LENGTH_SHORT).show()

        geminiAudioRecorder.audioEventListener = object : com.smartvoices.keyboard.voice.GeminiAudioRecorder.AudioEventListener {
            private var lastUpdate = 0L
            override fun onAudioLevel(rmsDb: Float) {
                val now = System.currentTimeMillis()
                if (now - lastUpdate > 180) {
                    lastUpdate = now
                    val bars = when {
                        rmsDb > 65 -> " ▃▅█"
                        rmsDb > 52 -> " ▂▃▅"
                        rmsDb > 38 -> " ▂▃ "
                        else -> " ▂  "
                    }
                    mainHandler.post {
                        if (isGeminiVoiceActive) {
                            btnMic.text = "🔴 শুনতেছি$bars"
                        }
                    }
                }
            }

            override fun onSpeechDetected() {
                mainHandler.post {
                    if (isGeminiVoiceActive) {
                        btnMic.text = "🔴 কথা শুনছি..."
                    }
                }
            }

            override fun onSilenceTimeout() {
                mainHandler.post {
                    if (isGeminiVoiceActive) {
                        stopVoiceListening()
                    }
                }
            }

            override fun onMaxDurationReached() {
                mainHandler.post {
                    if (isGeminiVoiceActive) {
                        stopVoiceListening()
                    }
                }
            }
        }
    }

    private fun handleVoiceCommandResult(result: com.smartvoices.keyboard.voice.VoiceCommandProcessor.VoiceResult) {
        val ic = currentInputConnection ?: return
        when (result.commandType) {
            "CLEAR" -> {
                ic.deleteSurroundingText(100, 0)
                Toast.makeText(this, "টেক্সট মুছে ফেলা হয়েছে 🗑️", Toast.LENGTH_SHORT).show()
            }
            "NEWLINE" -> {
                ic.commitText("\n", 1)
            }
            "TRANSLATE_TO_BN" -> {
                val payload = result.payload
                if (payload.isBlank()) return
                serviceScope.launch {
                    val res = geminiService.translateText(payload, "en", "bn")
                    res.onSuccess { translated ->
                        mainHandler.post {
                            ic.commitText("$translated ", 1)
                            Toast.makeText(this@SmartKeyboardService, "✅ বাংলায় অনুবাদ সম্পন্ন", Toast.LENGTH_SHORT).show()
                        }
                    }.onFailure { err ->
                        mainHandler.post {
                            ic.commitText("$payload ", 1)
                            Toast.makeText(this@SmartKeyboardService, "অনুবাদ ব্যর্থ: ${err.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            "TRANSLATE_TO_EN" -> {
                val payload = result.payload
                if (payload.isBlank()) return
                serviceScope.launch {
                    val res = geminiService.translateText(payload, "bn", "en")
                    res.onSuccess { translated ->
                        mainHandler.post {
                            ic.commitText("$translated ", 1)
                            Toast.makeText(this@SmartKeyboardService, "✅ Translated to English", Toast.LENGTH_SHORT).show()
                        }
                    }.onFailure { err ->
                        mainHandler.post {
                            ic.commitText("$payload ", 1)
                            Toast.makeText(this@SmartKeyboardService, "Translation failed: ${err.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            else -> {
                ic.commitText("${result.finalText} ", 1)
            }
        }
    }

    private fun launchVoiceInputDialog() {
        val intent = Intent(this, VoiceInputDialogActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(VoiceInputDialogActivity.EXTRA_LANG_MODE, getEffectiveVoiceLang())
        }
        startActivity(intent)
    }

    private fun startVoiceListening() {
        mainHandler.post {
            try {
                if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                    launchVoiceInputDialog()
                    return@post
                }

                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                        setRecognitionListener(object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {
                                isListening = true
                                updateMicButtonState(true, "🔴 শুনতেছি... বলুন")
                                Toast.makeText(this@SmartKeyboardService, "🎙️ শুনতেছি... কথা বলুন", Toast.LENGTH_SHORT).show()
                            }

                            override fun onBeginningOfSpeech() {}
                            override fun onRmsChanged(rmsdB: Float) {
                                val bars = when {
                                    rmsdB > 7f -> " ▃▅█"
                                    rmsdB > 4f -> " ▂▃▅"
                                    rmsdB > 2f -> " ▂▃ "
                                    else -> " ▂  "
                                }
                                mainHandler.post {
                                    if (isListening && !isGeminiVoiceActive) {
                                        btnMic.text = "🔴 শুনতেছি$bars"
                                    }
                                }
                            }
                            override fun onBufferReceived(buffer: ByteArray?) {}

                            override fun onEndOfSpeech() {
                                updateMicButtonState(true, "⏳ প্রসেস হচ্ছে...")
                            }

                            override fun onError(error: Int) {
                                stopVoiceListening()
                                // If speech recognizer fails on OEM device, launch reliable voice input dialog immediately
                                if (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS || error == SpeechRecognizer.ERROR_NO_MATCH) {
                                    launchVoiceInputDialog()
                                } else {
                                    val errorMsg = when (error) {
                                        SpeechRecognizer.ERROR_AUDIO -> "অডিও রেকর্ডিং সমস্যা"
                                        SpeechRecognizer.ERROR_NETWORK -> "ইন্টারনেট কানেকশন চেক করুন"
                                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "টাইমআউট হয়েছে"
                                        else -> "ভয়েস কোড: $error"
                                    }
                                    Toast.makeText(this@SmartKeyboardService, errorMsg, Toast.LENGTH_SHORT).show()
                                }
                            }

                            override fun onResults(results: Bundle?) {
                                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                if (!matches.isNullOrEmpty()) {
                                    val recognized = matches[0]
                                    val voiceResult = com.smartvoices.keyboard.voice.VoiceCommandProcessor.processVoiceText(recognized, getEffectiveVoiceLang())
                                    if (voiceResult.isCommand) {
                                        handleVoiceCommandResult(voiceResult)
                                    } else {
                                        currentInputConnection?.commitText("${voiceResult.finalText} ", 1)
                                    }
                                }
                                stopVoiceListening()
                            }

                            override fun onPartialResults(partialResults: Bundle?) {
                                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                if (!matches.isNullOrEmpty()) {
                                    updateMicButtonState(true, "🔴 ${matches[0].take(10)}...")
                                }
                            }

                            override fun onEvent(eventType: Int, params: Bundle?) {}
                        })
                    }
                }

                val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    val langCode = if (getEffectiveVoiceLang() == "en") "en-US" else "bn-BD"
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, langCode)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langCode)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "শুনতেছি... কথা বলুন")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }

                speechRecognizer?.startListening(speechIntent)
                isListening = true
                updateMicButtonState(true, "🔴 শুনতেছি...")
            } catch (e: Exception) {
                isListening = false
                // Fallback to voice input dialog
                launchVoiceInputDialog()
            }
        }
    }

    private fun updateMicButtonState(listening: Boolean, label: String) {
        mainHandler.post {
            val theme = getCurrentTheme()
            if (listening) {
                btnMic.text = label
                btnMic.textSize = 12f
                btnMic.background = createRoundedDrawable(Color.parseColor("#EF4444"), dpToPx(18))
                btnMic.layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dpToPx(38)
                ).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setMargins(dpToPx(4), 0, dpToPx(4), 0)
                }
            } else {
                btnMic.text = "🎙️"
                btnMic.textSize = 16f
                btnMic.background = createRoundedDrawable(theme.actionKeyBg, dpToPx(18))
                btnMic.layoutParams = LinearLayout.LayoutParams(
                    dpToPx(38),
                    dpToPx(38)
                ).apply {
                    gravity = Gravity.CENTER_VERTICAL
                }
            }
        }
    }

    private fun stopVoiceListening() {
        if (isGeminiVoiceActive) {
            isGeminiVoiceActive = false
            isListening = false
            updateMicButtonState(true, "⏳ রূপান্তর হচ্ছে...")
            geminiAudioRecorder.audioEventListener = null
            geminiAudioRecorder.audioLevelListener = null

            serviceScope.launch {
                val wavBytes = geminiAudioRecorder.stopRecording()
                val apiKey = prefs.getString("gemini_api_key", "") ?: ""
                val langMode = getEffectiveVoiceLang()

                val result = geminiAudioRecorder.transcribeWithGemini(wavBytes, apiKey, langMode)
                result.onSuccess { transcribed ->
                    mainHandler.post {
                        updateMicButtonState(false, "🎙️")
                        if (transcribed.isNotBlank()) {
                            val voiceResult = com.smartvoices.keyboard.voice.VoiceCommandProcessor.processVoiceText(transcribed, langMode)
                            if (voiceResult.isCommand) {
                                handleVoiceCommandResult(voiceResult)
                            } else {
                                currentInputConnection?.commitText("${voiceResult.finalText} ", 1)
                                Toast.makeText(this@SmartKeyboardService, "✅ লিখা সম্পন্ন", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this@SmartKeyboardService, "কোনো শব্দ শনাক্ত হয়নি", Toast.LENGTH_SHORT).show()
                        }
                    }
                }.onFailure { err ->
                    mainHandler.post {
                        updateMicButtonState(false, "🎙️")
                        Toast.makeText(this@SmartKeyboardService, "ত্রুটি: ${err.message}", Toast.LENGTH_LONG).show()
                        launchVoiceInputDialog()
                    }
                }
            }
            return
        }

        isListening = false
        updateMicButtonState(false, "🎙️")
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
        try {
            unregisterReceiver(keyboardReceiver)
        } catch (e: Exception) {}
        if (isGeminiVoiceActive) {
            try {
                geminiAudioRecorder.stopRecording()
            } catch (e: Exception) {}
        }
        speechRecognizer?.destroy()
    }
}
