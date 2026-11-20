package com.smartvoices.keyboard.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.smartvoices.keyboard.voice.GeminiAudioRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VoiceInputDialogActivity : AppCompatActivity() {

    companion object {
        const val ACTION_VOICE_RESULT = "com.smartvoices.keyboard.VOICE_RESULT"
        const val EXTRA_VOICE_TEXT = "voice_text"
        const val EXTRA_LANG_MODE = "lang_mode"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var geminiAudioRecorder: GeminiAudioRecorder? = null
    private var isGeminiRecording = false
    private val activityScope = CoroutineScope(Dispatchers.Main)
    private lateinit var tvStatus: TextView
    private lateinit var tvTranscript: TextView
    private lateinit var btnMicIcon: TextView
    private lateinit var btnDone: Button
    private lateinit var btnCancel: Button
    private lateinit var btnToggleLang: Button
    private var currentLang = "bn-BD"
    private var recognizedText = ""
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val langPref = intent.getStringExtra(EXTRA_LANG_MODE) ?: "bn"
        currentLang = if (langPref == "en") "en-US" else "bn-BD"

        // Build UI programmatically for lightweight dialog
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0F172A"))
                cornerRadius = 32f
            }
            setPadding(40, 32, 40, 32)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Top Status Header: "🎙️ শুনতেছি... কথা বলুন"
        tvStatus = TextView(this).apply {
            text = "🎙️ শুনতেছি... কথা বলুন"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#10B981"))
            gravity = Gravity.CENTER
            setPadding(0, 10, 0, 10)
        }
        root.addView(tvStatus)

        // Pulsing Mic Icon
        btnMicIcon = TextView(this).apply {
            text = "🎙️"
            textSize = 38f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                shape = GradientDrawable.OVAL
                setStroke(6, Color.parseColor("#10B981"))
            }
            layoutParams = LinearLayout.LayoutParams(160, 160).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, 16, 0, 16)
            }
            setOnClickListener {
                if (isListening || isGeminiRecording) {
                    stopListening()
                } else {
                    startListening()
                }
            }
        }
        root.addView(btnMicIcon)

        // Live Transcript Box
        tvTranscript = TextView(this).apply {
            text = "আপনি যা বলবেন তা এখানে দেখা যাবে..."
            textSize = 14f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = 16f
            }
            minHeight = 160
            setPadding(24, 20, 24, 20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 10, 0, 20)
            }
        }
        root.addView(tvTranscript)

        // Language toggle pill
        btnToggleLang = Button(this).apply {
            text = if (currentLang == "bn-BD") "🌐 ভাষা: বাংলা (bn-BD)" else "🌐 Language: English (en-US)"
            textSize = 12f
            setTextColor(Color.parseColor("#F8FAFC"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#334155"))
                cornerRadius = 24f
            }
            setPadding(30, 6, 30, 6)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setMargins(0, 0, 0, 20)
            }
            setOnClickListener {
                currentLang = if (currentLang == "bn-BD") "en-US" else "bn-BD"
                text = if (currentLang == "bn-BD") "🌐 ভাষা: বাংলা (bn-BD)" else "🌐 Language: English (en-US)"
                stopListening()
                startListening()
            }
        }
        root.addView(btnToggleLang)

        // Action Buttons: Cancel and Done
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        btnCancel = Button(this).apply {
            text = "✕ বাতিল"
            textSize = 13f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EF4444"))
                cornerRadius = 20f
            }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                setMargins(0, 0, 10, 0)
            }
            setOnClickListener {
                finish()
            }
        }
        btnRow.addView(btnCancel)

        btnDone = Button(this).apply {
            text = "✓ সম্পন্ন"
            textSize = 13f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#10B981"))
                cornerRadius = 20f
            }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                setMargins(10, 0, 0, 0)
            }
            setOnClickListener {
                commitAndFinish()
            }
        }
        btnRow.addView(btnDone)

        root.addView(btnRow)
        setContentView(root)

        // Check permission and start listening
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startListening()
        } else {
            tvStatus.text = "⚠️ মাইক্রোফোন পারমিশন প্রয়োজন"
            tvStatus.setTextColor(Color.parseColor("#EF4444"))
        }
    }

    private fun startListening() {
        stopListening()

        val prefs = getSharedPreferences("smart_keyboard_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_api_key", "") ?: ""

        if (apiKey.isNotBlank()) {
            startGeminiListening(apiKey)
        } else {
            startSystemSpeechRecognizer()
        }
    }

    private fun startGeminiListening(apiKey: String) {
        if (geminiAudioRecorder == null) {
            geminiAudioRecorder = GeminiAudioRecorder(this)
        }

        val started = geminiAudioRecorder?.startRecording() ?: false
        if (!started) {
            tvStatus.text = "⚠️ মাইক্রোফোন সক্রিয় করা যায়নি"
            tvStatus.setTextColor(Color.parseColor("#EF4444"))
            startSystemSpeechRecognizer()
            return
        }

        isGeminiRecording = true
        isListening = true
        tvStatus.text = "🎙️ শুনতেছি... কথা বলুন"
        tvStatus.setTextColor(Color.parseColor("#10B981"))
        btnMicIcon.background = GradientDrawable().apply {
            setColor(Color.parseColor("#EF4444"))
            shape = GradientDrawable.OVAL
            setStroke(6, Color.parseColor("#FFFFFF"))
        }

        geminiAudioRecorder?.audioLevelListener = object : GeminiAudioRecorder.AudioLevelListener {
            override fun onAudioLevel(rmsDb: Float) {
                mainHandler.post {
                    if (isGeminiRecording) {
                        val stroke = when {
                            rmsDb > 60 -> 12
                            rmsDb > 45 -> 8
                            else -> 4
                        }
                        btnMicIcon.background = GradientDrawable().apply {
                            setColor(Color.parseColor("#EF4444"))
                            shape = GradientDrawable.OVAL
                            setStroke(stroke, Color.parseColor("#FFFFFF"))
                        }
                    }
                }
            }
        }
    }

    private fun startSystemSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            tvStatus.text = "⚠️ স্পিচ রিকগনিশন সক্রিয় নয়। সেটিংসে Gemini API Key দিন।"
            tvStatus.setTextColor(Color.parseColor("#EF4444"))
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    tvStatus.text = "🎙️ শুনতেছি... কথা বলুন"
                    tvStatus.setTextColor(Color.parseColor("#10B981"))
                    btnMicIcon.background = GradientDrawable().apply {
                        setColor(Color.parseColor("#EF4444"))
                        shape = GradientDrawable.OVAL
                        setStroke(6, Color.parseColor("#FFFFFF"))
                    }
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    tvStatus.text = "⏳ প্রসেস হচ্ছে..."
                    tvStatus.setTextColor(Color.parseColor("#6366F1"))
                }

                override fun onError(error: Int) {
                    isListening = false
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "কোনো কথা শনাক্ত হয়নি। পুনরায় চেষ্টা করুন।"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "কোনো শব্দ পাওয়া যায়নি।"
                        SpeechRecognizer.ERROR_NETWORK -> "ইন্টারনেট কানেকশন চেক করুন।"
                        else -> "ভয়েস কোড: $error"
                    }
                    tvStatus.text = "⚠️ $msg"
                    tvStatus.setTextColor(Color.parseColor("#F59E0B"))
                    btnMicIcon.background = GradientDrawable().apply {
                        setColor(Color.parseColor("#1E293B"))
                        shape = GradientDrawable.OVAL
                        setStroke(6, Color.parseColor("#F59E0B"))
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        recognizedText = matches[0]
                        tvTranscript.text = recognizedText
                        tvTranscript.setTextColor(Color.parseColor("#F8FAFC"))
                        tvStatus.text = "✅ সম্পন্ন হয়েছে!"
                        tvStatus.setTextColor(Color.parseColor("#10B981"))
                        mainHandler.postDelayed({ commitAndFinish() }, 500)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        tvTranscript.text = matches[0]
                        tvTranscript.setTextColor(Color.parseColor("#F8FAFC"))
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, currentLang)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "শুনতেছি... কথা বলুন")
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            tvStatus.text = "ত্রুটি: ${e.message}"
        }
    }

    private fun stopListening() {
        if (isGeminiRecording) {
            isGeminiRecording = false
            isListening = false
            tvStatus.text = "⏳ Gemini AI ট্রান্সক্রাইব করছে..."
            tvStatus.setTextColor(Color.parseColor("#6366F1"))
            geminiAudioRecorder?.audioLevelListener = null

            val recorder = geminiAudioRecorder ?: return
            activityScope.launch {
                val wavBytes = recorder.stopRecording()
                val prefs = getSharedPreferences("smart_keyboard_prefs", Context.MODE_PRIVATE)
                val apiKey = prefs.getString("gemini_api_key", "") ?: ""
                val langMode = if (currentLang.startsWith("en")) "en" else "bn"

                val result = recorder.transcribeWithGemini(wavBytes, apiKey, langMode)
                result.onSuccess { text ->
                    recognizedText = text
                    tvTranscript.text = text
                    tvTranscript.setTextColor(Color.parseColor("#F8FAFC"))
                    tvStatus.text = "✅ সম্পন্ন হয়েছে!"
                    tvStatus.setTextColor(Color.parseColor("#10B981"))
                    btnMicIcon.background = GradientDrawable().apply {
                        setColor(Color.parseColor("#1E293B"))
                        shape = GradientDrawable.OVAL
                        setStroke(6, Color.parseColor("#10B981"))
                    }
                    mainHandler.postDelayed({ commitAndFinish() }, 400)
                }.onFailure { err ->
                    tvStatus.text = "ত্রুটি: ${err.message}"
                    tvStatus.setTextColor(Color.parseColor("#EF4444"))
                    btnMicIcon.background = GradientDrawable().apply {
                        setColor(Color.parseColor("#1E293B"))
                        shape = GradientDrawable.OVAL
                        setStroke(6, Color.parseColor("#EF4444"))
                    }
                }
            }
            return
        }

        isListening = false
        btnMicIcon.background = GradientDrawable().apply {
            setColor(Color.parseColor("#1E293B"))
            shape = GradientDrawable.OVAL
            setStroke(6, Color.parseColor("#10B981"))
        }
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (e: Exception) {}
    }

    private fun commitAndFinish() {
        if (isGeminiRecording || isListening) {
            stopListening()
        }
        val textToCommit = tvTranscript.text.toString().trim()
        if (textToCommit.isNotBlank() && !textToCommit.startsWith("আপনি যা বলবেন")) {
            val resultIntent = Intent(ACTION_VOICE_RESULT).apply {
                putExtra(EXTRA_VOICE_TEXT, textToCommit)
                setPackage(packageName)
            }
            sendBroadcast(resultIntent)
        }
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isGeminiRecording) {
            try {
                geminiAudioRecorder?.stopRecording()
            } catch (e: Exception) {}
        }
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {}
    }
}
