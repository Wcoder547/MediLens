package com.example.medilens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import android.widget.TextView
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class PhotoCaptureActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var tvTaskTitle: TextView
    private lateinit var cvPhotoPreview: MaterialCardView
    private lateinit var ivPhotoPreview: ImageView
    private lateinit var btnTakePhoto: MaterialButton
    private lateinit var btnSelectFromGallery: MaterialButton
    private lateinit var btnGoBack: MaterialButton
    private lateinit var btnVerify: MaterialButton

    private var capturedImageUri: Uri? = null
    private var currentPhotoPath: String? = null

    companion object {
        const val EXTRA_SCHEDULE_TITLE = "schedule_title"
        const val EXTRA_SCHEDULE_TIME = "schedule_time"
        const val EXTRA_PRESCRIPTION_IDS = "prescription_ids"
        const val EXTRA_CAPTURED_IMAGE_URI = "captured_image_uri"
    }


    // Camera launcher
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && capturedImageUri != null) {
            onPhotoSelected(capturedImageUri!!)
        }
    }

    // Gallery launcher
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            onPhotoSelected(it)
        }
    }

    // Camera permission launcher
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_photo_capture)

        // Initialize views
        toolbar = findViewById(R.id.toolbar)
        tvTaskTitle = findViewById(R.id.tvTaskTitle)
        cvPhotoPreview = findViewById(R.id.cvPhotoPreview)
        ivPhotoPreview = findViewById(R.id.ivPhotoPreview)
        btnTakePhoto = findViewById(R.id.btnTakePhoto)
        btnSelectFromGallery = findViewById(R.id.btnSelectFromGallery)
        btnGoBack = findViewById(R.id.btnGoBack)
        btnVerify = findViewById(R.id.btnVerify)

        // Setup toolbar
        toolbar.setNavigationOnClickListener {
            finish()
        }

        // Get title from intent
        val scheduleTitle = intent.getStringExtra(EXTRA_SCHEDULE_TITLE) ?: "Taking medication"
        tvTaskTitle.text = "Taking ${extractLabel(scheduleTitle)}"

        // Setup buttons
        btnTakePhoto.setOnClickListener {
            checkCameraPermissionAndLaunch()
        }

        btnSelectFromGallery.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        btnGoBack.setOnClickListener {
            finish()
        }

        btnVerify.setOnClickListener {
            capturedImageUri?.let { uri ->
                val intent = Intent(this, VerificationLoadingActivity::class.java).apply {
                    putExtra(VerificationLoadingActivity.EXTRA_SCHEDULE_TITLE, tvTaskTitle.text.toString())
                    putExtra(VerificationLoadingActivity.EXTRA_SCHEDULE_TIME, intent.getStringExtra(EXTRA_SCHEDULE_TIME))
                    putExtra(VerificationLoadingActivity.EXTRA_PRESCRIPTION_IDS, intent.getLongArrayExtra(EXTRA_PRESCRIPTION_IDS))
                    putExtra(VerificationLoadingActivity.EXTRA_IMAGE_URI, uri.toString())
                }
                startActivity(intent)
            } ?: run {
                Toast.makeText(this, "Please capture or select a photo first", Toast.LENGTH_SHORT).show()
            }
        }


    }

    private fun extractLabel(fullTitle: String): String {
        return fullTitle.split(" - ").firstOrNull() ?: fullTitle
    }

    private fun checkCameraPermissionAndLaunch() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                launchCamera()
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun launchCamera() {
        val photoFile = createImageFile()
        photoFile?.let {
            val uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                it
            )
            capturedImageUri = uri
            takePictureLauncher.launch(uri)
        }
    }

    private fun createImageFile(): File? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir = getExternalFilesDir(null)
            File.createTempFile(
                "MEDILENS_${timeStamp}_",
                ".jpg",
                storageDir
            ).apply {
                currentPhotoPath = absolutePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun onPhotoSelected(uri: Uri) {
        capturedImageUri = uri

        // Show photo preview
        cvPhotoPreview.visibility = View.VISIBLE
        ivPhotoPreview.setImageURI(uri)

        // Enable verify button with purple color
        btnVerify.isEnabled = true
        btnVerify.backgroundTintList = ContextCompat.getColorStateList(this, R.color.purple_700)

        Toast.makeText(this, "Photo selected! Click Verify to continue", Toast.LENGTH_SHORT).show()
    }
}
