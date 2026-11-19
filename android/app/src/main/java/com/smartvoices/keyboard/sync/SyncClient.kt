package com.smartvoices.keyboard.sync

import android.content.Context
import android.os.Build
import com.smartvoices.keyboard.clipboard.ClipboardDbHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class SyncClient(private val context: Context, private val dbHelper: ClipboardDbHelper) {

    private val prefs = context.getSharedPreferences("smart_keyboard_prefs", Context.MODE_PRIVATE)

    suspend fun performSync(): Result<Int> {
        return withContext(Dispatchers.IO) {
            try {
                val serverUrl = prefs.getString("sync_server_url", "http://10.0.2.2:4000") ?: "http://10.0.2.2:4000"
                val authToken = prefs.getString("sync_auth_token", "") ?: ""

                if (authToken.isBlank()) {
                    return@withContext Result.failure(Exception("AUTH_TOKEN_NOT_CONFIGURED"))
                }

                val unsyncedClips = dbHelper.getAllClips().take(20)
                if (unsyncedClips.isEmpty()) {
                    return@withContext Result.success(0)
                }

                val clipsArray = JSONArray()
                for (clip in unsyncedClips) {
                    val obj = JSONObject().apply {
                        put("client_id", "clip_android_${clip.id}_${clip.createdAt}")
                        put("text", clip.text)
                        put("source_device", "android")
                        put("is_pinned", clip.isPinned)
                        put("copy_count", clip.copyCount)
                    }
                    clipsArray.put(obj)
                }

                val payload = JSONObject().apply {
                    put("clips", clipsArray)
                }

                val url = URL("$serverUrl/api/clipboard/sync")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Authorization", "Bearer $authToken")
                    doOutput = true
                    connectTimeout = 8000
                    readTimeout = 10000
                }

                val writer = OutputStreamWriter(conn.outputStream)
                writer.write(payload.toString())
                writer.flush()
                writer.close()

                val code = conn.responseCode
                if (code == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val resp = reader.readText()
                    reader.close()

                    val jsonResp = JSONObject(resp)
                    if (jsonResp.optBoolean("success", false)) {
                        Result.success(unsyncedClips.size)
                    } else {
                        Result.failure(Exception(jsonResp.optString("message", "Sync failed")))
                    }
                } else {
                    Result.failure(Exception("HTTP error [$code]"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
