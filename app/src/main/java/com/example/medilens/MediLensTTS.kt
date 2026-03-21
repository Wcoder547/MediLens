package com.example.medilens

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class MediLensTTS(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var isReady = false
    private val TAG = "MediLensTTS"

    // Queue of messages to speak — (text, locale)
    private val queue = mutableListOf<Pair<String, Locale>>()

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isReady = true
                Log.d(TAG, "TTS initialized successfully")
                // Speak any queued messages
                flushQueue()
            } else {
                Log.e(TAG, "TTS initialization failed with status: $status")
            }
        }
    }

    fun speak(medicationName: String, dosage: String, time: String, isAlarm: Boolean) {
        queue.clear()

        // English message
        val englishMessage = if (isAlarm) {
            "It is time to take your medication. Please take $dosage of $medicationName now."
        } else {
            "Reminder. You need to take $dosage of $medicationName in 5 minutes."
        }

        // Urdu message (transliterated — TTS reads Roman Urdu well)
        val urduMessage = if (isAlarm) {
            "Dawai lainay ka waqt ho gaya hai. Abhi $dosage $medicationName lain."
        } else {
            "Yaad dahaani. Panch minute mein $dosage $medicationName lena hai."
        }

        queue.add(Pair(englishMessage, Locale.ENGLISH))
        queue.add(Pair(urduMessage, Locale("ur")))

        if (isReady) {
            flushQueue()
        }
        // If not ready yet, queue will flush automatically when TTS initializes
    }

    private fun flushQueue() {
        queue.forEachIndexed { index, (text, locale) ->
            val result = tts?.setLanguage(locale)

            // If Urdu is not supported, fall back to English for Urdu message
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

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }
}