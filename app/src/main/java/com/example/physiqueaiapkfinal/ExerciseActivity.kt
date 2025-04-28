package com.example.physiqueaiapkfinal

import android.content.Intent
import android.net.Uri
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

        recyclerExercise = findViewById(R.id.recyclerExercise)
        tvCartStatus = findViewById(R.id.tvCartStatus)
        searchView = findViewById(R.id.searchView)
        chipGroupWorkout = findViewById(R.id.chipGroupWorkout)

        setupSampleExercises()
        setupRecyclerView()
        setupChipGroup()
        setupSearchView()
        setupAddExerciseButton()
        updateCartStatus()
    }

    private fun setupSampleExercises() {
        val sampleExercises = listOf(
            ExerciseItem("Full Body Workout", "A complete body workout.", "Intermediate", "Intermediate", "Strength", "https://youtu.be/l9_SoClAO5g"),
            ExerciseItem("Cardio Blast", "High-intensity cardio workout.", "Intermediate", "Beginner", "Cardio", "https://youtu.be/KIl70ffF5FM"),
            ExerciseItem("Yoga for Beginners", "Yoga to increase flexibility and balance.", "Beginner", "Beginner", "Yoga", "https://youtu.be/0yYiErHenzs"),
            ExerciseItem("Strength Training", "Build muscle with these exercises.", "Advanced", "Intermediate", "Strength", "https://youtu.be/example1"),
            ExerciseItem("Morning Cardio", "Start your day with energy.", "Beginner", "Beginner", "Cardio", "https://youtu.be/example2")
        )
        exerciseList.addAll(sampleExercises)
        filteredList.addAll(sampleExercises)
    }

    private fun setupRecyclerView() {
        exerciseAdapter = ExerciseAdapter(filteredList)
        recyclerExercise.apply {
            layoutManager = LinearLayoutManager(this@ExerciseActivity)
            adapter = exerciseAdapter
            itemAnimator = DefaultItemAnimator()
        }
    }

    private fun setupChipGroup() {
        chipGroupWorkout.setOnCheckedStateChangeListener { group, checkedIds ->
            val selectedWorkoutType = if (checkedIds.isEmpty()) {
                "All"
            } else {
                val chipId = checkedIds.first()
                group.findViewById<Chip>(chipId)?.text?.toString() ?: "All"
            }
            applyFilters(searchView.query.toString(), selectedWorkoutType)
        }
    }

    private fun setupSearchView() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                applyFilters(newText.orEmpty(), getSelectedWorkoutType())
                return true
            }
        })
    }

    private fun setupAddExerciseButton() {
        findViewById<Button>(R.id.btnAddExercise).setOnClickListener {
            val newExercise = ExerciseItem(
                "5 Min Full Body Cool Down Stretches",
                "Do this 5-minute cool down routine after your workouts.",
                "Beginner", "Beginner", "Strength", "https://youtu.be/Qy3U09CnELI"
            )
            exerciseList.add(newExercise)
            applyFilters(searchView.query.toString(), getSelectedWorkoutType())
            updateCartStatus()
        }
    }

    private fun applyFilters(query: String, workoutType: String) {
        filteredList.apply {
            clear()
            addAll(exerciseList.filter { exercise ->
                val matchesType = workoutType == "All" || exercise.workoutType.equals(workoutType, ignoreCase = true)
                val matchesQuery = query.isBlank() || listOf(
                    exercise.title,
                    exercise.description,
                    exercise.workoutType
                ).any { it.contains(query, ignoreCase = true) }
                matchesType && matchesQuery
            })
        }
        exerciseAdapter.notifyDataSetChanged()
        updateCartStatus()
    }

    private fun getSelectedWorkoutType(): String {
        return chipGroupWorkout.checkedChipIds.firstOrNull()?.let {
            chipGroupWorkout.findViewById<Chip>(it)?.text.toString()
        } ?: "All"
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
        private val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        private val tvDescription: TextView = view.findViewById(R.id.tvDescription)
        private val tvBodyLevel: TextView = view.findViewById(R.id.tvBodyLevel)
        private val tvUserLevel: TextView = view.findViewById(R.id.tvUserLevel)
        private val tvWorkoutType: TextView = view.findViewById(R.id.tvWorkoutType)
        private val imgThumbnail: ImageView = view.findViewById(R.id.imgThumbnail)
        private val btnPlay: ImageView = view.findViewById(R.id.imgPlayIcon)
        private val btnAdd: Button = view.findViewById(R.id.btnAdd)

        fun bind(exercise: ExerciseItem) {
            with(exercise) {
                tvTitle.text = title
                tvDescription.text = description
                tvBodyLevel.text = "Body Level: $bodyLevel"
                tvUserLevel.text = "User Level: $userLevel"
                tvWorkoutType.text = "Workout Type: $workoutType"

                Glide.with(itemView.context)
                    .load("https://img.youtube.com/vi/${getVideoId(videoUrl)}/0.jpg")
                    .apply(RequestOptions()
                        .placeholder(R.drawable.strength_thumb)
                        .error(R.drawable.strength_thumb))
                    .into(imgThumbnail)

                btnPlay.setOnClickListener {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
                        itemView.context.startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                btnAdd.setOnClickListener {
                    // Add to cart logic placeholder
                    Toast.makeText(itemView.context, "Added: $title", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExerciseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_exercise, parent, false)
        return ExerciseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExerciseViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    private fun getVideoId(url: String): String {
        val regex = "(?:https?://)?(?:www\\.)?(?:youtube\\.com/watch\\?v=|youtu\\.be/)([\\w-]+)".toRegex()
        return regex.find(url)?.groupValues?.get(1) ?: ""
    }
}
