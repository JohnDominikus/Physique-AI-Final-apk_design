package com.example.physiqueaiapkfinal

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.physiqueaiapkfinal.LoginActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class DashboardActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Fetch and display user info
        fetchAndDisplayUserInfo()

        // Setup bottom navigation
        setupBottomNavigation()

        // Setup click listeners for the FrameLayout widgets
        setupFrameLayoutClickListeners()

        // Setup logout button click listener
        setupLogoutButton()

        // Setup settings icon click listener (assumed id: ivSettings)
        val ivSettings = findViewById<ImageView>(R.id.ivSettings)
        ivSettings.setOnClickListener { view ->
            showSettingsMenu(view)
        }
    }

    /**
     * Setup the click listeners for the FrameLayout widgets.
     */
    private fun setupFrameLayoutClickListeners() {
        findViewById<View>(R.id.btnPosture).setOnClickListener {
            navigateToActivity(PostureActivity::class.java)
        }
        findViewById<View>(R.id.btnExercise).setOnClickListener {
            navigateToActivity(WorkoutActivity::class.java)
        }
        findViewById<View>(R.id.btnDietary).setOnClickListener {
            navigateToActivity(DietPlannerActivity::class.java)
        }
        findViewById<View>(R.id.btnTask).setOnClickListener {
            navigateToActivity(TaskActivity::class.java)
        }
        findViewById<View>(R.id.btnBMI).setOnClickListener {
            navigateToActivity(BmiCalculatorActivity::class.java)
        }
    }

    /**
     * Setup logout button click listener.
     */
    private fun setupLogoutButton() {
        val logoutCard = findViewById<MaterialCardView>(R.id.btnLogout)
        logoutCard.setOnClickListener {
            showLogoutConfirmationDialog()
        }
    }

    /**
     * Generic method to navigate to any activity.
     */
    private fun navigateToActivity(activityClass: Class<*>) {
        startActivity(Intent(this, activityClass))
    }

    /**
     * Setup bottom navigation item selection logic.
     */
    private fun setupBottomNavigation() {
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { /* Already on home */ }
                R.id.nav_workout -> navigateToActivity(WorkoutActivity::class.java)
                R.id.nav_posture -> navigateToActivity(PostureActivity::class.java)
                R.id.nav_dietary -> navigateToActivity(DietPlannerActivity::class.java)
                R.id.nav_task -> navigateToActivity(TaskActivity::class.java)
                else -> Log.e("Dashboard", "Unknown menu item selected")
            }
            true
        }
    }

    /**
     * Fetches and displays the logged-in user's information in real-time.
     */
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

        // Set up Firestore listener for real-time updates
        firestore.collection("userinfo").document(uid)
            .addSnapshotListener { documentSnapshot, e ->
                if (e != null) {
                    Log.e("Dashboard", "Error fetching user info", e)
                    return@addSnapshotListener
                }

                if (documentSnapshot != null && documentSnapshot.exists()) {
                    // Retrieve physicalInfo
                    val physicalInfo = documentSnapshot.get("physicalInfo") as? Map<*, *>
                    val bodyLevel = physicalInfo?.get("bodyLevel")?.toString()?.lowercase() ?: "beginner"
                    tvPhysicalLevel.text = "Level: ${bodyLevel.replaceFirstChar { it.uppercase() }}"

                    when (bodyLevel) {
                        "beginner" -> {
                            ivStar.visibility = View.VISIBLE
                            ivStar.setColorFilter(Color.parseColor("#FFD700"))  // Gold color for beginner
                            tvPhysicalLevel.text =
                                "Level: Beginner"  // Update the text for beginner
                            tvPhysicalLevel.setTextColor(Color.parseColor("#FFD700"))  // Optional: set text color to match the star color
                        }

                        "intermediate" -> {
                            ivVerified.visibility = View.VISIBLE
                            ivVerified.setColorFilter(Color.parseColor("#1E90FF"))  // Dodger Blue for intermediate
                            tvPhysicalLevel.text =
                                "Level: Intermediate"  // Update the text for intermediate
                            tvPhysicalLevel.setTextColor(Color.parseColor("#1E90FF"))  // Optional: set text color to match the verified color
                        }

                        "advanced" -> {
                            ivMuscles.visibility = View.VISIBLE
                            ivMuscles.setColorFilter(Color.parseColor("#32CD32"))  // Lime Green for advanced
                            tvPhysicalLevel.text =
                                "Level: Advanced"  // Update the text for advanced
                            tvPhysicalLevel.setTextColor(Color.parseColor("#32CD32"))  // Optional: set text color to match the muscles color
                        }

                        else -> {
                            ivStar.visibility = View.VISIBLE
                            ivStar.setColorFilter(Color.parseColor("#FFD700"))  // Default to Gold color for "normal" state
                            tvPhysicalLevel.text = "Level: Normal"  // Default text
                            tvPhysicalLevel.setTextColor(Color.parseColor("#FFD700"))  // Default text color (Gold)
                        }
                    }

                        // Retrieve personalInfo
                    val personalInfo = documentSnapshot.get("personalInfo") as? Map<*, *>
                    val firstName = personalInfo?.get("firstName")?.toString() ?: ""
                    val lastName = personalInfo?.get("lastName")?.toString() ?: ""
                    tvUserName.text = "$firstName $lastName".trim()

                    // Retrieve bmiInfo and update BMI status TextView.
                    val bmiInfo = documentSnapshot.get("bmiInfo") as? Map<*, *>
                    val rawStatus = bmiInfo?.get("status")?.toString()?.trim()?.lowercase() ?: ""
                    val (statusToDisplay, color) = when (rawStatus) {
                        "normal" -> Pair("Normal", Color.parseColor("#228B22")) // Lime Green
                        "overweight" -> Pair("Overweight", Color.RED)
                        "underweight" -> Pair("Underweight", Color.RED)
                        "obese" -> Pair("Obese", Color.parseColor("#FFA500")) // Orange
                        "" -> Pair("N/A", Color.GRAY)
                        else -> Pair("Unknown", Color.GRAY)
                    }
                    tvBmiStatus.apply {
                        text = "BMI Status: $statusToDisplay"
                        setTextColor(color)
                    }
                } else {
                    Log.w("Dashboard", "No document found for user $uid")
                    showDataNotFoundWarning()
                }
            }
    }

    /**
     * Hides all provided image views.
     */
    private fun hideIcons(vararg icons: ImageView) {
        icons.forEach { it.visibility = View.GONE }
    }

    /**
     * Displays a settings menu (pop-up menu) when ivSettings is clicked.
     */
    private fun showSettingsMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.settings_menu, popup.menu)
        popup.setOnMenuItemClickListener { menuItem: MenuItem ->
            when (menuItem.itemId) {
                R.id.menu_profile -> {
                    // Navigate to the ProfileActivity
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

    /**
     * Displays a dialog prompting the user to complete their profile.
     */
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

    /**
     * Displays a logout confirmation dialog.
     */
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

    /**
     * Logs out the user, clears session data, and navigates to the login screen.
     */
    private fun logoutUser() {
        auth.signOut()
        val sharedPreferences = getSharedPreferences("USER_DATA", MODE_PRIVATE)
        sharedPreferences.edit().clear().apply()
        Toast.makeText(this, "Successfully logged out", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
