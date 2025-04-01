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
        private const val DATABASE_VERSION = 5

        // Table Name
        const val TABLE_USERINFO = "userinfo"

        // Common Columns
        const val COLUMN_ID = "id"
        const val COLUMN_FIREBASE_ID = "firebase_id"
        const val COLUMN_SYNC_STATUS = "is_synced"
        const val COLUMN_DATE = "date"

        // User Info
        const val COLUMN_FIRST_NAME = "first_name"
        const val COLUMN_LAST_NAME = "last_name"
        const val COLUMN_BIRTHDATE = "birthdate"
        const val COLUMN_PHONE = "phone"
        const val COLUMN_EMAIL = "email"
        const val COLUMN_PASSWORD = "password"

        // Physical Info
        const val COLUMN_BODY_LEVEL = "body_level"
        const val COLUMN_BODY_CLASSIFICATION = "body_classification"
        const val COLUMN_EXERCISE_ROUTINE = "exercise_routine"
        const val COLUMN_OTHER_INFO = "other_info"

        // Medical Activities
        const val COLUMN_ALLERGIES = "allergies"
        const val COLUMN_MEDICAL_HISTORY = "medical_history"
        const val COLUMN_FRACTURES = "fractures"
        const val COLUMN_OTHER_CONDITIONS = "other_conditions"
    }

    override fun onCreate(db: SQLiteDatabase) {
        createUnifiedTable(db)
    }

    private fun createUnifiedTable(db: SQLiteDatabase) {
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_USERINFO (
                    $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COLUMN_FIREBASE_ID TEXT UNIQUE,
                    $COLUMN_FIRST_NAME TEXT,
                    $COLUMN_LAST_NAME TEXT,
                    $COLUMN_BIRTHDATE TEXT,
                    $COLUMN_PHONE TEXT,
                    $COLUMN_EMAIL TEXT UNIQUE,
                    $COLUMN_PASSWORD TEXT,
                    $COLUMN_BODY_LEVEL TEXT,
                    $COLUMN_BODY_CLASSIFICATION TEXT,
                    $COLUMN_EXERCISE_ROUTINE TEXT,
                    $COLUMN_OTHER_INFO TEXT,
                    $COLUMN_ALLERGIES TEXT,
                    $COLUMN_MEDICAL_HISTORY TEXT,
                    $COLUMN_FRACTURES TEXT,
                    $COLUMN_OTHER_CONDITIONS TEXT,
                    $COLUMN_DATE TEXT,
                    $COLUMN_SYNC_STATUS INTEGER DEFAULT 0
                )
                """
            )
            Log.d("DatabaseHelper", "Unified table created successfully")
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error creating unified table: ${e.message}")
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < newVersion) {
            db.execSQL("DROP TABLE IF EXISTS $TABLE_USERINFO")
            onCreate(db)
            Log.d("DatabaseHelper", "Database upgraded to version $newVersion")
        }
    }

    fun markAsSynced(localId: String) {
        writableDatabase.use { db ->
            try {
                val values = ContentValues().apply {
                    put(COLUMN_SYNC_STATUS, 1)
                }
                val rowsUpdated = db.update(TABLE_USERINFO, values, "$COLUMN_ID = ?", arrayOf(localId))
                Log.d("DatabaseHelper", "Record $localId marked as synced. Rows updated: $rowsUpdated")
            } catch (e: Exception) {
                Log.e("DatabaseHelper", "Mark sync error: ${e.message}")
            }
        }
    }

    fun getUnsyncedRecords(): Cursor? {
        val db = readableDatabase
        return try {
            db.rawQuery("SELECT * FROM $TABLE_USERINFO WHERE $COLUMN_SYNC_STATUS = 0", null)
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error retrieving unsynced records: ${e.message}")
            db.close()
            null
        }
    }

    fun insertOrUpdateUser(
        firebaseId: String,
        firstName: String,
        lastName: String,
        birthdate: String,
        phone: String,
        email: String,
        password: String,
        bodyLevel: String? = null,
        bodyClassification: String? = null,
        exerciseRoutine: String? = null,
        otherInfo: String? = null,
        allergies: String? = null,
        medicalHistory: String? = null,
        fractures: String? = null,
        otherConditions: String? = null
    ): Long {
        val db = writableDatabase
        var rowId: Long = -1

        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put(COLUMN_FIREBASE_ID, firebaseId)
                put(COLUMN_FIRST_NAME, firstName)
                put(COLUMN_LAST_NAME, lastName)
                put(COLUMN_BIRTHDATE, birthdate)
                put(COLUMN_PHONE, phone)
                put(COLUMN_EMAIL, email)
                put(COLUMN_PASSWORD, password)
                put(COLUMN_BODY_LEVEL, bodyLevel)
                put(COLUMN_BODY_CLASSIFICATION, bodyClassification)
                put(COLUMN_EXERCISE_ROUTINE, exerciseRoutine)
                put(COLUMN_OTHER_INFO, otherInfo)
                put(COLUMN_ALLERGIES, allergies)
                put(COLUMN_MEDICAL_HISTORY, medicalHistory)
                put(COLUMN_FRACTURES, fractures)
                put(COLUMN_OTHER_CONDITIONS, otherConditions)
                put(COLUMN_DATE, System.currentTimeMillis().toString())
                put(COLUMN_SYNC_STATUS, 0)
            }

            rowId = db.insertWithOnConflict(TABLE_USERINFO, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
            Log.d("DatabaseHelper", "User info inserted/updated successfully. Row ID: $rowId")
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Insert/update error: ${e.message}")
        } finally {
            db.endTransaction()
            db.close()
        }

        return rowId
    }

    fun getUser(email: String): Cursor? {
        val db = readableDatabase
        return try {
            db.rawQuery("SELECT * FROM $TABLE_USERINFO WHERE $COLUMN_EMAIL = ?", arrayOf(email))
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "Error retrieving user: ${e.message}")
            db.close()
            null
        }
    }

    fun getUserIdAndName(email: String): Pair<String?, String?> {
        val db = readableDatabase
        val query = "SELECT $COLUMN_FIREBASE_ID, $COLUMN_FIRST_NAME, $COLUMN_LAST_NAME FROM $TABLE_USERINFO WHERE $COLUMN_EMAIL = ?"

        return db.rawQuery(query, arrayOf(email)).use { cursor ->
            if (cursor.moveToFirst()) {
                val firebaseId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FIREBASE_ID))
                val fullName = "${cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FIRST_NAME))} ${cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LAST_NAME))}"
                Pair(firebaseId, fullName)
            } else {
                Pair(null, null)
            }
        }
    }

    fun clearAllRecords() {
        writableDatabase.use { db ->
            db.execSQL("DELETE FROM $TABLE_USERINFO")
            Log.d("DatabaseHelper", "All records deleted.")
        }
    }
}
