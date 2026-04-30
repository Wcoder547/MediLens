package com.example.medilens

/**
 * Stage 4 — Local validator. Runs entirely on-device, no network.
 *
 * ACCEPTS all medicines regardless of YOLO class — every medicine a doctor
 * writes gets saved. This validator only flags DATA QUALITY issues so the
 * user knows which cards to review carefully.
 *
 * Flag rules:
 *   RED   → medicineName blank  OR  timesPerDay ≤ 0  OR  confidence < 0.65
 *   AMBER → confidence < 0.85   OR  dose blank  OR  scheduleTimes empty  OR  duration blank
 *   GREEN → everything looks complete and confidence ≥ 0.85
 */
object PrescriptionValidator {

    private val YOLO_CLASSES = setOf("panadol", "risek", "myteka", "ventolin")

    fun validate(medicines: List<ParsedMedicine>): List<ParsedMedicine> {
        return medicines.map { medicine ->
            val flag = computeFlag(medicine)
            val status = computeVerificationStatus(medicine)
            medicine.copy(validationFlag = flag, verificationStatus = status)
        }
    }

    // ── Flag computation ──────────────────────────────────────────────────────

    private fun computeFlag(m: ParsedMedicine): ValidationFlag {
        // RED — critical fields missing or very low confidence
        if (m.medicineName.isBlank())    return ValidationFlag.RED
        if (m.timesPerDay <= 0)          return ValidationFlag.RED
        if (m.confidence < 0.65f)        return ValidationFlag.RED

        // AMBER — non-critical gaps or moderate confidence
        if (m.confidence < 0.85f)        return ValidationFlag.AMBER
        if (m.dose.isBlank())            return ValidationFlag.AMBER
        if (m.scheduleTimes.isEmpty())   return ValidationFlag.AMBER
        if (m.duration.isBlank())        return ValidationFlag.AMBER

        // GREEN — all good
        return ValidationFlag.GREEN
    }

    // ── Verification status computation ──────────────────────────────────────
    // Only the 4 YOLO classes get YOLO_VERIFIED.
    // Everything else is ENROLLMENT_PENDING (Phase 2 embedding can upgrade it).

    private fun computeVerificationStatus(m: ParsedMedicine): VerificationStatus {
        return if (YOLO_CLASSES.contains(m.medicineName.trim().lowercase())) {
            VerificationStatus.YOLO_VERIFIED
        } else {
            // Preserve EMBEDDING_ENROLLED if it was already set upstream (Phase 2 hook)
            if (m.verificationStatus == VerificationStatus.EMBEDDING_ENROLLED) {
                VerificationStatus.EMBEDDING_ENROLLED
            } else {
                VerificationStatus.ENROLLMENT_PENDING
            }
        }
    }
}
