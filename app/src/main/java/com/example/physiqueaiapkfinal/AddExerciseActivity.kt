package com.example.physiqueaiapkfinal

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.FirebaseDatabase


class AddExerciseActivity : AppCompatActivity() {

    private lateinit var exerciseName: EditText
    private lateinit var exerciseDetails: EditText
    private lateinit var exerciseTime: EditText
    private lateinit var exerciseLevelSpinner: Spinner
    private lateinit var saveButton: Button
    private lateinit var selectVideoButton: Button
    private lateinit var videoStatus: TextView
    private lateinit var videoPreview: VideoView
    private lateinit var progressBar: ProgressBar

    private var videoUri: Uri? = null

    private val database = FirebaseDatabase.getInstance().getReference("exerciseinfos")
    private val storage = FirebaseStorage.getInstance()

    companion object {
        const val VIDEO_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_exercise)

        // UI Binding
        exerciseName = findViewById(R.id.exerciseName)
        exerciseDetails = findViewById(R.id.exerciseDetails)
        exerciseTime = findViewById(R.id.exerciseTime)
        exerciseLevelSpinner = findViewById(R.id.exerciseLevelSpinner)
        saveButton = findViewById(R.id.saveExerciseButton)
        selectVideoButton = findViewById(R.id.selectVideoButton)
        videoStatus = findViewById(R.id.videoStatus)
        videoPreview = findViewById(R.id.videoPreview)
        progressBar = findViewById(R.id.progressBar)

        // Setup Spinner
        val levels = arrayOf("Beginner", "Intermediate", "Advanced")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, levels)
        exerciseLevelSpinner.adapter = adapter

        // Select video
        selectVideoButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "video/*"
            startActivityForResult(intent, VIDEO_REQUEST_CODE)
        }

        // Save exercise
        saveButton.setOnClickListener {
            if (videoUri != null) {
                uploadVideoAndSaveData()
            } else {
                Toast.makeText(this, "Please select a video", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VIDEO_REQUEST_CODE && resultCode == Activity.RESULT_OK && data?.data != null) {
            videoUri = data.data
            videoStatus.text = "Video selected!"
            videoPreview.setVideoURI(videoUri)
            videoPreview.visibility = View.VISIBLE
            videoPreview.setOnPreparedListener { it.isLooping = true }
            videoPreview.start()
        }
    }

    private fun uploadVideoAndSaveData() {
        val name = exerciseName.text.toString().trim()
        val details = exerciseDetails.text.toString().trim()
        val time = exerciseTime.text.toString().trim()
        val level = exerciseLevelSpinner.selectedItem.toString()

        if (name.isEmpty() || details.isEmpty() || time.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        val exerciseId = database.push().key ?: return
        val videoRef = storage.reference.child("exerciseVideos/$exerciseId.mp4")

        videoRef.putFile(videoUri!!)
            .addOnSuccessListener {
                videoRef.downloadUrl.addOnSuccessListener { uri ->
                    val videoUrl = uri.toString()
                    val exercise = Workout(
                        exerciseId = exerciseId,
                        name = name,
                        details = details,
                        time = time,
                        level = level,
                        videoUrl = videoUrl
                    )

                    database.child(exerciseId).setValue(exercise)
                        .addOnSuccessListener {
                            progressBar.visibility = View.GONE
                            Toast.makeText(this, "Exercise saved successfully!", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        .addOnFailureListener {
                            progressBar.visibility = View.GONE
                            Toast.makeText(this, "Failed to save exercise", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Video upload failed", Toast.LENGTH_SHORT).show()
            }
    }
}
