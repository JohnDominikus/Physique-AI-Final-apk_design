package com.example.physiqueaiapkfinal

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var sqliteHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize Firebase and SQLite
        auth = FirebaseAuth.getInstance()
        sqliteHelper = DatabaseHelper(this)

        // Initialize UI elements
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)

        // Handle Login Button Click
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            // Validate input fields
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter both email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Attempt to log in
            loginUser(email, password)
        }
    }

    // Login the user with Firebase Authentication
    private fun loginUser(email: String, password: String) {
        // 🔥 Get the user ID from SQLite
        val firebaseId = sqliteHelper.getUserId(email)

        if (firebaseId != null) {
            // Firebase authentication
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()

                        // Pass the Firebase ID to the Dashboard
                        val intent = Intent(this, DashboardActivity::class.java)
                        intent.putExtra("USER_ID", firebaseId)
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(
                            this,
                            "Authentication failed: ${task.exception?.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        } else {
            Toast.makeText(this, "User not found in local database", Toast.LENGTH_SHORT).show()
        }
    }
}