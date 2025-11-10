package com.example.medilens

import android.graphics.LinearGradient
import android.graphics.Shader
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val userName = intent.getStringExtra("USER_NAME") ?: "User"
        val userEmail = intent.getStringExtra("USER_EMAIL") ?: ""

        val textView = findViewById<TextView>(R.id.tvGreeting)
        val paint = textView.paint
        val width = paint.measureText(textView.text.toString())
        val textShader = LinearGradient(
            0f, 0f, width, textView.textSize,
            intArrayOf(
                ContextCompat.getColor(this, R.color.primary),
                ContextCompat.getColor(this, R.color.secondary)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        textView.paint.shader = textShader

//        findViewById<TextView>(R.id.welcomeText).text =
//            "Welcome, $userName\nEmail: $userEmail"
    }
}
