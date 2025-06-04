package com.example.physiqueaiapkfinal

import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class WorkoutDetailActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var likeButton: ImageButton
    private lateinit var backButton: ImageButton
    private lateinit var workoutDetailImage: ImageView
    private lateinit var workoutDetailName: TextView
    private lateinit var workoutDetailTarget: TextView
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workout_detail)

        val workoutId = intent.getStringExtra("workoutId") ?: run {
            Toast.makeText(this, "Workout ID not provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        likeButton = findViewById(R.id.likeButton)
        backButton = findViewById(R.id.backButton)
        workoutDetailImage = findViewById(R.id.workoutDetailGif)
        workoutDetailName = findViewById(R.id.workoutDetailName)
        workoutDetailTarget = findViewById(R.id.workoutDetailTarget)
        tabLayout = findViewById(R.id.workoutDetailTabLayout)
        viewPager = findViewById(R.id.workoutDetailViewPager)

        setupBackButton()
        fetchWorkoutDetails(workoutId)
        setupTabs(workoutId)
        setupLikeButton(workoutId)
    }

    private fun setupBackButton() {
        backButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun fetchWorkoutDetails(workoutId: String) {
        db.collection("workoutcollection")
            .document(workoutId)
            .get()
            .addOnSuccessListener { doc ->
                val workout = doc?.toObject(Workout::class.java)
                if (workout != null) {
                    workoutDetailName.text = workout.name ?: "Unknown Workout"
                    workoutDetailTarget.text = workout.target ?: "Target Not Specified"

                    Glide.with(this)
                        .load(workout.thumbmail) // Corrected to thumbmail
                        .placeholder(android.R.drawable.sym_def_app_icon)
                        .error(android.R.drawable.ic_dialog_alert)
                        .into(workoutDetailImage)

                    (viewPager.adapter as? WorkoutDetailPagerAdapter)?.setWorkout(workout)
                } else {
                    Toast.makeText(this, "Workout not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading workout: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun setupLikeButton(workoutId: String) {
        val userId = auth.currentUser ?.uid ?: return

        db.collection("users").document(userId).collection("likedWorkouts").document(workoutId).get()
            .addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    likeButton.setImageResource(R.drawable.fav_filled)
                } else {
                    likeButton.setImageResource(R.drawable.fav)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error checking liked status: ${e.message}", Toast.LENGTH_SHORT).show()
            }

        likeButton.setOnClickListener {
            val userLikedWorkoutsRef = db.collection("users").document(userId).collection("likedWorkouts")

            userLikedWorkoutsRef.document(workoutId).get()
                .addOnSuccessListener { documentSnapshot ->
                    if (documentSnapshot.exists()) {
                        userLikedWorkoutsRef.document(workoutId).delete()
                            .addOnSuccessListener {
                                likeButton.setImageResource(R.drawable.fav)
                                Toast.makeText(this, "Workout unliked", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Error unliking workout: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        userLikedWorkoutsRef.document(workoutId).set(mapOf("workoutId" to workoutId))
                            .addOnSuccessListener {
                                likeButton.setImageResource(R.drawable.fav_filled)
                                Toast.makeText(this, "Workout liked", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Error liking workout: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error checking liked status: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun setupTabs(workoutId: String) {
        val tabTitles = listOf("INFO", "INSTRUCTION", "POSE AI")
        viewPager.adapter = WorkoutDetailPagerAdapter(this, workoutId, tabTitles.size)

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()
    }

    private inner class WorkoutDetailPagerAdapter(
        fragmentActivity: FragmentActivity,
        private val workoutId: String,
        private val itemCount: Int
    ) : FragmentStateAdapter(fragmentActivity) {

        private var workout: Workout? = null

        fun setWorkout(workout: Workout) {
            this.workout = workout
        }

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> WorkoutInfoFragment.newInstance(workoutId)
                1 -> WorkoutInstructionsFragment.newInstance(workoutId)
                2 -> WorkoutPoseAIFragment.newInstance(workoutId)
                else -> throw IllegalStateException("Invalid tab position")
            }
        }

        override fun getItemCount(): Int = itemCount
    }
}
