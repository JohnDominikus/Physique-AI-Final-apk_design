package com.example.physiqueaiapkfinal

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

class LikedWorkoutAdapter(
    private val workouts: MutableList<Workout>,
    private val onViewClick: (Workout) -> Unit,
    private val onRemoveClick: (Workout) -> Unit
) : RecyclerView.Adapter<LikedWorkoutAdapter.LikedWorkoutViewHolder>() {

    inner class LikedWorkoutViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.likedWorkoutName)
        val muscleGroups: TextView = view.findViewById(R.id.likedWorkoutTarget)
        val difficulty: TextView = view.findViewById(R.id.likedWorkoutDifficulty)
        val equipment: TextView = view.findViewById(R.id.likedWorkoutEquipment)
        val thumbnail: ImageView = view.findViewById(R.id.likedWorkoutImage)
        val viewButton: Button = view.findViewById(R.id.viewWorkoutButton)
        val removeButton: ImageButton = view.findViewById(R.id.removeWorkoutButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LikedWorkoutViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_liked_workout, parent, false)
        return LikedWorkoutViewHolder(view)
    }

    override fun onBindViewHolder(holder: LikedWorkoutViewHolder, position: Int) {
        val workout = workouts[position]

        // Set workout name or fallback
        holder.name.text = workout.name?.takeIf { it.isNotBlank() } ?: "Unnamed Workout"

        // Muscle groups or fallback
        holder.muscleGroups.text = workout.muscle_groups?.takeIf { it.isNotBlank() } ?: "Muscle Groups: N/A"

        // Difficulty with capitalization or fallback
        holder.difficulty.text = workout.difficulty?.let {
            "Difficulty: ${it.replaceFirstChar { char -> char.uppercase() }}"
        } ?: "Difficulty: Unknown"

        // Equipment or fallback
        holder.equipment.text = workout.equipment?.takeIf { it.isNotBlank() }?.let {
            "Equipment: $it"
        } ?: "Equipment: None"

        // Load thumbnail image with Glide, with caching and placeholders
        Glide.with(holder.itemView.context)
            .load(workout.thumbmail) // use workout.thumbmail or your actual image URL field
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_report_image)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .centerCrop()
            .into(holder.thumbnail)

        // View workout button click
        holder.viewButton.setOnClickListener {
            onViewClick(workout)
        }

        // Remove workout button click
        holder.removeButton.setOnClickListener {
            onRemoveClick(workout)
        }

        // Optional: entire item click triggers view workout
        holder.itemView.setOnClickListener {
            onViewClick(workout)
        }
    }

    override fun getItemCount(): Int = workouts.size

    // Add a workout and notify adapter
    fun addWorkout(workout: Workout) {
        workouts.add(workout)
        notifyItemInserted(workouts.size - 1)
    }

    // Remove a workout by object and notify adapter
    fun removeWorkout(workout: Workout) {
        val position = workouts.indexOf(workout)
        if (position != -1) {
            workouts.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    // Remove workout by position safely
    fun removeWorkoutAt(position: Int) {
        if (position in 0 until workouts.size) {
            workouts.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    // Replace entire dataset and notify adapter
    fun updateWorkouts(newWorkouts: List<Workout>) {
        workouts.clear()
        workouts.addAll(newWorkouts)
        notifyDataSetChanged()
    }

    // Clear all workouts from adapter
    fun clearWorkouts() {
        val size = workouts.size
        workouts.clear()
        notifyItemRangeRemoved(0, size)
    }
}
