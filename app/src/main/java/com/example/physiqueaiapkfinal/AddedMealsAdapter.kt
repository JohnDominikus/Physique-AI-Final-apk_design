package com.example.physiqueaiapkfinal

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AddedMealsAdapter(
    private var meals: List<MealTodo>, 
    private val onDoneClick: (MealTodo) -> Unit,
    private val onItemClick: (MealTodo) -> Unit
) : RecyclerView.Adapter<AddedMealsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMealName: TextView = view.findViewById(R.id.tvMealName)
        val tvMealType: TextView = view.findViewById(R.id.tvMealType)
        val tvCalories: TextView = view.findViewById(R.id.tvCalories)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
        val btnDoneMeal: Button = view.findViewById(R.id.btnDoneMeal)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_meal, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val meal = meals[position]
        holder.tvMealName.text = meal.mealName
        holder.tvMealType.text = meal.mealType
        holder.tvCalories.text = "${meal.calories} kcal"
        holder.tvTime.text = meal.scheduledTime

        // Click on the entire item goes to recipe details
        holder.itemView.setOnClickListener {
            onItemClick(meal)
        }

        // Click on the done button marks meal as completed
        holder.btnDoneMeal.setOnClickListener {
            onDoneClick(meal)
        }

        // Update button state based on completion
        if (meal.isCompleted) {
            holder.btnDoneMeal.text = "Completed"
            holder.btnDoneMeal.isEnabled = false
            holder.tvMealName.paintFlags = holder.tvMealName.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            holder.btnDoneMeal.text = "Done"
            holder.btnDoneMeal.isEnabled = true
            holder.tvMealName.paintFlags = holder.tvMealName.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
    }

    override fun getItemCount() = meals.size

    fun updateMeals(newMeals: List<MealTodo>) {
        meals = newMeals
        notifyDataSetChanged()
    }
}