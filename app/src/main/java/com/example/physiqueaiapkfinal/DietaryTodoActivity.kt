package com.example.physiqueaiapkfinal

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import androidx.activity.OnBackPressedCallback
import com.example.physiqueaiapkfinal.MealTodo
import com.example.physiqueaiapkfinal.UserMedicalInfo
import kotlin.math.roundToInt

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

// import com.example.physiqueaiapkfinal.MealTodo
// import com.example.physiqueaiapkfinal.UserMedicalInfo

class DietaryTodoActivity : AppCompatActivity() {
    
    private lateinit var mealSpinner: Spinner
    private lateinit var mealTypeSpinner: Spinner
    private lateinit var btnSelectDate: Button
    private lateinit var btnSelectTime: Button
    private lateinit var btnAddMeal: Button
    private lateinit var btnAutoGenerateMeals: Button
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
    private var userGymMode: String = "no_preference"
    private var todoListener: ListenerRegistration? = null
    
    // Executor for background operations
    private val backgroundExecutor: ExecutorService = Executors.newFixedThreadPool(3)
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Loading states
    private var isDataLoaded = false
    private var loadedComponents = 0
    private val totalComponents = 4 // meals, medical info, gym mode, todo list
    
    private val mealTypes = listOf("Select Meal Type", "Breakfast", "Lunch", "Dinner", "Snack")
    
    // Sample Meal Data is now fetched from Firestore.
    private val sampleMeals = emptyList<MealItem>()
    
