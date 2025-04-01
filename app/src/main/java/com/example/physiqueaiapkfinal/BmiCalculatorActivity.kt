package com.example.physiqueaiapkfinal

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.physiqueaiapkfinal.R

class BmiCalculatorActivity : AppCompatActivity() {

    private lateinit var genderMaleButton: Button
    private lateinit var genderFemaleButton: Button
    private lateinit var heightSeekBar: SeekBar
    private lateinit var heightTextView: TextView
    private lateinit var weightDecreaseButton: ImageButton
    private lateinit var weightIncreaseButton: ImageButton
    private lateinit var weightTextView: TextView
    private lateinit var ageDecreaseButton: ImageButton
    private lateinit var ageIncreaseButton: ImageButton
    private lateinit var ageTextView: TextView
    private lateinit var calculateBMIButton: Button

    private var gender: String = "Male"
    private var weight: Int = 70
    private var height: Int = 170
    private var age: Int = 25

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bmi_calculator)

        // Initialize views
        genderMaleButton = findViewById(R.id.genderMaleButton)
        genderFemaleButton = findViewById(R.id.genderFemaleButton)
        heightSeekBar = findViewById(R.id.heightSeekBar)
        heightTextView = findViewById(R.id.heightTextView)
        weightDecreaseButton = findViewById(R.id.weightDecreaseButton)
        weightIncreaseButton = findViewById(R.id.weightIncreaseButton)
        weightTextView = findViewById(R.id.weightTextView)
        ageDecreaseButton = findViewById(R.id.ageDecreaseButton)
        ageIncreaseButton = findViewById(R.id.ageIncreaseButton)
        ageTextView = findViewById(R.id.ageTextView)
        calculateBMIButton = findViewById(R.id.calculateBMIButton)

        // Gender buttons click listener
        genderMaleButton.setOnClickListener {
            gender = "Male"
            Toast.makeText(this, "Gender: Male", Toast.LENGTH_SHORT).show()
        }

        genderFemaleButton.setOnClickListener {
            gender = "Female"
            Toast.makeText(this, "Gender: Female", Toast.LENGTH_SHORT).show()
        }

        // Height slider listener
        heightSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                height = progress
                heightTextView.text = "Height: $height cm"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Weight buttons click listeners
        weightDecreaseButton.setOnClickListener {
            if (weight > 30) {
                weight -= 1
                weightTextView.text = "Weight: $weight kg"
            }
        }

        weightIncreaseButton.setOnClickListener {
            if (weight < 200) {
                weight += 1
                weightTextView.text = "Weight: $weight kg"
            }
        }

        // Age buttons click listeners
        ageDecreaseButton.setOnClickListener {
            if (age > 10) {
                age -= 1
                ageTextView.text = "Age: $age"
            }
        }

        ageIncreaseButton.setOnClickListener {
            if (age < 100) {
                age += 1
                ageTextView.text = "Age: $age"
            }
        }

        // Calculate BMI button click listener
        calculateBMIButton.setOnClickListener {
            calculateBMI()
        }
    }

    private fun calculateBMI() {
        val heightInMeters = height / 100.0
        val bmi = weight / (heightInMeters * heightInMeters)

        val result = when {
            bmi < 18.5 -> "Underweight"
            bmi in 18.5..24.9 -> "Normal"
            bmi in 25.0..29.9 -> "Overweight"
            else -> "Obese"
        }

        Toast.makeText(this, "Your BMI is: %.2f ($result)".format(bmi), Toast.LENGTH_LONG).show()
    }
}
