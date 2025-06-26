package com.example.physiqueaiapkfinal

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.*

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    // UI elements
    private lateinit var etFirstName: TextInputEditText
    private lateinit var etLastName: TextInputEditText
    private lateinit var tvBirthdate: TextInputEditText
    private lateinit var etPhone: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnRegister: MaterialButton
    private lateinit var btnBack: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Initialize UI elements
        initViews()

        // Set default birthdate
        tvBirthdate.setText(getString(R.string.select_birthdate))
        tvBirthdate.inputType = android.text.InputType.TYPE_NULL

        // DatePicker listeners
        tvBirthdate.setOnClickListener { showDatePicker() }
        tvBirthdate.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showDatePicker()
        }

        // Navigation
        btnBack.setOnClickListener {
            navigateToLandingActivity()
        }

        btnRegister.setOnClickListener {
            registerUser()
        }
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        etFirstName = findViewById(R.id.etFirstName)
        etLastName = findViewById(R.id.etLastName)
        tvBirthdate = findViewById(R.id.tvBirthdate)
        etPhone = findViewById(R.id.etPhone)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnRegister = findViewById(R.id.btnRegister)
    }

    private fun navigateToLandingActivity() {
        val intent = Intent(this, LandingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePicker = DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            val formattedDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).run {
                val cal = Calendar.getInstance().apply {
                    set(selectedYear, selectedMonth, selectedDay)
                }
                format(cal.time)
            }
            tvBirthdate.setText(formattedDate)
        }, year, month, day)

        datePicker.datePicker.maxDate = System.currentTimeMillis()
        datePicker.show()
    }

    private fun registerUser() {
        val firstName = etFirstName.text.toString().trim()
        val lastName = etLastName.text.toString().trim()
        val birthdate = tvBirthdate.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (!validateInput(firstName, lastName, birthdate, phone, email, password)) {
            return
        }

        btnRegister.isEnabled = false

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    user?.let {
                        saveUserToFirestore(
                            it.uid,
                            firstName,
                            lastName,
                            birthdate,
                            phone,
                            email
                        )
                    } ?: run {
                        showError("Failed to retrieve user ID")
                        btnRegister.isEnabled = true
                    }
                } else {
                    showError(task.exception?.message ?: "Registration failed")
                    btnRegister.isEnabled = true
                }
            }
    }

    private fun validateInput(
        firstName: String,
        lastName: String,
        birthdate: String,
        phone: String,
        email: String,
        password: String
    ): Boolean {
        return when {
            firstName.isEmpty() -> {
                etFirstName.error = "Please enter first name"
                false
            }
            lastName.isEmpty() -> {
                etLastName.error = "Please enter last name"
                false
            }
            birthdate == getString(R.string.select_birthdate) -> {
                Toast.makeText(this, "Please select a birthdate", Toast.LENGTH_SHORT).show()
                false
            }
            phone.isEmpty() -> {
                etPhone.error = "Please enter phone number"
                false
            }
            !phone.matches(Regex("^09\\d{9}\$")) -> {
                etPhone.error = "Phone number must be 11 digits and start with 09"
                false
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                etEmail.error = "Invalid email format"
                false
            }
            password.length < 6 -> {
                etPassword.error = "Password must be at least 6 characters long"
                false
            }
            else -> true
        }
    }

    private fun saveUserToFirestore(
        uid: String,
        firstName: String,
        lastName: String,
        birthdate: String,
        phone: String,
        email: String
    ) {
        val formatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val birthdateObj = formatter.parse(birthdate)

        val userData = hashMapOf(
            "personalInfo" to hashMapOf(
                "firstName" to firstName,
                "lastName" to lastName,
                "birthdate" to birthdateObj,
                "phone" to phone,
                "email" to email
            )
        )

        firestore.collection("userinfo").document(uid)
            .set(userData, SetOptions.merge())
            .addOnSuccessListener {
                navigateToPhysicalActivity(uid, birthdate)
            }
            .addOnFailureListener { e ->
                showError("Firestore error: ${e.message}")
                btnRegister.isEnabled = true
            }
    }

    private fun navigateToPhysicalActivity(uid: String, birthdate: String) {
        Toast.makeText(this, "Registration successful", Toast.LENGTH_SHORT).show()
        Intent(this, PhysicalActivity::class.java).apply {
            putExtra("userId", uid)
            putExtra("birthdate", birthdate)
            startActivity(this)
            finish()
        }
    }

    private fun showError(message: String) {
        Log.e("RegisterActivity", message)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
