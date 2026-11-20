package com.smartvoices.keyboard.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Base64
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class GeminiAudioRecorder(private val context: Context) {

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val audioDataStream = ByteArrayOutputStream()
    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null

    interface AudioLevelListener {
        fun onAudioLevel(rmsDb: Float)
    }

    var audioLevelListener: AudioLevelListener? = null

    fun isRecording(): Boolean = isRecording

    fun startRecording(): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return false
        }

        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(2048)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                return false
            }

            // Enable hardware noise suppression & echo canceler if available
            val audioSessionId = audioRecord?.audioSessionId ?: 0
            if (audioSessionId != 0) {
                if (NoiseSuppressor.isAvailable()) {
                    noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply { enabled = true }
                }
                if (AcousticEchoCanceler.isAvailable()) {
                    echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply { enabled = true }
                }
            }

            audioDataStream.reset()
            audioRecord?.startRecording()
            isRecording = true

            // Read buffer in background thread
            Thread {
                val buffer = ByteArray(bufferSize)
                while (isRecording && audioRecord != null) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        synchronized(audioDataStream) {
                            audioDataStream.write(buffer, 0, read)
                        }

                        // Calculate RMS level for live visualizer
                        var sum = 0.0
                        for (i in 0 until read step 2) {
                            if (i + 1 < read) {
                                val sample = (buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)
                                sum += sample * sample
                            }
                        }
                        val rms = Math.sqrt(sum / (read / 2.0))
                        val db = (20 * Math.log10(rms.coerceAtLeast(1.0))).toFloat()
                        audioLevelListener?.onAudioLevel(db)
                    }
                }
            }.start()

            return true
        } catch (e: Exception) {
            isRecording = false
            return false
        }
    }

    fun stopRecording(): ByteArray {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            noiseSuppressor?.release()
            echoCanceler?.release()
        } catch (e: Exception) {}

        synchronized(audioDataStream) {
            val pcmBytes = audioDataStream.toByteArray()
            audioDataStream.reset()
            return createWavFile(pcmBytes)
        }
    }

    /**
     * Wrap raw 16kHz 16-bit Mono PCM bytes into a standard WAV header
     */
    private fun createWavFile(pcmData: ByteArray): ByteArray {
        val totalAudioLen = pcmData.size
        val totalDataLen = totalAudioLen + 36
        val longSampleRate = sampleRate.toLong()
        val channels = 1
        val byteRate = (16 * sampleRate * channels / 8).toLong()

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte() // 'fmt ' chunk
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // 4 bytes: size of 'fmt ' chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // format = 1 (PCM)
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (longSampleRate and 0xff).toByte()
        header[25] = ((longSampleRate shr 8) and 0xff).toByte()
        header[26] = ((longSampleRate shr 16) and 0xff).toByte()
        header[27] = ((longSampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * 16 / 8).toByte() // block align
        header[33] = 0
        header[34] = 16 // bits per sample
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        val wavOutput = ByteArrayOutputStream()
        wavOutput.write(header)
        wavOutput.write(pcmData)
        return wavOutput.toByteArray()
    }

    /**
     * Direct Gemini 1.5 Flash Multimodal Audio Speech-to-Text Transcription
     */
    suspend fun transcribeWithGemini(audioWavBytes: ByteArray, apiKey: String, langMode: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (apiKey.isBlank()) {
                    return@withContext Result.failure(Exception("Gemini API Key missing"))
                }
                if (audioWavBytes.isEmpty()) {
                    return@withContext Result.failure(Exception("No audio recorded"))
                }

                val base64Audio = Base64.encodeToString(audioWavBytes, Base64.NO_WRAP)
                val langPrompt = if (langMode == "en") {
                    "Transcribe this audio in English. Maintain perfect capitalization and punctuation."
                } else {
                    "Transcribe this audio in natural Bengali (or mixed Bengali-English if spoken). Add appropriate punctuation like দাড়ি (।) or প্রশ্নবোধক চিহ্ন (?)."
                }

                val systemPrompt = "You are an enterprise speech recognition engine. $langPrompt Output ONLY the exact transcribed text without quotes, formatting, or commentary."

                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 12000
                    readTimeout = 20000
                }

                val body = JSONObject().apply {
                    val contents = JSONArray().apply {
                        val userObj = JSONObject().apply {
                            put("role", "user")
                            val parts = JSONArray().apply {
                                // 1. System instruction text part
                                put(JSONObject().put("text", systemPrompt))
                                // 2. Inline audio data part (WAV)
                                val inlineData = JSONObject().apply {
                                    put("mimeType", "audio/wav")
                                    put("data", base64Audio)
                                }
                                put(JSONObject().put("inlineData", inlineData))
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
                        val text = parts.getJSONObject(0).getString("text").trim()
                        Result.success(text)
                    } else {
                        Result.failure(Exception("No transcription returned from Gemini"))
                    }
                } else {
                    val reader = BufferedReader(InputStreamReader(connection.errorStream))
                    val error = reader.readText()
                    reader.close()
                    Result.failure(Exception("Gemini STT Error [$responseCode]: $error"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
