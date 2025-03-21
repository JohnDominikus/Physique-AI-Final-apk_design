package com.example.physiqueaiapkfinal

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val database = FirebaseDatabase.getInstance().getReference("users")
    private val db = FirebaseFirestore.getInstance()  // Firestore instance for saving data

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()

        // 🔹 Initialize UI elements
        val btnBack = findViewById<Button>(R.id.btnBack)
        val etFirstName = findViewById<EditText>(R.id.etFirstName)
        val etLastName = findViewById<EditText>(R.id.etLastName)
        val tvBirthdate = findViewById<TextView>(R.id.tvBirthdate)
        val etPhone = findViewById<EditText>(R.id.etPhone)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        // 🔹 Back button action
        btnBack.setOnClickListener {
            finish()  // Go back to the previous screen
        }

        // 🔹 Birthdate picker
        tvBirthdate.setOnClickListener {
            showDatePicker(tvBirthdate)
        }

        // 🔹 Register button action
        btnRegister.setOnClickListener {
            val firstName = etFirstName.text.toString().trim()
            val lastName = etLastName.text.toString().trim()
            val birthdate = tvBirthdate.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            // 🔥 Validate input fields
            if (validateInput(firstName, lastName, birthdate, phone, email, password)) {
                registerUser(firstName, lastName, birthdate, phone, email, password)
            } else {
                Toast.makeText(this, "Please fill all fields properly", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 🔹 Show DatePickerDialog
    private fun showDatePicker(tvBirthdate: TextView) {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePicker = DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
            val birthdate = "$selectedDay/${selectedMonth + 1}/$selectedYear"
            tvBirthdate.text = birthdate
        }, year, month, day)

        datePicker.show()
    }

    // 🔹 Validate user input
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
                birthdate != "Select Birthdate" &&
                phone.isNotEmpty() &&
                email.isNotEmpty() &&
                android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() &&
                password.length >= 6
    }

    // 🔹 Register the user with Firebase Authentication and save data to Firestore
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
                        val userMap = mapOf(
                            "firstName" to firstName,
                            "lastName" to lastName,
                            "birthdate" to birthdate,
                            "phone" to phone,
                            "email" to email
                        )

                        // Save to Firebase Realtime Database
                        database.child(uid).setValue(userMap)
                            .addOnSuccessListener {
                                // Save user data to Firestore for more structured data
                                val userData = hashMapOf(
                                    "firstName" to firstName,
                                    "lastName" to lastName,
                                    "birthdate" to birthdate,
                                    "phone" to phone,
                                    "email" to email
                                )

                                db.collection("userinfo").document(uid).set(userData)
                                    .addOnSuccessListener {
                                        Toast.makeText(this, "Registration Successful!", Toast.LENGTH_SHORT).show()

                                        // ✅ Navigate to Medical Activity for additional user info
                                        val intent = Intent(this, MedicalActivity::class.java).apply {
                                            putExtra("firstName", firstName)
                                            putExtra("lastName", lastName)
                                            putExtra("birthdate", birthdate)
                                            putExtra("phone", phone)
                                            putExtra("email", email)
                                        }
                                        startActivity(intent)
                                        finish()
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e("Firestore", "Error saving user data: ${e.message}")
                                    }
                            }
                            .addOnFailureListener { e ->
                                Log.e("Firebase", "Error saving user data: ${e.message}")
                            }
                    } else {
                        Toast.makeText(this, "Failed to retrieve user ID.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Log.e("Firebase", "Auth Error: ${task.exception?.message}")
                    Toast.makeText(this, "Registration failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }
}
