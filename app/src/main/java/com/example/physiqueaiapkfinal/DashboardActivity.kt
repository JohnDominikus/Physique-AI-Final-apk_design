package com.example.physiqueaiapkfinal

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

// Data classes for dashboard
data class DashboardStats(
    val caloriesBurned: Int = 0,
    val caloriesGoal: Int = 500,
    val workoutsCompleted: Int = 0,
    val workoutsGoal: Int = 3,
    val currentBMI: Double = 0.0
)

data class RecentActivity(
    val title: String = "",
    val subtitle: String = "",
    val timestamp: Long = 0L
)

// Recent Activities Adapter
class RecentActivitiesAdapter(private var activities: List<RecentActivity>) : 
    RecyclerView.Adapter<RecentActivitiesAdapter.ViewHolder>() {
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvActivityName)
        val tvSubtitle: TextView = view.findViewById(R.id.tvDuration)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_activity_log, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val activity = activities[position]
        holder.tvTitle.text = activity.title
        holder.tvSubtitle.text = activity.subtitle
        holder.tvTime.text = formatTime(activity.timestamp)
    }
    
    override fun getItemCount() = activities.size
    
    fun updateActivities(newActivities: List<RecentActivity>) {
        activities = newActivities
        notifyDataSetChanged()
    }
    
    private fun formatTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < 60000 -> "Just now"
            diff < 3600000 -> "${diff / 60000}m ago"
            diff < 86400000 -> "${diff / 3600000}h ago"
            else -> "${diff / 86400000}d ago"
        }
    }
}

class DashboardActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var userId: String
    
    // UI Components - Make them nullable to prevent crashes
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
    private var rvRecentActivities: RecyclerView? = null
    private var layoutEmptyState: LinearLayout? = null
    
    // Cards - Make them nullable to prevent crashes
    private var cardBMI: CardView? = null
    private var cardWorkouts: CardView? = null
    private var cardCalories: CardView? = null
    private var cardStartWorkout: CardView? = null
    private var cardBMICalc: CardView? = null
    private var cardPoseAI: CardView? = null

    // Dropdown menu components
    private var btnProfileMenu: ImageView? = null
    private var dropdownMenu: androidx.cardview.widget.CardView? = null
    private var isDropdownVisible = false

    private lateinit var mainHandler: Handler
    private var btnViewTodo: Button? = null
    private var tvNoTasks: TextView? = null

    // Real-time listeners
    private var userInfoListener: ListenerRegistration? = null
    private var workoutTodoListener: ListenerRegistration? = null
    private var mealTodoListener: ListenerRegistration? = null
    
    // Background executor for heavy operations
    private val backgroundExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            setContentView(R.layout.activity_dashboard)

            // Initialize Firebase with error handling
            initializeFirebase()
            
            // Check authentication with error handling
            if (!checkAuthentication()) {
                return
            }
            
            // Initialize UI components with error handling
            initializeViews()
            setupClickListeners()
            setupBottomNavigation()
            
            // Load dashboard data with error handling
            loadDashboardData()
            
            mainHandler = Handler(Looper.getMainLooper())
            
            // Setup real-time listeners
            setupRealtimeListeners()
            
            // Initialize btnViewTodo safely
            btnViewTodo = safelyFindView(R.id.btnViewTodo)
            btnViewTodo?.setOnClickListener {
                try {
                    // Navigate to Todo screen
                    startActivity(Intent(this, TodoHubActivity::class.java))
                } catch (e: Exception) {
                    Log.e("DashboardActivity", "Error navigating to TodoHub", e)
                    showError("Failed to open Todo Hub")
                }
            }
            
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error in onCreate", e)
            handleCriticalError("Failed to initialize dashboard", e)
        }
    }

    private fun initializeFirebase() {
        try {
            auth = FirebaseAuth.getInstance()
            firestore = FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Firebase initialization failed", e)
            throw RuntimeException("Firebase initialization failed", e)
        }
    }
        
    private fun checkAuthentication(): Boolean {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.w("DashboardActivity", "User not authenticated, redirecting to login")
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
                false
            } else {
                userId = currentUser.uid
                Log.d("DashboardActivity", "User authenticated: $userId")
                true
            }
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Authentication check failed", e)
            handleCriticalError("Authentication failed", e)
            false
        }
    }

    private fun initializeViews() {
        try {
            // Use safe findViewById with null checks
            tvWelcome = safelyFindView(R.id.tvWelcome)
            tvFullName = safelyFindView(R.id.tvFullName)
            tvEmail = safelyFindView(R.id.tvEmail)
            tvMotivation = safelyFindView(R.id.tvMotivation)
            ivProfileImage = safelyFindView(R.id.ivProfileImage)
            tvBMIValue = safelyFindView(R.id.tvBMIValue)
            tvWorkoutCount = safelyFindView(R.id.tvWorkoutCount)
            tvCaloriesBurned = safelyFindView(R.id.tvCaloriesBurned)
            tvTodoCount = safelyFindView(R.id.tvTodoCount)
            tvCaloriesProgress = safelyFindView(R.id.tvCaloriesProgress)
            tvWorkoutsProgress = safelyFindView(R.id.tvWorkoutsProgress)
            progressCalories = safelyFindView(R.id.progressCalories)
            progressWorkouts = safelyFindView(R.id.progressWorkouts)
            rvRecentActivities = safelyFindView(R.id.rvRecentActivities)
            layoutEmptyState = safelyFindView(R.id.layoutEmptyState)
            
            // Cards with error handling
            cardBMI = safelyFindView(R.id.cardBMI)
            cardWorkouts = safelyFindView(R.id.cardWorkouts)
            cardCalories = safelyFindView(R.id.cardCalories)
            cardStartWorkout = safelyFindView(R.id.cardStartWorkout)
            cardBMICalc = safelyFindView(R.id.cardBMICalc)
            cardPoseAI = safelyFindView(R.id.cardPoseAI)
            
            // Dropdown menu components
            btnProfileMenu = safelyFindView(R.id.btnProfileMenu)
            dropdownMenu = safelyFindView(R.id.dropdownMenu)
            
            // Setup RecyclerView with error handling
            rvRecentActivities?.let { rv ->
                rv.layoutManager = LinearLayoutManager(this)
            }
            
            Log.d("DashboardActivity", "Views initialized successfully")
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error initializing views", e)
            handleCriticalError("Failed to initialize interface", e)
        }
    }

    private fun setupClickListeners() {
        try {
            // Profile menu button
            btnProfileMenu?.setOnClickListener {
                toggleDropdownMenu()
            }
            
            // Dropdown menu items
            safelyFindView<LinearLayout>(R.id.menuAbout)?.setOnClickListener {
                showAboutDialog()
                hideDropdownMenu()
            }
            
            safelyFindView<LinearLayout>(R.id.menuSettings)?.setOnClickListener {
                navigateToSettings()
                hideDropdownMenu()
            }
            
            safelyFindView<LinearLayout>(R.id.menuLogout)?.setOnClickListener {
                logoutUser()
                hideDropdownMenu()
            }
            
            // Card click listeners
            cardStartWorkout?.setOnClickListener {
                startActivity(Intent(this, WorkoutListActivity::class.java))
            }
            
            cardBMICalc?.setOnClickListener {
                startActivity(Intent(this, BmiCalculatorActivity::class.java))
            }
            
            cardPoseAI?.setOnClickListener {
                startActivity(Intent(this, PoseActivity::class.java))
            }
            
            // Click outside to close dropdown
            findViewById<View>(android.R.id.content).setOnClickListener {
                hideDropdownMenu()
            }
            
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error setting up click listeners", e)
        }
    }

    private fun setupRealtimeListeners() {
        try {
            // Setup user info listener
            userInfoListener = firestore.collection("users")
                .document(userId)
                .addSnapshotListener { snapshot, error ->
                    try {
                        if (error != null) {
                            Log.e("DashboardActivity", "Error listening to user info", error)
                            return@addSnapshotListener
                        }
                        
                        if (snapshot != null && snapshot.exists()) {
                            val personalInfo = snapshot.get("personalInfo") as? Map<String, Any>
                            val bmiInfo = snapshot.get("bmiInfo") as? Map<String, Any>
                            
                            mainHandler.post {
                                try {
                                    // Update UI on main thread
                                    updateUserInfo(personalInfo, bmiInfo)
                                } catch (e: Exception) {
                                    Log.e("DashboardActivity", "Error updating user info UI", e)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("DashboardActivity", "Error in user info listener", e)
                    }
                }
            
            // Setup separate listeners for workout and meal todos
            val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            
            // Workout todos listener
            workoutTodoListener = firestore.collection("userTodoList")
                .document(userId)
                .collection("workoutPlan")
                .whereEqualTo("scheduledDate", today)
                .addSnapshotListener { snapshot, error ->
                    try {
                        if (error != null) {
                            Log.e("DashboardActivity", "Error listening to workout todos", error)
                            return@addSnapshotListener
                        }
                        
                        val workoutTodos = snapshot?.documents?.mapNotNull { doc ->
                            try {
                                // Use Map to avoid data class conflicts
                                val data = doc.data
                                if (data != null) {
                                    WorkoutTodo(
                                        id = doc.id,
                                        workoutName = data["workoutName"] as? String ?: "",
                                        sets = (data["sets"] as? Number)?.toInt() ?: 0,
                                        reps = (data["reps"] as? Number)?.toInt() ?: 0,
                                        estimatedCalories = (data["estimatedCalories"] as? Number)?.toInt() ?: 0,
                                        scheduledDate = data["scheduledDate"] as? String ?: "",
                                        isCompleted = data["isCompleted"] as? Boolean ?: false,
                                        createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                                    )
                                } else null
                            } catch (e: Exception) {
                                Log.w("DashboardActivity", "Error parsing workout todo: ${doc.id}", e)
                                null
                            }
                        } ?: emptyList()
                        
                        // Update dashboard with workout data
                        updateDashboardWithWorkoutData(workoutTodos)
                        
                    } catch (e: Exception) {
                        Log.e("DashboardActivity", "Error in workout todo listener", e)
                    }
                }
            
            // Meal todos listener
            mealTodoListener = firestore.collection("userTodoList")
                .document(userId)
                .collection("mealPlan")
                .whereEqualTo("scheduledDate", today)
                .addSnapshotListener { snapshot, error ->
                    try {
                        if (error != null) {
                            Log.e("DashboardActivity", "Error listening to meal todos", error)
                            return@addSnapshotListener
                        }
                        
                        val mealTodos = snapshot?.documents?.mapNotNull { doc ->
                            try {
                                // Use Map to avoid data class conflicts
                                val data = doc.data
                                if (data != null) {
                                    MealTodo(
                                        id = doc.id,
                                        mealName = data["mealName"] as? String ?: "",
                                        mealType = data["mealType"] as? String ?: "",
                                        calories = (data["calories"] as? Number)?.toInt() ?: 0,
                                        scheduledDate = data["scheduledDate"] as? String ?: "",
                                        isCompleted = data["isCompleted"] as? Boolean ?: false,
                                        createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                                    )
                                } else null
                            } catch (e: Exception) {
                                Log.w("DashboardActivity", "Error parsing meal todo: ${doc.id}", e)
                                null
                            }
                        } ?: emptyList()
                        
                        // Update dashboard with meal data
                        updateDashboardWithMealData(mealTodos)
                        
                    } catch (e: Exception) {
                        Log.e("DashboardActivity", "Error in meal todo listener", e)
                    }
                }
                
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error setting up real-time listeners", e)
        }
    }
    
    // Separate methods for updating dashboard data
    private fun updateDashboardWithWorkoutData(workoutTodos: List<WorkoutTodo>) {
        try {
            val completedWorkouts = workoutTodos.count { it.isCompleted }
            val totalCaloriesBurned = workoutTodos.filter { it.isCompleted }.sumOf { it.estimatedCalories }
            
            mainHandler.post {
                try {
                    tvWorkoutCount?.text = completedWorkouts.toString()
                    tvCaloriesBurned?.text = "$totalCaloriesBurned cal"
                    
                    // Update progress bars
                    val caloriesProgress = if (totalCaloriesBurned > 0) {
                        (totalCaloriesBurned * 100) / 2000
                    } else {
                        0
                    }
                    progressCalories?.progress = caloriesProgress.coerceAtMost(100)
                    tvCaloriesProgress?.text = "$totalCaloriesBurned cal"
                    
                    // Update recent activities
                    updateRecentActivities(workoutTodos, emptyList())
                    
                } catch (e: Exception) {
                    Log.e("DashboardActivity", "Error updating workout data UI", e)
                }
            }
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error updating workout data", e)
        }
    }
    
    private fun updateDashboardWithMealData(mealTodos: List<MealTodo>) {
        try {
            val completedMeals = mealTodos.count { it.isCompleted }
            val totalCaloriesConsumed = mealTodos.filter { it.isCompleted }.sumOf { it.calories }
            
            mainHandler.post {
                try {
                    // Update recent activities with meal data
                    updateRecentActivities(emptyList(), mealTodos)
                    
                } catch (e: Exception) {
                    Log.e("DashboardActivity", "Error updating meal data UI", e)
                }
            }
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error updating meal data", e)
        }
    }

    private fun updateDashboardStats(
        totalTodos: Int,
        completedTodos: Int,
        caloriesBurned: Int,
        caloriesConsumed: Int,
        workoutTodos: List<WorkoutTodo>,
        mealTodos: List<MealTodo>
    ) {
        try {
            // Update todo count
            tvTodoCount?.text = "$completedTodos/$totalTodos"
            
            // Update calories burned
            tvCaloriesBurned?.text = "$caloriesBurned cal"
            
            // Update workout count (completed workouts only)
            val completedWorkouts = workoutTodos.count { it.isCompleted }
            tvWorkoutCount?.text = completedWorkouts.toString()
            
            // Update progress bars
            val completionProgress = if (totalTodos > 0) {
                (completedTodos * 100) / totalTodos
            } else {
                0
            }
            
            progressWorkouts?.progress = completionProgress.coerceAtMost(100)
            tvWorkoutsProgress?.text = "$completedTodos/$totalTodos"
            
            // Update calories progress (assuming 2000 cal daily goal)
            val caloriesProgress = if (caloriesBurned > 0) {
                (caloriesBurned * 100) / 2000
            } else {
                0
            }
            progressCalories?.progress = caloriesProgress.coerceAtMost(100)
            tvCaloriesProgress?.text = "$caloriesBurned cal"
            
            // Update motivation text based on progress
            updateMotivationText(completedTodos, totalTodos, caloriesBurned)
            
            // Update recent activities
            updateRecentActivities(workoutTodos, mealTodos)
            
            Log.d("DashboardActivity", "Dashboard updated: $completedTodos/$totalTodos completed, $caloriesBurned cal burned")
            
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error updating dashboard stats", e)
        }
    }

    private fun updateMotivationText(completedTodos: Int, totalTodos: Int, caloriesBurned: Int) {
        try {
            val motivationText = when {
                totalTodos == 0 -> "Ready to crush your goals today? 💪"
                completedTodos == totalTodos -> "Amazing! You've completed all your tasks! 🎉"
                completedTodos > totalTodos / 2 -> "You're doing great! Keep it up! 🔥"
                completedTodos > 0 -> "Good start! You've got this! 💪"
                else -> "Time to get started! Your future self will thank you! ⚡"
            }
            
            tvMotivation?.text = motivationText
            
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error updating motivation text", e)
        }
    }

    private fun updateRecentActivities(workoutTodos: List<WorkoutTodo>, mealTodos: List<MealTodo>) {
        try {
            val recentActivities = mutableListOf<RecentActivity>()
            
            // Add completed workout activities
            workoutTodos.filter { it.isCompleted }
                .sortedByDescending { it.createdAt }
                .take(5)
                .forEach { todo ->
                    recentActivities.add(
                        RecentActivity(
                            title = todo.workoutName,
                            subtitle = "${todo.sets} sets × ${todo.reps} reps - ${todo.estimatedCalories} cal burned",
                            timestamp = todo.createdAt
                        )
                    )
                }
            
            // Add completed meal activities
            mealTodos.filter { it.isCompleted }
                .sortedByDescending { it.createdAt }
                .take(5)
                .forEach { todo ->
                    recentActivities.add(
                        RecentActivity(
                            title = todo.mealName,
                            subtitle = "${todo.mealType} - ${todo.calories} cal consumed",
                            timestamp = todo.createdAt
                        )
                    )
                }
            
            // Sort by timestamp and take top 10
            val sortedActivities = recentActivities.sortedByDescending { it.timestamp }.take(10)
            
            // Update RecyclerView if it exists
            rvRecentActivities?.let { rv ->
                if (sortedActivities.isNotEmpty()) {
                    layoutEmptyState?.visibility = View.GONE
                    rv.visibility = View.VISIBLE
                    
                    // Create adapter if not exists
                    if (rv.adapter == null) {
                        val adapter = RecentActivitiesAdapter(sortedActivities)
                        rv.adapter = adapter
                    } else {
                        (rv.adapter as? RecentActivitiesAdapter)?.updateActivities(sortedActivities)
                    }
                } else {
                    layoutEmptyState?.visibility = View.VISIBLE
                    rv.visibility = View.GONE
                }
            }
            
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error updating recent activities", e)
        }
    }

    private fun updateUserInfo(personalInfo: Map<String, Any>?, bmiInfo: Map<String, Any>?) {
        try {
            // Update welcome message and name
            val firstName = personalInfo?.get("firstName") as? String ?: ""
            val lastName = personalInfo?.get("lastName") as? String ?: ""
            val fullName = "$firstName $lastName".trim()
            
            tvWelcome?.text = "Welcome back!"
            tvFullName?.text = if (fullName.isNotEmpty()) fullName else "User"
            
            // Update BMI
            val currentBMI = bmiInfo?.get("currentBMI") as? Double ?: 0.0
            tvBMIValue?.text = String.format("%.1f", currentBMI)
            
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error updating user info", e)
        }
    }

    // 🎯 TOGGLE DROPDOWN MENU
    private fun toggleDropdownMenu() {
        try {
            dropdownMenu?.let { menu ->
                if (menu.visibility == View.VISIBLE) {
                    // Hide with animation
                    menu.animate()
                        .alpha(0f)
                        .translationY(-20f)
                        .setDuration(200)
                        .withEndAction {
                            menu.visibility = View.GONE
                            menu.alpha = 1f
                            menu.translationY = 0f
                        }
                        .start()
                } else {
                    // Show with animation
                    menu.alpha = 0f
                    menu.translationY = -20f
                    menu.visibility = View.VISIBLE
                    menu.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(200)
                        .start()
                }
            }
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error toggling dropdown menu", e)
        }
    }

    // 🎯 HIDE DROPDOWN MENU
    private fun hideDropdownMenu() {
        try {
            dropdownMenu?.let { menu ->
                if (menu.visibility == View.VISIBLE) {
                    menu.animate()
                        .alpha(0f)
                        .translationY(-20f)
                        .setDuration(200)
                        .withEndAction {
                            menu.visibility = View.GONE
                            menu.alpha = 1f
                            menu.translationY = 0f
                        }
                        .start()
                }
            }
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error hiding dropdown menu", e)
        }
    }

    // ℹ️ SHOW ABOUT DIALOG
    private fun showAboutDialog() {
        try {
            // Navigate to AboutActivity instead of showing dialog
            val intent = Intent(this, AboutActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error navigating to AboutActivity", e)
            // Fallback to dialog if navigation fails
            AlertDialog.Builder(this)
                .setTitle("About Physique AI")
                .setMessage("""
                    Physique AI v1.0
                    
                    Created by BSCS 4-3
                    
                    Your personal fitness companion powered by AI.
                    
                    Features:
                    • AI-powered workout recommendations
                    • Real-time pose detection
                    • Personalized meal planning
                    • Progress tracking
                    • BMI calculator
                    
                    Built with ❤️ for your fitness journey.
                """.trimIndent())
                .setPositiveButton("OK", null)
                .show()
        }
    }

    // ⚙️ NAVIGATE TO SETTINGS
    private fun navigateToSettings() {
        try {
            // Navigate to ProfileActivity for settings
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error navigating to ProfileActivity", e)
            // Fallback to dialog if navigation fails
            AlertDialog.Builder(this)
                .setTitle("Settings")
                .setMessage("Settings feature coming soon!")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    // 🚪 LOGOUT USER
    private fun logoutUser() {
        try {
            AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Yes") { _, _ ->
                    performLogout()
                }
                .setNegativeButton("No", null)
                .show()
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error showing logout dialog", e)
        }
    }

    // 🚪 PERFORM LOGOUT
    private fun performLogout() {
        try {
            // Sign out from Firebase
            FirebaseAuth.getInstance().signOut()
            
            // Clear any stored user data
            val sharedPrefs = getSharedPreferences("PhysiqueAI", Context.MODE_PRIVATE)
            sharedPrefs.edit().clear().apply()
            
            // Navigate to login screen and clear activity stack
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error performing logout", e)
            // Fallback: just navigate to login
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun setupBottomNavigation() {
        try {
            val bottomNavigation = safelyFindView<BottomNavigationView>(R.id.bottom_navigation)
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
                        showError("Navigation failed")
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
            showError("Failed to open ${activityClass.simpleName}")
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

    private fun showError(message: String) {
        try {
            runOnUiThread {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error showing toast", e)
        }
    }

    private fun handleCriticalError(message: String, error: Exception) {
        try {
            Log.e("DashboardActivity", "Critical error: $message", error)
            
            // Show user-friendly error message
            runOnUiThread {
                try {
                    AlertDialog.Builder(this)
                        .setTitle("Error")
                        .setMessage("$message\n\nPlease try again or restart the app.")
                        .setPositiveButton("OK") { _, _ ->
                            finish()
                        }
                        .setCancelable(false)
                        .show()
                } catch (e: Exception) {
                    Log.e("DashboardActivity", "Error showing error dialog", e)
                    finish()
                }
            }
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error handling critical error", e)
            finish()
        }
    }

    // 🧹 CLEANUP LISTENERS ON DESTROY
    override fun onDestroy() {
        try {
            // Clean up listeners to prevent memory leaks
            userInfoListener?.remove()
            workoutTodoListener?.remove()
            mealTodoListener?.remove()
            
            // Remove callbacks to prevent memory leaks
            mainHandler.removeCallbacksAndMessages(null)
            
            // Shutdown background executor
            backgroundExecutor.shutdown()
            
            super.onDestroy()
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error in onDestroy", e)
            super.onDestroy()
        }
    }

    override fun onPause() {
        try {
            // Hide dropdown menu when activity is paused
            hideDropdownMenu()
            super.onPause()
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Error in onPause", e)
            super.onPause()
        }
    }
} 