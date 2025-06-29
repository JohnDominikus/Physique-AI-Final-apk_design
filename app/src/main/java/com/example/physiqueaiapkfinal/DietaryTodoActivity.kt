package com.example.physiqueaiapkfinal

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// Data Models for Dietary
data class MealItem(
    val id: String = "",
    val mealName: String = "",
    val mealType: String = "", // Breakfast, Lunch, Dinner, Snack
    val prepTime: Int = 0, // in minutes
    val imageUrl: String = "",
    val allergies: List<String> = listOf(),
    val calories: Int = 0,
    val ingredients: List<String> = listOf(),
    val nutritionFacts: Map<String, String> = mapOf(),
    val dietaryRestrictions: List<String> = listOf() // Vegan, Vegetarian, Gluten-free, etc.
)

data class MealTodo(
    val id: String = UUID.randomUUID().toString(),
    val mealId: String = "",
    val mealName: String = "",
    val mealType: String = "",
    val scheduledDate: String = "",
    val scheduledTime: String = "", // e.g., "08:00"
    val isCompleted: Boolean = false,
    val userId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val calories: Int = 0,
    val allergies: List<String> = listOf(),
    val prepTime: Int = 0,
    val imageUrl: String = ""
)

data class UserMedicalInfo(
    val userId: String = "",
    val injuries: List<String> = listOf(),
    val allergies: List<String> = listOf(),
    val medicalConditions: List<String> = listOf(),
    val fitnessLevel: String = ""
)

class DietaryTodoActivity : AppCompatActivity() {
    
    private lateinit var mealSpinner: Spinner
    private lateinit var mealTypeSpinner: Spinner
    private lateinit var btnSelectDate: Button
    private lateinit var btnSelectTime: Button
    private lateinit var btnAddMeal: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvAllergyWarning: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var cardLoading: androidx.cardview.widget.CardView
    private lateinit var tvLoadingText: TextView
    private lateinit var etMealSearch: EditText
    
    private val firestore = FirebaseFirestore.getInstance()
    private val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private val mealList = mutableListOf<MealItem>()
    private val todoList = mutableListOf<MealTodo>()
    private lateinit var adapter: MealTodoAdapter
    private var selectedDate = ""
    private var selectedTime = ""
    private var userMedicalInfo: UserMedicalInfo? = null
    private var todoListener: ListenerRegistration? = null
    
    // Executor for background operations
    private val backgroundExecutor: ExecutorService = Executors.newFixedThreadPool(3)
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Loading states
    private var isDataLoaded = false
    private var loadedComponents = 0
    private val totalComponents = 3 // meals, medical info, todo list
    
    private val mealTypes = listOf("Select Meal Type", "Breakfast", "Lunch", "Dinner", "Snack")
    
