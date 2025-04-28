package com.example.physiqueaiapkfinal

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.physiqueaiapkfinal.FirebaseManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class TaskActivity : AppCompatActivity() {

    private lateinit var recyclerTasks: RecyclerView
    private lateinit var calendarView: RecyclerView
    private lateinit var btnBack: ImageView
    private lateinit var btnAddTask: Button

    private val taskList = mutableListOf<Task>()
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var weeklyCalendarAdapter: WeeklyCalendarAdapter

    private val calendar = Calendar.getInstance()
    private val workoutTypes = arrayOf("Cardio", "Strength", "Flexibility", "Rest")

    // Firebase variables
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var tasksRef: DatabaseReference
    private lateinit var valueEventListener: ValueEventListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task)

        // Initialize Firebase services using FirebaseManager
        auth = FirebaseManager.auth
        tasksRef = Firebase.database.getReference("tasks").child(auth.currentUser?.uid ?: "default")


        // Initialize RecyclerView for tasks and calendar
        recyclerTasks = findViewById(R.id.recyclerTasks)
        calendarView = findViewById(R.id.calendarView)
        btnBack = findViewById(R.id.btnBack)
        btnAddTask = findViewById(R.id.btnAddTask)

        // Setup RecyclerView for tasks
        recyclerTasks.layoutManager = LinearLayoutManager(this)
        taskAdapter = TaskAdapter(taskList, ::deleteTaskFromFirebase)
        recyclerTasks.adapter = taskAdapter

        // Setup RecyclerView for calendar
        calendarView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        weeklyCalendarAdapter = WeeklyCalendarAdapter(emptyList())
        calendarView.adapter = weeklyCalendarAdapter

        // Setup back button
        btnBack.setOnClickListener { onBackPressed() }

        // Setup add task button
        btnAddTask.setOnClickListener { showAddTaskDialog() }

        // Setup Firebase listener to sync tasks
        setupFirebaseListener()

        // Initialize greeting message
        initializeGreetingMessages()
    }

    private fun setupFirebaseListener() {
        valueEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                taskList.clear()
                for (taskSnapshot in snapshot.children) {
                    val task = taskSnapshot.getValue(Task::class.java)
                    task?.let {
                        it.id = taskSnapshot.key ?: ""
                        taskList.add(it)
                    }
                }
                taskAdapter.notifyDataSetChanged()
                updateWeeklyCalendar()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@TaskActivity, "Failed to load tasks: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        }
        tasksRef.addValueEventListener(valueEventListener)
    }

    private fun showAddTaskDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.activity_addtask, null)

        val etExerciseDetails = dialogView.findViewById<EditText>(R.id.etExerciseDetails)
        val btnSelectDateTime = dialogView.findViewById<Button>(R.id.btnSelectDateTime)
        val spinnerExerciseType = dialogView.findViewById<Spinner>(R.id.spinnerExerciseType)
        val spinnerMealType = dialogView.findViewById<Spinner>(R.id.spinnerMealType)

        val dialogCalendar = Calendar.getInstance()

        // Set up spinners
        spinnerExerciseType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, workoutTypes)

        val mealTypes = arrayOf("Breakfast", "Lunch", "Dinner", "Snack")
        spinnerMealType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, mealTypes)

        btnSelectDateTime.setOnClickListener {
            DatePickerDialog(this, { _, year, month, day ->
                TimePickerDialog(this, { _, hour, minute ->
                    dialogCalendar.set(year, month, day, hour, minute)
                    btnSelectDateTime.text = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(dialogCalendar.time)
                }, dialogCalendar.get(Calendar.HOUR_OF_DAY), dialogCalendar.get(Calendar.MINUTE), true).show()
            }, dialogCalendar.get(Calendar.YEAR), dialogCalendar.get(Calendar.MONTH), dialogCalendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("Add Task")
            .setPositiveButton("Add") { _, _ ->
                val exerciseDetails = etExerciseDetails.text.toString().trim()
                val dateTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(dialogCalendar.time)
                val exerciseType = spinnerExerciseType.selectedItem.toString()
                val mealType = spinnerMealType.selectedItem.toString()

                if (exerciseDetails.isNotEmpty()) {
                    val newTask = Task(
                        id = "",
                        details = exerciseDetails,
                        date = dateTime,
                        exerciseType = exerciseType,
                        mealType = mealType,
                        isCompleted = false
                    )
                    addTaskToFirebase(newTask)
                } else {
                    Toast.makeText(this, "Please enter exercise details", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.show()
    }

    private fun addTaskToFirebase(task: Task) {
        val taskRef = tasksRef.push()
        task.id = taskRef.key ?: ""
        taskRef.setValue(task)
            .addOnSuccessListener {
                Toast.makeText(this, "Task added successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to add task", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteTaskFromFirebase(task: Task) {
        if (task.id.isNotEmpty()) {
            tasksRef.child(task.id).removeValue()
                .addOnSuccessListener {
                    taskList.remove(task)
                    taskAdapter.notifyDataSetChanged()
                    updateWeeklyCalendar()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to delete task", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun updateTaskInFirebase(task: Task) {
        if (task.id.isNotEmpty()) {
            tasksRef.child(task.id).setValue(task)
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to update task", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun initializeGreetingMessages() {
        val welcomeMessage = findViewById<TextView>(R.id.tvWelcomeMessage)
        val todayMessage = findViewById<TextView>(R.id.tvTodayMessage)
        welcomeMessage.text = "Welcome! This is your weekly task."
        todayMessage.text = "Your tasks today: ${getTodaysTasksCount()}"
    }

    private fun getTodaysTasksCount(): Int {
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)
        return taskList.count { it.date.startsWith(todayDate) }
    }

    private fun getWeeklyTaskCounts(): List<Pair<String, Int>> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        val counts = mutableListOf<Pair<String, Int>>()

        for (i in 0 until 7) {
            val dateStr = dateFormat.format(cal.time)
            val count = taskList.count { it.date.startsWith(dateStr) }
            counts.add(dateStr to count)
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        return counts
    }

    private fun updateWeeklyCalendar() {
        weeklyCalendarAdapter.updateData(getWeeklyTaskCounts())
    }

    override fun onDestroy() {
        super.onDestroy()
        tasksRef.removeEventListener(valueEventListener)
    }

    // Task data class - updated for Firebase
    data class Task(
        var id: String = "",
        val details: String = "",
        val date: String = "",
        val exerciseType: String = "",
        val mealType: String = "",
        var isCompleted: Boolean = false
    )

    // Task Adapter - updated to handle Firebase updates
    class TaskAdapter(
        private val tasks: MutableList<Task>,
        private val onDeleteClick: (Task) -> Unit
    ) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

        inner class TaskViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val taskDetails: TextView = view.findViewById(R.id.tvTaskDetails)
            val taskDate: TextView = view.findViewById(R.id.tvTaskDate)
            val taskExerciseType: TextView = view.findViewById(R.id.tvExerciseType)
            val taskMealType: TextView = view.findViewById(R.id.tvMealType)
            val taskCheckbox: CheckBox = view.findViewById(R.id.cbTask)
            val deleteButton: ImageButton = view.findViewById(R.id.btnDeleteTask)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
            return TaskViewHolder(view)
        }

        override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
            val task = tasks[position]
            holder.taskDetails.text = task.details
            holder.taskDate.text = task.date
            holder.taskExerciseType.text = task.exerciseType
            holder.taskMealType.text = task.mealType
            holder.taskCheckbox.isChecked = task.isCompleted

            holder.taskCheckbox.setOnCheckedChangeListener { _, isChecked ->
                task.isCompleted = isChecked
                (holder.itemView.context as? TaskActivity)?.updateTaskInFirebase(task)
            }

            holder.deleteButton.setOnClickListener {
                onDeleteClick(task)
            }
        }

        override fun getItemCount() = tasks.size
    }

    // Weekly Calendar Adapter remains the same
    class WeeklyCalendarAdapter(private var data: List<Pair<String, Int>>) :
        RecyclerView.Adapter<WeeklyCalendarAdapter.WeeklyViewHolder>() {

        inner class WeeklyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val dateText: TextView = view.findViewById(R.id.tvCalendarDate)
            val taskCountText: TextView = view.findViewById(R.id.tvTaskCount)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WeeklyViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_calendar_day, parent, false)
            return WeeklyViewHolder(view)
        }

        override fun onBindViewHolder(holder: WeeklyViewHolder, position: Int) {
            val (date, count) = data[position]
            holder.dateText.text = date
            holder.taskCountText.text = "$count Tasks"
        }

        override fun getItemCount() = data.size

        fun updateData(newData: List<Pair<String, Int>>) {
            data = newData
            notifyDataSetChanged()
        }
    }
}
