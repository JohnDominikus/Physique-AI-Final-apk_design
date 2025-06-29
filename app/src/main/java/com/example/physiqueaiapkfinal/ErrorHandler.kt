package com.example.physiqueaiapkfinal

import android.content.Context
import android.util.Log
import android.widget.Toast
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Comprehensive error handling utility for the PhysiqueAI app
 * Provides centralized error handling, logging, and user feedback
 */
object ErrorHandler {
    
    private const val TAG = "PhysiqueAI_Error"
    private const val MAX_ERROR_LOG_SIZE = 1000
    
    // Error categories
    enum class ErrorCategory {
        NETWORK,
        DATABASE,
        UI,
        CAMERA,
        PERMISSION,
        MEMORY,
        CRASH,
        UNKNOWN
    }
    
    // Error severity levels
    enum class Severity {
        LOW,      // Minor issues, app continues normally
        MEDIUM,   // Some functionality affected
        HIGH,     // Major functionality affected
        CRITICAL  // App may crash or become unusable
    }
    
    data class ErrorInfo(
        val category: ErrorCategory,
        val severity: Severity,
        val message: String,
        val exception: Throwable? = null,
        val context: String = "",
        val timestamp: Long = System.currentTimeMillis()
    )
    
    private val errorLog = mutableListOf<ErrorInfo>()
    
    /**
     * Handle an error with automatic categorization and appropriate response
     */
    fun handleError(
        context: Context?,
        message: String,
        exception: Throwable? = null,
        category: ErrorCategory = ErrorCategory.UNKNOWN,
        severity: Severity = Severity.MEDIUM,
        showToast: Boolean = true
    ) {
        val errorInfo = ErrorInfo(
            category = category,
            severity = severity,
            message = message,
            exception = exception,
            context = getCurrentContext(),
            timestamp = System.currentTimeMillis()
        )
        
        // Log the error
        logError(errorInfo)
        
        // Add to error log
        addToErrorLog(errorInfo)
        
        // Show user feedback if needed
        if (showToast && context != null) {
            showUserFeedback(context, errorInfo)
        }
        
        // Take appropriate action based on severity
        handleSeverity(errorInfo)
    }
    
    /**
     * Handle a crash with detailed logging
     */
    fun handleCrash(
        context: Context?,
        exception: Throwable,
        additionalInfo: String = ""
    ) {
        val errorInfo = ErrorInfo(
            category = ErrorCategory.CRASH,
            severity = Severity.CRITICAL,
            message = "App crash: ${exception.message ?: "Unknown error"}",
            exception = exception,
            context = additionalInfo.ifEmpty { getCurrentContext() },
            timestamp = System.currentTimeMillis()
        )
        
        // Log crash details
        logCrash(errorInfo)
        
        // Add to error log
        addToErrorLog(errorInfo)
        
        // Show crash message to user
        if (context != null) {
            showCrashMessage(context, errorInfo)
        }
    }
    
    /**
     * Handle network-related errors
     */
    fun handleNetworkError(
        context: Context?,
        message: String,
        exception: Throwable? = null
    ) {
        handleError(
            context = context,
            message = "Network error: $message",
            exception = exception,
            category = ErrorCategory.NETWORK,
            severity = Severity.MEDIUM,
            showToast = true
        )
    }
    
    /**
     * Handle database-related errors
     */
    fun handleDatabaseError(
        context: Context?,
        message: String,
        exception: Throwable? = null
    ) {
        handleError(
            context = context,
            message = "Database error: $message",
            exception = exception,
            category = ErrorCategory.DATABASE,
            severity = Severity.HIGH,
            showToast = true
        )
    }
    
    /**
     * Handle UI-related errors
     */
    fun handleUIError(
        context: Context?,
        message: String,
        exception: Throwable? = null
    ) {
        handleError(
            context = context,
            message = "UI error: $message",
            exception = exception,
            category = ErrorCategory.UI,
            severity = Severity.MEDIUM,
            showToast = false
        )
    }
    
    /**
     * Handle camera-related errors
     */
    fun handleCameraError(
        context: Context?,
        message: String,
        exception: Throwable? = null
    ) {
        handleError(
            context = context,
            message = "Camera error: $message",
            exception = exception,
            category = ErrorCategory.CAMERA,
            severity = Severity.HIGH,
            showToast = true
        )
    }
    
    /**
     * Handle permission-related errors
     */
    fun handlePermissionError(
        context: Context?,
        message: String,
        exception: Throwable? = null
    ) {
        handleError(
            context = context,
            message = "Permission error: $message",
            exception = exception,
            category = ErrorCategory.PERMISSION,
            severity = Severity.HIGH,
            showToast = true
        )
    }
    
    /**
     * Handle memory-related errors
     */
    fun handleMemoryError(
        context: Context?,
        message: String,
        exception: Throwable? = null
    ) {
        handleError(
            context = context,
            message = "Memory error: $message",
            exception = exception,
            category = ErrorCategory.MEMORY,
            severity = Severity.CRITICAL,
            showToast = true
        )
    }
    
