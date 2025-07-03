package com.example.physiqueaiapkfinal

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// (Shared WorkoutItem data class from WorkoutTodoActivity will be used)

class AutoGenerateWorkoutActivity : AppCompatActivity() {

    private lateinit var btnGenerate: Button
    private lateinit var tvStatus: TextView

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val userId: String? get() = auth.currentUser?.uid

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auto_generate_workout)

        btnGenerate = findViewById(R.id.btnGenerate)
        tvStatus = findViewById(R.id.tvStatus)

        btnGenerate.setOnClickListener {
            generateWorkoutForToday()
        }
    }

    private fun generateWorkoutForToday() {
        val uid = userId ?: return show("User not logged in")
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        // Do not duplicate generation for the same day
        firestore.collection("userTodoList").document(uid)
            .collection("workoutPlan").whereEqualTo("scheduledDate", today).get()
            .addOnSuccessListener { existing ->
                if (!existing.isEmpty) {
                    show("Workout already generated for today!")
                    return@addOnSuccessListener
                }
                fetchAndGenerate(uid, today)
            }
            .addOnFailureListener { e -> show("Error: ${e.message}") }
    }

    private fun fetchAndGenerate(uid: String, today: String) {
        firestore.collection("workoutcollection").get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) return@addOnSuccessListener show("No exercises in database")

                val chosen = snapshot.documents.shuffled().take(3) // always 3 exercises
                val batch = firestore.batch()

                chosen.forEach { doc ->
                    val wk = doc.toObject(WorkoutItem::class.java) ?: return@forEach
                    val sets = (2..4).random()
                    val reps = (10..15).random()
                    val mins = (2..3).random()
                    val secs = 0

                    val todo = WorkoutTodo(
                        workoutName = wk.name,
                        sets = sets,
                        reps = reps,
                        minutes = mins,
                        seconds = secs,
                        scheduledDate = today,
                        userId = uid,
                        muscleGroups = wk.muscle_groups.toGroupList(),
                        isCompleted = false
                    )

                    val ref = firestore.collection("userTodoList")
                        .document(uid)
                        .collection("workoutPlan")
                        .document()
                    batch.set(ref, todo)
                }

                batch.commit()
                    .addOnSuccessListener { show("Today's workout generated! Check your dashboard") }
                    .addOnFailureListener { e -> show("Failed: ${e.message}") }
            }
            .addOnFailureListener { e -> show("Error: ${e.message}") }
    }

    private fun show(msg: String) {
        tvStatus.text = msg
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}

// Helper copied from WorkoutTodoActivity
private fun String.toGroupList(): List<String> =
    split(",").map { it.trim() }.filter { it.isNotEmpty() } 