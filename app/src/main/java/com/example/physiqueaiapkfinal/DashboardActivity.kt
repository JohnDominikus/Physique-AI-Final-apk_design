package com.example.physiqueaiapkfinal

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.physiqueaiapkfinal.WorkoutTaskActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class DashboardActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        auth = FirebaseAuth.getInstance()

        fetchAndDisplayUserInfo()
        setupBottomNavigation()

        val ivSettings = findViewById<ImageView>(R.id.ivSettings)
        ivSettings.setOnClickListener {
            showSettingsMenu(it)
        }
    }

    private fun navigateToActivity(activityClass: Class<*>) {
        startActivity(Intent(this, activityClass))
    }

    private fun setupBottomNavigation() {
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { /* Already on dashboard */ }
                R.id.nav_workout -> navigateToActivity(WorkoutListActivity::class.java)
                R.id.nav_posture -> navigateToActivity(PoseActivity::class.java)
                R.id.nav_dietary -> navigateToActivity(RecipeListActivity::class.java)
               R.id.nav_task -> navigateToActivity(WorkoutTaskActivity::class.java) // Optional
                else -> Log.e("Dashboard", "Unknown menu item selected")
            }
            true
        }
    }

    @SuppressLint("SetTextI18n")
    private fun fetchAndDisplayUserInfo() {
        val tvUserName = findViewById<TextView>(R.id.tvUserName)
        val tvPhysicalLevel = findViewById<TextView>(R.id.tvPhysicalLevel)
        val ivStar = findViewById<ImageView>(R.id.ivStar)
        val ivVerified = findViewById<ImageView>(R.id.ivVerified)
        val ivMuscles = findViewById<ImageView>(R.id.ivMuscles)
        val tvBmiStatus = findViewById<TextView>(R.id.tvBmiStatus)

        hideIcons(ivStar, ivVerified, ivMuscles)

        val uid = auth.currentUser?.uid ?: run {
            Log.w("Dashboard", "No authenticated user")
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        firestore.collection("userinfo").document(uid)
            .addSnapshotListener { documentSnapshot, e ->
                if (e != null) {
                    Log.e("Dashboard", "Error fetching user info", e)
                    return@addSnapshotListener
                }

                if (documentSnapshot != null && documentSnapshot.exists()) {
                    val personalInfo = documentSnapshot.get("personalInfo") as? Map<*, *>
                    val firstName = personalInfo?.get("firstName")?.toString() ?: ""
                    val lastName = personalInfo?.get("lastName")?.toString() ?: ""
                    tvUserName.text = "$firstName $lastName".trim()

                    val physicalInfo = documentSnapshot.get("physicalInfo") as? Map<*, *>
                    val bodyLevel = physicalInfo?.get("bodyLevel")?.toString()?.lowercase() ?: "beginner"
                    updatePhysicalLevelDisplay(bodyLevel, tvPhysicalLevel, ivStar, ivVerified, ivMuscles)

                    val bmiInfo = documentSnapshot.get("bmiInfo") as? Map<*, *>
                    val rawStatus = bmiInfo?.get("status")?.toString()?.trim()?.lowercase() ?: ""
                    val (statusText, color) = when (rawStatus) {
                        "normal" -> "Normal" to Color.parseColor("#228B22")
                        "overweight" -> "Overweight" to Color.RED
                        "underweight" -> "Underweight" to Color.RED
                        "obese" -> "Obese" to Color.parseColor("#FFA500")
                        "" -> "N/A" to Color.GRAY
                        else -> "Unknown" to Color.GRAY
                    }
                    tvBmiStatus.text = "BMI Status: $statusText"
                    tvBmiStatus.setTextColor(color)
                } else {
                    showDataNotFoundWarning()
                }
            }
    }

    @SuppressLint("SetTextI18n")
    private fun updatePhysicalLevelDisplay(
        level: String,
        tvPhysicalLevel: TextView,
        ivStar: ImageView,
        ivVerified: ImageView,
        ivMuscles: ImageView
    ) {
        hideIcons(ivStar, ivVerified, ivMuscles)

        when (level) {
            "beginner" -> {
                ivStar.visibility = View.VISIBLE
                ivStar.setColorFilter(Color.parseColor("#FFD700"))
                tvPhysicalLevel.text = "Level: Beginner"
                tvPhysicalLevel.setTextColor(Color.parseColor("#FFD700"))
            }
            "intermediate" -> {
                ivVerified.visibility = View.VISIBLE
                ivVerified.setColorFilter(Color.parseColor("#1E90FF"))
                tvPhysicalLevel.text = "Level: Intermediate"
                tvPhysicalLevel.setTextColor(Color.parseColor("#1E90FF"))
            }
            "advanced" -> {
                ivMuscles.visibility = View.VISIBLE
                ivMuscles.setColorFilter(Color.parseColor("#32CD32"))
                tvPhysicalLevel.text = "Level: Advanced"
                tvPhysicalLevel.setTextColor(Color.parseColor("#32CD32"))
            }
            else -> {
                ivStar.visibility = View.VISIBLE
                ivStar.setColorFilter(Color.parseColor("#FFD700"))
                tvPhysicalLevel.text = "Level: Normal"
                tvPhysicalLevel.setTextColor(Color.parseColor("#FFD700"))
            }
        }
    }

    private fun hideIcons(vararg icons: ImageView) {
        icons.forEach { it.visibility = View.GONE }
    }

    private fun showSettingsMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.settings_menu, popup.menu)
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    true
                }
                R.id.menu_logout -> {
                    showLogoutConfirmationDialog()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showDataNotFoundWarning() {
        AlertDialog.Builder(this)
            .setTitle("Profile Incomplete")
            .setMessage("Please complete your profile setup.")
            .setPositiveButton("Go to Profile") { _, _ ->
                startActivity(Intent(this, ProfileActivity::class.java))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLogoutConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Do you want to logout?")
            .setCancelable(false)
            .setPositiveButton("Yes") { dialog, _ ->
                logoutUser()
                dialog.dismiss()
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun logoutUser() {
        auth.signOut()
        getSharedPreferences("USER_DATA", MODE_PRIVATE).edit().clear().apply()
        Toast.makeText(this, "Successfully logged out", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
