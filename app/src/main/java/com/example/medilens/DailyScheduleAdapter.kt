package com.example.medilens

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DailyScheduleAdapter(
    private val onItemClick: (ScheduleItem) -> Unit
) : RecyclerView.Adapter<DailyScheduleAdapter.ScheduleViewHolder>() {

    private var scheduleItems = listOf<ScheduleItem>()

    fun updateList(newList: List<ScheduleItem>) {
        scheduleItems = newList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScheduleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_schedule, parent, false)
        return ScheduleViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScheduleViewHolder, position: Int) {
        holder.bind(scheduleItems[position])
    }

    override fun getItemCount() = scheduleItems.size

    inner class ScheduleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvScheduleTitle: TextView = itemView.findViewById(R.id.tvScheduleTitle)
        private val llMedications: LinearLayout = itemView.findViewById(R.id.llMedications)
        private val ivChevron: ImageView = itemView.findViewById(R.id.ivChevron)

        fun bind(item: ScheduleItem) {
            tvScheduleTitle.text = item.timeLabel

            // Clear previous medications
            llMedications.removeAllViews()

            // Add each medication as a text line with proper styling
            item.medications.forEachIndexed { index, medication ->
                val medicationView = TextView(itemView.context).apply {
                    text = medication.name
                    setTextColor(Color.parseColor("#333333")) // Dark gray/black color
                    textSize = 14f

                    // Add spacing between medication items
                    val topPadding = if (index == 0) 0 else 8
                    setPadding(0, topPadding, 0, 0)

                    // Line spacing for better readability
                    setLineSpacing(4f, 1.2f)
                }
                llMedications.addView(medicationView)
            }

            itemView.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}
