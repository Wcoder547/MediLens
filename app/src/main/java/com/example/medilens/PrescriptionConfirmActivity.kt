package com.example.medilens

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class PrescriptionConfirmActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MEDICINES = "extra_medicines"
    }

    private lateinit var rvMedicines:    RecyclerView
    private lateinit var btnConfirmAll:  MaterialButton
    private lateinit var btnAddManually: MaterialButton
    private lateinit var tvMedicineCount: TextView

    private val medicines = mutableListOf<ParsedMedicine>()
    private lateinit var adapter: MedicineCardAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_prescription_confirm)

        bindViews()

        // Receive medicines from ScanActivity
        val parcelables = intent.getParcelableArrayListExtra<ParsedMedicineParcelable>(EXTRA_MEDICINES)
        if (parcelables.isNullOrEmpty()) {
            Toast.makeText(this, "No medicines found. Please try again.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        medicines.addAll(parcelables.map { it.toParsedMedicine() })

        setupRecyclerView()
        updateMedicineCount()
        setupButtons()

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
    }

    private fun bindViews() {
        rvMedicines     = findViewById(R.id.rvMedicines)
        btnConfirmAll   = findViewById(R.id.btnConfirmAll)
        btnAddManually  = findViewById(R.id.btnAddManually)
        tvMedicineCount = findViewById(R.id.tvMedicineCount)
    }

    private fun setupRecyclerView() {
        adapter = MedicineCardAdapter(
            medicines  = medicines,
            onEdit     = { position -> showEditDialog(position) },
            onDelete   = { position ->
                medicines.removeAt(position)
                adapter.notifyItemRemoved(position)
                adapter.notifyItemRangeChanged(position, medicines.size)
                updateMedicineCount()
            }
        )
        rvMedicines.layoutManager = LinearLayoutManager(this)
        rvMedicines.adapter       = adapter
    }

    private fun updateMedicineCount() {
        val count = medicines.size
        tvMedicineCount.text = if (count == 1) "1 medicine found" else "$count medicines found"
        btnConfirmAll.isEnabled = count > 0
    }

    private fun setupButtons() {
        btnConfirmAll.setOnClickListener  { saveAllMedicines() }
        btnAddManually.setOnClickListener { showEditDialog(-1) }  // -1 = new card
    }

    // ── Save all to Room DB ────────────────────────────────────────────────
    private fun saveAllMedicines() {
        if (medicines.isEmpty()) {
            Toast.makeText(this, "No medicines to save.", Toast.LENGTH_SHORT).show()
            return
        }

        // Warn if any RED cards remain
        val redCount = medicines.count { it.validationFlag == ValidationFlag.RED }
        if (redCount > 0) {
            AlertDialog.Builder(this)
                .setTitle("⚠️ $redCount card${if (redCount > 1) "s have" else " has"} issues")
                .setMessage("Some medicines have missing or invalid data (shown in red). Save anyway?\n\nYou can edit them later in Prescriptions.")
                .setPositiveButton("Save Anyway") { _, _ -> performSave() }
                .setNegativeButton("Review First", null)
                .show()
        } else {
            performSave()
        }
    }

    private fun performSave() {
        lifecycleScope.launch {
            val db  = AppDatabase.getDatabase(this@PrescriptionConfirmActivity)
            val dao = db.prescriptionDao()
            var savedCount = 0

            for (medicine in medicines) {
                try {
                    val entity = medicine.toPrescriptionEntity(
                        prescriptionName = "Scanned - ${medicine.medicineName}"
                    )
                    val id = dao.insert(entity)
                    MedicationAlarmManager.scheduleAlarms(
                        this@PrescriptionConfirmActivity,
                        entity.copy(id = id)
                    )
                    savedCount++
                } catch (e: Exception) {
                    Toast.makeText(
                        this@PrescriptionConfirmActivity,
                        "Error saving ${medicine.medicineName}: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            if (savedCount > 0) {
                Toast.makeText(
                    this@PrescriptionConfirmActivity,
                    "✅ $savedCount medicine${if (savedCount > 1) "s" else ""} saved with reminders!",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }
        }
    }

    // ── Edit dialog ────────────────────────────────────────────────────────
    private fun showEditDialog(position: Int) {
        val isNew     = position == -1
        val existing  = if (isNew) null else medicines.getOrNull(position)

        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_edit_medicine, null)

        val etName     = dialogView.findViewById<TextInputEditText>(R.id.etMedicineName)
        val etDose     = dialogView.findViewById<TextInputEditText>(R.id.etDose)
        val etForm     = dialogView.findViewById<TextInputEditText>(R.id.etForm)
        val etDuration = dialogView.findViewById<TextInputEditText>(R.id.etDuration)
        val etInstr    = dialogView.findViewById<TextInputEditText>(R.id.etInstructions)
        val etQty      = dialogView.findViewById<TextInputEditText>(R.id.etQuantity)

        val rgFrequency = dialogView.findViewById<RadioGroup>(R.id.rgFrequency)
        val btnTime1    = dialogView.findViewById<MaterialButton>(R.id.btnTime1)
        val btnTime2    = dialogView.findViewById<MaterialButton>(R.id.btnTime2)
        val btnTime3    = dialogView.findViewById<MaterialButton>(R.id.btnTime3)

        // Pre-fill from existing medicine
        existing?.let {
            etName.setText(it.medicineName)
            etDose.setText(it.dose)
            etForm.setText(it.form)
            etDuration.setText(it.duration)
            etInstr.setText(it.instructions)
            etQty.setText(it.quantity.toString())

            when (it.timesPerDay) {
                2    -> rgFrequency.check(R.id.rbTwiceDaily)
                3    -> rgFrequency.check(R.id.rbThriceDaily)
                else -> rgFrequency.check(R.id.rbOnceDaily)
            }
        } ?: rgFrequency.check(R.id.rbOnceDaily)

        var t1 = existing?.scheduleTimes?.getOrNull(0)
        var t2 = existing?.scheduleTimes?.getOrNull(1)
        var t3 = existing?.scheduleTimes?.getOrNull(2)

        fun refreshButtons() {
            btnTime1.text = t1 ?: "Set time"
            btnTime2.text = t2 ?: "Set time"
            btnTime3.text = t3 ?: "Set time"
            when (rgFrequency.checkedRadioButtonId) {
                R.id.rbOnceDaily   -> { btnTime2.visibility = View.GONE; btnTime3.visibility = View.GONE }
                R.id.rbTwiceDaily  -> { btnTime2.visibility = View.VISIBLE; btnTime3.visibility = View.GONE }
                R.id.rbThriceDaily -> { btnTime2.visibility = View.VISIBLE; btnTime3.visibility = View.VISIBLE }
            }
        }

        rgFrequency.setOnCheckedChangeListener { _, _ -> refreshButtons() }
        refreshButtons()

        fun timePicker(onPicked: (String) -> Unit) {
            val cal = java.util.Calendar.getInstance()
            TimePickerDialog(this, { _, h, m ->
                val amPm = if (h >= 12) "PM" else "AM"
                val h12  = if (h % 12 == 0) 12 else h % 12
                onPicked(String.format("%02d:%02d %s", h12, m, amPm))
            }, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), false).show()
        }

        btnTime1.setOnClickListener { timePicker { t1 = it; refreshButtons() } }
        btnTime2.setOnClickListener { timePicker { t2 = it; refreshButtons() } }
        btnTime3.setOnClickListener { timePicker { t3 = it; refreshButtons() } }

        AlertDialog.Builder(this)
            .setTitle(if (isNew) "Add Medicine" else "Edit Medicine")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(this, "Medicine name is required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val timesPerDay = when (rgFrequency.checkedRadioButtonId) {
                    R.id.rbTwiceDaily  -> 2
                    R.id.rbThriceDaily -> 3
                    else               -> 1
                }
                val times = listOfNotNull(t1, t2, t3).take(timesPerDay)

                val updated = ParsedMedicine(
                    medicineName       = name,
                    dose               = etDose.text.toString().trim(),
                    form               = etForm.text.toString().trim().ifEmpty { "tablet" },
                    timesPerDay        = timesPerDay,
                    scheduleTimes      = times.ifEmpty {
                        when (timesPerDay) {
                            2    -> listOf("08:00 AM", "08:00 PM")
                            3    -> listOf("08:00 AM", "02:00 PM", "08:00 PM")
                            else -> listOf("08:00 AM")
                        }
                    },
                    duration           = etDuration.text.toString().trim(),
                    instructions       = etInstr.text.toString().trim(),
                    quantity           = etQty.text.toString().toIntOrNull() ?: 1,
                    confidence         = if (isNew) 1.0f else (existing?.confidence ?: 0.9f),
                    verificationStatus = existing?.verificationStatus ?: VerificationStatus.ENROLLMENT_PENDING,
                    validationFlag     = ValidationFlag.GREEN   // user manually edited → trust it
                )

                if (isNew) {
                    medicines.add(updated)
                    adapter.notifyItemInserted(medicines.size - 1)
                } else {
                    medicines[position] = updated
                    adapter.notifyItemChanged(position)
                }
                updateMedicineCount()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

// ── RecyclerView Adapter ───────────────────────────────────────────────────

class MedicineCardAdapter(
    private val medicines: MutableList<ParsedMedicine>,
    private val onEdit:    (Int) -> Unit,
    private val onDelete:  (Int) -> Unit
) : RecyclerView.Adapter<MedicineCardAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card:           MaterialCardView = view.findViewById(R.id.cardMedicine)
        val tvName:         TextView         = view.findViewById(R.id.tvMedicineName)
        val tvDose:         TextView         = view.findViewById(R.id.tvDose)
        val tvSchedule:     TextView         = view.findViewById(R.id.tvSchedule)
        val tvDuration:     TextView         = view.findViewById(R.id.tvDuration)
        val tvInstructions: TextView         = view.findViewById(R.id.tvInstructions)
        val tvVerifyBadge:  TextView         = view.findViewById(R.id.tvVerifyBadge)
        val tvConfidence:   TextView         = view.findViewById(R.id.tvConfidence)
        val btnEdit:        ImageButton      = view.findViewById(R.id.btnEdit)
        val btnDelete:      ImageButton      = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_medicine_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val m   = medicines[position]
        val ctx = holder.itemView.context

        // ── Card border colour ─────────────────────────────────────────────
        val (strokeColor, bgColor) = when (m.validationFlag) {
            ValidationFlag.GREEN -> Pair(
                ContextCompat.getColor(ctx, R.color.validation_green),
                ContextCompat.getColor(ctx, R.color.validation_green_bg)
            )
            ValidationFlag.AMBER -> Pair(
                ContextCompat.getColor(ctx, R.color.validation_amber),
                ContextCompat.getColor(ctx, R.color.validation_amber_bg)
            )
            ValidationFlag.RED   -> Pair(
                ContextCompat.getColor(ctx, R.color.validation_red),
                ContextCompat.getColor(ctx, R.color.validation_red_bg)
            )
        }
        holder.card.strokeColor         = strokeColor
        holder.card.strokeWidth         = 3
        holder.card.setCardBackgroundColor(bgColor)

        // ── Content ────────────────────────────────────────────────────────
        holder.tvName.text = m.medicineName.ifBlank { "⚠ Name missing" }

        holder.tvDose.text = buildString {
            if (m.dose.isNotBlank())  append(m.dose)
            if (m.form.isNotBlank())  append("  •  ${m.form}")
            if (m.quantity > 1)       append("  ×${m.quantity}")
        }.ifBlank { "Dose not specified" }

        holder.tvSchedule.text = buildString {
            append("${m.timesPerDay}× daily")
            if (m.scheduleTimes.isNotEmpty()) {
                append("  —  ${m.scheduleTimes.joinToString(", ")}")
            }
        }

        holder.tvDuration.text =
            if (m.duration.isNotBlank()) "⏱ ${m.duration}" else "⏱ Duration not specified"

        holder.tvInstructions.text =
            if (m.instructions.isNotBlank()) "ℹ️ ${m.instructions}" else ""
        holder.tvInstructions.visibility =
            if (m.instructions.isNotBlank()) View.VISIBLE else View.GONE

        // ── Verification badge ─────────────────────────────────────────────
        when (m.verificationStatus) {
            VerificationStatus.YOLO_VERIFIED -> {
                holder.tvVerifyBadge.text       = "📷 Verifiable"
                holder.tvVerifyBadge.visibility = View.VISIBLE
                holder.tvVerifyBadge.setBackgroundColor(
                    ContextCompat.getColor(ctx, R.color.badge_verifiable_bg)
                )
                holder.tvVerifyBadge.setTextColor(
                    ContextCompat.getColor(ctx, R.color.badge_verifiable_text)
                )
            }
            VerificationStatus.ENROLLMENT_PENDING -> {
                holder.tvVerifyBadge.text       = "🔔 Reminders only"
                holder.tvVerifyBadge.visibility = View.VISIBLE
                holder.tvVerifyBadge.setBackgroundColor(
                    ContextCompat.getColor(ctx, R.color.badge_pending_bg)
                )
                holder.tvVerifyBadge.setTextColor(
                    ContextCompat.getColor(ctx, R.color.badge_pending_text)
                )
            }
            VerificationStatus.EMBEDDING_ENROLLED -> {
                holder.tvVerifyBadge.text       = "📷 Enrolled"
                holder.tvVerifyBadge.visibility = View.VISIBLE
                holder.tvVerifyBadge.setBackgroundColor(
                    ContextCompat.getColor(ctx, R.color.badge_verifiable_bg)
                )
                holder.tvVerifyBadge.setTextColor(
                    ContextCompat.getColor(ctx, R.color.badge_verifiable_text)
                )
            }
        }

        // ── Confidence ─────────────────────────────────────────────────────
        val confidencePct = (m.confidence * 100).toInt()
        holder.tvConfidence.text = "$confidencePct% confidence"

        // ── Actions ────────────────────────────────────────────────────────
        holder.btnEdit.setOnClickListener   { onEdit(holder.adapterPosition) }
        holder.btnDelete.setOnClickListener { onDelete(holder.adapterPosition) }
    }

    override fun getItemCount() = medicines.size
}
