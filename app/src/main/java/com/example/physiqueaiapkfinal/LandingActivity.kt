package com.example.physiqueaiapkfinal

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.physiqueaiapkfinal.databinding.ActivityLandingBinding
import com.google.firebase.auth.FirebaseAuth

class LandingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLandingBinding
    private lateinit var auth: FirebaseAuth
    private val TAG = "LandingActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Activity starting.")
        
        try {
            // Initialize ViewBinding
            Log.d(TAG, "onCreate: Inflating layout.")
            binding = ActivityLandingBinding.inflate(layoutInflater)
            setContentView(binding.root)
            Log.d(TAG, "onCreate: Layout inflated successfully.")

            // Initialize Firebase Authentication
            auth = FirebaseAuth.getInstance()
            Log.d(TAG, "onCreate: Firebase Auth initialized.")

            // Set up NEW Sign In button click listener
            binding.btnNewSignIn.setOnClickListener {
                Log.d(TAG, "NEW Sign In button clicked!")
                Toast.makeText(this, "NEW Sign In Button Clicked!", Toast.LENGTH_SHORT).show()
                navigateToLoginScreen()
            }
            
            // Set up Sign Up button click listener
            binding.txtSignUp.setOnClickListener {
                Log.d(TAG, "Sign Up button clicked!")
                Toast.makeText(this, "Sign Up Clicked!", Toast.LENGTH_SHORT).show()
                navigateToRegisterScreen()
            }

            Log.d(TAG, "onCreate: NEW button setup complete.")
            Toast.makeText(this, "Landing Page Ready - NEW Sign In Button", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Log.e(TAG, "FATAL: Exception in onCreate", e)
            Toast.makeText(this, "Failed to load landing page: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun navigateToLoginScreen() {
        try {
            Log.d(TAG, "navigateToLoginScreen: Starting navigation")
            Toast.makeText(this, "Opening Login Screen...", Toast.LENGTH_SHORT).show()
            
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            
            Log.d(TAG, "navigateToLoginScreen: Starting LoginActivity")
            startActivity(intent)
            Log.d(TAG, "navigateToLoginScreen: Navigation successful")
            
        } catch (e: Exception) {
            Log.e(TAG, "navigateToLoginScreen: ERROR", e)
            Toast.makeText(this, "Navigation Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun navigateToRegisterScreen() {
        try {
            Log.d(TAG, "navigateToRegisterScreen: Starting navigation")
            Toast.makeText(this, "Opening Register Screen...", Toast.LENGTH_SHORT).show()
            
            val intent = Intent(this, RegisterActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            
            startActivity(intent)
            
        } catch (e: Exception) {
            Log.e(TAG, "navigateToRegisterScreen: ERROR", e)
            Toast.makeText(this, "Register Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun isUserLoggedIn(): Boolean {
        try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.d(TAG, "isUserLoggedIn: Firebase user is null.")
                return false
            }

            val sharedPreferences: SharedPreferences = getSharedPreferences("USER_DATA", MODE_PRIVATE)
            val storedUserId = sharedPreferences.getString("USER_ID", null)
            
            if (storedUserId == null) {
                Log.d(TAG, "isUserLoggedIn: Stored user ID is null.")
                return false
            }

            val isLoggedIn = currentUser.uid == storedUserId
            Log.d(TAG, "isUserLoggedIn: Check result - $isLoggedIn")
            return isLoggedIn

        } catch (e: Exception) {
            Log.e(TAG, "isUserLoggedIn: Exception while checking login status", e)
            return false
        }
    }

    override fun onStart() {
        super.onStart()
        try {
            if (isUserLoggedIn()) {
                Log.d(TAG, "User already logged in - navigating directly to Dashboard")
                val intent = Intent(this, DashboardActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            } else {
                Log.d(TAG, "User not logged in - staying on landing screen")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStart auto-login check", e)
        }
    }
}
