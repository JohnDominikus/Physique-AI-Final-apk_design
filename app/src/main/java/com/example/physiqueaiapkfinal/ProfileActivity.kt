package com.example.physiqueaiapkfinal

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class ProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val firestore = FirebaseFirestore.getInstance()
    private var uid: String? = null

    // Views
    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvBirthdate: TextView
    private lateinit var tvPhone: TextView
    private lateinit var tvBMIStatus: TextView
    private lateinit var tvBMI: TextView

    private lateinit var editName: EditText
    private lateinit var editBirthdate: EditText
    private lateinit var editPhone: EditText

    private lateinit var editNameLayout: LinearLayout
    private lateinit var editBirthdateLayout: LinearLayout
    private lateinit var editPhoneLayout: LinearLayout

    private lateinit var editNameIcon: ImageView
    private lateinit var saveNameIcon: ImageView
    private lateinit var editBirthdateIcon: ImageView
    private lateinit var saveBirthdateIcon: ImageView
    private lateinit var editPhoneIcon: ImageView
    private lateinit var savePhoneIcon: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()
        uid = auth.currentUser?.uid

        if (uid == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Initialize views
        tvName = findViewById(R.id.tvName)
        tvEmail = findViewById(R.id.tvEmail)
        tvBirthdate = findViewById(R.id.tvBirthdate)
        tvPhone = findViewById(R.id.tvPhone)
        tvBMIStatus = findViewById(R.id.tvBMIStatus)
        tvBMI = findViewById(R.id.tvBMI)

        editName = findViewById(R.id.editName)
        editBirthdate = findViewById(R.id.editBirthdate)
        editPhone = findViewById(R.id.editPhone)

        editNameLayout = findViewById(R.id.editNameLayout)
        editBirthdateLayout = findViewById(R.id.editBirthdateLayout)
        editPhoneLayout = findViewById(R.id.editPhoneLayout)

        editNameIcon = findViewById(R.id.editNameIcon)
        saveNameIcon = findViewById(R.id.saveNameIcon)
        editBirthdateIcon = findViewById(R.id.editBirthdateIcon)
        saveBirthdateIcon = findViewById(R.id.saveBirthdateIcon)
        editPhoneIcon = findViewById(R.id.editPhoneIcon)
        savePhoneIcon = findViewById(R.id.savePhoneIcon)

        setupEditListeners()
        fetchUserInfoRealtime()
    }

    private fun setupEditListeners() {
        editNameIcon.setOnClickListener {
            editNameLayout.visibility = View.VISIBLE
            // Pre-fill current name if available
            val currentName = tvName.text.toString().removePrefix("Name: ").trim()
            editName.setText(currentName)
        }

        saveNameIcon.setOnClickListener {
            val fullName = editName.text.toString().trim()
            val parts = fullName.split(" ", limit = 2)

            val firstName = parts.getOrNull(0)?.trim().orEmpty()
            val lastName = parts.getOrNull(1)?.trim().orEmpty()

            if (firstName.isEmpty() || lastName.isEmpty()) {
                Toast.makeText(this, "Enter both first and last name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val displayFormatted = "$firstName $lastName"

            AlertDialog.Builder(this)
                .setTitle("Confirm Save")
                .setMessage("Save this name?\n\n$displayFormatted")
                .setPositiveButton("Yes") { _, _ ->
                    firestore.collection("userinfo").document(uid!!).update(
                        mapOf(
                            "personalInfo.firstName" to firstName,
                            "personalInfo.lastName" to lastName
                        )
                    )
                        .addOnSuccessListener {
                            Toast.makeText(this, "Name updated", Toast.LENGTH_SHORT).show()
                            editNameLayout.visibility = View.GONE
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Update failed: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        editBirthdateIcon.setOnClickListener {
            editBirthdateLayout.visibility = View.VISIBLE
            // Pre-fill current birthdate if available
            val currentBirthdate = tvBirthdate.text.toString().removePrefix("Birthdate: ").trim()
            editBirthdate.setText(currentBirthdate)

            val cal = Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, d ->
                val dateStr = "${(m + 1).toString().padStart(2, '0')}/${d.toString().padStart(2, '0')}/$y"
                editBirthdate.setText(dateStr)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        saveBirthdateIcon.setOnClickListener {
            val birthdate = editBirthdate.text.toString().trim()

            if (birthdate.isEmpty()) {
                Toast.makeText(this, "Birthdate cannot be empty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            firestore.collection("userinfo").document(uid!!).update("personalInfo.birthdate", birthdate)
                .addOnSuccessListener {
                    Toast.makeText(this, "Birthdate updated", Toast.LENGTH_SHORT).show()
                    editBirthdateLayout.visibility = View.GONE
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Update failed: ${it.message}", Toast.LENGTH_LONG).show()
                }
        }

        editPhoneIcon.setOnClickListener {
            editPhoneLayout.visibility = View.VISIBLE
            // Pre-fill current phone if available
            val currentPhone = tvPhone.text.toString().removePrefix("Phone: ").trim()
            editPhone.setText(currentPhone)
        }

        savePhoneIcon.setOnClickListener {
            var phone = editPhone.text.toString().trim()
            phone = when {
                phone.startsWith("09") && phone.length == 11 -> "+63${phone.substring(1)}"
                phone.startsWith("9") && phone.length == 10 -> "+63$phone"
                phone.startsWith("+63") && phone.length == 13 -> phone
                else -> {
                    Toast.makeText(this, "Invalid phone format. Use 09XXXXXXXXX", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
            }

            AlertDialog.Builder(this)
                .setTitle("Confirm Save")
                .setMessage("Save this number?\n\n$phone")
                .setPositiveButton("Yes") { _, _ ->
                    firestore.collection("userinfo").document(uid!!).update("personalInfo.phone", phone)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Phone updated", Toast.LENGTH_SHORT).show()
                            editPhoneLayout.visibility = View.GONE
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Failed: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    @SuppressLint("SetTextI18n", "DefaultLocale")
    private fun fetchUserInfoRealtime() {
        firestore.collection("userinfo").document(uid!!).addSnapshotListener { doc, error ->
            if (error != null) {
                Toast.makeText(this, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }

            if (doc != null && doc.exists()) {
                val personal = doc.get("personalInfo") as? Map<*, *>
                val firstName = personal?.get("firstName")?.toString() ?: ""
                val lastName = personal?.get("lastName")?.toString() ?: ""
                val email = personal?.get("email")?.toString() ?: "N/A"
                val birthdate = personal?.get("birthdate")?.toString() ?: "N/A"
                val phone = personal?.get("phone")?.toString() ?: "N/A"

                tvName.text = "Name: $firstName $lastName"
                tvEmail.text = "Email: $email"
                tvBirthdate.text = "Birthdate: $birthdate"
                tvPhone.text = "Phone: $phone"

                // Fetch BMI info safely
                val bmiInfo = doc.get("bmiInfo") as? Map<*, *>
                val bmiStatusRaw = bmiInfo?.get("status")?.toString() ?: "N/A"
                val bmiStatus = bmiStatusRaw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                val bmiValueRaw = bmiInfo?.get("bmi")?.toString()
                val bmiValue = bmiValueRaw?.toDoubleOrNull()?.let { String.format("%.1f", it) } ?: "N/A"

                tvBMIStatus.text = "BMI Status: $bmiStatus"
                tvBMI.text = "BMI: $bmiValue"

                tvBMIStatus.setTextColor(
                    when (bmiStatusRaw.lowercase()) {
                        "normal" -> getColor(R.color.teal_700)
                        "overweight" -> getColor(android.R.color.holo_orange_light)
                        "underweight" -> getColor(android.R.color.holo_blue_light)
                        "obese" -> getColor(android.R.color.holo_red_dark)
                        else -> getColor(android.R.color.darker_gray)
                    }
                )
            }
        }
    }
}
