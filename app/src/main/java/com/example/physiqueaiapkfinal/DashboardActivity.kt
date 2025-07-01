package com.example.physiqueaiapkfinal

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.DocumentSnapshot
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import com.example.physiqueaiapkfinal.WorkoutTodo
import com.example.physiqueaiapkfinal.MealTodo
import com.example.physiqueaiapkfinal.UserMedicalInfo
import com.example.physiqueaiapkfinal.AddedMealsAdapter
import android.text.SpannableString
import android.text.style.StyleSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.firebase.storage.FirebaseStorage

// Data classes for dashboard
data class DashboardStats(
    val caloriesBurned: Int = 0,
    val caloriesGoal: Int = 500,
    val workoutsCompleted: Int = 0,
    val workoutsGoal: Int = 3,
    val currentBMI: Double = 0.0
)

data class AddedExercise(
    val id: String = "",
    val workoutName: String = "",
    val sets: Int = 0,
    val reps: Int = 0,
    val minutes: Int = 0,
    val seconds: Int = 0,
    val isCompleted: Boolean = false
)

// Import the shared data classes
// import com.example.physiqueaiapkfinal.MealTodo
// import com.example.physiqueaiapkfinal.WorkoutTodo



class DashboardActivity : AppCompatActivity() {

    // Firebase instances
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var currentUser: FirebaseUser? = null
    private var userId: String? = null
    
    // UI Components
    private var tvWelcome: TextView? = null
    private var tvFullName: TextView? = null
    private var tvEmail: TextView? = null
    private var tvMotivation: TextView? = null
    private var ivProfileImage: ImageView? = null
    private var tvBMIValue: TextView? = null
    private var tvWorkoutCount: TextView? = null
    private var tvCaloriesBurned: TextView? = null
    private var tvTodoCount: TextView? = null
    private var tvCaloriesProgress: TextView? = null
    private var tvWorkoutsProgress: TextView? = null
    private var progressCalories: ProgressBar? = null
    private var progressWorkouts: ProgressBar? = null
    private var rvAddedExercises: RecyclerView? = null
    private var tvNoExercisesToday: TextView? = null
    private var rvAddedMeals: RecyclerView? = null
    private var tvNoMealsToday: TextView? = null
    private var layoutEmptyState: View? = null
    
    // Cards
    private var cardBMI: CardView? = null
    private var cardWorkouts: CardView? = null
    private var cardCalories: CardView? = null
    private var cardStartWorkout: CardView? = null
    private var cardBMICalc: CardView? = null
    private var cardPoseAI: CardView? = null
    
    // Profile menu
    private var btnProfileMenu: ImageButton? = null
    
    // Todo section
    private var btnViewTodo: Button? = null
    
    // Firebase Listeners
    private var userInfoListener: ListenerRegistration? = null
    private var statsListener: ListenerRegistration? = null
    private var todosListener: ListenerRegistration? = null
    private var mealTodosListener: ListenerRegistration? = null
    
    // Legacy listener references (for cleanup)
    private var workoutTodoListener: ListenerRegistration? = null
    private var mealTodoListener: ListenerRegistration? = null
    private var userStatsListener: ListenerRegistration? = null
    
    // Background executor for heavy operations
    private val backgroundExecutor: ExecutorService = Executors.newFixedThreadPool(2)
    
    // Main handler for UI updates
    private var mainHandler: android.os.Handler? = null
    
    // Data storage for unified updates
    private var allWorkoutTodos: List<WorkoutTodo> = emptyList()
    private var allMealTodos: List<MealTodo> = emptyList()
    private var todayWorkoutTodos: List<WorkoutTodo> = emptyList()
    private var todayMealTodos: List<MealTodo> = emptyList()

    // New RecyclerView instances
    private var rvMealActivities: RecyclerView? = null
    private var rvWorkoutActivities: RecyclerView? = null

    // ======== idinagdag =========
    private val PROFILE_COLLECTION = "userinfo"

