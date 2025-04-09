package com.example.physiqueaiapkfinal

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Fetch and display user information
        fetchUserInfo()
    }

    private fun fetchUserInfo() {
        val tvName = findViewById<TextView>(R.id.tvName)
        val tvEmail = findViewById<TextView>(R.id.tvEmail)
        val tvBirthdate = findViewById<TextView>(R.id.tvBirthdate)
        val tvPhone = findViewById<TextView>(R.id.tvPhone)
        val tvBMIStatus = findViewById<TextView>(R.id.tvBMIStatus)
        val tvBMI = findViewById<TextView>(R.id.tvBMI)

        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        firestore.collection("userinfo").document(uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Fetch personal information
                    val personalInfo = document.get("personalInfo") as? Map<*, *>
                    val firstName = personalInfo?.get("firstName")?.toString() ?: "N/A"
                    val lastName = personalInfo?.get("lastName")?.toString() ?: "N/A"
                    val email = personalInfo?.get("email")?.toString() ?: "N/A"
                    val birthdate = personalInfo?.get("birthdate")?.toString() ?: "N/A"
                    val phone = personalInfo?.get("phone")?.toString() ?: "N/A"
                    tvName.text = "Name: $firstName $lastName"
                    tvEmail.text = "Email: $email"
                    tvBirthdate.text = "Birthdate: $birthdate"
                    tvPhone.text = "Phone: $phone"

                    // Fetch BMI information
                    val bmiInfo = document.get("bmiInfo") as? Map<*, *>
                    val bmiStatus = bmiInfo?.get("status")?.toString() ?: "N/A"
                    val bmiValue = bmiInfo?.get("bmi")?.toString() ?: "N/A"
                    tvBMIStatus.text = "BMI Status: ${bmiStatus.replaceFirstChar { it.uppercase() }}"
                    tvBMI.text = "BMI: $bmiValue"

                } else {
                    Toast.makeText(this, "User info not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
