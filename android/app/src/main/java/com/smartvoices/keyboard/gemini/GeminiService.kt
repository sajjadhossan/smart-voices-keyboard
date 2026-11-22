package com.smartvoices.keyboard.gemini

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class ApiHealthStatus(
    val isConnected: Boolean,
    val latencyMs: Long = 0L,
    val message: String = "",
    val activeModel: String = "gemini-flash-lite-latest",
    val apiVersion: String = "v1beta"
)

class GeminiService(private var apiKey: String) {

    companion object {
        @Volatile var activeApiVersion: String = "v1beta"
        @Volatile var activeModelName: String = "gemini-flash-lite-latest"

        // Candidate fallback list ordered by tested Google AI Studio live availability
        val CANDIDATE_MODELS = listOf(
            Pair("v1beta", "gemini-flash-lite-latest"),
            Pair("v1beta", "gemini-3.6-flash"),
            Pair("v1beta", "gemini-3.5-flash"),
            Pair("v1beta", "gemini-3.5-flash-lite"),
            Pair("v1beta", "gemini-3.1-flash-lite"),
            Pair("v1", "gemini-flash-lite-latest"),
            Pair("v1beta", "gemini-flash-latest")
        )

        fun sanitizeKey(key: String): String {
            return key.trim().replace("\"", "").replace("'", "").filter { !it.isWhitespace() && it.code in 32..126 }
        }

        fun getEndpointUrl(apiVersion: String, model: String, apiKey: String): URL {
            val cleanKey = sanitizeKey(apiKey)
            val cleanModel = model.removePrefix("models/")
            return URL("https://generativelanguage.googleapis.com/$apiVersion/models/$cleanModel:generateContent?key=$cleanKey")
        }

        fun getActiveEndpointUrl(apiKey: String): URL {
            return getEndpointUrl(activeApiVersion, activeModelName, apiKey)
        }
    }

    fun updateApiKey(newKey: String) {
        this.apiKey = sanitizeKey(newKey)
    }

