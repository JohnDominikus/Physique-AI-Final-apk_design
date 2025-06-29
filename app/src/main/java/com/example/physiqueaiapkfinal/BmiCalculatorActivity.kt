package com.example.physiqueaiapkfinal

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import kotlin.math.pow
import kotlin.math.roundToInt

class BmiCalculatorActivity : AppCompatActivity() {

    // UI Components - Make them nullable to prevent crashes
    private var maleCard: CardView? = null
    private var femaleCard: CardView? = null
    private var heightSeekBar: SeekBar? = null
    private var heightTextView: TextView? = null
    private var weightDecreaseButton: ImageButton? = null
    private var weightIncreaseButton: ImageButton? = null
    private var weightTextView: TextView? = null
    private var ageDecreaseButton: ImageButton? = null
    private var ageIncreaseButton: ImageButton? = null
    private var ageTextView: TextView? = null
    private var calculateBMIButton: Button? = null
    private var backButton: ImageButton? = null

    // Data variables
    private var selectedGender: Gender = Gender.MALE
    private var weight: Int = 70
    private var height: Int = 170
    private var age: Int = 25

    // Constants
    companion object {
        private const val MIN_WEIGHT = 30
        private const val MAX_WEIGHT = 300
        private const val MIN_AGE = 10
        private const val MAX_AGE = 120
        private const val MIN_HEIGHT = 100
        private const val MAX_HEIGHT = 250

        private const val UNDERWEIGHT_THRESHOLD = 18.5
        private const val NORMAL_LOWER = 18.5
        private const val NORMAL_UPPER = 24.9
        private const val OVERWEIGHT_LOWER = 25.0
        private const val OVERWEIGHT_UPPER = 29.9
        private const val OBESE_CLASS1_LOWER = 30.0
        private const val OBESE_CLASS1_UPPER = 34.9
        private const val OBESE_CLASS2_LOWER = 35.0
        private const val OBESE_CLASS2_UPPER = 39.9
        private const val OBESE_CLASS3_THRESHOLD = 40.0
    }

    enum class Gender {
        MALE, FEMALE
    }

