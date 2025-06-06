package com.example.physiqueaiapkfinal

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment

class WorkoutPoseAIFragment : Fragment() {

    private var workoutId: String? = null

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
            // Navigate to PoseActivity (which uses activity_poseai layout)
            val intent = Intent(requireActivity(), PoseActivity::class.java).apply {
                // Pass the workout ID if available
                workoutId?.let { id -> putExtra(ARG_WORKOUT_ID, id) }

                // Add any additional flags or data if needed
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)

            // Optional: Add fragment transition animation
            requireActivity().overridePendingTransition(
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )
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