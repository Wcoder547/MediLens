package com.example.medilens

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip

class PrescriptionsAdapter(
    private var prescriptions: List<PrescriptionEntity>,
    private val onEdit: (Int) -> Unit,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<PrescriptionsAdapter.ViewHolder>() {

    fun updateList(newList: List<PrescriptionEntity>) {
        prescriptions = newList
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val chipDrugName: Chip           = view.findViewById(R.id.chipDrugName)
        val chipDosage: Chip             = view.findViewById(R.id.chipDosage)
        val tvFrequency: Chip            = view.findViewById(R.id.tvFrequency)
        val tvTimes: LinearLayout        = view.findViewById(R.id.tvTimes)
        val btnMore: ImageButton         = view.findViewById(R.id.btnMore)
        val tvPrescriptionName: TextView = view.findViewById(R.id.tvPrescriptionName)
        val btnDelete: MaterialButton    = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_prescription, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val p = prescriptions[position]

        holder.tvPrescriptionName.text = p.prescriptionName
        holder.chipDrugName.text       = p.drugName
        holder.chipDosage.text         = p.dosageQuantity
        holder.tvFrequency.text        = p.frequency

        holder.tvTimes.removeAllViews()
        listOfNotNull(p.time1, p.time2, p.time3).forEach { time ->
            holder.tvTimes.addView(
                Chip(holder.tvTimes.context).apply {
                    text = time
                    setChipBackgroundColorResource(android.R.color.holo_green_light)
                }
            )
        }

        holder.btnMore.setOnClickListener { onEdit(position) }
        holder.btnDelete.setOnClickListener { onDelete(position) }
    }

    override fun getItemCount() = prescriptions.size
}