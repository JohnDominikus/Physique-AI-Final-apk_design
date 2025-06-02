package com.example.physiqueaiapkfinal

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class LikedWorkoutsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: LikedWorkoutAdapter
    private val likedWorkoutList = mutableListOf<Workout>()
    private var firestoreListener: ListenerRegistration? = null
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var backButton: ImageButton
    private lateinit var emptyListMessage: TextView
    private lateinit var loadingIndicator: ProgressBar

    private val TAG = "LikedWorkoutsActivity"
    private val BATCH_SIZE = 10 // Firestore "whereIn" max limit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_liked_workouts)

        initializeViews()
        setupAdapter()
        setupBackButton()
        fetchLikedWorkouts()
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.likedWorkoutsRecycler)
        recyclerView.layoutManager = LinearLayoutManager(this)
        backButton = findViewById(R.id.backButton)
        emptyListMessage = findViewById(R.id.emptyListMessage)
        loadingIndicator = findViewById(R.id.loadingIndicator)
    }

    private fun setupAdapter() {
        adapter = LikedWorkoutAdapter(
            likedWorkoutList,
            onViewClick = { workout ->
                // Open workout details screen
                val intent = Intent(this, WorkoutDetailActivity::class.java).apply {
                    putExtra("workoutId", workout.id)
                }
                startActivity(intent)
            },
            onRemoveClick = { workout ->
                // Remove workout from liked list
                removeWorkoutFromLiked(workout)
            }
        )
        recyclerView.adapter = adapter
    }

    private fun setupBackButton() {
        backButton.setOnClickListener {
            onBackPressed()
        }
    }

    private fun fetchLikedWorkouts() {
        val userId = auth.currentUser?.uid

        showLoading()

        if (userId == null) {
            Log.d(TAG, "User not logged in")
            handleError("Please log in to view liked workouts")
            return
        }

        Log.d(TAG, "Fetching liked workouts for user: $userId")

        firestoreListener = db.collection("users")
            .document(userId)
            .collection("likedWorkouts")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error fetching liked workouts", error)
                    handleError("Error fetching liked workouts")
                    return@addSnapshotListener
                }

                if (snapshot == null) {
                    Log.d(TAG, "Liked workouts snapshot is null")
                    handleError("Error fetching data")
                    return@addSnapshotListener
                }

                Log.d(TAG, "Liked workouts snapshot received. Documents: ${snapshot.size()}")

                val likedWorkoutIds = snapshot.documents.map { doc ->
                    // Use workoutId field if present, otherwise doc ID
                    doc.getString("workoutId") ?: doc.id
                }

                likedWorkoutList.clear()

                if (likedWorkoutIds.isEmpty()) {
                    Log.d(TAG, "No liked workouts found")
                    showEmptyState("You haven't liked any workouts yet")
                } else {
                    Log.d(TAG, "Fetching details for ${likedWorkoutIds.size} liked workouts")
                    fetchWorkoutDetails(likedWorkoutIds)
                }
            }
    }

    private fun fetchWorkoutDetails(workoutIds: List<String>) {
        val batches = workoutIds.chunked(BATCH_SIZE)
        var completedBatches = 0
        val totalBatches = batches.size

        for (batch in batches) {
            db.collection("workoutcollection")
                .whereIn(FieldPath.documentId(), batch)
                .get()
                .addOnSuccessListener { workoutsSnapshot ->
                    Log.d(TAG, "Batch workout details received. Count: ${workoutsSnapshot.size()}")

                    for (doc in workoutsSnapshot.documents) {
                        try {
                            val workout = doc.toObject(Workout::class.java)?.copy(id = doc.id)
                            workout?.let {
                                likedWorkoutList.add(it)
                                Log.d(TAG, "Added workout: ${it.name}")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing workout document: ${doc.id}", e)
                        }
                    }

                    completedBatches++
                    if (completedBatches == totalBatches) {
                        runOnUiThread {
                            adapter.notifyDataSetChanged()
                            updateUI()
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error fetching workout details for batch", e)
                    completedBatches++
                    if (completedBatches == totalBatches) {
                        runOnUiThread {
                            handleError("Error loading some workout details")
                        }
                    }
                }
        }
    }

    private fun showLoading() {
        loadingIndicator.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyListMessage.visibility = View.GONE
    }

    private fun showEmptyState(message: String) {
        loadingIndicator.visibility = View.GONE
        recyclerView.visibility = View.GONE
        emptyListMessage.visibility = View.VISIBLE
        emptyListMessage.text = message
    }

    private fun handleError(message: String) {
        loadingIndicator.visibility = View.GONE
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        showEmptyState(message)
    }

    private fun updateUI() {
        loadingIndicator.visibility = View.GONE

        if (likedWorkoutList.isEmpty()) {
            showEmptyState("You haven't liked any workouts yet")
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyListMessage.visibility = View.GONE
        }
    }

    private fun removeWorkoutFromLiked(workout: Workout) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Log.d(TAG, "User not logged in - can't remove liked workout")
            Toast.makeText(this, "Please log in to modify liked workouts", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Removing liked workout: ${workout.id}")

        db.collection("users")
            .document(userId)
            .collection("likedWorkouts")
            .document(workout.id)
            .delete()
            .addOnSuccessListener {
                Log.d(TAG, "Successfully removed liked workout")
                Toast.makeText(this, "${workout.name} removed from liked workouts", Toast.LENGTH_SHORT).show()

                // Remove locally and refresh UI
                likedWorkoutList.removeAll { it.id == workout.id }
                adapter.notifyDataSetChanged()
                updateUI()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error removing liked workout", e)
                Toast.makeText(this, "Error removing workout: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Optional helper to add liked workout externally
    fun addWorkoutToLiked(workoutId: String) {
        val userId = auth.currentUser?.uid ?: return

        val likedWorkoutData = mapOf(
            "workoutId" to workoutId,
            "timestamp" to System.currentTimeMillis(),
            "dateAdded" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        db.collection("users")
            .document(userId)
            .collection("likedWorkouts")
            .document(workoutId)
            .set(likedWorkoutData)
            .addOnSuccessListener {
                Log.d(TAG, "Successfully added workout to liked")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error adding workout to liked", e)
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        firestoreListener?.remove()
        Log.d(TAG, "Listener removed, Activity destroyed")
    }
}
