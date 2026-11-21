package com.smartvoices.keyboard.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val currentVersion: String,
    val apkDownloadUrl: String,
    val releaseNotesBn: String,
    val releaseNotesEn: String,
    val forceUpdate: Boolean
)

object AppUpdateManager {

    private const val VERSION_API_URL = "https://smart-voices-keyboard.vercel.app/api/app/version"

    suspend fun checkUpdate(currentVersion: String): Result<UpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val url = URL(VERSION_API_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
            }
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(text)
                val data = json.optJSONObject("data") ?: JSONObject()
                val latest = data.optString("latestVersion", currentVersion)
                val apkUrl = data.optString("apkDownloadUrl", "")
                val releaseNotesObj = data.optJSONObject("releaseNotes")
                val notesBn = releaseNotesObj?.optString("bn", "নতুন সংস্করণ উপলভ্য") ?: "নতুন সংস্করণ উপলভ্য"
                val notesEn = releaseNotesObj?.optString("en", "New version available") ?: "New version available"
                val forceUpdate = data.optBoolean("forceUpdate", false)

                val hasUpdate = isNewerVersion(currentVersion, latest)
                Result.success(
                    UpdateInfo(
                        hasUpdate = hasUpdate,
                        latestVersion = latest,
                        currentVersion = currentVersion,
                        apkDownloadUrl = apkUrl,
                        releaseNotesBn = notesBn,
                        releaseNotesEn = notesEn,
                        forceUpdate = forceUpdate
                    )
                )
            } else {
                Result.failure(Exception("সার্ভার থেকে ভার্সন তথ্য পাওয়া যায়নি [${conn.responseCode}]"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        try {
            val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
            val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
            val maxLen = maxOf(currentParts.size, latestParts.size)
            for (i in 0 until maxLen) {
                val c = currentParts.getOrElse(i) { 0 }
                val l = latestParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
        } catch (_: Exception) {}
        return false
    }

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val url = URL(downloadUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15000
                readTimeout = 30000
            }

            var redirectConn = conn
            var status = redirectConn.responseCode
            var redirects = 0
            while ((status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) && redirects < 5) {
                val newUrl = redirectConn.getHeaderField("Location")
                redirectConn = (URL(newUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                }
                status = redirectConn.responseCode
                redirects++
            }

            if (status != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(Exception("এপিকে ডাউনলোড ব্যর্থ হয়েছে (HTTP $status)"))
            }

            val totalBytes = redirectConn.contentLength
            val apkFile = File(context.cacheDir, "smartvoice-update.apk")
            if (apkFile.exists()) apkFile.delete()

            redirectConn.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (totalBytes > 0) {
                            val progress = ((totalRead * 100) / totalBytes).toInt().coerceIn(0, 100)
                            withContext(Dispatchers.Main) {
                                onProgress(progress)
                            }
                        }
                    }
                    output.flush()
                }
            }

            withContext(Dispatchers.Main) { onProgress(100) }
            Result.success(apkFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun installApk(activity: Activity, apkFile: File): Result<Boolean> {
        return try {
            if (!apkFile.exists()) {
                return Result.failure(Exception("ডাউনলোডকৃত এপিকে ফাইল পাওয়া যায়নি"))
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!activity.packageManager.canRequestPackageInstalls()) {
                    val permissionIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${activity.packageName}")
                    }
                    activity.startActivity(permissionIntent)
                    return Result.failure(Exception("অ্যাপ আপডেট ইনস্টল করার অনুমতি দিন এবং পুনরায় চেষ্টা করুন"))
                }
            }

            val apkUri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            activity.startActivity(installIntent)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
