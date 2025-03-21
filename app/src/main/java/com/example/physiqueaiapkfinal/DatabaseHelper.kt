package com.example.physiqueaiapkfinal

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues
import android.util.Log

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "PhysiqueAI.db"
        private const val DATABASE_VERSION = 3

        // Tables
        private const val TABLE_USERS = "users"
        private const val TABLE_PHYSICAL_ACTIVITIES = "physical_activities"
        private const val TABLE_MEDICAL_ACTIVITIES = "medical_activities"
        private const val TABLE_PHYSICAL_INFO = "physical_info"

        // Common Columns
        private const val COLUMN_ID = "id"
        private const val COLUMN_FIREBASE_ID = "firebase_id"
        private const val COLUMN_DATE = "date"

        // User Columns
        private const val COLUMN_FIRST_NAME = "first_name"
        private const val COLUMN_LAST_NAME = "last_name"
        private const val COLUMN_BIRTHDATE = "birthdate"
        private const val COLUMN_PHONE = "phone"
        private const val COLUMN_EMAIL = "email"
        private const val COLUMN_PASSWORD = "password"

        // Physical Info Columns
        private const val COLUMN_BODY_LEVEL = "body_level"
        private const val COLUMN_BODY_CLASSIFICATION = "body_classification"
        private const val COLUMN_EXERCISE_ROUTINE = "exercise_routine"
        private const val COLUMN_OTHER_INFO = "other_info"

        // Medical Activity Columns
        private const val COLUMN_ALLERGIES = "allergies"
        private const val COLUMN_MEDICAL_HISTORY = "medical_history"
        private const val COLUMN_FRACTURES = "fractures"
        private const val COLUMN_OTHER_CONDITIONS = "other_conditions"
    }

    // 🔥 Create Tables
    override fun onCreate(db: SQLiteDatabase) {
        try {
            db.execSQL("""
                CREATE TABLE $TABLE_USERS (
                    $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COLUMN_FIREBASE_ID TEXT UNIQUE,  
                    $COLUMN_FIRST_NAME TEXT,
                    $COLUMN_LAST_NAME TEXT,
                    $COLUMN_BIRTHDATE TEXT,
                    $COLUMN_PHONE TEXT,
                    $COLUMN_EMAIL TEXT UNIQUE,
                    $COLUMN_PASSWORD TEXT
                );
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE $TABLE_PHYSICAL_INFO (
                    $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COLUMN_FIREBASE_ID TEXT,
                    $COLUMN_BODY_LEVEL TEXT,
                    $COLUMN_BODY_CLASSIFICATION TEXT,
                    $COLUMN_EXERCISE_ROUTINE TEXT,
                    $COLUMN_OTHER_INFO TEXT,
                    $COLUMN_DATE TEXT
                );
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE $TABLE_MEDICAL_ACTIVITIES (
                    $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COLUMN_FIREBASE_ID TEXT,
                    $COLUMN_ALLERGIES TEXT,
                    $COLUMN_MEDICAL_HISTORY TEXT,
                    $COLUMN_FRACTURES TEXT,
                    $COLUMN_OTHER_CONDITIONS TEXT,
                    $COLUMN_DATE TEXT
                );
            """.trimIndent())

            Log.d("DatabaseHelper", "Tables created successfully")
        } catch (e: Exception) {
            Log.e("SQLiteError", "Error creating tables: ${e.message}")
        }
    }

    // 🔥 Upgrade Database
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            try {
                db.execSQL("ALTER TABLE $TABLE_PHYSICAL_INFO ADD COLUMN $COLUMN_BODY_LEVEL TEXT")
                db.execSQL("ALTER TABLE $TABLE_PHYSICAL_INFO ADD COLUMN $COLUMN_BODY_CLASSIFICATION TEXT")
                db.execSQL("ALTER TABLE $TABLE_PHYSICAL_INFO ADD COLUMN $COLUMN_EXERCISE_ROUTINE TEXT")
                db.execSQL("ALTER TABLE $TABLE_PHYSICAL_INFO ADD COLUMN $COLUMN_OTHER_INFO TEXT")

                Log.d("DatabaseHelper", "Database upgraded to version $newVersion")
            } catch (e: Exception) {
                Log.e("SQLiteError", "Error upgrading database: ${e.message}")
            }
        }
    }

    // 🔹 Insert User
    fun insertUser(
        firebaseId: String,
        firstName: String,
        lastName: String,
        birthdate: String,
        phone: String,
        email: String,
        password: String
    ) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_FIREBASE_ID, firebaseId)
            put(COLUMN_FIRST_NAME, firstName)
            put(COLUMN_LAST_NAME, lastName)
            put(COLUMN_BIRTHDATE, birthdate)
            put(COLUMN_PHONE, phone)
            put(COLUMN_EMAIL, email)
            put(COLUMN_PASSWORD, password)
        }

        try {
            db.insertWithOnConflict(TABLE_USERS, null, values, SQLiteDatabase.CONFLICT_IGNORE)
            Log.d("Database", "User inserted successfully")
        } catch (e: Exception) {
            Log.e("SQLiteError", "Error inserting user: ${e.message}")
        } finally {
            db.close()
        }
    }

    // 🔹 Insert Physical Info
    fun insertPhysicalInfo(
        userId: String,
        bodyLevel: String,
        bodyClassification: String,
        exerciseRoutine: String,
        otherInfo: String
    ): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_FIREBASE_ID, userId)
            put(COLUMN_BODY_LEVEL, bodyLevel)
            put(COLUMN_BODY_CLASSIFICATION, bodyClassification)
            put(COLUMN_EXERCISE_ROUTINE, exerciseRoutine)
            put(COLUMN_OTHER_INFO, otherInfo)
            put(COLUMN_DATE, System.currentTimeMillis().toString())
        }

        return try {
            val result = db.insert(TABLE_PHYSICAL_INFO, null, values)
            db.close()
            result != -1L
        } catch (e: Exception) {
            Log.e("SQLiteError", "Error inserting physical info: ${e.message}")
            db.close()
            false
        }
    }

    // 🔹 Insert Medical Activity
    fun insertMedicalActivity(
        userId: String,
        allergies: String,
        medicalHistory: String,
        fractures: String,
        otherConditions: String
    ): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_FIREBASE_ID, userId)
            put(COLUMN_ALLERGIES, allergies)
            put(COLUMN_MEDICAL_HISTORY, medicalHistory)
            put(COLUMN_FRACTURES, fractures)
            put(COLUMN_OTHER_CONDITIONS, otherConditions)
            put(COLUMN_DATE, System.currentTimeMillis().toString())
        }

        return try {
            val result = db.insert(TABLE_MEDICAL_ACTIVITIES, null, values)
            db.close()
            result != -1L
        } catch (e: Exception) {
            Log.e("SQLiteError", "Error inserting medical activity: ${e.message}")
            db.close()
            false
        }
    }

    // 🔥 Check if User Exists
    fun userExists(email: String): Boolean {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT 1 FROM $TABLE_USERS WHERE $COLUMN_EMAIL = ?",
            arrayOf(email)
        )

        val exists = cursor.count > 0
        cursor.close()
        db.close()

        return exists
    }

    // 🔹 Get User ID by Email
    fun getUserId(email: String): String? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT $COLUMN_FIREBASE_ID FROM $TABLE_USERS WHERE $COLUMN_EMAIL = ?",
            arrayOf(email)
        )

        val userId = if (cursor.moveToFirst()) {
            cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FIREBASE_ID))
        } else {
            null
        }

        cursor.close()
        db.close()
        return userId
    }

    // 🔹 Fetch Physical Info by User ID
    fun getPhysicalInfo(userId: String): Map<String, String?> {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_PHYSICAL_INFO WHERE $COLUMN_FIREBASE_ID = ?",
            arrayOf(userId)
        )

        val result = mutableMapOf<String, String?>()

        if (cursor.moveToFirst()) {
            result["bodyLevel"] = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_BODY_LEVEL))
            result["bodyClassification"] = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_BODY_CLASSIFICATION))
            result["exerciseRoutine"] = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_EXERCISE_ROUTINE))
            result["otherInfo"] = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_OTHER_INFO))
        }

        cursor.close()
        db.close()
        return result
    }
}
