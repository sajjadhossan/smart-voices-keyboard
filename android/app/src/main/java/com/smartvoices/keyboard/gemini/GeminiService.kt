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

class GeminiService(private var apiKey: String) {

    fun updateApiKey(newKey: String) {
        this.apiKey = newKey
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
            try {
                if (apiKey.isBlank()) {
                    return@withContext Result.failure(Exception("Gemini API Key is not set"))
                }

                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 10000
                    readTimeout = 15000
                }

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

                val writer = OutputStreamWriter(connection.outputStream)
                writer.write(body.toString())
                writer.flush()
                writer.close()

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(connection.inputStream))
                    val response = reader.readText()
                    reader.close()

                    val jsonResp = JSONObject(response)
                    val candidates = jsonResp.getJSONArray("candidates")
                    if (candidates.length() > 0) {
                        val content = candidates.getJSONObject(0).getJSONObject("content")
                        val parts = content.getJSONArray("parts")
                        val outputText = parts.getJSONObject(0).getString("text").trim()
                        Result.success(outputText)
                    } else {
                        Result.failure(Exception("No candidate content returned"))
                    }
                } else {
                    val reader = BufferedReader(InputStreamReader(connection.errorStream))
                    val error = reader.readText()
                    reader.close()
                    Result.failure(Exception("Gemini API Error [$responseCode]: $error"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
