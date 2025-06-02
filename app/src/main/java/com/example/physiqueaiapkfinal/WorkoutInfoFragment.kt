package com.example.physiqueaiapkfinal

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.firestore.FirebaseFirestore

class WorkoutInfoFragment : Fragment() {

    private var workoutId: String? = null
    private val db = FirebaseFirestore.getInstance()

    // Use lateinit for views that will be initialized in onCreateView
    private lateinit var difficultyTextView: TextView
    private lateinit var targetTextView: TextView
    private lateinit var muscleGroupsTextView: TextView
    private lateinit var equipmentTextView: TextView
    private lateinit var descriptionTextView: TextView

    companion object {
        private const val ARG_WORKOUT_ID = "workout_id"

        @JvmStatic
        fun newInstance(workoutId: String) =
            WorkoutInfoFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_WORKOUT_ID, workoutId)
                }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        workoutId = arguments?.getString(ARG_WORKOUT_ID)
        Log.d("WorkoutInfoFragment", "Received workout ID: $workoutId")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_workout_info, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeViews(view)
        loadWorkoutData()
    }

    private fun initializeViews(view: View) {
        descriptionTextView = view.findViewById(R.id.workoutInfoDescription)
        difficultyTextView = view.findViewById(R.id.workoutInfoDifficulty)
        equipmentTextView = view.findViewById(R.id.workoutInfoEquipment)
        muscleGroupsTextView = view.findViewById(R.id.workoutInfoMuscleGroups)
        targetTextView = view.findViewById(R.id.workoutInfoTarget)
    }

    private fun loadWorkoutData() {
        val id = workoutId ?: run {
            showToast("No workout ID provided.")
            return
        }

        Log.d("WorkoutInfoFragment", "Loading workout data for ID: $id")

        db.collection("workoutcollection").document(id)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    try {
                        val workout = document.toObject(Workout::class.java)
                        if (workout != null) {
                            populateWorkoutInfo(workout)
                        } else {
                            Log.e("WorkoutInfoFragment", "Workout object is null")
                            showToast("Error parsing workout data.")
                        }
                    } catch (e: Exception) {
                        Log.e("WorkoutInfoFragment", "Error converting document: ${e.message}")
                        showToast("Error processing workout data.")
                    }
                } else {
                    Log.e("WorkoutInfoFragment", "Document does not exist")
                    showToast("Workout not found.")
                }
            }
            .addOnFailureListener { exception ->
                Log.e("WorkoutInfoFragment", "Error fetching document: ${exception.message}")
                showToast("Error fetching data: ${exception.message}")
            }
    }

    private fun populateWorkoutInfo(workout: Workout) {
        try {
            descriptionTextView.text = workout.description ?: "No description available"
            difficultyTextView.text = getString(R.string.difficulty_format, workout.difficulty ?: "N/A")
            equipmentTextView.text = getString(R.string.equipment_format, workout.equipment ?: "N/A")

            val muscleGroupsText = when {
                workout.muscle_groups is List<*> -> (workout.muscle_groups as List<*>)
                    .filterIsInstance<String>()
                    .joinToString(", ")
                workout.muscle_groups is String -> workout.muscle_groups as String
                else -> "N/A"
            }
            muscleGroupsTextView.text = getString(R.string.muscle_groups_format, muscleGroupsText)

            targetTextView.text = getString(R.string.target_format, workout.target ?: "N/A")

            Log.d("WorkoutInfoFragment", "Successfully populated workout info")
        } catch (e: Exception) {
            Log.e("WorkoutInfoFragment", "Error populating workout info: ${e.message}")
            showToast("Error displaying workout data")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}