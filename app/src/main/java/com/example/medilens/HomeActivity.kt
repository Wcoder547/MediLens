package com.example.medilens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.ClearCredentialException
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

class HomeActivity : AppCompatActivity() {

    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var btnLogout:        ImageButton
    lateinit var btnTakeNow: com.google.android.material.button.MaterialButton
    private lateinit var auth:             FirebaseAuth
    private lateinit var credentialManager: CredentialManager

    // Tracks if Take Now button is currently pulsing
    private var isPulsing = false

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Notification permission denied. You may miss medication reminders.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        auth              = FirebaseAuth.getInstance()
        credentialManager = CredentialManager.create(this)

        requestNotificationPermission()

        btnLogout  = findViewById(R.id.btnLogout)
        btnTakeNow = findViewById(R.id.btnTakeNow)

        btnLogout.setOnClickListener { view -> showLogoutMenu(view) }

        bottomNavigation = findViewById(R.id.bottomNavigation)

        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
        }

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    loadFragment(HomeFragment())
                    true
                }
                R.id.nav_prescriptions -> {
                    stopPulse()
                    btnTakeNow.visibility = View.GONE
                    loadFragment(PrescriptionsFragment())
                    true
                }
                R.id.nav_calendar -> {
                    stopPulse()
                    btnTakeNow.visibility = View.GONE
                    loadFragment(CalendarFragment())
                    true
                }
                else -> false
            }
        }

        // Handle if opened from notification
        handleReminderIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleReminderIntent(intent)
    }

    // ── Handle notification tap ───────────────────────────────────────────
    private fun handleReminderIntent(intent: Intent) {
        val fromReminder   = intent.getBooleanExtra("from_reminder", false)
        val medicationName = intent.getStringExtra("reminder_medication_name") ?: return
        val medicationTime = intent.getStringExtra("reminder_medication_time") ?: return

        if (medicationName.isEmpty() || medicationTime.isEmpty()) return

        lifecycleScope.launch {
            val db            = AppDatabase.getDatabase(this@HomeActivity)
            val prescriptions = db.prescriptionDao().getAllPrescriptions().first()

            // If opened from 5-min reminder, cancel the exact alarm
            // so it doesn't fire again when user is already in the app
            if (fromReminder) {
                prescriptions.forEach { prescription ->
                    if (prescription.drugName.equals(medicationName, ignoreCase = true)) {
                        listOfNotNull(
                            prescription.time1,
                            prescription.time2,
                            prescription.time3
                        ).forEach { time ->
                            if (time.trim().equals(medicationTime.trim(), ignoreCase = true)) {
                                MedicationAlarmManager.cancelExactAlarm(
                                    this@HomeActivity, prescription, time
                                )
                            }
                        }
                    }
                }
            }

            // Start pulsing the Take Now button to draw attention visually
            // No voice here — voice only comes from MedicationAlarmReceiver
            startPulse()
        }
    }

    // ── Pulsing animation for Take Now button ─────────────────────────────
    fun startPulse() {
        if (isPulsing) return
        isPulsing = true

        val pulse = AlphaAnimation(1f, 0.3f).apply {
            duration        = 700
            repeatMode      = Animation.REVERSE
            repeatCount     = Animation.INFINITE
        }
        btnTakeNow.startAnimation(pulse)
        btnTakeNow.visibility = View.VISIBLE
    }

    fun stopPulse() {
        if (!isPulsing) return
        isPulsing = false
        btnTakeNow.clearAnimation()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> { }

                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    AlertDialog.Builder(this)
                        .setTitle("Notification Permission Required")
                        .setMessage("MediLens needs notification permission to remind you to take your medications on time.")
                        .setPositiveButton("Grant") { _, _ ->
                            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }

                else -> {
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun showLogoutMenu(view: android.view.View) {
        val popup = PopupMenu(this, view)
        popup.menuInflater.inflate(R.menu.menu_home, popup.menu)
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_logout -> { showLogoutDialog(); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { _, _ -> performLogout() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performLogout() {
        Toast.makeText(this, "Logging out...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            try {
                // ── Cancel ALL alarms before signing out ──────────────────
                val db = AppDatabase.getDatabase(this@HomeActivity)
                val allPrescriptions = db.prescriptionDao().getAllPrescriptions().first()
                allPrescriptions.forEach { prescription ->
                    MedicationAlarmManager.cancelAlarms(this@HomeActivity, prescription)
                }
                // ─────────────────────────────────────────────────────────

                auth.signOut()
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
                startActivity(Intent(this@HomeActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            } catch (e: ClearCredentialException) {
                Toast.makeText(this@HomeActivity, "Error during logout: ${e.message}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@HomeActivity, "Unexpected error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}