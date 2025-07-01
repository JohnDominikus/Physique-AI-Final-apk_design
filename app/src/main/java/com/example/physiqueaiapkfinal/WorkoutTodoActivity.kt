package com.example.physiqueaiapkfinal

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import com.example.physiqueaiapkfinal.WorkoutTodo
import com.example.physiqueaiapkfinal.UserMedicalInfo
import android.view.View

// Import UserMedicalInfo from DietaryTodoActivity

// Data Models
data class WorkoutItem(
    val id: String = "",
    val name: String = "",
    val gif_url: String = "",
    val muscle_groups: String = "",
    val safety_warning: String = "",
    val calories_per_minute: Int = 0
)

class WorkoutTodoActivity : AppCompatActivity() {
    
    private lateinit var workoutSpinner: Spinner
    private lateinit var etSets: EditText
    private lateinit var etReps: EditText
    private lateinit var etMinutes: EditText
    private lateinit var etSeconds: EditText
    private lateinit var btnSelectDate: Button
    private lateinit var btnAddWorkout: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvWarning: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var cardLoading: androidx.cardview.widget.CardView
    private lateinit var tvLoadingText: TextView
    private lateinit var btnPlusReps: ImageButton
    private lateinit var btnMinusReps: ImageButton
    private lateinit var btnPlusMinutes: ImageButton
    private lateinit var btnMinusMinutes: ImageButton
    private lateinit var btnPlusSeconds: ImageButton
    private lateinit var btnMinusSeconds: ImageButton
    private lateinit var btnPlusSets: ImageButton
    private lateinit var btnMinusSets: ImageButton
    private lateinit var etWorkoutSearch: EditText
    
    private val firestore = FirebaseFirestore.getInstance()
    private val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private val workoutList = mutableListOf<WorkoutItem>()
    private val todoList = mutableListOf<WorkoutTodo>()
    private lateinit var adapter: WorkoutTodoAdapter
    private var selectedDate: String = ""
    private var userMedicalInfo: UserMedicalInfo? = null
    private var todoListener: ListenerRegistration? = null
    
