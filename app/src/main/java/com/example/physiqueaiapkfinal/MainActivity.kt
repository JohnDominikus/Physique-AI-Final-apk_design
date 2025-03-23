package com.example.physiqueaiapkfinal

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationView

// MainActivity.kt
class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toolbar: Toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views
        drawerLayout = findViewById(R.id.drawerLayout)
        toolbar = findViewById(R.id.toolbar)

        // Set up the toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.menu) // Hamburger icon

        // Handle toolbar navigation click (toggle drawer)
        toolbar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // Handle NavigationView item clicks
        val navView = findViewById<NavigationView>(R.id.navView)
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    // Handle home click
                    true
                }
                R.id.nav_exercise -> {
                    // Handle exercise click
                    true
                }
                R.id.nav_posture -> {
                    // Handle posture click
                    true
                }
                R.id.nav_settings -> {
                    // Handle settings click
                    true
                }
                R.id.nav_logout -> {
                    // Handle logout
                    true
                }
                else -> false
            }
        }

        // Handle BottomNavigationView item clicks
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        bottomNavigationView.setOnNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    // Handle home click
                    true
                }
                R.id.nav_exercise -> {
                    // Handle exercise click
                    true
                }
                R.id.nav_posture -> {
                    // Handle posture click
                    true
                }
                R.id.nav_settings -> {
                    // Handle settings click
                    true
                }
                R.id.nav_logout -> {
                    // Handle logout
                    true
                }
                else -> false
            }
        }
    }
}