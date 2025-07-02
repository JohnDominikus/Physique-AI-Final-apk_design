@file:OptIn(androidx.camera.core.ExperimentalGetImage::class)

package com.example.physiqueaiapkfinal

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.physiqueaiapkfinal.databinding.ActivitySitUpsBinding
import com.example.physiqueaiapkfinal.visionutils.GraphicOverlay
import com.example.physiqueaiapkfinal.visionutils.PoseGraphic
import com.example.physiqueaiapkfinal.visionutils.classification.PoseClassifierProcessor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.ArrayList as JavaArrayList
import kotlin.math.atan2

class SitUpsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySitUpsBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var backgroundExecutor: ExecutorService
    private lateinit var poseDetector: com.google.mlkit.vision.pose.PoseDetector
    private var poseClassifierProcessor: PoseClassifierProcessor? = null
    private val TAG = "SitUpsActivity"
    private var sitUpCount = 0
    private var targetReps: Int = 0
    private val mainHandler = Handler(Looper.getMainLooper())

    // Sit-up detection variables
    private var isDown = false
    private var lastSitUpTime = 0L
    private val MIN_SIT_UP_INTERVAL = 800L // Minimum time between sit-ups

    // Advanced detection variables
    private var downFrameCount = 0
    private var upFrameCount = 0
    private val MIN_STABLE_FRAMES = 4 // Frames for stable detection

    // Multi-dimensional history tracking for sit-up detection
    private val torsoAngleHistory = mutableListOf<Float>()
    private val HISTORY_SIZE = 8 // History size for smoothing

    // Performance optimization variables
    private var frameSkipCounter = 0
    private val FRAME_SKIP_COUNT = 1 // Reduce skip for better accuracy

    // Camera switching variables
    private var isUsingFrontCamera = true
    private var cameraProvider: ProcessCameraProvider? = null

    // Accurate sit-up detection with multiple criteria
    private var hasBeenDown = false // Track if person has been in down position first
    private var lastCountedUp = false // Track if we just counted to prevent double counting

    // Timer functionality
    private var countDownTimer: CountDownTimer? = null
    private var totalTimeInMillis: Long = 0
    private var timeRemaining: Long = 0
    private var totalSets: Int = 0
    private var currentSet: Int = 1
    private var isRestPeriod: Boolean = false
    private val REST_TIME_SECONDS = 20

    companion object {
        private const val TAG = "SitUpsActivity"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val REQUEST_CODE_PERMISSIONS = 10
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySitUpsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Read values from intent and display them
        val sets = intent.getIntExtra("sets", 0)
        val reps = intent.getIntExtra("reps", 0)
        val minutes = intent.getIntExtra("minutes", 0)
        val seconds = intent.getIntExtra("seconds", 0)

        targetReps = reps

        // Store set information
        totalSets = sets
        currentSet = 1

        // Calculate total time in milliseconds
        totalTimeInMillis = ((minutes * 60) + seconds) * 1000L
        timeRemaining = totalTimeInMillis

        // Update the UI with the exercise information
        try {
            updateSetDisplay()
            // Start the timer if time is specified
            if (totalTimeInMillis > 0 && totalSets > 0) {
                startCountdownTimer()
            } else {
                binding.tvTimeLabel.text = "Time: 0m 0s"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not update time/set labels: ${e.message}")
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        // Initialize pose detector with stream mode
        val options = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
        poseDetector = PoseDetection.getClient(options)

        // Initialize executors
        cameraExecutor = Executors.newSingleThreadExecutor()
        backgroundExecutor = Executors.newSingleThreadExecutor()

        // Initialize pose classifier in a background thread
        backgroundExecutor.execute {
            poseClassifierProcessor = PoseClassifierProcessor(this, true)
        }

        // Update UI
        updateSitUpCounter()

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnReset.setOnClickListener {
            resetSitUpCounter()
        }

        binding.btnSwitchCamera.setOnClickListener {
            switchCamera()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = this.cameraProvider ?: return

        val preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

        // Mirror the preview for front camera
        if (isUsingFrontCamera) {
            binding.viewFinder.scaleX = -1f // Horizontal flip
        } else {
            binding.viewFinder.scaleX = 1f // Normal view
        }

        val imageAnalyzer = ImageAnalysis.Builder()
            .setTargetResolution(android.util.Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setImageQueueDepth(1)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, PoseAnalyzer())
            }

        val cameraSelector = if (isUsingFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalyzer
            )

            // Update camera status indicator
            binding.tvCameraStatus.text = if (isUsingFrontCamera) "Camera: Front (Mirrored)" else "Camera: Back"

        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
            Toast.makeText(this, "Failed to start camera", Toast.LENGTH_SHORT).show()
        }
    }

    private fun switchCamera() {
        try {
            isUsingFrontCamera = !isUsingFrontCamera
            Log.d(TAG, "Switching to ${if (isUsingFrontCamera) "front" else "back"} camera")
            bindCameraUseCases()

            // Reset pose detection state when switching cameras
            resetPoseDetectionState()
        } catch (e: Exception) {
            Log.e(TAG, "Camera switch failed: ${e.message}", e)
            Toast.makeText(this, "Camera switch failed", Toast.LENGTH_SHORT).show()
            // Revert camera selection if switching failed
            isUsingFrontCamera = !isUsingFrontCamera
        }
    }

    private fun resetPoseDetectionState() {
        isDown = false
        hasBeenDown = false
        lastCountedUp = false
        downFrameCount = 0
        upFrameCount = 0
        frameSkipCounter = 0
        torsoAngleHistory.clear()
    }

    private inner class PoseAnalyzer : ImageAnalysis.Analyzer {
        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                poseDetector.process(image)
                    .addOnSuccessListener { pose ->
                        processPose(pose, imageProxy.width, imageProxy.height)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Pose detection failed: ${e.message}", e)
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }

    private fun processPose(pose: Pose, width: Int, height: Int) {
        // Frame skipping for performance
        frameSkipCounter++
        if (frameSkipCounter <= FRAME_SKIP_COUNT) {
            return
        }
        frameSkipCounter = 0

        // Clear the overlay and draw pose
        binding.graphicOverlay.clear()

        if (pose.allPoseLandmarks.isNotEmpty()) {
            binding.graphicOverlay.add(
                PoseGraphic(
                    binding.graphicOverlay,
                    pose,
                    false, // showInFrameLikelihood
                    false, // visualizeZ
                    false, // rescaleZForVisualization
                    JavaArrayList<String>() // poseClassification
                )
            )
        }

        // Update the overlay's image source info
        val isImageFlipped = isUsingFrontCamera
        binding.graphicOverlay.setImageSourceInfo(width, height, isImageFlipped)
        binding.graphicOverlay.postInvalidate()

        // Process sit-up exercise
        processSitUpPose(pose)
    }

    private fun processSitUpPose(pose: Pose) {
        val landmarks = pose.allPoseLandmarks
        if (landmarks.isEmpty()) {
            mainHandler.post {
                binding.tvPositionStatus.text = "Position: No pose detected"
                binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
                binding.tvPoseStatus.text = ""
            }
            return
        }

        // Get key landmarks for sit-up detection
        val nose = pose.getPoseLandmark(PoseLandmark.NOSE)
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)

        // Check confidence for key landmarks
        if (nose?.inFrameLikelihood ?: 0f < 0.5f ||
            leftShoulder?.inFrameLikelihood ?: 0f < 0.5f ||
            rightShoulder?.inFrameLikelihood ?: 0f < 0.5f ||
            leftHip?.inFrameLikelihood ?: 0f < 0.5f ||
            rightHip?.inFrameLikelihood ?: 0f < 0.5f) {
            mainHandler.post {
                binding.tvPositionStatus.text = "Position: Low confidence"
                binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
            }
            return
        }

        val currentTime = System.currentTimeMillis()

        // Calculate average shoulder and hip positions
        val avgShoulderX = (leftShoulder!!.position.x + rightShoulder!!.position.x) / 2
        val avgShoulderY = (leftShoulder.position.y + rightShoulder.position.y) / 2
        val avgHipX = (leftHip!!.position.x + rightHip!!.position.x) / 2
        val avgHipY = (leftHip.position.y + rightHip.position.y) / 2

        // Calculate torso angle (from hip to shoulder)
        val torsoAngle = atan2(
            avgShoulderY - avgHipY,
            avgShoulderX - avgHipX
        ) * 180 / Math.PI

        // Normalize torso angle to 0-180 range
        val normalizedTorsoAngle = kotlin.math.abs(torsoAngle.toFloat())

        // Add to history for smoothing
        torsoAngleHistory.add(normalizedTorsoAngle)
        if (torsoAngleHistory.size > HISTORY_SIZE) {
            torsoAngleHistory.removeAt(0)
        }

        // Calculate smoothed angles
        val smoothedTorsoAngle = torsoAngleHistory.average().toFloat()

        // Sit-up detection logic
        // Down position: torso more horizontal (angle closer to 90°)
        // Up position: torso more vertical (angle closer to 0° or 180°)

        val isInDownPosition = smoothedTorsoAngle > 45f && smoothedTorsoAngle < 135f // More horizontal
        val isInUpPosition = smoothedTorsoAngle < 30f || smoothedTorsoAngle > 150f // More vertical

        // Track phases
        if (isInDownPosition && !isDown) {
            downFrameCount++
            if (downFrameCount >= MIN_STABLE_FRAMES) {
                isDown = true
                hasBeenDown = true
                upFrameCount = 0
                lastCountedUp = false
            }
        } else if (isInUpPosition && isDown && hasBeenDown) {
            upFrameCount++
            if (upFrameCount >= MIN_STABLE_FRAMES) {
                if (!lastCountedUp && currentTime - lastSitUpTime > MIN_SIT_UP_INTERVAL) {
                    // Count the sit-up
                    incrementSitUpCount()
                    playBeep()
                    lastSitUpTime = currentTime
                    lastCountedUp = true
                }
                isDown = false
                downFrameCount = 0
            }
        } else if (!isInDownPosition && !isInUpPosition) {
            // In transition - reduce frame counts gradually
            if (downFrameCount > 0) downFrameCount--
            if (upFrameCount > 0) upFrameCount--
        }

        // Update UI
        mainHandler.post {
            val phaseText = when {
                isDown && hasBeenDown -> "DOWN ✓"
                isInUpPosition && hasBeenDown -> "UP ✓"
                isInDownPosition -> "Getting Down..."
                isInUpPosition -> "Getting Up..."
                else -> "In Position"
            }

            binding.tvPositionStatus.text = "Position: $phaseText"
            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))

            val detailText = "Angle: ${smoothedTorsoAngle.toInt()}°"
            binding.tvPoseStatus.text = detailText
            binding.tvPoseStatus.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        }

        // Debug logging
        Log.d(TAG, "Sit-up - Torso: ${smoothedTorsoAngle.toInt()}°, Down: $isDown, Up count: $upFrameCount")
    }

    private fun incrementSitUpCount() {
        // Don't count reps during rest period
        if (!isRestPeriod) {
            sitUpCount++
            updateSitUpCounter()
            Log.d(TAG, "Sit-up count: $sitUpCount")
        }
    }

    private fun resetSitUpCounter() {
        sitUpCount = 0
        resetPoseDetectionState()
        updateSitUpCounter()
        binding.tvPositionStatus.text = "Position: Ready"
        binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_light))
        binding.tvPoseStatus.text = "Lie down and start doing sit-ups"
        Log.d(TAG, "Sit-up counter reset")
    }

    private fun playBeep() {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing beep", e)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun startCountdownTimer() {
        countDownTimer?.cancel() // Cancel any existing timer
        
        countDownTimer = object : CountDownTimer(timeRemaining, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemaining = millisUntilFinished
                updateTimeDisplay(millisUntilFinished)
            }

            override fun onFinish() {
                if (isRestPeriod) {
                    // Rest period finished, start next set
                    onRestComplete()
                } else {
                    // Set finished
                    onSetComplete()
                }
            }
        }.start()
    }

    private fun onSetComplete() {
        // Play completion sound
        try {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 300)
            backgroundExecutor.execute {
                Thread.sleep(350)
                toneGenerator.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio feedback failed: ${e.message}")
        }

        if (currentSet < totalSets) {
            // More sets remaining, start rest period
            Toast.makeText(this, "✅ Set $currentSet completed! Rest for ${REST_TIME_SECONDS}s", Toast.LENGTH_SHORT).show()
            startRestPeriod()
        } else {
            // All sets completed
            binding.tvTimeLabel.text = "ALL SETS DONE!"
            binding.tvTimeLabel.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
            binding.tvSetLabel.text = "🎉 Workout Complete!"
            binding.tvSetLabel.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
            Toast.makeText(this, "Done!", Toast.LENGTH_LONG).show()
            
            // Remove exercise from dashboard and return
            removeExerciseAndFinish()
        }
    }

    private fun startRestPeriod() {
        isRestPeriod = true
        currentSet++
        resetSitUpCounter()
        timeRemaining = REST_TIME_SECONDS * 1000L
        updateSetDisplay()
        startCountdownTimer()
    }

    private fun onRestComplete() {
        // Rest finished, start next set
        isRestPeriod = false
        timeRemaining = totalTimeInMillis
        updateSetDisplay()
        
        Toast.makeText(this, "🔥 Starting Set $currentSet!", Toast.LENGTH_SHORT).show()
        startCountdownTimer()
    }

    private fun updateSetDisplay() {
        val reps = intent.getIntExtra("reps", 0)
        if (isRestPeriod) {
            binding.tvSetLabel.text = "💤 Rest (Next: Set $currentSet)"
            binding.tvSetLabel.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
        } else {
            binding.tvSetLabel.text = "Set: $currentSet/$totalSets x $reps"
            binding.tvSetLabel.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
        }
    }

    private fun updateTimeDisplay(millisUntilFinished: Long) {
        val minutes = (millisUntilFinished / 1000) / 60
        val seconds = (millisUntilFinished / 1000) % 60
        
        if (isRestPeriod) {
            binding.tvTimeLabel.text = "Rest: ${seconds}s"
            binding.tvTimeLabel.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
        } else {
            binding.tvTimeLabel.text = "Time: ${minutes}m ${seconds}s"
            // Change color as time runs out
            when {
                millisUntilFinished <= 30000 -> { // Last 30 seconds - red
                    binding.tvTimeLabel.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
                }
                millisUntilFinished <= 60000 -> { // Last minute - orange
                    binding.tvTimeLabel.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
                }
                else -> { // Normal - red
                    binding.tvTimeLabel.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
                }
            }
        }
    }

    private fun pauseTimer() {
        countDownTimer?.cancel()
    }

    private fun resumeTimer() {
        if (timeRemaining > 0) {
            startCountdownTimer()
        }
    }

    private fun updateSitUpCounter() {
        binding.tvSitUpCounter.text = "Sit-ups: ${sitUpCount}/${targetReps}"
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        cameraExecutor.shutdown()
        poseDetector.close()
    }

    override fun onPause() {
        super.onPause()
        pauseTimer()
    }

    override fun onResume() {
        super.onResume()
        if (timeRemaining > 0 && totalTimeInMillis > 0) {
            resumeTimer()
        }
    }

    private fun removeExerciseAndFinish() {
        val workoutId = intent.getStringExtra("WORKOUT_ID")
        if (workoutId != null) {
            // Get Firebase instances
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val userId = auth.currentUser?.uid
            
            if (userId != null) {
                firestore.collection("userTodoList").document(userId)
                    .collection("workoutPlan").document(workoutId)
                    .delete()
                    .addOnSuccessListener {
                        Log.d(TAG, "Exercise removed from dashboard")
                        finish() // Return to dashboard
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to remove exercise: ${e.message}")
                        finish() // Return anyway
                    }
            } else {
                finish() // Return to dashboard even if user not found
            }
        } else {
            finish() // Return to dashboard if no workout ID
        }
    }
}