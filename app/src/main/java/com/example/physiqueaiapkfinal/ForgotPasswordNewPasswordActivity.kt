package com.example.physiqueaiapkfinal

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class ForgotPasswordNewPasswordActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password_new_password)

        auth = FirebaseAuth.getInstance()

        val etNewPassword = findViewById<EditText>(R.id.etNewPassword)
        val etConfirmPassword = findViewById<EditText>(R.id.etConfirmPassword)
        val btnResetPassword = findViewById<Button>(R.id.btnResetPassword)

        val email = intent.getStringExtra("EMAIL") ?: ""

        btnResetPassword.setOnClickListener {
            val newPassword = etNewPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()

            // Validate passwords
            if (!isValidPassword(newPassword, confirmPassword)) return@setOnClickListener

            // Proceed with password reset
            resetPassword(email, newPassword)
        }
    }

    private fun isValidPassword(newPassword: String, confirmPassword: String): Boolean {
        return when {
            newPassword.isEmpty() || confirmPassword.isEmpty() -> {
                showToast("Please enter both new password and confirmation")
                false
            }
            newPassword != confirmPassword -> {
                showToast("Passwords do not match")
                false
            }
            else -> true
        }
    }

    private fun resetPassword(email: String, newPassword: String) {
        try {
            // Use modern Firebase Auth approach
            val auth = FirebaseAuth.getInstance()
            
            // Check if user exists and has password sign-in method
            auth.fetchSignInMethodsForEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val signInMethods = task.result?.signInMethods ?: emptyList()
                        if (signInMethods.contains("password")) {
                            // User exists with password, proceed with reset
                            sendPasswordResetEmail(email, newPassword)
                        } else {
                            showToast("No password account found for this email")
                        }
                    } else {
                        showToast("Error checking email: ${task.exception?.message}")
                    }
                }
        } catch (e: Exception) {
            showToast("Error: ${e.message}")
        }
    }

    private fun sendPasswordResetEmail(email: String, newPassword: String) {
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { resetTask ->
                if (resetTask.isSuccessful) {
                    showToast("Password reset email sent")
                    updatePassword(newPassword)
                } else {
                    showToast("Error: ${resetTask.exception?.message}")
                }
            }
    }

    private fun updatePassword(newPassword: String) {
        val user = auth.currentUser
        user?.updatePassword(newPassword)
            ?.addOnCompleteListener { updateTask ->
                if (updateTask.isSuccessful) {
                    showToast("Password updated successfully")
                    finish() // Go back to the login screen
                } else {
                    showToast("Error updating password: ${updateTask.exception?.message}")
                }
            }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
