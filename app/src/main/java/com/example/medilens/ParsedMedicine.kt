package com.example.medilens

/**
 * Represents a single medicine extracted from a prescription.
 * This is the intermediate data class used between Stage 2/3 and Stage 5/6.
 * It is NOT a Room entity — it gets mapped to PrescriptionEntity on save.
 */
data class ParsedMedicine(
    val medicineName: String,
    val dose: String,
    val form: String,                    // tablet / capsule / syrup / inhaler / injection
    val timesPerDay: Int,
    val scheduleTimes: List<String>,     // e.g. ["08:00 AM", "08:00 PM"]
    val duration: String,                // e.g. "7 days", "2 weeks", "ongoing"
    val instructions: String,            // e.g. "after meals", "" if none
    val quantity: Int,                   // units per dose
    val confidence: Float,               // 0.0 – 1.0 from Gemini or rule engine
    val verificationStatus: VerificationStatus = VerificationStatus.ENROLLMENT_PENDING,
    val validationFlag: ValidationFlag = ValidationFlag.GREEN
) {
    /**
     * Maps this parsed medicine to a PrescriptionEntity for Room DB insertion.
     * scheduleTimes[0] → time1, [1] → time2, [2] → time3 (max 3 slots).
     */
    fun toPrescriptionEntity(prescriptionName: String = "Scanned Prescription"): PrescriptionEntity {
        return PrescriptionEntity(
            prescriptionName = prescriptionName,
            drugName         = medicineName.trim(),
            dosageQuantity   = buildDosageQuantity(),
            totalDrugQuantity = buildTotalQuantity(),
            frequency        = buildFrequencyString(),
            time1            = scheduleTimes.getOrNull(0),
            time2            = scheduleTimes.getOrNull(1),
            time3            = scheduleTimes.getOrNull(2),
            verificationStatus = verificationStatus.name
        )
    }

    private fun buildDosageQuantity(): String {
        // e.g. "1 tablet of 500mg" or just "500mg" if no quantity info
        return if (dose.isNotBlank() && form.isNotBlank()) {
            "$quantity $form of $dose"
        } else if (dose.isNotBlank()) {
            "$quantity x $dose"
        } else {
            "$quantity $form"
        }
    }

    private fun buildTotalQuantity(): String {
        // Total = timesPerDay × duration days if parseable, else "See prescription"
        val durationDays = parseDurationDays()
        return if (durationDays > 0) {
            "${timesPerDay * durationDays * quantity} units"
        } else {
            if (duration.isNotBlank()) "Duration: $duration" else "See prescription"
        }
    }

    private fun buildFrequencyString(): String {
        return when (timesPerDay) {
            1    -> "Once daily"
            2    -> "Twice daily"
            3    -> "Three times daily"
            4    -> "Four times daily"
            else -> "$timesPerDay times daily"
        }
    }

    private fun parseDurationDays(): Int {
        val lower = duration.lowercase()
        val numMatch = Regex("(\\d+)").find(lower)?.groupValues?.get(1)?.toIntOrNull() ?: return 0
        return when {
            lower.contains("week")  -> numMatch * 7
            lower.contains("month") -> numMatch * 30
            lower.contains("day")   -> numMatch
            else                    -> 0
        }
    }
}

/**
 * Data quality flag set by PrescriptionValidator.
 * Controls the card border colour in PrescriptionConfirmActivity.
 */
enum class ValidationFlag {
    GREEN,  // confidence ≥ 0.85, all critical fields present
    AMBER,  // confidence 0.65–0.85, or non-critical fields missing
    RED     // confidence < 0.65, or critical field (name / timesPerDay) missing
}
