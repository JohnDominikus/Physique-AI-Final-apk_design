package com.example.physiqueaiapkfinal

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth

class ResultActivity : AppCompatActivity() {

    private lateinit var bmiResultText: TextView
    private lateinit var bmiStatusText: TextView
    private lateinit var saveBmiButton: Button
    private lateinit var backToBmiCalculatorButton: Button

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        // Initialize views
        bmiResultText = findViewById(R.id.bmiResultText)
        bmiStatusText = findViewById(R.id.bmiStatusText)
        saveBmiButton = findViewById(R.id.saveBmiButton)
        backToBmiCalculatorButton = findViewById(R.id.backToBmiCalculatorButton)

        // Get the BMI and Status from the Intent
        val bmi = intent.getDoubleExtra("BMI", 0.0)
        val status = intent.getStringExtra("STATUS")

        // Display the BMI result and status
        bmiResultText.text = "Your BMI: %.2f".format(bmi)  // Format to show 2 decimal places
        bmiStatusText.text = "Status: $status"  // Display the status

        // Handle Save BMI button
        saveBmiButton.setOnClickListener {
            saveBmiInfo(bmi, status)
        }

        // Handle Back to BMI Calculator button
        backToBmiCalculatorButton.setOnClickListener {
            val intent = Intent(this, BmiCalculatorActivity::class.java)
            startActivity(intent)
            finish()  // Optionally finish this activity to prevent going back to it
        }
    }

    private fun saveBmiInfo(bmi: Double, status: String?) {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            val bmiInfo = hashMapOf(
                "bmi" to bmi,
                "status" to status,
                "timestamp" to System.currentTimeMillis()  // Optional timestamp to track when the data was saved
            )

            // Save to Firestore
            firestore.collection("userinfo")
                .document(uid)
                .update("bmiInfo", bmiInfo)
                .addOnSuccessListener {
                    Toast.makeText(this, "BMI info saved!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error saving BMI info: $e", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
        }
    }
}
