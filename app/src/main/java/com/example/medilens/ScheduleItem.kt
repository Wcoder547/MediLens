package com.example.medilens

data class ScheduleItem(
    val timeLabel: String,
    val time: String,
    val medications: List<MedicationDetail>,
    val prescriptionIds: List<Long>,
    var isCompleted: Boolean = false // Add this
)


data class MedicationDetail(
    val name: String,        // e.g., "Take 1 pill of Stalevo(125MG)"
    val prescriptionId: Long  // Changed to Long
)
