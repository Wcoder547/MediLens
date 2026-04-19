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

        // 🔹 Convert medicine name BEFORE speaking
        val friendlyName = getTtsFriendlyMedicineName(medicationName)

        // English message (keep original name)
        val englishMessage = if (isAlarm) {
            "It is time to take your medication. Please take $dosage of $medicationName now."
        } else {
            "Reminder. You need to take $dosage of $medicationName in 5 minutes."
        }

        // 🔹 Urdu message (use phonetic name)
        val urduMessage = if (isAlarm) {
            "Da-waa lay-nay ka waqt ho gaya hai. Abhi $dosage $friendlyName lo."
        } else {
            "Yaad dahaani. Paanch minute mein $dosage $friendlyName layna hai."
        }

        queue.add(Pair(englishMessage, Locale.ENGLISH))
        queue.add(Pair(urduMessage, Locale("ur")))

        if (isReady) {
            flushQueue()
        }
    }


    fun speakMessage(message: String) {
        queue.clear()

        // 🔹 Apply phonetic conversion to known medicine names
        var processedMessage = message

        val medicines = listOf("panadol", "risek", "myteka", "ventolin")

        medicines.forEach { med ->
            val friendly = getTtsFriendlyMedicineName(med)
            processedMessage = processedMessage.replace(
                med,
                friendly,
                ignoreCase = true
            )
        }

        queue.add(Pair(processedMessage, Locale.ENGLISH))

        if (isReady) {
            flushQueue()
        }
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

    // 🔹 Convert medicine names into TTS-friendly Roman Urdu
    private fun getTtsFriendlyMedicineName(name: String): String {
        val clean = name.lowercase().trim()

        return when {
            clean.contains("panadol")  -> "Pana-dol"
            clean.contains("risek")    -> "Ra-esik"
            clean.contains("myteka")   -> "My-tee-ka"
            clean.contains("ventolin") -> "Ventolin"

            // 🔹 Fallback (generic pronunciation improvement)
            else -> clean
                .replace("a", "aa")
                .replace("e", "ee")
                .replace("i", "ee")
                .replace("o", "o")
                .replace("u", "oo")
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }


}