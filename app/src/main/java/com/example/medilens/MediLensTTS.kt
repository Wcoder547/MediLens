package com.example.medilens

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class MediLensTTS(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false
    private val TAG = "MediLensTTS"

    private val queue = mutableListOf<Pair<String, Locale>>()

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                Log.d(TAG, "TTS initialized successfully")
                flushQueue()
            } else {
                Log.e(TAG, "TTS initialization failed with status: $status")
            }
        }
    }

    fun speak(medicationName: String, dosage: String, time: String, isAlarm: Boolean) {
        queue.clear()

        val friendlyName = getTtsFriendlyMedicineName(medicationName)

        val englishMessage = if (isAlarm) {
            "It is time to take your medication. Please take $dosage of $medicationName now."
        } else {
            "Reminder. You need to take $dosage of $medicationName in 5 minutes."
        }

        // اصل اردو
        val urduMessage = if (isAlarm) {
            "دوائی لینے کا وقت ہو گیا ہے۔ ابھی $dosage $friendlyName لیں۔"
        } else {
            "یاد دہانی۔ پانچ منٹ میں $dosage $friendlyName لینا ہے۔"
        }

        queue.add(Pair(englishMessage, Locale.ENGLISH))
        queue.add(Pair(urduMessage, Locale("ur")))

        if (isReady) flushQueue()
    }

    fun speakMessage(message: String) {
        queue.clear()

        var processedMessage = message
        val medicines = listOf("panadol", "risek", "myteka", "ventolin")
        medicines.forEach { med ->
            val friendly = getTtsFriendlyMedicineName(med)
            processedMessage = processedMessage.replace(med, friendly, ignoreCase = true)
        }

        queue.add(Pair(processedMessage, Locale("ur")))

        if (isReady) flushQueue()
    }

    private fun flushQueue() {
        queue.forEachIndexed { index, (text, locale) ->
            val result = tts?.setLanguage(locale)

            val finalLocale = if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Locale $locale not supported, falling back to English")
                tts?.setLanguage(Locale.ENGLISH)
                Locale.ENGLISH
            } else {
                locale
            }

            val utteranceId = "medilens_tts_$index"

            tts?.speak(
                text,
                if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                null,
                utteranceId
            )

            Log.d(TAG, "Speaking [$finalLocale]: $text")
        }
        queue.clear()
    }

    fun speakBoth(englishText: String, urduText: String) {
        queue.clear()
        if (englishText.isNotBlank()) {
            queue.add(Pair(englishText, Locale.ENGLISH))
        }
        if (urduText.isNotBlank()) {
            queue.add(Pair(urduText, Locale("ur")))
        }
        if (isReady) flushQueue()
    }

    private fun getTtsFriendlyMedicineName(name: String): String {
        val clean = name.lowercase().trim()

        return when {
            clean.contains("panadol")  -> "پیناڈول"
            clean.contains("risek")    -> "رائیسک"
            clean.contains("myteka")   -> "مائیٹیکا"
            clean.contains("ventolin") -> "وینٹولن"
            else -> name
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}