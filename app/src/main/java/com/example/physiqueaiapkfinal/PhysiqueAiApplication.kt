package com.example.physiqueaiapkfinal

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

class PhysiqueAiApplication : Application() {

    private lateinit var crashPrefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        
        try {
            crashPrefs = getSharedPreferences("CRASH_REPORTS", Context.MODE_PRIVATE)
            Log.d("PhysiqueAI", "Application started successfully")
            
            // Set up enhanced global exception handler
            setupCrashHandler()
            
            // Log app startup info
            logStartupInfo()
            
        } catch (e: Exception) {
            Log.e("PhysiqueAI", "Application startup failed", e)
            ErrorHandler.handleCrash(this, e, "Application startup")
        }
    }
    
    private fun setupCrashHandler() {
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            try {
                Log.e("PhysiqueAI", "=== UNCAUGHT EXCEPTION ===")
                Log.e("PhysiqueAI", "Thread: ${thread.name}")
                Log.e("PhysiqueAI", "Exception: ${exception.javaClass.simpleName}")
                Log.e("PhysiqueAI", "Message: ${exception.message}")
                
                // Get full stack trace
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                exception.printStackTrace(pw)
                val stackTrace = sw.toString()
                
                Log.e("PhysiqueAI", "Stack trace: $stackTrace")
                
                // Save crash info for debugging
                saveCrashReport(exception, stackTrace)
                
                // Use ErrorHandler for comprehensive crash handling
                ErrorHandler.handleCrash(this, exception, "Uncaught exception in thread: ${thread.name}")
                
                // Identify common crash types
                identifyCrashType(exception)
                
            } catch (e: Exception) {
                Log.e("PhysiqueAI", "Error in crash handler", e)
            }
        }
    }
    
    private fun saveCrashReport(exception: Throwable, stackTrace: String) {
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val crashCount = crashPrefs.getInt("crash_count", 0) + 1
            
            crashPrefs.edit().apply {
                putInt("crash_count", crashCount)
                putString("last_crash_time", timestamp)
                putString("last_crash_type", exception.javaClass.simpleName)
                putString("last_crash_message", exception.message ?: "No message")
                putString("last_crash_stack", stackTrace)
                apply()
            }
            
            Log.e("PhysiqueAI", "Crash #$crashCount saved at $timestamp")
            
        } catch (e: Exception) {
            Log.e("PhysiqueAI", "Failed to save crash report", e)
        }
    }
    
    private fun identifyCrashType(exception: Throwable) {
        val crashType = when {
            exception is NullPointerException -> {
                "NULL_POINTER - Check findViewById calls and null safety"
            }
            exception is ClassCastException -> {
                "CLASS_CAST - Check type casting and view types"
            }
            exception is android.content.res.Resources.NotFoundException -> {
                "RESOURCE_NOT_FOUND - Check drawable/layout/string resources"
            }
            exception is IllegalStateException -> {
                "ILLEGAL_STATE - Check activity lifecycle and fragment states"
            }
            exception is SecurityException -> {
                "SECURITY - Check permissions and Firebase configuration"
            }
            exception.message?.contains("firebase", ignoreCase = true) == true -> {
                "FIREBASE_ERROR - Check Firebase initialization and network"
            }
            exception.message?.contains("layout", ignoreCase = true) == true -> {
                "LAYOUT_ERROR - Check XML layout files and view IDs"
            }
            exception.message?.contains("theme", ignoreCase = true) == true -> {
                "THEME_ERROR - Check theme attributes and styles"
            }
            else -> {
                "UNKNOWN - ${exception.javaClass.simpleName}"
            }
        }
        
        Log.e("PhysiqueAI", "=== CRASH DIAGNOSIS ===")
        Log.e("PhysiqueAI", "Type: $crashType")
        Log.e("PhysiqueAI", "Suggestion: ${getCrashSuggestion(crashType)}")
        Log.e("PhysiqueAI", "========================")
    }
    
    private fun getCrashSuggestion(crashType: String): String {
        return when {
            crashType.startsWith("NULL_POINTER") -> {
                "1. Check all findViewById calls\n2. Use safe null checks (?.)\n3. Initialize views in onCreate"
            }
            crashType.startsWith("RESOURCE_NOT_FOUND") -> {
                "1. Check drawable files exist\n2. Verify layout XML files\n3. Check string resources"
            }
            crashType.startsWith("FIREBASE_ERROR") -> {
                "1. Check internet connection\n2. Verify google-services.json\n3. Check Firebase rules"
            }
            crashType.startsWith("LAYOUT_ERROR") -> {
                "1. Check XML syntax\n2. Verify view IDs match\n3. Check theme attributes"
            }
            crashType.startsWith("THEME_ERROR") -> {
                "1. Check theme inheritance\n2. Verify color/style resources\n3. Check Material Design components"
            }
            else -> {
                "Check logs for specific error details and stack trace"
            }
        }
    }
    
    private fun logStartupInfo() {
        try {
            val crashCount = crashPrefs.getInt("crash_count", 0)
            val lastCrashTime = crashPrefs.getString("last_crash_time", "Never")
            val lastCrashType = crashPrefs.getString("last_crash_type", "None")
            
            Log.i("PhysiqueAI", "=== APP STARTUP INFO ===")
            Log.i("PhysiqueAI", "Previous crashes: $crashCount")
            Log.i("PhysiqueAI", "Last crash: $lastCrashTime")
            Log.i("PhysiqueAI", "Last crash type: $lastCrashType")
            Log.i("PhysiqueAI", "========================")
            
        } catch (e: Exception) {
            Log.e("PhysiqueAI", "Error logging startup info", e)
        }
    }
    
    // Public method to get crash history for debugging
    fun getCrashHistory(): String {
        return try {
            val crashCount = crashPrefs.getInt("crash_count", 0)
            val lastCrashTime = crashPrefs.getString("last_crash_time", "Never") ?: "Never"
            val lastCrashType = crashPrefs.getString("last_crash_type", "None") ?: "None"
            val lastCrashMessage = crashPrefs.getString("last_crash_message", "None") ?: "None"
            
            """
            === CRASH HISTORY ===
            Total Crashes: $crashCount
            Last Crash: $lastCrashTime
            Type: $lastCrashType
            Message: $lastCrashMessage
            =====================
            """.trimIndent()
            
        } catch (e: Exception) {
            "Error retrieving crash history: ${e.message}"
        }
    }
    
    // Method to clear crash history
    fun clearCrashHistory() {
        try {
            crashPrefs.edit().clear().apply()
            Log.i("PhysiqueAI", "Crash history cleared")
        } catch (e: Exception) {
            Log.e("PhysiqueAI", "Error clearing crash history", e)
        }
    }
    
    // Method to get error statistics from ErrorHandler
    fun getErrorStatistics(): String {
        return try {
            val stats = ErrorHandler.getErrorStats()
            val recentErrors = ErrorHandler.getRecentErrors(5)
            
            """
            === ERROR STATISTICS ===
            Total Errors: ${stats["total_errors"]}
            Recent Errors (24h): ${stats["recent_errors"]}
            Errors by Category: ${stats["errors_by_category"]}
            Errors by Severity: ${stats["errors_by_severity"]}
            
            Recent Errors:
            ${recentErrors.joinToString("\n") { "- ${it.message} (${it.category})" }}
            ======================
            """.trimIndent()
            
        } catch (e: Exception) {
            "Error retrieving error statistics: ${e.message}"
        }
    }
} 