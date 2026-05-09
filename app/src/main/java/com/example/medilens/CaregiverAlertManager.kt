package com.example.medilens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

object CaregiverAlertManager {

    private const val TAG                  = "CaregiverAlertManager"
    private const val PREF_NAME            = "medilens_caregiver"
    private const val KEY_PHONE            = "caregiver_phone"
    private const val KEY_CMB_APIKEY       = "callmebot_apikey"
    private const val KEY_DAILY_MISS_DATE  = "daily_miss_date"
    private const val KEY_DAILY_MISS_COUNT = "daily_miss_count"
    private const val DAILY_MISS_THRESHOLD = 2

    fun saveCaregiverPhone(context: Context, phone: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_PHONE, phone).apply()
    }

    fun getCaregiverPhone(context: Context): String? =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PHONE, null)

    fun saveCallMeBotApiKey(context: Context, apiKey: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_CMB_APIKEY, apiKey).apply()
    }

    fun getCallMeBotApiKey(context: Context): String? =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CMB_APIKEY, null)

    suspend fun checkAndAlert(
        context: Context,
        score: AdherenceScoreEntity,
        drugName: String,
        scheduledTime: String
    ): Boolean {
        val phone = getCaregiverPhone(context) ?: run {
            Log.w(TAG, "No caregiver phone — skipping")
            return false
        }

        val todayMissCount = incrementDailyMissCount(context)
        Log.d(TAG, "Daily miss count: $todayMissCount")

        if (todayMissCount < DAILY_MISS_THRESHOLD) {
            Log.d(TAG, "Count $todayMissCount < threshold — no alert")
            return false
        }

        val message = "MediLens Alert: Patient ne aaj $todayMissCount baar dawai miss ki. " +
                "Aakhri miss: $scheduledTime wali $drugName. " +
                "Meherbani karke patient se rabta karein."

        val waSent = tryWhatsAppCallMeBot(context, phone, message)
        if (!waSent) {
            Log.w(TAG, "CallMeBot failed — SMS fallback")
            return sendSms(context, phone, message)
        }
        return true
    }

    private fun incrementDailyMissCount(context: Context): Int {
        val prefs   = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val today   = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val saved   = prefs.getString(KEY_DAILY_MISS_DATE, "")
        val current = if (saved == today) prefs.getInt(KEY_DAILY_MISS_COUNT, 0) else 0
        val newCount = current + 1
        prefs.edit()
            .putString(KEY_DAILY_MISS_DATE, today)
            .putInt(KEY_DAILY_MISS_COUNT, newCount)
            .apply()
        return newCount
    }

    private suspend fun tryWhatsAppCallMeBot(
        context: Context, phone: String, message: String
    ): Boolean {
        val apiKey = getCallMeBotApiKey(context)
        if (apiKey.isNullOrBlank()) return false
        return withContext(Dispatchers.IO) {
            try {
                val clean   = phone.replace(" ", "").replace("-", "")
                val encoded = URLEncoder.encode(message, "UTF-8")
                val url     = "https://api.callmebot.com/whatsapp.php?phone=$clean&text=$encoded&apikey=$apiKey"
                val resp    = URL(url).readText()
                resp.contains("Message queued", ignoreCase = true) ||
                        resp.contains("OK", ignoreCase = true)
            } catch (e: Exception) {
                Log.e(TAG, "CallMeBot failed: ${e.message}")
                false
            }
        }
    }

    private fun sendSms(context: Context, phone: String, message: String): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "SEND_SMS permission missing")
            return false
        }
        return try {
            val sms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S)
                context.getSystemService(SmsManager::class.java)
            else
                @Suppress("DEPRECATION") SmsManager.getDefault()
            val parts = sms.divideMessage(message)
            sms.sendMultipartTextMessage(phone, null, parts, null, null)
            Log.d(TAG, "SMS sent to $phone")
            true
        } catch (e: Exception) {
            Log.e(TAG, "SMS failed: ${e.message}")
            false
        }
    }
}