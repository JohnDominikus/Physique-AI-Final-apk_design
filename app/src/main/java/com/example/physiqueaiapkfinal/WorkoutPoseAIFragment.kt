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
                navigateToCorrectPoseActivity()
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
    
    private fun navigateToCorrectPoseActivity() {
        val workoutId = this.workoutId
        if (workoutId == null) {
            Toast.makeText(requireContext(), "Workout ID not available", Toast.LENGTH_SHORT).show()
            return
        }
        
        // First, get the workout details to determine which AI activity to launch
        db.collection("workoutcollection").document(workoutId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val workoutName = document.getString("name")?.lowercase() ?: ""
                    Log.d(TAG, "Workout name: $workoutName")
                    
                    val targetActivity = when {
                        workoutName.contains("hip thrust", ignoreCase = true) || 
                        workoutName.contains("hip-thrust", ignoreCase = true) ||
                        workoutName.contains("hipthrust", ignoreCase = true) -> {
                            Log.d(TAG, "Navigating to HipThrustsActivity")
                            HipThrustsActivity::class.java
                        }
                        workoutName.contains("squat", ignoreCase = true) -> {
                            Log.d(TAG, "Navigating to SquatActivity")
                            SquatActivity::class.java
                        }
                        workoutName.contains("dumbbell", ignoreCase = true) && 
                        workoutName.contains("front", ignoreCase = true) && 
                        workoutName.contains("raise", ignoreCase = true) -> {
                            Log.d(TAG, "Navigating to DumbbellFrontRaiseActivity")
                            DumbbellFrontRaiseActivity::class.java
                        }
                        workoutName.contains("hammer curl", true)   ||   // may espasyo
                        workoutName.contains("hammer-curl", true)   ||   // may gitling
                        workoutName.contains("hammercurl",  true)   ||   // walang espasyo
                        (workoutName.contains("hammer", true) && workoutName.contains("curl", true)) -> {
                            Log.d(TAG, "Navigating to DumbbellHammerCurlActivity")
                            DumbbellHammerCurlActivity::class.java
                        }
                        workoutName.contains("military press", true) ||          // may espasyo
                        workoutName.contains("military-press", true) ||          // may gitling
                        workoutName.contains("militarypress", true)  ||          // walang espasyo
                        (workoutName.contains("military", true) &&               // hiwalay pero pareho
                         workoutName.contains("press",    true))      ||
                        workoutName.contains("shoulder press", true) -> {
                            Log.d(TAG, "Navigating to MilitaryPressActivity")
                            MilitaryPressActivity::class.java
                        }
                        workoutName.contains("sit up",  true)  ||   // may espasyo
                        workoutName.contains("sit-up",  true)  ||   // may gitling
                        workoutName.contains("situps",  true)  ||   // may s
                        workoutName.contains("situp",   true)  ||   // walang espasyo
                        (workoutName.contains("sit", true) && workoutName.contains("up", true)) ||
                        workoutName.contains("crunch",  true)  -> {
                            Log.d(TAG, "Navigating to SitUpsActivity")
                            SitUpsActivity::class.java
                        }
                        workoutName.contains("windmill",  true)  ||   // normal
                        workoutName.contains("wind-mill", true)  ||   // may gitling
                        workoutName.contains("wind mill", true)  -> { // may espasyo
                            Log.d(TAG, "Navigating to WindmillActivity")
                            WindmillActivity::class.java
                        }
                        workoutName.contains("push", ignoreCase = true) && 
                        workoutName.contains("up", ignoreCase = true) -> {
                            Log.d(TAG, "Navigating to StreamActivity (Push-ups)")
                            StreamActivity::class.java
                        }
                        else -> {
                            Log.d(TAG, "No specific AI activity found, using generic PoseActivity")
                            PoseActivity::class.java
                        }
                    }
                    
                    try {
                        val intent = Intent(requireContext(), targetActivity).apply {
                            putExtra("workoutId", workoutId)
                            putExtra("workout_name", workoutName)
                        }
                        
                        // Use modern activity transition
                        val options = ActivityOptionsCompat.makeCustomAnimation(
                            requireContext(),
                            R.anim.slide_in_right,
                            R.anim.slide_out_left
                        )
                        
                        startActivity(intent, options.toBundle())
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Error navigating to specific pose activity", e)
                        // Fallback to generic PoseActivity
                        fallbackToPoseActivity()
                    }
                } else {
                    Log.e(TAG, "Workout document not found")
                    Toast.makeText(requireContext(), "Workout not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error fetching workout details", e)
                Toast.makeText(requireContext(), "Error loading workout", Toast.LENGTH_SHORT).show()
                // Fallback to generic PoseActivity
                fallbackToPoseActivity()
            }
    }
    
    private fun fallbackToPoseActivity() {
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
            Log.e("WorkoutPoseAI", "Error navigating to fallback pose activity", e)
            // Last resort - simple navigation
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