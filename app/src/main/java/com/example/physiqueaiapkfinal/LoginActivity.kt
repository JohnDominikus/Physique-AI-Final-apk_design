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
import com.google.firebase.firestore.FirebaseFirestore
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
        Log.d(TAG, "LoginActivity onCreate started")
        
        try {
            // Set content view first
            setContentView(R.layout.activity_login)
            Log.d(TAG, "Layout set successfully")

            // Show success toast
            Toast.makeText(this, "Login screen loaded successfully!", Toast.LENGTH_SHORT).show()
            
            // Initialize Firebase Auth with error handling
            try {
                auth = FirebaseAuth.getInstance()
                Log.d(TAG, "Firebase Auth initialized")
            } catch (e: Exception) {
                Log.e(TAG, "Firebase Auth failed, using fallback", e)
                // Continue without Firebase for now
            }
            
            // Initialize SharedPreferences with error handling
            try {
                sharedPreferences = getSharedPreferences("USER_DATA", MODE_PRIVATE)
                Log.d(TAG, "SharedPreferences initialized")
            } catch (e: Exception) {
                Log.e(TAG, "SharedPreferences failed", e)
                // Continue without SharedPreferences for now
            }
            
            // Initialize UI elements with null safety
            initializeUIElementsSafely()
            
            // Setup click listeners with error handling
            setupClickListenersSafely()
            
            Log.d(TAG, "LoginActivity onCreate completed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Critical error in LoginActivity onCreate", e)
            Toast.makeText(this, "Error loading login screen", Toast.LENGTH_LONG).show()
            
            // Emergency fallback - go back to landing
            try {
                finish()
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to finish activity", e2)
            }
        }
    }

    private fun initializeUIElementsSafely() {
        try {
            Log.d(TAG, "Initializing UI elements")
            
            etEmail = findViewById(R.id.etEmail)
            etPassword = findViewById(R.id.etPassword)
            btnLogin = findViewById(R.id.btnLogin)
            btnBack = findViewById(R.id.btnBack)
            cbRememberMe = findViewById(R.id.cbRememberMe)
            tvForgotPassword = findViewById(R.id.tvForgotPassword)
            tvRegister = findViewById(R.id.tvRegister)
            
            Log.d(TAG, "UI elements found successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error finding UI elements", e)
            // Continue anyway - some elements might still work
        }
    }

    private fun setupClickListenersSafely() {
        try {
            Log.d(TAG, "Setting up click listeners")
            
            // Back button - always works
            btnBack?.setOnClickListener {
                Log.d(TAG, "Back button clicked")
                try {
                    finish()
                } catch (e: Exception) {
                    Log.e(TAG, "Error finishing activity", e)
                }
            }

            // Login button
            btnLogin?.setOnClickListener {
                Log.d(TAG, "Login button clicked")
                Toast.makeText(this, "Login button works!", Toast.LENGTH_SHORT).show()
                handleLoginClick()
            }

            // Register link
            tvRegister?.setOnClickListener {
                Log.d(TAG, "Register link clicked")
                try {
                    val intent = Intent(this, RegisterActivity::class.java)
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start RegisterActivity", e)
                    Toast.makeText(this, "Error opening registration", Toast.LENGTH_SHORT).show()
                }
            }

            // Forgot password
            tvForgotPassword?.setOnClickListener {
                Log.d(TAG, "Forgot password clicked")
                try {
                    val intent = Intent(this, ForgotPasswordActivity::class.java)
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start ForgotPasswordActivity", e)
                    Toast.makeText(this, "Error opening forgot password", Toast.LENGTH_SHORT).show()
                }
            }

            // Remember me checkbox
            cbRememberMe?.setOnCheckedChangeListener { _, isChecked ->
                Log.d(TAG, "Remember me: $isChecked")
                try {
                    if (::sharedPreferences.isInitialized) {
                        sharedPreferences.edit()
                            .putBoolean("REMEMBER_ME", isChecked)
                            .apply()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving remember me", e)
                }
            }
            
            Log.d(TAG, "Click listeners setup completed")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up click listeners", e)
        }
    }

    private fun handleLoginClick() {
        try {
            Log.d(TAG, "Handling login click")
            
            val email = etEmail?.text?.toString()?.trim() ?: ""
            val password = etPassword?.text?.toString()?.trim() ?: ""

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please enter a valid email", Toast.LENGTH_SHORT).show()
                return
            }

            // Disable login button
            btnLogin?.isEnabled = false
            Toast.makeText(this, "Signing in...", Toast.LENGTH_SHORT).show()

            // Perform login with Firebase if available
            if (::auth.isInitialized) {
                performFirebaseLogin(email, password)
            } else {
                Toast.makeText(this, "Authentication not available", Toast.LENGTH_SHORT).show()
                btnLogin?.isEnabled = true
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in handleLoginClick", e)
            Toast.makeText(this, "Login error: ${e.message}", Toast.LENGTH_SHORT).show()
            btnLogin?.isEnabled = true
        }
    }

    private fun performFirebaseLogin(email: String, password: String) {
        try {
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    btnLogin?.isEnabled = true
                    
                    if (task.isSuccessful) {
                        Log.d(TAG, "Login successful")
                        Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                        
                        val user = auth.currentUser
                        if (user != null) {
                            saveUserSession(user.uid, email)
                            checkProfileCompletion(user.uid, email)
                        }
                    } else {
                        Log.e(TAG, "Login failed: ${task.exception?.message}")
                        Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { exception ->
                    btnLogin?.isEnabled = true
                    Log.e(TAG, "Login exception", exception)
                    Toast.makeText(this, "Login error: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error in performFirebaseLogin", e)
            Toast.makeText(this, "Authentication error", Toast.LENGTH_SHORT).show()
            btnLogin?.isEnabled = true
        }
    }

    private fun saveUserSession(userId: String, userEmail: String) {
        try {
            if (::sharedPreferences.isInitialized) {
                sharedPreferences.edit()
                    .putString(USER_ID_KEY, userId)
                    .putString(USER_EMAIL_KEY, userEmail)
                    .putLong(TIMESTAMP_KEY, System.currentTimeMillis())
                    .apply()
                Log.d(TAG, "User session saved")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving user session", e)
        }
    }

    private fun checkProfileCompletion(userId: String, email: String?) {
        val db = FirebaseFirestore.getInstance()
        db.collection("userinfo").document(userId).get()
            .addOnSuccessListener { document ->
                val physicalInfo = document.get("physicalInfo") as? Map<*, *>
                val medicalInfo = document.get("medicalInfo") as? Map<*, *>

                when {
                    physicalInfo == null || physicalInfo.isEmpty() -> {
                        Log.d(TAG, "Physical info missing – redirecting user to PhysicalActivity")
                        val intent = Intent(this, PhysicalActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        finish()
                    }
                    medicalInfo == null || medicalInfo.isEmpty() -> {
                        Log.d(TAG, "Medical info missing – redirecting user to MedicalActivity")
                        val intent = Intent(this, MedicalActivity::class.java).apply {
                            putExtra("userId", userId)
                            email?.let { putExtra("email", it) }
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        finish()
                    }
                    else -> {
                        Log.d(TAG, "All profile info complete – navigating to Dashboard")
                        navigateToDashboard()
                    }
                }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Failed to fetch profile info: ${error.message}")
                navigateToDashboard()
            }
    }

    private fun navigateToDashboard() {
        try {
            Log.d(TAG, "Navigating to dashboard")
            val intent = Intent(this, DashboardActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        } catch (e: Exception) {
            Log.e(TAG, "Error navigating to dashboard", e)
            Toast.makeText(this, "Navigation error", Toast.LENGTH_SHORT).show()
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

    override fun onStart() {
        super.onStart()
        // If user is already authenticated, skip login screen
        try {
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                Log.d(TAG, "User already authenticated. Checking profile completion.")
                val savedEmail = sharedPreferences.getString(USER_EMAIL_KEY, "") ?: ""
                checkProfileCompletion(currentUser.uid, savedEmail)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking existing auth in onStart", e)
        }
    }
}