    suspend fun discoverActiveModel(): Pair<String, String>? {
        return withContext(Dispatchers.IO) {
            val cleanKey = sanitizeKey(apiKey)
            if (cleanKey.isBlank()) return@withContext null
            val versions = listOf("v1beta", "v1")
            for (ver in versions) {
                try {
                    val url = URL("https://generativelanguage.googleapis.com/$ver/models?key=$cleanKey")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        setRequestProperty("x-goog-api-key", cleanKey)
                        connectTimeout = 7000
                        readTimeout = 8000
                    }
                    if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                        val text = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(text)
                        val modelsArr = json.optJSONArray("models") ?: JSONArray()
                        val availableModels = mutableListOf<String>()
                        for (i in 0 until modelsArr.length()) {
                            val mObj = modelsArr.getJSONObject(i)
                            val mName = mObj.optString("name", "").removePrefix("models/")
                            val methods = mObj.optJSONArray("supportedGenerationMethods") ?: JSONArray()
                            var supportsGenerate = false
                            for (j in 0 until methods.length()) {
                                if (methods.optString(j) == "generateContent") {
                                    supportsGenerate = true
                                    break
                                }
                            }
                            if (supportsGenerate && mName.isNotBlank() &&
                                !mName.contains("2.5") && !mName.contains("1.5") &&
                                !mName.contains("tts") && !mName.contains("image") && 
                                !mName.contains("embedding") && !mName.contains("robotics") &&
                                !mName.contains("customtools")) {
                                availableModels.add(mName)
                            }
                        }

                        if (availableModels.isNotEmpty()) {
                            // Smart priority sorting: prefer flash-lite-latest, then flash-lite, then flash
                            availableModels.sortWith { a, b ->
                                fun score(name: String): Int {
                                    var s = 0
                                    if (name == "gemini-flash-lite-latest") s += 250
                                    else if (name.contains("flash-lite")) s += 100
                                    else if (name.contains("flash")) s += 50
                                    if (name.contains("3.6")) s += 45
                                    else if (name.contains("3.5")) s += 40
                                    else if (name.contains("3.1")) s += 35
                                    else if (name.contains("3.")) s += 30
                                    return -s
                                }
                                score(a).compareTo(score(b))
                            }

                            // Dynamic live-probe: verify with actual micro-request so deprecated models are skipped
                            for (candidate in availableModels.take(8)) {
                                try {
                                    val probeUrl = getEndpointUrl(ver, candidate, cleanKey)
                                    val probeConn = (probeUrl.openConnection() as HttpURLConnection).apply {
                                        requestMethod = "POST"
                                        setRequestProperty("Content-Type", "application/json")
                                        setRequestProperty("x-goog-api-key", cleanKey)
                                        doOutput = true
                                        connectTimeout = 4000
                                        readTimeout = 5000
                                    }
                                    val pingPayload = "{\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":\"OK\"}]}]}"
                                    OutputStreamWriter(probeConn.outputStream).use { it.write(pingPayload); it.flush() }
                                    if (probeConn.responseCode == HttpURLConnection.HTTP_OK) {
                                        activeApiVersion = ver
                                        activeModelName = candidate
                                        return@withContext Pair(ver, candidate)
                                    }
                                } catch (_: Exception) {}
                            }

                            // Fallback to top priority candidate
                            val chosen = availableModels.first()
                            activeApiVersion = ver
                            activeModelName = chosen
                            return@withContext Pair(ver, chosen)
                        }
                    }
                } catch (_: Exception) {}
            }
            null
        }
    }

    suspend fun pingConnection(): ApiHealthStatus {
        return withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            try {
                if (apiKey.isBlank()) {
                    return@withContext ApiHealthStatus(false, 0, "API Key খালি রাখা হয়েছে", activeModelName, activeApiVersion)
                }

                // 1. Try dynamic model discovery
                val discovered = discoverActiveModel()
                if (discovered != null) {
                    activeApiVersion = discovered.first
                    activeModelName = discovered.second
                }

                // 2. Perform verification ping with multi-model fallback cascade
                val result = callGemini("Reply with only the word OK", "ping")
                val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                if (result.isSuccess) {
                    ApiHealthStatus(
                        isConnected = true,
                        latencyMs = latency,
                        message = "কানেকশন সক্রিয় (মডেল: $activeModelName, ল্যাটেন্সি: ${latency}ms)",
                        activeModel = activeModelName,
                        apiVersion = activeApiVersion
                    )
                } else {
                    val err = result.exceptionOrNull()?.message ?: "কানেকশন ব্যর্থ"
                    ApiHealthStatus(false, latency, err, activeModelName, activeApiVersion)
                }
            } catch (e: Exception) {
                val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
                ApiHealthStatus(false, latency, e.message ?: "নেটওয়ার্ক ত্রুটি", activeModelName, activeApiVersion)
            }
        }
    }

    suspend fun verifyConnection(): Result<Boolean> {
        val status = pingConnection()
        return if (status.isConnected) {
            Result.success(true)
        } else {
            Result.failure(Exception(status.message))
        }
    }

    suspend fun translateText(text: String, sourceLang: String, targetLang: String): Result<String> {
        val direction = if (sourceLang == "en" && targetLang == "bn") {
            "English to fluent natural Bengali"
        } else {
            "Bengali to fluent natural English"
        }
        val prompt = "You are a professional real-time translation engine. Translate the following from $direction. Output ONLY the translated text without explanations, greetings, quotes, or markdown."
        return callGemini(prompt, text)
    }

    suspend fun executeSmartAction(text: String, actionType: String): Result<String> {
        val prompt = when (actionType) {
            "TRANSLATE_TO_BN" -> "Translate the text into natural, fluent Bengali. Output ONLY the translated text."
            "TRANSLATE_TO_EN" -> "Translate the text into fluent, natural English. Output ONLY the translated text."
            "FORMAL_EMAIL" -> "Convert this message into a complete, professional, courteous formal email including Subject and Body. Output ONLY the email."
            "FIX_GRAMMAR" -> "Correct all spelling mistakes, typos, and grammatical errors in Bengali or English. Output ONLY the polished text."
            "SUMMARIZE_BULLETS" -> "Summarize the text into clear bullet points. Output ONLY the bullets."
            else -> "Process the text clearly and accurately."
        }
        return callGemini(prompt, text)
    }

    private suspend fun callGemini(systemPrompt: String, input: String): Result<String> {
        return withContext(Dispatchers.IO) {
            val cleanKey = sanitizeKey(apiKey)
            if (cleanKey.isBlank()) {
                return@withContext Result.failure(Exception("Gemini API Key is not set"))
            }

            // Build request JSON payload once
            val body = JSONObject().apply {
                val contents = JSONArray().apply {
                    val userObj = JSONObject().apply {
                        put("role", "user")
                        val parts = JSONArray().apply {
                            put(JSONObject().put("text", "$systemPrompt\n\n$input"))
                        }
                        put("parts", parts)
                    }
                    put(userObj)
                }
                put("contents", contents)
            }
            val bodyString = body.toString()

            // Prepare list of candidates to try (active first, followed by others)
            val candidateList = mutableListOf<Pair<String, String>>()
            candidateList.add(Pair(activeApiVersion, activeModelName))
            for (c in CANDIDATE_MODELS) {
                if (c != Pair(activeApiVersion, activeModelName)) {
                    candidateList.add(c)
                }
            }

            var lastError = ""
            var lastCode = 0

            for ((version, model) in candidateList) {
                try {
                    val url = getEndpointUrl(version, model, cleanKey)
                    val connection = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("x-goog-api-key", cleanKey)
                        doOutput = true
                        connectTimeout = 8000
                        readTimeout = 12000
                    }

                    OutputStreamWriter(connection.outputStream).use { writer ->
                        writer.write(bodyString)
                        writer.flush()
                    }

                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val response = connection.inputStream.bufferedReader().use { it.readText() }
                        val jsonResp = JSONObject(response)
                        val candidates = jsonResp.optJSONArray("candidates")
                        if (candidates != null && candidates.length() > 0) {
                            val content = candidates.getJSONObject(0).optJSONObject("content")
                            val parts = content?.optJSONArray("parts")
                            val outputText = parts?.optJSONObject(0)?.optString("text", "")?.trim() ?: ""
                            
                            // Success! Save this working model as active
                            activeApiVersion = version
                            activeModelName = model
                            return@withContext Result.success(outputText)
                        } else {
                            return@withContext Result.failure(Exception("No candidate content returned"))
                        }
                    } else {
                        lastCode = responseCode
                        val errorText = try {
                            connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                        } catch (_: Exception) { "" }
                        lastError = errorText

                        // If 404 (Model not found) or 400 (unsupported), continue to next candidate
                        if (responseCode == HttpURLConnection.HTTP_NOT_FOUND || responseCode == HttpURLConnection.HTTP_BAD_REQUEST) {
                            continue
                        } else {
                            // Auth error (401/403) or quota error (429) -> don't try all candidates, break
                            break
                        }
                    }
                } catch (e: Exception) {
                    lastError = e.message ?: "Network error"
                }
            }

            val googleMsg = try {
                val errJson = JSONObject(lastError)
                errJson.optJSONObject("error")?.optString("message", "") ?: ""
            } catch (_: Exception) { "" }

            val friendlyMsg = if (googleMsg.contains("has not been used in project") || googleMsg.contains("is disabled")) {
                "গুগল ক্লাউড প্রজেক্টে Generative Language API সক্রিয় নেই। সমাধান: aistudio.google.com/apikey এ গিয়ে 'Create API key in new project' বেছে নিন।"
            } else if (lastCode == HttpURLConnection.HTTP_NOT_FOUND) {
                "গুগল জেমিনি মডেল পাওয়া যায়নি (Error 404)। রুট কজ: আপনার Google প্রজেক্টে 'Generative Language API' চালু (Enable) নেই। সমাধান: aistudio.google.com/apikey এ যান -> 'Create API Key' -> অবশ্যই 'Create API key in NEW project' সিলেক্ট করুন।"
            } else if (lastCode == HttpURLConnection.HTTP_UNAUTHORIZED || lastCode == HttpURLConnection.HTTP_FORBIDDEN) {
                "Gemini API Key অবৈধ অথবা অনুমতি নেই (Error $lastCode)। সঠিক কী নিশ্চিত করুন।"
            } else {
                if (googleMsg.isNotBlank()) "Gemini API Error [$lastCode]: $googleMsg"
                else "Gemini API Error [$lastCode]: $lastError"
            }

            Result.failure(Exception(friendlyMsg))
        }
    }
}
