package com.example.physiqueaiapkfinal

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.app.ActivityOptions
import androidx.appcompat.app.AppCompatActivity
import com.example.physiqueaiapkfinal.databinding.ActivityLandingBinding
import com.example.physiqueaiapkfinal.utils.LoginActivity
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

        // 🔒 Auto-login if user is already authenticated
        if (isUserLoggedIn()) {
            Log.d("LandingActivity", "User already logged in: ${auth.currentUser?.email}")
            navigateThroughSplash(DashboardActivity::class.java)
            return
        }

        // Handle Sign In button click
        binding.btnSignIn.setOnClickListener {
            navigateThroughSplash(LoginActivity::class.java)
        }

        // Handle Sign Up button click
        binding.txtSignUp.setOnClickListener {
            navigateThroughSplash(RegisterActivity::class.java)
        }
    }

    /**
     * Checks if a user is logged in and their UID matches stored data
     */
    private fun isUserLoggedIn(): Boolean {
        val sharedPreferences: SharedPreferences = getSharedPreferences("USER_DATA", MODE_PRIVATE)
        val storedUserId = sharedPreferences.getString("USER_ID", null)
        return auth.currentUser != null && auth.currentUser?.uid == storedUserId
    }

    /**
     * Navigates to the splash screen and passes the target activity to load after splash
     */
    private fun navigateThroughSplash(targetActivity: Class<*>) {
        // Create intent for SplashActivity
        val splashIntent = Intent(this, SplashActivity::class.java)

        // Pass the target activity name as extra
        splashIntent.putExtra("TARGET_ACTIVITY", targetActivity.name)

        // Add fade-in and fade-out animations
        val options = ActivityOptions.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out)

        // Start SplashActivity with animation
        startActivity(splashIntent, options.toBundle())

        // Optionally, remove LandingActivity from the backstack
        finish()
    }
}
