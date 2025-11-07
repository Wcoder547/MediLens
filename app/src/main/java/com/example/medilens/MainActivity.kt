package com.example.medilens

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID

// ✅ Credential Manager (AndroidX)
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.NoCredentialException

// ✅ Google Identity Services
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException


class MainActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ✅ Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // ✅ Initialize Credential Manager (new API)
        credentialManager = CredentialManager.create(this)

        // ✅ Google Sign-In Button
        findViewById<MaterialButton>(R.id.googleButton).setOnClickListener {
            signInWithGoogle()
        }

        // Optional: Sign Out Button (for testing)
        // findViewById<MaterialButton>(R.id.signOutButton).setOnClickListener {
        //     signOut()
        // }
    }

    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            Log.d(TAG, "✅ User already signed in: ${currentUser.displayName}")
            navigateToHome(currentUser)
        } else {
            Log.d(TAG, "ℹ️ No user signed in yet.")
        }
    }

    // 🔹 Step 1: Sign In with Google using Credential Manager
    private fun signInWithGoogle() {
        // Generate a cryptographically secure nonce
        val rawNonce = UUID.randomUUID().toString()
        val hashedNonce = hashNonce(rawNonce)

        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(getString(R.string.default_web_client_id))
            .setFilterByAuthorizedAccounts(false) // Show all Google accounts
            .setAutoSelectEnabled(true) // Auto-select if only one account
            .setNonce(hashedNonce) // Security: prevents replay attacks
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try {
                Log.d(TAG, "🔹 Starting Google Sign-In flow...")
                val result = credentialManager.getCredential(
                    request = request,
                    context = this@MainActivity
                )
                Log.d(TAG, "✅ Credential received successfully")
                handleSignIn(result)
            } catch (e: NoCredentialException) {
                Log.e(TAG, "❌ No credentials available", e)
                Toast.makeText(
                    this@MainActivity,
                    "No Google accounts found. Please add an account.",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: GetCredentialException) {
                Log.e(TAG, "❌ Sign-in failed: ${e.message}", e)
                Toast.makeText(
                    this@MainActivity,
                    "Google Sign-In failed: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Unexpected error: ${e.message}", e)
                Toast.makeText(
                    this@MainActivity,
                    "An unexpected error occurred",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // 🔹 Generate a secure hashed nonce (SHA-256)
    private fun hashNonce(rawNonce: String): String {
        val bytes = rawNonce.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    // 🔹 Step 2: Handle Google Credential result
    private fun handleSignIn(result: GetCredentialResponse) {
        val credential = result.credential

        when (credential) {
            is CustomCredential -> {
                if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    try {
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        val idToken = googleIdTokenCredential.idToken

                        Log.d(TAG, "✅ Got Google ID Token")
                        Log.d(TAG, "📧 User Email: ${googleIdTokenCredential.id}")
                        Log.d(TAG, "👤 Display Name: ${googleIdTokenCredential.displayName}")

                        if (idToken.isNotEmpty()) {
                            firebaseAuthWithGoogle(idToken)
                        } else {
                            Log.e(TAG, "❌ ID token is empty!")
                            Toast.makeText(this, "Invalid ID token", Toast.LENGTH_SHORT).show()
                        }

                    } catch (e: GoogleIdTokenParsingException) {
                        Log.e(TAG, "❌ GoogleIdTokenCredential parsing error", e)
                        Toast.makeText(this, "Failed to parse Google credentials", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.w(TAG, "❌ Unexpected credential type: ${credential.type}")
                    Toast.makeText(this, "Invalid credential type", Toast.LENGTH_SHORT).show()
                }
            }

            else -> {
                Log.w(TAG, "❌ Unexpected credential class: ${credential::class.simpleName}")
                Toast.makeText(this, "Unexpected credential format", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 🔹 Step 3: Authenticate with Firebase using Google ID token
    private fun firebaseAuthWithGoogle(idToken: String) {
        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)

        auth.signInWithCredential(firebaseCredential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    Log.d(TAG, "✅ Firebase Authentication successful")
                    Log.d(TAG, "👤 User: ${user?.displayName} (${user?.email})")
                    Log.d(TAG, "🆔 UID: ${user?.uid}")

                    Toast.makeText(
                        this,
                        "Welcome, ${user?.displayName ?: "User"}!",
                        Toast.LENGTH_SHORT
                    ).show()

                    navigateToHome(user)
                } else {
                    // Handle specific Firebase Auth errors
                    val errorCode = task.exception?.message
                    Log.e(TAG, "❌ Firebase Authentication failed: $errorCode", task.exception)

                    val errorMessage = when {
                        errorCode?.contains("network", ignoreCase = true) == true ->
                            "Network error. Check your connection."
                        errorCode?.contains("invalid", ignoreCase = true) == true ->
                            "Invalid credentials. Please try again."
                        else -> "Authentication failed: ${task.exception?.message}"
                    }

                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
    }

    // 🔹 Navigate to Home Screen after successful login
    private fun navigateToHome(user: FirebaseUser?) {
        user?.let {
            val intent = Intent(this, HomeActivity::class.java).apply {
                putExtra("USER_NAME", it.displayName)
                putExtra("USER_EMAIL", it.email)
                putExtra("USER_PHOTO_URL", it.photoUrl?.toString())
                putExtra("USER_UID", it.uid)
                // Add these flags to prevent going back to login
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }
    }

    // 🔹 Sign out user
    private fun signOut() {
        Log.d(TAG, "🔹 Signing out user...")

        // Sign out from Firebase
        auth.signOut()

        // Clear Credential Manager state
        lifecycleScope.launch {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
                Log.d(TAG, "✅ Credentials cleared successfully")
                updateUI(null)
                Toast.makeText(
                    this@MainActivity,
                    "Signed out successfully",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: ClearCredentialException) {
                Log.e(TAG, "❌ Error clearing credentials: ${e.message}", e)
                Toast.makeText(
                    this@MainActivity,
                    "Error during sign out",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // 🔹 Update UI after login/logout
    private fun updateUI(user: FirebaseUser?) {
        if (user != null) {
            Log.d(TAG, "✅ UI Updated - User signed in: ${user.displayName}")
            // You can update UI elements here if needed
            // e.g., show user profile, hide sign-in button, etc.
        } else {
            Log.d(TAG, "ℹ️ UI Updated - User signed out")
            // Show sign-in button, hide profile, etc.
        }
    }
}