    // New UI components
    private var btnResetCalories: ImageButton? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_dashboard)
            
            // Initialize UI components first (fast operations)
            initializeUIComponents()
            
            // Initialize Firebase in background
            backgroundExecutor.execute {
                try {
                    initializeFirebase()
                    
                    // Setup listeners in background
                    setupRealTimeListeners()
                    
                    // Setup UI interactions on main thread
                    mainHandler?.post {
                        try {
                            setupClickListeners()
                            setupBottomNavigation()
                        } catch (e: Exception) {
                            Log.e("DashboardActivity", "Error setting up UI interactions", e)
                        }
                    }
                    
                } catch (e: Exception) {
                    Log.e("DashboardActivity", "Error in background initialization", e)
                    mainHandler?.post {
                        ErrorHandler.handleError(this, "Failed to initialize app data", e)
                    }
                }
            }
            
            // Initialize main handler
            mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            
            // Initialize new UI components
            btnResetCalories = findViewById(R.id.btnResetCalories)
            
            Log.d("DashboardActivity", "UI components initialized successfully")
            
            btnResetCalories?.setOnClickListener {
                resetCalories()
            }
            
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Critical error in onCreate", e)
            ErrorHandler.handleCrash(this, e, "DashboardActivity onCreate failed")
        }
    }
    
    private fun initializeFirebase() {
        try {
            firestore = FirebaseFirestore.getInstance()
            auth = FirebaseAuth.getInstance()
            currentUser = auth.currentUser
            
            if (currentUser == null) {
                Log.w("DashboardActivity", "No authenticated user found")
                // Redirect to login if no user
                mainHandler?.post {
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
                return
            }
            
            userId = currentUser?.uid
            Log.d("DashboardActivity", "Firebase initialized for user: $userId")
            
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Firebase initialization failed", e)
            ErrorHandler.handleError(this, "Failed to initialize app data", e)
            // Show user-friendly error and redirect to login
            mainHandler?.post {
            }
        }
    }
    
    private fun initializeUIComponents() {
        try {
            // Initialize all UI components with null safety
            tvWelcome = findViewById(R.id.tvWelcome)
            tvFullName = findViewById(R.id.tvFullName)
            tvEmail = findViewById(R.id.tvEmail)
            tvMotivation = findViewById(R.id.tvMotivation)
            ivProfileImage = findViewById(R.id.ivProfileImage)
            tvBMIValue = findViewById(R.id.tvBMIValue)
            tvWorkoutCount = findViewById(R.id.tvWorkoutCount)
            tvCaloriesBurned = findViewById(R.id.tvCaloriesBurned)
            tvTodoCount = findViewById(R.id.tvTodoCount)
            progressCalories = findViewById(R.id.progressCalories)
            progressWorkouts = findViewById(R.id.progressWorkouts)
            rvAddedExercises = findViewById(R.id.rvAddedExercises)
            tvNoExercisesToday = findViewById(R.id.tvNoExercisesToday)
            rvAddedMeals = findViewById(R.id.rvAddedMeals)
            tvNoMealsToday = findViewById(R.id.tvNoMealsToday)
            layoutEmptyState = findViewById(R.id.layoutEmptyState)
            
            // Initialize cards
            cardBMI = findViewById(R.id.cardBMI)
            cardWorkouts = findViewById(R.id.cardWorkouts)
            cardCalories = findViewById(R.id.cardCalories)
            cardStartWorkout = findViewById(R.id.cardStartWorkout)
            cardBMICalc = findViewById(R.id.cardBMICalc)
            cardPoseAI = findViewById(R.id.cardPoseAI)
            
            // Initialize profile menu
            btnProfileMenu = findViewById(R.id.btnProfileMenu)

            
            Log.d("DashboardActivity", "UI components initialized successfully")
            
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error initializing UI components", e)
            ErrorHandler.handleError(this, "Failed to load interface", e)
        }
    }
    
    private fun setupClickListeners() {
        try {
            // Enhanced settings button with better error handling
            btnProfileMenu?.setOnClickListener { view ->
                Log.d("DashboardActivity", "Settings button clicked!")
                try {
                    // Create popup menu with proper context
                    val popup = android.widget.PopupMenu(this@DashboardActivity, view)
                    Log.d("DashboardActivity", "Popup menu created")
                    
                    // Inflate menu from resources for reliable IDs/titles
                    try {
                        popup.menuInflater.inflate(R.menu.settings_menu, popup.menu)
                        Log.d("DashboardActivity", "Menu inflated successfully")
                    } catch (e: Exception) {
                        Log.e("DashboardActivity", "Error inflating settings menu", e)
                        Toast.makeText(this@DashboardActivity, "Menu error", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    
                    // Set menu item click listener with enhanced error handling using item IDs
                    popup.setOnMenuItemClickListener { item ->
                        try {
                            when (item.itemId) {
                                R.id.menu_profile -> {
                                    try {
                                        val intent = Intent(this@DashboardActivity, ProfileActivity::class.java)
                                        startActivity(intent)
                                    } catch (e: Exception) {
                                        Log.e("DashboardActivity", "Error navigating to Profile", e)
                                        Toast.makeText(this@DashboardActivity, "Cannot open Profile: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                    true
                                }
                                R.id.menu_logout -> {
                                    try {
                                        com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                                        cleanupListeners()
                                        val intent = Intent(this@DashboardActivity, LoginActivity::class.java)
                                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                        startActivity(intent)
                                        finish()
                                    } catch (e: Exception) {
                                        Log.e("DashboardActivity", "Error during logout", e)
                                        Toast.makeText(this@DashboardActivity, "Logout failed", Toast.LENGTH_SHORT).show()
                                    }
                                    true
                                }
                                R.id.menu_about -> {
                                    try {
                                        val intent = Intent(this@DashboardActivity, AboutActivity::class.java)
                                        startActivity(intent)
                                    } catch (e: Exception) {
                                        Log.e("DashboardActivity", "Error navigating to About", e)
                                        Toast.makeText(this@DashboardActivity, "Cannot open About", Toast.LENGTH_SHORT).show()
                                    }
                                    true
                                }
                                else -> {
                                    Log.w("DashboardActivity", "Unknown menu item id: ${item.itemId}")
                                    false
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("DashboardActivity", "Error handling menu item click", e)
                            Toast.makeText(this@DashboardActivity, "Menu action failed", Toast.LENGTH_SHORT).show()
                            false
                        }
                    }
                    
                    // Show popup with error handling
                    try {
                        popup.show()
                    } catch (e: Exception) {
                        Log.e("DashboardActivity", "Error showing popup menu", e)
                        Toast.makeText(this@DashboardActivity, "Cannot show menu", Toast.LENGTH_SHORT).show()
                    }
                    
                } catch (e: Exception) {
                    Log.e("DashboardActivity", "Error creating settings menu", e)
                    Toast.makeText(this@DashboardActivity, "Settings menu error", Toast.LENGTH_SHORT).show()
                }
            }
            
            // Card click listeners with null safety
            cardStartWorkout?.setOnClickListener {
                try {
                    startActivity(Intent(this, WorkoutListActivity::class.java))
                } catch (e: Exception) {
                    Log.e("DashboardActivity", "Error navigating to workouts", e)
                }
            }
            
            cardBMICalc?.setOnClickListener {
                try {
                    startActivity(Intent(this, BmiCalculatorActivity::class.java))
                } catch (e: Exception) {
                    Log.e("DashboardActivity", "Error navigating to BMI calculator", e)
                }
            }
            
            cardPoseAI?.setOnClickListener {
                try {
                    startActivity(Intent(this, PoseActivity::class.java))
                } catch (e: Exception) {
                    Log.e("DashboardActivity", "Error navigating to Pose AI", e)
                }
            }
            
            cardWorkouts?.setOnClickListener {
                try {
                    startActivity(Intent(this, WorkoutListActivity::class.java))
                } catch (e: Exception) {
                    Log.e("DashboardActivity", "Error navigating to workout list", e)
                }
            }
            
            cardCalories?.setOnClickListener {
                try {
                    startActivity(Intent(this, TodoHubActivity::class.java))
                } catch (e: Exception) {
                    Log.e("DashboardActivity", "Error navigating to todo hub", e)
                }
            }
            
            btnViewTodo?.setOnClickListener {
                try {
                    startActivity(Intent(this, TodoHubActivity::class.java))
                } catch (e: Exception) {
                    Log.e("DashboardActivity", "Error navigating to todo hub", e)
                }
            }
            
            // TEMPORARY: Test direct profile navigation (for debugging)
            cardBMI?.setOnClickListener {
                try {
                    Log.d("DashboardActivity", "BMI card clicked - testing profile navigation")
                    val intent = Intent(this, ProfileActivity::class.java)
                    startActivity(intent)
                    Log.d("DashboardActivity", "Profile navigation from BMI card successful")
                } catch (e: Exception) {
                    Log.e("DashboardActivity", "Error navigating to Profile from BMI card", e)
                    Toast.makeText(this, "Profile error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error in setupClickListeners", e)
        }
    }
    
    // Helper method to cleanup listeners before logout
    private fun cleanupListeners() {
        try {
            userInfoListener?.remove()
            statsListener?.remove()
            todosListener?.remove()
            mealTodosListener?.remove()
            
            workoutTodoListener?.remove()
            mealTodoListener?.remove()
            userStatsListener?.remove()
            
            // Shutdown background executor
            backgroundExecutor.shutdown()
            
            Log.d("DashboardActivity", "All listeners cleaned up")
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error cleaning up listeners", e)
        }
    }
    
    private fun setupRealTimeListeners() {
        try {
            if (userId.isNullOrEmpty()) {
                Log.w("DashboardActivity", "Cannot setup listeners: userId is null")
                return
            }
            
            // Setup listeners in background to prevent ANR
            backgroundExecutor.execute {
                try {
                    // Setup listeners with timeout protection
                    val timeoutHandler = Handler(Looper.getMainLooper())
                    val timeoutRunnable = Runnable {
                        Log.w("DashboardActivity", "Listener setup timeout, continuing...")
                    }
                    
                    // Set 2-second timeout (reduced from 3 seconds)
                    timeoutHandler.postDelayed(timeoutRunnable, 2000)
                    
                    // Setup user info listener
                    setupUserInfoListener()
                    
                    // Setup stats listener
                    setupStatsListener()
                    
                    // Setup todos listener
                    setupTodosListener()
                    
                    // Setup added exercises listener
                    setupAddedExercisesListener()

                    // Setup added meals listener
                    setupAddedMealsListener()
                    
                    // Remove timeout
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    
                    Log.d("DashboardActivity", "Real-time listeners setup completed")
                    
                } catch (e: Exception) {
                    Log.e("DashboardActivity", "Error setting up real-time listeners", e)
                    mainHandler?.post {
                        ErrorHandler.handleError(this@DashboardActivity, "Failed to setup data sync", e)
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error setting up real-time listeners", e)
            ErrorHandler.handleError(this, "Failed to setup data sync", e)
        }
    }
    
    private fun setupUserInfoListener() {
        try {
            userInfoListener = firestore.collection(PROFILE_COLLECTION)
                .document(userId!!)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("DashboardActivity", "User info listener error", error)
                        return@addSnapshotListener
                    }
                    snapshot?.let { safeSnap ->
                        // ipasa ang buong snapshot para magamit ang fallback logic sa UI
                        mainHandler?.post { updateUserInfoFromSnapshot(safeSnap) }
                    }
                }
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error setting up user info listener", e)
        }
    }
    
    private fun updateUserInfoFromSnapshot(doc: DocumentSnapshot) {
        val personal = doc.get("personalInfo") as? Map<*, *> ?: return
        val firstName = personal["firstName"] as? String ?: return
        val email     = personal["email"]     as? String ?: return

        /* ======== Build styled "Welcome back, Firstname!" ======== */
        val welcomeRaw = "Welcome back, $firstName!"
        val spannable  = SpannableString(welcomeRaw).apply {
            val start = "Welcome back, ".length
            val end   = welcomeRaw.length

            // Bold
            setSpan(StyleSpan(Typeface.BOLD), start, end, 0)

            // Black color
            val black = ContextCompat.getColor(this@DashboardActivity, android.R.color.black)
            setSpan(ForegroundColorSpan(black), start, end, 0)

            // 1.2× size (adjust as you like)
            setSpan(RelativeSizeSpan(1.2f), start, end, 0)
        }
        tvWelcome?.text = spannable
        /* ========================================================= */

        tvEmail?.text = email
        tvFullName?.visibility = View.GONE      // wala nang duplicate

        // motivation text (walang pagbabago)
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        tvMotivation?.text = when {
            hour < 12 -> "Good morning! Ready to crush your goals?"
            hour < 17 -> "Good afternoon! Keep pushing forward!"
            else      -> "Good evening! Great work today!"
        }

        /* ==================  B M I   U P D A T E  ================== */
        val bmiMap = doc.get("bmiInfo") as? Map<*, *>
        val bmiVal = (bmiMap?.get("bmi") as? Number)?.toDouble() ?: -1.0
        if (bmiVal > 0) {
            // I-display ang value
            tvBMIValue?.text = String.format("%.1f", bmiVal)

            // Tukuyin ang kategorya para sa kulay
            val bmiCategory = when {
                bmiVal < 18.5 -> "underweight"
                bmiVal < 25   -> "normal"
                bmiVal < 30   -> "overweight"
                else          -> "obese"
            }
            cardBMI?.setCardBackgroundColor(getBMIColor(bmiCategory))
        }
        /* ============================================================ */

        /* ======== Profile photo ======== */
        val photoUrl = doc.getString("profilePhotoUrl")
        if (!photoUrl.isNullOrBlank()) {
            try {
                ivProfileImage?.let { img ->
                    Glide.with(this)
                        .load(photoUrl)
                        .circleCrop()
                        .into(img)
                }
            } catch (e: Exception) {
                Log.e("DashboardActivity", "Error loading profile image", e)
            }
        }
        /* =========================================== */
    }
    
    private fun setupStatsListener() {
        try {
            statsListener = firestore.collection("userStats")
                .document(userId!!)
                .addSnapshotListener { snapshot, error ->
                    backgroundExecutor.execute {
                        try {
                            if (error != null) {
                                Log.e("DashboardActivity", "Stats listener error", error)
                                return@execute
                            }
                            
                            if (snapshot != null && snapshot.exists()) {
                                val stats = snapshot.data
                                
                                // Update UI on main thread
                                mainHandler?.post {
                                    try {
                                        updateDashboardStats(stats)
                                    } catch (e: Exception) {
                                        Log.e("DashboardActivity", "Error updating dashboard stats", e)
                                    }
                                }
                            } else {
                                Log.w("DashboardActivity", "Stats document not found")
                                mainHandler?.post {
                                    try {
                                        showEmptyStatsState()
                                    } catch (e: Exception) {
                                        Log.e("DashboardActivity", "Error showing empty stats state", e)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("DashboardActivity", "Error processing stats", e)
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error setting up stats listener", e)
        }
    }
    
    private fun setupTodosListener() {
        try {
            // Setup workout todos listener with limit and background processing
            todosListener = firestore.collection("userTodoList")
                .document(userId!!)
                .collection("workoutPlan")
                .whereEqualTo("scheduledDate", getCurrentDate())
                .limit(5) // Reduced from 10 to 5 for better performance
                .addSnapshotListener { snapshot, error ->
                    backgroundExecutor.execute {
                        try {
                            if (error != null) {
                                Log.e("DashboardActivity", "Workout todos listener error", error)
                                return@execute
                            }
                            
                            val workoutTodos = snapshot?.documents?.mapNotNull { doc ->
                                try {
                                    doc.toObject(WorkoutTodo::class.java)?.copy(id = doc.id)
                                } catch (e: Exception) {
                                    Log.e("DashboardActivity", "Error parsing workout todo", e)
                                    null
                                }
                            } ?: emptyList()
                            
                            allWorkoutTodos = workoutTodos
                            todayWorkoutTodos = workoutTodos.filter { todo ->
                                todo.scheduledDate == getCurrentDate()
                            }
                            
                            // Update UI on main thread
                            mainHandler?.post {
                                try {
                                    updateTodosDisplay(allWorkoutTodos, allMealTodos)
                                } catch (e: Exception) {
                                    Log.e("DashboardActivity", "Error updating todos display", e)
                                }
                            }
                            
                        } catch (e: Exception) {
                            Log.e("DashboardActivity", "Error processing workout todos", e)
                        }
                    }
                }
            
            // Setup meal todos listener with limit and background processing
            mealTodosListener = firestore.collection("userTodoList")
                .document(userId!!)
                .collection("mealPlan")
                .whereEqualTo("scheduledDate", getCurrentDate())
                .limit(5) // Reduced from 10 to 5 for better performance
                .addSnapshotListener { snapshot, error ->
                    backgroundExecutor.execute {
                        try {
                            if (error != null) {
                                Log.e("DashboardActivity", "Meal todos listener error", error)
                                return@execute
                            }
                            
                            val mealTodos = snapshot?.documents?.mapNotNull { doc ->
                                try {
                                    doc.toObject(MealTodo::class.java)?.copy(id = doc.id)
                                } catch (e: Exception) {
                                    Log.e("DashboardActivity", "Error parsing meal todo", e)
                                    null
                                }
                            } ?: emptyList()
                            
                            allMealTodos = mealTodos
                            todayMealTodos = mealTodos.filter { todo ->
                                todo.scheduledDate == getCurrentDate()
                            }
                            
                            // Update UI on main thread
                            mainHandler?.post {
                                try {
                                    updateTodosDisplay(allWorkoutTodos, allMealTodos)
                                } catch (e: Exception) {
                                    Log.e("DashboardActivity", "Error updating todos display", e)
                                }
                            }
                            
                        } catch (e: Exception) {
                            Log.e("DashboardActivity", "Error processing meal todos", e)
                        }
                    }
                }
                
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error setting up todos listeners", e)
        }
    }
    
    private fun getCurrentDate(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
    
    private fun today(): String =
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

    private fun setupAddedExercisesListener() {
        try {
            workoutTodoListener = firestore.collection("userTodoList")
                .document(userId!!)
                .collection("workoutPlan")
                .whereEqualTo("scheduledDate", getCurrentDate())
                .addSnapshotListener { snapshot, error ->
                    backgroundExecutor.execute {
                        try {
                            if (error != null) {
                                Log.e("DashboardActivity", "Added exercises listener error", error)
                                return@execute
                            }

                            val exercises = snapshot?.documents?.mapNotNull { doc ->
                                try {
                                    doc.toObject(WorkoutTodo::class.java)?.let { workoutTodo ->
                                        AddedExercise(
                                            id = workoutTodo.id,
                                            workoutName = workoutTodo.workoutName,
                                            sets = workoutTodo.sets,
                                            reps = workoutTodo.reps,
                                            minutes = workoutTodo.minutes,
                                            seconds = workoutTodo.seconds,
                                            isCompleted = workoutTodo.isCompleted
                                        )
                                    }
                                } catch (e: Exception) {
                                    Log.e("DashboardActivity", "Error parsing added exercise", e)
                                    null
                                }
                            } ?: emptyList()

                            Log.d("DashboardActivity", "Fetched exercises: $exercises")
                            // Update UI on main thread
                            mainHandler?.post {
                                try {
                                    updateAddedExercises(exercises)
                                } catch (e: Exception) {
                                    Log.e("DashboardActivity", "Error updating added exercises", e)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("DashboardActivity", "Error processing added exercises", e)
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error setting up added exercises listener", e)
        }
    }

    private fun setupAddedMealsListener() {
        try {
            mealTodosListener = firestore.collection("userTodoList")
                .document(userId!!)
                .collection("mealPlan")
                .whereEqualTo("scheduledDate", getCurrentDate())
                .addSnapshotListener { snapshot, error ->
                    backgroundExecutor.execute {
                        try {
                            if (error != null) {
                                Log.e("DashboardActivity", "Added meals listener error", error)
                                return@execute
                            }

                            val meals = snapshot?.documents?.mapNotNull { doc ->
                                try {
                                    doc.toObject(MealTodo::class.java)?.copy(id = doc.id)
                                } catch (e: Exception) {
                                    Log.e("DashboardActivity", "Error parsing added meal", e)
                                    null
                                }
                            } ?: emptyList()

                            Log.d("DashboardActivity", "Fetched meals: $meals")
                            // Update UI on main thread
                            mainHandler?.post {
                                try {
                                    updateAddedMeals(meals)
                                } catch (e: Exception) {
                                    Log.e("DashboardActivity", "Error updating added meals", e)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("DashboardActivity", "Error processing added meals", e)
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error setting up added meals listener", e)
        }
    }
    
    private fun updateDashboardStats(stats: Map<String, Any>?) {
        try {
            runOnUiThread {
                try {
                    stats?.let { data ->
                        val totalWorkouts = (data["totalWorkouts"] as? Long)?.toInt() ?: 0
                        val totalCaloriesBurned = (data["totalCaloriesConsumed"] as? Long)?.toInt() ?: 0
                        val todayWorkouts = (data["todayWorkouts"] as? Long)?.toInt() ?: 0
                        val todayCaloriesBurned = (data["todayCaloriesConsumed"] as? Long)?.toInt() ?: 0
                        
                        // Update total stats
                        tvWorkoutCount?.text = totalWorkouts.toString()
                        tvCaloriesBurned?.text = totalCaloriesBurned.toString()
                        
                        // Update today progress
                        tvWorkoutsProgress?.text = "$todayWorkouts today"
                        tvCaloriesProgress?.text = "$todayCaloriesBurned today"
                        
                        // Update progress bars
                        progressWorkouts?.progress = todayWorkouts
                        progressCalories?.progress = todayCaloriesBurned
                        
                    } ?: run {
                        // No stats available
                        showEmptyStatsState()
                    }
                    
                } catch (e: Exception) {
                    Log.e("DashboardActivity", "Error updating UI with stats", e)
                }
            }
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error in updateDashboardStats", e)
        }
    }
    
    private fun updateTodosDisplay(workoutTodos: List<WorkoutTodo>, mealTodos: List<MealTodo>) {
        try {
            runOnUiThread {
                try {
                    val totalTodos = workoutTodos.size + mealTodos.size
                    val completedTodos = workoutTodos.count { it.isCompleted } + mealTodos.count { it.isCompleted }
                    
                    tvTodoCount?.text = "$completedTodos/$totalTodos"
                    
                    if (totalTodos == 0) {
                        btnViewTodo?.text = "Add Your First Task"
                    } else {
                        btnViewTodo?.text = "View All Tasks"
                    }
                    
                } catch (e: Exception) {
                    Log.e("DashboardActivity", "Error updating todos display", e)
                }
            }
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error in updateTodosDisplay", e)
        }
    }
    
    private fun updateAddedExercises(exercises: List<AddedExercise>) {
        try {
            runOnUiThread {
                try {
                    if (exercises.isEmpty()) {
                        rvAddedExercises?.visibility = View.GONE
                        tvNoExercisesToday?.visibility = View.VISIBLE
                    } else {
                        rvAddedExercises?.visibility = View.VISIBLE
                        tvNoExercisesToday?.visibility = View.GONE
                        if (rvAddedExercises?.adapter == null) {
                            rvAddedExercises?.layoutManager = LinearLayoutManager(this)
                            rvAddedExercises?.adapter = AddedExercisesAdapter(exercises, 
                                { exercise -> toggleWorkoutCompletion(exercise) },
                                { exercise -> navigateToExerciseActivity(exercise) }
                            )
                        } else {
                            (rvAddedExercises?.adapter as? AddedExercisesAdapter)?.updateExercises(exercises)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("DashboardActivity", "Error updating added exercises", e)
                }
            }
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error in updateAddedExercises", e)
        }
    }

    private fun navigateToExerciseActivity(exercise: AddedExercise) {
        val workoutNameClean = exercise.workoutName.trim().lowercase()
        Log.d("DashboardActivity", "Navigating to activity for: '$workoutNameClean'")

        val activityClass = when (workoutNameClean) {
            "push-up" -> StreamActivity::class.java
            "squat" -> SquatActivity::class.java
            "dumbbell front raise" -> DumbbellFrontRaiseActivity::class.java
            "dumbbell hammer curl" -> DumbbellHammerCurlActivity::class.java
            "hip thrusts" -> HipThrustsActivity::class.java
            "military press" -> MilitaryPressActivity::class.java
            "sit ups" -> SitUpsActivity::class.java
            "windmill" -> WindmillActivity::class.java
            // Add other exercises here, potentially mapping them to StreamActivity if no specific one exists
            else -> {
                Toast.makeText(this, "No specific activity for '${exercise.workoutName}', using default.", Toast.LENGTH_SHORT).show()
                StreamActivity::class.java // Fallback to StreamActivity
            }
        }

        val intent = Intent(this, activityClass).apply {
            // Pass exercise details to the activity
            putExtra("WORKOUT_NAME", exercise.workoutName)
            putExtra("WORKOUT_ID", exercise.id)
        }
        startActivity(intent)
    }

    private fun toggleWorkoutCompletion(exercise: AddedExercise) {
        userId?.let { uid ->
            firestore.collection("userTodoList").document(uid)
                .collection("workoutPlan").document(exercise.id)
                .update("isCompleted", !exercise.isCompleted)
                .addOnSuccessListener {
                    Toast.makeText(this, "Workout updated!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Log.e("DashboardActivity", "Error updating workout completion", e)
                    Toast.makeText(this, "Failed to update workout: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
    
    private fun showEmptyUserState() {
        runOnUiThread {
            tvWelcome?.text = "Welcome!"
            tvFullName?.visibility = View.GONE
            tvEmail?.text = "No email available"
            tvMotivation?.text = "Complete your profile to get started!"
        }
    }

    private fun updateAddedMeals(meals: List<MealTodo>) {
        try {
            runOnUiThread {
                try {
                    if (meals.isEmpty()) {
                        rvAddedMeals?.visibility = View.GONE
                        tvNoMealsToday?.visibility = View.VISIBLE
                    } else {
                        rvAddedMeals?.visibility = View.VISIBLE
                        tvNoMealsToday?.visibility = View.GONE
                        if (rvAddedMeals?.adapter == null) {
                            rvAddedMeals?.layoutManager = LinearLayoutManager(this)
                            rvAddedMeals?.adapter = AddedMealsAdapter(meals) { meal ->
                                toggleMealCompletion(meal)
                            }
                        } else {
                            (rvAddedMeals?.adapter as? AddedMealsAdapter)?.updateMeals(meals)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("DashboardActivity", "Error updating added meals", e)
                }
            }
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error in updateAddedMeals", e)
        }
    }

    private fun toggleMealCompletion(meal: MealTodo) {
        userId?.let { uid ->
            // 1) Add its calories to the dashboard totals
            updateCaloriesConsumedAsync(meal.calories)

            // 2) Remove the meal document since it is finished
            firestore.collection("userTodoList").document(uid)
                .collection("mealPlan").document(meal.id)
                .delete()
                .addOnSuccessListener {
                    Toast.makeText(this, "Meal completed and removed!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Log.e("DashboardActivity", "Error deleting completed meal", e)
                    Toast.makeText(this, "Failed to complete meal: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // 🔢 Update calorie stats when a meal is completed from the dashboard
    private fun updateCaloriesConsumedAsync(calories: Int) {
        try {
            val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

            // Update per-day stats document
            firestore.collection("userStats")
                .document(userId!!)
                .collection("dailyStats")
                .document(today)
                .get()
                .addOnSuccessListener { document ->
                    val currentCalories = document.getLong("caloriesConsumed")?.toInt() ?: 0
                    val currentMeals = document.getLong("mealsCompleted")?.toInt() ?: 0

                    val updatedCalories = currentCalories + calories
                    val updatedMeals = currentMeals + 1

                    val statsData = mapOf(
                        "caloriesConsumed" to updatedCalories,
                        "mealsCompleted" to updatedMeals,
                        "lastUpdated" to System.currentTimeMillis(),
                        "date" to today,
                        "userId" to userId
                    )

                    firestore.collection("userStats")
                        .document(userId!!)
                        .collection("dailyStats")
                        .document(today)
                        .set(statsData, com.google.firebase.firestore.SetOptions.merge())
                        .addOnSuccessListener {
                            // Also update the aggregate stats document
                            firestore.collection("userStats")
                                .document(userId!!)
                                .get()
                                .addOnSuccessListener { mainDoc ->
                                    val totalCaloriesConsumed = mainDoc.getLong("totalCaloriesConsumed")?.toInt() ?: 0
                                    val totalMealsCompleted = mainDoc.getLong("totalMealsCompleted")?.toInt() ?: 0

                                    firestore.collection("userStats")
                                        .document(userId!!)
                                        .set(mapOf(
                                            "totalCaloriesConsumed" to (totalCaloriesConsumed + calories),
                                            "totalMealsCompleted" to (totalMealsCompleted + 1),
                                            "todayCaloriesConsumed" to updatedCalories,
                                            "todayMealsCompleted" to updatedMeals,
                                            "lastUpdated" to System.currentTimeMillis()
                                        ), com.google.firebase.firestore.SetOptions.merge())
                                }
                        }
                }
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error updating calories consumed", e)
        }
    }

    private fun showEmptyStatsState() {
        try {
            runOnUiThread {
                tvWorkoutCount?.text = "0"
                tvCaloriesBurned?.text = "0"
                tvWorkoutsProgress?.text = "0 today"
                tvCaloriesProgress?.text = "0 today"
                progressWorkouts?.progress = 0
                progressCalories?.progress = 0
            }
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error showing empty stats state", e)
        }
    }

    private fun getBMIColor(category: String): Int {
        return try {
            when (category.lowercase()) {
                "underweight" -> Color.parseColor("#FF6B6B")
                "normal" -> Color.parseColor("#4ECDC4")
                "overweight" -> Color.parseColor("#FFA726")
                "obese" -> Color.parseColor("#EF5350")
                else -> Color.parseColor("#9E9E9E")
            }
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error parsing BMI color", e)
            Color.parseColor("#9E9E9E")
        }
    }

    private fun setupBottomNavigation() {
        try {
            val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)
            bottomNavigation?.let { nav ->
                nav.selectedItemId = R.id.nav_home
                
                nav.setOnItemSelectedListener { item ->
                    try {
                        when (item.itemId) {
                            R.id.nav_home -> { 
                                // Already on dashboard
                                true
                            }
                            R.id.nav_workout -> {
                                safeNavigateToActivity(WorkoutListActivity::class.java)
                                true
                            }
                            R.id.nav_posture -> {
                                safeNavigateToActivity(PoseActivity::class.java)
                                true
                            }
                            R.id.nav_dietary -> {
                                safeNavigateToActivity(RecipeListActivity::class.java)
                                true
                            }
                            R.id.nav_task -> {
                                safeNavigateToActivity(TodoHubActivity::class.java)
                                true
                            }
                            else -> {
                                Log.e("Dashboard", "Unknown menu item selected: ${item.itemId}")
                                false
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("DashboardActivity", "Error in navigation", e)
                        false
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error setting up bottom navigation", e)
        }
    }

    private fun safeNavigateToActivity(activityClass: Class<*>) {
        try {
            val intent = Intent(this, activityClass)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Failed to navigate to ${activityClass.simpleName}", e)
            ErrorHandler.handleError(this, "Failed to open ${activityClass.simpleName}", e)
        }
    }

    private fun loadDashboardData() {
        try {
            // This will be handled by real-time listeners now
            Log.d("DashboardActivity", "Dashboard data loading initiated")
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error loading dashboard data", e)
        }
    }

    // 🛡️ SAFE FINDVIEW HELPER
    private inline fun <reified T : View> safelyFindView(@SuppressLint("DiscouragedApi") id: Int): T? {
        return try {
            findViewById(id)
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error finding view with id: $id", e)
            null
        }
    }

    override fun onDestroy() {
        try {
            // Clean up listeners to prevent memory leaks
            // (cleanupListeners removed)
            // Remove callbacks to prevent memory leaks
            mainHandler?.removeCallbacksAndMessages(null)
            // Shutdown background executor properly
            if (!backgroundExecutor.isShutdown) {
                backgroundExecutor.shutdown()
                try {
                    if (!backgroundExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        backgroundExecutor.shutdownNow()
                    }
                } catch (e: InterruptedException) {
                    backgroundExecutor.shutdownNow()
                    Thread.currentThread().interrupt()
                }
            }
            super.onDestroy()
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error in onDestroy", e)
            super.onDestroy()
        }
    }

    override fun onPause() {
        try {
            // Don't cleanup listeners immediately to prevent unnecessary re-initialization
            // Only cleanup in onDestroy to prevent memory leaks
            super.onPause()
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error in onPause", e)
            super.onPause()
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            // Listeners are already active, no need to restart
            // Only restart if they were actually cleaned up
            if (userInfoListener == null && !userId.isNullOrEmpty() && !isFinishing) {
                Log.d("DashboardActivity", "Restarting listeners in onResume")
                setupRealTimeListeners()
            }
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error in onResume", e)
        }
    }

    private fun resetCalories() {
        userId?.let { uid ->
            val firestore = FirebaseFirestore.getInstance()
            // Reset aggregate stats
            val userStatsRef = firestore.collection("userStats").document(uid)
            userStatsRef.update(mapOf(
                "totalCaloriesConsumed" to 0,
                "todayCaloriesConsumed" to 0,
                "totalMealsCompleted" to 0,
                "todayMealsCompleted" to 0
            )).addOnSuccessListener {
                Log.d("DashboardActivity", "Total calories and meals reset successfully.")
                Toast.makeText(this, "Calories have been reset.", Toast.LENGTH_SHORT).show()
            }.addOnFailureListener { e ->
                Log.e("DashboardActivity", "Error resetting total calories", e)
            }

            // Also reset daily stats for today
            val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            val dailyStatsRef = userStatsRef.collection("dailyStats").document(today)
            dailyStatsRef.update(mapOf(
                "caloriesConsumed" to 0,
                "mealsCompleted" to 0
            )).addOnSuccessListener {
                Log.d("DashboardActivity", "Daily calories and meals reset successfully.")
            }.addOnFailureListener { e ->
                Log.e("DashboardActivity", "Error resetting daily calories", e)
            }
        }
    }
} 