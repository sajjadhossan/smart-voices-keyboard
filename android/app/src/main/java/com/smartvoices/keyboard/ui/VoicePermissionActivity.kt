package com.smartvoices.keyboard.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class VoicePermissionActivity : AppCompatActivity() {

    companion object {
        const val PERMISSION_REQUEST_CODE = 2026
        const val ACTION_PERMISSION_RESULT = "com.smartvoices.keyboard.PERMISSION_RESULT"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            sendResult(true)
            finish()
            return
        }

        // Request audio record permission directly
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val isGranted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (isGranted) {
                Toast.makeText(this, "ভয়েস ইনপুট পারমিশন সক্রিয় করা হয়েছে!", Toast.LENGTH_SHORT).show()
                sendResult(true)
            } else {
                Toast.makeText(this, "ভয়েস টাইপিংয়ের জন্য মাইক্রোফোন পারমিশন প্রয়োজন", Toast.LENGTH_LONG).show()
                sendResult(false)
            }
            finish()
        }
    }

    private fun sendResult(granted: Boolean) {
        val intent = Intent(ACTION_PERMISSION_RESULT).apply {
            putExtra("granted", granted)
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }
}
