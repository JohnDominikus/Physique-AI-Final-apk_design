package com.example.physiqueaiapkfinal.utils

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.physiqueaiapkfinal.DatabaseHelper
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SyncManager(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {

    private val firestore = FirebaseFirestore.getInstance()
    private val dbHelper = DatabaseHelper(context)

    override fun doWork(): Result {
        return try {
            CoroutineScope(Dispatchers.IO).launch {
                syncAllData()           // Local to Firestore sync
                syncCloudToLocal()      // Firestore to local SQLite sync
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncManager", "Sync failed: ${e.message}", e)
            Result.retry()
        }
    }

    // ✅ Sync all local SQLite data to Firestore
    private fun syncAllData() {
        val cursor = dbHelper.getUnsyncedRecords()

        cursor?.use { safeCursor ->
            if (safeCursor.count == 0) {
                Log.d("SyncManager", "No unsynced records to sync.")
                return
            }

            while (safeCursor.moveToNext()) {
                val docId = safeCursor.getStringOrNull(safeCursor.getColumnIndex(DatabaseHelper.COLUMN_FIREBASE_ID))
                if (docId == null) {
                    Log.e("SyncManager", "Invalid Firebase ID. Skipping record.")
                    continue
                }

                // Extracting data
                val personalInfo = extractPersonalInfo(safeCursor)
                val physicalInfo = extractPhysicalInfo(safeCursor)
                val medicalInfo = extractMedicalInfo(safeCursor)

                val data = hashMapOf(
                    "personalInfo" to personalInfo,
                    "physicalInfo" to physicalInfo,
                    "medicalInfo" to medicalInfo
                )

                firestore.collection("userinfo").document(docId)
                    .set(data)
                    .addOnSuccessListener {
                        dbHelper.markAsSynced(docId)
                        Log.d("SyncManager", "Record $docId synced successfully.")
                    }
                    .addOnFailureListener { e ->
                        Log.e("SyncManager", "Failed to sync $docId: ${e.message}", e)
                    }
            }
        } ?: Log.e("SyncManager", "Failed to fetch unsynced records.")
    }

    // ✅ Sync Firestore data to local SQLite
    private fun syncCloudToLocal() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = dbHelper.writableDatabase
                val documents = firestore.collection("userinfo").get().await()

                db.beginTransaction()
                try {
                    for (doc in documents) {
                        val data = doc.data

                        val personalInfo = data["personalInfo"] as? Map<*, *> ?: emptyMap<String, Any>()
                        val physicalInfo = data["physicalInfo"] as? Map<*, *> ?: emptyMap<String, Any>()
                        val medicalInfo = data["medicalInfo"] as? Map<*, *> ?: emptyMap<String, Any>()

                        val userValues = ContentValues().apply {
                            put(DatabaseHelper.COLUMN_FIREBASE_ID, doc.id)

                            // Insert Personal Info
                            personalInfo.forEach { (key, value) ->
                                put(key.toString(), value.toString())
                            }

                            // Insert Physical Info
                            physicalInfo.forEach { (key, value) ->
                                put(key.toString(), value.toString())
                            }

                            // Insert Medical Info
                            medicalInfo.forEach { (key, value) ->
                                put(key.toString(), value.toString())
                            }

                            put(DatabaseHelper.COLUMN_SYNC_STATUS, 1)
                        }

                        db.insertWithOnConflict(
                            DatabaseHelper.TABLE_USERINFO,
                            null,
                            userValues,
                            SQLiteDatabase.CONFLICT_REPLACE
                        )
                    }

                    db.setTransactionSuccessful()
                    Log.d("SyncManager", "Cloud data successfully synced to local SQLite.")
                } catch (e: Exception) {
                    Log.e("SyncManager", "Failed to insert cloud data: ${e.message}", e)
                } finally {
                    db.endTransaction()
                    db.close()
                }
            } catch (e: Exception) {
                Log.e("SyncManager", "Failed to sync cloud data: ${e.message}", e)
            }
        }
    }

    // ✅ Extract Personal Info from the cursor
    private fun extractPersonalInfo(cursor: android.database.Cursor): Map<String, Any> {
        return mapOf(
            "firstName" to (cursor.getStringOrNull(cursor.getColumnIndex(DatabaseHelper.COLUMN_FIRST_NAME)) ?: ""),
            "lastName" to (cursor.getStringOrNull(cursor.getColumnIndex(DatabaseHelper.COLUMN_LAST_NAME)) ?: ""),
            "birthdate" to (cursor.getStringOrNull(cursor.getColumnIndex(DatabaseHelper.COLUMN_BIRTHDATE)) ?: ""),
            "phone" to (cursor.getStringOrNull(cursor.getColumnIndex(DatabaseHelper.COLUMN_PHONE)) ?: ""),
            "email" to (cursor.getStringOrNull(cursor.getColumnIndex(DatabaseHelper.COLUMN_EMAIL)) ?: "")
        )
    }

    // ✅ Extract Physical Info from the cursor
    private fun extractPhysicalInfo(cursor: android.database.Cursor): Map<String, Any> {
        return mapOf(
            "bodyLevel" to (cursor.getStringOrNull(cursor.getColumnIndex(DatabaseHelper.COLUMN_BODY_LEVEL)) ?: ""),
            "bodyClassification" to (cursor.getStringOrNull(cursor.getColumnIndex(DatabaseHelper.COLUMN_BODY_CLASSIFICATION)) ?: ""),
            "exerciseRoutine" to (cursor.getStringOrNull(cursor.getColumnIndex(DatabaseHelper.COLUMN_EXERCISE_ROUTINE)) ?: ""),
            "otherInfo" to (cursor.getStringOrNull(cursor.getColumnIndex(DatabaseHelper.COLUMN_OTHER_INFO)) ?: "")
        )
    }

    // ✅ Extract Medical Info from the cursor
    private fun extractMedicalInfo(cursor: android.database.Cursor): Map<String, Any> {
        return mapOf(
            "allergies" to (cursor.getStringOrNull(cursor.getColumnIndex(DatabaseHelper.COLUMN_ALLERGIES)) ?: ""),
            "medicalHistory" to (cursor.getStringOrNull(cursor.getColumnIndex(DatabaseHelper.COLUMN_MEDICAL_HISTORY)) ?: ""),
            "fractures" to (cursor.getStringOrNull(cursor.getColumnIndex(DatabaseHelper.COLUMN_FRACTURES)) ?: ""),
            "otherConditions" to (cursor.getStringOrNull(cursor.getColumnIndex(DatabaseHelper.COLUMN_OTHER_CONDITIONS)) ?: "")
        )
    }

    // ✅ Extension function to safely get a nullable string from the cursor
    private fun android.database.Cursor.getStringOrNull(index: Int): String? {
        return if (index != -1 && !isNull(index)) getString(index) else null
    }
}
