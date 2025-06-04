package com.example.physiqueaiapkfinal

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject

class WorkoutInfoFragment : Fragment() {

    private var workoutId: String? = null
    private val db by lazy { FirebaseFirestore.getInstance() }

    // Views
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var nameTextView: TextView
    private lateinit var difficultyTextView: TextView
    private lateinit var targetTextView: TextView
    private lateinit var muscleGroupsTextView: TextView
    private lateinit var equipmentTextView: TextView
    private lateinit var descriptionTextView: TextView
    private lateinit var repsTextView: TextView
    private lateinit var timerTextView: TextView
    private lateinit var instructionsTextView: TextView
    private lateinit var safetyWarningTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Restore workoutId from savedInstanceState if available, else from arguments
        workoutId = savedInstanceState?.getString(ARG_WORKOUT_ID) ?: arguments?.getString(ARG_WORKOUT_ID)?.takeIf { it.isNotBlank() }
        Log.d(TAG, "onCreate: Initialized with workout ID: $workoutId")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d(TAG, "onCreateView called")
        return inflater.inflate(R.layout.fragment_workout_info, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated called")
        initViews(view)
        if (workoutId != null) {
            loadWorkoutData(workoutId!!)
        } else {
            showError("Invalid workout ID")
            // Do not force back navigation here; just show error to user
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        workoutId?.let {
            outState.putString(ARG_WORKOUT_ID, it)
            Log.d(TAG, "onSaveInstanceState: saved workoutId: $it")
        }
    }

    private fun initViews(view: View) {
        with(view) {
            loadingProgressBar = findViewById(R.id.loadingProgressBar)
            nameTextView = findViewById(R.id.workoutInfoName)
            difficultyTextView = findViewById(R.id.workoutInfoDifficulty)
            targetTextView = findViewById(R.id.workoutInfoTarget)
            muscleGroupsTextView = findViewById(R.id.workoutInfoMuscleGroups)
            equipmentTextView = findViewById(R.id.workoutInfoEquipment)
            descriptionTextView = findViewById(R.id.workoutInfoDescription)
            repsTextView = findViewById(R.id.workoutInfoReps)
            timerTextView = findViewById(R.id.workoutInfoTimer)
            instructionsTextView = findViewById(R.id.workoutInfoInstructions)
            safetyWarningTextView = findViewById(R.id.workoutInfoSafetyWarning)
        }
    }

    private fun loadWorkoutData(workoutId: String) {
        showLoading(true)
        Log.d(TAG, "Fetching workout data for ID: $workoutId")

        db.collection("workoutcollection").document(workoutId)
            .get()
            .addOnSuccessListener { document ->
                if (!isAdded) {
                    Log.w(TAG, "Fragment not added to activity, skipping UI update")
                    return@addOnSuccessListener
                }

                showLoading(false)

                if (!document.exists()) {
                    showError("Workout not found for ID: $workoutId")
                    return@addOnSuccessListener
                }

                try {
                    Log.d(TAG, "Document data: ${document.data}")
                    val workout = document.toObject<Workout>()?.apply {
                        id = document.id
                    }

                    if (workout != null) {
                        displayWorkoutData(workout)
                    } else {
                        showError("Invalid workout format")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing workout", e)
                    showError("Error loading workout data")
                }
            }
            .addOnFailureListener { e ->
                if (!isAdded) {
                    Log.w(TAG, "Fragment not added to activity, skipping UI update")
                    return@addOnFailureListener
                }
                showLoading(false)
                Log.e(TAG, "Firestore error", e)
                showError("Failed to load: ${e.localizedMessage}")
            }
    }

    private fun displayWorkoutData(workout: Workout) {
        fun String?.orDefault(default: String) = this?.takeIf { it.isNotBlank() } ?: default
        fun Int?.orDefault(default: String) = this?.toString() ?: default

        nameTextView.text = workout.name.orDefault("Unnamed Workout")
        difficultyTextView.text = "Difficulty: ${workout.difficulty.orDefault("Not specified")}"
        targetTextView.text = "Target: ${workout.target.orDefault("Not specified")}"
        muscleGroupsTextView.text = "Muscles: ${workout.muscle_groups.orDefault("Not specified")}"
        equipmentTextView.text = "Equipment: ${workout.equipment.orDefault("None required")}"
        descriptionTextView.text = workout.description.orDefault("No description available")
        repsTextView.text = "Reps: ${workout.reps.orDefault("Not specified")}"
        timerTextView.text = "Duration: ${workout.timer.orDefault("0")} seconds"
        instructionsTextView.text = workout.instructions.orDefault("No instructions provided")

        workout.safety_warning?.takeIf { it.isNotBlank() }?.let {
            safetyWarningTextView.text = "⚠️ $it"
            safetyWarningTextView.visibility = View.VISIBLE
        } ?: run {
            safetyWarningTextView.visibility = View.GONE
        }
    }

    private fun showLoading(show: Boolean) {
        loadingProgressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        Log.e(TAG, message)
        // Do not perform forced back navigation here; just notify user
    }

    companion object {
        private const val TAG = "WorkoutInfoFragment"
        private const val ARG_WORKOUT_ID = "workout_id"

        fun newInstance(workoutId: String) = WorkoutInfoFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_WORKOUT_ID, workoutId)
            }
        }
    }
}
