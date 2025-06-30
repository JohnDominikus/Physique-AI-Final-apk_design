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
import androidx.core.app.ActivityOptionsCompat
import com.google.firebase.firestore.FirebaseFirestore

class WorkoutPoseAIFragment : Fragment() {

    private var workoutId: String? = null
    private val db = FirebaseFirestore.getInstance()
    private val TAG = "WorkoutPoseAIFragment"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_workout_pose_ai, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Get workout ID from arguments
        arguments?.let { args ->
            workoutId = args.getString("workoutId")
        }
        
        if (workoutId.isNullOrEmpty()) {
            Log.e("WorkoutPoseAI", "No workout ID provided")
            return
        }
        
        setupUI()
    }
    
    private fun setupUI() {
        try {
            // Setup pose analysis button
            view?.findViewById<Button>(R.id.startPostureButton)?.setOnClickListener {
                navigateToPoseActivity()
            }
            
            // Setup other UI elements if they exist
            // view?.findViewById<Button>(R.id.btnInstructions)?.setOnClickListener {
            //     // Navigate to instructions
            // }
            
            // view?.findViewById<Button>(R.id.btnStartWorkout)?.setOnClickListener {
            //     // Start workout
            // }
            
            // view?.findViewById<Button>(R.id.btnPauseWorkout)?.setOnClickListener {
            //     // Pause workout
            // }
            
            // view?.findViewById<Button>(R.id.btnStopWorkout)?.setOnClickListener {
            //     // Stop workout
            // }
            
        } catch (e: Exception) {
            Log.e("WorkoutPoseAI", "Error setting up UI", e)
        }
    }
    
    private fun navigateToPoseActivity() {
        try {
            val intent = Intent(requireContext(), PoseActivity::class.java)
            
            // Use modern activity transition
            val options = ActivityOptionsCompat.makeCustomAnimation(
                requireContext(),
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
            
            startActivity(intent, options.toBundle())
        } catch (e: Exception) {
            Log.e("WorkoutPoseAI", "Error navigating to pose activity", e)
            // Fallback to simple navigation
            startActivity(Intent(requireContext(), PoseActivity::class.java))
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