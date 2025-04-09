package com.example.physiqueaiapkfinal.utils

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.physiqueaiapkfinal.R
import com.example.physiqueaiapkfinal.DashboardActivity
import com.example.physiqueaiapkfinal.LandingActivity
import com.google.firebase.auth.FirebaseAuth
import android.util.Patterns
import android.widget.CheckBox
import android.widget.TextView
import java.util.concurrent.TimeUnit

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var sharedPreferences: SharedPreferences
    private var isPasswordVisible = false // Track password visibility state

    companion object {
        private const val COOKIE_EXPIRATION_DAYS = 30
        private const val USER_ID_KEY = "USER_ID"
        private const val TIMESTAMP_KEY = "LOGIN_TIMESTAMP"
        private const val USER_EMAIL_KEY = "USER_EMAIL"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)


        findViewById<TextView>(R.id.tvForgotPassword).setOnClickListener {
            // Navigate to ForgotPasswordActivity
            val intent = Intent(this, ForgotPasswordActivity::class.java)
            startActivity(intent)
        }


        // Initialize Firebase Auth and SharedPreferences
        auth = FirebaseAuth.getInstance()
        sharedPreferences = getSharedPreferences("USER_DATA", MODE_PRIVATE)

        // Check if user is already logged in with valid cookies
        if (isUserLoggedIn()) {
            navigateToDashboard()
            return
        }

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val passwordToggle = findViewById<ImageView>(R.id.passwordToggle)

        // Password visibility toggle
        passwordToggle.setOnClickListener {
            isPasswordVisible = !isPasswordVisible // Toggle the state

            // Set the input type based on the state
            etPassword.inputType = if (isPasswordVisible) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }

            // Move cursor to the end of the text
            etPassword.setSelection(etPassword.text.length)

            // Update the icon
            passwordToggle.setImageResource(
                if (isPasswordVisible) R.drawable.ic_eye else R.drawable.ic_eye_open
            )
        }

        // Handle login button click
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim().lowercase()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter both email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            loginUser(email, password)
        }

        // Back button to navigate to LandingActivity
        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            startActivity(Intent(this, LandingActivity::class.java))
            finish()
        }

        // Additional setup for remember me functionality (future feature)
        val rememberMeCheckbox = findViewById<CheckBox>(R.id.cbRememberMe)
        rememberMeCheckbox.setOnCheckedChangeListener { _, isChecked ->
            // Optionally save the checkbox state for future reference
            val editor = sharedPreferences.edit()
            editor.putBoolean("REMEMBER_ME", isChecked)
            editor.apply()
        }
    }

    // Rest of your existing code remains the same...
    private fun isUserLoggedIn(): Boolean {
        val storedUserId = sharedPreferences.getString(USER_ID_KEY, null)
        val lastLoginTimestamp = sharedPreferences.getLong(TIMESTAMP_KEY, 0)

        if (storedUserId == null || lastLoginTimestamp == 0L) return false

        val currentTime = System.currentTimeMillis()
        val daysSinceLastLogin = TimeUnit.MILLISECONDS.toDays(currentTime - lastLoginTimestamp)

        return daysSinceLastLogin < COOKIE_EXPIRATION_DAYS
    }

    private fun loginUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    user?.let {
                        saveUserSession(it.uid, email)
                        Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                        navigateToDashboard()
                    }
                } else {
                    Toast.makeText(this, "Authentication failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun saveUserSession(userId: String, userEmail: String) {
        val editor = sharedPreferences.edit()
        editor.putString(USER_ID_KEY, userId)
        editor.putString(USER_EMAIL_KEY, userEmail)
        editor.putLong(TIMESTAMP_KEY, System.currentTimeMillis())
        editor.apply()
    }

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
// Handle Forgot Password click

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }


}