    private fun logError(errorInfo: ErrorInfo) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date(errorInfo.timestamp))
        
        Log.e(TAG, "=== ERROR LOGGED ===")
        Log.e(TAG, "Time: $timestamp")
        Log.e(TAG, "Category: ${errorInfo.category}")
        Log.e(TAG, "Severity: ${errorInfo.severity}")
        Log.e(TAG, "Message: ${errorInfo.message}")
        Log.e(TAG, "Context: ${errorInfo.context}")
        
        errorInfo.exception?.let { exception ->
            Log.e(TAG, "Exception: ${exception.javaClass.simpleName}")
            Log.e(TAG, "Exception Message: ${exception.message}")
            
            // Log stack trace
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            exception.printStackTrace(pw)
            Log.e(TAG, "Stack Trace: ${sw.toString()}")
        }
        
        Log.e(TAG, "=====================")
    }
    
    private fun logCrash(errorInfo: ErrorInfo) {
        Log.e(TAG, "=== CRASH DETECTED ===")
        Log.e(TAG, "Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(errorInfo.timestamp))}")
        Log.e(TAG, "Context: ${errorInfo.context}")
        Log.e(TAG, "Message: ${errorInfo.message}")
        
        errorInfo.exception?.let { exception ->
            Log.e(TAG, "Crash Type: ${exception.javaClass.simpleName}")
            Log.e(TAG, "Crash Message: ${exception.message}")
            
            // Detailed stack trace for crashes
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            exception.printStackTrace(pw)
            Log.e(TAG, "Full Stack Trace: ${sw.toString()}")
        }
        
        Log.e(TAG, "======================")
    }
    
    private fun addToErrorLog(errorInfo: ErrorInfo) {
        synchronized(errorLog) {
            errorLog.add(errorInfo)
            
            // Keep log size manageable
            if (errorLog.size > MAX_ERROR_LOG_SIZE) {
                errorLog.removeAt(0)
            }
        }
    }
    
    private fun showUserFeedback(context: Context, errorInfo: ErrorInfo) {
        try {
            val userMessage = when (errorInfo.category) {
                ErrorCategory.NETWORK -> "Network connection issue. Please check your internet."
                ErrorCategory.DATABASE -> "Data loading issue. Please try again."
                ErrorCategory.CAMERA -> "Camera access issue. Please check permissions."
                ErrorCategory.PERMISSION -> "Permission required. Please grant access."
                ErrorCategory.MEMORY -> "Memory issue. Please restart the app."
                ErrorCategory.CRASH -> "App encountered an error. Please restart."
                else -> "An error occurred. Please try again."
            }
            
            Toast.makeText(context, userMessage, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing user feedback", e)
        }
    }
    
    private fun showCrashMessage(context: Context, errorInfo: ErrorInfo) {
        try {
            Toast.makeText(
                context,
                "App encountered an error. Please restart the application.",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing crash message", e)
        }
    }
    
    private fun handleSeverity(errorInfo: ErrorInfo) {
        when (errorInfo.severity) {
            Severity.LOW -> {
                // Just log, no special action needed
            }
            Severity.MEDIUM -> {
                // Log and potentially show user feedback
                Log.w(TAG, "Medium severity error handled: ${errorInfo.message}")
            }
            Severity.HIGH -> {
                // Log and consider app state recovery
                Log.e(TAG, "High severity error: ${errorInfo.message}")
                // Could trigger app state recovery here
            }
            Severity.CRITICAL -> {
                // Log and consider app restart
                Log.e(TAG, "Critical error detected: ${errorInfo.message}")
                // Could trigger app restart here
            }
        }
    }
    
    private fun getCurrentContext(): String {
        return try {
            val stackTrace = Thread.currentThread().stackTrace
            if (stackTrace.size > 3) {
                val element = stackTrace[3]
                "${element.className}.${element.methodName}:${element.lineNumber}"
            } else {
                "Unknown context"
            }
        } catch (e: Exception) {
            "Error getting context"
        }
    }
    
    /**
     * Get error statistics
     */
    fun getErrorStats(): Map<String, Any> {
        synchronized(errorLog) {
            val stats = mutableMapOf<String, Any>()
            
            stats["total_errors"] = errorLog.size
            stats["errors_by_category"] = errorLog.groupBy { it.category }.mapValues { it.value.size }
            stats["errors_by_severity"] = errorLog.groupBy { it.severity }.mapValues { it.value.size }
            
            val recentErrors = errorLog.filter { 
                System.currentTimeMillis() - it.timestamp < 24 * 60 * 60 * 1000 // Last 24 hours
            }
            stats["recent_errors"] = recentErrors.size
            
            return stats
        }
    }
    
    /**
     * Clear error log
     */
    fun clearErrorLog() {
        synchronized(errorLog) {
            errorLog.clear()
        }
    }
    
    /**
     * Get recent errors for debugging
     */
    fun getRecentErrors(limit: Int = 10): List<ErrorInfo> {
        synchronized(errorLog) {
            return errorLog.takeLast(limit)
        }
    }
} 