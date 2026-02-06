package com.example.medilens

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity  // CHANGED: From ComponentActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import com.google.android.material.bottomnavigation.BottomNavigationView

class HomeActivity : AppCompatActivity() {  // CHANGED
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    loadFragment(HomeFragment())
                    true
                }
                R.id.nav_prescriptions -> {
                    loadFragment(PrescriptionsFragment())
                    true
                }
                R.id.nav_calendar -> {
                    loadFragment(CalendarFragment())
                    true
                }
                else -> false
            }
        }

        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.nav_home
            loadFragment(HomeFragment())
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.commitNow {  // Now works perfectly!
            replace(R.id.fragment_container, fragment)
        }
    }
}
