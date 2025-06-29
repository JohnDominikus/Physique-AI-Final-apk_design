package com.example.physiqueaiapkfinal

import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

data class WorkoutTask(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "", 
    val dayOfWeek: String = "",
    val isDone: Boolean = false,
    val exerciseType: String = "",
    val duration: String = "",
    val notes: String = ""
)

class WorkoutTaskActivity : AppCompatActivity() {

    private lateinit var inputTask: EditText
    private lateinit var exerciseTypeSpinner: Spinner
    private lateinit var daySpinner: Spinner
    private lateinit var durationSpinner: Spinner
    private lateinit var addButton: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: WorkoutAdapter
    private lateinit var weekView: LinearLayout

    private val workoutList = mutableListOf<WorkoutTask>()
    private val firestore = FirebaseFirestore.getInstance()
    private val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "testUser"

    private val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    private val exerciseTypes = listOf("Cardio", "Strength Training", "Flexibility", "HIIT", "Yoga", "Pilates", "Running", "Cycling", "Swimming", "Walking")
    private val durations = listOf("15 minutes", "30 minutes", "45 minutes", "1 hour", "1.5 hours", "2 hours")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create main layout
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.






            VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xFFF5F5F5.toInt())
        }

        // Create header
        val headerLayout = createHeader()
        mainLayout.addView(headerLayout)

        // Create input section
        val inputSection = createInputSection()
        mainLayout.addView(inputSection)

        // Create week view
        weekView = createWeekView()
        mainLayout.addView(weekView)

        // Create task list
        val taskSection = createTaskSection()
        mainLayout.addView(taskSection, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0, 1f
        ))

        setContentView(mainLayout)

        adapter = WorkoutAdapter(
            workoutList,
            { task, isChecked -> updateTaskStatus(task, isChecked) },
            { saveToFirestore() }
        )
        recyclerView.adapter = adapter

        addButton.setOnClickListener {
            addNewTask()
        }

        loadFromFirestore()
    }

    private fun createHeader(): LinearLayout {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 24)
        }

        val title = TextView(this).apply {
            text = "Weekly Exercise Planner"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF2196F3.toInt())
            gravity = android.view.Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "Plan your exercises for the week"
            textSize = 14f
            setTextColor(0xFF666666.toInt())
            gravity = android.view.Gravity.CENTER
            setPadding(0, 8, 0, 0)
        }

        header.addView(title)
        header.addView(subtitle)
        return header
    }

    private fun createInputSection(): LinearLayout {
        val inputSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFFFFFFF.toInt())
            setPadding(16, 16, 16, 16)
        }

        // Task name input
        inputTask = EditText(this).apply {
            hint = "Enter exercise name (e.g., Morning Run, Gym Workout)"
            setPadding(12, 12, 12, 12)
            setBackgroundResource(android.R.drawable.edit_text)
        }

        // Day of week spinner
        val dayLabel = TextView(this).apply {
            text = "Day of Week:"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 16, 0, 8)
        }

        daySpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@WorkoutTaskActivity, android.R.layout.simple_spinner_item, daysOfWeek)
        }

        // Exercise type spinner
        val exerciseLabel = TextView(this).apply {
            text = "Exercise Type:"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 16, 0, 8)
        }

        exerciseTypeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@WorkoutTaskActivity, android.R.layout.simple_spinner_item, exerciseTypes)
        }

        // Duration spinner
        val durationLabel = TextView(this).apply {
            text = "Duration:"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 16, 0, 8)
        }

        durationSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@WorkoutTaskActivity, android.R.layout.simple_spinner_item, durations)
        }

        // Add button
        addButton = Button(this).apply {
            text = "Add Exercise"
            setBackgroundColor(0xFF4CAF50.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 12, 0, 12)
        }

        inputSection.addView(inputTask)
        inputSection.addView(dayLabel)
        inputSection.addView(daySpinner)
        inputSection.addView(exerciseLabel)
        inputSection.addView(exerciseTypeSpinner)
        inputSection.addView(durationLabel)
        inputSection.addView(durationSpinner)
        inputSection.addView(addButton)

        return inputSection
    }

    private fun createWeekView(): LinearLayout {
        val weekLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 24, 0, 24)
        }

        val weekTitle = TextView(this).apply {
            text = "This Week's Schedule"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF333333.toInt())
            setPadding(0, 0, 0, 16)
        }

        weekLayout.addView(weekTitle)
        return weekLayout
    }

    private fun createTaskSection(): LinearLayout {
        val taskSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val taskTitle = TextView(this).apply {
            text = "All Exercises"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF333333.toInt())
            setPadding(0, 0, 0, 16)
        }

        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@WorkoutTaskActivity)
        }

        taskSection.addView(taskTitle)
        taskSection.addView(recyclerView)
        return taskSection
    }

    private fun addNewTask() {
        val taskName = inputTask.text.toString().trim()
        if (taskName.isEmpty()) {
            Toast.makeText(this, "Please enter an exercise name", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedDay = daySpinner.selectedItem.toString()
        val selectedType = exerciseTypeSpinner.selectedItem.toString()
        val selectedDuration = durationSpinner.selectedItem.toString()

        val task = WorkoutTask(
            name = taskName,
            dayOfWeek = selectedDay,
            exerciseType = selectedType,
            duration = selectedDuration
        )

        workoutList.add(task)
        adapter.notifyItemInserted(workoutList.size - 1)
        inputTask.text.clear()
        saveToFirestore()
        updateWeekView()
        
        Toast.makeText(this, "Exercise added for $selectedDay", Toast.LENGTH_SHORT).show()
    }

    private fun updateTaskStatus(task: WorkoutTask, isDone: Boolean) {
        val index = workoutList.indexOfFirst { it.id == task.id }
        if (index != -1) {
            workoutList[index] = task.copy(isDone = isDone)
            adapter.notifyItemChanged(index)
            saveToFirestore()
        }
    }

    private fun updateWeekView() {
        weekView.removeAllViews()
        
        val weekTitle = TextView(this).apply {
            text = "This Week's Schedule"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF333333.toInt())
            setPadding(0, 0, 0, 16)
        }
        weekView.addView(weekTitle)

        daysOfWeek.forEach { day ->
            val dayTasks = workoutList.filter { it.dayOfWeek == day }
            if (dayTasks.isNotEmpty()) {
                val dayLayout = createDayLayout(day, dayTasks)
                weekView.addView(dayLayout)
            }
        }
    }

    private fun createDayLayout(day: String, tasks: List<WorkoutTask>): LinearLayout {
        val dayLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFFFFFFF.toInt())
            setPadding(16, 12, 16, 12)
        }

        val dayTitle = TextView(this).apply {
            text = day
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(0xFF2196F3.toInt())
        }

        dayLayout.addView(dayTitle)

        tasks.forEach { task ->
            val taskLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(16, 8, 0, 8)
            }

            val checkBox = CheckBox(this).apply {
                isChecked = task.isDone
                setOnCheckedChangeListener { _, isChecked ->
                    updateTaskStatus(task, isChecked)
                }
            }

            val taskInfo = TextView(this).apply {
                text = "${task.name} (${task.exerciseType} - ${task.duration})"
                textSize = 14f
                setTextColor(if (task.isDone) 0xFF888888.toInt() else 0xFF333333.toInt())
                setPadding(8, 0, 0, 0)
            }

            taskLayout.addView(checkBox)
            taskLayout.addView(taskInfo)
            dayLayout.addView(taskLayout)
        }

        return dayLayout
    }

    private fun saveToFirestore() {
        firestore.collection("userTasks")
            .document(userId)
            .set(mapOf("workoutTasks" to workoutList))
            .addOnSuccessListener {
                Log.d("WorkoutTaskActivity", "Tasks saved successfully")
            }
            .addOnFailureListener { e ->
                Log.e("WorkoutTaskActivity", "Failed to save tasks", e)
                Toast.makeText(this, "Failed to save tasks", Toast.LENGTH_SHORT).show()
            }
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
                updateWeekView()
            }
            .addOnFailureListener { e ->
                Log.e("WorkoutTaskActivity", "Failed to load tasks", e)
                Toast.makeText(this, "Failed to load tasks", Toast.LENGTH_SHORT).show()
            }
    }

    // Firestore mapping class
    data class UserTasks(val workoutTasks: List<WorkoutTask> = listOf())

    class WorkoutAdapter(
        private val items: List<WorkoutTask>,
        private val onTaskStatusChanged: (WorkoutTask, Boolean) -> Unit,
        private val onTaskDeleted: () -> Unit
    ) : RecyclerView.Adapter<WorkoutAdapter.WorkoutViewHolder>() {

        class WorkoutViewHolder(val layout: LinearLayout) : RecyclerView.ViewHolder(layout) {
            val checkBox: CheckBox = CheckBox(layout.context)
            val taskInfo: TextView = TextView(layout.context)
            val deleteButton: ImageButton = ImageButton(layout.context)

            init {
                layout.orientation = LinearLayout.HORIZONTAL
                layout.setPadding(16, 12, 16, 12)
                layout.setBackgroundColor(0xFFFFFFFF.toInt())
                
                checkBox.setPadding(0, 0, 8, 0)
                
                taskInfo.textSize = 16f
                taskInfo.setPadding(8, 0, 0, 0)
                taskInfo.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                
                deleteButton.setImageResource(android.R.drawable.ic_menu_delete)
                deleteButton.setPadding(8, 0, 0, 0)
                
                layout.addView(checkBox)
                layout.addView(taskInfo)
                layout.addView(deleteButton)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkoutViewHolder {
            val layout = LinearLayout(parent.context)
            return WorkoutViewHolder(layout)
        }

        override fun onBindViewHolder(holder: WorkoutViewHolder, position: Int) {
            val task = items[position]
            
            holder.taskInfo.text = "${task.name}\n${task.dayOfWeek} • ${task.exerciseType} • ${task.duration}"
            holder.checkBox.isChecked = task.isDone
            
            holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
                onTaskStatusChanged(task, isChecked)
            }
            
            holder.deleteButton.setOnClickListener {
                showDeleteConfirmation(task, holder.deleteButton.context)
            }
        }

        override fun getItemCount(): Int = items.size

        private fun showDeleteConfirmation(task: WorkoutTask, context: android.content.Context) {
            AlertDialog.Builder(context)
                .setTitle("Delete Exercise")
                .setMessage("Are you sure you want to delete '${task.name}'?")
                .setPositiveButton("Delete") { _, _ ->
                    val index = items.indexOf(task)
                    if (index != -1) {
                        (items as MutableList).removeAt(index)
                        notifyItemRemoved(index)
                        onTaskDeleted()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}
