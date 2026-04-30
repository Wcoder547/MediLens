package com.example.medilens

enum class VerificationStatus {
    YOLO_VERIFIED,       // panadol / risek / myteka / ventolin
                         // camera verification works immediately
    EMBEDDING_ENROLLED,  // user added photos via MyPillsActivity (Phase 2)
                         // camera verification works immediately
    ENROLLMENT_PENDING   // saved but no photos yet
                         // reminders work fully, camera verification not yet
}
