package com.example.medilens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.*

class CalendarFragment : Fragment(R.layout.fragment_calendar) {

    private lateinit var tvMonthYear:      TextView
    private lateinit var btnPrev:          ImageButton
    private lateinit var btnNext:          ImageButton
    private lateinit var llDayNames:       LinearLayout
    private lateinit var calendarGridView: CalendarGridView
    private lateinit var tvSelectedDate:   TextView
    private lateinit var tvMedCount:       TextView
    private lateinit var tvMonthlySummary: TextView
    private lateinit var llMedicineCards:  LinearLayout

    private val allLogs    = mutableMapOf<String, MutableList<MedicineLog>>()
    private val displayFmt = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    private val keyFmt     = SimpleDateFormat("yyyy-MM-dd",   Locale.getDefault())
    private val monthFmt   = SimpleDateFormat("MMMM yyyy",    Locale.getDefault())

    // Currently displayed month
    private val displayCal = Calendar.getInstance()

    // Currently selected date
    private val selectedCal = Calendar.getInstance()

    data class MedicineLog(
        val name:   String,
        val time:   String,
        val dosage: String,
        var status: String
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_calendar, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvMonthYear      = view.findViewById(R.id.tvMonthYear)
        btnPrev          = view.findViewById(R.id.btnPrevMonth)
        btnNext          = view.findViewById(R.id.btnNextMonth)
        llDayNames       = view.findViewById(R.id.llDayNames)
        calendarGridView = view.findViewById(R.id.calendarGridView)
        tvSelectedDate   = view.findViewById(R.id.tvSelectedDate)
        tvMedCount       = view.findViewById(R.id.tvMedCount)
        tvMonthlySummary = view.findViewById(R.id.tvMonthlySummary)
        llMedicineCards  = view.findViewById(R.id.llMedicineCards)

        loadSampleData()
        buildDayNameHeader()

        // Launch on today
        showMonth()
        showDay(selectedCal)

        btnPrev.setOnClickListener {
            displayCal.add(Calendar.MONTH, -1)
            showMonth()
        }
        btnNext.setOnClickListener {
            displayCal.add(Calendar.MONTH, 1)
            showMonth()
        }

        calendarGridView.onDayClick = { day ->
            selectedCal.set(
                displayCal.get(Calendar.YEAR),
                displayCal.get(Calendar.MONTH),
                day
            )
            showDay(selectedCal)
        }
    }

    // ── Month header + grid ────────────────────────────────────────────────

    private fun showMonth() {
        tvMonthYear.text = monthFmt.format(displayCal.time)

        val year  = displayCal.get(Calendar.YEAR)
        val month = displayCal.get(Calendar.MONTH)

        // Build dot map for this month
        val tmp = Calendar.getInstance()
        val dots = mutableMapOf<Int, CalendarGridView.DotColor>()

        val daysInMonth = displayCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        for (d in 1..daysInMonth) {
            tmp.set(year, month, d)
            val logs = allLogs[keyFmt.format(tmp.time)] ?: continue
            if (logs.isEmpty()) continue
            val taken  = logs.count { it.status == "Taken"  }
            val missed = logs.count { it.status == "Missed" }
            dots[d] = when {
                taken  == logs.size -> CalendarGridView.DotColor.GREEN
                missed == logs.size -> CalendarGridView.DotColor.RED
                else                -> CalendarGridView.DotColor.ORANGE
            }
        }

        // Selected day shown only if same month
        val selDay = if (
            selectedCal.get(Calendar.YEAR)  == year &&
            selectedCal.get(Calendar.MONTH) == month
        ) selectedCal.get(Calendar.DAY_OF_MONTH) else -1

        calendarGridView.setMonthData(year, month, dots, selDay)
    }

    // ── Day detail ─────────────────────────────────────────────────────────

