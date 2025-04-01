package com.example.physiqueaiapkfinal

import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.example.physiqueaiapkfinal.databinding.ActivityLandingBinding
import com.example.physiqueaiapkfinal.DashboardActivity
import com.example.physiqueaiapkfinal.utils.LoginActivity
import com.google.firebase.auth.FirebaseAuth

class LandingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLandingBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Use ViewBinding for cleaner code and better performance
        binding = ActivityLandingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase Authentication
        auth = FirebaseAuth.getInstance()

        // Load SharedPreferences (cookies)
        val sharedPreferences: SharedPreferences = getSharedPreferences("USER_DATA", MODE_PRIVATE)
        val storedUserId = sharedPreferences.getString("USER_ID", null)

        // 🔥 Check if the user is already authenticated
        if (storedUserId != null && auth.currentUser != null && auth.currentUser?.uid == storedUserId) {
            // ✅ User is authenticated → go to Dashboard
            Log.d("LandingActivity", "User found in cookies: ${auth.currentUser?.email}")
            navigateTo(DashboardActivity::class.java)
        } else {
            // 🔥 User not authenticated → Go to Login
            Log.d("LandingActivity", "No valid user found → Redirecting to Login")
            navigateTo(LoginActivity::class.java)
        }
    }

    // Helper function for navigation
    private fun navigateTo(activity: Class<*>) {
        val intent = Intent(this, activity)
        startActivity(intent)
        finish()
    }
}
