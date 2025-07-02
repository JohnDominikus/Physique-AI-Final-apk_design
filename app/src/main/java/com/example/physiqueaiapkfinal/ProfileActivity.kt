package com.example.physiqueaiapkfinal

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private var uid: String? = null
    private var imageUri: Uri? = null

    // Views
    private var profileImageView: ImageView? = null
    private var etFullName: TextInputEditText? = null
    private var etEmail: TextInputEditText? = null
    private var etPhone: TextInputEditText? = null
    private var etBirthdate: TextInputEditText? = null
    private var btnEdit: ImageButton? = null
    private var btnBack: ImageButton? = null
    private var btnChangePhoto: MaterialButton? = null
    private var btnSave: MaterialButton? = null
    private var btnCancel: MaterialButton? = null
    private var layoutActionButtons: LinearLayout? = null

    // Physical & Medical Views
    private var tvBodyLevel: TextView? = null
    private var tvBodyClassification: TextView? = null
    private var tvExerciseRoutine: TextView? = null
    private var tvOtherInfo: TextView? = null
    private var tvGymMode: TextView? = null

    private var tvCondition: TextView? = null
    private var tvMedication: TextView? = null
    private var tvAllergies: TextView? = null
    private var tvFractures: TextView? = null
    private var tvOtherConditions: TextView? = null

    private var btnEditPhysical: MaterialButton? = null
    private var btnEditMedical: MaterialButton? = null

    // Idinagdag – para i-centralize ang koleksiyong gagamitin
    private val USERS_COLLECTION = "userinfo"

    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            try {
                imageUri = it
                profileImageView?.setImageURI(it)
                uploadProfileImage()
            } catch (e: Exception) {
                Log.e("ProfileActivity", "Error setting image URI", e)
                Toast.makeText(this, "Error loading image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("ProfileActivity", "onCreate started")
        
        try {
            setContentView(R.layout.activity_profile)
            Log.d("ProfileActivity", "Layout set successfully")

            // Initialize Firebase services
            auth = FirebaseAuth.getInstance()
            firestore = FirebaseFirestore.getInstance()
            storage = FirebaseStorage.getInstance()
            uid = auth.currentUser?.uid
            Log.d("ProfileActivity", "Firebase initialized, uid: $uid")

            if (uid == null) {
                Log.w("ProfileActivity", "User not logged in")
                Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            initializeViews()
            setupListeners()
            fetchUserInfo()
            setEditMode(false) // Initial state is view mode
            
            Log.d("ProfileActivity", "onCreate completed successfully")
            
        } catch (e: Exception) {
            Log.e("ProfileActivity", "Critical error in onCreate", e)
            Toast.makeText(this, "Error loading profile: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun initializeViews() {
        try {
            Log.d("ProfileActivity", "Initializing views...")
            
            profileImageView = findViewById(R.id.profileImageView)
            etFullName = findViewById(R.id.etFullName)
            etEmail = findViewById(R.id.etEmail)
            etPhone = findViewById(R.id.etPhone)
            etBirthdate = findViewById(R.id.etBirthdate)
            btnEdit = findViewById(R.id.btnEdit)
            btnBack = findViewById(R.id.btnBack)
            btnChangePhoto = findViewById(R.id.btnChangePhoto)
            btnSave = findViewById(R.id.btnSave)
            btnCancel = findViewById(R.id.btnCancel)
            layoutActionButtons = findViewById(R.id.layoutActionButtons)
            
            // Physical
            tvBodyLevel = findViewById(R.id.tvBodyLevel)
            tvBodyClassification = findViewById(R.id.tvBodyClassification)
            tvExerciseRoutine = findViewById(R.id.tvExerciseRoutine)
            tvOtherInfo = findViewById(R.id.tvOtherInfo)
            tvGymMode = findViewById(R.id.tvGymMode)

            // Medical
            tvCondition = findViewById(R.id.tvCondition)
            tvMedication = findViewById(R.id.tvMedication)
            tvAllergies = findViewById(R.id.tvAllergies)
            tvFractures = findViewById(R.id.tvFractures)
            tvOtherConditions = findViewById(R.id.tvOtherConditions)

            btnEditPhysical = findViewById(R.id.btnEditPhysical)
            btnEditMedical = findViewById(R.id.btnEditMedical)
            
            Log.d("ProfileActivity", "All views initialized successfully")
            
        } catch (e: Exception) {
            Log.e("ProfileActivity", "Error initializing views", e)
            throw e
        }
    }

    private fun setupListeners() {
        try {
            Log.d("ProfileActivity", "Setting up listeners...")
            
            btnBack?.setOnClickListener { 
                Log.d("ProfileActivity", "Back button clicked")
                finish() 
            }
            
            btnEdit?.setOnClickListener { 
                Log.d("ProfileActivity", "Edit button clicked")
                setEditMode(true) 
            }
            
            btnCancel?.setOnClickListener {
                Log.d("ProfileActivity", "Cancel button clicked")
                setEditMode(false)
                fetchUserInfo() // Revert changes by fetching original data
            }
            
            btnSave?.setOnClickListener { 
                Log.d("ProfileActivity", "Save button clicked")
                saveUserInfo() 
            }
            
            btnChangePhoto?.setOnClickListener { 
                Log.d("ProfileActivity", "Change photo button clicked")
                imagePickerLauncher.launch("image/*") 
            }

            etBirthdate?.setOnClickListener {
                try {
                    if (etBirthdate?.isFocusable == true) {
                        Log.d("ProfileActivity", "Birthdate field clicked - showing date picker")
                        showDatePickerDialog()
                    }
                } catch (e: Exception) {
                    Log.e("ProfileActivity", "Error in birthdate click", e)
                }
            }
            
            btnEditPhysical?.setOnClickListener {
                startActivity(Intent(this, PhysicalActivity::class.java))
            }

            btnEditMedical?.setOnClickListener {
                uid?.let { id ->
                    val intent = Intent(this, MedicalActivity::class.java).apply {
                        putExtra("userId", id)
                    }
                    startActivity(intent)
                }
            }
            
            Log.d("ProfileActivity", "Listeners setup completed")
            
        } catch (e: Exception) {
            Log.e("ProfileActivity", "Error setting up listeners", e)
            throw e
        }
    }

    private fun setEditMode(isEditing: Boolean) {
        try {
            Log.d("ProfileActivity", "Setting edit mode: $isEditing")
            
            etFullName?.isFocusableInTouchMode = isEditing
            etEmail?.isFocusableInTouchMode = isEditing
            etPhone?.isFocusableInTouchMode = isEditing
            
            etBirthdate?.isFocusable = isEditing
            etBirthdate?.isFocusableInTouchMode = false // Keep it not directly editable
            etBirthdate?.isClickable = isEditing

            if (!isEditing) {
                etFullName?.clearFocus()
                etEmail?.clearFocus()
                etPhone?.clearFocus()
            }

            layoutActionButtons?.visibility = if (isEditing) View.VISIBLE else View.GONE
            btnChangePhoto?.visibility = if (isEditing) View.VISIBLE else View.GONE
            
            Log.d("ProfileActivity", "Edit mode set successfully")
            
        } catch (e: Exception) {
            Log.e("ProfileActivity", "Error setting edit mode", e)
        }
    }

    private fun fetchUserInfo() {
        try {
            Log.d("ProfileActivity", "Fetching user info for uid: $uid")
            uid?.let { userId ->
                firestore.collection(USERS_COLLECTION).document(userId).get()
                    .addOnSuccessListener { document ->
                        if (document != null && document.exists()) {
                            Log.d("ProfileActivity", "User document found")
                            
                            val personal = document.get("personalInfo") as? Map<*, *>
                            val first = personal?.get("firstName") as? String ?: ""
                            val last  = personal?.get("lastName")  as? String ?: ""
                            etFullName?.setText("$first $last".trim())
                            etEmail?.setText(personal?.get("email") as? String ?: "")
                            etPhone?.setText(personal?.get("phone") as? String ?: "")

                            val ts = personal?.get("birthdate")
                            val birthText = when (ts) {
                                is com.google.firebase.Timestamp -> {
                                    SimpleDateFormat("MMMM dd, yyyy", Locale.US).format(ts.toDate())
                                }
                                is java.util.Date -> {
                                    SimpleDateFormat("MMMM dd, yyyy", Locale.US).format(ts)
                                }
                                else -> ts?.toString() ?: ""
                            }
                            etBirthdate?.setText(birthText)

                            val photoUrl = document.getString("profilePhotoUrl")
                            if (!photoUrl.isNullOrEmpty()) {
                                Log.d("ProfileActivity", "Loading profile photo: $photoUrl")
                                try {
                                    profileImageView?.let { imageView ->
                                        Glide.with(this@ProfileActivity)
                                            .load(photoUrl)
                                            .circleCrop()
                                            .into(imageView)
                                    }
                                } catch (e: Exception) {
                                    Log.e("ProfileActivity", "Error loading profile photo", e)
                                }
                            }
                            
                            // Physical Info
                            val physical = document.get("physicalInfo") as? Map<*, *>
                            tvBodyLevel?.text = "Body Level: ${physical?.get("bodyLevel") ?: "-"}"
                            tvBodyClassification?.text = "Body Type: ${physical?.get("bodyClassification") ?: "-"}"
                            tvExerciseRoutine?.text = "Routine: ${physical?.get("exerciseRoutine") ?: "-"}"
                            tvOtherInfo?.text = "Other Info: ${physical?.get("otherInfo") ?: "-"}"
                            tvGymMode?.text = "Diet Mode: ${physical?.get("gymMode") ?: "-"}"

                            // Medical Info
                            val medical = document.get("medicalInfo") as? Map<*, *>
                            tvCondition?.text = "Condition: ${medical?.get("condition") ?: "-"}"
                            tvMedication?.text = "Medication: ${medical?.get("medication") ?: "-"}"
                            tvAllergies?.text = "Allergies: ${medical?.get("allergies") ?: "-"}"
                            tvFractures?.text = "Fractures: ${medical?.get("fractures") ?: "-"}"
                            tvOtherConditions?.text = "Other Conditions: ${medical?.get("otherConditions") ?: "-"}"
                            
                            Log.d("ProfileActivity", "User info loaded successfully")
                        } else {
                            Log.w("ProfileActivity", "No user document found")
                            Toast.makeText(this@ProfileActivity, "No profile data found.", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("ProfileActivity", "Failed to fetch user info", e)
                        Toast.makeText(this@ProfileActivity, "Failed to load profile: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        } catch (e: Exception) {
            Log.e("ProfileActivity", "Error in fetchUserInfo", e)
        }
    }

    private fun saveUserInfo() {
        try {
            Log.d("ProfileActivity", "Saving user info...")
            val fullName = etFullName?.text.toString().trim()
            val email    = etEmail?.text.toString().trim()
            val phone    = etPhone?.text.toString().trim()
            val birth    = etBirthdate?.text.toString().trim()

            if (fullName.isEmpty() || email.isEmpty()) {
                Toast.makeText(this, "Full name and email are required.", Toast.LENGTH_SHORT).show()
                return
            }

            // Hatiin ang pangalan para ma-store nang hiwalay
            val nameParts = fullName.split(" ", limit = 2)
            val first = nameParts.getOrNull(0) ?: ""
            val last  = nameParts.getOrNull(1) ?: ""

            val userUpdates = hashMapOf<String, Any>(
                "personalInfo.firstName" to first,
                "personalInfo.lastName"  to last,
                "personalInfo.email"     to email,
                "personalInfo.phone"     to phone,
                "personalInfo.birthdate" to birth  // pwede ring i-convert sa Date kung gusto mo
            )

            uid?.let { userId ->
                firestore.collection(USERS_COLLECTION).document(userId).update(userUpdates)
                    .addOnSuccessListener {
                        Log.d("ProfileActivity", "Profile updated successfully")
                        Toast.makeText(this@ProfileActivity, "Profile updated successfully.", Toast.LENGTH_SHORT).show()
                        setEditMode(false)
                    }
                    .addOnFailureListener { e ->
                        Log.e("ProfileActivity", "Failed to update profile", e)
                        Toast.makeText(this@ProfileActivity, "Failed to update profile: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        } catch (e: Exception) {
            Log.e("ProfileActivity", "Error in saveUserInfo", e)
            Toast.makeText(this, "Error saving profile: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun uploadProfileImage() {
        try {
            imageUri?.let { uri ->
                // kunin ang tamang extension (jpg/png/webp, etc.)
                val mime  = contentResolver.getType(uri) ?: "image/jpeg"
                val ext   = MimeTypeMap.getSingleton()
                              .getExtensionFromMimeType(mime) ?: "jpg"

                val storageRef = storage.reference.child("profile_images/$uid.jpg")
                Log.d("ProfileUpload", "TARGET PATH = ${storageRef.path}")

                storageRef.putFile(uri)
                    .addOnSuccessListener {
                        storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                            firestore.collection(USERS_COLLECTION)
                                .document(uid!!)
                                .update("profilePhotoUrl", downloadUrl.toString())
                                .addOnSuccessListener {
                                    Toast.makeText(
                                        this,
                                        "Profile photo updated!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    // refresh Dashboard photo via listener
                                }
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            this,
                            "Upload failed: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            } ?: Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDatePickerDialog() {
        try {
            Log.d("ProfileActivity", "Showing date picker dialog")
            
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            val datePickerDialog = DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
                try {
                    val selectedDate = Calendar.getInstance()
                    selectedDate.set(selectedYear, selectedMonth, selectedDay)
                    val sdf = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
                    etBirthdate?.setText(sdf.format(selectedDate.time))
                    Log.d("ProfileActivity", "Date selected successfully")
                } catch (e: Exception) {
                    Log.e("ProfileActivity", "Error setting selected date", e)
                }
            }, year, month, day)
            
            datePickerDialog.show()
            
        } catch (e: Exception) {
            Log.e("ProfileActivity", "Error showing date picker", e)
            Toast.makeText(this, "Error showing date picker: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
