package com.example.physiqueaiapkfinal

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import Exercise
class ExerciseDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exercise_detail)

        val exerciseId = intent.getStringExtra("exerciseId") ?: return

        val gifImage: ImageView = findViewById(R.id.gifImage)
        val nameText: TextView = findViewById(R.id.name)
        val descText: TextView = findViewById(R.id.description)
        val instructionsText: TextView = findViewById(R.id.instructions)
        val repsText: TextView = findViewById(R.id.reps)
        val timerText: TextView = findViewById(R.id.timer)
        val difficultyText: TextView = findViewById(R.id.difficulty)
        val muscleText: TextView = findViewById(R.id.muscleGroups)
        val safetyText: TextView = findViewById(R.id.safetyWarning)
        val equipmentText: TextView = findViewById(R.id.equipment)
        val targetText: TextView = findViewById(R.id.target)

        FirebaseFirestore.getInstance().collection("workoutcollection")
            .document(exerciseId)
            .addSnapshotListener { doc, _ ->
                val ex = doc?.toObject(Exercise::class.java) ?: return@addSnapshotListener
                nameText.text = ex.name ?: ""
                descText.text = ex.description ?: ""
                instructionsText.text = ex.instructions ?: ""
                repsText.text = "Reps: ${ex.reps ?: ""}"
                timerText.text = "Timer: ${ex.timer ?: 0}s"
                difficultyText.text = "Difficulty: ${ex.difficulty ?: ""}"
                muscleText.text = "Muscle Groups: ${ex.muscle_groups ?: ""}"
                safetyText.text = "Warning: ${ex.safety_warning ?: ""}"
                equipmentText.text = "Equipment: ${ex.equipment ?: ""}"
                targetText.text = "Target: ${ex.target ?: ""}"
                Glide.with(this)
                    .asGif()
                    .load(ex.gif_url ?: "")
                    .into(gifImage)
            }
    }
}