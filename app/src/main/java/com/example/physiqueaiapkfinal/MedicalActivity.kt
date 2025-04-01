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
import com.google.firebase.firestore.SetOptions

class MedicalActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var sqliteHelper: DatabaseHelper
    private lateinit var userId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_medical)

        // ✅ Initialize Firebase and SQLite
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        sqliteHelper = DatabaseHelper(this)

        // ✅ Retrieve the user ID from the previous activity
        userId = intent.getStringExtra("userId") ?: ""

        if (userId.isEmpty()) {
            Toast.makeText(this, "User ID not found!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // ✅ Initialize UI elements
        val editAllergies = findViewById<EditText>(R.id.editAllergies)
        val editMedicalHistory = findViewById<EditText>(R.id.editMedicalHistory)
        val editFractures = findViewById<EditText>(R.id.editFractures)
        val editOtherConditions = findViewById<EditText>(R.id.editOtherConditions)
        val btnBack = findViewById<Button>(R.id.btnBack)
        val btnSubmit = findViewById<Button>(R.id.btnSubmit)

        // ✅ Navigate back to the previous screen
        btnBack.setOnClickListener { finish() }

        // ✅ Submit medical information
        btnSubmit.setOnClickListener {
            val allergies = editAllergies.text.toString().trim()
            val medicalHistory = editMedicalHistory.text.toString().trim()
            val fractures = editFractures.text.toString().trim()
            val otherConditions = editOtherConditions.text.toString().trim()

            // Save data
            saveMedicalInformation(allergies, medicalHistory, fractures, otherConditions)
        }
    }

    // ✅ Save medical information to Firestore and SQLite
    private fun saveMedicalInformation(
        allergies: String,
        medicalHistory: String,
        fractures: String,
        otherConditions: String
    ) {
        // 🔥 Check if all fields are empty
        if (allergies.isEmpty() && medicalHistory.isEmpty() && fractures.isEmpty() && otherConditions.isEmpty()) {
            Toast.makeText(this, "Please fill at least one field", Toast.LENGTH_SHORT).show()
            return
        }

        // ✅ Prepare data for Firestore
        val medicalData = mapOf(
            "allergies" to allergies,
            "medicalHistory" to medicalHistory,
            "fractures" to fractures,
            "otherConditions" to otherConditions
        )

        val firestoreData = mapOf(
            "medicalInfo" to medicalData
        )

        // ✅ Save to Firestore
        firestore.collection("userinfo").document(userId)
            .set(firestoreData, SetOptions.merge())
            .addOnSuccessListener {
                Log.d("Firestore", "Medical info saved successfully in Firestore")

                // ✅ Save to SQLite
                val rowId = sqliteHelper.insertOrUpdateUser(
                    firebaseId = userId,
                    firstName = "",
                    lastName = "",
                    birthdate = "",
                    phone = "",
                    email = "",
                    password = "",
                    bodyLevel = null,
                    bodyClassification = null,
                    exerciseRoutine = null,
                    otherInfo = null,
                    allergies = allergies,
                    medicalHistory = medicalHistory,
                    fractures = fractures,
                    otherConditions = otherConditions
                )

                if (rowId != -1L) {
                    Toast.makeText(this, "Medical info saved successfully!", Toast.LENGTH_SHORT).show()

                    // 🚀 Navigate to `DashboardActivity`
                    val intent = Intent(this, DashboardActivity::class.java)
                    startActivity(intent)
                    finish()

                } else {
                    Toast.makeText(this, "Failed to save medical info in SQLite", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error saving medical info: ${e.message}")
                Toast.makeText(this, "Failed to save medical info to Firestore", Toast.LENGTH_SHORT).show()
            }
    }
}
