package com.smartvoices.keyboard.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.smartvoices.keyboard.R
import com.smartvoices.keyboard.clipboard.ClipboardDbHelper

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("smart_keyboard_prefs", Context.MODE_PRIVATE)
        val clipboardDb = ClipboardDbHelper(this)

        val btnEnable = findViewById<Button>(R.id.btnEnableKeyboard)
        val btnSelect = findViewById<Button>(R.id.btnSelectKeyboard)
        val etApiKey = findViewById<EditText>(R.id.etApiKey)
        val btnSaveSettings = findViewById<Button>(R.id.btnSaveSettings)
        val btnClearClipboard = findViewById<Button>(R.id.btnClearClipboard)
        val cbVibration = findViewById<CheckBox>(R.id.cbVibration)
        val cbSound = findViewById<CheckBox>(R.id.cbSound)
        val spinnerThemes = findViewById<Spinner>(R.id.spinnerThemes)
        val spinnerDefaultMode = findViewById<Spinner>(R.id.spinnerDefaultMode)

        // Setup Themes Spinner
        val themeKeys = listOf("dark", "amoled", "light", "cyber")
        val themeNames = listOf("Dark Slate (ডিফল্ট ডার্ক)", "AMOLED Black (পিউর ব্ল্যাক)", "Modern Light (উজ্জ্বল লাইট)", "Cyber Neon (সাইবার নিয়ন)")
        val themeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, themeNames)
        spinnerThemes.adapter = themeAdapter

        val savedTheme = prefs.getString("theme", "dark") ?: "dark"
        val themeIndex = themeKeys.indexOf(savedTheme).coerceAtLeast(0)
        spinnerThemes.setSelection(themeIndex)

        // Setup Default Mode Spinner
        val modeKeys = listOf("english", "banglish", "bangla")
        val modeNames = listOf("English (QWERTY)", "বাংলিশ ফোনেটিক (Banglish to Bangla)", "খাঁটি বাংলা ইউনিকোড (Bangla)")
        val modeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modeNames)
        spinnerDefaultMode.adapter = modeAdapter

        val savedMode = prefs.getString("default_mode", "english") ?: "english"
        val modeIndex = modeKeys.indexOf(savedMode).coerceAtLeast(0)
        spinnerDefaultMode.setSelection(modeIndex)

        // Load existing preferences
        etApiKey.setText(prefs.getString("gemini_api_key", ""))
        cbVibration.isChecked = prefs.getBoolean("enable_vibration", true)
        cbSound.isChecked = prefs.getBoolean("enable_sound", true)

        btnEnable.setOnClickListener {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            startActivity(intent)
        }

        btnSelect.setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }

        btnSaveSettings.setOnClickListener {
            val key = etApiKey.text.toString().trim()
            val selectedThemeKey = themeKeys[spinnerThemes.selectedItemPosition]
            val selectedModeKey = modeKeys[spinnerDefaultMode.selectedItemPosition]

            prefs.edit()
                .putString("gemini_api_key", key)
                .putString("theme", selectedThemeKey)
                .putString("default_mode", selectedModeKey)
                .putBoolean("enable_vibration", cbVibration.isChecked)
                .putBoolean("enable_sound", cbSound.isChecked)
                .apply()

            Toast.makeText(this, "সেটিংস সফলভাবে সংরক্ষিত হয়েছে!", Toast.LENGTH_SHORT).show()
        }

        btnClearClipboard.setOnClickListener {
            val cleared = clipboardDb.clearUnpinned()
            Toast.makeText(this, "$cleared টি অপিনকৃত ক্লিপ মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
        }
    }
}
