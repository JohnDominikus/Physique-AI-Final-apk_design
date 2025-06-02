package com.example.physiqueaiapkfinal

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
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
    private lateinit var btnAll: Button
    private lateinit var btnChest: Button
    private lateinit var btnBack: Button
    private lateinit var btnLegs: Button
    private lateinit var btnArms: Button
    private lateinit var likedWorkoutsButton: ImageButton // ImageButton for liked workouts

    private val workoutList = mutableListOf<Workout>()
    private var filteredList = mutableListOf<Workout>()
    private lateinit var adapter: WorkoutAdapter
    private var firestoreListener: ListenerRegistration? = null
    private val db = FirebaseFirestore.getInstance() // Get Firestore instance
    private val auth = FirebaseAuth.getInstance() // Get FirebaseAuth instance
    private val likedWorkoutIds = mutableSetOf<String>() // To store liked workout IDs
    private var likedWorkoutsListener: ListenerRegistration? = null // Listener for liked workouts

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workoutlist)

        recyclerView = findViewById(R.id.workoutRecycler)
        searchEditText = findViewById(R.id.searchWorkoutEditText)
        btnAll = findViewById(R.id.btnAllWorkouts)
        btnChest = findViewById(R.id.btnChest)
        btnBack = findViewById(R.id.btnBack)
        btnLegs = findViewById(R.id.btnLegs)
        btnArms = findViewById(R.id.btnArms)
        likedWorkoutsButton = findViewById(R.id.likedWorkoutsButton) // Find the liked workouts button

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = WorkoutAdapter(filteredList) { workout ->
            // Handle item click: Navigate to WorkoutDetailActivity
            val intent = Intent(this, WorkoutDetailActivity::class.java)
            intent.putExtra("workoutId", workout.id)
            startActivity(intent)
        }
        recyclerView.adapter = adapter

        setupSearchAndFilter()
        listenForWorkoutsRealtime()
        setupLikedWorkoutsButton()
        listenForLikedWorkouts() // Start listening for liked workouts
    }

    private fun setupSearchAndFilter() {
        findViewById<ImageButton>(R.id.searchWorkoutButton).setOnClickListener {
            filterWorkouts()
        }
        searchEditText.setOnEditorActionListener { _, _, _ ->
            filterWorkouts()
            true
        }
        btnAll.setOnClickListener { filterWorkouts(category = "All") }
        btnChest.setOnClickListener { filterWorkouts(category = "Chest") }
        btnBack.setOnClickListener { filterWorkouts(category = "Back") }
        btnLegs.setOnClickListener { filterWorkouts(category = "Legs") }
        btnArms.setOnClickListener { filterWorkouts(category = "Arms") }
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

    private fun setupLikedWorkoutsButton() {
        likedWorkoutsButton.setOnClickListener {
            val intent = Intent(this, LikedWorkoutsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun listenForLikedWorkouts() {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            likedWorkoutsListener = db.collection("users").document(userId).collection("likedWorkouts")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        // Handle error
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        likedWorkoutIds.clear()
                        for (doc in snapshot.documents) {
                            doc.getString("workoutId")?.let { likedWorkoutIds.add(it) }
                        }
                        // Update the adapter to reflect liked status changes
                        adapter.setLikedWorkoutIds(likedWorkoutIds)
                    }
                }
        } else {
            likedWorkoutIds.clear()
            adapter.setLikedWorkoutIds(likedWorkoutIds)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        firestoreListener?.remove()
        likedWorkoutsListener?.remove() // Remove liked workouts listener
    }
}