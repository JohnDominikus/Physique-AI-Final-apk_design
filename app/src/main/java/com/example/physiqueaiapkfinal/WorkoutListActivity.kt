package com.example.physiqueaiapkfinal

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class WorkoutListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var searchEditText: EditText
    private lateinit var categorySpinner: Spinner
    // Removed liked workouts button

    private val workoutList = mutableListOf<Workout>()
    private var filteredList = mutableListOf<Workout>()
    private lateinit var adapter: WorkoutAdapter
    private var firestoreListener: ListenerRegistration? = null
    private val db = FirebaseFirestore.getInstance() // Get Firestore instance
    private val auth = FirebaseAuth.getInstance() // Get FirebaseAuth instance
    private val likedWorkoutIds = mutableSetOf<String>() // To store liked workout IDs
    private var likedWorkoutsListener: ListenerRegistration? = null // Listener for liked workouts

    // Categories for the dropdown
    private val categories = arrayOf("All", "Chest", "Back", "Legs", "Arms")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workoutlist)

        recyclerView = findViewById(R.id.workoutRecycler)
        searchEditText = findViewById(R.id.searchWorkoutEditText)
        categorySpinner = findViewById(R.id.categorySpinner)
        // No liked workouts button

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = WorkoutAdapter(filteredList) { workout ->
            // Handle item click: Navigate to WorkoutDetailActivity
            val intent = Intent(this, WorkoutDetailActivity::class.java)
            intent.putExtra("workoutId", workout.id)
            startActivity(intent)
        }
        recyclerView.adapter = adapter

        setupCategorySpinner()
        setupSearchAndFilter()
        listenForWorkoutsRealtime()
        // Liked workouts feature removed
    }

    private fun setupCategorySpinner() {
        // Create adapter for spinner
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, categories)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner.adapter = spinnerAdapter

        // Set listener for spinner selection
        categorySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedCategory = categories[position]
                filterWorkouts(category = selectedCategory)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }
    }

    private fun setupSearchAndFilter() {
        findViewById<ImageButton>(R.id.searchWorkoutButton).setOnClickListener {
            val selectedCategory = categories[categorySpinner.selectedItemPosition]
            filterWorkouts(category = selectedCategory)
        }
        searchEditText.setOnEditorActionListener { _, _, _ ->
            val selectedCategory = categories[categorySpinner.selectedItemPosition]
            filterWorkouts(category = selectedCategory)
            true
        }
    }

    private fun listenForWorkoutsRealtime() {
        firestoreListener = db.collection("workoutcollection") // Changed from "dietarylist" to "workoutlist"
            .addSnapshotListener { result, error ->
                if (error != null) {
                    Toast.makeText(this, "Error loading workouts", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                workoutList.clear()
                var hadError = false
                if (result != null) {
                    for (doc in result) {
                        try {
                            val workout = doc.toObject(Workout::class.java)?.copy(id = doc.id)
                            if (workout != null) workoutList.add(workout)
                        } catch (e: Exception) {
                            hadError = true
                            e.printStackTrace()
                        }
                    }
                }
                filterWorkouts() // Filter the workouts after fetching
                if (hadError) {
                    Toast.makeText(this, "Some workouts could not be loaded.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun filterWorkouts(category: String = "All") {
        val query = searchEditText.text.toString().trim().lowercase()
        filteredList.clear()
        filteredList.addAll(workoutList.filter { workout ->
            val matchesSearch = (workout.name ?: "").lowercase().contains(query) ||
                    (workout.description ?: "").lowercase().contains(query) ||
                    (workout.target ?: "").lowercase().contains(query) ||
                    (workout.muscle_groups ?: "").lowercase().contains(query) ||
                    (workout.equipment ?: "").lowercase().contains(query)

            val matchesCategory = when (category) {
                "All" -> true
                "Chest" -> (workout.muscle_groups?.contains("chest", ignoreCase = true) == true ||
                        workout.muscle_groups?.contains("pectorals", ignoreCase = true) == true) ||
                        (workout.target?.contains("chest", ignoreCase = true) == true)
                "Back" -> (workout.muscle_groups?.contains("back", ignoreCase = true) == true ||
                        workout.muscle_groups?.contains("lats", ignoreCase = true) == true ||
                        workout.muscle_groups?.contains("rhomboids", ignoreCase = true) == true ||
                        workout.muscle_groups?.contains("traps", ignoreCase = true) == true) ||
                        (workout.target?.contains("back", ignoreCase = true) == true)
                "Legs" -> (workout.muscle_groups?.contains("legs", ignoreCase = true) == true ||
                        workout.muscle_groups?.contains("quads", ignoreCase = true) == true ||
                        workout.muscle_groups?.contains("hamstrings", ignoreCase = true) == true ||
                        workout.muscle_groups?.contains("glutes", ignoreCase = true) == true ||
                        workout.muscle_groups?.contains("calves", ignoreCase = true) == true) ||
                        (workout.target?.contains("legs", ignoreCase = true) == true)
                "Arms" -> (workout.muscle_groups?.contains("arms", ignoreCase = true) == true ||
                        workout.muscle_groups?.contains("biceps", ignoreCase = true) == true ||
                        workout.muscle_groups?.contains("triceps", ignoreCase = true) == true ||
                        workout.muscle_groups?.contains("forearms", ignoreCase = true) == true) ||
                        (workout.target?.contains("arms", ignoreCase = true) == true) ||
                        (workout.target?.contains("biceps", ignoreCase = true) == true) ||
                        (workout.target?.contains("triceps", ignoreCase = true) == true)
                else -> true
            }
            matchesSearch && matchesCategory
        })
        adapter.notifyDataSetChanged()
    }

    // Removed setupLikedWorkoutsButton

    // Removed listenForLikedWorkouts

    override fun onDestroy() {
        super.onDestroy()
        firestoreListener?.remove()
        // No liked workouts listener to remove
    }
}