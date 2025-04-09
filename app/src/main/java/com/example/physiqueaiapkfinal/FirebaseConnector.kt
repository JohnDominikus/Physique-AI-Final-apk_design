package com.example.physiqueaiapkfinal.utils

import android.content.Context
import android.database.Cursor
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.physiqueaiapkfinal.DatabaseHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlin.math.pow

// --- Firebase Initialization ---
object FirebaseManager {
    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    val handler = Handler(Looper.getMainLooper())
}

// --- Retry Logic ---
fun retrySync(
    maxRetries: Int = 3,
    delayMillis: Long = 1000,
    syncAction: (Int) -> Unit
) {
    var attempt = 0

    fun execute() {
        syncAction(attempt)
        attempt++
        if (attempt < maxRetries) {
            FirebaseManager.handler.postDelayed({ execute() }, delayMillis * (2.0.pow(attempt).toLong()))
        }
    }
    execute()
}

// --- Combined User and Sync Operations ---
class UserOperations(private val context: Context) {

    private val dbHelper = DatabaseHelper(context)

    // Registers a new user with Firebase and stores data locally
    fun registerUser(
        firstName: String,
        lastName: String,
        birthdate: String,
        phone: String,
        email: String,
        password: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        FirebaseManager.auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = FirebaseManager.auth.currentUser
                    if (user == null) {
                        onComplete(false, "Failed to retrieve user UID after authentication.")
                        return@addOnCompleteListener
                    }

                    val uid = user.uid

                    val userData = hashMapOf(
                        "personalInfo" to hashMapOf(
                            "firstName" to firstName,
                            "lastName" to lastName,
                            "birthdate" to birthdate,
                            "phone" to phone,
                            "email" to email
                        ),
                        "physicalInfo" to hashMapOf<String, Any>(),
                        "medicalInfo" to hashMapOf<String, Any>()
                    )

                    FirebaseManager.firestore.collection("userinfo").document(uid)
                        .set(userData, SetOptions.merge())
                        .addOnSuccessListener {
                            val rowId = dbHelper.insertOrUpdateUser(
                                firebaseId = uid,
                                firstName = firstName,
                                lastName = lastName,
                                birthdate = birthdate,
                                phone = phone,
                                email = email,
                                password = password,
                                bodyLevel = null,
                                bodyClassification = null,
                                exerciseRoutine = null,
                                otherInfo = null,
                                allergies = null,
                                medicalHistory = null,
                                fractures = null,
                                otherConditions = null
                            )

                            if (rowId != -1L) {
                                onComplete(true, null)
                            } else {
                                onComplete(false, "Failed to save user data locally")
                            }
                        }
                        .addOnFailureListener { e ->
                            onComplete(false, "Firestore error: ${e.message}")
                        }
                } else {
                    onComplete(false, task.exception?.message ?: "Registration failed")
                }
            }
    }

    // --- Sync Operations ---
    /**
     * Syncs all unsynced user records from SQLite to Firestore.
     */
    fun syncAllData() {
        syncTable(DatabaseHelper.TABLE_USERINFO)
    }

    /**
     * Syncs data from the local SQLite table to Firestore.
     */
    private fun syncTable(localTable: String) {
        val cursor: Cursor? = dbHelper.getUnsyncedRecords()

        cursor?.use { safeCursor ->
            if (safeCursor.count == 0) {
                Log.d("Sync", "No unsynced records in $localTable.")
                return
            }

            while (safeCursor.moveToNext()) {
                val firebaseIdIndex = safeCursor.getColumnIndex(DatabaseHelper.COLUMN_FIREBASE_ID)
                val docId = safeCursor.getStringOrNull(firebaseIdIndex) ?: continue
                val data = extractDataFromCursor(safeCursor)

                retrySync { attempt ->
                    FirebaseManager.firestore.collection("userinfo").document(docId)
                        .set(data, SetOptions.merge())
                        .addOnSuccessListener {
                            val idIndex = safeCursor.getColumnIndex(DatabaseHelper.COLUMN_ID)
                            val recordId = safeCursor.getLongOrNull(idIndex)
                            recordId?.let {
                                dbHelper.markAsSynced(it.toString())
                                Log.d("FirebaseSync", "Record $docId synced successfully")
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("FirebaseSync", "Sync failed on attempt $attempt for $docId: ${e.message}")
                        }
                }
            }
        } ?: Log.e("Sync", "Failed to fetch cursor or cursor is null")
    }

    /**
     * Extracts data from the cursor into a HashMap for Firestore.
     */
    private fun extractDataFromCursor(cursor: Cursor): HashMap<String, Any> {
        val data = hashMapOf<String, Any>()
        for (column in cursor.columnNames) {
            if (column !in listOf(DatabaseHelper.COLUMN_ID, DatabaseHelper.COLUMN_SYNC_STATUS)) {
                val index = cursor.getColumnIndexOrThrow(column) // Throws exception if column is not found
                when (cursor.getType(index)) {
                    Cursor.FIELD_TYPE_STRING -> {
                        cursor.getStringOrNull(index)?.let { data[column] = it }
                    }
                    Cursor.FIELD_TYPE_INTEGER -> {
                        cursor.getLongOrNull(index)?.let { data[column] = it }
                    }
                    Cursor.FIELD_TYPE_FLOAT -> {
                        cursor.getDoubleOrNull(index)?.let { data[column] = it }
                    }
                }
            }
        }
        return data
    }

    /**
     * Safely retrieves a nullable string from the cursor.
     */
    private fun Cursor.getStringOrNull(index: Int): String? {
        return if (index != -1 && !isNull(index)) getString(index) else null
    }

    /**
     * Safely retrieves a nullable long from the cursor.
     */
    private fun Cursor.getLongOrNull(index: Int): Long? {
        return if (index != -1 && !isNull(index)) getLong(index) else null
    }

    /**
     * Safely retrieves a nullable double from the cursor.
     */
    private fun Cursor.getDoubleOrNull(index: Int): Double? {
        return if (index != -1 && !isNull(index)) getDouble(index) else null
    }
}
