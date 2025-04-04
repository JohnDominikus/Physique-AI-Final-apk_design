package com.example.physiqueaiapkfinal

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.*

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var dbHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Initialize Firebase and SQLite
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        dbHelper = DatabaseHelper(this)

        // Bind UI elements
        val btnBack = findViewById<Button>(R.id.btnBack)
        val etFirstName = findViewById<EditText>(R.id.etFirstName)
        val etLastName = findViewById<EditText>(R.id.etLastName)
        val tvBirthdate = findViewById<TextView>(R.id.tvBirthdate)
        val etPhone = findViewById<EditText>(R.id.etPhone)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        // Set default birthdate text
        tvBirthdate.text = getString(R.string.select_birthdate)

        // Back button action
        btnBack.setOnClickListener {
            // Create an intent to go back to the Landing Activity
            val intent = Intent(this, LandingActivity::class.java)
            startActivity(intent) // Start the LandingActivity
            finish() // Finish the current activity to remove it from the back stack
        }

        // Birthdate picker
        tvBirthdate.setOnClickListener { showDatePicker(tvBirthdate) }

        // Register button action
        btnRegister.setOnClickListener {
            val firstName = etFirstName.text.toString().trim()
            val lastName = etLastName.text.toString().trim()
            val birthdate = tvBirthdate.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (validateInput(firstName, lastName, birthdate, phone, email, password)) {
                registerUser(firstName, lastName, birthdate, phone, email, password)
            }
        }
    }

    // DatePicker Dialog
    private fun showDatePicker(tvBirthdate: TextView) {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePicker = DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            val birthdate = String.format(Locale.US, "%02d/%02d/%04d", selectedDay, selectedMonth + 1, selectedYear)
            tvBirthdate.text = birthdate
        }, year, month, day)

        datePicker.show()
    }

    // Input Validation
    private fun validateInput(
        firstName: String,
        lastName: String,
        birthdate: String,
        phone: String,
        email: String,
        password: String
    ): Boolean {
        if (firstName.isEmpty() || lastName.isEmpty() || phone.isEmpty() || email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return false
        }
        if (birthdate == getString(R.string.select_birthdate)) {
            Toast.makeText(this, "Please select a birthdate", Toast.LENGTH_SHORT).show()
            return false
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Invalid email format", Toast.LENGTH_SHORT).show()
            return false
        }
        if (password.length < 6) {
            Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    // Register User with Firebase Authentication
    private fun registerUser(
        firstName: String,
        lastName: String,
        birthdate: String,
        phone: String,
        email: String,
        password: String
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val uid = user?.uid

                    if (uid != null) {
                        saveUserToFirestoreAndLocal(uid, firstName, lastName, birthdate, phone, email, password)
                    } else {
                        showError("Failed to retrieve user ID")
                    }
                } else {
                    showError(task.exception?.message ?: "Registration failed")
                }
            }
    }

    // Save User Data to Firestore and SQLite
    private fun saveUserToFirestoreAndLocal(
        uid: String,
        firstName: String,
        lastName: String,
        birthdate: String,
        phone: String,
        email: String,
        password: String
    ) {
        val userData = hashMapOf(
            "personalInfo" to hashMapOf(
                "firstName" to firstName,
                "lastName" to lastName,
                "birthdate" to birthdate,
                "phone" to phone,
                "email" to email
            )
        )

        // Save to Firestore
        firestore.collection("userinfo").document(uid)
            .set(userData, SetOptions.merge())
            .addOnSuccessListener {
                // Save to local SQLite
                saveToLocalDatabase(uid, firstName, lastName, birthdate, phone, email, password)
            }
            .addOnFailureListener { e ->
                showError("Firestore error: ${e.message}")
            }
    }

    // Save to Local SQLite Database
    private fun saveToLocalDatabase(
        uid: String,
        firstName: String,
        lastName: String,
        birthdate: String,
        phone: String,
        email: String,
        password: String
    ) {
        val db = dbHelper.writableDatabase

        val values = android.content.ContentValues().apply {
            put("firebase_id", uid)
            put("first_name", firstName)
            put("last_name", lastName)
            put("birthdate", birthdate)
            put("phone", phone)
            put("email", email)
            put("password", password)
            put("date", System.currentTimeMillis().toString())
            put("is_synced", 0)
        }

        val rowId = db.insertWithOnConflict(
            DatabaseHelper.TABLE_USERINFO,
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )

        if (rowId != -1L) {
            Toast.makeText(this, "Registration successful", Toast.LENGTH_SHORT).show()

            // Navigate to PhysicalActivity
            val intent = Intent(this, PhysicalActivity::class.java).apply {
                putExtra("userId", uid)
                putExtra("birthdate", birthdate)
            }
            startActivity(intent)
            finish()
        } else {
            showError("Failed to save locally")
        }
    }

    // Error Display Helper
    private fun showError(message: String) {
        Log.e("RegisterActivity", message)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
