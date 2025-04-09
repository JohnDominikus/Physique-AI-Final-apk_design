package com.example.physiqueaiapkfinal

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.physiqueaiapkfinal.DatabaseHelper.Companion.COLUMN_TASK_COMPLETED
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
                syncAllData()
                syncCloudToLocal()
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncManager", "Sync failed: ${e.message}", e)
            Result.retry()
        }
    }

    // 🚀 SYNC FROM LOCAL TO FIRESTORE
    private fun syncAllData() {
        syncUserInfo()
        syncTasks()
    }

    private fun syncUserInfo() {
        val cursor = dbHelper.getUnsyncedRecords()  // Assumes this method is implemented
        cursor?.use { safeCursor ->
            while (safeCursor.moveToNext()) {
                val docId = safeCursor.getStringOrNull(safeCursor.getColumnIndex(DatabaseHelper.COLUMN_FIREBASE_ID))
                    ?: continue

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
                        dbHelper.markAsSynced(docId)  // Assumes this method is implemented
                        Log.d("SyncManager", "User record $docId synced.")
                    }
                    .addOnFailureListener { e ->
                        Log.e("SyncManager", "User sync failed: ${e.message}")
                    }
            }
        }
    }

    private fun syncTasks() {
        val taskCursor = dbHelper.getAllTasks()  // Assumes this method is implemented
        taskCursor?.use { cursor ->
            while (cursor.moveToNext()) {
                val taskId = cursor.getStringOrNull(cursor.getColumnIndex(DatabaseHelper.COLUMN_TASK_ID)) ?: continue
                val firebaseUserId = cursor.getStringOrNull(cursor.getColumnIndex(DatabaseHelper.COLUMN_FIREBASE_ID)) ?: continue

                val taskData = hashMapOf(
                    "firebase_id" to firebaseUserId,
                    "task_name" to (cursor.getStringOrNull(cursor.getColumnIndex(DatabaseHelper.COLUMN_TASK_NAME)) ?: ""),
                    "task_workout_type" to (cursor.getStringOrNull(cursor.getColumnIndex(DatabaseHelper.COLUMN_TASK_WORKOUT_TYPE)) ?: ""),
                    "is_completed" to (cursor.getColumnIndex(COLUMN_TASK_COMPLETED).takeIf { it != -1 }?.let { cursor.getInt(it) == 0 } ?: false),
                    "task_datetime" to (cursor.getStringOrNull(cursor.getColumnIndex(DatabaseHelper.COLUMN_TASK_DATE)) ?: "")
                )


                firestore.collection("tasks").document(taskId)
                    .set(taskData)
                    .addOnSuccessListener {
                        Log.d("SyncManager", "Task $taskId synced.")
                    }
                    .addOnFailureListener { e ->
                        Log.e("SyncManager", "Failed to sync task $taskId: ${e.message}")
                    }
            }
        }
    }

    // 🔄 SYNC FROM FIRESTORE TO LOCAL SQLITE
    private fun syncCloudToLocal() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                syncFirestoreUserData()
                syncFirestoreTasks()
            } catch (e: Exception) {
                Log.e("SyncManager", "Cloud-to-local sync failed: ${e.message}", e)
            }
        }
    }

    private suspend fun syncFirestoreUserData() {
        val documents = firestore.collection("userinfo").get().await()
        val db = dbHelper.writableDatabase

        db.beginTransaction()
        try {
            for (doc in documents) {
                val data = doc.data
                val personalInfo = data["personalInfo"] as? Map<*, *> ?: continue
                val physicalInfo = data["physicalInfo"] as? Map<*, *> ?: emptyMap<String, Any>()
                val medicalInfo = data["medicalInfo"] as? Map<*, *> ?: emptyMap<String, Any>()

                val values = ContentValues().apply {
                    put(DatabaseHelper.COLUMN_FIREBASE_ID, doc.id)
                    personalInfo.forEach { put(it.key.toString(), it.value.toString()) }
                    physicalInfo.forEach { put(it.key.toString(), it.value.toString()) }
                    medicalInfo.forEach { put(it.key.toString(), it.value.toString()) }
                    put(DatabaseHelper.COLUMN_SYNC_STATUS, 1)
                }

                db.insertWithOnConflict(
                    DatabaseHelper.TABLE_USERINFO,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }

            db.setTransactionSuccessful()
            Log.d("SyncManager", "User data from cloud synced locally.")
        } catch (e: Exception) {
            Log.e("SyncManager", "User data sync failed: ${e.message}")
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    private suspend fun syncFirestoreTasks() {
        val documents = firestore.collection("tasks").get().await()
        val db = dbHelper.writableDatabase

        db.beginTransaction()
        try {
            for (doc in documents) {
                val data = doc.data

                val values = ContentValues().apply {
                    put(DatabaseHelper.COLUMN_TASK_ID, doc.id)
                    put(DatabaseHelper.COLUMN_FIREBASE_ID, data["firebase_id"]?.toString() ?: "")
                    put(DatabaseHelper.COLUMN_TASK_NAME, data["title"]?.toString() ?: "")
                    put(DatabaseHelper.COLUMN_TASK_WORKOUT_TYPE, data["task_workout_type"]?.toString() ?: "")
                    put(COLUMN_TASK_COMPLETED, if ((data["is_completed"] as? Boolean) == true) 1 else 0)
                    put(DatabaseHelper.COLUMN_TASK_DATE, data["task_datetime"]?.toString() ?: "")
                }

                db.insertWithOnConflict(
                    DatabaseHelper.TABLE_TASKS,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }

            db.setTransactionSuccessful()
            Log.d("SyncManager", "Tasks from cloud synced locally.")
        } catch (e: Exception) {
            Log.e("SyncManager", "Task sync failed: ${e.message}")
        } finally {
            db.endTransaction()
            db.close()
        }
    }

    // 🔧 Cursor Safe Getter
    private fun Cursor.getStringOrNull(index: Int): String? {
        return if (index != -1 && !isNull(index)) getString(index) else null
    }

    // 👤 Extractors
    private fun extractPersonalInfo(cursor: Cursor): Map<String, Any> = mapOf(
        "firstName" to (cursor.getStringOrNull(cursor.getColumnIndex(DatabaseHelper.COLUMN_FIRST_NAME)) ?: ""),
        "lastName" to (cursor.getStringOrNull(cursor.getColumnIndex(DatabaseHelper.COLUMN_LAST_NAME)) ?: ""),
        "birthdate" to (cursor.getStringOrNull(cursor.getColumnIndex(DatabaseHelper.COLUMN_BIRTHDATE)) ?: ""),
        "phone" to (cursor.getStringOrNull(cursor.getColumnIndex(DatabaseHelper.COLUMN_PHONE)) ?: ""),
        "email" to (cursor.getStringOrNull(cursor.getColumnIndex(DatabaseHelper.COLUMN_EMAIL)) ?: "")
    )

    private fun extractPhysicalInfo(cursor: Cursor): Map<String, Any> = mapOf(
        "bodyLevel" to (cursor.getStringOrNull(cursor.getColumnIndex(DatabaseHelper.COLUMN_BODY_LEVEL)) ?: ""),
        "bodyClassification" to (cursor.getStringOrNull(cursor.getColumnIndex(DatabaseHelper.COLUMN_BODY_CLASSIFICATION)) ?: ""),
        "exerciseRoutine" to (cursor.getStringOrNull(cursor.getColumnIndex(DatabaseHelper.COLUMN_EXERCISE_ROUTINE)) ?: ""),
        "otherInfo" to (cursor.getStringOrNull(cursor.getColumnIndex(DatabaseHelper.COLUMN_OTHER_INFO)) ?: "")
    )

    private fun extractMedicalInfo(cursor: Cursor): Map<String, Any> = mapOf(
        "allergies" to (cursor.getStringOrNull(cursor.getColumnIndex(DatabaseHelper.COLUMN_ALLERGIES)) ?: ""),
        "medicalHistory" to (cursor.getStringOrNull(cursor.getColumnIndex(DatabaseHelper.COLUMN_MEDICAL_HISTORY)) ?: ""),
        "fractures" to (cursor.getStringOrNull(cursor.getColumnIndex(DatabaseHelper.COLUMN_FRACTURES)) ?: ""),
        "otherConditions" to (cursor.getStringOrNull(cursor.getColumnIndex(DatabaseHelper.COLUMN_OTHER_CONDITIONS)) ?: "")
    )
}
