package com.example.physiqueaiapkfinal

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

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

        // Initialize views from the layout XML
        genderMaleButton = findViewById(R.id.maleButton)
        genderFemaleButton = findViewById(R.id.femaleButton)
        heightSeekBar = findViewById(R.id.heightSeekBar)
        heightTextView = findViewById(R.id.heightValue)
        weightDecreaseButton = findViewById(R.id.weightMinus)
        weightIncreaseButton = findViewById(R.id.weightPlus)
        weightTextView = findViewById(R.id.weightInput)
        ageDecreaseButton = findViewById(R.id.ageMinus)
        ageIncreaseButton = findViewById(R.id.agePlus)
        ageTextView = findViewById(R.id.ageInput)
        calculateBMIButton = findViewById(R.id.calculateButton)

        // Gender buttons click listeners
        genderMaleButton.setOnClickListener {
            gender = "Male"
            Toast.makeText(this, "Gender: Male", Toast.LENGTH_SHORT).show()
        }

        genderFemaleButton.setOnClickListener {
            gender = "Female"
            Toast.makeText(this, "Gender: Female", Toast.LENGTH_SHORT).show()
        }

        // Height SeekBar listener
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
                weightTextView.text = "$weight"
            }
        }

        weightIncreaseButton.setOnClickListener {
            if (weight < 200) {
                weight += 1
                weightTextView.text = "$weight"
            }
        }

        // Age buttons click listeners
        ageDecreaseButton.setOnClickListener {
            if (age > 10) {
                age -= 1
                ageTextView.text = "$age"
            }
        }

        ageIncreaseButton.setOnClickListener {
            if (age < 100) {
                age += 1
                ageTextView.text = "$age"
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

        // Pass the BMI result to the ResultActivity
        val intent = Intent(this, ResultActivity::class.java)
        intent.putExtra("BMI", bmi)
        intent.putExtra("STATUS", result)
        startActivity(intent)
    }
}
