package com.example.physiqueaiapkfinal

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.firestore.FirebaseFirestore

class WorkoutPoseAIFragment : Fragment() {

    private var workoutId: String? = null
    private val db = FirebaseFirestore.getInstance()
    private val TAG = "WorkoutPoseAIFragment"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        workoutId = arguments?.getString(ARG_WORKOUT_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_workout_pose_ai, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.startPostureButton)?.setOnClickListener {
            startPostureActivityBasedOnWorkout()
        }
    }

    private fun startPostureActivityBasedOnWorkout() {
        val workoutId = this.workoutId
        if (workoutId.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Workout ID hindi makita", Toast.LENGTH_SHORT).show()
            return
        }

        // Fetch workout details from Firestore
        db.collection("workoutcollection")
            .document(workoutId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val workout = document.toObject(Workout::class.java)
                    val workoutName = workout?.name?.lowercase() ?: ""
                    
                    Log.d(TAG, "Workout name: $workoutName")
                    
                    // Navigate to appropriate activity based on workout name
                    val intent = when {
                        workoutName.contains("push-up") || workoutName.contains("pushup") || workoutName.contains("push up") -> {
                            Intent(requireActivity(), StreamActivity::class.java)
                        }
                        workoutName.contains("squat") -> {
                            Intent(requireActivity(), SquatActivity::class.java)
                        }
                        workoutName.contains("hip thrust") || workoutName.contains("hip-thrust") -> {
                            Intent(requireActivity(), HipThrustsActivity::class.java)
                        }
                        workoutName.contains("front raise") || workoutName.contains("front-raise") || workoutName.contains("dumbbell front raise") -> {
                            Intent(requireActivity(), DumbbellFrontRaiseActivity::class.java)
                        }
                        else -> {
                            // Default fallback to general pose activity
                            Log.w(TAG, "Hindi makita ang specific activity para sa workout: $workoutName, ginagamit ang default PoseActivity")
                            Intent(requireActivity(), PoseActivity::class.java)
                        }
                    }

                    // Add workout information to intent
                    intent.apply {
                        putExtra(ARG_WORKOUT_ID, workoutId)
                        putExtra("workout_name", workout?.name)
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    
                    startActivity(intent)
                    
                    // Add activity transition animation
                    requireActivity().overridePendingTransition(
                        R.anim.slide_in_right,
                        R.anim.slide_out_left
                    )
                    
                    Log.d(TAG, "Nagsimula ang activity para sa $workoutName")
                    
                } else {
                    Toast.makeText(requireContext(), "Workout hindi nahanap", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "Workout document hindi nahanap: $workoutId")
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(requireContext(), "Error sa pagload ng workout: ${exception.message}", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "Error sa pagkuha ng workout", exception)
            }
    }

    companion object {
        private const val ARG_WORKOUT_ID = "workoutId"

        fun newInstance(workoutId: String) = WorkoutPoseAIFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_WORKOUT_ID, workoutId)
            }
        }
    }
}