    // Sample Meal Data
    private val sampleMeals = listOf(
        MealItem(
            id = "1",
            mealName = "Oatmeal with Berries",
            mealType = "Breakfast",
            prepTime = 10,
            calories = 350,
            allergies = listOf("Gluten"),
            ingredients = listOf("Oats", "Blueberries", "Strawberries", "Honey", "Milk"),
            dietaryRestrictions = listOf("Vegetarian"),
            nutritionFacts = mapOf("Protein" to "8g", "Carbs" to "60g", "Fat" to "6g")
        ),
        MealItem(
            id = "2", 
            mealName = "Grilled Chicken Salad",
            mealType = "Lunch",
            prepTime = 20,
            calories = 450,
            allergies = listOf(),
            ingredients = listOf("Chicken Breast", "Mixed Greens", "Tomatoes", "Cucumber", "Olive Oil"),
            dietaryRestrictions = listOf("High Protein", "Low Carb"),
            nutritionFacts = mapOf("Protein" to "35g", "Carbs" to "12g", "Fat" to "18g")
        ),
        MealItem(
            id = "3",
            mealName = "Salmon with Rice",
            mealType = "Dinner", 
            prepTime = 25,
            calories = 520,
            allergies = listOf("Fish"),
            ingredients = listOf("Salmon Fillet", "Brown Rice", "Broccoli", "Lemon", "Garlic"),
            dietaryRestrictions = listOf("High Protein", "Omega-3 Rich"),
            nutritionFacts = mapOf("Protein" to "40g", "Carbs" to "35g", "Fat" to "20g")
        ),
        MealItem(
            id = "4",
            mealName = "Greek Yogurt Parfait",
            mealType = "Snack",
            prepTime = 5,
            calories = 220,
            allergies = listOf("Dairy"),
            ingredients = listOf("Greek Yogurt", "Granola", "Honey", "Banana"),
            dietaryRestrictions = listOf("Vegetarian", "High Protein"),
            nutritionFacts = mapOf("Protein" to "15g", "Carbs" to "25g", "Fat" to "8g")
        ),
        MealItem(
            id = "5",
            mealName = "Avocado Toast",
            mealType = "Breakfast",
            prepTime = 8,
            calories = 310,
            allergies = listOf("Gluten"),
            ingredients = listOf("Whole Grain Bread", "Avocado", "Tomato", "Lime", "Salt"),
            dietaryRestrictions = listOf("Vegan", "High Fiber"),
            nutritionFacts = mapOf("Protein" to "8g", "Carbs" to "30g", "Fat" to "18g")
        ),
        MealItem(
            id = "6",
            mealName = "Vegetable Stir Fry",
            mealType = "Lunch",
            prepTime = 15,
            calories = 280,
            allergies = listOf("Soy"),
            ingredients = listOf("Mixed Vegetables", "Tofu", "Soy Sauce", "Ginger", "Garlic"),
            dietaryRestrictions = listOf("Vegan", "Low Calorie"),
            nutritionFacts = mapOf("Protein" to "12g", "Carbs" to "25g", "Fat" to "12g")
        ),
        MealItem(
            id = "7",
            mealName = "Protein Smoothie",
            mealType = "Snack",
            prepTime = 5,
            calories = 180,
            allergies = listOf("Dairy"),
            ingredients = listOf("Protein Powder", "Banana", "Milk", "Peanut Butter"),
            dietaryRestrictions = listOf("High Protein"),
            nutritionFacts = mapOf("Protein" to "25g", "Carbs" to "15g", "Fat" to "6g")
        ),
        MealItem(
            id = "8",
            mealName = "Quinoa Bowl",
            mealType = "Dinner",
            prepTime = 30,
            calories = 420,
            allergies = listOf(),
            ingredients = listOf("Quinoa", "Black Beans", "Sweet Potato", "Spinach", "Lime"),
            dietaryRestrictions = listOf("Vegan", "Gluten-Free", "High Fiber"),
            nutritionFacts = mapOf("Protein" to "16g", "Carbs" to "65g", "Fat" to "8g")
        )
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dietary_todo)
        
