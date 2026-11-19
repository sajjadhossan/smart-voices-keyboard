package com.smartvoices.keyboard.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
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
        val btnSaveApiKey = findViewById<Button>(R.id.btnSaveApiKey)
        val btnClearClipboard = findViewById<Button>(R.id.btnClearClipboard)

        // Load existing API Key
        val savedKey = prefs.getString("gemini_api_key", "")
        etApiKey.setText(savedKey)

        btnEnable.setOnClickListener {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            startActivity(intent)
        }

        btnSelect.setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }

        btnSaveApiKey.setOnClickListener {
            val key = etApiKey.text.toString().trim()
            prefs.edit().putString("gemini_api_key", key).apply()
            Toast.makeText(this, "Gemini API Key সংরক্ষিত হয়েছে!", Toast.LENGTH_SHORT).show()
        }

        btnClearClipboard.setOnClickListener {
            val cleared = clipboardDb.clearUnpinned()
            Toast.makeText(this, "$cleared টি ক্লিপ মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
        }
    }
}
