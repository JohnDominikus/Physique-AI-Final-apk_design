package com.example.physiqueaiapkfinal.utils

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.physiqueaiapkfinal.R
import com.example.physiqueaiapkfinal.utils.DashboardActivity
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.TimeUnit

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var sharedPreferences: SharedPreferences

    companion object {
        private const val COOKIE_EXPIRATION_DAYS = 30
        private const val USER_ID_KEY = "USER_ID"
        private const val TIMESTAMP_KEY = "LOGIN_TIMESTAMP"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("USER_DATA", MODE_PRIVATE)

        // 🔥 Check if user is already logged in and if cookies are valid
        if (isUserLoggedIn()) {
            navigateToDashboard()
            return
        }

        // UI elements
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        // Handle Login Button Click
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim().lowercase()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter both email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Firebase authentication
            loginUser(email, password)
        }
    }

    // ✅ Function to check if the user is logged in and if the cookie is still valid
    private fun isUserLoggedIn(): Boolean {
        val storedUserId = sharedPreferences.getString(USER_ID_KEY, null)
        val lastLoginTimestamp = sharedPreferences.getLong(TIMESTAMP_KEY, 0)

        // 🚫 No stored user ID → force login
        if (storedUserId == null || lastLoginTimestamp == 0L) return false

        // 🔥 Check if 30 days have passed
        val currentTime = System.currentTimeMillis()
        val daysSinceLastLogin = TimeUnit.MILLISECONDS.toDays(currentTime - lastLoginTimestamp)

        return daysSinceLastLogin < COOKIE_EXPIRATION_DAYS
    }

    // ✅ Firebase Authentication with Email and Password
    private fun loginUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        saveUserSession(user.uid)
                        Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                        navigateToDashboard()
                    }
                } else {
                    Toast.makeText(
                        this,
                        "Authentication failed: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    // ✅ Save user ID and timestamp in SharedPreferences (cookies)
    private fun saveUserSession(userId: String) {
        val editor = sharedPreferences.edit()
        editor.putString(USER_ID_KEY, userId)
        editor.putLong(TIMESTAMP_KEY, System.currentTimeMillis())  // Save current time
        editor.apply()
    }

    // ✅ Navigate to DashboardActivity
    private fun navigateToDashboard() {
        val user = auth.currentUser
        val userId = user?.uid ?: "Unknown ID"
        val userEmail = user?.email ?: "Unknown Email"

        val intent = Intent(this, DashboardActivity::class.java).apply {
            putExtra("USER_ID", userId)
            putExtra("USER_EMAIL", userEmail)
        }
        startActivity(intent)
        finish()
    }
}