        try {
            // Setup action bar with back button
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = "Meal Planner"
            
            initViews()
            setupRecyclerView()
            setupClickListeners()
            
            // Test if UI elements are properly initialized
            testUIElements()
            
            // Load data asynchronously with proper sequencing
            loadDataAsync()
        } catch (e: Exception) {
            handleError("Initialization error", e)
        }
    }
    
    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun initViews() {
        try {
            mealSpinner = findViewById(R.id.spinnerMeal)
            mealTypeSpinner = findViewById(R.id.spinnerMealType)
            btnSelectDate = findViewById(R.id.btnSelectDate)
            btnSelectTime = findViewById(R.id.btnSelectTime)
            btnAddMeal = findViewById(R.id.btnAddMeal)
            recyclerView = findViewById(R.id.recyclerMealTodos)
            tvAllergyWarning = findViewById(R.id.tvAllergyWarning)
            progressBar = findViewById(R.id.progressBar)
            cardLoading = findViewById(R.id.cardLoading)
            tvLoadingText = findViewById(R.id.tvLoadingText)
            etMealSearch = findViewById(R.id.etMealSearch)
            
            // Enable interactions immediately for better UX
            btnSelectDate.isEnabled = true
            btnSelectTime.isEnabled = true
            btnAddMeal.isEnabled = true
            mealSpinner.isEnabled = true
            mealTypeSpinner.isEnabled = true
            
            // Set default date to today
            val calendar = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            selectedDate = dateFormat.format(calendar.time)
            selectedTime = timeFormat.format(calendar.time)
            btnSelectDate.text = selectedDate
            btnSelectTime.text = selectedTime
            
            // Setup meal type spinner with placeholder
            val mealTypeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, mealTypes)
            mealTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            mealTypeSpinner.adapter = mealTypeAdapter
            
            // Setup meal spinner with placeholder initially
            val initialMealAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, listOf("Select a Meal"))
            initialMealAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            mealSpinner.adapter = initialMealAdapter
            
        } catch (e: Exception) {
            handleError("Error initializing views", e)
        }
    }
    
    private fun setupRecyclerView() {
        adapter = MealTodoAdapter(todoList) { todo ->
            toggleTodoCompletion(todo)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }
    
    private fun setupClickListeners() {
        try {
            // Date and Time buttons
            btnSelectDate.setOnClickListener { 
                try {
                    showDatePicker() 
                } catch (e: Exception) {
                    handleError("Error in date picker", e)
                }
            }
            
            btnSelectTime.setOnClickListener { 
                try {
                    showTimePicker() 
                } catch (e: Exception) {
                    handleError("Error in time picker", e)
                }
            }
            
            // Add meal button
            btnAddMeal.setOnClickListener { 
                try {
                    addMealTodo() 
                } catch (e: Exception) {
                    handleError("Error adding meal", e)
                }
            }
            
            // Meal spinner listener with fallback
            mealSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    try {
                        android.util.Log.d("DietaryTodoActivity", "Meal selected at position: $position")
                        if (position > 0 && mealList.isNotEmpty()) { // Skip placeholder at position 0
                            val actualIndex = position - 1
                            if (actualIndex < mealList.size) {
                                checkAllergyWarnings(mealList[actualIndex])
                            }
                        } else {
                            // Hide warning when placeholder is selected
                            tvAllergyWarning.visibility = android.view.View.GONE
                        }
                    } catch (e: Exception) {
                        handleError("Error in meal selection", e)
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {
                    try {
                        tvAllergyWarning.visibility = android.view.View.GONE
                    } catch (e: Exception) {
                        handleError("Error in meal selection", e)
                    }
                }
            }
            
            // Add touch listener as fallback for spinner issues
            mealSpinner.setOnTouchListener { _, event ->
                try {
                    android.util.Log.d("DietaryTodoActivity", "Meal spinner touched")
                    // If spinner doesn't respond, show meal selection dialog
                    if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                        showMealSelectionDialog()
                        return@setOnTouchListener true
                    }
                } catch (e: Exception) {
                    handleError("Error in meal spinner touch", e)
                }
                false
            }
            
            // Meal type spinner listener
            mealTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    try {
                        android.util.Log.d("DietaryTodoActivity", "Meal type selected: ${mealTypes[position]}")
                    } catch (e: Exception) {
                        handleError("Error in meal type selection", e)
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
            
        } catch (e: Exception) {
            handleError("Error setting up click listeners", e)
        }
    }
    
    private fun showMealSelectionDialog() {
        try {
            if (mealList.isEmpty()) {
                showError("No meals available")
                return
            }
            
            val mealNames = mealList.map { "${it.mealName} (${it.calories} cal, ${it.prepTime}min)" }.toTypedArray()
            
            AlertDialog.Builder(this)
                .setTitle("Select a Meal")
                .setItems(mealNames) { _, which ->
                    try {
                        // Simulate spinner selection
                        val selectedMeal = mealList[which]
                        checkAllergyWarnings(selectedMeal)
                        
                        // Update UI to show selection
                        Toast.makeText(this, "Selected: ${selectedMeal.mealName}", Toast.LENGTH_SHORT).show()
                        
                        // Store selection for later use
                        // You can add a variable to track the selected meal
                        
                    } catch (e: Exception) {
                        handleError("Error selecting meal from dialog", e)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
                
        } catch (e: Exception) {
            handleError("Error showing meal selection dialog", e)
        }
    }
    
    private fun loadDataAsync() {
        showLoading(true)
        
        // Load data in sequence to prevent overwhelming Firebase
        loadUserMedicalInfoAsync {
            loadMealDataAsync {
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
    
    private fun loadMealDataAsync(onComplete: () -> Unit) {
        backgroundExecutor.execute {
            try {
                // Use sample data instead of Firebase for now
                mainHandler.post {
                    mealList.clear()
                    mealList.addAll(sampleMeals)
                    setupMealSpinner()
                    incrementLoadedComponents()
                    onComplete()
                }
            } catch (e: Exception) {
                handleError("Meal data loading error", e)
                onComplete()
            }
        }
    }
    
    private fun setupMealSpinner() {
        try {
            if (mealList.isEmpty()) {
                showError("No meals available")
                // Add fallback sample data
                mealList.addAll(sampleMeals)
            }
            val mealNames = mutableListOf("Select a Meal")
            mealNames.addAll(mealList.map { "${it.mealName} (${it.calories} cal, ${it.prepTime}min)" })
            val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, mealNames)
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            mainHandler.post {
                mealSpinner.adapter = spinnerAdapter
                mealSpinner.isEnabled = true
            }
            // Filtration logic
            etMealSearch.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) {
                    val filtered = mealList.filter { it.mealName.contains(s.toString(), ignoreCase = true) }
                    val filteredNames = mutableListOf("Select a Meal")
                    filteredNames.addAll(filtered.map { "${it.mealName} (${it.calories} cal, ${it.prepTime}min)" })
                    val filterAdapter = ArrayAdapter(this@DietaryTodoActivity, android.R.layout.simple_spinner_item, filteredNames)
                    filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    mainHandler.post {
                        mealSpinner.adapter = filterAdapter
                    }
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
        } catch (e: Exception) {
            handleError("Error setting up meal spinner", e)
        }
    }
    
    private fun loadTodoListAsync(onComplete: () -> Unit) {
        try {
            // Remove existing listener
            todoListener?.remove()
            
            todoListener = firestore.collection("userTodoList")
                .document(userId)
                .collection("mealPlan")
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
                                    val todo = doc.toObject(MealTodo::class.java).copy(id = doc.id)
                                    todoList.add(todo)
                                }
                            }
                            adapter.notifyDataSetChanged()
                            
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
            0 -> "Loading meal data..."
            1 -> "Loading medical information..."
            2 -> "Loading your meal plans..."
            else -> "Almost ready..."
        }
        
        mainHandler.post {
            progressBar.progress = progress
            tvLoadingText.text = loadingMessage
        }
    }
    
    private fun onAllDataLoaded() {
        try {
            isDataLoaded = true
            mainHandler.post {
                showLoading(false)
                
                // Ensure all UI elements are enabled
                btnAddMeal.isEnabled = true
                btnSelectDate.isEnabled = true
                btnSelectTime.isEnabled = true
                mealSpinner.isEnabled = true
                mealTypeSpinner.isEnabled = true
                
                // Force refresh spinners
                mealSpinner.invalidate()
                mealTypeSpinner.invalidate()
                
                Toast.makeText(this, "Ready to plan your meals!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            handleError("Error in data loaded callback", e)
        }
    }
    
    private fun showLoading(show: Boolean) {
        cardLoading.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
    }
    
    private fun handleError(message: String, exception: Exception) {
        android.util.Log.e("DietaryTodoActivity", "$message: ${exception.message}", exception)
        mainHandler.post {
            showLoading(false)
            showError("$message. Please try again.")
            btnAddMeal.isEnabled = false
        }
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    private fun checkAllergyWarnings(meal: MealItem) {
        try {
            var warningMessage = ""
            
            userMedicalInfo?.let { medicalInfo ->
                // Check for allergies
                val userAllergies = medicalInfo.allergies
                val mealAllergies = meal.allergies
                
                val conflictingAllergies = userAllergies.intersect(mealAllergies.toSet())
                
                if (conflictingAllergies.isNotEmpty()) {
                    warningMessage += "🚨 ALLERGY ALERT: This meal contains: ${conflictingAllergies.joinToString(", ")}. " +
                            "You have reported allergies to these ingredients. Please avoid this meal.\n"
                }
                
                // Check for potential allergens
                val potentialAllergies = mealAllergies.filter { allergen ->
                    userAllergies.any { userAllergy ->
                        allergen.contains(userAllergy, ignoreCase = true) || 
                        userAllergy.contains(allergen, ignoreCase = true)
                    }
                }
                
                if (potentialAllergies.isNotEmpty()) {
                    warningMessage += "⚠️ WARNING: This meal may contain traces of: ${potentialAllergies.joinToString(", ")}. " +
                            "Please check ingredients carefully.\n"
                }
                
                // Dietary restriction suggestions
                meal.dietaryRestrictions.forEach { restriction ->
                    warningMessage += "🌱 This meal is $restriction-friendly.\n"
                }
            }
            
            // Always show meal allergies
            if (meal.allergies.isNotEmpty()) {
                warningMessage += "📋 This meal contains: ${meal.allergies.joinToString(", ")}\n"
            }
            
            mainHandler.post {
                if (warningMessage.isNotEmpty()) {
                    tvAllergyWarning.text = warningMessage
                    tvAllergyWarning.visibility = android.view.View.VISIBLE
                } else {
                    tvAllergyWarning.visibility = android.view.View.GONE
                }
            }
        } catch (e: Exception) {
            handleError("Error checking allergies", e)
        }
    }
    
    private fun showDatePicker() {
        try {
            val calendar = Calendar.getInstance()
            val datePickerDialog = DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    try {
                        calendar.set(year, month, dayOfMonth)
                        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                        selectedDate = dateFormat.format(calendar.time)
                        btnSelectDate.text = selectedDate
                        // Reload todo list for new date
                        if (isDataLoaded) {
                            loadTodoListAsync {}
                        }
                    } catch (e: Exception) {
                        handleError("Error setting date", e)
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
    
    private fun showTimePicker() {
        try {
            val calendar = Calendar.getInstance()
            val timePickerDialog = android.app.TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    try {
                        selectedTime = String.format("%02d:%02d", hourOfDay, minute)
                        btnSelectTime.text = selectedTime
                    } catch (e: Exception) {
                        handleError("Error setting time", e)
                    }
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            )
            timePickerDialog.show()
        } catch (e: Exception) {
            handleError("Error showing time picker", e)
        }
    }
    
    private fun addMealTodo() {
        try {
            // Check if data is loaded
            if (mealList.isEmpty()) {
                showError("Meal data is still loading. Please wait a moment.")
                return
            }
            
            val selectedMealPosition = mealSpinner.selectedItemPosition
            val selectedMealTypePosition = mealTypeSpinner.selectedItemPosition
            
            // Check if placeholders are selected
            if (selectedMealPosition <= 0) {
                showError("Please select a meal first")
                return
            }
            
            if (selectedMealTypePosition <= 0) {
                showError("Please select a meal type first")
                return
            }
            
            // Adjust for placeholder (position - 1)
            val actualMealIndex = selectedMealPosition - 1
            if (actualMealIndex < 0 || actualMealIndex >= mealList.size) {
                showError("Invalid meal selection. Please try again.")
                return
            }
            
            val selectedMeal = mealList[actualMealIndex]
            val selectedMealType = mealTypes[selectedMealTypePosition]
            
            // Validate date and time
            if (selectedDate.isEmpty() || selectedTime.isEmpty()) {
                showError("Please select date and time")
                return
            }
            
            // Check for allergy conflicts before adding
            userMedicalInfo?.let { medicalInfo ->
                val conflictingAllergies = medicalInfo.allergies.intersect(selectedMeal.allergies.toSet())
                if (conflictingAllergies.isNotEmpty()) {
                    AlertDialog.Builder(this)
                        .setTitle("⚠️ Allergy Warning")
                        .setMessage("This meal contains allergens you've reported: ${conflictingAllergies.joinToString(", ")}. Are you sure you want to add it?")
                        .setPositiveButton("Add Anyway") { _, _ -> 
                            createMealTodoAsync(selectedMeal, selectedMealType)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                    return
                }
            }
            
            createMealTodoAsync(selectedMeal, selectedMealType)
            
        } catch (e: Exception) {
            handleError("Error adding meal", e)
        }
    }
    
    private fun createMealTodoAsync(meal: MealItem, mealType: String) {
        backgroundExecutor.execute {
            try {
                val mealTodo = MealTodo(
                    mealId = meal.id,
                    mealName = meal.mealName,
                    mealType = mealType,
                    scheduledDate = selectedDate,
                    scheduledTime = selectedTime,
                    userId = userId,
                    calories = meal.calories,
                    allergies = meal.allergies,
                    prepTime = meal.prepTime,
                    imageUrl = meal.imageUrl
                )
                
                firestore.collection("userTodoList")
                    .document(userId)
                    .collection("mealPlan")
                    .add(mealTodo)
                    .addOnSuccessListener {
                        mainHandler.post {
                            Toast.makeText(this@DietaryTodoActivity, "✅ ${meal.mealName} added to your plan!", Toast.LENGTH_SHORT).show()
                            resetFormAfterAdd()
                            generateMealSuggestions(mealType)
                        }
                    }
                    .addOnFailureListener { e ->
                        handleError("Failed to add meal", e)
                    }
            } catch (e: Exception) {
                handleError("Error creating meal todo", e)
            }
        }
    }
    
    private fun resetFormAfterAdd() {
        try {
            // Reset spinners to placeholder positions
            mealSpinner.setSelection(0)
            mealTypeSpinner.setSelection(0)
            
            // Hide allergy warning
            tvAllergyWarning.visibility = android.view.View.GONE
            
            // Update date to next meal time suggestion
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.HOUR_OF_DAY, 3) // Suggest 3 hours later
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            selectedTime = timeFormat.format(calendar.time)
            btnSelectTime.text = selectedTime
        } catch (e: Exception) {
            handleError("Error resetting form", e)
        }
    }
    
    private fun generateMealSuggestions(currentMealType: String) {
        try {
            // Smart meal suggestions based on nutritional balance
            val nextMealType = when (currentMealType) {
                "Breakfast" -> "Lunch"
                "Lunch" -> "Dinner"
                "Dinner" -> "Snack"
                else -> "Breakfast"
            }
            
            // Show suggestion dialog
            AlertDialog.Builder(this)
                .setTitle("🍽️ Meal Suggestion")
                .setMessage("Great choice! For your next meal ($nextMealType), consider adding foods rich in protein and fiber to maintain nutritional balance.")
                .setPositiveButton("Got it!", null)
                .show()
        } catch (e: Exception) {
            handleError("Error generating suggestions", e)
        }
    }
    
    private fun toggleTodoCompletion(todo: MealTodo) {
        backgroundExecutor.execute {
            try {
                val updatedTodo = todo.copy(isCompleted = !todo.isCompleted)
                
                firestore.collection("userTodoList")
                    .document(userId)
                    .collection("mealPlan")
                    .document(todo.id)
                    .set(updatedTodo)
                    .addOnSuccessListener {
                        if (updatedTodo.isCompleted) {
                            updateCaloriesConsumedAsync(todo.calories)
                            addToRecentActivities(todo)
                        }
                        mainHandler.post {
                            Toast.makeText(
                                this@DietaryTodoActivity,
                                if (updatedTodo.isCompleted) "✅ ${todo.mealName} completed!" else "⏳ ${todo.mealName} marked as pending",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        handleError("Failed to update todo", e)
                    }
            } catch (e: Exception) {
                handleError("Error toggling todo completion", e)
            }
        }
    }
    
    private fun updateCaloriesConsumedAsync(calories: Int) {
        try {
            val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
            
            firestore.collection("userStats")
                .document(userId)
                .collection("dailyStats")
                .document(today)
                .get()
                .addOnSuccessListener { document ->
                    val currentCalories = document.getLong("caloriesConsumed")?.toInt() ?: 0
                    val updatedCalories = currentCalories + calories
                    
                    firestore.collection("userStats")
                        .document(userId)
                        .collection("dailyStats")
                        .document(today)
                        .set(mapOf(
                            "caloriesConsumed" to updatedCalories,
                            "lastUpdated" to System.currentTimeMillis(),
                            "date" to today
                        ), com.google.firebase.firestore.SetOptions.merge())
                        .addOnSuccessListener {
                            Log.d("DietaryTodo", "Calories consumed updated: $updatedCalories")
                        }
                        .addOnFailureListener { e ->
                            Log.e("DietaryTodo", "Error updating calories consumed", e)
                        }
                }
                .addOnFailureListener { e ->
                    Log.e("DietaryTodo", "Error getting current calories", e)
                }
        } catch (e: Exception) {
            Log.e("DietaryTodo", "Error updating calories consumed", e)
        }
    }
    
    private fun addToRecentActivities(todo: MealTodo) {
        try {
            val activity = mapOf(
                "title" to todo.mealName,
                "subtitle" to "${todo.mealType} - ${todo.calories} cal consumed",
                "timestamp" to System.currentTimeMillis(),
                "type" to "meal",
                "caloriesConsumed" to todo.calories
            )
            
            firestore.collection("userActivities")
                .document(userId)
                .collection("recentActivities")
                .add(activity)
                .addOnSuccessListener {
                    Log.d("DietaryTodo", "Activity added to recent activities")
                }
                .addOnFailureListener { e ->
                    Log.e("DietaryTodo", "Error adding to recent activities", e)
                }
        } catch (e: Exception) {
            Log.e("DietaryTodo", "Error adding to recent activities", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            // Clean up resources to prevent memory leaks
            todoListener?.remove()
            backgroundExecutor.shutdown()
        } catch (e: Exception) {
            android.util.Log.e("DietaryTodoActivity", "Error during cleanup", e)
        }
    }
    
    override fun onPause() {
        super.onPause()
        try {
            // Remove expensive listeners when not visible
            todoListener?.remove()
        } catch (e: Exception) {
            android.util.Log.e("DietaryTodoActivity", "Error pausing", e)
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
            android.util.Log.e("DietaryTodoActivity", "Error resuming", e)
        }
    }
    
    private fun testUIElements() {
        try {
            // Test if all UI elements are properly initialized
            val elements = listOf(
                "Meal Spinner" to mealSpinner,
                "Meal Type Spinner" to mealTypeSpinner,
                "Date Button" to btnSelectDate,
                "Time Button" to btnSelectTime,
                "Add Button" to btnAddMeal,
                "RecyclerView" to recyclerView
            )
            
            elements.forEach { (name, element) ->
                if (element == null) {
                    android.util.Log.e("DietaryTodoActivity", "$name is null!")
                } else {
                    android.util.Log.d("DietaryTodoActivity", "$name is properly initialized")
                }
            }
            
            // Test if spinners are clickable
            mealSpinner.setOnTouchListener { _, _ ->
                android.util.Log.d("DietaryTodoActivity", "Meal spinner touched")
                false
            }
            
            mealTypeSpinner.setOnTouchListener { _, _ ->
                android.util.Log.d("DietaryTodoActivity", "Meal type spinner touched")
                false
            }
            
        } catch (e: Exception) {
            handleError("Error testing UI elements", e)
        }
    }
}

// Adapter for MealTodo RecyclerView
class MealTodoAdapter(
    private val todoList: List<MealTodo>,
    private val onToggleCompletion: (MealTodo) -> Unit
) : RecyclerView.Adapter<MealTodoAdapter.ViewHolder>() {
    
    class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val checkBox: CheckBox = view.findViewById(R.id.cbCompleted)
        val tvMealName: TextView = view.findViewById(R.id.tvMealName)
        val tvMealType: TextView = view.findViewById(R.id.tvMealType)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
        val tvCalories: TextView = view.findViewById(R.id.tvCalories)
        val tvPrepTime: TextView = view.findViewById(R.id.tvPrepTime)
        val tvAllergies: TextView = view.findViewById(R.id.tvAllergies)
        val ivMealImage: ImageView = view.findViewById(R.id.ivMealImage)
    }
    
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_meal_todo, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val todo = todoList[position]
        
        holder.checkBox.isChecked = todo.isCompleted
        holder.tvMealName.text = todo.mealName
        holder.tvMealType.text = todo.mealType
        holder.tvTime.text = todo.scheduledTime
        holder.tvCalories.text = "${todo.calories} cal"
        holder.tvPrepTime.text = "Prep: ${todo.prepTime} min"
        
        if (todo.allergies.isNotEmpty()) {
            holder.tvAllergies.text = "Contains: ${todo.allergies.joinToString(", ")}"
            holder.tvAllergies.visibility = android.view.View.VISIBLE
        } else {
            holder.tvAllergies.visibility = android.view.View.GONE
        }
        
        // Load meal image if available
        if (todo.imageUrl.isNotEmpty()) {
            // Use Glide or Picasso to load image
            // Glide.with(holder.itemView.context).load(todo.imageUrl).into(holder.ivMealImage)
        }
        
        holder.checkBox.setOnCheckedChangeListener { _, _ ->
            onToggleCompletion(todo)
        }
    }
    
    override fun getItemCount() = todoList.size
} 