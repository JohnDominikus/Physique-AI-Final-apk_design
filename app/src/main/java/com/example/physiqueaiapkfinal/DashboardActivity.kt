package com.example.physiqueaiapkfinal

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.physiqueaiapkfinal.models.DashboardInfo
import com.example.physiqueaiapkfinal.utils.UserOperations
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class DashboardActivity : AppCompatActivity() {

    private lateinit var userOperations: UserOperations
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        // ✅ Initialize Firebase Auth and UserOperations
        auth = FirebaseAuth.getInstance()
        userOperations = UserOperations(this)

        // ✅ Display user info
        fetchAndDisplayUserInfo()

        // 🔥 Bottom navigation setup
        setupBottomNavigation()
    }

    /**
     * ✅ Sets up the bottom navigation listener
     */
    private fun setupBottomNavigation() {
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                // Example navigation handlers
                R.id.menu_home -> startActivity(Intent(this, DashboardActivity::class.java))
                R.id.menu_profile -> startActivity(Intent(this, ProfileActivity::class.java))
                R.id.menu_settings -> startActivity(Intent(this, SettingsActivity::class.java))
                else -> Log.e("Dashboard", "Unknown menu item selected")
            }
            true
        }
    }

    /**
     * ✅ Fetches and displays the logged-in user's information.
     */
    private fun fetchAndDisplayUserInfo() {
        val tvUserName = findViewById<TextView>(R.id.tvUserName)
        val tvPhysicalLevel = findViewById<TextView>(R.id.tvPhysicalLevel)
        val ivStar = findViewById<ImageView>(R.id.ivStar)
        val ivVerified = findViewById<ImageView>(R.id.ivVerified)
        val ivMuscles = findViewById<ImageView>(R.id.ivMuscles)

        // ✅ Hide all icons initially
        hideIcons(ivStar, ivVerified, ivMuscles)

        val currentUser: FirebaseUser? = auth.currentUser

        if (currentUser != null) {
            val firebaseUserId = currentUser.uid
            Log.d("Dashboard", "Fetching user info for Firebase ID: $firebaseUserId")

            val dashboardInfo: DashboardInfo? = userOperations.getDashboardInfoById(firebaseUserId)

            if (dashboardInfo != null) {
                // ✅ Display full name
                val fullName = "${dashboardInfo.firstName} ${dashboardInfo.lastName}".trim()
                tvUserName.text = if (fullName.isNotBlank()) fullName else "Guest User"

                // ✅ Display physical level and corresponding icon
                val physicalLevel = dashboardInfo.bodyLevel ?: "Beginner"
                tvPhysicalLevel.text = "Level: $physicalLevel"

                updatePhysicalLevelUI(physicalLevel, tvPhysicalLevel, ivStar, ivVerified, ivMuscles)
            } else {
                Log.e("Dashboard", "No user info found for ID: $firebaseUserId")
                displayGuestUser(tvUserName, tvPhysicalLevel)
            }
        } else {
            Log.e("Dashboard", "No authenticated user found")
            displayGuestUser(tvUserName, tvPhysicalLevel)
        }
    }

    /**
     * ✅ Hides all physical level icons.
     */
    private fun hideIcons(vararg icons: ImageView) {
        icons.forEach { it.visibility = View.GONE }
    }

    /**
     * ✅ Updates the UI based on physical level.
     */
    private fun updatePhysicalLevelUI(
        level: String,
        tvLevel: TextView,
        ivStar: ImageView,
        ivVerified: ImageView,
        ivMuscles: ImageView
    ) {
        hideIcons(ivStar, ivVerified, ivMuscles)  // Ensure all icons are hidden first

        when (level.lowercase()) {
            "beginner" -> {
                tvLevel.setTextColor(getColor(R.color.green))
                ivStar.visibility = View.VISIBLE
            }
            "intermediate" -> {
                tvLevel.setTextColor(getColor(R.color.orange))
                ivVerified.visibility = View.VISIBLE
            }
            "advanced" -> {
                tvLevel.setTextColor(getColor(R.color.red))
                ivMuscles.visibility = View.VISIBLE
            }
            else -> {
                tvLevel.setTextColor(getColor(R.color.black))
            }
        }
    }

    /**
     * ✅ Displays guest user info if no authenticated user is found.
     */
    private fun displayGuestUser(tvUserName: TextView, tvPhysicalLevel: TextView) {
        tvUserName.text = "Guest User"
        tvPhysicalLevel.text = "Level: N/A"
        tvPhysicalLevel.setTextColor(getColor(R.color.black))
    }
}
