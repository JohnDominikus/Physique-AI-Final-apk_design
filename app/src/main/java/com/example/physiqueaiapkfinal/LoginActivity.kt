package com.example.physiqueaiapkfinal

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.util.Patterns
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.TimeUnit

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var sharedPreferences: SharedPreferences
    private var isPasswordVisible = false

    // UI Components - made nullable for safety
    private var etEmail: EditText? = null
    private var etPassword: EditText? = null
    private var btnLogin: Button? = null
    private var btnBack: ImageButton? = null
    private var cbRememberMe: CheckBox? = null
    private var tvForgotPassword: TextView? = null
    private var tvRegister: TextView? = null

    companion object {
        private const val TAG = "LoginActivity"
        private const val COOKIE_EXPIRATION_DAYS = 30
        private const val USER_ID_KEY = "USER_ID"
        private const val TIMESTAMP_KEY = "LOGIN_TIMESTAMP"
        private const val USER_EMAIL_KEY = "USER_EMAIL"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            setContentView(R.layout.activity_login)
            
            // Initialize Firebase Auth first
            initializeFirebase()
            
            // Initialize SharedPreferences
            initializeSharedPreferences()
            
            // If user is already logged in, navigate to the Dashboard
            if (isUserLoggedIn()) {
                navigateToDashboard()
                return
            }
            
            // Initialize UI elements safely
            initializeUIElements()
            
            // Setup click listeners
            setupClickListeners()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            // Show error to user and potentially close activity
            showError("Failed to initialize login screen: ${e.message}")
        }
    }

    private fun initializeFirebase() {
        try {
            auth = FirebaseAuth.getInstance()
            Log.d(TAG, "Firebase Auth initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase Auth", e)
            showError("Failed to initialize authentication system")
            throw e
        }
    }

    private fun initializeSharedPreferences() {
        try {
            sharedPreferences = getSharedPreferences("USER_DATA", MODE_PRIVATE)
            Log.d(TAG, "SharedPreferences initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SharedPreferences", e)
            showError("Failed to initialize user preferences")
            throw e
        }
    }

    private fun initializeUIElements() {
        try {
            // Initialize UI elements with null safety
            etEmail = findViewById<EditText>(R.id.etEmail)
            etPassword = findViewById<EditText>(R.id.etPassword)
            btnLogin = findViewById<Button>(R.id.btnLogin)
            btnBack = findViewById<ImageButton>(R.id.btnBack)
            cbRememberMe = findViewById<CheckBox>(R.id.cbRememberMe)
            tvForgotPassword = findViewById<TextView>(R.id.tvForgotPassword)
            tvRegister = findViewById<TextView>(R.id.tvRegister)
            
            // Verify all elements were found
            if (etEmail == null || etPassword == null || btnLogin == null) {
                throw Exception("Critical UI elements not found")
            }
            
            Log.d(TAG, "UI elements initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize UI elements", e)
            showError("Failed to initialize user interface")
            throw e
        }
    }

    private fun setupClickListeners() {
        try {
            // Handle forgot password click
            tvForgotPassword?.setOnClickListener {
                try {
                    val intent = Intent(this, ForgotPasswordActivity::class.java)
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to navigate to forgot password", e)
                    showError("Failed to open forgot password screen")
                }
            }

            // Handle login button click
            btnLogin?.setOnClickListener {
                handleLoginClick()
            }

            // Back button to navigate to the landing activity
            btnBack?.setOnClickListener {
                try {
                    startActivity(Intent(this, LandingActivity::class.java))
                    finish()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to navigate back", e)
                    finish() // Just close this activity if navigation fails
                }
            }

            // Remember me checkbox functionality
            cbRememberMe?.setOnCheckedChangeListener { _, isChecked ->
                try {
                    val editor = sharedPreferences.edit()
                    editor.putBoolean("REMEMBER_ME", isChecked)
                    editor.apply()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save remember me preference", e)
                }
            }

            // Add functionality for "Don't have an account? Register"
            tvRegister?.setOnClickListener {
                try {
                    val intent = Intent(this, RegisterActivity::class.java)
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to navigate to register", e)
                    showError("Failed to open registration screen")
                }
            }
            
            Log.d(TAG, "Click listeners setup successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup click listeners", e)
            showError("Failed to setup user interactions")
        }
    }

    private fun handleLoginClick() {
        try {
            val email = etEmail?.text?.toString()?.trim()?.lowercase() ?: ""
            val password = etPassword?.text?.toString()?.trim() ?: ""

            if (email.isEmpty() || password.isEmpty()) {
                showError("Please enter both email and password")
                return
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                showError("Please enter a valid email address")
                return
            }

            // Disable login button to prevent multiple clicks
            btnLogin?.isEnabled = false

            loginUser(email, password)
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleLoginClick", e)
            showError("Failed to process login: ${e.message}")
            btnLogin?.isEnabled = true
        }
    }

    // Check if the user is logged in
    private fun isUserLoggedIn(): Boolean {
        return try {
            val storedUserId = sharedPreferences.getString(USER_ID_KEY, null)
            val lastLoginTimestamp = sharedPreferences.getLong(TIMESTAMP_KEY, 0)
            if (storedUserId == null || lastLoginTimestamp == 0L) return false
            val currentTime = System.currentTimeMillis()
            val daysSinceLastLogin = TimeUnit.MILLISECONDS.toDays(currentTime - lastLoginTimestamp)
            daysSinceLastLogin < COOKIE_EXPIRATION_DAYS
        } catch (e: Exception) {
            Log.e(TAG, "Error checking login status", e)
            false
        }
    }

    // Handle user login with Firebase
    private fun loginUser(email: String, password: String) {
        try {
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    // Re-enable login button
                    btnLogin?.isEnabled = true
                    
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        user?.let {
                            saveUserSession(it.uid, email)
                            showSuccess("Login successful!")
                            navigateToDashboard()
                        } ?: run {
                            showError("Failed to get user information")
                        }
                    } else {
                        val errorMessage = task.exception?.message ?: "Authentication failed"
                        Log.e(TAG, "Login failed: $errorMessage")
                        showError("Login failed: $errorMessage")
                    }
                }
                .addOnFailureListener { exception ->
                    btnLogin?.isEnabled = true
                    Log.e(TAG, "Login exception", exception)
                    showError("Login failed: ${exception.message}")
                }
        } catch (e: Exception) {
            btnLogin?.isEnabled = true
            Log.e(TAG, "Error in loginUser", e)
            showError("Failed to login: ${e.message}")
        }
    }

    // Save user session to SharedPreferences
    private fun saveUserSession(userId: String, userEmail: String) {
        try {
            val editor = sharedPreferences.edit()
            editor.putString(USER_ID_KEY, userId)
            editor.putString(USER_EMAIL_KEY, userEmail)
            editor.putLong(TIMESTAMP_KEY, System.currentTimeMillis())
            editor.apply()
            Log.d(TAG, "User session saved successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save user session", e)
            // Don't show error to user as login was successful
        }
    }

    // Navigate to the dashboard (or main activity)
    private fun navigateToDashboard() {
        try {
            val intent = Intent(this, SplashActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to navigate to dashboard", e)
            showError("Login successful but failed to open dashboard")
        }
    }

    private fun showError(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
        Log.e(TAG, message)
    }

    private fun showSuccess(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
        Log.d(TAG, message)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up any resources if needed
        etEmail = null
        etPassword = null
        btnLogin = null
        btnBack = null
        cbRememberMe = null
        tvForgotPassword = null
        tvRegister = null
    }
}
