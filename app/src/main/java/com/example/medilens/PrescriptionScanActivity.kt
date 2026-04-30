package com.example.medilens

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PrescriptionScanActivity : AppCompatActivity() {

    // ── Views ──────────────────────────────────────────────────────────────
    private lateinit var layoutIdle:      LinearLayout
    private lateinit var layoutPreview:   LinearLayout
    private lateinit var layoutProcessing: LinearLayout
    private lateinit var ivPreview:       ImageView
    private lateinit var tvFileName:      TextView
    private lateinit var tvFileType:      TextView
    private lateinit var btnChooseFile:   Button
    private lateinit var btnScanAnother:  Button
    private lateinit var btnProcess:      Button
    private lateinit var progressBar:     ProgressBar
    private lateinit var tvProgressStep:  TextView

    private var selectedUri: Uri? = null

    // ── File picker ────────────────────────────────────────────────────────
    private val filePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            showPreview(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_prescription_scan)

        bindViews()
        setupClickListeners()

        // Initialise PDFBox resource loader once
        PdfTextExtractor.init(this)
    }

    // ── View binding ───────────────────────────────────────────────────────
    private fun bindViews() {
        layoutIdle       = findViewById(R.id.layoutIdle)
        layoutPreview    = findViewById(R.id.layoutPreview)
        layoutProcessing = findViewById(R.id.layoutProcessing)
        ivPreview        = findViewById(R.id.ivPreview)
        tvFileName       = findViewById(R.id.tvFileName)
        tvFileType       = findViewById(R.id.tvFileType)
        btnChooseFile    = findViewById(R.id.btnChooseFile)
        btnScanAnother   = findViewById(R.id.btnScanAnother)
        btnProcess       = findViewById(R.id.btnProcess)
        progressBar      = findViewById(R.id.progressBar)
        tvProgressStep   = findViewById(R.id.tvProgressStep)
    }

    private fun setupClickListeners() {
        btnChooseFile.setOnClickListener  { openFilePicker() }
        btnScanAnother.setOnClickListener { resetToIdle() }
        btnProcess.setOnClickListener     { startPipeline() }

        // Back arrow
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
    }

    // ── File picker ────────────────────────────────────────────────────────
    private fun openFilePicker() {
        // "*/*" shows all files — user can pick image, PDF, or Word
        filePicker.launch("*/*")
    }

    // ── Preview ────────────────────────────────────────────────────────────
    private fun showPreview(uri: Uri) {
        val mimeType = contentResolver.getType(uri) ?: "unknown"
        val fileName = getFileName(uri)

        tvFileName.text = fileName
        tvFileType.text = mimeTypeLabel(mimeType)

        when {
            mimeType.startsWith("image/") -> {
                ivPreview.visibility = View.VISIBLE
                ivPreview.setImageURI(uri)
            }
            else -> {
                ivPreview.visibility = View.VISIBLE
                ivPreview.setImageResource(fileIcon(mimeType))
            }
        }

        layoutIdle.visibility      = View.GONE
        layoutPreview.visibility   = View.VISIBLE
        layoutProcessing.visibility = View.GONE
    }

    // ── Main pipeline ──────────────────────────────────────────────────────
    private fun startPipeline() {
        val uri = selectedUri ?: return

        layoutPreview.visibility    = View.GONE
        layoutProcessing.visibility = View.VISIBLE
        progressBar.isIndeterminate = true

        lifecycleScope.launch {
            try {
                // ── Stage 1: Route ─────────────────────────────────────────
                updateProgress("Detecting file type…")
                val route = InputRouter.route(this@PrescriptionScanActivity, uri)

                if (route is InputRouter.RouteResult.Unsupported) {
                    showError("Unsupported file type: ${route.mimeType}\n\nPlease upload an image, PDF, or Word document.")
                    return@launch
                }

                // ── Stage 2: Extract raw text ──────────────────────────────
                val rawText: String = withContext(Dispatchers.IO) {
                    when (route) {
                        is InputRouter.RouteResult.Image -> {
                            updateProgress("Reading prescription image…\n(This may take up to 60 seconds)")
                            DonutApiClient.extractText(
                                this@PrescriptionScanActivity, route.uri
                            )
                        }
                        is InputRouter.RouteResult.Pdf -> {
                            updateProgress("Extracting text from PDF…")
                            PdfTextExtractor.extract(
                                this@PrescriptionScanActivity, route.uri
                            )
                        }
                        is InputRouter.RouteResult.Word -> {
                            updateProgress("Extracting text from Word document…")
                            WordTextExtractor.extract(
                                this@PrescriptionScanActivity, route.uri, route.mimeType
                            )
                        }
                        else -> throw Exception("Routing error")
                    }
                }

                if (rawText.isBlank()) {
                    showError("Could not extract any text from this file.\n\nFor images, please ensure the prescription is clear and well-lit.")
                    return@launch
                }

                // ── Stage 3: NER — Gemini with rule engine fallback ────────
                updateProgress("Identifying medicines…")
                val parsed: List<ParsedMedicine> = try {
                    GeminiNerClient.extractMedicines(rawText)
                } catch (geminiError: Exception) {
                    // Gemini failed — try rule engine offline fallback
                    android.util.Log.e("GeminiNER", "Gemini failed: ${geminiError.message}", geminiError)
                    updateProgress("AI unavailable — using offline parser…")
                    val fallback = RuleEngineParser.parse(rawText)
                    if (fallback.isEmpty()) {
                        showError("Could not identify any medicines.\n\nPlease check the prescription and try again, or add medicines manually.")
                        return@launch
                    }
                    fallback
                }

                // ── Stage 4: Validate ──────────────────────────────────────
                updateProgress("Validating results…")
                val validated = PrescriptionValidator.validate(parsed)

                // ── Stage 5: Launch confirmation screen ───────────────────
                updateProgress("Opening confirmation screen…")
                val intent = Intent(
                    this@PrescriptionScanActivity,
                    PrescriptionConfirmActivity::class.java
                ).apply {
                    putParcelableArrayListExtra(
                        PrescriptionConfirmActivity.EXTRA_MEDICINES,
                        ArrayList(validated.map { it.toParcelable() })
                    )
                }
                startActivity(intent)
                finish()

            } catch (e: Exception) {
                showError("Something went wrong:\n${e.message}\n\nPlease try again.")
            }
        }
    }

    // ── UI helpers ─────────────────────────────────────────────────────────
    private suspend fun updateProgress(message: String) = withContext(Dispatchers.Main) {
        tvProgressStep.text = message
    }

    private suspend fun showError(message: String) = withContext(Dispatchers.Main) {
        layoutProcessing.visibility = View.GONE
        layoutPreview.visibility    = View.VISIBLE
        Toast.makeText(this@PrescriptionScanActivity, message, Toast.LENGTH_LONG).show()
    }

    private fun resetToIdle() {
        selectedUri = null
        layoutIdle.visibility       = View.VISIBLE
        layoutPreview.visibility    = View.GONE
        layoutProcessing.visibility = View.GONE
    }

    private fun getFileName(uri: Uri): String {
        var name = "prescription_file"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && idx >= 0) name = cursor.getString(idx)
        }
        return name
    }

    private fun mimeTypeLabel(mimeType: String): String = when {
        mimeType.startsWith("image/") -> "📷 Image"
        mimeType == "application/pdf" -> "📄 PDF Document"
        mimeType.contains("word") || mimeType.contains("openxml") -> "📝 Word Document"
        else -> "📁 $mimeType"
    }

    private fun fileIcon(mimeType: String): Int = when {
        mimeType == "application/pdf"      -> R.drawable.ic_pdf
        mimeType.contains("word")
            || mimeType.contains("openxml") -> R.drawable.ic_word
        else                               -> R.drawable.ic_document
    }
}
