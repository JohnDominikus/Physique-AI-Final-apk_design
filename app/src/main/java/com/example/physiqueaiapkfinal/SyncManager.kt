package com.example.physiqueaiapkfinal.utils

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.physiqueaiapkfinal.DatabaseHelper
import com.google.firebase.firestore.FirebaseFirestore

class SyncManager(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    private val firestore = FirebaseFirestore.getInstance()
    private val dbHelper = DatabaseHelper(context)
    private val connector = FirebaseConnector(context)

    override fun doWork(): Result {
        return try {
            // Sync local changes to Firestore
            connector.SyncOperations().syncAllData()

            // Sync Firestore changes to local
            syncCloudToLocal("users", DatabaseHelper.TABLE_USERS)
            syncCloudToLocal("physical_info", DatabaseHelper.TABLE_PHYSICAL_INFO)
            syncCloudToLocal("medical_activities", DatabaseHelper.TABLE_MEDICAL_ACTIVITIES)

            Result.success()
        } catch (e: Exception) {
            Log.e("SyncManager", "Sync failed: ${e.message}")
            Result.retry()
        }
    }

    private fun syncCloudToLocal(cloudCollection: String, localTable: String) {
        firestore.collection(cloudCollection).get()
            .addOnSuccessListener { documents ->
                documents.forEach { doc ->
                    val contentValues = ContentValues().apply {
                        put(DatabaseHelper.COLUMN_FIREBASE_ID, doc.id)
                        doc.data.forEach { (key, value) ->
                            when (value) {
                                is String -> put(key, value)
                                is Long -> put(key, value)
                                is Double -> put(key, value.toFloat())
                            }
                        }
                        put(DatabaseHelper.COLUMN_SYNC_STATUS, 1)
                    }

                    dbHelper.writableDatabase.insertWithOnConflict(
                        localTable,
                        null,
                        contentValues,
                        SQLiteDatabase.CONFLICT_REPLACE
                    )
                }
            }
    }
}