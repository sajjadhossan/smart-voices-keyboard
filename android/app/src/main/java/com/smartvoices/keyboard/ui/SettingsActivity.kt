package com.smartvoices.keyboard.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.smartvoices.keyboard.R
import com.smartvoices.keyboard.clipboard.ClipboardDbHelper

class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQ_CODE = 1001
        const val ACTION_SETTINGS_UPDATED = "com.smartvoices.keyboard.SETTINGS_UPDATED"
    }

    private lateinit var tvPermissionStatus: TextView
    private lateinit var btnGrantPermission: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("smart_keyboard_prefs", Context.MODE_PRIVATE)
        val clipboardDb = ClipboardDbHelper(this)

        tvPermissionStatus = findViewById(R.id.tvPermissionStatus)
        btnGrantPermission = findViewById(R.id.btnGrantPermission)
        val btnEnable = findViewById<Button>(R.id.btnEnableKeyboard)
        val btnSelect = findViewById<Button>(R.id.btnSelectKeyboard)
        val etApiKey = findViewById<EditText>(R.id.etApiKey)
        val btnSaveSettings = findViewById<Button>(R.id.btnSaveSettings)
        val btnClearClipboard = findViewById<Button>(R.id.btnClearClipboard)
        val cbVibration = findViewById<CheckBox>(R.id.cbVibration)
        val cbSound = findViewById<CheckBox>(R.id.cbSound)
        val spinnerThemes = findViewById<Spinner>(R.id.spinnerThemes)
        val spinnerDefaultMode = findViewById<Spinner>(R.id.spinnerDefaultMode)

        // Check & auto-request audio permission immediately on open
        checkAndRequestAudioPermission()

        btnGrantPermission.setOnClickListener {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQ_CODE
            )
        }

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
                .putLong("last_updated_at", System.currentTimeMillis())
                .apply()

            // Broadcast update to SmartKeyboardService
            val updateIntent = Intent(ACTION_SETTINGS_UPDATED).apply {
                putExtra("gemini_api_key", key)
                putExtra("theme", selectedThemeKey)
                putExtra("default_mode", selectedModeKey)
                setPackage(packageName)
            }
            sendBroadcast(updateIntent)

            Toast.makeText(this, "সেটিংস ও এপিআই কি সফলভাবে সংরক্ষিত হয়েছে!", Toast.LENGTH_SHORT).show()
        }

        btnClearClipboard.setOnClickListener {
            val cleared = clipboardDb.clearUnpinned()
            Toast.makeText(this, "$cleared টি অপিনকৃত ক্লিপ মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionUI()
    }

    private fun checkAndRequestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                PERMISSION_REQ_CODE
            )
        }
        updatePermissionUI()
    }

    private fun updatePermissionUI() {
        val isGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (isGranted) {
            tvPermissionStatus.text = "✅ মাইক্রোফোন পারমিশন সক্রিয় (ভয়েস টাইপিং সম্পূর্ণ প্রস্তুত)"
            tvPermissionStatus.setTextColor(Color.parseColor("#10B981"))
            btnGrantPermission.visibility = View.GONE
        } else {
            tvPermissionStatus.text = "⚠️ মাইক্রোফোন পারমিশন এখনো দেওয়া হয়নি (ভয়েস টাইপিং বন্ধ থাকবে)"
            tvPermissionStatus.setTextColor(Color.parseColor("#F59E0B"))
            btnGrantPermission.visibility = View.VISIBLE
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQ_CODE) {
            updatePermissionUI()
            val isGranted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (isGranted) {
                Toast.makeText(this, "ভয়েস ইনপুট পারমিশন সক্রিয় হয়েছে!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "ভয়েস অপশন কাজ করার জন্য মাইক্রোফোন পারমিশন প্রয়োজন", Toast.LENGTH_LONG).show()
            }
        }
    }
}
