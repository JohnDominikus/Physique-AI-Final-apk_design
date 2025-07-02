@file:OptIn(androidx.camera.core.ExperimentalGetImage::class)

package com.example.physiqueaiapkfinal

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.physiqueaiapkfinal.databinding.ActivityStreamBinding
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
import kotlin.math.pow
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.math.cos
import kotlin.math.acos

class StreamActivity : AppCompatActivity() {
    private lateinit var binding: ActivityStreamBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var backgroundExecutor: ExecutorService
    private lateinit var poseDetector: com.google.mlkit.vision.pose.PoseDetector
    private var poseClassifierProcessor: PoseClassifierProcessor? = null
    private val TAG = "StreamActivity"
    private var pushupCount = 0
    private var targetReps: Int = 0
    private val mainHandler = Handler(Looper.getMainLooper())

    // Ultra-accurate push-up detection variables
    private var isDown = false
    private var lastPushupTime = 0L
    private val MIN_PUSHUP_INTERVAL = 800L // Increased for maximum accuracy

    // Advanced detection variables
    private var downFrameCount = 0
    private var upFrameCount = 0
    private val MIN_STABLE_FRAMES = 6 // Increased for maximum stability

    // Multi-dimensional history tracking
    private val shoulderElbowDiffHistory = mutableListOf<Float>()
    private val armAngleHistory = mutableListOf<Float>()
    private val bodyAngleHistory = mutableListOf<Float>()
    private val shoulderWristAngleHistory = mutableListOf<Float>()
    private val velocityHistory = mutableListOf<Float>()
    private val accelerationHistory = mutableListOf<Float>()
    private val HISTORY_SIZE = 10 // Increased for better temporal analysis

    // Biomechanical validation
    private var previousElbowY = 0f
    private var previousVelocity = 0f
    private val motionPhases = mutableListOf<String>()
    private val PHASE_HISTORY_SIZE = 5

    // Quality metrics
    private var highConfidenceFrames = 0
    private val MIN_CONFIDENCE_FRAMES = 5
    private var lastFrameTime = 0L

    // Orientation tracking
    private var lastRotation = Surface.ROTATION_0

    // Performance optimization variables
    private var frameSkipCounter = 0
    private val FRAME_SKIP_COUNT = 1 // Reduce skip for better accuracy

    // Camera switching variables
    private var isUsingFrontCamera = true
    private var cameraProvider: ProcessCameraProvider? = null

    // Ultra-accurate push-up detection with multiple criteria
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStreamBinding.inflate(layoutInflater)
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
        updatePushupCounter()

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnReset.setOnClickListener {
            resetPushupCounter()
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
        isUsingFrontCamera = !isUsingFrontCamera
        Log.d(TAG, "Switching to ${if (isUsingFrontCamera) "front" else "back"} camera")
        bindCameraUseCases()

        // Reset pose detection state when switching cameras
        resetPoseDetectionState()
    }

    private fun resetPoseDetectionState() {
        isDown = false
        hasBeenDown = false
        lastCountedUp = false
        downFrameCount = 0
        upFrameCount = 0
        shoulderElbowDiffHistory.clear()
        armAngleHistory.clear()

        Log.d(TAG, "Pose detection state reset for camera switch")
    }

