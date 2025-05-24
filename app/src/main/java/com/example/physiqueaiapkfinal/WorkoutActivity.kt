package com.example.physiqueaiapkfinal

import Exercise
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class WorkoutActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var searchEditText: EditText
    private lateinit var btnAll: Button
    private lateinit var btnStrength: Button
    private lateinit var btnCardio: Button
    private lateinit var btnOthers: Button

    private val exerciseList = mutableListOf<Exercise>()
    private var filteredList = mutableListOf<Exercise>()
    private lateinit var adapter: ExerciseAdapter
    private var firestoreListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        setContentView(R.layout.activity_workoutlist)

        recyclerView = findViewById(R.id.exerciseRecycler)
        searchEditText = findViewById(R.id.searchEditText)
        btnAll = findViewById(R.id.btnAll)
        btnStrength = findViewById(R.id.btnStrength)
        btnCardio = findViewById(R.id.btnCardio)
        btnOthers = findViewById(R.id.btnOthers)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ExerciseAdapter(filteredList) { exercise ->
            val intent = Intent(this, ExerciseDetailActivity::class.java)
            intent.putExtra("exerciseId", exercise.id)
            startActivity(intent)
        }
        recyclerView.adapter = adapter

        setupSearchAndFilter()
        listenForExercisesRealtime()
    }

    private fun setupSearchAndFilter() {
        findViewById<ImageButton>(R.id.searchButton).setOnClickListener {
            filterExercises()
        }
        searchEditText.setOnEditorActionListener { _, _, _ ->
            filterExercises()
            true
        }
        btnAll.setOnClickListener { filterExercises(category = "All") }
        btnStrength.setOnClickListener { filterExercises(category = "Strength") }
        btnCardio.setOnClickListener { filterExercises(category = "Cardio") }
        btnOthers.setOnClickListener { filterExercises(category = "Others") }
    }

    private fun listenForExercisesRealtime() {
        firestoreListener = FirebaseFirestore.getInstance()
            .collection("workoutcollection")
            .addSnapshotListener { result, error ->
                if (error != null) {
                    Toast.makeText(this, "Error loading workouts", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                exerciseList.clear()
                var hadError = false
                if (result != null) {
                    for (doc in result) {
                        try {
                            val ex = doc.toObject(Exercise::class.java)?.copy(id = doc.id)
                            if (ex != null) exerciseList.add(ex)
                        } catch (e: Exception) {
                            hadError = true
                            e.printStackTrace()
                        }
                    }
                }
                filterExercises()
                if (hadError) {
                    Toast.makeText(this, "Some exercises could not be loaded.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun filterExercises(category: String = "All") {
        val query = searchEditText.text.toString().trim().lowercase()
        filteredList.clear()
        filteredList.addAll(exerciseList.filter { ex ->
            val matchesSearch = (ex.name ?: "").lowercase().contains(query)
            val groups = (ex.muscle_groups ?: "").lowercase()
            val matchesCategory = when (category) {
                "All" -> true
                "Strength" -> groups.contains("chest") || groups.contains("arms")
                "Cardio" -> groups.contains("cardio")
                "Others" -> !(groups.contains("chest") || groups.contains("arms") || groups.contains("cardio"))
                else -> true
            }
            matchesSearch && matchesCategory
        })
        adapter.notifyDataSetChanged()
    }

    override fun onDestroy() {
        super.onDestroy()
        firestoreListener?.remove()
    }

    inner class ExerciseAdapter(
        private val exercises: List<Exercise>,
        private val onItemClick: (Exercise) -> Unit
    ) : RecyclerView.Adapter<ExerciseAdapter.ExerciseViewHolder>() {

        inner class ExerciseViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.itemName)
            val reps: TextView = view.findViewById(R.id.itemReps)
            val timer: TextView = view.findViewById(R.id.itemTimer)
            val difficulty: TextView = view.findViewById(R.id.itemDifficulty)
            val thumbnail: ImageView = view.findViewById(R.id.itemThumbnail)
            val startBtn: Button = view.findViewById(R.id.btnAdd)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ExerciseViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_exercise, parent, false)
            return ExerciseViewHolder(view)
        }

        override fun onBindViewHolder(holder: ExerciseViewHolder, position: Int) {
            val ex = exercises[position]
            holder.name.text = ex.name ?: ""
            holder.reps.text = ex.reps ?: ""
            holder.timer.text = "${ex.timer ?: 0}s"
            holder.difficulty.text = ex.difficulty ?: ""
            Glide.with(holder.itemView.context)
                .asGif()
                .load(ex.thumbmail ?: "")
                .into(holder.thumbnail)
            holder.startBtn.setOnClickListener { onItemClick(ex) }
            holder.itemView.setOnClickListener { onItemClick(ex) }
        }

        override fun getItemCount() = exercises.size
    }
}