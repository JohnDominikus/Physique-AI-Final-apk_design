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
import androidx.activity.OnBackPressedCallback

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
    private val BATCH_SIZE = 10 // Firestore 'whereIn' max

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_liked_workouts)

        // Setup modern back press handling
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

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
                val intent = Intent(this, WorkoutDetailActivity::class.java).apply {
                    putExtra("workoutId", workout.id)
                }
                startActivity(intent)
            },
            onRemoveClick = { workout ->
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

    // 🔁 Real-time listener for liked workout IDs
    private fun fetchLikedWorkouts() {
        val userId = auth.currentUser?.uid

        showLoading()

        if (userId == null) {
            Log.d(TAG, "User not logged in")
            handleError("Please log in to view liked workouts")
            return
        }

        firestoreListener?.remove() // Clear any old listener
        firestoreListener = db.collection("users")
            .document(userId)
            .collection("likedWorkouts")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to liked workouts", error)
                    handleError("Error fetching liked workouts")
                    return@addSnapshotListener
                }

                if (snapshot == null || snapshot.isEmpty) {
                    Log.d(TAG, "No liked workouts found")
                    likedWorkoutList.clear()
                    adapter.notifyDataSetChanged()
                    showEmptyState("You haven't liked any workouts yet")
                    return@addSnapshotListener
                }

                val likedWorkoutIds = snapshot.documents.mapNotNull {
                    it.getString("workoutId") ?: it.id
                }

                Log.d(TAG, "Liked workout IDs: ${likedWorkoutIds.size}")
                fetchWorkoutDetailsInBatches(likedWorkoutIds)
            }
    }

    // 🔄 Fetch workouts in chunks of 10
    private fun fetchWorkoutDetailsInBatches(workoutIds: List<String>) {
        val batches = workoutIds.chunked(BATCH_SIZE)
        val tempWorkoutList = mutableListOf<Workout>()
        var completedBatches = 0

        if (batches.isEmpty()) {
            likedWorkoutList.clear()
            adapter.notifyDataSetChanged()
            showEmptyState("You haven't liked any workouts yet")
            return
        }

        for (batch in batches) {
            db.collection("workoutcollection")
                .whereIn(FieldPath.documentId(), batch)
                .get()
                .addOnSuccessListener { snapshot ->
                    for (doc in snapshot.documents) {
                        val workout = doc.toObject(Workout::class.java)?.copy(id = doc.id)
                        workout?.let { tempWorkoutList.add(it) }
                    }
                    completedBatches++
                    checkIfAllBatchesCompleted(completedBatches, batches.size, tempWorkoutList)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error fetching batch", e)
                    completedBatches++
                    checkIfAllBatchesCompleted(completedBatches, batches.size, tempWorkoutList)
                }
        }
    }

    private fun checkIfAllBatchesCompleted(
        completed: Int,
        total: Int,
        workouts: List<Workout>
    ) {
        if (completed == total) {
            val uniqueWorkouts = workouts.distinctBy { it.id }
            likedWorkoutList.clear()
            likedWorkoutList.addAll(uniqueWorkouts)
            adapter.notifyDataSetChanged()
            updateUI()
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
            Toast.makeText(this, "Please log in to modify liked workouts", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("users")
            .document(userId)
            .collection("likedWorkouts")
            .document(workout.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "${workout.name} removed from liked workouts", Toast.LENGTH_SHORT).show()
                likedWorkoutList.removeAll { it.id == workout.id }
                adapter.notifyDataSetChanged()
                updateUI()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error removing workout: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Optional: Add liked workout externally
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
