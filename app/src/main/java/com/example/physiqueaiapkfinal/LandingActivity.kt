package com.example.physiqueaiapkfinal

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.app.ActivityOptions
import androidx.appcompat.app.AppCompatActivity
import com.example.physiqueaiapkfinal.databinding.ActivityLandingBinding
import com.example.physiqueaiapkfinal.LoginActivity
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
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
            return
        }

        // Handle Sign In button click
        binding.btnSignIn.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        // Handle Sign Up button click
        binding.txtSignUp.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
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
}
