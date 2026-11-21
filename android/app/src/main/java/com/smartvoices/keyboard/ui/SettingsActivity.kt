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
import androidx.lifecycle.lifecycleScope
import com.smartvoices.keyboard.R
import com.smartvoices.keyboard.clipboard.ClipboardDbHelper
import com.smartvoices.keyboard.gemini.GeminiService
import com.smartvoices.keyboard.update.AppUpdateManager
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQ_CODE = 1001
        private const val REQUEST_CODE_RESTORE = 2002
        const val ACTION_SETTINGS_UPDATED = "com.smartvoices.keyboard.SETTINGS_UPDATED"
    }

    private lateinit var tvPermissionStatus: TextView
    private lateinit var btnGrantPermission: Button
    private lateinit var tvApiStatus: TextView
    private lateinit var btnTestApi: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("smart_keyboard_prefs", Context.MODE_PRIVATE)
        val clipboardDb = ClipboardDbHelper(this)

        tvPermissionStatus = findViewById(R.id.tvPermissionStatus)
        btnGrantPermission = findViewById(R.id.btnGrantPermission)
        tvApiStatus = findViewById(R.id.tvApiStatus)
        btnTestApi = findViewById(R.id.btnTestApi)

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

        // Setup Themes Spinner (7 Modern Themes)
        val themeKeys = listOf("dark", "amoled", "light", "cyber", "ocean", "forest", "sunset")
        val themeNames = listOf(
            "Dark Slate (ডিফল্ট ডার্ক)",
            "AMOLED Black (পিউর ব্ল্যাক)",
            "Modern Light (উজ্জ্বল লাইট)",
            "Cyber Neon (সাইবার ভায়োলেট)",
            "Ocean Blue (ডিপ ওশান ব্লু)",
            "Forest Emerald (এমেরাল্ড গ্রিন)",
            "Sunset Flame (সানসেট অ্যাম্বার)"
        )
        val themeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, themeNames)
        spinnerThemes.adapter = themeAdapter

        val savedTheme = prefs.getString("theme", "dark") ?: "dark"
        val themeIndex = themeKeys.indexOf(savedTheme).coerceAtLeast(0)
        spinnerThemes.setSelection(themeIndex)

        // Instant Live Theme Preview on Selection
        var isInitialThemeSpinnerSetup = true
        spinnerThemes.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isInitialThemeSpinnerSetup) {
                    isInitialThemeSpinnerSetup = false
                    return
                }
                val selectedThemeKey = themeKeys[position]
                prefs.edit().putString("theme", selectedThemeKey).apply()
                val updateIntent = Intent(ACTION_SETTINGS_UPDATED).apply {
                    putExtra("theme", selectedThemeKey)
                    putExtra("gemini_api_key", prefs.getString("gemini_api_key", "") ?: "")
                    setPackage(packageName)
                }
                sendBroadcast(updateIntent)
                Toast.makeText(this@SettingsActivity, "থিম সেট করা হয়েছে: ${themeNames[position]} 🎨", Toast.LENGTH_SHORT).show()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Setup Default Mode Spinner
        val modeKeys = listOf("english", "banglish", "bangla")
        val modeNames = listOf("English (QWERTY)", "বাংলিশ ফোনেটিক (Banglish to Bangla)", "খাঁটি বাংলা ইউনিকোড (Bangla)")
        val modeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, modeNames)
        spinnerDefaultMode.adapter = modeAdapter

        val savedMode = prefs.getString("default_mode", "english") ?: "english"
        val modeIndex = modeKeys.indexOf(savedMode).coerceAtLeast(0)
        spinnerDefaultMode.setSelection(modeIndex)

        // Load existing preferences
        val initialApiKey = prefs.getString("gemini_api_key", "") ?: ""
        etApiKey.setText(initialApiKey)
        cbVibration.isChecked = prefs.getBoolean("enable_vibration", true)
        cbSound.isChecked = prefs.getBoolean("enable_sound", true)

        val isVerified = prefs.getBoolean("gemini_api_verified", false)
        if (initialApiKey.isNotBlank() && isVerified) {
            tvApiStatus.text = "🟢 Gemini API সক্রিয় ও প্রস্তুত (Active)"
            tvApiStatus.setTextColor(Color.parseColor("#10B981"))
        } else if (initialApiKey.isNotBlank()) {
            tvApiStatus.text = "🟡 API Key দেওয়া আছে (ভেরিফাই করা হয়নি)"
            tvApiStatus.setTextColor(Color.parseColor("#F59E0B"))
        } else {
            tvApiStatus.text = "⚪ API Key খালি (অনুবাদ নিষ্ক্রিয়)"
            tvApiStatus.setTextColor(Color.parseColor("#94A3B8"))
        }

        btnTestApi.setOnClickListener {
            val key = GeminiService.sanitizeKey(etApiKey.text.toString())
            if (key.isBlank()) {
                tvApiStatus.text = "🔴 অনুগ্রহ করে প্রথমে API Key লিখুন"
                tvApiStatus.setTextColor(Color.parseColor("#EF4444"))
                return@setOnClickListener
            }

            tvApiStatus.text = "⏳ Gemini API যাচাই করা হচ্ছে..."
            tvApiStatus.setTextColor(Color.parseColor("#6366F1"))
            btnTestApi.isEnabled = false

            lifecycleScope.launch {
                val service = GeminiService(key)
                val status = service.pingConnection()
                btnTestApi.isEnabled = true
                if (status.isConnected) {
                    tvApiStatus.text = "🟢 Gemini API সক্রিয় | ল্যাটেন্সি: ${status.latencyMs}ms | মডেল: ${status.activeModel}"
                    tvApiStatus.setTextColor(Color.parseColor("#10B981"))
                    prefs.edit()
                        .putString("gemini_api_key", key)
                        .putString("gemini_active_model", status.activeModel)
                        .putBoolean("gemini_api_verified", true)
                        .apply()
                    Toast.makeText(this@SettingsActivity, "Gemini API সক্রিয়! 🟢 (${status.activeModel})", Toast.LENGTH_SHORT).show()
                } else {
                    tvApiStatus.text = "🔴 এপিআই ত্রুটি: ${status.message}"
                    tvApiStatus.setTextColor(Color.parseColor("#EF4444"))
                    prefs.edit().putBoolean("gemini_api_verified", false).apply()
                    Toast.makeText(this@SettingsActivity, "API Key কাজ করছে না: ${status.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        btnEnable.setOnClickListener {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            startActivity(intent)
        }

        btnSelect.setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }

        btnSaveSettings.setOnClickListener {
            val key = GeminiService.sanitizeKey(etApiKey.text.toString())
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

        // Backup & Restore Actions
        val btnExportBackup = findViewById<Button>(R.id.btnExportBackup)
        val btnImportBackup = findViewById<Button>(R.id.btnImportBackup)

        btnExportBackup?.setOnClickListener {
            try {
                val backupJson = com.smartvoices.keyboard.backup.BackupManager.createBackupJson(this)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_SUBJECT, "Smart Voice Keyboard Backup")
                    putExtra(Intent.EXTRA_TEXT, backupJson)
                }
                startActivity(Intent.createChooser(shareIntent, "ব্যাকআপ শেয়ার বা সেভ করুন"))
                Toast.makeText(this, "ব্যাকআপ প্রস্তুত হয়েছে! 📥", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "ব্যাকআপ তৈরিতে ত্রুটি: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        btnImportBackup?.setOnClickListener {
            val filePickerIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(Intent.createChooser(filePickerIntent, "ব্যাকআপ ফাইল বেছে নিন"), REQUEST_CODE_RESTORE)
        }

        // In-App One-Click Auto-Update Engine
        val tvUpdateStatus = findViewById<TextView>(R.id.tvUpdateStatus)
        val tvReleaseNotes = findViewById<TextView>(R.id.tvReleaseNotes)
        val pbUpdateProgress = findViewById<ProgressBar>(R.id.pbUpdateProgress)
        val tvProgressPercent = findViewById<TextView>(R.id.tvProgressPercent)
        val btnCheckUpdate = findViewById<Button>(R.id.btnCheckUpdate)
        val btnDownloadInstall = findViewById<Button>(R.id.btnDownloadInstall)

        var pendingDownloadUrl = ""

        fun runCheckUpdate(showNoUpdateToast: Boolean = false) {
            tvUpdateStatus?.text = "⏳ সার্ভারে নতুন আপডেট যাচাই করা হচ্ছে..."
            tvUpdateStatus?.setTextColor(Color.parseColor("#38BDF8"))
            btnCheckUpdate?.isEnabled = false

            lifecycleScope.launch {
                val currentVer = "2.2.0"
                val result = AppUpdateManager.checkUpdate(currentVer)
                btnCheckUpdate?.isEnabled = true
                result.onSuccess { info ->
                    if (info.hasUpdate && info.apkDownloadUrl.isNotBlank()) {
                        pendingDownloadUrl = info.apkDownloadUrl
                        tvUpdateStatus?.text = "🎉 নতুন সংস্করণ উপলভ্য: v${info.latestVersion}"
                        tvUpdateStatus?.setTextColor(Color.parseColor("#10B981"))
                        tvReleaseNotes?.visibility = View.VISIBLE
                        tvReleaseNotes?.text = "রিলিজ নোট:\n${info.releaseNotesBn}"
                        btnDownloadInstall?.visibility = View.VISIBLE
                        btnDownloadInstall?.text = "এখনই আপডেট করুন (v${info.latestVersion}) 📥"
                        Toast.makeText(this@SettingsActivity, "নতুন আপডেট পাওয়া গেছে! 🟢 (v${info.latestVersion})", Toast.LENGTH_SHORT).show()
                    } else {
                        tvUpdateStatus?.text = "✅ আপনি সর্বশেষ সংস্করণ (v$currentVer) ব্যবহার করছেন • Release Signed"
                        tvUpdateStatus?.setTextColor(Color.parseColor("#10B981"))
                        tvReleaseNotes?.visibility = View.GONE
                        btnDownloadInstall?.visibility = View.GONE
                        if (showNoUpdateToast) {
                            Toast.makeText(this@SettingsActivity, "অ্যাপ সম্পূর্ণ আপ-টু-ডেট! ✨", Toast.LENGTH_SHORT).show()
                        }
                    }
                }.onFailure { err ->
                    tvUpdateStatus?.text = "আপডেট চেক ব্যর্থ: ${err.message}"
                    tvUpdateStatus?.setTextColor(Color.parseColor("#EF4444"))
                }
            }
        }

        btnCheckUpdate?.setOnClickListener {
            runCheckUpdate(showNoUpdateToast = true)
        }

        btnDownloadInstall?.setOnClickListener {
            if (pendingDownloadUrl.isBlank()) return@setOnClickListener
            btnDownloadInstall.isEnabled = false
            btnCheckUpdate?.isEnabled = false
            pbUpdateProgress?.visibility = View.VISIBLE
            tvProgressPercent?.visibility = View.VISIBLE
            pbUpdateProgress?.progress = 0
            tvProgressPercent?.text = "ডাউনলোড শুরু হচ্ছে..."

            lifecycleScope.launch {
                val dlResult = AppUpdateManager.downloadApk(this@SettingsActivity, pendingDownloadUrl) { progress ->
                    pbUpdateProgress?.progress = progress
                    tvProgressPercent?.text = "ডাউনলোড হচ্ছে: $progress%"
                }

                btnDownloadInstall.isEnabled = true
                btnCheckUpdate?.isEnabled = true

                dlResult.onSuccess { apkFile ->
                    tvProgressPercent?.text = "ডাউনলোড সম্পন্ন! ইনস্টলার চালু হচ্ছে... 🚀"
                    val installResult = AppUpdateManager.installApk(this@SettingsActivity, apkFile)
                    installResult.onFailure { err ->
                        Toast.makeText(this@SettingsActivity, "ইনস্টলেশন শুরু করা যায়নি: ${err.message}", Toast.LENGTH_LONG).show()
                    }
                }.onFailure { err ->
                    tvProgressPercent?.text = "ডাউনলোড ব্যর্থ: ${err.message}"
                    Toast.makeText(this@SettingsActivity, "ডাউনলোড ব্যর্থ: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        // Silent background check on launch
        runCheckUpdate(showNoUpdateToast = false)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_RESTORE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                val jsonString = contentResolver.openInputStream(uri)?.bufferedReader().use { it?.readText() } ?: ""
                val res = com.smartvoices.keyboard.backup.BackupManager.restoreFromJson(this, jsonString)
                res.onSuccess { count ->
                    Toast.makeText(this, "সফলভাবে $count টি আইটেম রিস্টোর হয়েছে! 🟢", Toast.LENGTH_LONG).show()
                    val updateIntent = Intent(ACTION_SETTINGS_UPDATED).apply {
                        setPackage(packageName)
                    }
                    sendBroadcast(updateIntent)
                }.onFailure { err ->
                    Toast.makeText(this, "রিস্টোর ব্যর্থ: ${err.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "ফাইল পড়তে সমস্যা হয়েছে: ${e.message}", Toast.LENGTH_LONG).show()
            }
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
