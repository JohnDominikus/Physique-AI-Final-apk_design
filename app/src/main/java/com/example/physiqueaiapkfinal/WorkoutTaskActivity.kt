package com.example.physiqueaiapkfinal

import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

data class WorkoutTask(val name: String = "", val isDone: Boolean = false)

class WorkoutTaskActivity : AppCompatActivity() {

    private lateinit var inputTask: EditText
    private lateinit var addButton: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: WorkoutAdapter

    private val workoutList = mutableListOf<WorkoutTask>()

    private val firestore = FirebaseFirestore.getInstance()
    private val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "testUser"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Layout programmatically
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        inputTask = EditText(this).apply {
            hint = "Enter workout task"
        }

        addButton = Button(this).apply {
            text = "Add Workout Task"
        }

        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@WorkoutTaskActivity)
        }

        layout.addView(inputTask)
        layout.addView(addButton)
        layout.addView(recyclerView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0, 1f
        ))

        setContentView(layout)

        adapter = WorkoutAdapter(workoutList)
        recyclerView.adapter = adapter

        addButton.setOnClickListener {
            val taskName = inputTask.text.toString().trim()
            if (taskName.isNotEmpty()) {
                val task = WorkoutTask(taskName)
                workoutList.add(task)
                adapter.notifyItemInserted(workoutList.size - 1)
                inputTask.text.clear()
                saveToFirestore()
            }
        }

        loadFromFirestore()
    }

    private fun saveToFirestore() {
        firestore.collection("userTasks")
            .document(userId)
            .set(mapOf("workoutTasks" to workoutList))
    }

    private fun loadFromFirestore() {
        firestore.collection("userTasks")
            .document(userId)
            .get()
            .addOnSuccessListener { doc ->
                val tasks = doc.toObject(UserTasks::class.java)
                workoutList.clear()
                workoutList.addAll(tasks?.workoutTasks ?: listOf())
                adapter.notifyDataSetChanged()
            }
    }

    // Firestore mapping class
    data class UserTasks(val workoutTasks: List<WorkoutTask> = listOf())

    class WorkoutAdapter(private val items: List<WorkoutTask>) :
        RecyclerView.Adapter<WorkoutAdapter.WorkoutViewHolder>() {

        class WorkoutViewHolder(val layout: LinearLayout) : RecyclerView.ViewHolder(layout) {
            val checkBox: CheckBox = CheckBox(layout.context)
            val textView: TextView = TextView(layout.context)

            init {
                layout.orientation = LinearLayout.HORIZONTAL
                layout.addView(checkBox)
                layout.addView(textView)
                textView.setPadding(8, 0, 0, 0)
                textView.textSize = 16f
                textView.setTypeface(null, Typeface.BOLD)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkoutViewHolder {
            val layout = LinearLayout(parent.context)
            return WorkoutViewHolder(layout)
        }

        override fun onBindViewHolder(holder: WorkoutViewHolder, position: Int) {
            val task = items[position]
            holder.textView.text = task.name
            holder.checkBox.isChecked = task.isDone
        }

        override fun getItemCount(): Int = items.size
    }
}