    private fun showDay(cal: Calendar) {
        tvSelectedDate.text = displayFmt.format(cal.time)

        val key  = keyFmt.format(cal.time)
        val logs = allLogs[key] ?: emptyList()

        tvMedCount.text = "${logs.size} ${if (logs.size == 1) "med" else "meds"}"

        llMedicineCards.removeAllViews()
        if (logs.isEmpty()) {
            llMedicineCards.addView(TextView(requireContext()).apply {
                text     = "No medicines scheduled for this day"
                textSize = 14f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.subtext))
                setPadding(32, 32, 32, 32)
            })
        } else {
            logs.forEachIndexed { index, log ->
                llMedicineCards.addView(buildCard(log, key, index))
            }
        }

        updateMonthlySummary()
    }

    // ── Medicine card ──────────────────────────────────────────────────────

    private fun buildCard(log: MedicineLog, dateKey: String, logIndex: Int): View {
        val card = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_medicine_log, llMedicineCards, false)

        card.findViewById<TextView>(R.id.tvMedicineName).text = log.name
        card.findViewById<TextView>(R.id.tvTimeDosage).text   = "${log.time} • ${log.dosage}"

        val tvStatus = card.findViewById<TextView>(R.id.tvStatus)
        tvStatus.text = log.status

        val (bgCol, txtCol) = when (log.status) {
            "Taken"  -> R.color.status_taken_bg   to R.color.status_taken_text
            "Missed" -> R.color.status_missed_bg  to R.color.status_missed_text
            else     -> R.color.status_pending_bg to R.color.status_pending_text
        }
        tvStatus.background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_status_pill)
            ?.mutate()?.also { it.setTint(ContextCompat.getColor(requireContext(), bgCol)) }
        tvStatus.setTextColor(ContextCompat.getColor(requireContext(), txtCol))

        val btnTaken = card.findViewById<Button>(R.id.btnMarkTaken)
        btnTaken.visibility = if (log.status == "Pending") View.VISIBLE else View.GONE
        btnTaken.setOnClickListener {
            allLogs[dateKey]?.getOrNull(logIndex)?.status = "Taken"
            showDay(selectedCal)
            showMonth()     // dots update hojayein
        }

        return card
    }

    // ── Day name row: M  T  W  T  F  S  S ────────────────────────────────

    private fun buildDayNameHeader() {
        val days = listOf("M", "T", "W", "T", "F", "S", "S")
        days.forEach { d ->
            llDayNames.addView(TextView(requireContext()).apply {
                text      = d
                textSize  = 12f
                gravity   = android.view.Gravity.CENTER
                setTextColor(ContextCompat.getColor(requireContext(), R.color.subtext))
                layoutParams = LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            })
        }
    }

    // ── Monthly summary ────────────────────────────────────────────────────

    private fun updateMonthlySummary() {
        var total = 0; var taken = 0
        allLogs.values.forEach { list ->
            list.forEach { log -> total++; if (log.status == "Taken") taken++ }
        }
        tvMonthlySummary.text = "This month: $taken / $total doses taken"
    }

    // ── Sample data ────────────────────────────────────────────────────────

    private fun loadSampleData() {
        val cal = Calendar.getInstance()

        allLogs[keyFmt.format(cal.time)] = mutableListOf(
            MedicineLog("Panadol",  "8:00 AM",  "10mg",   "Taken"),
            MedicineLog("Myteka",   "12:00 PM", "10mg",    "Pending"),
            MedicineLog("Ventolin", "6:00 PM",  "2 mg", "Missed")
        )
        cal.add(Calendar.DAY_OF_MONTH, -1)
        allLogs[keyFmt.format(cal.time)] = mutableListOf(
            MedicineLog("Panadol", "8:00 AM", "10mg", "Taken"),
            MedicineLog("Risek",   "8:00 AM", "20mg",  "Taken")
        )
        cal.add(Calendar.DAY_OF_MONTH, -1)
        allLogs[keyFmt.format(cal.time)] = mutableListOf(
            MedicineLog("Panadol",  "8:00 AM", "10mg",   "Missed"),
            MedicineLog("Ventolin", "6:00 PM", "2 mg", "Missed")
        )
        cal.add(Calendar.DAY_OF_MONTH, -1)
        allLogs[keyFmt.format(cal.time)] = mutableListOf(
            MedicineLog("Panadol", "8:00 AM", "10mg", "Taken"),
            MedicineLog("Risek",   "8:00 AM", "20mg",  "Missed")
        )
    }
}