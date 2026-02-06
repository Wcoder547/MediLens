package com.example.medilens

data class ScheduleItem(
    val timeLabel: String,  // e.g., "Morning medicine - 12:00 PM"
    val time: String,        // e.g., "12:00 PM" for sorting
    val medications: List<MedicationDetail>,
    val prescriptionIds: List<Long> = emptyList()  // Changed to Long
)

data class MedicationDetail(
    val name: String,        // e.g., "Take 1 pill of Stalevo(125MG)"
    val prescriptionId: Long  // Changed to Long
)
