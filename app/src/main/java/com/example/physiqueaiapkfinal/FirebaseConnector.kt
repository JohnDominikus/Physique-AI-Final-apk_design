package com.example.physiqueaiapkfinal.utils


import com.example.physiqueaiapkfinal.DatabaseHelper

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class FirebaseConnector(context: Context) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val database: DatabaseReference = FirebaseDatabase.getInstance().reference
    private val dbHelper = DatabaseHelper(context) // Ensuring encapsulation

    inner class Users {

        // 🔹 Register a new user
        fun register(
            firstName: String,
            lastName: String,
            birthdate: String,
            phone: String,
            email: String,
            password: String,
            onSuccess: () -> Unit,
            onFailure: (String) -> Unit
        ) {
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        val uid = user?.uid

                        if (uid != null) {
                            // 🔥 Store additional user info in Firebase Realtime Database
                            val userMap = mapOf(
                                "firstName" to firstName,
                                "lastName" to lastName,
                                "birthdate" to birthdate,
                                "phone" to phone,
                                "email" to email
                            )

                            database.child("users").child(uid).setValue(userMap)
                                .addOnSuccessListener {
                                    // ✅ Store user info in SQLite
                                    dbHelper.insertUser(
                                        uid, firstName, lastName, birthdate, phone, email, password
                                    )
                                    onSuccess()
                                }
                                .addOnFailureListener { e ->
                                    Log.e("FirebaseConnector", "Database Error: ${e.message}")
                                    onFailure(e.message ?: "Failed to save user data.")
                                }
                        } else {
                            onFailure("Failed to retrieve user ID.")
                        }

                    } else {
                        Log.e("FirebaseConnector", "Auth Error: ${task.exception?.message}")
                        onFailure(task.exception?.message ?: "Registration failed.")
                    }
                }
        }

        // 🔹 Retrieve User ID by Email
        fun getUserIdByEmail(email: String, onResult: (String?) -> Unit) {
            database.child("users").orderByChild("email").equalTo(email).get()
                .addOnSuccessListener { snapshot ->
                    val userId = snapshot.children.firstOrNull()?.key
                    onResult(userId)
                }
                .addOnFailureListener { e ->
                    Log.e("FirebaseConnector", "Error retrieving user ID: ${e.message}")
                    onResult(null)
                }
        }
    }
}