    // Async handling
    private val backgroundExecutor: ExecutorService = Executors.newFixedThreadPool(3)
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Loading states
    private var isDataLoaded = false
    private var loadedComponents = 0
    private val totalComponents = 3 // workouts, medical info, todo list
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workout_todo)
        
        try {
        initViews()
        setupRecyclerView()
        setupClickListeners()
            
            // Load data asynchronously
            loadDataAsync()
        } catch (e: Exception) {
            handleError("Initialization error", e)
        }
    }
    
    private fun initViews() {
        try {
            workoutSpinner = findViewById(R.id.spinnerWorkout)
            etSets = findViewById(R.id.etSets)
            etReps = findViewById(R.id.etReps)
            etMinutes = findViewById(R.id.etMinutes)
            etSeconds = findViewById(R.id.etSeconds)
            btnSelectDate = findViewById(R.id.btnSelectDate)
            btnAddWorkout = findViewById(R.id.btnAddWorkout)
            recyclerView = findViewById(R.id.recyclerWorkoutTodos)
            tvWarning = findViewById(R.id.tvWarning)
            btnPlusReps = findViewById(R.id.btnPlusReps)
            btnMinusReps = findViewById(R.id.btnMinusReps)
            btnPlusMinutes = findViewById(R.id.btnPlusMinutes)
            btnMinusMinutes = findViewById(R.id.btnMinusMinutes)
            btnPlusSeconds = findViewById(R.id.btnPlusSeconds)
            btnMinusSeconds = findViewById(R.id.btnMinusSeconds)
            btnPlusSets = findViewById(R.id.btnPlusSets)
            btnMinusSets = findViewById(R.id.btnMinusSets)
            etWorkoutSearch = findViewById(R.id.etWorkoutSearch)
            // Safe initialization for progressBar and cardLoading
            try { progressBar = findViewById(R.id.progressBar) } catch (_: Exception) {}
            try { cardLoading = findViewById(R.id.cardLoading) } catch (_: Exception) {}
            btnAddWorkout.isEnabled = false
            workoutSpinner.isEnabled = false
            val calendar = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            selectedDate = dateFormat.format(calendar.time)
            updateDateButtonText()
        } catch (e: Exception) {
            Log.e("WorkoutTodo", "Error initializing views", e)
            handleError("Failed to initialize interface", e)
        }
    }
    
    private fun updateDateButtonText() {
        try {
            btnSelectDate.text = selectedDate
        } catch (e: Exception) {
            Log.e("WorkoutTodo", "Error updating date button text", e)
        }
    }
    
    private fun setupRecyclerView() {
        adapter = WorkoutTodoAdapter(
            todoList,
            onToggleCompletion = { todo -> toggleTodoCompletion(todo) },
            onDelete = { todo -> deleteTodo(todo) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }
    
    private fun setupClickListeners() {
        try {
            // Back button
            findViewById<ImageButton>(R.id.btnBack)?.setOnClickListener {
                finish()
            }
            
            // Sets +/- buttons
            findViewById<ImageButton>(R.id.btnPlusSets)?.setOnClickListener {
                try {
                    val currentValue = findViewById<EditText>(R.id.etSets)?.text?.toString()?.toIntOrNull() ?: 0
                    findViewById<EditText>(R.id.etSets)?.setText((currentValue + 1).toString())
                } catch (e: Exception) {
                    Log.e("WorkoutTodo", "Error incrementing sets", e)
                }
            }
            
            findViewById<ImageButton>(R.id.btnMinusSets)?.setOnClickListener {
                try {
                    val currentValue = findViewById<EditText>(R.id.etSets)?.text?.toString()?.toIntOrNull() ?: 0
                    if (currentValue > 0) {
                        findViewById<EditText>(R.id.etSets)?.setText((currentValue - 1).toString())
                    }
                } catch (e: Exception) {
                    Log.e("WorkoutTodo", "Error decrementing sets", e)
                }
            }
            
            // Reps +/- buttons
            btnPlusReps?.setOnClickListener {
                try {
                    val currentValue = etReps?.text?.toString()?.toIntOrNull() ?: 0
                    etReps?.setText((currentValue + 1).toString())
                } catch (e: Exception) {
                    Log.e("WorkoutTodo", "Error incrementing reps", e)
                }
            }
            
            btnMinusReps?.setOnClickListener {
                try {
                    val currentValue = etReps?.text?.toString()?.toIntOrNull() ?: 0
                    if (currentValue > 0) {
                        etReps?.setText((currentValue - 1).toString())
                    }
                } catch (e: Exception) {
                    Log.e("WorkoutTodo", "Error decrementing reps", e)
                }
            }
            
            // Minutes +/- buttons
            btnPlusMinutes?.setOnClickListener {
                try {
                    val currentValue = etMinutes?.text?.toString()?.toIntOrNull() ?: 0
                    etMinutes?.setText((currentValue + 1).toString())
                } catch (e: Exception) {
                    Log.e("WorkoutTodo", "Error incrementing minutes", e)
                }
            }
            
            btnMinusMinutes?.setOnClickListener {
                try {
                    val currentValue = etMinutes?.text?.toString()?.toIntOrNull() ?: 0
                    if (currentValue > 0) {
                        etMinutes?.setText((currentValue - 1).toString())
                    }
                } catch (e: Exception) {
                    Log.e("WorkoutTodo", "Error decrementing minutes", e)
                }
            }
            
            // Seconds +/- buttons
            btnPlusSeconds?.setOnClickListener {
                try {
                    val currentValue = etSeconds?.text?.toString()?.toIntOrNull() ?: 0
                    etSeconds?.setText((currentValue + 1).toString())
                } catch (e: Exception) {
                    Log.e("WorkoutTodo", "Error incrementing seconds", e)
                }
            }
            
            btnMinusSeconds?.setOnClickListener {
                try {
                    val currentValue = etSeconds?.text?.toString()?.toIntOrNull() ?: 0
                    if (currentValue > 0) {
                        etSeconds?.setText((currentValue - 1).toString())
                    }
                } catch (e: Exception) {
                    Log.e("WorkoutTodo", "Error decrementing seconds", e)
                }
            }
            
            // Date selection
            btnSelectDate?.setOnClickListener {
                showDatePicker()
            }
            
            // Add workout button
            btnAddWorkout?.setOnClickListener {
                addWorkoutTodo()
            }
            
        } catch (e: Exception) {
            Log.e("WorkoutTodo", "Error setting up click listeners", e)
            handleError("Failed to setup interactions", e)
        }
    }
    
    private fun loadDataAsync() {
        showLoading(true)
        
        // Load data in sequence to prevent overwhelming Firebase
        loadUserMedicalInfoAsync {
            loadWorkoutDataAsync {
                loadTodoListAsync {
                    onAllDataLoaded()
                }
            }
        }
    }
    
    private fun loadUserMedicalInfoAsync(onComplete: () -> Unit) {
        backgroundExecutor.execute {
            try {
                firestore.collection("userMedicalInfo")
                    .document(userId)
                    .get()
                    .addOnSuccessListener { document ->
                        try {
                            if (document.exists()) {
                                userMedicalInfo = document.toObject(UserMedicalInfo::class.java)
                            }
                            incrementLoadedComponents()
                            onComplete()
                        } catch (e: Exception) {
                            handleError("Error parsing medical info", e)
                            onComplete()
                        }
                    }
                    .addOnFailureListener { e ->
                        handleError("Failed to load medical info", e)
                        onComplete()
                    }
            } catch (e: Exception) {
                handleError("Medical info loading error", e)
                onComplete()
            }
        }
    }
    
    private fun loadWorkoutDataAsync(onComplete: () -> Unit) {
        backgroundExecutor.execute {
            try {
                firestore.collection("workoutcollection")
                    .limit(50)
                    .get()
                    .addOnSuccessListener { docs ->
                        mainHandler.post {
                            workoutList.clear()
                            docs.forEach { d ->
                                d.toObject(WorkoutItem::class.java)
                                    ?.copy(id = d.id)
                                    ?.let { workoutList.add(it) }
                            }
                            setupWorkoutSpinner()
                            incrementLoadedComponents()
                            onComplete()
                        }
                    }
                    .addOnFailureListener { e ->
                        handleError("Failed to load workouts", e)
                        mainHandler.post {
                            setupWorkoutSpinner()
                            incrementLoadedComponents()
                            onComplete()
                        }
                    }
            } catch (e: Exception) {
                handleError("Workout data loading error", e)
                mainHandler.post {
                    setupWorkoutSpinner()
                    incrementLoadedComponents()
                    onComplete()
                }
            }
        }
    }
    
    private fun setupWorkoutSpinner() {
        try {
            if (workoutList.isEmpty()) {
                showError("No workouts available")
                workoutSpinner.isEnabled = false
                return
            }

            val options = mutableListOf("Select Exercise")
            options.addAll(workoutList.map { it.name })

            val adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                options
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

            mainHandler.post {
                workoutSpinner.adapter = adapter
                workoutSpinner.isEnabled = true
            }

            /* search/filter */
            etWorkoutSearch.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val filtered = workoutList
                        .filter { it.name.contains(s.toString(), true) }
                        .map { it.name }
                    val fAdapter = ArrayAdapter(
                        this@WorkoutTodoActivity,
                        android.R.layout.simple_spinner_item,
                        mutableListOf("Select Exercise").apply { addAll(filtered) }
                    ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                    mainHandler.post { workoutSpinner.adapter = fAdapter }
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        } catch (e: Exception) {
            handleError("Error setting up workout spinner", e)
        }
    }
    
    private fun loadTodoListAsync(onComplete: () -> Unit) {
        try {
            todoListener?.remove()
            todoListener = firestore.collection("userTodoList")
                .document(userId)
                .collection("workoutPlan")
                .whereEqualTo("scheduledDate", selectedDate)
                .addSnapshotListener { snapshots, e ->
                    if (e != null) {
                        handleError("Todo list listener error", e)
                        onComplete()
                        return@addSnapshotListener
                    }
                    try {
                        mainHandler.post {
                            todoList.clear()
                            snapshots?.let {
                                for (doc in it) {
                                    val todo = doc.toObject(WorkoutTodo::class.java).copy(id = doc.id)
                                    todoList.add(todo)
                                }
                            }
                            adapter.notifyDataSetChanged()
                            updateProgressCards()
                            if (loadedComponents < totalComponents) {
                                incrementLoadedComponents()
                                onComplete()
                            }
                        }
                    } catch (e: Exception) {
                        handleError("Error updating todo list", e)
                        onComplete()
                    }
                }
        } catch (e: Exception) {
            handleError("Todo list setup error", e)
            onComplete()
        }
    }
    
    private fun incrementLoadedComponents() {
        loadedComponents++
        updateLoadingProgress()
    }
    
    private fun updateLoadingProgress() {
        val progress = (loadedComponents * 100) / totalComponents
        val loadingMessage = when (loadedComponents) {
            0 -> "Loading workout data..."
            1 -> "Loading medical information..."
            2 -> "Loading your workout plans..."
            else -> "Almost ready..."
        }
        
        mainHandler.post {
            progressBar.progress = progress
            try {
                tvLoadingText.text = loadingMessage
            } catch (e: Exception) {
                // Fallback if loading text view doesn't exist
            }
        }
    }
    
    private fun onAllDataLoaded() {
        isDataLoaded = true
        mainHandler.post {
            showLoading(false)
            btnAddWorkout.isEnabled = true
            Toast.makeText(this, "Ready to plan your workouts!", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showLoading(show: Boolean) {
        try {
            if (::cardLoading.isInitialized) {
                cardLoading.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
            }
            if (::progressBar.isInitialized) {
                progressBar.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
            }
        } catch (e: Exception) {
            // Fallback: do nothing
        }
    }
    
    private fun handleError(message: String, exception: Exception) {
        android.util.Log.e("WorkoutTodoActivity", "$message: ${exception.message}", exception)
        mainHandler.post {
            showLoading(false)
            showError("$message. Please try again.")
            btnAddWorkout.isEnabled = false
        }
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun checkMedicalWarnings(workout: WorkoutItem) {
        try {
            var msg = ""
            if (workout.safety_warning.isNotEmpty())
                msg += "⚠️ Safety Warning: ${workout.safety_warning}\n"

            val groups = workout.muscle_groups.toGroupList()
            userMedicalInfo?.let { med ->
                val relevant = med.injuries.filter { inj ->
                    groups.any { mus -> inj.contains(mus, true) || mus.contains(inj, true) }
                }
                if (relevant.isNotEmpty())
                    msg += "🚨 Medical Alert: You have reported injuries in: ${relevant.joinToString(", ")}. " +
                           "This workout targets: ${groups.joinToString(", ")}.\n"

                if (med.fitnessLevel == "Beginner" && groups.contains("Core"))
                    msg += "💡 Beginner Tip: Start with lower intensity and gradually increase.\n"
            }

            mainHandler.post {
                tvWarning.text = msg
                tvWarning.visibility = if (msg.isNotEmpty()) View.VISIBLE else View.GONE
            }
        } catch (e: Exception) {
            handleError("Error checking medical warnings", e)
        }
    }
    
    private fun showDatePicker() {
        try {
            val calendar = Calendar.getInstance()
            val dateParts = selectedDate.split("/")
            if (dateParts.size == 3) {
                calendar.set(Calendar.DAY_OF_MONTH, dateParts[0].toInt())
                calendar.set(Calendar.MONTH, dateParts[1].toInt() - 1)
                calendar.set(Calendar.YEAR, dateParts[2].toInt())
            }
            val datePickerDialog = DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    val cal = Calendar.getInstance()
                    cal.set(year, month, dayOfMonth)
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    selectedDate = dateFormat.format(cal.time)
                    updateDateButtonText()
                    if (isDataLoaded) {
                        loadTodoListAsync {}
                    }
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            datePickerDialog.show()
        } catch (e: Exception) {
            handleError("Error showing date picker", e)
        }
    }
    
    private fun addWorkoutTodo() {
        try {
            if (!isDataLoaded || workoutList.isEmpty()) {
                showError("Please wait for workouts to load")
                return
            }
            val selectedPosition = workoutSpinner.selectedItemPosition
            if (selectedPosition <= 0) {
                showError("Please select an exercise from the dropdown")
                return
            }
            val workoutIndex = selectedPosition - 1
            if (workoutIndex < 0 || workoutIndex >= workoutList.size) {
                showError("Please select a valid workout")
                return
            }
            val selectedWorkout = workoutList[workoutIndex]
            val sets = etSets.text.toString().toIntOrNull() ?: 0
            val reps = etReps.text.toString().toIntOrNull() ?: 0
            val minutes = etMinutes.text.toString().toIntOrNull() ?: 0
            val seconds = etSeconds.text.toString().toIntOrNull() ?: 0
            if ((sets <= 0 || reps <= 0) && (minutes <= 0 && seconds <= 0)) {
                showError("Please enter valid sets & reps or time")
                return
            }
            createWorkoutTodoAsync(selectedWorkout, sets, reps, minutes, seconds)
        } catch (e: Exception) {
            handleError("Error adding workout", e)
        }
    }
    
    private fun createWorkoutTodoAsync(workout: WorkoutItem, sets: Int, reps: Int, minutes: Int, seconds: Int) {
        backgroundExecutor.execute {
            try {
                val totalMinutes = minutes + (seconds / 60.0)
                val estimatedCalories = (workout.calories_per_minute * totalMinutes).toInt()
                val workoutTodo = WorkoutTodo(
                    id = "",
                    workoutName = workout.name,
                    sets = sets,
                    reps = reps,
                    minutes = minutes,
                    seconds = seconds,
                    scheduledDate = selectedDate,
                    userId = userId,
                    muscleGroups = workout.muscle_groups.toGroupList(),
                    estimatedCalories = estimatedCalories,
                    isCompleted = false,
                    durationMinutes = if (minutes > 0 || seconds > 0) minutes + (seconds / 60) else 0
                )
                firestore.collection("userTodoList")
                    .document(userId)
                    .collection("workoutPlan")
                    .add(workoutTodo)
                    .addOnSuccessListener {
                        // Magdagdag ng activity log sa recentActivities
                        val activity = mapOf(
                            "title" to workout.name,
                            "subtitle" to "$sets sets × $reps reps - $estimatedCalories cal scheduled",
                            "timestamp" to System.currentTimeMillis(),
                            "type" to "workout",
                            "caloriesBurned" to estimatedCalories,
                            "date" to selectedDate,
                            "userId" to userId
                        )
                        firestore.collection("userActivities")
                            .document(userId)
                            .collection("recentActivities")
                            .add(activity)
                        mainHandler.post {
                            Toast.makeText(this@WorkoutTodoActivity, "✅ Workout added to your plan!", Toast.LENGTH_SHORT).show()
                            clearInputs()
                            updateProgressCards()
                        }
                    }
                    .addOnFailureListener { e ->
                        handleError("Failed to add workout", e)
                    }
            } catch (e: Exception) {
                handleError("Error creating workout todo", e)
            }
        }
    }
    
    private fun toggleTodoCompletion(todo: WorkoutTodo) {
        backgroundExecutor.execute {
            try {
                val updatedTodo = todo.copy(isCompleted = !todo.isCompleted)
                
                firestore.collection("userTodoList")
                    .document(userId)
                    .collection("workoutPlan")
                    .document(todo.id)
                    .set(updatedTodo)
                    .addOnSuccessListener {
                        // Update calories burned if completed
                        if (updatedTodo.isCompleted) {
                            updateCaloriesBurnedAsync(todo.estimatedCalories)
                            addToRecentActivities(todo)
                        }
                        // Update progress cards immediately
                        mainHandler.post {
                            updateProgressCards()
                            Toast.makeText(
                                this@WorkoutTodoActivity, 
                                if (updatedTodo.isCompleted) "✅ ${todo.workoutName} completed!" else "⏳ ${todo.workoutName} marked as pending",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        handleError("Failed to update workout", e)
                    }
            } catch (e: Exception) {
                handleError("Error toggling completion", e)
            }
        }
    }
    
    private fun updateCaloriesBurnedAsync(calories: Int) {
        try {
            val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            
            // Update userStats collection for dashboard integration
            firestore.collection("userStats")
                .document(userId)
                .collection("dailyStats")
                .document(today)
                .get()
                .addOnSuccessListener { document ->
                    val currentCalories = document.getLong("caloriesBurned")?.toInt() ?: 0
                    val currentWorkouts = document.getLong("workoutsCompleted")?.toInt() ?: 0
                    val updatedCalories = currentCalories + calories
                    val updatedWorkouts = currentWorkouts + 1
                    
                    val statsData = mapOf(
                        "caloriesBurned" to updatedCalories,
                        "workoutsCompleted" to updatedWorkouts,
                        "lastUpdated" to System.currentTimeMillis(),
                        "date" to today,
                        "userId" to userId
                    )
                    
                    firestore.collection("userStats")
                        .document(userId)
                        .collection("dailyStats")
                        .document(today)
                        .set(statsData, com.google.firebase.firestore.SetOptions.merge())
                        .addOnSuccessListener {
                            Log.d("WorkoutTodo", "Dashboard stats updated: Calories=$updatedCalories, Workouts=$updatedWorkouts")
                            
                            // Also update the main userStats document for quick access
                            firestore.collection("userStats")
                                .document(userId)
                                .get()
                                .addOnSuccessListener { mainDoc ->
                                    val totalCaloriesBurned = mainDoc.getLong("totalCaloriesBurned")?.toInt() ?: 0
                                    val totalWorkoutsCompleted = mainDoc.getLong("totalWorkoutsCompleted")?.toInt() ?: 0
                                    
                                    firestore.collection("userStats")
                                        .document(userId)
                                        .set(mapOf(
                                            "totalCaloriesBurned" to (totalCaloriesBurned + calories),
                                            "totalWorkoutsCompleted" to (totalWorkoutsCompleted + 1),
                                            "todayCaloriesBurned" to updatedCalories,
                                            "todayWorkoutsCompleted" to updatedWorkouts,
                                            "lastUpdated" to System.currentTimeMillis()
                                        ), com.google.firebase.firestore.SetOptions.merge())
                                        .addOnSuccessListener {
                                            Log.d("WorkoutTodo", "Main userStats updated with today's progress")
                                        }
                                        .addOnFailureListener { e ->
                                            Log.e("WorkoutTodo", "Error updating main userStats", e)
                                        }
                                }
                                .addOnFailureListener { e ->
                                    Log.e("WorkoutTodo", "Error getting main userStats", e)
                                }
                        }
                        .addOnFailureListener { e ->
                            Log.e("WorkoutTodo", "Error updating calories burned", e)
                        }
                }
                .addOnFailureListener { e ->
                    Log.e("WorkoutTodo", "Error getting current calories", e)
                }
        } catch (e: Exception) {
            Log.e("WorkoutTodo", "Error updating calories burned", e)
        }
    }
    
    private fun addToRecentActivities(todo: WorkoutTodo) {
        try {
            val activity = mapOf(
                "title" to todo.workoutName,
                "subtitle" to "${todo.sets} sets × ${todo.reps} reps - ${todo.estimatedCalories} cal burned",
                "timestamp" to System.currentTimeMillis(),
                "type" to "workout",
                "caloriesBurned" to todo.estimatedCalories,
                "date" to todo.scheduledDate,
                "userId" to userId
            )
            
            // Add to recent activities for dashboard
            firestore.collection("userActivities")
                .document(userId)
                .collection("recentActivities")
                .add(activity)
                .addOnSuccessListener {
                    Log.d("WorkoutTodo", "Activity added to recent activities")
                    
                    // Also update the main activities document for quick access
                    firestore.collection("userActivities")
                        .document(userId)
                        .set(mapOf(
                            "lastActivity" to activity,
                            "lastUpdated" to System.currentTimeMillis()
                        ), com.google.firebase.firestore.SetOptions.merge())
                }
                .addOnFailureListener { e ->
                    Log.e("WorkoutTodo", "Error adding to recent activities", e)
                }
        } catch (e: Exception) {
            Log.e("WorkoutTodo", "Error adding to recent activities", e)
        }
    }
    
    private fun updateProgressCards() {
        try {
            val completedCount = todoList.count { it.isCompleted }
            val pendingCount = todoList.count { !it.isCompleted }
            val totalMinutes = todoList.sumOf { 
                if (it.isCompleted) it.minutes + (it.seconds / 60.0) else 0.0
            }.toInt()
            
            // Update progress card views
            findViewById<TextView>(R.id.tvCompletedCount)?.text = completedCount.toString()
            findViewById<TextView>(R.id.tvPendingCount)?.text = pendingCount.toString()
            findViewById<TextView>(R.id.tvTotalTime)?.text = "${totalMinutes}m"
            
        } catch (e: Exception) {
            android.util.Log.e("WorkoutTodo", "Error updating progress cards", e)
        }
    }
    
    private fun clearInputs() {
        try {
            workoutSpinner?.setSelection(0)
            findViewById<EditText>(R.id.etSets)?.setText("")
            etReps?.setText("")
            etMinutes?.setText("")
            etSeconds?.setText("")
        } catch (e: Exception) {
            android.util.Log.e("WorkoutTodo", "Error clearing inputs", e)
        }
    }
    
    private fun deleteTodo(todo: WorkoutTodo) {
        backgroundExecutor.execute {
            try {
                firestore.collection("userTodoList")
                    .document(userId)
                    .collection("workoutPlan")
                    .document(todo.id)
                    .delete()
                    .addOnSuccessListener {
                        mainHandler.post {
                            Toast.makeText(this@WorkoutTodoActivity, "✅ Workout deleted from your plan!", Toast.LENGTH_SHORT).show()
                            updateProgressCards()
                        }
                    }
                    .addOnFailureListener { e ->
                        handleError("Failed to delete workout", e)
                    }
            } catch (e: Exception) {
                handleError("Error deleting workout", e)
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            // Clean up resources to prevent memory leaks
            todoListener?.remove()
            
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
        } catch (e: Exception) {
            android.util.Log.e("WorkoutTodoActivity", "Error during cleanup", e)
        }
    }
    
    override fun onPause() {
        super.onPause()
        try {
            // Remove expensive listeners when not visible
            todoListener?.remove()
        } catch (e: Exception) {
            android.util.Log.e("WorkoutTodoActivity", "Error pausing", e)
        }
    }
    
    override fun onResume() {
        super.onResume()
        try {
            if (isDataLoaded && todoListener == null) {
                // Restart listener when activity resumes
                loadTodoListAsync {}
            }
        } catch (e: Exception) {
            android.util.Log.e("WorkoutTodoActivity", "Error resuming", e)
        }
    }
}

// Adapter for WorkoutTodo RecyclerView
class WorkoutTodoAdapter(
    private val todoList: List<WorkoutTodo>,
    private val onToggleCompletion: (WorkoutTodo) -> Unit,
    private val onDelete: (WorkoutTodo) -> Unit
) : RecyclerView.Adapter<WorkoutTodoAdapter.ViewHolder>() {
    
    class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val checkBox: CheckBox = view.findViewById(R.id.cbCompleted)
        val tvWorkoutName: TextView = view.findViewById(R.id.tvWorkoutName)
        val tvDetails: TextView = view.findViewById(R.id.tvDetails)
        val tvCalories: TextView = view.findViewById(R.id.tvCalories)
        val tvMuscles: TextView = view.findViewById(R.id.tvMuscles)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
        val ivCompletionOverlay: ImageView = view.findViewById(R.id.ivCompletionOverlay)
        val ivStatusIcon: ImageView = view.findViewById(R.id.ivStatusIcon)
        val cardTodo: androidx.cardview.widget.CardView = view.findViewById(R.id.cardTodo)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }
    
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_workout_todo, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        try {
            val todo = todoList[position]
            
            // Set checkbox state
            holder.checkBox.setOnCheckedChangeListener(null)
            holder.checkBox.isChecked = todo.isCompleted
            holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
                onToggleCompletion(todo.copy(isCompleted = isChecked))
            }
            
            // Set workout details
            holder.tvWorkoutName.text = todo.workoutName
            val details = if (todo.reps > 0) {
                "${todo.sets} sets × ${todo.reps} reps"
            } else {
                "${todo.minutes}:${String.format("%02d", todo.seconds)}"
            }
            holder.tvDetails.text = details
            holder.tvCalories.text = "${todo.estimatedCalories} cal"
            holder.tvMuscles.text = "Targets: ${todo.muscleGroups.joinToString(", ")}"
            
            // Update completion status styling
            if (todo.isCompleted) {
                // Completed styling
                holder.tvStatus.text = "✅ Completed"
                holder.tvStatus.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.status_completed))
                holder.ivStatusIcon.setImageResource(R.drawable.ic_check_circle)
                holder.ivStatusIcon.setColorFilter(ContextCompat.getColor(holder.itemView.context, R.color.status_completed))
                holder.ivCompletionOverlay.visibility = android.view.View.VISIBLE
                
                // Apply completed card styling
                holder.cardTodo.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.success_light))
                holder.cardTodo.alpha = 0.95f
                
                // Strike through workout name
                holder.tvWorkoutName.paintFlags = holder.tvWorkoutName.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                holder.tvWorkoutName.alpha = 0.7f
                
            } else {
                // Pending styling
                holder.tvStatus.text = "⏳ Pending"
                holder.tvStatus.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.status_pending))
                holder.ivStatusIcon.setImageResource(R.drawable.ic_timer)
                holder.ivStatusIcon.setColorFilter(ContextCompat.getColor(holder.itemView.context, R.color.status_pending))
                holder.ivCompletionOverlay.visibility = android.view.View.GONE
                
                // Apply pending card styling
                holder.cardTodo.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.context, R.color.surface_light))
                holder.cardTodo.alpha = 1.0f
                
                // Remove strike through
                holder.tvWorkoutName.paintFlags = holder.tvWorkoutName.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
                holder.tvWorkoutName.alpha = 1.0f
            }
            
            holder.btnDelete.setOnClickListener {
                onDelete(todo)
            }
            
        } catch (e: Exception) {
            Log.e("WorkoutTodoAdapter", "Error binding view holder", e)
        }
    }
    
    override fun getItemCount() = todoList.size
}

/* Helper para gawing List kapag kailangan */
private fun String.toGroupList(): List<String> =
    split(",").map { it.trim() }.filter { it.isNotEmpty() } 