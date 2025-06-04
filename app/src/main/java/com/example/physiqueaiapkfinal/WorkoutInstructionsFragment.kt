package com.example.physiqueaiapkfinal

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.firestore.FirebaseFirestore
import android.util.Log

class WorkoutInstructionsFragment : Fragment() {

    private var workoutId: String? = null // Changed to workoutId for clarity
    private val db = FirebaseFirestore.getInstance()
    private val TAG = "WorkoutInstructionsFragment"

    // View references
    private var instructionsTextView: TextView? = null
    private var repsTimerTextView: TextView? = null
    private var safetyWarningTextView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            workoutId = it.getString(ARG_WORKOUT_ID) // Changed to ARG_WORKOUT_ID
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_workout_instructions, container, false)

        // Initialize views
        instructionsTextView = view.findViewById(R.id.workoutInstructionsTextView)
        repsTimerTextView = view.findViewById(R.id.workoutInstructionsRepsTimerTextView)
        safetyWarningTextView = view.findViewById(R.id.workoutInstructionsSafetyWarningTextView)

        loadWorkoutData()
        return view
    }

    private fun loadWorkoutData() {
        val id = workoutId
        if (id.isNullOrBlank()) {
            Log.w(TAG, "No workout ID provided.")
            showError("No workout ID provided.")
            return
        }

        // Show loading state if needed
        instructionsTextView?.text = "Loading..."

        db.collection("workoutcollection")
            .document(id) // Fetching by document ID
            .get()
            .addOnSuccessListener { document ->
                // Check if fragment is still attached to avoid crashes
                if (!isAdded) return@addOnSuccessListener

                if (document.exists()) {
                    val workout = document.toObject(Workout::class.java)

                    if (workout != null) {
                        displayWorkoutData(workout)
                        Log.d(TAG, "Workout instructions loaded for ID: $id")
                    } else {
                        Log.e(TAG, "Error parsing workout data for ID: $id")
                        showError("Error parsing workout data.")
                    }
                } else {
                    Log.w(TAG, "Workout not found in Firestore: $id")
                    showError("Workout not found.")
                }
            }
            .addOnFailureListener { exception ->
                // Check if fragment is still attached
                if (!isAdded) return@addOnFailureListener

                Log.e(TAG, "Error fetching workout data for ID: $id", exception)
                showError("Error fetching data: ${exception.message}")
            }
    }

    private fun displayWorkoutData(workout: Workout) {
        // Display instructions
        instructionsTextView?.text = workout.instructions?.takeIf { it.isNotBlank() }
            ?: "No instructions available."

        // Display reps or timer
        val repsTimerText = when {
            !workout.reps.isNullOrBlank() -> "Reps: ${workout.reps}"
            workout.timer != null && workout.timer > 0 -> "Timer: ${workout.timer} seconds"
            else -> "Reps/Timer: N/A"
        }
        repsTimerTextView?.text = repsTimerText

        // Display safety warning
        val safetyText = workout.safety_warning?.takeIf { it.isNotBlank() }
            ?.let { "Safety Warning: $it" }
            ?: "Safety Warning: None"
        safetyWarningTextView?.text = safetyText
    }

    private fun showError(message: String) {
        instructionsTextView?.text = message
        repsTimerTextView?.visibility = View.GONE
        safetyWarningTextView?.visibility = View.GONE

        // Only show toast if fragment is added to avoid crashes
        if (isAdded) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clear view references to prevent memory leaks
        instructionsTextView = null
        repsTimerTextView = null
        safetyWarningTextView = null
    }

    companion object {
        private const val ARG_WORKOUT_ID = "workout_id" // Changed to ARG_WORKOUT_ID

        @JvmStatic
        fun newInstance(workoutId: String) =
            WorkoutInstructionsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_WORKOUT_ID, workoutId) // Changed to workoutId
                }
            }
    }
}
