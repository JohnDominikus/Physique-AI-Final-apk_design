package com.example.physiqueaiapkfinal

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AddedExercisesAdapter(
    private var exercises: List<AddedExercise>,
    private val onDoneClick: (AddedExercise) -> Unit,
    private val onItemClick: (AddedExercise) -> Unit
) : RecyclerView.Adapter<AddedExercisesAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvExerciseName: TextView = view.findViewById(R.id.tvExerciseName)
        val tvSets: TextView = view.findViewById(R.id.tvSets)
        val tvReps: TextView = view.findViewById(R.id.tvReps)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
        val btnDoneExercise: Button = view.findViewById(R.id.btnDoneExercise)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_exercise, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val exercise = exercises[position]
        holder.tvExerciseName.text = exercise.workoutName
        holder.tvSets.text = "${exercise.sets} sets"
        holder.tvReps.text = "${exercise.reps} reps"
        holder.tvTime.text = "${exercise.minutes}m ${exercise.seconds}s"

        holder.btnDoneExercise.setOnClickListener {
            onDoneClick(exercise)
        }

        // Set the new item click listener
        holder.itemView.setOnClickListener {
            onItemClick(exercise)
        }
    }

    override fun getItemCount() = exercises.size

    fun updateExercises(newExercises: List<AddedExercise>) {
        exercises = newExercises
        notifyDataSetChanged()
    }
}