package com.example.medilens

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class DailyScheduleAdapter(
    private val onItemClick: (ScheduleItem) -> Unit
) : RecyclerView.Adapter<DailyScheduleAdapter.ScheduleViewHolder>() {

    private var scheduleList: List<ScheduleItem> = emptyList()

    fun updateList(newList: List<ScheduleItem>) {
        scheduleList = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScheduleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schedule, parent, false)
        return ScheduleViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScheduleViewHolder, position: Int) {
        val item = scheduleList[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = scheduleList.size

    inner class ScheduleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView.findViewById(R.id.cardScheduleItem)
        private val tvScheduleTitle: TextView  = itemView.findViewById(R.id.tvScheduleTitle)
        private val llMedications: LinearLayout = itemView.findViewById(R.id.llMedications)
        private val ivCheckmark: ImageView     = itemView.findViewById(R.id.ivCheckmark)
        private val ivChevron: ImageView       = itemView.findViewById(R.id.ivChevron)
        private val viewTimeStrip: View        = itemView.findViewById(R.id.viewTimeStrip)

        fun bind(item: ScheduleItem) {
            tvScheduleTitle.text = item.timeLabel

            // Clear previous medications
            llMedications.removeAllViews()

            // Add medication items
            item.medications.forEach { medication ->
                val medicationView = createMedicationView(medication.name, item.isCompleted)
                llMedications.addView(medicationView)
            }

            if (item.isCompleted) {
                // Completed styling — grey out everything
                ivCheckmark.visibility = View.VISIBLE
                cardView.alpha = 0.5f
                tvScheduleTitle.setTextColor(Color.parseColor("#999999"))
                ivChevron.alpha = 0.5f
                viewTimeStrip.setBackgroundColor(Color.parseColor("#CCCCCC"))
                cardView.setCardBackgroundColor(Color.parseColor("#FFFFFF"))
                cardView.isClickable = false
                cardView.isFocusable = false
                cardView.foreground = null
            } else {
                // Active — apply time-based theme
                ivCheckmark.visibility = View.GONE
                cardView.alpha = 1.0f
                ivChevron.alpha = 1.0f
                cardView.isClickable = true
                cardView.isFocusable = true
                cardView.setOnClickListener { onItemClick(item) }

                val theme = getTimeTheme(item.time)
                tvScheduleTitle.setTextColor(Color.parseColor(theme.titleColor))
                viewTimeStrip.setBackgroundColor(Color.parseColor(theme.stripColor))
                cardView.setCardBackgroundColor(Color.parseColor(theme.cardBg))
            }
        }

        private fun createMedicationView(medicationText: String, isCompleted: Boolean): TextView {
            return TextView(itemView.context).apply {
                text = medicationText
                setTextColor(
                    if (isCompleted) Color.parseColor("#BBBBBB")
                    else Color.parseColor("#666666")
                )
                textSize = 14f
                setPadding(0, 4, 0, 4)
            }
        }
    }

    // ── Time-based theme data ────────────────────────────────────────────────

    private data class TimeTheme(
        val titleColor: String,
        val stripColor: String,
        val cardBg: String
    )

    private fun getTimeTheme(time: String): TimeTheme {
        // Handles comma-separated times (combined sessions) — use first time
        val firstTime = time.split(",").first().trim()
        val hour = parseHour(firstTime)
        return when {
            hour in 6..11  -> TimeTheme("#E65100", "#FF8F00", "#FFF8F0")  // Morning  — warm orange
            hour in 12..16 -> TimeTheme("#1565C0", "#2196F3", "#F0F7FF")  // Afternoon — blue
            hour in 17..20 -> TimeTheme("#6A1B9A", "#7B1FA2", "#F8F0FF")  // Evening  — purple
            else           -> TimeTheme("#1A237E", "#3949AB", "#F0F0FF")  // Night    — dark indigo
        }
    }

    private fun parseHour(time: String): Int {
        return try {
            val parts = time.trim().split(" ")
            var hour = parts[0].split(":")[0].toInt()
            if (parts.size > 1) {
                if (parts[1].uppercase() == "PM" && hour != 12) hour += 12
                if (parts[1].uppercase() == "AM" && hour == 12) hour = 0
            }
            hour
        } catch (e: Exception) { 12 }
    }
}