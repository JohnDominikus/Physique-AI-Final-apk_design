package com.example.physiqueaiapkfinal

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class PhysicalActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var email: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_physical)

        // Initialize Firebase, Firestore, and SQLite
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        dbHelper = DatabaseHelper(this)

        // Retrieve the email from the previous activity
        email = intent.getStringExtra("email").orEmpty()

        // Initialize UI elements
        val spinnerBodyLevel = findViewById<Spinner>(R.id.spinnerBodyLevel)
        val spinnerBodyClassification = findViewById<Spinner>(R.id.spinnerBodyClassification)
        val spinnerExercise = findViewById<Spinner>(R.id.spinnerExercise)
        val spinnerOthers = findViewById<Spinner>(R.id.spinnerOthers)
        val btnBack = findViewById<Button>(R.id.btnBackToNext)
        val btnNext = findViewById<Button>(R.id.btnNext)

        // Populate Spinners
        setupSpinners()

        // Handle Back Button
        btnBack.setOnClickListener { finish() }

        // Handle Next Button
        btnNext.setOnClickListener {
            val bodyLevel = spinnerBodyLevel.selectedItem.toString()
            val bodyClassification = spinnerBodyClassification.selectedItem.toString()
            val exerciseRoutine = spinnerExercise.selectedItem.toString()
            val otherInfo = spinnerOthers.selectedItem.toString()

            // Validate spinner selections
            if (validateSelection(bodyLevel, bodyClassification, exerciseRoutine)) {
                savePhysicalInfo(bodyLevel, bodyClassification, exerciseRoutine, otherInfo)
            } else {
                Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSpinners() {
        val bodyLevels = arrayOf("Select", "Beginner", "Intermediate", "Advanced")
        val classifications = arrayOf("Select", "Endomorph", "Mesomorph", "Ectomorph")
        val exerciseRoutines = arrayOf("Select", "Strength", "Cardio", "Flexibility", "Mixed")
        val others = arrayOf("Select", "Gym member", "Home workout", "Personal trainer", "Other")

        setupSpinner(R.id.spinnerBodyLevel, bodyLevels)
        setupSpinner(R.id.spinnerBodyClassification, classifications)
        setupSpinner(R.id.spinnerExercise, exerciseRoutines)
        setupSpinner(R.id.spinnerOthers, others)
    }

    private fun setupSpinner(spinnerId: Int, items: Array<String>) {
        val spinner = findViewById<Spinner>(spinnerId)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    private fun validateSelection(
        bodyLevel: String,
        bodyClassification: String,
        exerciseRoutine: String
    ): Boolean {
        return bodyLevel != "Select" && bodyClassification != "Select" && exerciseRoutine != "Select"
    }

    private fun savePhysicalInfo(
        bodyLevel: String,
        bodyClassification: String,
        exerciseRoutine: String,
        otherInfo: String
    ) {
        val userId = auth.currentUser?.uid

        if (userId != null) {
            val physicalInfo = hashMapOf(
                "bodyLevel" to bodyLevel,
                "bodyClassification" to bodyClassification,
                "exerciseRoutine" to exerciseRoutine,
                "otherInfo" to otherInfo
            )

            firestore.collection("userinfo").document(userId)
                .update("physicalInfo", physicalInfo)
                .addOnSuccessListener {
                    Log.d("Firestore", "Physical info saved to Firestore successfully")

                    saveToLocalDatabase(
                        userId, bodyLevel, bodyClassification, exerciseRoutine, otherInfo
                    )

                    // ✅ Navigate to MedicalActivity, passing userId
                    val intent = Intent(this, MedicalActivity::class.java).apply {
                        putExtra("userId", userId)  // Pass the user ID
                    }
                    startActivity(intent)
                    finish()
                }
                .addOnFailureListener { e ->
                    Log.e("FirestoreError", "Failed to save physical info: ${e.message}")
                    Toast.makeText(this, "Failed to save to Firestore", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveToLocalDatabase(
        userId: String,
        bodyLevel: String,
        bodyClassification: String,
        exerciseRoutine: String,
        otherInfo: String
    ) {
        val db = dbHelper.writableDatabase

        val values = android.content.ContentValues().apply {
            put("firebase_id", userId)
            put("body_level", bodyLevel)
            put("body_classification", bodyClassification)
            put("exercise_routine", exerciseRoutine)
            put("other_info", otherInfo)
            put("date", System.currentTimeMillis().toString())
            put("is_synced", 0)
        }

        db.insertWithOnConflict(
            DatabaseHelper.TABLE_USERINFO,
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
    }
}
