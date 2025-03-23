package com.example.physiqueaiapkfinal.utils

import android.content.Context
import android.database.Cursor
import android.util.Log
import com.example.physiqueaiapkfinal.DatabaseHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlin.math.pow
import android.os.Handler
import android.os.Looper

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val dbHelper = DatabaseHelper(context)
    private val handler = Handler(Looper.getMainLooper())

    // Retry logic for sync operations
    private fun retrySync(
        maxRetries: Int = 3,
        delayMillis: Long = 1000,
        syncAction: (Int) -> Unit
    ) {
        var attempt = 0

        fun execute() {
            syncAction(attempt)
            attempt++
            if (attempt < maxRetries) {
                handler.postDelayed({ execute() }, delayMillis * (2.0.pow(attempt).toLong()))
            }
        }
        execute()
    }

    // User Operations
    class UserOperations {

        fun registerUser(
            firstName: String,
            lastName: String,
            birthdate: String,
            phone: String,
            email: String,
            password: String,
            onComplete: (Boolean, String?) -> Unit
        ) {
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser
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

                        firestore.collection("userinfo").document(uid)
                            .set(userData, SetOptions.merge())
                            .addOnSuccessListener {
                                val isInserted = dbHelper.insertUser(uid, firstName, lastName, birthdate, phone, email, password)

                                if (isInserted != -1L) {
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
    }

    // Sync Operations
    class SyncOperations {

        fun syncAllData() {
            syncTable(DatabaseHelper.TABLE_USERS, "userinfo")
            syncTable(DatabaseHelper.TABLE_PHYSICAL_INFO, "physical_info")
            syncTable(DatabaseHelper.TABLE_MEDICAL_ACTIVITIES, "medical_activities")
        }

        private fun syncTable(localTable: String, cloudCollection: String) {
            dbHelper.getUnsyncedRecords(localTable).use { cursor ->
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_FIREBASE_ID))
                    val data = hashMapOf<String, Any>()

                    for (column in cursor.columnNames) {
                        if (column !in listOf(DatabaseHelper.COLUMN_ID, DatabaseHelper.COLUMN_SYNC_STATUS)) {
                            when (cursor.getType(cursor.getColumnIndexOrThrow(column))) {
                                Cursor.FIELD_TYPE_STRING -> data[column] = cursor.getString(cursor.getColumnIndexOrThrow(column))
                                Cursor.FIELD_TYPE_INTEGER -> data[column] = cursor.getLong(cursor.getColumnIndexOrThrow(column))
                                Cursor.FIELD_TYPE_FLOAT -> data[column] = cursor.getDouble(cursor.getColumnIndexOrThrow(column))
                            }
                        }
                    }

                    val firestoreData = when (localTable) {
                        DatabaseHelper.TABLE_PHYSICAL_INFO -> hashMapOf("physicalInfo" to data)
                        DatabaseHelper.TABLE_MEDICAL_ACTIVITIES -> hashMapOf("medicalInfo" to data)
                        else -> data
                    }

                    retrySync { attempt ->
                        firestore.collection("userinfo").document(docId)
                            .set(firestoreData, SetOptions.merge())
                            .addOnSuccessListener {
                                dbHelper.markAsSynced(localTable, cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_ID)))
                                Log.d("FirebaseSync", "$localTable record $docId synced")
                            }
                            .addOnFailureListener { e ->
                                Log.e("FirebaseSync", "Sync failed on attempt $attempt for $docId: ${e.message}")
                            }
                    }
                }
            }
        }
    }