    private inner class PoseAnalyzer : ImageAnalysis.Analyzer {
        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                poseDetector.process(image)
                    .addOnSuccessListener { pose ->
                        // Process pose detection results
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
        // Frame skipping for better performance
        frameSkipCounter++
        if (frameSkipCounter <= FRAME_SKIP_COUNT) {
            return
        }
        frameSkipCounter = 0

        // Clear the overlay
        binding.graphicOverlay.clear()

        // Get all landmarks
        val allPoseLandmarks = listOfNotNull(
            pose.getPoseLandmark(PoseLandmark.NOSE),
            pose.getPoseLandmark(PoseLandmark.LEFT_EYE),
            pose.getPoseLandmark(PoseLandmark.RIGHT_EYE),
            pose.getPoseLandmark(PoseLandmark.LEFT_EAR),
            pose.getPoseLandmark(PoseLandmark.RIGHT_EAR),
            pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER),
            pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER),
            pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW),
            pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW),
            pose.getPoseLandmark(PoseLandmark.LEFT_WRIST),
            pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST),
            pose.getPoseLandmark(PoseLandmark.LEFT_HIP),
            pose.getPoseLandmark(PoseLandmark.RIGHT_HIP),
            pose.getPoseLandmark(PoseLandmark.LEFT_KNEE),
            pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE),
            pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE),
            pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)
        )

        if (allPoseLandmarks.isEmpty()) {
            // No landmarks detected
            return
        }

        // Add the graphic to the overlay using Java-style parameters (not named params)
        binding.graphicOverlay.add(
            PoseGraphic(
                binding.graphicOverlay,
                pose,
                false, // showInFrameLikelihood - disabled for performance
                false, // visualizeZ - disabled for performance
                false, // rescaleZForVisualization - disabled for performance
                JavaArrayList<String>() // poseClassification
            )
        )

        // Get display orientation for proper coordinate handling
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }

        // Update the overlay's image source info with correct orientation
        val isImageFlipped = isUsingFrontCamera // Mirror front camera
        binding.graphicOverlay.setImageSourceInfo(width, height, isImageFlipped)
        binding.graphicOverlay.postInvalidate()

        // Check for push-ups with orientation awareness
        checkForPushupDirectly(pose, rotation)

        // Comment out classification for now to avoid conflicts
        /*
        // Run classification on a worker thread (this is just for visual feedback)
        val processor = poseClassifierProcessor
        if (processor != null) {
            backgroundExecutor.execute {
                try {
                    Log.d(TAG, "Starting pose classification...")
                    val classificationResult = processor.getPoseResult(pose)
                    Log.d(TAG, "Classification result: $classificationResult")
                    
                    // Update UI on the main thread
                    mainHandler.post {
                        // Update the counter from classification results
                        if (classificationResult.isNotEmpty()) {
                            val counterText = classificationResult[0]
                            Log.d(TAG, "Counter text: $counterText")
                            
                            if (counterText.contains("pushups_down")) {
                                // Extract number from "pushups_down : X reps"
                                val regex = "\\d+".toRegex()
                                val countStr = regex.find(counterText)?.value
                                Log.d(TAG, "Found count: $countStr")
                                countStr?.let {
                                    pushupCount = it.toInt()
                                    binding.tvPushupCounter.text = getString(R.string.pushup_counter_text, pushupCount)
                                }
                            }
                            
                            // Display the pose name and confidence if available
                            if (classificationResult.size > 1) {
                                val confidenceText = classificationResult[1]
                                Log.d(TAG, "Confidence text: $confidenceText")
                                binding.tvPoseStatus.text = confidenceText
                            }
                        } else {
                            Log.d(TAG, "Empty classification result")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Classification error: ${e.message}", e)
                    e.printStackTrace()
                }
            }
        }
        */
    }

    // Ultra-accurate push-up detection with multiple criteria
    private fun checkForPushupDirectly(pose: Pose, rotation: Int) {
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
        val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)

        if (leftShoulder != null && rightShoulder != null && leftElbow != null &&
            rightElbow != null && leftWrist != null && rightWrist != null) {

            // Reset detection state if orientation changed
            if (rotation != lastRotation) {
                Log.d(TAG, "Orientation changed from $lastRotation to $rotation - resetting detection state")
                downFrameCount = 0
                upFrameCount = 0
                shoulderElbowDiffHistory.clear()
                armAngleHistory.clear()
                isDown = false
                hasBeenDown = false // Reset cycle tracking
                lastCountedUp = false
                lastRotation = rotation
            }

            // Much stricter detection to prevent false positives

            // Higher confidence requirement
            val allLandmarks = listOf(leftShoulder, rightShoulder, leftElbow, rightElbow, leftWrist, rightWrist)
            val avgConfidence = allLandmarks.map { it.inFrameLikelihood }.average().toFloat()

            if (avgConfidence < 0.7f) { // Increased from 0.5f to 0.7f for better accuracy
                Log.d(TAG, "Low confidence: ${(avgConfidence*100).toInt()}%")
                return
            }

            // Calculate elbow angles with stricter validation
            val leftElbowAngle = calculateAngle(leftShoulder.position3D, leftElbow.position3D, leftWrist.position3D)
            val rightElbowAngle = calculateAngle(rightShoulder.position3D, rightElbow.position3D, rightWrist.position3D)
            val avgElbowAngle = (leftElbowAngle + rightElbowAngle) / 2

            // Check arm symmetry - both arms should be similar
            val armSymmetry = Math.abs(leftElbowAngle - rightElbowAngle)
            val isSymmetric = armSymmetry < 30f // Both arms should move similarly

            // Position-based detection with stricter thresholds
            val avgElbowY = (leftElbow.position.y + rightElbow.position.y) * 0.5f
            val avgShoulderY = (leftShoulder.position.y + rightShoulder.position.y) * 0.5f
            val avgWristY = (leftWrist.position.y + rightWrist.position.y) * 0.5f
            val shoulderElbowDiff = avgElbowY - avgShoulderY

            // Additional validation: wrists should be lower than shoulders in down position
            val wristBelowShoulder = avgWristY > avgShoulderY + 0.02f

            // Add to history for smoothing (keep longer history for stability)
            shoulderElbowDiffHistory.add(shoulderElbowDiff)
            armAngleHistory.add(avgElbowAngle)

            if (shoulderElbowDiffHistory.size > 8) { // Increased from 5 to 8 for better smoothing
                shoulderElbowDiffHistory.removeAt(0)
                armAngleHistory.removeAt(0)
            }

            val smoothedDiff = shoulderElbowDiffHistory.average().toFloat()
            val smoothedAngle = armAngleHistory.average().toFloat()

            // Much stricter detection criteria to prevent false positives
            val isDownPosition = smoothedDiff > 0.05f && // Increased threshold from 0.03f
                    smoothedAngle < 110f && smoothedAngle > 40f && // Stricter angle range
                    isSymmetric && // Both arms must be symmetric
                    wristBelowShoulder && // Wrists must be below shoulders
                    avgConfidence > 0.7f // High confidence required

            Log.d(TAG, "Detection - Diff: ${String.format("%.3f", smoothedDiff)}, Angle: ${smoothedAngle.toInt()}°, Down: $isDownPosition, HasBeenDown: $hasBeenDown, Symmetric: $isSymmetric, Confidence: ${(avgConfidence*100).toInt()}%")

            // State tracking with higher stability requirements
            if (isDownPosition) {
                downFrameCount++
                upFrameCount = 0
                lastCountedUp = false // Reset count flag when going down
            } else {
                upFrameCount++
                downFrameCount = 0
            }

            var stateChanged = false

            // State changes with stricter frame requirements
            if (!isDown && downFrameCount >= 5) { // Increased from 3 to 5 frames
                isDown = true
                hasBeenDown = true // Mark that person has been in down position
                stateChanged = true
                Log.d(TAG, "💪 Position changed to DOWN - cycle started")
            } else if (isDown && upFrameCount >= 5 && !lastCountedUp) { // Only count UP motion and prevent double counting
                // Only count if person has been down first (complete cycle) AND we haven't just counted
                if (hasBeenDown) {
                    val currentTime = System.currentTimeMillis()

                    if (currentTime - lastPushupTime >= MIN_PUSHUP_INTERVAL) {
                        // Don't count reps during rest period
                        if (!isRestPeriod) {
                            pushupCount++
                        lastPushupTime = currentTime
                        lastCountedUp = true // Prevent double counting
                        stateChanged = true

                            Log.d(TAG, "🎉 Push-up #$pushupCount completed! UP motion detected, Angle: ${smoothedAngle.toInt()}°")

                            mainHandler.post {
                                updatePushupCounter()
                                binding.tvPositionStatus.text = "Position: COUNT +1 (UP)!"
                                binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@StreamActivity, android.R.color.holo_orange_light))
                            }

                            // Audio feedback
                            try {
                                val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
                                backgroundExecutor.execute {
                                    Thread.sleep(250)
                                    toneGenerator.release()
                                }
                            } catch (e: Exception) {
                                // Ignore audio errors
                            }
                        }
                    }

                    // Reset cycle tracking for next push-up
                    hasBeenDown = false
                } else {
                    Log.d(TAG, "⚠️ Going UP but no DOWN detected first - no count")
                }

                isDown = false
            }

            // UI updates with clearer status messages
            if (stateChanged || (frameSkipCounter % 30 == 0)) {
                mainHandler.post {
                    if (isDown) {
                        binding.tvPositionStatus.text = "Position: Down (${smoothedAngle.toInt()}°) - Hold Position"
                        binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@StreamActivity, android.R.color.holo_blue_light))
                    } else {
                        val statusText = when {
                            lastCountedUp -> "Position: Up - Great Job!"
                            hasBeenDown -> "Position: Up - Push Up Now!"
                            else -> "Position: Up - Go Down First"
                        }
                        binding.tvPositionStatus.text = statusText
                        binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@StreamActivity, android.R.color.holo_green_light))
                    }
                }
            }

        } else {
            // Reset when landmarks are lost
            if (isDown) {
                isDown = false
                mainHandler.post {
                    binding.tvPositionStatus.text = "Position: Show arms clearly"
                    binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@StreamActivity, android.R.color.holo_red_light))
                }
            }
            downFrameCount = 0
            upFrameCount = 0
            shoulderElbowDiffHistory.clear()
            armAngleHistory.clear()
            hasBeenDown = false // Reset cycle tracking when pose is lost
            lastCountedUp = false
        }
    }

    private fun resetPushupCounter() {
        pushupCount = 0
        isDown = false
        hasBeenDown = false
        lastCountedUp = false
        lastPushupTime = 0L

        // Reset simplified detection variables
        downFrameCount = 0
        upFrameCount = 0
        shoulderElbowDiffHistory.clear()
        armAngleHistory.clear()

        // Reset the pose classifier processor counter as well
        backgroundExecutor.execute {
            poseClassifierProcessor?.resetCounters()
        }

        updatePushupCounter()

        binding.tvPositionStatus.text = "Position: Ready - Go Down First"
        binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))

        Log.d(TAG, "Push-up counter and detection state reset to 0")
    }

    private fun updatePushupCounter() {
        binding.tvPushupCounter.text = "Push-ups: ${pushupCount}/${targetReps}"
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
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
        resetPushupCounter()
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

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        cameraExecutor.shutdown()
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

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    // Calculate angle between three points in degrees
    private fun calculateAngle(p1: com.google.mlkit.vision.common.PointF3D,
                               p2: com.google.mlkit.vision.common.PointF3D,
                               p3: com.google.mlkit.vision.common.PointF3D): Float {
        val v1x = p1.x - p2.x
        val v1y = p1.y - p2.y
        val v2x = p3.x - p2.x
        val v2y = p3.y - p2.y

        val dot = v1x * v2x + v1y * v2y
        val mag1 = sqrt(v1x * v1x + v1y * v1y)
        val mag2 = sqrt(v2x * v2x + v2y * v2y)

        if (mag1 == 0f || mag2 == 0f) return 0f

        val cosAngle = dot / (mag1 * mag2)
        val clampedCos = cosAngle.coerceIn(-1f, 1f)

        return Math.toDegrees(kotlin.math.acos(clampedCos.toDouble())).toFloat()
    }
}