package com.example.physiqueaiapkfinal

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.core.app.ActivityOptionsCompat

class TodoHubActivity : AppCompatActivity() {

    // UI Components
    private lateinit var cardWorkoutTodo: CardView
    private lateinit var cardDietaryTodo: CardView
    private lateinit var cardOutdoorActivity: CardView
    private lateinit var backButton: ImageButton
    private lateinit var bottomNavigation: BottomNavigationView
    private var loadingProgressBar: ProgressBar? = null

    // Background executor for async operations
    private val backgroundExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Loading state management
    private var isInitialized = false
    private var isPaused = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            setContentView(R.layout.activity_todo_hub)
            
            // Initialize components asynchronously to prevent blocking
            initializeAsync()
        } catch (e: Exception) {
            handleError("Initialization error", e)
        }
    }

    private fun initializeAsync() {
        // Show loading state
        showLoading(true)
        
        backgroundExecutor.execute {
            try {
                // Simulate any heavy operations that might be needed
                Thread.sleep(100) // Minimal delay to show loading
                
                mainHandler.post {
                    try {
                        if (!isFinishing && !isPaused) {
                            initializeViews()
                            setupClickListeners()
                            setupBottomNavigation()
                            isInitialized = true
                            showLoading(false)
                        }
                    } catch (e: Exception) {
                        handleError("UI initialization error", e)
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    handleError("Background initialization error", e)
                }
            }
        }
    }

    private fun initializeViews() {
        try {
            cardWorkoutTodo = findViewById(R.id.cardWorkoutTodo)
            cardDietaryTodo = findViewById(R.id.cardDietaryTodo)
            cardOutdoorActivity = findViewById(R.id.cardOutdoorActivity)
            backButton = findViewById(R.id.backButton)
            bottomNavigation = findViewById(R.id.bottom_navigation)
            
            // Initially disable cards until fully loaded
            setCardsEnabled(false)
        } catch (e: Exception) {
            handleError("View initialization error", e)
        }
    }

    private fun setupClickListeners() {
        try {
            // Back button click listener with debouncing
            var lastBackClickTime = 0L
            backButton.setOnClickListener {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastBackClickTime > 1000) { // 1 second debounce
                    lastBackClickTime = currentTime
                    finishSafely()
                }
            }

            // Workout Todo Card with async navigation
            cardWorkoutTodo.setOnClickListener {
                if (isInitialized) {
                    navigateToWorkoutTodo()
                }
            }

            // Dietary Todo Card with async navigation
            cardDietaryTodo.setOnClickListener {
                if (isInitialized) {
                    navigateToDietaryTodo()
                }
            }

            // Outdoor Activity Card with async navigation
            cardOutdoorActivity.setOnClickListener {
                if (isInitialized) {
                    navigateToActivityAsync(OutdoorActivityActivity::class.java, "Outdoor Activity")
                }
            }
            
            // Enable cards after setup
            setCardsEnabled(true)
        } catch (e: Exception) {
            handleError("Click listener setup error", e)
        }
    }

    private fun setupBottomNavigation() {
        try {
            bottomNavigation.selectedItemId = R.id.nav_task
            
            bottomNavigation.setOnItemSelectedListener { item ->
                try {
                    if (!isInitialized) return@setOnItemSelectedListener false
                    
                    when (item.itemId) {
                        R.id.nav_home -> {
                            navigateToActivityAsync(DashboardActivity::class.java, "Dashboard")
                            true
                        }
                        R.id.nav_workout -> {
                            navigateToActivityAsync(WorkoutListActivity::class.java, "Workout List")
                            true
                        }
                        R.id.nav_posture -> {
                            navigateToActivityAsync(PoseActivity::class.java, "Pose AI")
                            true
                        }
                        R.id.nav_dietary -> {
                            navigateToActivityAsync(RecipeListActivity::class.java, "Recipe List")
                            true
                        }
                        R.id.nav_task -> {
                            // Already on todo hub
                            true
                        }
                        else -> false
                    }
                } catch (e: Exception) {
                    handleError("Navigation error", e)
                    false
                }
            }
        } catch (e: Exception) {
            handleError("Bottom navigation setup error", e)
        }
    }

    private fun navigateToActivityAsync(activityClass: Class<*>, screenName: String) {
        // Disable UI temporarily to prevent double-clicks
        setCardsEnabled(false)
        showLoading(true)
        
        backgroundExecutor.execute {
            try {
                mainHandler.post {
                    try {
                        if (!isFinishing && !isPaused) {
                            val intent = Intent(this, activityClass)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            startActivity(intent)
                            
                            // Add smooth transition
                            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                        }
                    } catch (e: Exception) {
                        handleError("Error opening $screenName", e)
                    } finally {
                        showLoading(false)
                        setCardsEnabled(true)
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    handleError("Navigation preparation error", e)
                    showLoading(false)
                    setCardsEnabled(true)
                }
            }
        }
    }

    private fun navigateToWorkoutTodo() {
        try {
            val intent = Intent(this, WorkoutTodoActivity::class.java)
            
            // Use modern activity transition
            val options = ActivityOptionsCompat.makeCustomAnimation(
                this,
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
            
            startActivity(intent, options.toBundle())
        } catch (e: Exception) {
            Log.e("TodoHub", "Error navigating to workout todo", e)
            // Fallback to simple navigation
            startActivity(Intent(this, WorkoutTodoActivity::class.java))
        }
    }
    
    private fun navigateToDietaryTodo() {
        try {
            val intent = Intent(this, DietaryTodoActivity::class.java)
            
            // Use modern activity transition
            val options = ActivityOptionsCompat.makeCustomAnimation(
                this,
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
            
            startActivity(intent, options.toBundle())
        } catch (e: Exception) {
            Log.e("TodoHub", "Error navigating to dietary todo", e)
            // Fallback to simple navigation
            startActivity(Intent(this, DietaryTodoActivity::class.java))
        }
    }

    private fun setCardsEnabled(enabled: Boolean) {
        try {
            if (::cardWorkoutTodo.isInitialized) {
                cardWorkoutTodo.isEnabled = enabled
                cardWorkoutTodo.alpha = if (enabled) 1.0f else 0.6f
            }
            if (::cardDietaryTodo.isInitialized) {
                cardDietaryTodo.isEnabled = enabled
                cardDietaryTodo.alpha = if (enabled) 1.0f else 0.6f
            }
            if (::cardOutdoorActivity.isInitialized) {
                cardOutdoorActivity.isEnabled = enabled
                cardOutdoorActivity.alpha = if (enabled) 1.0f else 0.6f
            }
        } catch (e: Exception) {
            Log.e("TodoHub", "Error setting cards enabled state", e)
        }
    }

    private fun showLoading(show: Boolean) {
        try {
            if (loadingProgressBar == null) {
                loadingProgressBar = ProgressBar(this).apply {
                    isIndeterminate = true
                }
            }
            
            loadingProgressBar?.visibility = if (show) View.VISIBLE else View.GONE
            
            // Also disable bottom navigation during loading
            if (::bottomNavigation.isInitialized) {
                bottomNavigation.isEnabled = !show
            }
        } catch (e: Exception) {
            Log.e("TodoHub", "Error showing loading state", e)
        }
    }

    private fun handleError(message: String, exception: Exception) {
        Log.e("TodoHub", "$message: ${exception.message}", exception)
        
        mainHandler.post {
            try {
                if (!isFinishing) {
                    Toast.makeText(this, "Error: $message. Please try again.", Toast.LENGTH_SHORT).show()
                    showLoading(false)
                    setCardsEnabled(true)
                }
            } catch (e: Exception) {
                Log.e("TodoHub", "Error showing error message", e)
            }
        }
    }

    private fun finishSafely() {
        try {
            isPaused = true
            backgroundExecutor.execute {
                mainHandler.post {
                    try {
                        if (!isFinishing) {
                            val intent = Intent(this, DashboardActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            startActivity(intent)
                            finish()
                            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                        }
                    } catch (e: Exception) {
                        Log.e("TodoHub", "Error finishing activity", e)
                        finish()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("TodoHub", "Error in finishSafely", e)
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    @android.annotation.SuppressLint("MissingSuperCall")
    override fun onBackPressed() {
        finishSafely()
    }

    override fun onPause() {
        super.onPause()
        isPaused = true
        try {
            setCardsEnabled(false)
        } catch (e: Exception) {
            Log.e("TodoHub", "Error in onPause", e)
        }
    }

    override fun onResume() {
        super.onResume()
        isPaused = false
        try {
            if (isInitialized) {
                setCardsEnabled(true)
            }
        } catch (e: Exception) {
            Log.e("TodoHub", "Error in onResume", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            backgroundExecutor.shutdown()
        } catch (e: Exception) {
            Log.e("TodoHub", "Error shutting down executor", e)
        }
    }
} 