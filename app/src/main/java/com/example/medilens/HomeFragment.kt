package com.example.medilens

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {

    private lateinit var db: AppDatabase
    private lateinit var tvDate: TextView
    private lateinit var tvGreeting: TextView
    private lateinit var rvDailySchedule: RecyclerView
    private lateinit var emptyStateSchedule: LinearLayout
    private lateinit var scheduleAdapter: DailyScheduleAdapter
    private val btnTakeNow get() = (activity as? HomeActivity)?.btnTakeNow

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = AppDatabase.getDatabase(requireContext())

        tvDate = view.findViewById(R.id.tvDate)
        tvGreeting = view.findViewById(R.id.tvGreeting)
        rvDailySchedule = view.findViewById(R.id.rvDailySchedule)
        emptyStateSchedule = view.findViewById(R.id.emptyStateSchedule)

        setupUI()
        setupRecyclerView()
        loadDailySchedule()
        rescheduleAllAlarms()

    }

    override fun onResume() {
        super.onResume()
        // Refresh schedule when returning to home
        loadDailySchedule()
    }

    private fun setupUI() {
        // Set current date in format: WED 6 FEB
        val dateFormat = SimpleDateFormat("EEE d MMM", Locale.ENGLISH)
        tvDate.text = dateFormat.format(Date()).uppercase(Locale.getDefault())

        // Set dynamic greeting based on time of day
        val greeting = getDynamicGreeting()
        val userName = getUserName()
        tvGreeting.text = "$greeting, $userName"
    }

    private fun getDynamicGreeting(): String {
        val calendar = Calendar.getInstance()
        return when (calendar.get(Calendar.HOUR_OF_DAY)) {
            in 0..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..20 -> "Good evening"
            else -> "Good night"
        }
    }

    private fun getUserName(): String {
        val currentUser = FirebaseAuth.getInstance().currentUser
        return currentUser?.displayName?.split(" ")?.firstOrNull() ?: "User"
    }

    private fun setupRecyclerView() {
        scheduleAdapter = DailyScheduleAdapter { scheduleItem ->
            onScheduleItemClicked(scheduleItem)
        }

        rvDailySchedule.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = scheduleAdapter
        }
    }

    private fun loadDailySchedule() {
        lifecycleScope.launch {
            val currentDate    = getCurrentDate()
            val currentCal     = Calendar.getInstance()
            val currentMinutes = currentCal.get(Calendar.HOUR_OF_DAY) * 60 +
                    currentCal.get(Calendar.MINUTE)

            db.prescriptionDao().getAllPrescriptions().collect { prescriptions ->
                val scheduleItems = generateDailySchedule(prescriptions)

                for (item in scheduleItems) {
                    item.isCompleted = isTaskCompleted(item, currentDate)
                }

                if (scheduleItems.isEmpty()) {
                    emptyStateSchedule.visibility = View.VISIBLE
                    rvDailySchedule.visibility    = View.GONE
                    btnTakeNow?.visibility        = View.GONE
                } else {
                    emptyStateSchedule.visibility = View.GONE
                    rvDailySchedule.visibility    = View.VISIBLE
                    scheduleAdapter.updateList(scheduleItems)
                }

                // ── FIXED: Priority-based slot selection ──────────────────
                // Priority 1: slot whose window contains RIGHT NOW (±30 min)
                // Priority 2: most recent PAST pending slot (overdue, still ok)
                // Never show FUTURE slots that haven't arrived yet

                val pendingItems = scheduleItems.filter { !it.isCompleted }

                // ── Group slots that are within 60 minutes of each other into "sessions"
// So "Morning medicine 08:00 AM" + "08:30 AM" → one combined Take Now
                fun groupIntoSessions(items: List<ScheduleItem>): List<List<ScheduleItem>> {
                    val sorted = items.sortedBy { parseTime(it.time) }
                    val sessions = mutableListOf<MutableList<ScheduleItem>>()
                    for (item in sorted) {
                        val added = sessions.lastOrNull()?.let { session ->
                            val lastTime = parseTime(session.last().time)
                            val thisTime = parseTime(item.time)
                            if (thisTime - lastTime <= 60) { session.add(item); true } else false
                        } ?: false
                        if (!added) sessions.add(mutableListOf(item))
                    }
                    return sessions
                }

                val sessions = groupIntoSessions(pendingItems)

// Priority 1: session whose window contains RIGHT NOW (any slot ±60 min)
                val currentSession = sessions.firstOrNull { session ->
                    session.any { item ->
                        val diff = parseTime(item.time) - currentMinutes
                        diff in -60..60
                    }
                }

// Priority 2: most recent PAST session (all slots more than 60 min ago)
                val overdueSession = if (currentSession == null) {
                    sessions
                        .filter { session -> session.all { parseTime(it.time) < currentMinutes - 60 } }
                        .maxByOrNull { session -> session.maxOf { parseTime(it.time) } }
                } else null

                val chosenSession = currentSession ?: overdueSession

                if (chosenSession != null) {
                    val allPrescriptionIds = chosenSession
                        .flatMap { it.prescriptionIds }
                        .distinct()
                        .toLongArray()

                    val allMedications = chosenSession
                        .flatMap { it.medications }
                        .distinctBy { it.prescriptionId }

                    // Label: use the time-of-day label of the first slot (e.g. "Evening dose")
                    val slotLabel = chosenSession.first().timeLabel.split(" - ").firstOrNull() ?: "Medication"

                    // For scheduleTime: pass ALL distinct times comma-separated
                    val allTimes = chosenSession.map { it.time }.distinct().joinToString(",")

                    btnTakeNow?.visibility = View.VISIBLE
                    btnTakeNow?.text = "Take Now — $slotLabel"
                    btnTakeNow?.setOnClickListener {
                        val combinedItem = ScheduleItem(
                            timeLabel       = "$slotLabel - $allTimes",
                            time            = allTimes,
                            medications     = allMedications,
                            prescriptionIds = allPrescriptionIds.toList(),
                            isCompleted     = false
                        )
                        onScheduleItemClicked(combinedItem)
                    }
                } else {
                    btnTakeNow?.visibility = View.GONE
                }
            }
        }
    }

    private fun getCurrentDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return dateFormat.format(Date())
    }

    private suspend fun isTaskCompleted(scheduleItem: ScheduleItem, currentDate: String): Boolean {
        // Check if all prescriptions for this time slot are completed
        return scheduleItem.prescriptionIds.all { prescriptionId ->
            db.medicationLogDao().isTaskCompleted(
                prescriptionId,
                scheduleItem.time,
                currentDate
            ) != null
        }
    }

    private fun rescheduleAllAlarms() {
        lifecycleScope.launch {
            db.prescriptionDao().getAllPrescriptions().collect { prescriptions ->
                prescriptions.forEach { prescription ->
                    MedicationAlarmManager.scheduleAlarms(requireContext(), prescription)
                }
            }
        }
    }

    private fun generateDailySchedule(prescriptions: List<PrescriptionEntity>): List<ScheduleItem> {
        val scheduleMap = mutableMapOf<String, MutableList<MedicationDetail>>()
        val timeToLabelMap = mutableMapOf<String, String>()
        val prescriptionIdsMap = mutableMapOf<String, MutableList<Long>>()

        prescriptions.forEach { prescription ->
            val times = listOfNotNull(
                prescription.time1,
                prescription.time2,
                prescription.time3
            )

            times.forEach { time ->
                val label = getTimeLabelForTime(time)
                val fullLabel = "$label - $time"

                timeToLabelMap[time] = fullLabel

                val medicationDetail = formatMedicationDetail(prescription)
                scheduleMap.getOrPut(time) { mutableListOf() }.add(medicationDetail)
                prescriptionIdsMap.getOrPut(time) { mutableListOf() }.add(prescription.id)
            }
        }

        // Sort by time and create schedule items
        return scheduleMap.entries
            .sortedBy { parseTime(it.key) }
            .map { (time, medications) ->
                ScheduleItem(
                    timeLabel = timeToLabelMap[time] ?: time,
                    time = time,
                    medications = medications,
                    prescriptionIds = prescriptionIdsMap[time] ?: emptyList()
                )
            }
    }

    private fun getTimeLabelForTime(time: String): String {
        val hour = parseTimeToHour(time)
        return when {
            hour < 12 -> "Morning medicine"
            hour < 17 -> "Afternoon dose"
            hour < 21 -> "Evening dose"
            hour < 23 -> "Bedtime"
            else -> "Night dose"
        }
    }

    private fun parseTimeToHour(time: String): Int {
        return try {
            // Parse format like "12:00 PM"
            val parts = time.split(" ")
            val timePart = parts[0].split(":")
            var hour = timePart[0].toInt()

            if (parts.size > 1) {
                val amPm = parts[1].uppercase(Locale.getDefault())
                if (amPm == "PM" && hour != 12) hour += 12
                if (amPm == "AM" && hour == 12) hour = 0
            }

            hour
        } catch (e: Exception) {
            12 // Default to noon
        }
    }

    private fun parseTime(time: String): Int {
        // Convert time to minutes for sorting
        val hour = parseTimeToHour(time)
        val minute = try {
            time.split(":")[1].split(" ")[0].toInt()
        } catch (e: Exception) {
            0
        }
        return hour * 60 + minute
    }

    private fun formatMedicationDetail(prescription: PrescriptionEntity): MedicationDetail {
        // Format: "Take 1 pill of Stalevo(125MG)"
        val dosage = prescription.dosageQuantity
        val drugName = prescription.drugName

        return MedicationDetail(
            name = "Take $dosage of $drugName",
            prescriptionId = prescription.id
        )
    }

    private fun onScheduleItemClicked(scheduleItem: ScheduleItem) {
        val intent = Intent(requireContext(), MedicationTrackingActivity::class.java).apply {
            putExtra(MedicationTrackingActivity.EXTRA_SCHEDULE_TITLE, scheduleItem.timeLabel)
            putExtra(MedicationTrackingActivity.EXTRA_SCHEDULE_TIME, scheduleItem.time)
            putExtra(MedicationTrackingActivity.EXTRA_PRESCRIPTION_IDS, scheduleItem.prescriptionIds.toLongArray())
        }
        startActivity(intent)
    }
}
