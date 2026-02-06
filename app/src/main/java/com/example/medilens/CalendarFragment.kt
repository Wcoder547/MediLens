package com.example.medilens

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class CalendarFragment : Fragment(R.layout.fragment_calendar) {

    private lateinit var calendarView: CalendarView
    private lateinit var rvMedicineLogs: RecyclerView
    private lateinit var tvSelectedDate: TextView
    private lateinit var fabAddMedicine: FloatingActionButton
    private val medicineLogs = ArrayList<MedicineLog>()
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

    data class MedicineLog(
        val name: String,
        val time: String,
        val dosage: String,
        val status: String // "Taken", "Missed", "Pending"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_calendar, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        calendarView = view.findViewById(R.id.calendarView)
        rvMedicineLogs = view.findViewById(R.id.rvMedicineLogs)
        tvSelectedDate = view.findViewById(R.id.tvSelectedDate)
        fabAddMedicine = view.findViewById(R.id.fabAddMedicine)

        rvMedicineLogs.layoutManager = LinearLayoutManager(requireContext())
        rvMedicineLogs.adapter = MedicineLogAdapter(medicineLogs)

        // Sample data - replace with your DB later
        loadSampleData()

        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val selectedDate = Calendar.getInstance().apply {
                set(year, month, dayOfMonth)
            }
            tvSelectedDate.text = dateFormat.format(selectedDate.time)
            loadLogsForDate(selectedDate) // Filter logs for selected date
        }

        fabAddMedicine.setOnClickListener {

        }
    }

    private fun loadSampleData() {
        // Simulate logs from prescriptions
        medicineLogs.add(MedicineLog("Aspirin", "8:00 AM", "100mg", "Taken"))
        medicineLogs.add(MedicineLog("Insulin", "12:00 PM", "10 units", "Pending"))
        medicineLogs.add(MedicineLog("Metformin", "6:00 PM", "500mg", "Missed"))
        (rvMedicineLogs.adapter as MedicineLogAdapter).notifyDataSetChanged()
    }

    private fun loadLogsForDate(date: Calendar) {
        // Filter medicineLogs for the selected date (implement with your DB)
        // For demo, reload all
        (rvMedicineLogs.adapter as MedicineLogAdapter).notifyDataSetChanged()
    }

}

class MedicineLogAdapter(private val logs: List<CalendarFragment.MedicineLog>) : RecyclerView.Adapter<MedicineLogAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvMedicineName)
        val tvTimeDosage: TextView = view.findViewById(R.id.tvTimeDosage)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val btnMarkTaken: Button = view.findViewById(R.id.btnMarkTaken)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_medicine_log, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val log = logs[position]
        holder.tvName.text = log.name
        holder.tvTimeDosage.text = "${log.time} • ${log.dosage}"
        holder.tvStatus.text = log.status
        holder.tvStatus.setBackgroundResource(
            when (log.status) {
                "Taken" -> R.color.green // Define colors
                "Missed" -> R.color.red
                else -> R.color.orange
            }
        )

        holder.btnMarkTaken.setOnClickListener {
            // Update status to "Taken" - save to DB later
            Toast.makeText(holder.itemView.context, "${log.name} marked as taken", Toast.LENGTH_SHORT).show()
        }
    }

    override fun getItemCount() = logs.size
}
