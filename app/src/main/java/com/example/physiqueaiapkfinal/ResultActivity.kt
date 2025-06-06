package com.example.physiqueaiapkfinal

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ResultActivity : AppCompatActivity() {

    private lateinit var bmiValueText: TextView
    private lateinit var bmiFormulaText: TextView
    private lateinit var weightRangeText: TextView
    private lateinit var bmiStatusText: TextView
    private lateinit var bmiStatusIcon: ImageView
    private lateinit var saveBmiButton: MaterialButton
    private lateinit var backToBmiCalculatorButton: MaterialButton

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        // View bindings
        bmiValueText               = findViewById(R.id.bmiValueText)
        bmiFormulaText             = findViewById(R.id.bmiFormulaText)
        weightRangeText            = findViewById(R.id.weightRangeText)
        bmiStatusText              = findViewById(R.id.bmiStatusText)
        bmiStatusIcon              = findViewById(R.id.bmiStatusIcon)
        saveBmiButton              = findViewById(R.id.saveBmiButton)
        backToBmiCalculatorButton  = findViewById(R.id.backToBmiCalculatorButton)

        // Get data from intent
        val bmi         = intent.getDoubleExtra("BMI", 0.0)
        val weight      = intent.getIntExtra("CURRENT_WEIGHT", 0).toDouble()
        val height      = intent.getIntExtra("HEIGHT", 0).toDouble()
        val bmiCategory = intent.getStringExtra("STATUS") ?: "Unknown"

        // Calculate healthy weight range
        val heightInMeters = height / 100
        val lowerWeight    = String.format("%.1f", 18.5 * heightInMeters * heightInMeters)
        val upperWeight    = String.format("%.1f", 24.9 * heightInMeters * heightInMeters)

        // Display BMI results
        bmiValueText.text     = String.format("%.1f", bmi)
        bmiFormulaText.text   = "BMI = ${String.format("%.2f", bmi)} kg/m²"
        weightRangeText.text  = "$lowerWeight kg ~ $upperWeight kg"

        // Display normality indicator
        if (bmiCategory == "Normal") {
            bmiStatusText.text = "Your BMI  the normal "
            bmiStatusIcon.setImageResource(R.drawable.ic_check_circle)
        } else {
            bmiStatusText.text = "Your BMI is not   normal "
            bmiStatusIcon.setImageResource(R.drawable.ic_warning)
        }

        // Save BMI to Firestore
        saveBmiButton.setOnClickListener {
            saveBmiInfo(bmi, height, weight)
        }

        backToBmiCalculatorButton.setOnClickListener {
            finish()
        }
    }

    private fun saveBmiInfo(bmi: Double, height: Double, weight: Double) {
        val uid = auth.currentUser?.uid

        if (uid != null) {
            val bmiInfo = hashMapOf(
                "bmi"       to bmi,
                "height"    to height,
                "weight"    to weight,
                "timestamp" to System.currentTimeMillis()
            )

            firestore.collection("userinfo")
                .document(uid)
                .update("bmiInfo", bmiInfo)
                .addOnSuccessListener {
                    Toast.makeText(this, "BMI info saved!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    firestore.collection("userinfo")
                        .document(uid)
                        .set(mapOf("bmiInfo" to bmiInfo))
                        .addOnSuccessListener {
                            Toast.makeText(this, "BMI info saved!", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(
                                this,
                                "Error saving BMI info: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
        } else {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
        }
    }
}
