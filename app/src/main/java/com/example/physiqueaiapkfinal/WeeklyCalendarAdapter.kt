package com.example.physiqueaiapkfinal

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class WeeklyCalendarAdapter(private val taskCounts: List<Pair<String, Int>>) :
    RecyclerView.Adapter<WeeklyCalendarAdapter.CalendarViewHolder>() {

    inner class CalendarViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val taskDate: TextView = view.findViewById(R.id.calendarDate)
        val countText: TextView = view.findViewById(R.id.taskCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CalendarViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_calendar_day, parent, false)
        return CalendarViewHolder(view)
    }

    override fun getItemCount() = 7 // Weekly view

    override fun onBindViewHolder(holder: CalendarViewHolder, position: Int) {
        val date = Calendar.getInstance()
        date.add(Calendar.DAY_OF_YEAR, position)  // Adjust to show the correct day in the week
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val currentDate = dateFormat.format(date.time)

        // Find the task count for the current date (if any)
        val taskCountForDate = taskCounts.find { it.first == currentDate }?.second ?: 0

        // Bind the data to the view
        holder.taskDate.text = currentDate
        holder.countText.text = "$taskCountForDate tasks"
    }
}
