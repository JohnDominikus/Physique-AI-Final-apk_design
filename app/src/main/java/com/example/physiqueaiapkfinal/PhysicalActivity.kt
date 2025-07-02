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

class PhysicalActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var email: String

    companion object {
        private const val TAG = "PhysicalActivity"
        private const val DEFAULT_SELECT = "Select"
    }

    // Only for the 3 special spinners
    data class SpinnerItem(val displayText: String, val saveValue: String, val description: String)

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

        // Simple spinner setup for Body Level and Others
        setupSimpleSpinner(spinnerBodyLevel, arrayOf("Select", "Beginner", "Intermediate", "Advanced"))
        setupSimpleSpinner(spinnerOthers, arrayOf("Select", "Gym member", "Home workout", "Personal trainer", "Other"))

        // Enhanced setup for the 3 special spinners
        setupSpinnerWithDescriptions(spinnerBodyClassification, arrayOf(
            SpinnerItem("Select", DEFAULT_SELECT, "Please select your body type"),
            SpinnerItem("Endomorph", "endomorph", "Naturally curvy or stocky, tends to store fat easily"),
            SpinnerItem("Mesomorph", "mesomorph", "Naturally muscular and athletic, gains muscle easily"),
            SpinnerItem("Ectomorph", "ectomorph", "Naturally lean and slender, finds it hard to gain weight")
        ))

        setupSpinnerWithDescriptions(spinnerExercise, arrayOf(
            SpinnerItem("Select", DEFAULT_SELECT, "Please select your preferred exercise type"),
            SpinnerItem("Strength", "strength", "Focus on building muscle through weight training"),
            SpinnerItem("Cardio", "cardio", "Focus on cardiovascular endurance (running, cycling, etc.)"),
            SpinnerItem("Flexibility", "flexibility", "Focus on stretching and mobility (yoga, pilates, etc.)"),
            SpinnerItem("Mixed", "mixed", "Combination of different exercise types")
        ))

        setupSpinnerWithDescriptions(spinnerGymMode, arrayOf(
            SpinnerItem("Select", DEFAULT_SELECT, "Please select your preferred diet approach"),
            SpinnerItem("Vegetarian", "vegetarian", "Plant-based diet with no meat"),
            SpinnerItem("Keto", "keto", "Low-carb, high-fat diet"),
            SpinnerItem("High protein", "high_protein", "Diet focused on high protein intake"),
            SpinnerItem("No preference", "no_preference", "No specific dietary preferences")
        ))

        btnBackToNext.setOnClickListener { finish() }

        btnNext.setOnClickListener {
            val bodyLevel = spinnerBodyLevel.selectedItem.toString()
            val bodyClassification = (spinnerBodyClassification.selectedItem as? SpinnerItem)?.saveValue ?: DEFAULT_SELECT
            val exerciseRoutine = (spinnerExercise.selectedItem as? SpinnerItem)?.saveValue ?: DEFAULT_SELECT
            val otherInfo = spinnerOthers.selectedItem.toString()
            val gymMode = (spinnerGymMode.selectedItem as? SpinnerItem)?.saveValue ?: DEFAULT_SELECT

            if (!isValidSelection(bodyLevel, bodyClassification, exerciseRoutine)) {
                showToast("Please fill all required fields")
                return@setOnClickListener
            }

            savePhysicalInfo(bodyLevel, bodyClassification, exerciseRoutine, otherInfo, gymMode)
        }
    }

    // Info Dialog Methods (only for the 3 special spinners)
    fun showBodyClassificationInfo(view: View) {
        showInfoDialog(
            "Body Type Information",
            "Select your natural body type:\n\n" +
                    "• Endomorph: Naturally curvy/stocky, tends to store fat easily\n" +
                    "• Mesomorph: Naturally muscular/athletic, gains muscle easily\n" +
                    "• Ectomorph: Naturally lean/slender, finds it hard to gain weight"
        )
    }

    fun showExerciseRoutineInfo(view: View) {
        showInfoDialog(
            "Exercise Routine Information",
            "Select your preferred workout focus:\n\n" +
                    "• Strength: Focus on building muscle through weights\n" +
                    "• Cardio: Focus on cardiovascular endurance\n" +
                    "• Flexibility: Focus on stretching and mobility\n" +
                    "• Mixed: Combination of different exercise types"
        )
    }

    fun showGymModeInfo(view: View) {
        showInfoDialog(
            "Diet Preference Information",
            "Select your preferred diet approach:\n\n" +
                    "• Vegetarian: Plant-based diet with no meat\n" +
                    "• Keto: Low-carb, high-fat diet\n" +
                    "• High protein: Diet focused on protein intake\n" +
                    "• No preference: No specific diet"
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
            .setMessage("$description\n\nAre you sure you want to select this?")
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