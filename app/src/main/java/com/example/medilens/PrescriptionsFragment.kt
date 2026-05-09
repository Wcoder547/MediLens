package com.example.medilens

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.chip.Chip
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class PrescriptionsFragment : Fragment(R.layout.fragment_prescriptions) {

    // DB
    private lateinit var db: AppDatabase
    private val prescriptionDao get() = db.prescriptionDao()

    // StateFlow
    private val _prescriptions = MutableStateFlow<List<PrescriptionEntity>>(emptyList())
    private val prescriptions: StateFlow<List<PrescriptionEntity>> = _prescriptions.asStateFlow()

    // Views
    private lateinit var rvPrescriptions:     RecyclerView
    private lateinit var fabAddPrescription:  FloatingActionButton
    private lateinit var fabScanPrescription: FloatingActionButton   // ← NEW
    private lateinit var emptyState:          LinearLayout

    companion object {
        private const val TAG = "PrescriptionsFragment"
    }

    // ---------- EXTENSION ----------
    private fun TextInputEditText?.validateNotEmpty(errorMsg: String): Boolean {
        return this?.let {
            val text = it.text.toString().trim()
            if (text.isEmpty()) {
                it.error = errorMsg
                false
            } else {
                it.error = null
                true
            }
        } ?: false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = AppDatabase.getDatabase(requireContext())

        rvPrescriptions     = view.findViewById(R.id.rvPrescriptions)
        fabAddPrescription  = view.findViewById(R.id.fabAddPrescription)
        fabScanPrescription = view.findViewById(R.id.fabScanPrescription)   // ← NEW
        emptyState          = view.findViewById(R.id.emptyState)

        setupRecyclerView()
        setupFab()
        observePrescriptions()
    }

    private fun setupRecyclerView() {
        rvPrescriptions.layoutManager = LinearLayoutManager(requireContext())
        rvPrescriptions.adapter = PrescriptionsAdapter(
            prescriptions = emptyList(),
            onEdit        = { position -> editPrescription(position) },
            onDelete      = { position -> confirmDeletePrescription(position) },
            // ── ADD THIS ──────────────────────────────────────────────────
            onScoreClick  = { position ->
                val prescription = _prescriptions.value.getOrNull(position) ?: return@PrescriptionsAdapter
                val intent = Intent(requireContext(), AdherenceScoreActivity::class.java)
                intent.putExtra("prescription_id", prescription.id)
                startActivity(intent)
            }
            // ──────────────────────────────────────────────────────────────
        )
    }

    private fun confirmDeletePrescription(position: Int) {
        val prescription = _prescriptions.value.getOrNull(position) ?: return

        AlertDialog.Builder(requireContext())
            .setTitle("Delete Prescription")
            .setMessage("Delete \"${prescription.prescriptionName}\"?\n\nThis will also remove all medication reminders for ${prescription.drugName}.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    try {
                        MedicationAlarmManager.cancelAlarms(requireContext(), prescription)
                        prescriptionDao.delete(prescription)
                        Toast.makeText(
                            requireContext(),
                            "✅ ${prescription.prescriptionName} deleted",
                            Toast.LENGTH_SHORT
                        ).show()
                    } catch (e: Exception) {
                        Toast.makeText(
                            requireContext(),
                            "Error deleting: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── FAB setup ──────────────────────────────────────────────────────────
    private fun setupFab() {
        // Existing manual-add FAB — unchanged
        fabAddPrescription.setOnClickListener { showAddPrescriptionDialog() }

        // NEW: Scan prescription FAB
        fabScanPrescription.setOnClickListener {
            startActivity(Intent(requireContext(), PrescriptionScanActivity::class.java))
        }
    }

    private fun observePrescriptions() {
        lifecycleScope.launch {
            prescriptionDao.getAllPrescriptions().collectLatest { list ->
                _prescriptions.value = list
                updateUI()
            }
        }
    }

    private fun updateUI() {
        val list = _prescriptions.value
        if (list.isEmpty()) {
            emptyState.visibility      = View.VISIBLE
            rvPrescriptions.visibility = View.GONE
        } else {
            emptyState.visibility      = View.GONE
            rvPrescriptions.visibility = View.VISIBLE
            (rvPrescriptions.adapter as? PrescriptionsAdapter)?.updateList(list)
        }
    }

    // ===================== DIALOG =====================
    private fun showAddPrescriptionDialog(
        preFill: PrescriptionEntity? = null,
        position: Int? = null
    ) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_prescription_stepper, null, false)

        val step1 = dialogView.findViewById<View>(R.id.step1Layout)
        val step2 = dialogView.findViewById<View>(R.id.step2Layout)
        val step3 = dialogView.findViewById<View>(R.id.step3Layout)

        val btnPrevious = dialogView.findViewById<MaterialButton>(R.id.btnPrevious)
        val btnNext     = dialogView.findViewById<MaterialButton>(R.id.btnNext)

        val etPrescriptionName = dialogView.findViewById<TextInputEditText>(R.id.etPrescriptionName)
        val etDrugName         = dialogView.findViewById<TextInputEditText>(R.id.etDrugName)
        val etDosageQuantity   = dialogView.findViewById<TextInputEditText>(R.id.etDosageQuantity)
        val etTotalQuantity    = dialogView.findViewById<TextInputEditText>(R.id.etTotalQuantity)

        val rgFrequency = dialogView.findViewById<RadioGroup>(R.id.rgFrequency)
        val btnTime1    = dialogView.findViewById<MaterialButton>(R.id.btnTime1)
        val btnTime2    = dialogView.findViewById<MaterialButton>(R.id.btnTime2)
        val btnTime3    = dialogView.findViewById<MaterialButton>(R.id.btnTime3)

        var currentStep = 1
        var t1: String? = null
        var t2: String? = null
        var t3: String? = null

        fun updateStepVisibility() {
            step1.visibility = if (currentStep == 1) View.VISIBLE else View.GONE
            step2.visibility = if (currentStep == 2) View.VISIBLE else View.GONE
            step3.visibility = if (currentStep == 3) View.VISIBLE else View.GONE
            btnPrevious.visibility = if (currentStep > 1) View.VISIBLE else View.GONE
            btnNext.text = if (currentStep == 3) "Done" else "Next"
        }

        preFill?.let {
            etPrescriptionName.setText(it.prescriptionName)
            etDrugName.setText(it.drugName)
            etDosageQuantity.setText(it.dosageQuantity)
            etTotalQuantity.setText(it.totalDrugQuantity)

            when (it.frequency) {
                "Daily one time"   -> rgFrequency.check(R.id.rbOnceDaily)
                "Daily two times"  -> rgFrequency.check(R.id.rbTwiceDaily)
                "Daily three times"-> rgFrequency.check(R.id.rbThriceDaily)
            }

            t1 = it.time1
            t2 = it.time2
            t3 = it.time3
            currentStep = 3
        } ?: rgFrequency.check(R.id.rbOnceDaily)

        fun refreshTimeButtons() {
            when (rgFrequency.checkedRadioButtonId) {
                R.id.rbOnceDaily  -> { btnTime2.visibility = View.GONE;    btnTime3.visibility = View.GONE;    t2 = null; t3 = null }
                R.id.rbTwiceDaily -> { btnTime2.visibility = View.VISIBLE; btnTime3.visibility = View.GONE;    t3 = null }
                R.id.rbThriceDaily-> { btnTime2.visibility = View.VISIBLE; btnTime3.visibility = View.VISIBLE }
            }
            btnTime1.text = t1 ?: "Select time"
            btnTime2.text = t2 ?: "Select time"
            btnTime3.text = t3 ?: "Select time"
        }

        rgFrequency.setOnCheckedChangeListener { _, _ -> refreshTimeButtons() }
        refreshTimeButtons()

        fun showTimePicker(onPicked: (String) -> Unit) {
            val now = java.util.Calendar.getInstance()
            TimePickerDialog(
                requireContext(),
                { _, h, m ->
                    val amPm  = if (h >= 12) "PM" else "AM"
                    val hour12 = if (h % 12 == 0) 12 else h % 12
                    onPicked(String.format("%02d:%02d %s", hour12, m, amPm))
                },
                now.get(java.util.Calendar.HOUR_OF_DAY),
                now.get(java.util.Calendar.MINUTE),
                false
            ).show()
        }

        btnTime1.setOnClickListener { showTimePicker { t1 = it; btnTime1.text = it } }
        btnTime2.setOnClickListener { showTimePicker { t2 = it; btnTime2.text = it } }
        btnTime3.setOnClickListener { showTimePicker { t3 = it; btnTime3.text = it } }

        fun validateStep1() = etPrescriptionName.validateNotEmpty("Required")
        fun validateStep2() =
            etDrugName.validateNotEmpty("Required") &&
            etDosageQuantity.validateNotEmpty("Required") &&
            etTotalQuantity.validateNotEmpty("Required")
        fun validateStep3() = when (rgFrequency.checkedRadioButtonId) {
            R.id.rbOnceDaily   -> t1 != null
            R.id.rbTwiceDaily  -> t1 != null && t2 != null
            R.id.rbThriceDaily -> t1 != null && t2 != null && t3 != null
            else -> false
        }

        fun buildPrescriptionEntity(): PrescriptionEntity {
            val frequency = when (rgFrequency.checkedRadioButtonId) {
                R.id.rbOnceDaily   -> "Daily one time"
                R.id.rbTwiceDaily  -> "Daily two times"
                R.id.rbThriceDaily -> "Daily three times"
                else               -> "Unknown"
            }
            return PrescriptionEntity(
                prescriptionName  = etPrescriptionName.text.toString().trim(),
                drugName          = etDrugName.text.toString().trim(),
                dosageQuantity    = etDosageQuantity.text.toString().trim(),
                totalDrugQuantity = etTotalQuantity.text.toString().trim(),
                frequency         = frequency,
                time1             = t1,
                time2             = t2,
                time3             = t3
            )
        }

        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()

        btnPrevious.setOnClickListener {
            if (currentStep == 1) dialog.dismiss()
            else { currentStep--; updateStepVisibility() }
        }

        btnNext.setOnClickListener {
            when (currentStep) {
                1 -> if (validateStep1()) { currentStep = 2; updateStepVisibility() }
                2 -> if (validateStep2()) { currentStep = 3; updateStepVisibility() }
                3 -> if (validateStep3()) {
                    val entity = buildPrescriptionEntity()
                    lifecycleScope.launch {
                        try {
                            if (position != null) {
                                val original = prescriptionDao.getAllPrescriptions().first()[position]
                                val updated  = original.copy(
                                    prescriptionName  = entity.prescriptionName,
                                    drugName          = entity.drugName,
                                    dosageQuantity    = entity.dosageQuantity,
                                    totalDrugQuantity = entity.totalDrugQuantity,
                                    frequency         = entity.frequency,
                                    time1             = entity.time1,
                                    time2             = entity.time2,
                                    time3             = entity.time3
                                )
                                MedicationAlarmManager.cancelAlarms(requireContext(), original)
                                prescriptionDao.update(updated)
                                MedicationAlarmManager.scheduleAlarms(requireContext(), updated)
                                Log.d(TAG, "✅ Updated: ${updated.drugName}")
                                Toast.makeText(requireContext(), "✅ Updated with reminders!", Toast.LENGTH_SHORT).show()
                            } else {
                                val id       = prescriptionDao.insert(entity)
                                val inserted = entity.copy(id = id)
                                MedicationAlarmManager.scheduleAlarms(requireContext(), inserted)
                                Log.d(TAG, "✅ Saved: ${inserted.drugName}")
                                Toast.makeText(requireContext(), "✅ Added with reminders!", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Error saving", e)
                            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    dialog.dismiss()
                } else {
                    Toast.makeText(requireContext(), "Please select all required times", Toast.LENGTH_SHORT).show()
                }
            }
        }

        updateStepVisibility()
        dialog.show()
    }

    private fun editPrescription(position: Int) {
        lifecycleScope.launch {
            val list = prescriptionDao.getAllPrescriptions().first()
            showAddPrescriptionDialog(list[position], position)
        }
    }
}
