package com.example.physiqueaiapkfinal

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class MedicalActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var userId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_medical)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Get the userId from the Intent extra
        userId = intent.getStringExtra("userId") ?: ""
        if (userId.isEmpty()) {
            Toast.makeText(this, "User ID not found!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Initialize UI elements
        val btnBack = findViewById<ImageButton>(R.id.btnBack) // Change to ImageButton
        val btnSubmit = findViewById<Button>(R.id.btnSubmit)
        val spinnerCondition = findViewById<Spinner>(R.id.spinnerCondition)
        val spinnerMedication = findViewById<Spinner>(R.id.spinnerMedication)
        val spinnerAllergies = findViewById<Spinner>(R.id.spinnerAllergies)
        val spinnerFractures = findViewById<Spinner>(R.id.spinnerFractures)
        val spinnerOtherConditions = findViewById<Spinner>(R.id.spinnerOtherConditions)

        // Spinner data
        val conditionItems = arrayOf("None", "Asthma", "Heart Condition", "Diabetes", "Joint Pain", "Back Pain", "Other")
        val medicationItems = arrayOf("None", "Blood Pressure Meds", "Insulin", "Pain Reliever", "Muscle Relaxants", "Other")
        val allergyItems = arrayOf("None", "Peanuts", "Dust", "Pollen", "Medication Allergy", "Other")
        val fractureItems = arrayOf("None", "Arm", "Leg", "Shoulder", "Back", "Other")
        val otherConditionItems = arrayOf("None", "Chronic Fatigue", "Dizziness", "Post-Surgery", "Recent Illness", "Other")

        // Function to set the data for spinners
        fun setSpinner(spinner: Spinner, items: Array<String>) {
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
        }

        // Set up the spinners
        setSpinner(spinnerCondition, conditionItems)
        setSpinner(spinnerMedication, medicationItems)
        setSpinner(spinnerAllergies, allergyItems)
        setSpinner(spinnerFractures, fractureItems)
        setSpinner(spinnerOtherConditions, otherConditionItems)

        // Back button click listener to navigate back
        btnBack.setOnClickListener {
            finish()
        }

        // Submit button click listener to save medical information
        btnSubmit.setOnClickListener {
            // Get selected values from spinners
            val condition = spinnerCondition.selectedItem.toString()
            val medication = spinnerMedication.selectedItem.toString()
            val allergies = spinnerAllergies.selectedItem.toString()
            val fractures = spinnerFractures.selectedItem.toString()
            val otherConditions = spinnerOtherConditions.selectedItem.toString()

            // Check if any condition is selected
            if (listOf(condition, medication, allergies, fractures, otherConditions).all { it == "None" }) {
                Toast.makeText(this, "Please select at least one condition", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Save the selected information
            saveMedicalInformation(condition, medication, allergies, fractures, otherConditions)
        }
    }

    // Method to save the medical information to Firestore
    private fun saveMedicalInformation(
        condition: String,
        medication: String,
        allergies: String,
        fractures: String,
        otherConditions: String
    ) {
        // Prepare data for saving
        val medicalData = mapOf(
            "condition" to condition,
            "medication" to medication,
            "allergies" to allergies,
            "fractures" to fractures,
            "otherConditions" to otherConditions
        )

        val firestoreData = mapOf("medicalInfo" to medicalData)

        // Save data to Firestore
        firestore.collection("userinfo").document(userId)
            .set(firestoreData, SetOptions.merge()) // Merge with existing data
            .addOnSuccessListener {
                Toast.makeText(this, "Medical info saved successfully!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, DashboardActivity::class.java)) // Redirect to Dashboard
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save medical info to Firestore: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
