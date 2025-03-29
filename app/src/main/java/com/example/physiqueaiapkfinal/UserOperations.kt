package com.example.physiqueaiapkfinal.utils

import android.content.Context
import android.database.Cursor
import com.example.physiqueaiapkfinal.DatabaseHelper
import com.example.physiqueaiapkfinal.models.User

class UserOperations(context: Context) {

    private val dbHelper = DatabaseHelper(context)

    /**
     * ✅ Fetches a user by Firebase ID from local SQLite database.
     */
    fun getUserFromLocalDB(firebaseUserId: String): User? {
        val db = dbHelper.readableDatabase
        var user: User? = null

        val query = """
            SELECT * FROM ${DatabaseHelper.TABLE_USERINFO} 
            WHERE ${DatabaseHelper.COLUMN_FIREBASE_ID} = ?
        """.trimIndent()

        val cursor: Cursor? = db.rawQuery(query, arrayOf(firebaseUserId))

        cursor?.use {
            if (it.moveToFirst()) {
                user = User(
                    firebaseId = it.getString(it.getColumnIndexOrThrow(DatabaseHelper.COLUMN_FIREBASE_ID)),
                    firstName = it.getString(it.getColumnIndexOrThrow("firstName")),
                    lastName = it.getString(it.getColumnIndexOrThrow("lastName")),
                    email = it.getString(it.getColumnIndexOrThrow("email")),
                    bodyLevel = it.getString(it.getColumnIndexOrThrow("bodyLevel"))
                )
            }
        }

        cursor?.close()
        db.close()
        return user
    }

    /**
     * ✅ Insert or update a user in SQLite.
     */
    fun saveUserToLocalDB(user: User) {
        val db = dbHelper.writableDatabase
        val values = android.content.ContentValues().apply {
            put(DatabaseHelper.COLUMN_FIREBASE_ID, user.firebaseId)
            put("firstName", user.firstName)
            put("lastName", user.lastName)
            put("email", user.email)
            put("bodyLevel", user.bodyLevel)
        }

        db.insertWithOnConflict(
            DatabaseHelper.TABLE_USERINFO,
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )

        db.close()
    }
}
