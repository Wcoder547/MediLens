package com.example.medilens

/**
 * Stage 3 fallback — runs entirely offline when Gemini is unavailable.
 * Uses regex patterns and a Pakistani prescription abbreviation dictionary.
 * Confidence is capped at 0.60 to always trigger AMBER/RED review by user.
 *
 * This is intentionally conservative — it is better to show a RED card
 * that the user corrects than to silently save wrong data.
 */
object RuleEngineParser {

    private val YOLO_CLASSES = setOf("panadol", "risek", "myteka", "ventolin")

    // ── Frequency patterns ordered most-to-least specific ────────────────────
    private data class FreqEntry(
        val pattern: Regex,
        val timesPerDay: Int,
        val scheduleTimes: List<String>
    )

    private val FREQ_ENTRIES = listOf(
        FreqEntry(Regex("(?i)\\bQID\\b|4\\s*times?\\s*/\\s*day|four\\s*times"),
            4, listOf("08:00 AM", "12:00 PM", "04:00 PM", "08:00 PM")),
        FreqEntry(Regex("(?i)\\bTDS\\b|\\bTID\\b|3\\s*times?\\s*/\\s*day|three\\s*times|1\\s*\\+\\s*1\\s*\\+\\s*1"),
            3, listOf("08:00 AM", "02:00 PM", "08:00 PM")),
        FreqEntry(Regex("(?i)\\bBD\\b|\\bBID\\b|twice\\s*(daily|a\\s*day)?|2\\s*times?|1\\s*\\+\\s*0\\s*\\+\\s*1"),
            2, listOf("08:00 AM", "08:00 PM")),
        FreqEntry(Regex("(?i)\\bHS\\b|at\\s*bed\\s*time|bedtime|night|0\\s*\\+\\s*0\\s*\\+\\s*1|night\\s*only"),
            1, listOf("09:00 PM")),
        FreqEntry(Regex("(?i)\\bOD\\b|once\\s*(daily|a\\s*day)?|1\\s*\\+\\s*0\\s*\\+\\s*0|morning\\s*only"),
            1, listOf("08:00 AM")),
        FreqEntry(Regex("(?i)\\bdaily\\b|\\bonce\\b"),
            1, listOf("08:00 AM"))
    )

    // ── Dose pattern ──────────────────────────────────────────────────────────
    private val DOSE_PATTERN = Regex(
        """(?i)(\d+(?:\.\d+)?\s*(?:mg|mcg|g\b|iu|ml|mg/ml|mg/5ml|mg/10ml|mcg/puff))"""
    )

    // ── Duration pattern ──────────────────────────────────────────────────────
    private val DURATION_PATTERN = Regex(
        """(?i)(\d+)\s*(day|week|month)s?|(?:ongoing|continue(?:\s+until)?|till\s+better|long.?term)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Parses raw prescription text into a list of ParsedMedicine objects.
     * Returns at least one entry (may be low-confidence) so the user always
     * sees a confirmation screen rather than a silent failure.
     */
    fun parse(rawText: String): List<ParsedMedicine> {
        val medicines = mutableListOf<ParsedMedicine>()

        val lines = rawText.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        for (line in lines) {
            val medicine = tryParseLine(line)
            if (medicine != null) medicines.add(medicine)
        }

        // If nothing parsed at all, return a single RED placeholder so user
        // can manually fill in rather than seeing a blank confirmation screen.
        if (medicines.isEmpty()) {
            medicines.add(buildFallbackEntry(rawText))
        }

        return medicines
    }

    // ── Line parsing ──────────────────────────────────────────────────────────

    private fun tryParseLine(line: String): ParsedMedicine? {
        val doseMatch = DOSE_PATTERN.find(line) ?: return null

        // Medicine name is everything before the dose value
        val nameRaw = line.substring(0, doseMatch.range.first)
            .replace(Regex("[^A-Za-z0-9\\s\\-]"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.length > 1 }
            .joinToString(" ")

        if (nameRaw.length < 2) return null

        val medicineName = nameRaw.split(" ").joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }

        val dose = doseMatch.value.trim()
        val (timesPerDay, scheduleTimes) = detectFrequency(line)

        val durationMatch = DURATION_PATTERN.find(line)
        val duration = durationMatch?.value?.trim() ?: ""

        val instructions = detectInstructions(line)
        val form         = detectForm(line)

        val verificationStatus =
            if (YOLO_CLASSES.contains(medicineName.lowercase())) VerificationStatus.YOLO_VERIFIED
            else VerificationStatus.ENROLLMENT_PENDING

        // Rule engine always gets capped confidence to force user review
        return ParsedMedicine(
            medicineName       = medicineName,
            dose               = dose,
            form               = form,
            timesPerDay        = timesPerDay,
            scheduleTimes      = scheduleTimes,
            duration           = duration,
            instructions       = instructions,
            quantity           = 1,
            confidence         = 0.55f,
            verificationStatus = verificationStatus
        )
    }

    private fun detectFrequency(line: String): Pair<Int, List<String>> {
        for (entry in FREQ_ENTRIES) {
            if (entry.pattern.containsMatchIn(line)) {
                return Pair(entry.timesPerDay, entry.scheduleTimes)
            }
        }
        return Pair(1, listOf("08:00 AM")) // default once daily
    }

    private fun detectInstructions(line: String): String {
        return when {
            line.contains(Regex("(?i)after\\s*meal|\\bPC\\b"))         -> "after meals"
            line.contains(Regex("(?i)before\\s*meal|\\bAC\\b"))        -> "before meals"
            line.contains(Regex("(?i)empty\\s*stomach"))               -> "on empty stomach"
            line.contains(Regex("(?i)with\\s*food"))                   -> "with food"
            line.contains(Regex("(?i)\\bHS\\b|at\\s*bedtime|bedtime")) -> "at bedtime"
            else                                                        -> ""
        }
    }

    private fun detectForm(line: String): String {
        return when {
            line.contains(Regex("(?i)syrup|suspension|oral\\s*solution|\\bml\\b")) -> "syrup"
            line.contains(Regex("(?i)inhaler?|puff|\\bMDI\\b|spray"))              -> "inhaler"
            line.contains(Regex("(?i)inj(ection)?\\b|\\.?iv\\b|i\\.m\\.?"))        -> "injection"
            line.contains(Regex("(?i)cap(sule)?s?\\b"))                            -> "capsule"
            line.contains(Regex("(?i)drop(s)?\\b"))                                -> "drops"
            line.contains(Regex("(?i)cream|ointment|gel\\b|lotion"))               -> "cream"
            else                                                                    -> "tablet"
        }
    }

    private fun buildFallbackEntry(rawText: String): ParsedMedicine {
        return ParsedMedicine(
            medicineName       = "",         // blank → triggers RED
            dose               = "",
            form               = "tablet",
            timesPerDay        = 0,          // zero → triggers RED
            scheduleTimes      = emptyList(),
            duration           = "",
            instructions       = rawText.take(300),  // put raw text in instructions for reference
            quantity           = 1,
            confidence         = 0.1f,
            verificationStatus = VerificationStatus.ENROLLMENT_PENDING
        )
    }
}
