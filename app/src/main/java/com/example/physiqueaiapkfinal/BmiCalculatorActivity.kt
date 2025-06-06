package com.example.physiqueaiapkfinal

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import kotlin.math.pow
import kotlin.math.roundToInt

class BmiCalculatorActivity : AppCompatActivity() {

    // UI Components
    private lateinit var maleCard: CardView
    private lateinit var femaleCard: CardView
    private lateinit var heightSeekBar: SeekBar
    private lateinit var heightTextView: TextView
    private lateinit var weightDecreaseButton: ImageButton
    private lateinit var weightIncreaseButton: ImageButton
    private lateinit var weightTextView: TextView
    private lateinit var ageDecreaseButton: ImageButton
    private lateinit var ageIncreaseButton: ImageButton
    private lateinit var ageTextView: TextView
    private lateinit var calculateBMIButton: Button
    private lateinit var backButton: ImageButton

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
        setContentView(R.layout.activity_bmi_calculator)

        initializeViews()
        setupInitialValues()
        setupEventListeners()
    }

    private fun initializeViews() {
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
    }

    private fun setupInitialValues() {
        heightSeekBar.max = MAX_HEIGHT - MIN_HEIGHT
        heightSeekBar.progress = height - MIN_HEIGHT
        updateUI()
        updateGenderSelection()
    }

    private fun setupEventListeners() {
        // Back button click listener
        backButton.setOnClickListener {
            finish() // Close the current activity and return to previous one
        }

        maleCard.setOnClickListener {
            selectedGender = Gender.MALE
            updateGenderSelection()
            showToast("Gender: Male selected")
        }

        femaleCard.setOnClickListener {
            selectedGender = Gender.FEMALE
            updateGenderSelection()
            showToast("Gender: Female selected")
        }

        heightSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                height = MIN_HEIGHT + progress
                updateHeightDisplay()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        weightDecreaseButton.setOnClickListener {
            if (weight > MIN_WEIGHT) {
                weight--
                updateWeightDisplay()
            } else {
                showToast("Minimum weight is $MIN_WEIGHT kg")
            }
        }

        weightIncreaseButton.setOnClickListener {
            if (weight < MAX_WEIGHT) {
                weight++
                updateWeightDisplay()
            } else {
                showToast("Maximum weight is $MAX_WEIGHT kg")
            }
        }

        ageDecreaseButton.setOnClickListener {
            if (age > MIN_AGE) {
                age--
                updateAgeDisplay()
            } else {
                showToast("Minimum age is $MIN_AGE years")
            }
        }

        ageIncreaseButton.setOnClickListener {
            if (age < MAX_AGE) {
                age++
                updateAgeDisplay()
            } else {
                showToast("Maximum age is $MAX_AGE years")
            }
        }

        calculateBMIButton.setOnClickListener {
            if (validateInputs()) {
                calculateAndShowBMI()
            }
        }
    }

    private fun updateGenderSelection() {
        when (selectedGender) {
            Gender.MALE -> {
                maleCard.cardElevation = 12f
                femaleCard.cardElevation = 4f
                maleCard.alpha = 1.0f
                femaleCard.alpha = 0.7f
            }
            Gender.FEMALE -> {
                femaleCard.cardElevation = 12f
                maleCard.cardElevation = 4f
                femaleCard.alpha = 1.0f
                maleCard.alpha = 0.7f
            }
        }
    }

    private fun updateUI() {
        updateHeightDisplay()
        updateWeightDisplay()
        updateAgeDisplay()
    }

    @SuppressLint("SetTextI18n")
    private fun updateHeightDisplay() {
        heightTextView.text = "$height cm"
    }

    @SuppressLint("SetTextI18n")
    private fun updateWeightDisplay() {
        weightTextView.text = "$weight kg"
    }

    @SuppressLint("SetTextI18n")
    private fun updateAgeDisplay() {
        ageTextView.text = "$age yrs"
    }

    private fun validateInputs(): Boolean {
        return when {
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
    }

    private fun calculateAndShowBMI() {
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
    }

    private fun calculateBMIResult(): BMIResult {
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

        return BMIResult(
            bmi = bmi,
            category = category,
            description = description,
            healthyWeightRange = healthyWeightRange
        )
    }

    private fun calculateHealthyWeightRange(heightInMeters: Double): Pair<Int, Int> {
        val minWeight = (NORMAL_LOWER * heightInMeters.pow(2)).roundToInt()
        val maxWeight = (NORMAL_UPPER * heightInMeters.pow(2)).roundToInt()
        return Pair(minWeight, maxWeight)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}