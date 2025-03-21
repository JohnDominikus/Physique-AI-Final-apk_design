package com.example.physiqueaiapkfinal

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MedicalActivity : AppCompatActivity() {

    // Firebase and SQLite instances
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var sqliteHelper: DatabaseHelper

    // UI components
    private lateinit var editAllergies: EditText
    private lateinit var editMedicalHistory: EditText
    private lateinit var editFractures: EditText
    private lateinit var editOtherConditions: EditText
    private lateinit var btnBack: Button
    private lateinit var btnSubmit: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_medical)

        // Initialize Firebase and SQLite
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        sqliteHelper = DatabaseHelper(this)

        // Initialize UI elements
        editAllergies = findViewById(R.id.editAllergies)
        editMedicalHistory = findViewById(R.id.editMedicalHistory)
        editFractures = findViewById(R.id.editFractures)
        editOtherConditions = findViewById(R.id.editOtherConditions)
        btnBack = findViewById(R.id.btnBack)
        btnSubmit = findViewById(R.id.btnSubmit)

        // Navigate back to the previous screen
        btnBack.setOnClickListener {
            finish()
        }

        // Submit medical information
        btnSubmit.setOnClickListener {
            saveMedicalInformation()
        }
    }

    // 🔹 Save medical information to SQLite and Firestore
    private fun saveMedicalInformation() {
        val allergies = editAllergies.text.toString().trim()
        val medicalHistory = editMedicalHistory.text.toString().trim()
        val fractures = editFractures.text.toString().trim()
        val otherConditions = editOtherConditions.text.toString().trim()

        // Check if all fields are empty
        if (allergies.isEmpty() && medicalHistory.isEmpty() && fractures.isEmpty() && otherConditions.isEmpty()) {
            Toast.makeText(this, "Please fill at least one field", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if the user is authenticated
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userId = currentUser.uid

            // 🔹 Save to Firestore
            val medicalData = hashMapOf(
                "allergies" to allergies,
                "medicalHistory" to medicalHistory,
                "fractures" to fractures,
                "otherConditions" to otherConditions
            )

            db.collection("userinfo").document(userId)
                .set(medicalData)
                .addOnSuccessListener {
                    Log.d("Firestore", "Medical data saved successfully")

                    // 🔹 Save to SQLite
                    val savedToSQLite = sqliteHelper.insertMedicalActivity(
                        userId,
                        allergies,
                        medicalHistory,
                        fractures,
                        otherConditions
                    )

                    if (savedToSQLite) {
                        Toast.makeText(this, "Medical info saved successfully!", Toast.LENGTH_SHORT).show()

                        // Navigate to the next screen
                        startActivity(Intent(this, DashboardActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this, "Failed to save in SQLite", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("Firestore", "Error saving medical info: ${e.message}")
                    Toast.makeText(this, "Firestore Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
        }
    }
}
