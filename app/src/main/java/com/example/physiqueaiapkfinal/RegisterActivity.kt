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

        // Initialize UI elements
        val btnBack = findViewById<Button>(R.id.btnBack)
        val etFirstName = findViewById<EditText>(R.id.etFirstName)
        val etLastName = findViewById<EditText>(R.id.etLastName)
        val tvBirthdate = findViewById<TextView>(R.id.tvBirthdate)
        val etPhone = findViewById<EditText>(R.id.etPhone)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        // Set default birthdate text using a string resource
        tvBirthdate.text = getString(R.string.select_birthdate)

        // Back button action
        btnBack.setOnClickListener {
            finish()  // Go back to the previous screen
        }

        // Birthdate picker
        tvBirthdate.setOnClickListener {
            showDatePicker(tvBirthdate)
        }

        // Register button action
        btnRegister.setOnClickListener {
            val firstName = etFirstName.text.toString().trim()
            val lastName = etLastName.text.toString().trim()
            val birthdate = tvBirthdate.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            // Validate input fields
            if (validateInput(firstName, lastName, birthdate, phone, email, password)) {
                registerUser(firstName, lastName, birthdate, phone, email, password)
            } else {
                Toast.makeText(this, getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Show DatePickerDialog
    private fun showDatePicker(tvBirthdate: TextView) {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePicker = DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            // Format the date as dd/MM/yyyy using a specific locale (e.g., Locale.US)
            val formattedDay = String.format(Locale.US, "%02d", selectedDay)
            val formattedMonth = String.format(Locale.US, "%02d", selectedMonth + 1)  // Month is 0-based
            val birthdate = "$formattedDay/$formattedMonth/$selectedYear"
            tvBirthdate.text = birthdate
        }, year, month, day)

        datePicker.show()
    }

    // Validate user input
    private fun validateInput(
        firstName: String,
        lastName: String,
        birthdate: String,
        phone: String,
        email: String,
        password: String
    ): Boolean {
        return firstName.isNotEmpty() &&
                lastName.isNotEmpty() &&
                birthdate != getString(R.string.select_birthdate) &&  // Ensure birthdate is selected
                phone.isNotEmpty() &&
                email.isNotEmpty() &&
                android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() &&
                password.length >= 6
    }

    // Register the user with Firebase Authentication and save data to Firestore and SQLite
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
                        // Prepare user data for Firestore and SQLite
                        val userData = hashMapOf(
                            "personalInfo" to hashMapOf(
                                "firstName" to firstName,
                                "lastName" to lastName,
                                "birthdate" to birthdate,  // Birthdate in dd/MM/yyyy format
                                "phone" to phone,
                                "email" to email
                            )
                        )

                        // Save to Firestore
                        firestore.collection("userinfo").document(uid)
                            .set(userData, SetOptions.merge())
                            .addOnSuccessListener {
                                // Save to SQLite
                                val isInserted = dbHelper.insertUser(uid, firstName, lastName, birthdate, phone, email, password)

                                if (isInserted != -1L) {
                                    Toast.makeText(this, getString(R.string.registration_successful), Toast.LENGTH_SHORT).show()

                                    // Navigate to PhysicalActivity
                                    val intent = Intent(this, PhysicalActivity::class.java)
                                    intent.putExtra("userId", uid)  // Pass the user ID to the next activity
                                    intent.putExtra("birthdate", birthdate)  // Pass the birthdate to the next activity
                                    startActivity(intent)
                                    finish()
                                } else {
                                    Toast.makeText(this, getString(R.string.failed_to_save_locally), Toast.LENGTH_SHORT).show()
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e("Firestore", "Error saving user data: ${e.message}")
                                Toast.makeText(this, getString(R.string.failed_to_save_firestore), Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        Toast.makeText(this, getString(R.string.failed_to_retrieve_user_id), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e("Firebase", "Auth Error: ${task.exception?.message}")
                    Toast.makeText(this, getString(R.string.registration_failed, task.exception?.message), Toast.LENGTH_SHORT).show()
                }
            }
    }
}