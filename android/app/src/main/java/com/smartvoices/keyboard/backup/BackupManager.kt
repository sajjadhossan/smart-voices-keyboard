package com.smartvoices.keyboard.backup

import android.content.Context
import com.smartvoices.keyboard.clipboard.ClipEntry
import com.smartvoices.keyboard.clipboard.ClipboardDbHelper
import com.smartvoices.keyboard.security.SecurityHelper
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

object BackupManager {

    private const val APP_IDENTIFIER = "smart-voices-keyboard"
    private const val SCHEMA_VERSION = "1.7.0"

    /**
     * Create exportable JSON backup string
     */
    fun createBackupJson(context: Context, passphrase: String? = null): String {
        val prefs = context.getSharedPreferences("smart_keyboard_prefs", Context.MODE_PRIVATE)
        val dbHelper = ClipboardDbHelper(context)
        val clips = dbHelper.getAllClips()

        val settingsObj = JSONObject().apply {
            put("theme", prefs.getString("theme", "dark") ?: "dark")
            put("default_mode", prefs.getString("default_mode", "english") ?: "english")
            put("gemini_api_key", prefs.getString("gemini_api_key", "") ?: "")
            put("gemini_api_verified", prefs.getBoolean("gemini_api_verified", false))
            put("enable_vibration", prefs.getBoolean("enable_vibration", true))
            put("enable_sound", prefs.getBoolean("enable_sound", true))
        }

        val clipsArray = JSONArray()
        for (c in clips) {
            val clipJson = JSONObject().apply {
                put("id", c.id)
                put("text", c.text)
                put("is_pinned", c.isPinned)
                put("copy_count", c.copyCount)
                put("created_at", c.createdAt)
            }
            clipsArray.put(clipJson)
        }

        val dataObj = JSONObject().apply {
            put("settings", settingsObj)
            put("clips", clipsArray)
            put("user_dictionary", JSONArray())
        }

        val backupObj = JSONObject().apply {
            put("app", APP_IDENTIFIER)
            put("version", SCHEMA_VERSION)
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            put("exported_at", sdf.format(Date()))
        }

        if (!passphrase.isNullOrBlank()) {
            val encrypted = SecurityHelper.encrypt(dataObj.toString(), passphrase)
            backupObj.put("encrypted", true)
            backupObj.put("payload", encrypted)
        } else {
            backupObj.put("encrypted", false)
            backupObj.put("data", dataObj)
        }

        return backupObj.toString(2)
    }

    /**
     * Restore settings and clips from a backup JSON string
     */
    fun restoreFromJson(context: Context, backupJsonStr: String, passphrase: String? = null): Result<Int> {
        return try {
            val backupObj = JSONObject(backupJsonStr)
            val app = backupObj.optString("app", "")
            if (app != APP_IDENTIFIER) {
                return Result.failure(Exception("ফাইলটি এই অ্যাপ্লিকেশনের ব্যাকআপ নয়"))
            }

            val isEncrypted = backupObj.optBoolean("encrypted", false)
            val dataObj = if (isEncrypted) {
                if (passphrase.isNullOrBlank()) {
                    return Result.failure(Exception("এনক্রিপ্টেড ব্যাকআপ রিস্টোর করতে পাসওয়ার্ড প্রয়োজন"))
                }
                val payload = backupObj.getString("payload")
                val decrypted = SecurityHelper.decrypt(payload, passphrase)
                JSONObject(decrypted)
            } else {
                backupObj.getJSONObject("data")
            }

            // 1. Restore Settings
            if (dataObj.has("settings")) {
                val settings = dataObj.getJSONObject("settings")
                val prefs = context.getSharedPreferences("smart_keyboard_prefs", Context.MODE_PRIVATE)
                val editor = prefs.edit()
                if (settings.has("theme")) editor.putString("theme", settings.getString("theme"))
                if (settings.has("default_mode")) editor.putString("default_mode", settings.getString("default_mode"))
                if (settings.has("gemini_api_key")) editor.putString("gemini_api_key", settings.getString("gemini_api_key"))
                if (settings.has("gemini_api_verified")) editor.putBoolean("gemini_api_verified", settings.getBoolean("gemini_api_verified"))
                if (settings.has("enable_vibration")) editor.putBoolean("enable_vibration", settings.getBoolean("enable_vibration"))
                if (settings.has("enable_sound")) editor.putBoolean("enable_sound", settings.getBoolean("enable_sound"))
                editor.apply()
            }

            // 2. Restore Clips
            var importedCount = 0
            if (dataObj.has("clips")) {
                val dbHelper = ClipboardDbHelper(context)
                val clipsArray = dataObj.getJSONArray("clips")
                for (i in 0 until clipsArray.length()) {
                    val c = clipsArray.getJSONObject(i)
                    val text = c.optString("text", "")
                    val isPinned = c.optBoolean("is_pinned", false)
                    if (text.isNotBlank()) {
                        val id = dbHelper.insertOrUpdateClip(text)
                        if (isPinned && id > 0) {
                            dbHelper.togglePin(id, true)
                        }
                        importedCount++
                    }
                }
            }

            Result.success(importedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