    data class BMIResult(
        val bmi: Double,
        val category: String,
        val description: String,
        val healthyWeightRange: Pair<Int, Int>
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            setContentView(R.layout.activity_bmi_calculator)
            initializeViews()
            setupInitialValues()
            setupEventListeners()
        } catch (e: Exception) {
            Log.e("BmiCalculatorActivity", "Error in onCreate", e)
            handleError("Failed to initialize BMI calculator", e)
        }
    }

    private fun initializeViews() {
        try {
            maleCard = findViewById(R.id.maleCard)
            femaleCard = findViewById(R.id.femaleCard)
            heightSeekBar = findViewById(R.id.heightSeekBar)
            heightTextView = findViewById(R.id.heightValue)
            weightDecreaseButton = findViewById(R.id.weightMinus)
            weightIncreaseButton = findViewById(R.id.weightPlus)
            weightTextView = findViewById(R.id.weightValue)
            ageDecreaseButton = findViewById(R.id.ageMinus)
            ageIncreaseButton = findViewById(R.id.agePlus)
            ageTextView = findViewById(R.id.ageValue)
            calculateBMIButton = findViewById(R.id.calculateButton)
            backButton = findViewById(R.id.backButton)
        } catch (e: Exception) {
            Log.e("BmiCalculatorActivity", "Error initializing views", e)
            handleError("Failed to initialize interface", e)
        }
    }

    private fun setupInitialValues() {
        try {
            heightSeekBar?.max = MAX_HEIGHT - MIN_HEIGHT
            heightSeekBar?.progress = height - MIN_HEIGHT
            updateUI()
            updateGenderSelection()
        } catch (e: Exception) {
            Log.e("BmiCalculatorActivity", "Error setting initial values", e)
        }
    }

    private fun setupEventListeners() {
        try {
            // Back button click listener
            backButton?.setOnClickListener {
                finish()
            }

            maleCard?.setOnClickListener {
                selectedGender = Gender.MALE
                updateGenderSelection()
                showToast("Gender: Male selected")
            }

            femaleCard?.setOnClickListener {
                selectedGender = Gender.FEMALE
                updateGenderSelection()
                showToast("Gender: Female selected")
            }

            heightSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    height = MIN_HEIGHT + progress
                    updateHeightDisplay()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            weightDecreaseButton?.setOnClickListener {
                if (weight > MIN_WEIGHT) {
                    weight--
                    updateWeightDisplay()
                } else {
                    showToast("Minimum weight is $MIN_WEIGHT kg")
                }
            }

            weightIncreaseButton?.setOnClickListener {
                if (weight < MAX_WEIGHT) {
                    weight++
                    updateWeightDisplay()
                } else {
                    showToast("Maximum weight is $MAX_WEIGHT kg")
                }
            }

            ageDecreaseButton?.setOnClickListener {
                if (age > MIN_AGE) {
                    age--
                    updateAgeDisplay()
                } else {
                    showToast("Minimum age is $MIN_AGE years")
                }
            }

            ageIncreaseButton?.setOnClickListener {
                if (age < MAX_AGE) {
                    age++
                    updateAgeDisplay()
                } else {
                    showToast("Maximum age is $MAX_AGE years")
                }
            }

            calculateBMIButton?.setOnClickListener {
                if (validateInputs()) {
                    calculateAndShowBMI()
                }
            }
        } catch (e: Exception) {
            Log.e("BmiCalculatorActivity", "Error setting up event listeners", e)
            handleError("Failed to setup interactions", e)
        }
    }

    private fun updateGenderSelection() {
        try {
            when (selectedGender) {
                Gender.MALE -> {
                    maleCard?.cardElevation = 12f
                    femaleCard?.cardElevation = 4f
                    maleCard?.alpha = 1.0f
                    femaleCard?.alpha = 0.7f
                }
                Gender.FEMALE -> {
                    femaleCard?.cardElevation = 12f
                    maleCard?.cardElevation = 4f
                    femaleCard?.alpha = 1.0f
                    maleCard?.alpha = 0.7f
                }
            }
        } catch (e: Exception) {
            Log.e("BmiCalculatorActivity", "Error updating gender selection", e)
        }
    }

    private fun updateUI() {
        try {
            updateHeightDisplay()
            updateWeightDisplay()
            updateAgeDisplay()
        } catch (e: Exception) {
            Log.e("BmiCalculatorActivity", "Error updating UI", e)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateHeightDisplay() {
        try {
            heightTextView?.text = "$height cm"
        } catch (e: Exception) {
            Log.e("BmiCalculatorActivity", "Error updating height display", e)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateWeightDisplay() {
        try {
            weightTextView?.text = "$weight kg"
        } catch (e: Exception) {
            Log.e("BmiCalculatorActivity", "Error updating weight display", e)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateAgeDisplay() {
        try {
            ageTextView?.text = "$age yrs"
        } catch (e: Exception) {
            Log.e("BmiCalculatorActivity", "Error updating age display", e)
        }
    }

    private fun validateInputs(): Boolean {
        return try {
            when {
                height !in MIN_HEIGHT..MAX_HEIGHT -> {
                    showToast("Please set a valid height between $MIN_HEIGHT-$MAX_HEIGHT cm")
                    false
                }
                weight !in MIN_WEIGHT..MAX_WEIGHT -> {
                    showToast("Please set a valid weight between $MIN_WEIGHT-$MAX_WEIGHT kg")
                    false
                }
                age !in MIN_AGE..MAX_AGE -> {
                    showToast("Please set a valid age between $MIN_AGE-$MAX_AGE years")
                    false
                }
                else -> true
            }
        } catch (e: Exception) {
            Log.e("BmiCalculatorActivity", "Error validating inputs", e)
            false
        }
    }

    private fun calculateAndShowBMI() {
        try {
            val bmiResult = calculateBMIResult()

            val intent = Intent(this, ResultActivity::class.java).apply {
                putExtra("BMI", bmiResult.bmi)
                putExtra("STATUS", bmiResult.category)
                putExtra("DESCRIPTION", bmiResult.description)
                putExtra("HEALTHY_WEIGHT_MIN", bmiResult.healthyWeightRange.first)
                putExtra("HEALTHY_WEIGHT_MAX", bmiResult.healthyWeightRange.second)
                putExtra("CURRENT_WEIGHT", weight)
                putExtra("HEIGHT", height)
                putExtra("AGE", age)
                putExtra("GENDER", selectedGender.name)
            }

            startActivity(intent)
        } catch (e: Exception) {
            Log.e("BmiCalculatorActivity", "Error calculating and showing BMI", e)
            handleError("Failed to calculate BMI", e)
        }
    }

    private fun calculateBMIResult(): BMIResult {
        return try {
            val heightInMeters = height / 100.0
            val rawBmi = weight / (heightInMeters.pow(2))
            val bmi = (rawBmi * 10).roundToInt() / 10.0

            val (category, description) = when {
                bmi < UNDERWEIGHT_THRESHOLD ->
                    "Underweight" to "You may need to gain weight. Consider consulting a healthcare provider."

                bmi in NORMAL_LOWER..NORMAL_UPPER ->
                    "Normal" to "You have a healthy weight. Keep it up!"

                bmi in OVERWEIGHT_LOWER..OVERWEIGHT_UPPER ->
                    "Overweight" to "Consider some lifestyle changes like exercise or healthy eating."

                bmi in OBESE_CLASS1_LOWER..OBESE_CLASS1_UPPER ->
                    "Obese (Class I)" to "You should consult a doctor about a weight-loss plan."

                bmi in OBESE_CLASS2_LOWER..OBESE_CLASS2_UPPER ->
                    "Obese (Class II)" to "Weight loss is highly recommended. Consult a healthcare provider."

                bmi >= OBESE_CLASS3_THRESHOLD ->
                    "Obese (Class III)" to "You should seek medical attention for your weight urgently."

                else -> "Unknown" to "Unable to classify BMI"
            }

            val healthyWeightRange = calculateHealthyWeightRange(heightInMeters)

            BMIResult(
                bmi = bmi,
                category = category,
                description = description,
                healthyWeightRange = healthyWeightRange
            )
        } catch (e: Exception) {
            Log.e("BmiCalculatorActivity", "Error calculating BMI result", e)
            BMIResult(0.0, "Error", "Failed to calculate BMI", Pair(0, 0))
        }
    }

    private fun calculateHealthyWeightRange(heightInMeters: Double): Pair<Int, Int> {
        return try {
            val minWeight = (NORMAL_LOWER * heightInMeters.pow(2)).roundToInt()
            val maxWeight = (NORMAL_UPPER * heightInMeters.pow(2)).roundToInt()
            Pair(minWeight, maxWeight)
        } catch (e: Exception) {
            Log.e("BmiCalculatorActivity", "Error calculating healthy weight range", e)
            Pair(0, 0)
        }
    }

    private fun showToast(message: String) {
        try {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("BmiCalculatorActivity", "Error showing toast: $message", e)
        }
    }

    private fun handleError(message: String, exception: Exception) {
        Log.e("BmiCalculatorActivity", "Error: $message", exception)
        try {
            Toast.makeText(this, "Error: $message. Please try again.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e("BmiCalculatorActivity", "Error showing error message", e)
        }
    }
}