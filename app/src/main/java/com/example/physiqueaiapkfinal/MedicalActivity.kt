package com.example.physiqueaiapkfinal

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class MedicalActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var userId: String

    companion object {
        private const val DEFAULT_SELECT = "none"
    }

    // Data class for spinner items with descriptions
    data class SpinnerItem(val displayText: String, val saveValue: String, val description: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_medical)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        userId = intent.getStringExtra("userId") ?: run {
            Toast.makeText(this, "User ID not found!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Initialize UI elements
        val btnBack = findViewById<ImageButton>(R.id.btnBack)
        val btnSubmit = findViewById<Button>(R.id.btnSubmit)
        val spinnerCondition = findViewById<Spinner>(R.id.spinnerCondition)
        val spinnerMedication = findViewById<Spinner>(R.id.spinnerMedication)
        val spinnerAllergies = findViewById<Spinner>(R.id.spinnerAllergies)
        val spinnerFractures = findViewById<Spinner>(R.id.spinnerFractures)
        val spinnerOtherConditions = findViewById<Spinner>(R.id.spinnerOtherConditions)

        // Set up simple spinners
        setupSimpleSpinner(spinnerCondition, arrayOf(
            "None", "Asthma", "Heart Problem", "Diabetes",
            "High Blood Pressure", "Joint Pain", "Back Pain",
            "Arthritis", "Thyroid Problem", "Knee Pain",
            "Shoulder Pain", "Other"
        ))

        setupSimpleSpinner(spinnerMedication, arrayOf(
            "None", "Blood Pressure Meds", "Insulin",
            "Pain Reliever", "Muscle Relaxants", "Other"
        ))

        // Enhanced spinners with descriptions
        setupSpinnerWithDescriptions(spinnerAllergies, arrayOf(
            SpinnerItem("None", "none", "No known allergies"),
            SpinnerItem("Food", "food", "Allergies to peanuts, shellfish, dairy, etc."),
            SpinnerItem("Environmental", "environmental", "Allergies to pollen, dust, mold, etc."),
            SpinnerItem("Medication", "medication", "Allergies to penicillin, NSAIDs, etc."),
            SpinnerItem("Insect Stings", "insect", "Allergies to bee stings, wasp stings, etc."),
            SpinnerItem("Latex", "latex", "Allergy to latex products"),
            SpinnerItem("Other", "other", "Other unspecified allergies")
        ))

        setupSpinnerWithDescriptions(spinnerFractures, arrayOf(
            SpinnerItem("None", "none", "No history of fractures"),
            SpinnerItem("Arm", "arm", "Fractures in arm bones (humerus, radius, ulna)"),
            SpinnerItem("Leg", "leg", "Fractures in leg bones (femur, tibia, fibula)"),
            SpinnerItem("Shoulder", "shoulder", "Clavicle or scapula fractures"),
            SpinnerItem("Back", "back", "Vertebral fractures"),
            SpinnerItem("Wrist", "wrist", "Wrist or hand fractures"),
            SpinnerItem("Ankle", "ankle", "Ankle or foot fractures"),
            SpinnerItem("Other", "other", "Other unspecified fractures")
        ))

        setupSpinnerWithDescriptions(spinnerOtherConditions, arrayOf(
            SpinnerItem("None", "none", "No other medical conditions"),
            SpinnerItem("Chronic Fatigue", "fatigue", "Persistent tiredness affecting daily activities"),
            SpinnerItem("Dizziness", "dizziness", "Frequent episodes of dizziness or vertigo"),
            SpinnerItem("Post-Surgery", "post_surgery", "Recent surgical procedures"),
            SpinnerItem("Recent Illness", "illness", "Recent significant illnesses or infections"),
            SpinnerItem("Other", "other", "Other unspecified medical conditions")
        ))

        btnBack.setOnClickListener { finish() }

        btnSubmit.setOnClickListener {
            val condition = spinnerCondition.selectedItem.toString().trim()
            val medication = spinnerMedication.selectedItem.toString().trim()
            val allergies = (spinnerAllergies.selectedItem as? SpinnerItem)?.saveValue ?: DEFAULT_SELECT
            val fractures = (spinnerFractures.selectedItem as? SpinnerItem)?.saveValue ?: DEFAULT_SELECT
            val otherConditions = (spinnerOtherConditions.selectedItem as? SpinnerItem)?.saveValue ?: DEFAULT_SELECT

            if (listOf(condition, medication, allergies, fractures, otherConditions).all { it.equals(DEFAULT_SELECT, ignoreCase = true) }) {
                Toast.makeText(this, "Please select at least one condition", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            saveMedicalInformation(condition, medication, allergies, fractures, otherConditions)
        }
    }

    // Info dialog methods for enhanced spinners
    fun showAllergiesInfo(view: View) {
        showInfoDialog(
            "Allergies Information",
            "Select any allergies that might affect your training:\n\n" +
                    "• Food: Common food allergies\n" +
                    "• Environmental: Pollen, dust, etc.\n" +
                    "• Medication: Drug allergies\n" +
                    "• Insect Stings: Bee, wasp stings\n" +
                    "• Latex: Rubber product allergies"
        )
    }

    fun showFracturesInfo(view: View) {
        showInfoDialog(
            "Fractures Information",
            "Select any past fractures that might affect your training:\n\n" +
                    "• Arm: Humerus, radius, ulna\n" +
                    "• Leg: Femur, tibia, fibula\n" +
                    "• Shoulder: Clavicle fractures\n" +
                    "• Back: Vertebral fractures\n" +
                    "• Wrist/Ankle: Common joint fractures"
        )
    }

    fun showOtherConditionsInfo(view: View) {
        showInfoDialog(
            "Other Conditions Information",
            "Select any other conditions that might affect your training:\n\n" +
                    "• Chronic Fatigue: Persistent tiredness\n" +
                    "• Dizziness: Balance issues\n" +
                    "• Post-Surgery: Recent operations\n" +
                    "• Recent Illness: Current health issues"
        )
    }

    private fun showInfoDialog(title: String, message: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun setupSimpleSpinner(spinner: Spinner, items: Array<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    private fun setupSpinnerWithDescriptions(spinner: Spinner, items: Array<SpinnerItem>) {
        val adapter = object : ArrayAdapter<SpinnerItem>(this, R.layout.spinner_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: layoutInflater.inflate(R.layout.spinner_item, parent, false)
                val textView = view.findViewById<TextView>(R.id.text)
                val iconView = view.findViewById<ImageView>(R.id.icon)

                textView.text = items[position].displayText
                iconView.visibility = if (position == 0) View.GONE else View.VISIBLE
                iconView.setOnClickListener {
                    showDescriptionDialog(items[position].displayText, items[position].description)
                }
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: layoutInflater.inflate(R.layout.spinner_item, parent, false)
                val textView = view.findViewById<TextView>(R.id.text)
                val iconView = view.findViewById<ImageView>(R.id.icon)

                textView.text = items[position].displayText
                iconView.visibility = if (position == 0) View.GONE else View.VISIBLE
                iconView.setOnClickListener {
                    showDescriptionDialog(items[position].displayText, items[position].description)
                }
                return view
            }
        }

        spinner.adapter = adapter

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0) {
                    val selectedItem = items[position]
                    showConfirmationDialog(selectedItem.displayText, selectedItem.description) { confirmed ->
                        if (!confirmed) {
                            spinner.setSelection(0)
                        }
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun showDescriptionDialog(title: String, description: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(description)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showConfirmationDialog(title: String, description: String, callback: (Boolean) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Confirm: $title")
            .setMessage("$description\n\nAre you sure this applies to you?")
            .setPositiveButton("Yes") { dialog, _ ->
                callback(true)
                dialog.dismiss()
            }
            .setNegativeButton("No") { dialog, _ ->
                callback(false)
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun saveMedicalInformation(
        condition: String,
        medication: String,
        allergies: String,
        fractures: String,
        otherConditions: String
    ) {
        val medicalData = mapOf(
            "condition" to condition,
            "medication" to medication,
            "allergies" to allergies,
            "fractures" to fractures,
            "otherConditions" to otherConditions
        )

        firestore.collection("userinfo").document(userId)
            .set(mapOf("medicalInfo" to medicalData), SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, "Medical info saved!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, DashboardActivity::class.java))
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}