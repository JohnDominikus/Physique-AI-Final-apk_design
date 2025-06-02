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

    private var workoutName: String? = null
    private val db = FirebaseFirestore.getInstance()

    private val TAG = "WorkoutInstructionsFragment"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            workoutName = it.getString(ARG_WORKOUT_NAME)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_workout_instructions, container, false)

        val instructionsTextView = view.findViewById<TextView>(R.id.workoutInstructionsTextView)
        val repsTimerTextView = view.findViewById<TextView>(R.id.workoutInstructionsRepsTimerTextView)
        val safetyWarningTextView = view.findViewById<TextView>(R.id.workoutInstructionsSafetyWarningTextView)

        workoutName?.let { name ->
            db.collection("workoutcollection")
                .whereEqualTo("name", name) // Make sure 'name' is unique or use document ID instead
                .get()
                .addOnSuccessListener { querySnapshot ->
                    if (!querySnapshot.isEmpty) {
                        val document = querySnapshot.documents[0]
                        val workout = document.toObject(Workout::class.java)
                        if (workout != null) {
                            instructionsTextView.text = workout.instructions ?: "No instructions available."

                            val repsTimerText = when {
                                !workout.reps.isNullOrBlank() -> "Reps: ${workout.reps}"
                                workout.timer != null && workout.timer > 0 -> "Timer: ${workout.timer} seconds"
                                else -> "Reps/Timer: N/A"
                            }
                            repsTimerTextView.text = repsTimerText

                            safetyWarningTextView.text = "Safety Warning: ${workout.safety_warning ?: "None"}"
                            Log.d(TAG, "Workout instructions loaded for: $name")
                        } else {
                            Log.e(TAG, "Error parsing workout data for: $name")
                            Toast.makeText(context, "Error parsing workout data.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Log.w(TAG, "Workout not found in Firestore: $name")
                        Toast.makeText(context, "Workout not found.", Toast.LENGTH_SHORT).show()
                        instructionsTextView.text = "Workout details not found."
                        repsTimerTextView.visibility = View.GONE
                        safetyWarningTextView.visibility = View.GONE
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Error fetching workout data for: $name", exception)
                    Toast.makeText(context, "Error fetching data: ${exception.message}", Toast.LENGTH_SHORT).show()
                    instructionsTextView.text = "Error loading workout details."
                    repsTimerTextView.visibility = View.GONE
                    safetyWarningTextView.visibility = View.GONE
                }
        } ?: run {
            Log.w(TAG, "No workout name provided.")
            instructionsTextView.text = "No workout name provided."
            repsTimerTextView.visibility = View.GONE
            safetyWarningTextView.visibility = View.GONE
        }

        return view
    }

    companion object {
        private const val ARG_WORKOUT_NAME = "workout_name"

        @JvmStatic
        fun newInstance(workoutName: String) =
            WorkoutInstructionsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_WORKOUT_NAME, workoutName)
                }
            }
    }
}
