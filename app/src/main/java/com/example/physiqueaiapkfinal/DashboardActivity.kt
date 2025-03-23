package com.example.physiqueaiapkfinal.utils

import android.content.Context
import android.database.Cursor
import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import com.example.physiqueaiapkfinal.DatabaseHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class FirebaseConnector(context: Context) : Parcelable {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val dbHelper = DatabaseHelper(context)

    constructor(parcel: Parcel) : this(TODO("context")) {
    }

    // User Operations
    inner class UserOperations {
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
                        val uid = user?.uid ?: run {
                            onComplete(false, "User authentication failed")
                            return@addOnCompleteListener
                        }

                        // Prepare user data for Firestore
                        val userData = hashMapOf(
                            "personalInfo" to hashMapOf(
                                "firstName" to firstName,
                                "lastName" to lastName,
                                "birthdate" to birthdate,
                                "phone" to phone,
                                "email" to email
                            ),
                            "physicalInfo" to hashMapOf<String, Any>(),  // Empty for now
                            "medicalInfo" to hashMapOf<String, Any>()   // Empty for now
                        )

                        // Save to Firestore
                        firestore.collection("userinfo").document(uid)
                            .set(userData, SetOptions.merge())
                            .addOnSuccessListener {
                                // Save to SQLite
                                val isInserted = dbHelper.insertUser(uid, firstName, lastName, birthdate, phone, email, password)

                                if (isInserted != -1L) {  // Check if SQLite insertion was successful
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
    inner class SyncOperations {
        fun syncAllData() {
            syncTable(DatabaseHelper.TABLE_USERS, "userinfo")
            syncTable(DatabaseHelper.TABLE_PHYSICAL_INFO, "physical_info")
            syncTable(DatabaseHelper.TABLE_MEDICAL_ACTIVITIES, "medical_activities")
        }

        private fun syncTable(localTable: String, cloudCollection: String) {
            val cursor = dbHelper.getUnsyncedRecords(localTable)
            while (cursor.moveToNext()) {
                val docId = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_FIREBASE_ID))
                val data = hashMapOf<String, Any>()

                // Map all columns except internal IDs and sync status
                for (column in cursor.columnNames) {
                    if (column !in listOf(
                            DatabaseHelper.COLUMN_ID,
                            DatabaseHelper.COLUMN_SYNC_STATUS
                        )
                    ) {
                        when (cursor.getType(cursor.getColumnIndexOrThrow(column))) {
                            Cursor.FIELD_TYPE_STRING -> data[column] = cursor.getString(cursor.getColumnIndexOrThrow(column))
                            Cursor.FIELD_TYPE_INTEGER -> data[column] = cursor.getLong(cursor.getColumnIndexOrThrow(column))
                            Cursor.FIELD_TYPE_FLOAT -> data[column] = cursor.getDouble(cursor.getColumnIndexOrThrow(column))
                        }
                    }
                }

                // Save to Firestore under the appropriate nested field
                val firestoreData = when (localTable) {
                    DatabaseHelper.TABLE_PHYSICAL_INFO -> hashMapOf("physicalInfo" to data)
                    DatabaseHelper.TABLE_MEDICAL_ACTIVITIES -> hashMapOf("medicalInfo" to data)
                    else -> data
                }

                firestore.collection("userinfo").document(docId)
                    .set(firestoreData, SetOptions.merge())
                    .addOnSuccessListener {
                        // Mark as synced in SQLite
                        dbHelper.markAsSynced(localTable, cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COLUMN_ID)))
                        Log.d("FirebaseSync", "$localTable record $docId synced")
                    }
                    .addOnFailureListener { e ->
                        Log.e("FirebaseSync", "Sync failed for $docId: ${e.message}")
                    }
            }
            cursor.close()
        }
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {

    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<FirebaseConnector> {
        override fun createFromParcel(parcel: Parcel): FirebaseConnector {
            return FirebaseConnector(parcel)
        }

        override fun newArray(size: Int): Array<FirebaseConnector?> {
            return arrayOfNulls(size)
        }
    }
}