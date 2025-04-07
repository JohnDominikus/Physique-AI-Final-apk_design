package com.example.physiqueaiapkfinal

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ResultActivity : AppCompatActivity() {

    private lateinit var bmiResultText: TextView
    private lateinit var bmiStatusText: TextView
    private lateinit var saveBmiButton: Button
    private lateinit var backToBmiCalculatorButton: Button

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
            // Implement save BMI functionality here (e.g., save to database or SharedPreferences)
        }

        // Handle Back to BMI Calculator button
        backToBmiCalculatorButton.setOnClickListener {
            val intent = Intent(this, BmiCalculatorActivity::class.java)
            startActivity(intent)
            finish()  // Optionally finish this activity to prevent going back to it
        }
    }
}
