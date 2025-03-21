package com.example.physiqueaiapkfinal

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class PhysicalActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var sqliteHelper: DatabaseHelper
    private lateinit var email: String  // Store the email from previous step

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_physical)

        // Initialize FirebaseAuth, Firestore, and SQLite helper
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        sqliteHelper = DatabaseHelper(this)

        // Retrieve the email from the previous activity
        email = intent.getStringExtra("email").toString()

        // Initialize Spinners
        val spinnerBodyLevel = findViewById<Spinner>(R.id.spinnerBodyLevel)
        val spinnerBodyClassification = findViewById<Spinner>(R.id.spinnerBodyClassification)
        val spinnerExercise = findViewById<Spinner>(R.id.spinnerExercise)
        val spinnerOthers = findViewById<Spinner>(R.id.spinnerOthers)

        // Initialize Buttons
        val btnBack = findViewById<Button>(R.id.btnBackToNext)
        val btnNext = findViewById<Button>(R.id.btnNext)

        // Populate Spinners with sample data
        setupSpinners()

        // Handle Back Button
        btnBack.setOnClickListener {
            finish()  // Go back to the previous screen
        }

        // Handle Next Button
        btnNext.setOnClickListener {
            val bodyLevel = spinnerBodyLevel.selectedItem.toString()
            val bodyClassification = spinnerBodyClassification.selectedItem.toString()
            val exerciseRoutine = spinnerExercise.selectedItem.toString()
            val otherInfo = spinnerOthers.selectedItem.toString()

            // Validate selection
            if (bodyLevel != "Select" && bodyClassification != "Select" && exerciseRoutine != "Select") {
                savePhysicalInfo(bodyLevel, bodyClassification, exerciseRoutine, otherInfo)
            } else {
                Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 🔹 Populate Spinner Data
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

    // 🔹 Helper function to setup Spinner with ArrayAdapter
    private fun setupSpinner(spinnerId: Int, items: Array<String>) {
        val spinner = findViewById<Spinner>(spinnerId)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    // 🔹 Save Physical Info to SQLite and Firebase Firestore
    private fun savePhysicalInfo(
        bodyLevel: String,
        bodyClassification: String,
        exerciseRoutine: String,
        otherInfo: String
    ) {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            // Save to Firestore
            val physicalInfo = hashMapOf(
                "bodyLevel" to bodyLevel,
                "bodyClassification" to bodyClassification,
                "exerciseRoutine" to exerciseRoutine,
                "otherInfo" to otherInfo
            )

            firestore.collection("userinfo")
                .document(userId)
                .set(physicalInfo)
                .addOnSuccessListener {
                    Toast.makeText(this, "Physical info saved to Firestore", Toast.LENGTH_SHORT).show()

                    // Save to SQLite
                    val isInserted = sqliteHelper.insertPhysicalInfo(
                        userId,
                        bodyLevel,
                        bodyClassification,
                        exerciseRoutine,
                        otherInfo
                    )

                    if (isInserted) {
                        Toast.makeText(this, "Physical info saved locally", Toast.LENGTH_SHORT).show()

                        // Proceed to MedicalActivity with the email
                        val intent = Intent(this, MedicalActivity::class.java)
                        intent.putExtra("email", email)  // Pass email to the next step
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this, "Failed to save physical info in SQLite", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to save physical info to Firestore: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
        }
    }
}
