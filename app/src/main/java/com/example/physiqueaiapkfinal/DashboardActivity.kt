package com.example.physiqueaiapkfinal

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.physiqueaiapkfinal.utils.LoginActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
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

        // Setup click listeners for the FrameLayout widgets in the GridLayout
        setupFrameLayoutClickListeners()

        // Setup logout button click listener
        findViewById<View>(R.id.btnLogout).setOnClickListener {
            showLogoutConfirmationDialog()
        }
    }

    /**
     * Setup the click listeners for the FrameLayout widgets
     */
    private fun setupFrameLayoutClickListeners() {
        findViewById<View>(R.id.btnPosture).setOnClickListener {
            navigateToActivity(PostureActivity::class.java)
        }

        findViewById<View>(R.id.btnExercise).setOnClickListener {
            navigateToActivity(WorkoutActivity::class.java)
        }

        findViewById<View>(R.id.btnDietary).setOnClickListener {
            navigateToActivity(DietaryActivity::class.java)
        }

        findViewById<View>(R.id.btnTask).setOnClickListener {
            navigateToActivity(TaskActivity::class.java)
        }

        findViewById<View>(R.id.btnBMI).setOnClickListener {
            navigateToActivity(BmiCalculatorActivity::class.java)
        }
    }

    /**
     * Generic method to navigate to any activity.
     */
    private fun navigateToActivity(activityClass: Class<*>) {
        startActivity(Intent(this, activityClass))
    }

    /**
     * Setup bottom navigation item selection logic
     */
    private fun setupBottomNavigation() {
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)

        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> navigateToActivity(DashboardActivity::class.java)
                R.id.nav_workout -> navigateToActivity(WorkoutActivity::class.java)
                R.id.nav_posture -> navigateToActivity(PostureActivity::class.java)
                R.id.nav_dietary -> navigateToActivity(DietaryActivity::class.java)
                R.id.nav_task -> navigateToActivity(TaskActivity::class.java)
                else -> Log.e("Dashboard", "Unknown menu item selected")
            }
            true
        }
    }

    /**
     * Fetches and displays the logged-in user's information.
     */
    private fun fetchAndDisplayUserInfo() {
        val tvUserName = findViewById<TextView>(R.id.tvUserName)
        val tvPhysicalLevel = findViewById<TextView>(R.id.tvPhysicalLevel)
        val ivStar = findViewById<ImageView>(R.id.ivStar)
        val ivVerified = findViewById<ImageView>(R.id.ivVerified)
        val ivMuscles = findViewById<ImageView>(R.id.ivMuscles)

        // Hide all icons initially
        hideIcons(ivStar, ivVerified, ivMuscles)

        val currentUser = auth.currentUser

        if (currentUser != null) {
            val uid = currentUser.uid

            // Fetch user data from Firestore using UID
            firestore.collection("userinfo")
                .document(uid)
                .get()
                .addOnSuccessListener { documentSnapshot ->
                    if (documentSnapshot.exists()) {
                        // Get user data from Firestore
                        val firstName = documentSnapshot.getString("personalInfo.firstName") ?: "N/A"
                        val lastName = documentSnapshot.getString("personalInfo.lastName") ?: "N/A"
                        val physicalLevel = documentSnapshot.getString("physicalInfo.level") ?: "Beginner"

                        // Set the user name and physical level
                        tvUserName.text = "$firstName $lastName"
                        tvPhysicalLevel.text = "Level: $physicalLevel"
                    } else {
                        // Handle the case where the document doesn't exist
                        tvUserName.text = "User data not found"
                        tvPhysicalLevel.text = "Level: N/A"
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("Dashboard", "Error fetching user data: ${exception.message}")
                    tvUserName.text = "Error"
                    tvPhysicalLevel.text = "Unable to fetch data"
                }
        } else {
            Log.e("Dashboard", "No authenticated user found")
            // Handle case when there's no authenticated user
            tvUserName.text = "Guest User"
            tvPhysicalLevel.text = "Level: N/A"
        }
    }

    /**
     * Hides all physical level icons.
     */
    private fun hideIcons(vararg icons: ImageView) {
        icons.forEach { it.visibility = View.GONE }
    }

    /**
     * Show the logout confirmation dialog.
     */
    private fun showLogoutConfirmationDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Logout")
        builder.setMessage("Do you want to logout?")
        builder.setCancelable(false)

        builder.setPositiveButton("Yes") { dialog, _ ->
            logoutUser()
            dialog.dismiss()
        }

        builder.setNegativeButton("No") { dialog, _ ->
            dialog.dismiss()
        }

        builder.show()
    }

    /**
     * Logs the user out and clears their session.
     */
    private fun logoutUser() {
        // Clear Firebase authentication session
        auth.signOut()

        // Clear cookies (if any, for example via a shared preference)
        // You can clear cookies by clearing the shared preferences or app-specific cache if used

        Toast.makeText(this, "Successfully logged out", Toast.LENGTH_SHORT).show()

        // Navigate back to the login screen
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
