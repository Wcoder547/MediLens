package com.example.medilens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class CaregiverSettingsActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_SMS = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_caregiver_settings)

        val etPhone  = findViewById<EditText>(R.id.etCaregiverPhone)
        val etApiKey = findViewById<EditText>(R.id.etCallMeBotKey)
        val btnSave  = findViewById<Button>(R.id.btnSaveCaregiver)
        val tvStatus = findViewById<TextView>(R.id.tvSaveStatus)

        // Pehle se saved values load karo
        etPhone.setText(CaregiverAlertManager.getCaregiverPhone(this) ?: "")
        etApiKey.setText(CaregiverAlertManager.getCallMeBotApiKey(this) ?: "")

        btnSave.setOnClickListener {
            val phone  = etPhone.text.toString().trim()
            val apiKey = etApiKey.text.toString().trim()

            if (phone.isEmpty()) {
                etPhone.error = "Phone number daalna zaroori hai"
                return@setOnClickListener
            }
            if (!phone.startsWith("+")) {
                etPhone.error = "Country code include karo, maslan +923001234567"
                return@setOnClickListener
            }

            CaregiverAlertManager.saveCaregiverPhone(this, phone)
            if (apiKey.isNotEmpty()) {
                CaregiverAlertManager.saveCallMeBotApiKey(this, apiKey)
            }

            requestSmsPermission()

            tvStatus.text       = "✅ Settings save ho gayi!"
            tvStatus.visibility = View.VISIBLE
        }
    }

    private fun requestSmsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.SEND_SMS),
                REQUEST_SMS
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_SMS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "SMS permission mil gayi ✅", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this,
                    "SMS permission nahi di — sirf WhatsApp alerts jayenge",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}