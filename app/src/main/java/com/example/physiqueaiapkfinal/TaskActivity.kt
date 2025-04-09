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
import com.google.android.material.chip.Chip
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

        initViews()
        setupAdapters()
        setupListeners()
    }

    private fun initViews() {
        etTaskInput = findViewById(R.id.etTaskInput)
        btnSelectDate = findViewById(R.id.btnSelectDate)
        btnSelectTime = findViewById(R.id.btnSelectTime)
        spinnerWorkoutType = findViewById(R.id.spinnerWorkoutType)
        btnAddTask = findViewById(R.id.btnAddTask)
        recyclerTasks = findViewById(R.id.recyclerTasks)
        btnBack = findViewById(R.id.btnBack)
        calendarView = findViewById(R.id.calendarView)
    }

    private fun setupAdapters() {
        // Setup workout type spinner
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, workoutTypes)
        spinnerWorkoutType.adapter = spinnerAdapter

        // Setup RecyclerView for tasks
        taskAdapter = TaskAdapter(taskList) { task ->
            taskList.remove(task)
            taskAdapter.notifyDataSetChanged()
            updateWeeklyCalendar()
        }
        recyclerTasks.layoutManager = LinearLayoutManager(this)
        recyclerTasks.adapter = taskAdapter

        // Setup RecyclerView for weekly calendar
        weeklyCalendarAdapter = WeeklyCalendarAdapter(getWeeklyTaskCounts())
        calendarView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        calendarView.adapter = weeklyCalendarAdapter
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }

        // Date Picker
        btnSelectDate.setOnClickListener {
            DatePickerDialog(this, { _, year, month, day ->
                calendar.set(year, month, day)
                updateDateButtonText()
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        // Time Picker
        btnSelectTime.setOnClickListener {
            TimePickerDialog(this, { _, hour, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
                updateTimeButtonText()
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
        }

        // Add Task
        btnAddTask.setOnClickListener {
            val taskName = etTaskInput.text.toString().trim()
            val selectedWorkoutType = spinnerWorkoutType.selectedItem.toString()
            val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(calendar.time)

            if (taskName.isNotEmpty()) {
                val newTask = Task(
                    name = taskName,
                    date = formattedDate,
                    workoutType = selectedWorkoutType,
                    isCompleted = false
                )
                taskList.add(newTask)
                taskAdapter.notifyItemInserted(taskList.size - 1)
                updateWeeklyCalendar()
                etTaskInput.text.clear()
            } else {
                Toast.makeText(this, "Please enter a task name", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getWeeklyTaskCounts(): List<Pair<String, Int>> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val calendar = Calendar.getInstance()
        val counts = mutableListOf<Pair<String, Int>>()

        repeat(7) { i ->
            calendar.add(Calendar.DAY_OF_YEAR, if (i == 0) 0 else 1)
            val dateStr = dateFormat.format(calendar.time)
            val count = taskList.count { it.date.startsWith(dateStr) }
            counts.add(dateStr to count)
        }

        return counts
    }

    private fun updateWeeklyCalendar() {
        weeklyCalendarAdapter.updateData(getWeeklyTaskCounts())
    }

    private fun updateDateButtonText() {
        btnSelectDate.text = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
    }

    private fun updateTimeButtonText() {
        btnSelectTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(calendar.time)
    }

    data class Task(
        val name: String,
        val date: String,
        val workoutType: String,
        var isCompleted: Boolean
    )

    class TaskAdapter(
        private val tasks: MutableList<Task>,
        private val onDeleteClick: (Task) -> Unit
    ) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

        inner class TaskViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val taskName: TextView = view.findViewById(R.id.tvTaskName)
            val taskDate: TextView = view.findViewById(R.id.tvTaskDate)
            val workoutType: Chip = view.findViewById(R.id.chipWorkoutType)
            val taskCheckbox: CheckBox = view.findViewById(R.id.cbTask)
            val deleteButton: View = view.findViewById(R.id.btnDeleteTask)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_task, parent, false)
            return TaskViewHolder(view)
        }

        override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
            val task = tasks[position]
            holder.taskName.text = task.name
            holder.taskDate.text = task.date
            holder.workoutType.text = task.workoutType
            holder.taskCheckbox.isChecked = task.isCompleted

            holder.taskCheckbox.setOnCheckedChangeListener { _, isChecked ->
                task.isCompleted = isChecked
            }

            holder.deleteButton.setOnClickListener {
                onDeleteClick(task)
            }
        }

        override fun getItemCount() = tasks.size
    }

    class WeeklyCalendarAdapter(
        private var taskCounts: List<Pair<String, Int>>
    ) : RecyclerView.Adapter<WeeklyCalendarAdapter.CalendarViewHolder>() {

        fun updateData(newData: List<Pair<String, Int>>) {
            taskCounts = newData
            notifyDataSetChanged()
        }

        inner class CalendarViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val dayText: TextView = view.findViewById(R.id.calendarDate)
            val countText: TextView = view.findViewById(R.id.taskCount)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CalendarViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_calendar_day, parent, false)
            return CalendarViewHolder(view)
        }

        override fun onBindViewHolder(holder: CalendarViewHolder, position: Int) {
            val (date, count) = taskCounts[position]
            val dayName = SimpleDateFormat("EEEE", Locale.getDefault())
                .format(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date)!!)

            holder.dayText.text = dayName
            holder.countText.text = if (count > 0) "$count tasks" else "No tasks"
        }

        override fun getItemCount() = taskCounts.size
    }
}