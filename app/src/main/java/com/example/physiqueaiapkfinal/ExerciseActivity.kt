package com.example.physiqueaiapkfinal

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class ExerciseActivity : AppCompatActivity() {

    private lateinit var recyclerExercise: RecyclerView
    private lateinit var exerciseAdapter: ExerciseAdapter
    private val exerciseList = mutableListOf<ExerciseItem>()
    private val filteredList = mutableListOf<ExerciseItem>()
    private lateinit var tvCartStatus: TextView
    private lateinit var searchView: SearchView
    private lateinit var chipGroupWorkout: ChipGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exercise)

        // Initialize views
        recyclerExercise = findViewById(R.id.recyclerExercise)
        tvCartStatus = findViewById(R.id.tvCartStatus)
        searchView = findViewById(R.id.searchView)
        chipGroupWorkout = findViewById(R.id.chipGroupWorkout)

        // Sample exercises
        val sampleExercises = listOf(
            ExerciseItem(
                "Full Body Workout",
                "A complete body workout.",
                "Intermediate",
                "Intermediate",
                "Strength",
                "https://youtu.be/l9_SoClAO5g"
            ),
            ExerciseItem(
                "Cardio Blast",
                "High-intensity cardio workout.",
                "Intermediate",
                "Beginner",
                "Cardio",
                "https://youtu.be/KIl70ffF5FM"
            ),
            ExerciseItem(
                "Yoga for Beginners",
                "Yoga to increase flexibility and balance.",
                "Beginner",
                "Beginner",
                "Yoga",
                "https://youtu.be/0yYiErHenzs"
            ),
            ExerciseItem(
                "Strength Training",
                "Build muscle with these exercises.",
                "Advanced",
                "Intermediate",
                "Strength",
                "https://youtu.be/example1"
            ),
            ExerciseItem(
                "Morning Cardio",
                "Start your day with energy.",
                "Beginner",
                "Beginner",
                "Cardio",
                "https://youtu.be/example2"
            )
        )

        // Add sample exercises to the list
        exerciseList.addAll(sampleExercises)
        filteredList.addAll(exerciseList)

        // Initialize the adapter
        exerciseAdapter = ExerciseAdapter(filteredList)
        recyclerExercise.layoutManager = LinearLayoutManager(this)
        recyclerExercise.adapter = exerciseAdapter
        recyclerExercise.itemAnimator = DefaultItemAnimator()

        // Setup chip group selection listener
        chipGroupWorkout.setOnCheckedChangeListener { group, checkedId ->
            val chip = group.findViewById<Chip>(checkedId)
            val selectedWorkoutType = chip?.text?.toString() ?: "All"
            applyFilters(searchView.query.toString(), selectedWorkoutType)
        }

        // Search functionality
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                val selectedChip = chipGroupWorkout.findViewById<Chip>(chipGroupWorkout.checkedChipId)
                val workoutType = selectedChip?.text?.toString() ?: "All"
                applyFilters(newText ?: "", workoutType)
                return true
            }
        })

        // Add Exercise Button functionality
        val btnAddExercise: Button = findViewById(R.id.btnAddExercise)
        btnAddExercise.setOnClickListener {
            val newExercise = ExerciseItem(
                "5 Min Full Body Cool Down Stretches",
                "Do this 5-minute cool down routine after your workouts.",
                "Beginner", "Beginner", "Strength", "https://youtu.be/Qy3U09CnELI"
            )
            exerciseList.add(newExercise)
            applyFilters(searchView.query.toString(), getSelectedWorkoutType())
            updateCartStatus()
        }

        // Initial cart status update
        updateCartStatus()
    }

    private fun applyFilters(query: String, workoutType: String) {
        filteredList.clear()

        // Apply both filters
        val filtered = exerciseList.filter { exercise ->
            // Workout type filter
            val matchesWorkoutType = workoutType == "All" || exercise.workoutType.equals(workoutType, ignoreCase = true)

            // Search query filter
            val matchesQuery = query.isEmpty() ||
                    exercise.title.contains(query, ignoreCase = true) ||
                    exercise.description.contains(query, ignoreCase = true) ||
                    exercise.workoutType.contains(query, ignoreCase = true)

            matchesWorkoutType && matchesQuery
        }

        filteredList.addAll(filtered)
        exerciseAdapter.notifyDataSetChanged()
        updateCartStatus()
    }

    private fun getSelectedWorkoutType(): String {
        return chipGroupWorkout.findViewById<Chip>(chipGroupWorkout.checkedChipId)?.text?.toString() ?: "All"
    }

    private fun updateCartStatus() {
        tvCartStatus.text = "Exercises in Cart: ${filteredList.size}"
    }
}

data class ExerciseItem(
    val title: String,
    val description: String,
    val bodyLevel: String,
    val userLevel: String,
    val workoutType: String,
    val videoUrl: String
)

class ExerciseAdapter(private val items: List<ExerciseItem>) :
    RecyclerView.Adapter<ExerciseAdapter.ExerciseViewHolder>() {

    inner class ExerciseViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvDescription: TextView = view.findViewById(R.id.tvDescription)
        val tvBodyLevel: TextView = view.findViewById(R.id.tvBodyLevel)
        val tvUserLevel: TextView = view.findViewById(R.id.tvUserLevel)
        val tvWorkoutType: TextView = view.findViewById(R.id.tvWorkoutType)
        val imgThumbnail: ImageView = view.findViewById(R.id.imgThumbnail)
        val btnPlay: ImageView = view.findViewById(R.id.imgPlayIcon)
        val btnAdd: Button = view.findViewById(R.id.btnAdd)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExerciseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_exercise, parent, false)
        return ExerciseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExerciseViewHolder, position: Int) {
        val exercise = items[position]
        holder.tvTitle.text = exercise.title
        holder.tvDescription.text = exercise.description
        holder.tvBodyLevel.text = "Body Level: ${exercise.bodyLevel}"
        holder.tvUserLevel.text = "User Level: ${exercise.userLevel}"
        holder.tvWorkoutType.text = "Workout Type: ${exercise.workoutType}"

        // Load thumbnail using Glide
        Glide.with(holder.itemView.context)
            .load("https://img.youtube.com/vi/${getVideoId(exercise.videoUrl)}/0.jpg")
            .apply(RequestOptions()
                .placeholder(R.drawable.strength_thumb)
                .error(R.drawable.strength_thumb))
            .into(holder.imgThumbnail)

        // Play video when clicked
        holder.btnPlay.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(exercise.videoUrl))
                holder.itemView.context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Add to cart functionality
        holder.btnAdd.setOnClickListener {
            // Implement your add to cart logic here
        }
    }

    override fun getItemCount(): Int = items.size

    private fun getVideoId(url: String): String {
        if (url.isEmpty()) return ""
        val regex = "(?:https?://)?(?:www\\.)?(?:youtube\\.com/watch\\?v=|youtu\\.be/)([a-zA-Z0-9_-]+)".toRegex()
        return regex.find(url)?.groupValues?.getOrNull(1) ?: ""
    }
}