package com.example.physiqueaiapkfinal

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class PhysicalActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var dbHelper: DatabaseHelper
    private lateinit var email: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_physical)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        dbHelper = DatabaseHelper(this)
        email = intent.getStringExtra("email").orEmpty()

        val spinnerBodyLevel = findViewById<Spinner>(R.id.spinnerBodyLevel)
        val spinnerBodyClassification = findViewById<Spinner>(R.id.spinnerBodyClassification)
        val spinnerExercise = findViewById<Spinner>(R.id.spinnerExercise)
        val spinnerOthers = findViewById<Spinner>(R.id.spinnerOthers)
        val spinnerGymMode = findViewById<Spinner>(R.id.spinnerGymMode)
        val btnBack = findViewById<Button>(R.id.btnBackToNext)
        val btnNext = findViewById<Button>(R.id.btnNext)

        setupSpinners()

        btnBack.setOnClickListener { finish() }

        btnNext.setOnClickListener {
            val bodyLevel = spinnerBodyLevel.selectedItem.toString()
            val bodyClassification = spinnerBodyClassification.selectedItem.toString()
            val exerciseRoutine = spinnerExercise.selectedItem.toString()
            val otherInfo = spinnerOthers.selectedItem.toString()
            val gymMode = spinnerGymMode.selectedItem.toString()

            if (validateSelection(bodyLevel, bodyClassification, exerciseRoutine)) {
                savePhysicalInfo(bodyLevel, bodyClassification, exerciseRoutine, otherInfo, gymMode)
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
        val gymModes = arrayOf("Select", "Vegetarian", "Keto", "High protein", "No preference")

        setupSpinner(R.id.spinnerBodyLevel, bodyLevels)
        setupSpinner(R.id.spinnerBodyClassification, classifications)
        setupSpinner(R.id.spinnerExercise, exerciseRoutines)
        setupSpinner(R.id.spinnerOthers, others)
        setupSpinner(R.id.spinnerGymMode, gymModes)
    }

    private fun setupSpinner(spinnerId: Int, items: Array<String>) {
        val spinner = findViewById<Spinner>(spinnerId)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    private fun validateSelection(bodyLevel: String, bodyClassification: String, exerciseRoutine: String): Boolean {
        return bodyLevel != "Select" && bodyClassification != "Select" && exerciseRoutine != "Select"
    }

    private fun savePhysicalInfo(
        bodyLevel: String,
        bodyClassification: String,
        exerciseRoutine: String,
        otherInfo: String,
        gymMode: String
    ) {
        val userId = auth.currentUser?.uid

        if (userId != null) {
            val physicalInfo = hashMapOf(
                "bodyLevel" to bodyLevel,
                "bodyClassification" to bodyClassification,
                "exerciseRoutine" to exerciseRoutine,
                "otherInfo" to otherInfo,
                "gymMode" to gymMode
            )

            firestore.collection("userinfo").document(userId)
                .set(mapOf("physicalInfo" to physicalInfo), SetOptions.merge())
                .addOnSuccessListener {
                    Log.d("Firestore", "Physical info saved")

                    // Navigate to next screen
                    val intent = Intent(this, MedicalActivity::class.java)
                    intent.putExtra("userId", userId)
                    startActivity(intent)
                    finish()
                }
                .addOnFailureListener { e ->
                    Log.e("FirestoreError", "Failed to save: ${e.message}")
                    Toast.makeText(this, "Failed to save data", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
        }
    }
}
