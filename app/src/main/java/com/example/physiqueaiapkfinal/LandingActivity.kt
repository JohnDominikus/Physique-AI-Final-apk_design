package com.example.physiqueaiapkfinal

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.physiqueaiapkfinal.databinding.ActivityLandingBinding
import com.example.physiqueaiapkfinal.utils.LoginActivity
import com.google.firebase.auth.FirebaseAuth
import android.app.ActivityOptions

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

        // 🔒 Check if the user is already authenticated
        if (isUserLoggedIn()) {
            Log.d("LandingActivity", "User already logged in: ${auth.currentUser?.email}")
            navigateTo(DashboardActivity::class.java)
            return
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

    /**
     * Checks if a user is logged in and has a stored UID match
     */
    private fun isUserLoggedIn(): Boolean {
        val sharedPreferences: SharedPreferences = getSharedPreferences("USER_DATA", MODE_PRIVATE)
        val storedUserId = sharedPreferences.getString("USER_ID", null)
        return auth.currentUser != null && auth.currentUser?.uid == storedUserId
    }

    /**
     * Navigates to the specified activity with a fade animation
     */
    private fun navigateTo(activity: Class<*>) {
        val intent = Intent(this, activity)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val options = ActivityOptions.makeCustomAnimation(this, android.R.anim.fade_in, android.R.anim.fade_out)
            startActivity(intent, options.toBundle())
        } else {
            startActivity(intent)
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        finish() // Ensures the landing activity is removed from backstack
    }
}
