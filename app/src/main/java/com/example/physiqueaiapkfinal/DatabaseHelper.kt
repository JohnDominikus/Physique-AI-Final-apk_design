package com.example.physiqueaiapkfinal

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "PhysiqueAI.db"
        private const val DATABASE_VERSION = 4

        // Tables
        const val TABLE_USERS = "users"
        const val TABLE_PHYSICAL_INFO = "physical_info"
        const val TABLE_MEDICAL_ACTIVITIES = "medical_activities"

        // Common Columns
        const val COLUMN_ID = "id"
        const val COLUMN_FIREBASE_ID = "firebase_id"
        const val COLUMN_DATE = "date"
        const val COLUMN_SYNC_STATUS = "is_synced"

        // User Columns
        const val COLUMN_FIRST_NAME = "first_name"
        const val COLUMN_LAST_NAME = "last_name"
        const val COLUMN_BIRTHDATE = "birthdate"
        const val COLUMN_PHONE = "phone"
        const val COLUMN_EMAIL = "email"
        const val COLUMN_PASSWORD = "password"

        // Physical Info Columns
        const val COLUMN_BODY_LEVEL = "body_level"
        const val COLUMN_BODY_CLASSIFICATION = "body_classification"
        const val COLUMN_EXERCISE_ROUTINE = "exercise_routine"
        const val COLUMN_OTHER_INFO = "other_info"

        // Medical Activity Columns
        const val COLUMN_ALLERGIES = "allergies"
        const val COLUMN_MEDICAL_HISTORY = "medical_history"
        const val COLUMN_FRACTURES = "fractures"
        const val COLUMN_OTHER_CONDITIONS = "other_conditions"
    }

    override fun onCreate(db: SQLiteDatabase) {
        createTables(db)
    }

    private fun createTables(db: SQLiteDatabase) {
        try {
            // Users Table
            db.execSQL("""
                CREATE TABLE $TABLE_USERS (
                    $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COLUMN_FIREBASE_ID TEXT UNIQUE,
                    $COLUMN_FIRST_NAME TEXT,
                    $COLUMN_LAST_NAME TEXT,
                    $COLUMN_BIRTHDATE TEXT,
                    $COLUMN_PHONE TEXT,
                    $COLUMN_EMAIL TEXT UNIQUE,
                    $COLUMN_PASSWORD TEXT,
                    $COLUMN_SYNC_STATUS INTEGER DEFAULT 0
                )""")

            // Physical Info Table
            db.execSQL("""
                CREATE TABLE $TABLE_PHYSICAL_INFO (
                    $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COLUMN_FIREBASE_ID TEXT,
                    $COLUMN_BODY_LEVEL TEXT,
                    $COLUMN_BODY_CLASSIFICATION TEXT,
                    $COLUMN_EXERCISE_ROUTINE TEXT,
                    $COLUMN_OTHER_INFO TEXT,
                    $COLUMN_DATE TEXT,
                    $COLUMN_SYNC_STATUS INTEGER DEFAULT 0
                )""")

            // Medical Activities Table
            db.execSQL("""
                CREATE TABLE $TABLE_MEDICAL_ACTIVITIES (
                    $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COLUMN_FIREBASE_ID TEXT,
                    $COLUMN_ALLERGIES TEXT,
                    $COLUMN_MEDICAL_HISTORY TEXT,
                    $COLUMN_FRACTURES TEXT,
                    $COLUMN_OTHER_CONDITIONS TEXT,
                    $COLUMN_DATE TEXT,
                    $COLUMN_SYNC_STATUS INTEGER DEFAULT 0
                )""")

            Log.d("DatabaseHelper", "Tables created successfully")
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error creating tables: ${e.message}")
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 4) {
            try {
                listOf(TABLE_USERS, TABLE_PHYSICAL_INFO, TABLE_MEDICAL_ACTIVITIES).forEach { table ->
                    db.execSQL("ALTER TABLE $table ADD COLUMN $COLUMN_SYNC_STATUS INTEGER DEFAULT 0")
                }
                Log.d("DatabaseHelper", "Database upgraded to version $newVersion")
            } catch (e: Exception) {
                Log.e("DatabaseHelper", "Upgrade error: ${e.message}")
            }
        }
    }

    // region Sync Methods
    fun markAsSynced(table: String, localId: Long) {
        val db = writableDatabase
        try {
            db.execSQL(
                "UPDATE $table SET $COLUMN_SYNC_STATUS = 1 WHERE $COLUMN_ID = ?",
                arrayOf(localId.toString())
            )
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Mark sync error: ${e.message}")
        } finally {
            db.close()
        }
    }

    fun getUnsyncedRecords(table: String): Cursor {
        return readableDatabase.rawQuery(
            "SELECT * FROM $table WHERE $COLUMN_SYNC_STATUS = 0",
            null
        )
    }
    // endregion

    // region CRUD Operations
    fun insertUser(
        firebaseId: String,
        firstName: String,
        lastName: String,
        birthdate: String,
        phone: String,
        email: String,
        password: String
    ): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_FIREBASE_ID, firebaseId)
            put(COLUMN_FIRST_NAME, firstName)
            put(COLUMN_LAST_NAME, lastName)
            put(COLUMN_BIRTHDATE, birthdate)
            put(COLUMN_PHONE, phone)
            put(COLUMN_EMAIL, email)
            put(COLUMN_PASSWORD, password)
            put(COLUMN_SYNC_STATUS, 0)
        }

        return try {
            db.insertWithOnConflict(TABLE_USERS, null, values, SQLiteDatabase.CONFLICT_REPLACE).also {
                db.close()
            }
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "User insert error: ${e.message}")
            -1
        }
    }

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
            put(COLUMN_SYNC_STATUS, 0)
        }

        return try {
            val result = db.insert(TABLE_PHYSICAL_INFO, null, values)
            db.close()
            result != -1L  // Return true if insertion was successful
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Physical info insert error: ${e.message}")
            db.close()
            false  // Return false if insertion failed
        }
    }

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
            put(COLUMN_SYNC_STATUS, 0)
        }

        return try {
            val result = db.insert(TABLE_MEDICAL_ACTIVITIES, null, values)
            db.close()
            result != -1L  // Return true if insertion was successful
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Medical activity insert error: ${e.message}")
            db.close()
            false  // Return false if insertion failed
        }
    }

    fun userExists(email: String): Boolean {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT $COLUMN_ID FROM $TABLE_USERS WHERE $COLUMN_EMAIL = ?",
            arrayOf(email)
        )
        val exists = cursor.count > 0
        cursor.close()
        db.close()
        return exists
    }

    fun getUserId(email: String): String? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT $COLUMN_FIREBASE_ID FROM $TABLE_USERS WHERE $COLUMN_EMAIL = ?",
            arrayOf(email)
        )
        val userId = if (cursor.moveToFirst()) {
            cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FIREBASE_ID))
        } else null
        cursor.close()
        db.close()
        return userId
    }

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
    // endregion
}