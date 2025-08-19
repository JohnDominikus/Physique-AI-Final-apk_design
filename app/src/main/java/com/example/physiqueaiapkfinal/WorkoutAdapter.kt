package com.example.physiqueaiapkfinal

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class WorkoutAdapter(
    private val workouts: MutableList<Workout>,
    private val onItemClick: (Workout) -> Unit
) : RecyclerView.Adapter<WorkoutAdapter.WorkoutViewHolder>() {

    private var likedWorkoutIds: MutableSet<String> = mutableSetOf()

    inner class WorkoutViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.workoutName)
        val muscleGroups: TextView = view.findViewById(R.id.workoutMuscleGroups)
        val difficulty: TextView = view.findViewById(R.id.workoutDifficulty)
        val thumbmail: ImageView = view.findViewById(R.id.workoutImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkoutViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_workout, parent, false)
        return WorkoutViewHolder(view)
    }

    override fun onBindViewHolder(holder: WorkoutViewHolder, position: Int) {
        val workout = workouts[position]
        val workoutId = workout.id ?: return

        holder.name.text = workout.name ?: "Unknown Workout"
        holder.muscleGroups.text = workout.muscle_groups ?: "Muscle Groups Not Specified"
        holder.difficulty.text = "Difficulty: ${workout.difficulty ?: "Not Specified"}"

        Glide.with(holder.itemView.context)
            .load(workout.thumbmail)
            .placeholder(android.R.drawable.sym_def_app_icon)
            .error(android.R.drawable.ic_dialog_alert)
            .into(holder.thumbmail)

        holder.itemView.setOnClickListener { onItemClick(workout) }

        // Removed heart like interactions
    }

    override fun getItemCount() = workouts.size

    // Removed heart icon updates

    // Liked workouts feature removed

    fun updateData(newList: List<Workout>) {
        workouts.clear()
        workouts.addAll(newList)
        notifyDataSetChanged()
    }
}
