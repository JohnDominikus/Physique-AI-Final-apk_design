package com.example.physiqueaiapkfinal

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class PhysicalActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var email: String

    companion object {
        private const val TAG = "PhysicalActivity"
        private const val DEFAULT_SELECT = "Select"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_physical)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        email = intent.getStringExtra("email").orEmpty()

        val spinnerBodyLevel = findViewById<Spinner>(R.id.spinnerBodyLevel)
        val spinnerBodyClassification = findViewById<Spinner>(R.id.spinnerBodyClassification)
        val spinnerExercise = findViewById<Spinner>(R.id.spinnerExercise)
        val spinnerOthers = findViewById<Spinner>(R.id.spinnerOthers)
        val spinnerGymMode = findViewById<Spinner>(R.id.spinnerGymMode)

        val btnBackToNext: ImageButton = findViewById(R.id.btnBackToNext)
        val btnNext = findViewById<Button>(R.id.btnNext)

        setupSpinner(spinnerBodyLevel, arrayOf(DEFAULT_SELECT, "Beginner", "Intermediate", "Advanced"))
        setupSpinner(spinnerBodyClassification, arrayOf(DEFAULT_SELECT, "Endomorph", "Mesomorph", "Ectomorph"))
        setupSpinner(spinnerExercise, arrayOf(DEFAULT_SELECT, "Strength", "Cardio", "Flexibility", "Mixed"))
        setupSpinner(spinnerOthers, arrayOf(DEFAULT_SELECT, "Gym member", "Home workout", "Personal trainer", "Other"))
        setupSpinner(spinnerGymMode, arrayOf(DEFAULT_SELECT, "Vegetarian", "Keto", "High protein", "No preference"))

        btnBackToNext.setOnClickListener { finish() }

        btnNext.setOnClickListener {
            val bodyLevel = spinnerBodyLevel.selectedItem.toString()
            val bodyClassification = spinnerBodyClassification.selectedItem.toString()
            val exerciseRoutine = spinnerExercise.selectedItem.toString()
            val otherInfo = spinnerOthers.selectedItem.toString()
            val gymMode = spinnerGymMode.selectedItem.toString()

            if (!isValidSelection(bodyLevel, bodyClassification, exerciseRoutine)) {
                showToast("Please fill all required fields")
                return@setOnClickListener
            }

            savePhysicalInfo(bodyLevel, bodyClassification, exerciseRoutine, otherInfo, gymMode)
        }
    }

    private fun setupSpinner(spinner: Spinner, items: Array<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    private fun isValidSelection(vararg selections: String): Boolean {
        return selections.none { it == DEFAULT_SELECT }
    }

    private fun savePhysicalInfo(
        bodyLevel: String,
        bodyClassification: String,
        exerciseRoutine: String,
        otherInfo: String,
        gymMode: String
    ) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            showToast("User not authenticated")
            return
        }

        val physicalInfo = mapOf(
            "bodyLevel" to bodyLevel,
            "bodyClassification" to bodyClassification,
            "exerciseRoutine" to exerciseRoutine,
            "otherInfo" to otherInfo,
            "gymMode" to gymMode
        )

        firestore.collection("userinfo").document(userId)
            .set(mapOf("physicalInfo" to physicalInfo), SetOptions.merge())
            .addOnSuccessListener {
                navigateToMedicalActivity(userId)
            }
            .addOnFailureListener { e ->
                showToast("Failed to save data: ${e.message}")
            }
    }

    private fun navigateToMedicalActivity(userId: String) {
        try {
            startActivity(Intent(this, MedicalActivity::class.java).apply {
                putExtra("userId", userId)
                putExtra("email", email)
            })
            finish()
        } catch (e: Exception) {
            showToast("Failed to open next screen.\nCheck:\n1. MedicalActivity exists\n2. It's declared in AndroidManifest.xml")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
