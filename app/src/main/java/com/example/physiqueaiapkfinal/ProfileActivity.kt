package com.example.physiqueaiapkfinal

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class ProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var uid: String? = null

    // Views from the modernized layout
    private lateinit var etFullName: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPhone: TextInputEditText
    private lateinit var etBirthdate: TextInputEditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    private lateinit var btnBack: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        uid = auth.currentUser?.uid

        if (uid == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Initialize views from modernized layout
        etFullName = findViewById(R.id.etFullName)
        etEmail = findViewById(R.id.etEmail)
        etPhone = findViewById(R.id.etPhone)
        etBirthdate = findViewById(R.id.etBirthdate)
        btnSave = findViewById(R.id.btnSave)
        btnCancel = findViewById(R.id.btnCancel)
        btnBack = findViewById(R.id.btnBack)

        setupListeners()
        fetchUserInfoRealtime()
    }

    private fun setupListeners() {
        // Back button
        btnBack.setOnClickListener {
            finish()
        }

        // Birthdate picker
        etBirthdate.setOnClickListener {
            showDatePicker()
        }

        // Save button
        btnSave.setOnClickListener {
            saveUserInfo()
        }

        // Cancel button
        btnCancel.setOnClickListener {
            fetchUserInfoRealtime() // Reset to original values
            Toast.makeText(this, "Changes cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val formattedDate = "${month + 1}/$dayOfMonth/$year"
                etBirthdate.setText(formattedDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private fun saveUserInfo() {
        val fullName = etFullName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val phone = etPhone.text.toString().trim()
        val birthdate = etBirthdate.text.toString().trim()

        // Validate input
        if (fullName.isEmpty()) {
            Toast.makeText(this, "Please enter your full name", Toast.LENGTH_SHORT).show()
            return
        }

        val nameParts = fullName.split(" ", limit = 2)
        val firstName = nameParts.getOrNull(0)?.trim().orEmpty()
        val lastName = nameParts.getOrNull(1)?.trim().orEmpty()

        if (firstName.isEmpty() || lastName.isEmpty()) {
            Toast.makeText(this, "Please enter both first and last name", Toast.LENGTH_SHORT).show()
            return
        }

        // Format phone number
        val formattedPhone = formatPhoneNumber(phone)
        if (formattedPhone == null && phone.isNotEmpty()) {
            Toast.makeText(this, "Invalid phone format. Use 09XXXXXXXXX", Toast.LENGTH_LONG).show()
            return
        }

        // Show confirmation dialog
        AlertDialog.Builder(this)
            .setTitle("Confirm Changes")
            .setMessage("Save these changes to your profile?")
            .setPositiveButton("Save") { _, _ ->
                updateFirestore(firstName, lastName, email, formattedPhone ?: "", birthdate)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun formatPhoneNumber(phone: String): String? {
        return when {
            phone.isEmpty() -> ""
            phone.startsWith("09") && phone.length == 11 -> "+63${phone.substring(1)}"
            phone.startsWith("9") && phone.length == 10 -> "+63$phone"
            phone.startsWith("+63") && phone.length == 13 -> phone
            else -> null
        }
    }

    private fun updateFirestore(firstName: String, lastName: String, email: String, phone: String, birthdate: String) {
        val updates = mapOf(
            "personalInfo.firstName" to firstName,
            "personalInfo.lastName" to lastName,
            "personalInfo.email" to email,
            "personalInfo.phone" to phone,
            "personalInfo.birthdate" to birthdate
        )

        firestore.collection("userinfo").document(uid!!)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Update failed: ${exception.message}", Toast.LENGTH_LONG).show()
            }
    }

    @SuppressLint("SetTextI18n")
    private fun fetchUserInfoRealtime() {
        firestore.collection("userinfo").document(uid!!)
            .addSnapshotListener { document, error ->
                if (error != null) {
                    Toast.makeText(this, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (document != null && document.exists()) {
                    val personal = document.get("personalInfo") as? Map<*, *>
                    
                    val firstName = personal?.get("firstName")?.toString() ?: ""
                    val lastName = personal?.get("lastName")?.toString() ?: ""
                    val email = personal?.get("email")?.toString() ?: ""
                    val phone = personal?.get("phone")?.toString() ?: ""
                    val birthdate = personal?.get("birthdate")?.toString() ?: ""

                    // Update the input fields
                    etFullName.setText("$firstName $lastName".trim())
                    etEmail.setText(email)
                    etPhone.setText(phone)
                    etBirthdate.setText(birthdate)
                }
            }
    }
}
