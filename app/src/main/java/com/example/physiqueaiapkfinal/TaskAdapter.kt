package com.example.physiqueaiapkfinal

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip

class TaskAdapter(private val tasks: List<Map<String, String>>, private val onDeleteClick: (Map<String, String>) -> Unit) :
    RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    inner class TaskViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val taskTitle: TextView = view.findViewById(R.id.tvTaskName)
        val taskDate: TextView = view.findViewById(R.id.tvTaskDate)
        val workoutType: Chip = view.findViewById(R.id.chipWorkoutType)
        val checkBox: CheckBox = view.findViewById(R.id.cbTask)
        val deleteButton: View = view.findViewById(R.id.btnDeleteTask)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun getItemCount() = tasks.size

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = tasks[position]

        holder.taskTitle.text = task["name"] ?: "Task Name"
        holder.taskDate.text = task["date"] ?: "Date"
        holder.workoutType.text = task["workoutType"] ?: "Workout Type"

        holder.deleteButton.setOnClickListener {
            onDeleteClick(task)
        }

        holder.checkBox.isChecked = task["isChecked"]?.toBoolean() ?: false
    }
}
