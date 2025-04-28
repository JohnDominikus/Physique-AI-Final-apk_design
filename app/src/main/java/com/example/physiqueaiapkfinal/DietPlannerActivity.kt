package com.example.physiqueaiapkfinal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class DietPlannerActivity : AppCompatActivity() {

    private lateinit var recyclerMeals: RecyclerView
    private lateinit var mealAdapter: MealAdapter
    private val mealList = mutableListOf<MealItem>()
    private val filteredList = mutableListOf<MealItem>()
    private lateinit var tvNutritionSummary: TextView
    private lateinit var searchView: SearchView
    private lateinit var chipGroupMealType: ChipGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diet_planner)

        // Initialize views
        recyclerMeals = findViewById(R.id.recyclerMeals)
        tvNutritionSummary = findViewById(R.id.tvNutritionSummary)
        searchView = findViewById(R.id.searchView)
        chipGroupMealType = findViewById(R.id.chipGroupMealType)

        setupSampleMeals()
        setupRecyclerView()
        setupChipGroup()
        setupSearchView()
        setupAddMealButton()
        updateNutritionSummary()
    }

    private fun setupSampleMeals() {
        val sampleMeals = listOf(
            MealItem("Protein Pancakes", "High-protein breakfast with banana", 350, 30, "Breakfast", R.drawable.diet1),
            MealItem("Grilled Chicken Salad", "Lean protein with fresh vegetables", 450, 40, "Lunch", R.drawable.diet1),
            MealItem("Salmon with Quinoa", "Healthy omega-3 rich dinner", 500, 35, "Dinner", R.drawable.diet1),
            MealItem("Greek Yogurt with Berries", "Protein-packed snack", 250, 15, "Snack", R.drawable.diet1)
        )
        mealList.addAll(sampleMeals)
        filteredList.addAll(sampleMeals)
    }

    private fun setupRecyclerView() {
        mealAdapter = MealAdapter(filteredList) { meal ->
            Toast.makeText(this, "${meal.name} added to your plan", Toast.LENGTH_SHORT).show()
        }

        recyclerMeals.apply {
            layoutManager = LinearLayoutManager(this@DietPlannerActivity)
            adapter = mealAdapter
            itemAnimator = DefaultItemAnimator()
        }
    }

    private fun setupChipGroup() {
        chipGroupMealType.setOnCheckedStateChangeListener { group, checkedIds ->
            val selectedMealType = if (checkedIds.isEmpty()) {
                "All"
            } else {
                val chipId = checkedIds.first()
                group.findViewById<Chip>(chipId)?.text?.toString() ?: "All"
            }
            applyFilters(searchView.query.toString(), selectedMealType)
        }
    }

    private fun setupSearchView() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                applyFilters(newText ?: "", getSelectedMealType())
                return true
            }
        })
    }

    private fun setupAddMealButton() {
        findViewById<Button>(R.id.btnAddMeal).setOnClickListener {
            val newMeal = MealItem("Custom Meal", "Your custom created meal", 400, 25, "Custom", R.drawable.diet1)
            mealList.add(newMeal)
            applyFilters(searchView.query.toString(), getSelectedMealType())
            updateNutritionSummary()
        }
    }

    private fun applyFilters(query: String, mealType: String) {
        filteredList.apply {
            clear()
            addAll(mealList.filter { meal ->
                val matchesType = mealType == "All" || meal.mealType.equals(mealType, ignoreCase = true)
                val matchesQuery = query.isBlank() || listOf(meal.name, meal.description, meal.mealType)
                    .any { it.contains(query, ignoreCase = true) }
                matchesType && matchesQuery
            })
        }
        mealAdapter.notifyDataSetChanged()
        updateNutritionSummary()
    }

    private fun getSelectedMealType(): String {
        return chipGroupMealType.checkedChipIds.firstOrNull()?.let {
            chipGroupMealType.findViewById<Chip>(it)?.text.toString()
        } ?: "All"
    }

    private fun updateNutritionSummary() {
        val totalCalories = filteredList.sumOf { it.calories }
        val totalProtein = filteredList.sumOf { it.protein }
        tvNutritionSummary.text = "Meals: ${filteredList.size} | Calories: $totalCalories | Protein: ${totalProtein}g"
    }

    // Data class
    data class MealItem(
        val name: String,
        val description: String,
        val calories: Int,
        val protein: Int,
        val mealType: String,
        val imageResId: Int
    )

    // Adapter
    class MealAdapter(
        private val items: List<MealItem>,
        private val onAddClick: (MealItem) -> Unit
    ) : RecyclerView.Adapter<MealAdapter.MealViewHolder>() {

        inner class MealViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val tvTitle: TextView = view.findViewById(R.id.tvTitle)
            private val tvDescription: TextView = view.findViewById(R.id.tvDescription)
            private val tvCalories: TextView = view.findViewById(R.id.tvCalories)
            private val tvProtein: TextView = view.findViewById(R.id.tvProtein)
            private val tvMealType: TextView = view.findViewById(R.id.tvMealType)
            private val imgMeal: ImageView = view.findViewById(R.id.imgMeal)
            private val btnAdd: Button = view.findViewById(R.id.btnAdd)

            fun bind(meal: MealItem) {
                with(meal) {
                    tvTitle.text = name
                    tvDescription.text = description
                    tvCalories.text = "$calories kcal"
                    tvProtein.text = "$protein g protein"
                    tvMealType.text = mealType

                    Glide.with(itemView.context)
                        .load(imageResId)
                        .apply(RequestOptions()
                            .placeholder(R.drawable.meal_placeholder)
                            .error(R.drawable.meal_placeholder))
                        .into(imgMeal)

                    btnAdd.setOnClickListener { onAddClick(this) }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MealViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_meal, parent, false)
            return MealViewHolder(view)
        }

        override fun onBindViewHolder(holder: MealViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount() = items.size
    }
}
