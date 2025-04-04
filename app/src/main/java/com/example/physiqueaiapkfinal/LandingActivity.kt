package com.example.physiqueaiapkfinal

import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.example.physiqueaiapkfinal.databinding.ActivityLandingBinding
import com.example.physiqueaiapkfinal.utils.LoginActivity
import com.example.physiqueaiapkfinal.RegisterActivity
import com.google.firebase.auth.FirebaseAuth

class LandingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLandingBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize ViewBinding
        binding = ActivityLandingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase Authentication
        auth = FirebaseAuth.getInstance()

        // Load SharedPreferences (cookies)
        val sharedPreferences: SharedPreferences = getSharedPreferences("USER_DATA", MODE_PRIVATE)
        val storedUserId = sharedPreferences.getString("USER_ID", null)

        // 🔥 Check if the user is already authenticated
        if (storedUserId != null && auth.currentUser != null && auth.currentUser?.uid == storedUserId) {
            Log.d("LandingActivity", "User found in cookies: ${auth.currentUser?.email}")
            navigateTo(DashboardActivity::class.java)
        }

        // Handle Sign In button click → Redirect to LoginActivity
        binding.btnSignIn.setOnClickListener {
            navigateTo(LoginActivity::class.java)
        }

        // Handle Sign Up button click → Redirect to RegisterActivity
        binding.txtSignUp.setOnClickListener {
            navigateTo(RegisterActivity::class.java)
        }
    }

    // Helper function for navigation
    private fun navigateTo(activity: Class<*>) {
        val intent = Intent(this, activity)
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out) // Smooth transition
        finish()
    }
}
