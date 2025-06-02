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
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_workout_pose_ai, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val startButton = view.findViewById<Button>(R.id.startPostureButton)
        startButton.setOnClickListener {
            val intent = Intent(requireContext(), PostureActivity::class.java)
            // Pass workoutId to PostureActivity if available
            intent.putExtra(ARG_WORKOUT_ID, workoutId)
            startActivity(intent)
        }
    }

    companion object {
        private const val ARG_WORKOUT_ID = "workoutId"

        @JvmStatic
        fun newInstance(workoutId: String) = WorkoutPoseAIFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_WORKOUT_ID, workoutId)
            }
        }
    }
}
