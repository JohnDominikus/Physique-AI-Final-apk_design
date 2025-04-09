package com.example.physiqueaiapkfinal.utils

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.physiqueaiapkfinal.ForgotPasswordNewPasswordActivity
import com.example.physiqueaiapkfinal.R
import com.google.firebase.auth.FirebaseAuth

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var progressBar: ProgressBar  // Declare ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start with the email and phone layout
        setContentView(R.layout.activity_forgot_password_email_phone)

        auth = FirebaseAuth.getInstance()

        // Find the ProgressBar by ID
        progressBar = findViewById(R.id.progressBar)

        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPhone = findViewById<EditText>(R.id.etPhone)
        val btnNext = findViewById<Button>(R.id.btnNext)

        btnNext.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val phone = etPhone.text.toString().trim()

            // Validate email and phone
            if (email.isEmpty() || phone.isEmpty()) {
                Toast.makeText(this, "Please enter both email and phone number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!Patterns.PHONE.matcher(phone).matches()) {
                Toast.makeText(this, "Please enter a valid phone number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Show ProgressBar
            progressBar.visibility = ProgressBar.VISIBLE

            // For testing purposes, simulate success and proceed to next screen
            // You can bypass Firebase and just proceed to the next screen
            simulateEmailVerificationAndProceed(email, phone)
        }
    }

    private fun simulateEmailVerificationAndProceed(email: String, phone: String) {
        // Simulate email verification success
        // You can bypass Firebase for testing purposes, just proceed to the next screen

        // Hide ProgressBar when the task completes
        progressBar.visibility = ProgressBar.GONE

        // Here we simply assume the email is valid for testing purposes
        proceedToNextScreen(email, phone)
    }

    private fun proceedToNextScreen(email: String, phone: String) {
        // Proceed to the next screen (password reset)
        val intent = Intent(this, ForgotPasswordNewPasswordActivity::class.java)
        intent.putExtra("EMAIL", email)
        intent.putExtra("PHONE", phone)
        startActivity(intent)
    }
}
