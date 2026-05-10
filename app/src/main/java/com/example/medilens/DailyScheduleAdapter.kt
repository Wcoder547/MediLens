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
        holder.bind(scheduleList[position])
    }

    override fun getItemCount(): Int = scheduleList.size

    inner class ScheduleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView  = itemView.findViewById(R.id.cardScheduleItem)
        private val tvScheduleTitle: TextView   = itemView.findViewById(R.id.tvScheduleTitle)
        private val llMedications: LinearLayout = itemView.findViewById(R.id.llMedications)
        private val ivCheckmark: ImageView      = itemView.findViewById(R.id.ivCheckmark)
        private val ivChevron: ImageView        = itemView.findViewById(R.id.ivChevron)
        private val skyBackground: SkyBackgroundView = itemView.findViewById(R.id.skyBackground)

        fun bind(item: ScheduleItem) {
            tvScheduleTitle.text = item.timeLabel
            llMedications.removeAllViews()

            item.medications.forEach { medication ->
                llMedications.addView(createMedicationView(medication.name, item.isCompleted))
            }

            val firstTime = item.time.split(",").first().trim()
            val hour = parseHour(firstTime)

            if (item.isCompleted) {
                skyBackground.setHour(hour, completed = true)
                ivCheckmark.visibility = View.VISIBLE
                cardView.alpha = 0.6f
                cardView.isClickable = false
                cardView.isFocusable = false
                cardView.foreground = null
                // Completed — dark grey text readable on light grey bg
                tvScheduleTitle.setTextColor(Color.parseColor("#888888"))
                ivChevron.alpha = 0.4f
            } else {
                skyBackground.setHour(hour, completed = false)
                ivCheckmark.visibility = View.GONE
                cardView.alpha = 1.0f
                cardView.isClickable = true
                cardView.isFocusable = true
                cardView.setOnClickListener { onItemClick(item) }

                // Text color per background for contrast
                val colors = getTextColors(hour)
                tvScheduleTitle.setTextColor(Color.parseColor(colors.title))
                ivChevron.colorFilter = android.graphics.PorterDuffColorFilter(
                    Color.parseColor(colors.chevron),
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
            }
        }

        private fun createMedicationView(text: String, isCompleted: Boolean): TextView {
            return TextView(itemView.context).apply {
                this.text = text
                val colors = if (isCompleted) "#AAAAAA"
                else getMedTextColor(parseHour(
                    scheduleList.getOrNull(adapterPosition)
                        ?.time?.split(",")?.first()?.trim() ?: "12:00 PM"
                ))
                setTextColor(Color.parseColor(colors))
                textSize = 14f
                setPadding(0, 4, 0, 4)
            }
        }
    }

    // ── Text colors chosen for contrast against each sky background ──────────

    private data class TextColors(val title: String, val chevron: String)

    private fun getTextColors(hour: Int): TextColors = when {
        hour in 6..11  -> TextColors("#5D3200", "#8B5500")   // Morning  — dark brown on warm yellow
        hour in 12..16 -> TextColors("#0D3C6E", "#1565C0")   // Afternoon — dark navy on light blue
        hour in 17..20 -> TextColors("#FFECB3", "#FFD54F")   // Evening  — pale yellow on dark purple/orange
        else           -> TextColors("#E8EAF6", "#9FA8DA")   // Night    — pale lavender on deep navy
    }

    private fun getMedTextColor(hour: Int): String = when {
        hour in 6..11  -> "#7B4400"   // Morning
        hour in 12..16 -> "#1A4F7A"   // Afternoon
        hour in 17..20 -> "#FFD180"   // Evening
        else           -> "#B0BEC5"   // Night
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