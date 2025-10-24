package com.example.physiqueaiapkfinal

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.physiqueaiapkfinal.databinding.ActivityLandingBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LandingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLandingBinding
    private lateinit var auth: FirebaseAuth
    private val TAG = "LandingActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Activity starting.")

        try {
            // ✅ View Binding
            binding = ActivityLandingBinding.inflate(layoutInflater)
            setContentView(binding.root)

            // ✅ Firebase Auth
            auth = FirebaseAuth.getInstance()
            Log.d(TAG, "Firebase Auth initialized.")

            // ✅ Login Button (btnGetStarted - now Log In)
            binding.btnGetStarted.setOnClickListener {
                Log.d(TAG, "Login button clicked!")
                Toast.makeText(this, "Opening Login Screen...", Toast.LENGTH_SHORT).show()
                navigateToLoginScreen()
            }

            // ✅ Register Button (tvSignIn - now Register)
            binding.tvSignIn.setOnClickListener {
                Log.d(TAG, "Register button clicked!")
                Toast.makeText(this, "Opening Registration Screen...", Toast.LENGTH_SHORT).show()
                navigateToRegisterScreen()
            }

            Log.d(TAG, "Landing screen setup complete.")
        } catch (e: Exception) {
            Log.e(TAG, "FATAL: Exception in onCreate", e)
            Toast.makeText(this, "Error loading landing page: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ✅ Navigate to LoginActivity
    private fun navigateToLoginScreen() {
        try {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "navigateToLoginScreen: ERROR", e)
            Toast.makeText(this, "Navigation Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ✅ Navigate to RegisterActivity
    private fun navigateToRegisterScreen() {
        try {
            val intent = Intent(this, RegisterActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "navigateToRegisterScreen: ERROR", e)
            Toast.makeText(this, "Register Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ✅ Check if user is logged in (Firebase + SharedPrefs optional)
    private fun isUserLoggedIn(): Boolean {
        return try {
            val currentUser = auth.currentUser
            currentUser != null
        } catch (e: Exception) {
            Log.e(TAG, "isUserLoggedIn: Exception", e)
            false
        }
    }

    // ✅ Auto-login logic
    override fun onStart() {
        super.onStart()
        try {
            val currentUser = auth.currentUser
            if (currentUser != null) {
                Log.d(TAG, "User already logged in - checking Firestore profile completion")
                checkUserProfile(currentUser.uid)
            } else {
                Log.d(TAG, "User not logged in - staying on landing screen")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in auto-login check", e)
        }
    }

    // ✅ Check Firestore user info and route accordingly
    private fun checkUserProfile(userId: String) {
        val db = FirebaseFirestore.getInstance()
        db.collection("userinfo").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val physicalInfo = document.get("physicalInfo") as? Map<*, *>
                    val medicalInfo = document.get("medicalInfo") as? Map<*, *>

                    when {
                        physicalInfo == null || physicalInfo.isEmpty() -> {
                            Log.d(TAG, "Physical info missing – redirecting to PhysicalActivity")
                            startActivity(Intent(this, PhysicalActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            })
                            finish()
                        }
                        medicalInfo == null || medicalInfo.isEmpty() -> {
                            Log.d(TAG, "Medical info missing – redirecting to MedicalActivity")
                            startActivity(Intent(this, MedicalActivity::class.java).apply {
                                putExtra("userId", userId)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            })
                            finish()
                        }
                        else -> {
                            Log.d(TAG, "All info complete – going to Dashboard")
                            goToDashboard()
                        }
                    }
                } else {
                    Log.d(TAG, "User document not found – redirecting to Dashboard by default")
                    goToDashboard()
                }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Failed to fetch user profile: ${error.message}")
                goToDashboard()
            }
    }

    // ✅ Go to DashboardActivity
    private fun goToDashboard() {
        Log.d(TAG, "Navigating to DashboardActivity")
        val intent = Intent(this, DashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}