    /* ╔═══════ HELPER FUNCTIONS ═══════╗ */
    private fun Any?.toIntSafe(): Int = when (this) {
        is Number -> this.toDouble().roundToInt()
        is String -> this.toDoubleOrNull()?.roundToInt() ?: 0
        else      -> 0
    }
    private fun Any?.toStringList(): List<String> = when (this) {
        is List<*> -> this.filterIsInstance<String>()
        is String  -> this.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        else       -> listOf()
    }
    /* ╚═══════════════════════════════╝ */
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dietary_todo)
        
        // Setup modern back press handling
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
        
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
            btnAutoGenerateMeals = findViewById(R.id.btnAutoGenerateMeals)
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
            btnAutoGenerateMeals.isEnabled = true
            mealSpinner.isEnabled = true
            mealTypeSpinner.isEnabled = true
            
            // Set default date to today
            val calendar = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            selectedDate = dateFormat.format(calendar.time)
            selectedTime = timeFormat.format(calendar.time)
            btnSelectDate.text = selectedDate
            btnSelectTime.text = selectedTime
            
            // Setup meal type spinner with placeholder
            val mealTypeAdapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, mealTypes) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val v = super.getView(position, convertView, parent)
                    (v as? TextView)?.setTextColor(ContextCompat.getColor(context, R.color.black))
                    return v
                }

                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val v = super.getDropDownView(position, convertView, parent)
                    (v as? TextView)?.setTextColor(ContextCompat.getColor(context, R.color.black))
                    return v
                }
            }
            mealTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            mealTypeSpinner.adapter = mealTypeAdapter
            
            // Setup meal spinner with placeholder initially
            val initialMealAdapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, listOf("Select a Meal")) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val v = super.getView(position, convertView, parent)
                    (v as? TextView)?.setTextColor(ContextCompat.getColor(context, R.color.black))
                    return v
                }

                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val v = super.getDropDownView(position, convertView, parent)
                    (v as? TextView)?.setTextColor(ContextCompat.getColor(context, R.color.black))
                    return v
                }
            }
            initialMealAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            mealSpinner.adapter = initialMealAdapter
            
        } catch (e: Exception) {
            handleError("Error initializing views", e)
        }
    }
    
    private fun setupRecyclerView() {
        adapter = MealTodoAdapter(
            todoList,
            onToggleCompletion = { todo -> toggleTodoCompletion(todo) },
            onDelete = { todo -> deleteTodo(todo) }
        )
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
            
            // Auto-generate meals button
            btnAutoGenerateMeals.setOnClickListener {
                try {
                    autoGenerateMealsNow()
                } catch (e: Exception) {
                    handleError("Error auto-generating meals", e)
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
            loadUserGymModeAsync {
                loadMealDataAsync {
                    loadTodoListAsync {
                        onAllDataLoaded()
                    }
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
    
    private fun loadUserGymModeAsync(onComplete: () -> Unit) {
        backgroundExecutor.execute {
            try {
                firestore.collection("userinfo")
                    .document(userId)
                    .get()
                    .addOnSuccessListener { doc ->
                        try {
                            val physicalInfo = doc.get("physicalInfo") as? Map<*, *>
                            userGymMode = ((physicalInfo?.get("gymMode") as? String)?.lowercase() ?: "no_preference").replace('_', '-')
                        } catch (e: Exception) {
                            android.util.Log.e("DietaryTodoActivity", "Gym mode parse error", e)
                        } finally {
                            incrementLoadedComponents()
                            onComplete()
                        }
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("DietaryTodoActivity", "Gym mode load fail", e)
                        incrementLoadedComponents()
                        onComplete()
                    }
            } catch (e: Exception) {
                android.util.Log.e("DietaryTodoActivity", "Gym mode load exception", e)
                incrementLoadedComponents()
                onComplete()
            }
        }
    }
    
    private fun loadMealDataAsync(onComplete: () -> Unit) {
        backgroundExecutor.execute {
            firestore.collection("dietarylist")
                .get()
                .addOnSuccessListener { documents ->
                    mainHandler.post {
                        mealList.clear()
                        if (documents != null && !documents.isEmpty) {
                            Log.d("DietaryTodoActivity", "Fetched ${documents.size()} documents from Firestore.")
                            val fetchedMeals = documents.map { doc ->
                                MealItem(
                                    id = doc.id,
                                    mealName = doc.getString("mealName") ?: "",
                                    mealType = doc.getString("mealType") ?: "",
                                    prepTime = doc.get("prepTime").toIntSafe(),
                                    imageUrl = doc.getString("imageUrl") ?: "",
                                    allergies = doc.get("allergies").toStringList(),
                                    calories = doc.get("calories").toIntSafe(),
                                    ingredients = doc.get("ingredients").toStringList(),
                                    dietaryRestrictions = doc.get("dietaryRestrictions").toStringList(),
                                    nutritionFacts = doc.get("nutritionFacts") as? Map<String, String> ?: emptyMap()
                                )
                            }
                            mealList.addAll(fetchedMeals)
                            Log.d("DietaryTodoActivity", "Parsed ${mealList.size} meals into mealList.")
                        } else {
                            Log.d("DietaryTodoActivity", "Firestore collection 'dietarylist' is empty or null.")
                        }
                        setupMealSpinner()
                        incrementLoadedComponents()
                        onComplete()
                    }
                }
                .addOnFailureListener { e ->
                    handleError("Failed to load meals", e)
                    mainHandler.post {
                        setupMealSpinner()
                        incrementLoadedComponents()
                        onComplete()
                    }
                }
        }
    }
    
    private fun setupMealSpinner() {
        try {
            val mealNames: MutableList<String>
            if (mealList.isEmpty()) {
                mealNames = mutableListOf("No Meals Available")
                mainHandler.post { mealSpinner.isEnabled = false }
            } else {
                mealNames = mutableListOf("Select a Meal")
                mealNames.addAll(mealList.map { "${it.mealName} (${it.calories} cal, ${it.prepTime}min)" })
                mainHandler.post { mealSpinner.isEnabled = true }
            }

            val spinnerAdapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, mealNames) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val v = super.getView(position, convertView, parent)
                    (v as? TextView)?.setTextColor(ContextCompat.getColor(context, R.color.black))
                    return v
                }

                override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val v = super.getDropDownView(position, convertView, parent)
                    (v as? TextView)?.setTextColor(ContextCompat.getColor(context, R.color.black))
                    return v
                }
            }
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            mainHandler.post {
                mealSpinner.adapter = spinnerAdapter
            }

            // Filtration logic
            etMealSearch.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) {
                    val filtered = mealList.filter { it.mealName.contains(s.toString(), ignoreCase = true) }
                    val filteredNames = mutableListOf("Select a Meal")
                    if (filtered.isNotEmpty()) {
                        filteredNames.addAll(filtered.map { "${it.mealName} (${it.calories} cal, ${it.prepTime}min)" })
                    }

                    val filterAdapter = object : ArrayAdapter<String>(this@DietaryTodoActivity, android.R.layout.simple_spinner_item, filteredNames) {
                        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                            val v = super.getView(position, convertView, parent)
                            (v as? TextView)?.setTextColor(ContextCompat.getColor(context, R.color.black))
                            return v
                        }

                        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                            val v = super.getDropDownView(position, convertView, parent)
                            (v as? TextView)?.setTextColor(ContextCompat.getColor(context, R.color.black))
                            return v
                        }
                    }
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
            2 -> "Loading gym mode..."
            3 -> "Loading your meal plans..."
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
                btnAutoGenerateMeals.isEnabled = true
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
            
            // Temporarily disable button to prevent rapid clicking
            btnAddMeal.isEnabled = false
            
            // Re-enable button after 2 seconds to allow retry
            mainHandler.postDelayed({
                btnAddMeal.isEnabled = true
            }, 2000)
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
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
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
            // Ensure button is enabled (recovery from any previous error state)
            btnAddMeal.isEnabled = true
            
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

            if (mealSpinner.selectedItem.toString() == "No results found") {
                showError("Invalid meal selection.")
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
                            proceedAfterDietCheck(selectedMeal, selectedMealType)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                    return
                }
            }
            
            // Diet preference check (keto / vegetarian / high protein)
            proceedAfterDietCheck(selectedMeal, selectedMealType)
            
        } catch (e: Exception) {
            handleError("Error adding meal", e)
        }
    }
    
    private fun proceedAfterDietCheck(meal: MealItem, mealType: String) {
        try {
            if (userGymMode != "no_preference" && !matchesGymMode(meal)) {
                AlertDialog.Builder(this)
                    .setTitle("⚠️ Diet Preference Warning")
                    .setMessage("This meal may not align with your $userGymMode diet preference. Are you sure you want to add it?")
                    .setPositiveButton("Add Anyway") { _, _ ->
                        createMealTodoAsync(meal, mealType)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                createMealTodoAsync(meal, mealType)
            }
        } catch (e: Exception) {
            handleError("Diet check error", e)
            createMealTodoAsync(meal, mealType)
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
                        // Magdagdag ng activity log sa recentActivities
                        val activity = mapOf(
                            "title" to meal.mealName,
                            "subtitle" to "$mealType - ${meal.calories} cal scheduled",
                            "timestamp" to System.currentTimeMillis(),
                            "type" to "meal",
                            "caloriesConsumed" to meal.calories,
                            "date" to selectedDate,
                            "userId" to userId
                        )
                        firestore.collection("userActivities")
                            .document(userId)
                            .collection("recentActivities")
                            .add(activity)
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
            
            // Re-enable all UI elements
            btnAddMeal.isEnabled = true
            btnAutoGenerateMeals.isEnabled = true
            btnSelectDate.isEnabled = true
            btnSelectTime.isEnabled = true
            mealSpinner.isEnabled = true
            mealTypeSpinner.isEnabled = true
            
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
            
            // Update userStats collection for dashboard integration
            firestore.collection("userStats")
                .document(userId)
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
                        .document(userId)
                        .collection("dailyStats")
                        .document(today)
                        .set(statsData, com.google.firebase.firestore.SetOptions.merge())
                        .addOnSuccessListener {
                            Log.d("DietaryTodo", "Dashboard stats updated: Calories=$updatedCalories, Meals=$updatedMeals")
                            
                            // Also update the main userStats document for quick access
                            firestore.collection("userStats")
                                .document(userId)
                                .get()
                                .addOnSuccessListener { mainDoc ->
                                    val totalCaloriesConsumed = mainDoc.getLong("totalCaloriesConsumed")?.toInt() ?: 0
                                    val totalMealsCompleted = mainDoc.getLong("totalMealsCompleted")?.toInt() ?: 0
                                    
                                    firestore.collection("userStats")
                                        .document(userId)
                                        .set(mapOf(
                                            "totalCaloriesConsumed" to (totalCaloriesConsumed + calories),
                                            "totalMealsCompleted" to (totalMealsCompleted + 1),
                                            "todayCaloriesConsumed" to updatedCalories,
                                            "todayMealsCompleted" to updatedMeals,
                                            "lastUpdated" to System.currentTimeMillis()
                                        ), com.google.firebase.firestore.SetOptions.merge())
                                        .addOnSuccessListener {
                                            Log.d("DietaryTodo", "Main userStats updated with today's progress")
                                        }
                                        .addOnFailureListener { e ->
                                            Log.e("DietaryTodo", "Error updating main userStats", e)
                                        }
                                }
                                .addOnFailureListener { e ->
                                    Log.e("DietaryTodo", "Error getting main userStats", e)
                                }
                        }
                        .addOnFailureListener { e ->
                            Log.e("DietaryTodo", "Error getting current calories", e)
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
                "caloriesConsumed" to todo.calories,
                "date" to todo.scheduledDate,
                "userId" to userId
            )
            
            // Add to recent activities for dashboard
            firestore.collection("userActivities")
                .document(userId)
                .collection("recentActivities")
                .add(activity)
                .addOnSuccessListener {
                    Log.d("DietaryTodo", "Activity added to recent activities")
                    
                    // Also update the main activities document for quick access
                    firestore.collection("userActivities")
                        .document(userId)
                        .set(mapOf(
                            "lastActivity" to activity,
                            "lastUpdated" to System.currentTimeMillis()
                        ), com.google.firebase.firestore.SetOptions.merge())
                }
                .addOnFailureListener { e ->
                    Log.e("DietaryTodo", "Error adding to recent activities", e)
                }
        } catch (e: Exception) {
            Log.e("DietaryTodo", "Error adding to recent activities", e)
        }
    }
    
    private fun deleteTodo(todo: MealTodo) {
        backgroundExecutor.execute {
            try {
                firestore.collection("userTodoList")
                    .document(userId)
                    .collection("mealPlan")
                    .document(todo.id)
                    .delete()
                    .addOnSuccessListener {
                        mainHandler.post {
                            Toast.makeText(this@DietaryTodoActivity, "✅ ${todo.mealName} removed from your plan!", Toast.LENGTH_SHORT).show()
                            loadTodoListAsync {}
                        }
                    }
                    .addOnFailureListener { e ->
                        handleError("Failed to delete meal", e)
                    }
            } catch (e: Exception) {
                handleError("Error deleting meal", e)
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

    /** Auto-generate 3–4 random meals for the currently selected date */
    private fun autoGenerateMealsNow() {
        try {
            if (!isDataLoaded || mealList.isEmpty()) {
                showError("Please wait for meals to load")
                return
            }

            btnAutoGenerateMeals.isEnabled = false

            val mealCount = (3..4).random()

            val eligibleMeals = mealList.filter { matchesGymMode(it) }
            val source = if (eligibleMeals.size >= mealCount) eligibleMeals else mealList
            val randomMeals = source.shuffled().take(mealCount)

            val timeDefaults = mapOf(
                "Breakfast" to "08:00",
                "Lunch" to "12:00",
                "Dinner" to "18:00",
                "Snack" to "15:00"
            )

            val batch = firestore.batch()
            val mealPlanRef = firestore.collection("userTodoList").document(userId)
                .collection("mealPlan")

            for (meal in randomMeals) {
                val todo = MealTodo(
                    mealId = meal.id,
                    mealName = meal.mealName,
                    mealType = meal.mealType,
                    scheduledDate = selectedDate,
                    scheduledTime = timeDefaults[meal.mealType] ?: selectedTime,
                    userId = userId,
                    calories = meal.calories,
                    allergies = meal.allergies,
                    prepTime = meal.prepTime,
                    imageUrl = meal.imageUrl
                )

                batch.set(mealPlanRef.document(), todo)
            }

            batch.commit()
                .addOnSuccessListener {
                    mainHandler.post {
                        Toast.makeText(this, "✅ Generated ${randomMeals.size} meals!", Toast.LENGTH_SHORT).show()
                        btnAutoGenerateMeals.isEnabled = true
                    }
                }
                .addOnFailureListener { e ->
                    handleError("Failed to generate meals", e)
                    mainHandler.post { btnAutoGenerateMeals.isEnabled = true }
                }

        } catch (e: Exception) {
            handleError("Auto-generate meals error", e)
            btnAutoGenerateMeals.isEnabled = true
        }
    }

    private fun matchesGymMode(meal: MealItem): Boolean {
        return when (userGymMode) {
            "vegetarian" -> meal.dietaryRestrictions.any { it.equals("Vegetarian", true) }
            "keto" -> meal.dietaryRestrictions.any { it.equals("Keto", true) || it.equals("Low-carb", true) }
            "high-protein" -> meal.dietaryRestrictions.any { it.contains("High", true) && it.contains("protein", true) }
            else -> true // no preference
        }
    }
}

// Adapter for MealTodo RecyclerView
class MealTodoAdapter(
    private val todoList: List<MealTodo>,
    private val onToggleCompletion: (MealTodo) -> Unit,
    private val onDelete: (MealTodo) -> Unit
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
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
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
        
        // Load meal image
        loadMealImage(holder.ivMealImage, todo)
        
        holder.checkBox.setOnCheckedChangeListener { _, _ ->
            onToggleCompletion(todo)
        }
        holder.btnDelete.setOnClickListener {
            onDelete(todo)
        }
    }
    
    override fun getItemCount() = todoList.size
    
    private fun loadMealImage(imageView: ImageView, todo: MealTodo) {
        // First try to load from URL if available
        if (todo.imageUrl.isNotEmpty() && todo.imageUrl != "placeholder") {
            Glide.with(imageView.context)
                .load(todo.imageUrl)
                .placeholder(getFallbackImage(todo.mealName))
                .error(getFallbackImage(todo.mealName))
                .into(imageView)
        } else {
            // Use fallback image based on meal name
            imageView.setImageResource(getFallbackImage(todo.mealName))
        }
    }
    
    private fun getFallbackImage(mealName: String): Int {
        val name = mealName.lowercase()
        return when {
            // Curry and lentil dishes - use curry icon
            name.contains("curry") -> R.drawable.curry_food
            name.contains("lentil") -> R.drawable.curry_food
            
            // Tofu and vegetarian dishes - use tofu icon
            name.contains("tofu") -> R.drawable.tofu_food
            name.contains("veggie") -> R.drawable.tofu_food
            name.contains("vegetarian") -> R.drawable.tofu_food
            name.contains("bowl") -> R.drawable.tofu_food
            
            // Smoothies and drinks - use smoothie thumbnail
            name.contains("smoothie") -> R.drawable.smoothie_thumb
            name.contains("protein") && (name.contains("shake") || name.contains("peanut")) -> R.drawable.smoothie_thumb
            name.contains("peanut butter") -> R.drawable.smoothie_thumb
            
            // Breakfast items - use breakfast thumbnail
            name.contains("oatmeal") -> R.drawable.breakfast_thumb
            name.contains("cereal") -> R.drawable.breakfast_thumb
            name.contains("pancake") -> R.drawable.breakfast_thumb
            name.contains("toast") -> R.drawable.breakfast_thumb
            name.contains("breakfast") -> R.drawable.breakfast_thumb
            
            // Salads and healthy dishes - use meal prep thumbnail
            name.contains("salad") -> R.drawable.meal_prep_thumb
            
            // Default meal categories - use general food image
            else -> R.drawable.diet1
        }
    }
} 