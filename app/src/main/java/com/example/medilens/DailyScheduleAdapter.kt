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
        private val tvScheduleTitle: TextView = itemView.findViewById(R.id.tvScheduleTitle)
        private val llMedications: LinearLayout = itemView.findViewById(R.id.llMedications)
        private val ivCheckmark: ImageView = itemView.findViewById(R.id.ivCheckmark)
        private val ivChevron: ImageView = itemView.findViewById(R.id.ivChevron)

        fun bind(item: ScheduleItem) {
            tvScheduleTitle.text = item.timeLabel

            // Clear previous medications
            llMedications.removeAllViews()

            // Add medication items
            item.medications.forEach { medication ->
                val medicationView = createMedicationView(medication.name, item.isCompleted)
                llMedications.addView(medicationView)
            }

            // Apply completed styling
            if (item.isCompleted) {
                // Show checkmark
                ivCheckmark.visibility = View.VISIBLE

                // Apply grayed-out style
                cardView.alpha = 0.5f
                tvScheduleTitle.setTextColor(Color.parseColor("#999999"))
                ivChevron.alpha = 0.5f

                // Disable click
                cardView.isClickable = false
                cardView.isFocusable = false
                cardView.foreground = null
            } else {
                // Hide checkmark
                ivCheckmark.visibility = View.GONE

                // Normal style
                cardView.alpha = 1.0f
                tvScheduleTitle.setTextColor(Color.parseColor("#2196F3"))
                ivChevron.alpha = 1.0f

                // Enable click
                cardView.isClickable = true
                cardView.isFocusable = true
                cardView.setOnClickListener {
                    onItemClick(item)
                }
            }
        }

        private fun createMedicationView(medicationText: String, isCompleted: Boolean): TextView {
            return TextView(itemView.context).apply {
                text = medicationText
                setTextColor(if (isCompleted) {
                    Color.parseColor("#BBBBBB")
                } else {
                    Color.parseColor("#666666")
                })
                textSize = 14f
                setPadding(0, 4, 0, 4)
            }
        }
    }
}
