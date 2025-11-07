package com.example.medilens

import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val userName = intent.getStringExtra("USER_NAME") ?: "User"
        val userEmail = intent.getStringExtra("USER_EMAIL") ?: ""

        findViewById<TextView>(R.id.welcomeText).text =
            "Welcome, $userName\nEmail: $userEmail"
    }
}
