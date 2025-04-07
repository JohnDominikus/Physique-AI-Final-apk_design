package com.example.physiqueaiapkfinal

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.physiqueaiapkfinal.R
import java.text.SimpleDateFormat
import java.util.*

class TaskActivity : AppCompatActivity() {

    private lateinit var etTaskInput: EditText
    private lateinit var btnSelectDate: Button
    private lateinit var btnSelectTime: Button
    private lateinit var spinnerWorkoutType: Spinner
    private lateinit var btnAddTask: Button
    private lateinit var recyclerTasks: RecyclerView
    private lateinit var btnBack: ImageView
    private lateinit var calendarView: RecyclerView

    private val taskList = mutableListOf<Task>()
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var weeklyCalendarAdapter: WeeklyCalendarAdapter
    private val calendar = Calendar.getInstance()
    private val workoutTypes = arrayOf("Cardio", "Strength", "Flexibility", "Rest")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task)

        etTaskInput = findViewById(R.id.etTaskInput)
        btnSelectDate = findViewById(R.id.btnSelectDate)
        btnSelectTime = findViewById(R.id.btnSelectTime)
        spinnerWorkoutType = findViewById(R.id.spinnerWorkoutType)
        btnAddTask = findViewById(R.id.btnAddTask)
        recyclerTasks = findViewById(R.id.recyclerTasks)
        btnBack = findViewById(R.id.btnBack)
        calendarView = findViewById(R.id.calendarView)

        btnBack.setOnClickListener { finish() }

        // Setup workout type spinner
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, workoutTypes)
        spinnerWorkoutType.adapter = adapter

        // Setup RecyclerView for tasks
        taskAdapter = TaskAdapter(taskList)
        recyclerTasks.layoutManager = LinearLayoutManager(this)
        recyclerTasks.adapter = taskAdapter

        // Setup RecyclerView for weekly calendar
        weeklyCalendarAdapter = WeeklyCalendarAdapter(taskList)
        calendarView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        calendarView.adapter = weeklyCalendarAdapter

        // Date Picker
        btnSelectDate.setOnClickListener {
            val datePicker = DatePickerDialog(this, { _, year, month, day ->
                calendar.set(year, month, day)
                updateDateButtonText()
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
            datePicker.show()
        }

        // Time Picker
        btnSelectTime.setOnClickListener {
            val timePicker = TimePickerDialog(this, { _, hour, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
                updateTimeButtonText()
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true)
            timePicker.show()
        }

        // Add Task
        btnAddTask.setOnClickListener {
            val taskName = etTaskInput.text.toString().trim()
            val selectedWorkoutType = spinnerWorkoutType.selectedItem.toString()
            val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(calendar.time)

            if (taskName.isNotEmpty()) {
                val newTask = Task(taskName, formattedDate, selectedWorkoutType, false)
                taskList.add(newTask)
                taskAdapter.notifyItemInserted(taskList.size - 1) // Notify Task List Adapter
                updateWeeklyCalendar() // Notify Weekly Calendar Adapter
                etTaskInput.text.clear()
            }
        }
    }

    private fun updateWeeklyCalendar() {
        // Notify the weekly calendar adapter that data has changed
        weeklyCalendarAdapter.notifyDataSetChanged()
    }

    private fun updateDateButtonText() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        btnSelectDate.text = dateFormat.format(calendar.time)
    }

    private fun updateTimeButtonText() {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        btnSelectTime.text = timeFormat.format(calendar.time)
    }

    // Task Model
    data class Task(
        val name: String,
        val date: String,
        val workoutType: String,
        var isCompleted: Boolean
    )

    // Task Adapter
    class TaskAdapter(private val tasks: List<Task>) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

        class TaskViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val taskName: TextView = view.findViewById(R.id.tvTaskName)
            val taskDate: TextView = view.findViewById(R.id.tvTaskDate)
            val taskCheckbox: CheckBox = view.findViewById(R.id.cbTask)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
            return TaskViewHolder(view)
        }

        override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
            val task = tasks[position]
            holder.taskName.text = task.name
            holder.taskDate.text = task.date
            holder.taskCheckbox.isChecked = task.isCompleted

            holder.taskCheckbox.setOnCheckedChangeListener { _, isChecked ->
                task.isCompleted = isChecked
            }
        }

        override fun getItemCount(): Int {
            return tasks.size
        }
    }

    // Weekly Calendar Adapter
    class WeeklyCalendarAdapter(private val tasks: List<Task>) : RecyclerView.Adapter<WeeklyCalendarAdapter.WeeklyCalendarViewHolder>() {

        class WeeklyCalendarViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val dayText: TextView = view.findViewById(R.id.tvDay)
            val taskText: TextView = view.findViewById(R.id.tvTask)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WeeklyCalendarViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_calendar_day, parent, false)
            return WeeklyCalendarViewHolder(view)
        }

        override fun onBindViewHolder(holder: WeeklyCalendarViewHolder, position: Int) {
            val day = getDateForPosition(position)
            val taskForDay = getTasksForDay(day)

            holder.dayText.text = SimpleDateFormat("EEEE", Locale.getDefault()).format(day)
            holder.taskText.text = if (taskForDay.isNotEmpty()) {
                taskForDay.joinToString(", ") { it.name }
            } else {
                "No tasks"
            }
        }

        override fun getItemCount(): Int {
            return 7 // A week has 7 days
        }

        private fun getDateForPosition(position: Int): Date {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, position)
            return calendar.time
        }

        private fun getTasksForDay(date: Date): List<Task> {
            val formattedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
            return tasks.filter { it.date.startsWith(formattedDate) }
        }
    }
}
