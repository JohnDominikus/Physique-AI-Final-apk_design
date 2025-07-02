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
import com.example.physiqueaiapkfinal.databinding.ActivitySquatBinding
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

class SquatActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySquatBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var backgroundExecutor: ExecutorService
    private lateinit var poseDetector: com.google.mlkit.vision.pose.PoseDetector
    private var poseClassifierProcessor: PoseClassifierProcessor? = null
    private val TAG = "SquatActivity"
    private var squatCount = 0
    private var targetReps: Int = 0
    private val mainHandler = Handler(Looper.getMainLooper())

    // Ultra-accurate squat detection variables
    private var isDown = false
    private var lastSquatTime = 0L
    private val MIN_SQUAT_INTERVAL = 800L // Minimum time between squats

    // Advanced detection variables
    private var downFrameCount = 0
    private var upFrameCount = 0
    private val MIN_STABLE_FRAMES = 6 // Minimum frames for stable detection

    // Multi-dimensional history tracking for squat detection
    private val kneeAngleHistory = mutableListOf<Float>()
    private val hipAngleHistory = mutableListOf<Float>()
    private val torsoAngleHistory = mutableListOf<Float>()
    private val velocityHistory = mutableListOf<Float>()
    private val HISTORY_SIZE = 10 // History size for smoothing

    // Biomechanical validation for squats
    private var previousKneeY = 0f
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

    // Ultra-accurate squat detection with multiple criteria
    private var hasBeenDown = false // Track if person has been in down position first
    private var lastCountedUp = false // Track if we just counted to prevent double counting

    // Timer functionality
    private var countDownTimer: CountDownTimer? = null
    private var totalTimeInMillis: Long = 0
    private var timeRemaining: Long = 0
    private var totalSets: Int = 0
    private var currentSet: Int = 1
    private var isRestPeriod: Boolean = false
    private val REST_TIME_SECONDS = 20 // 20 seconds rest between sets

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySquatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Read values from intent and display them
        val sets = intent.getIntExtra("sets", 0)
        val reps = intent.getIntExtra("reps", 0)
        val minutes = intent.getIntExtra("minutes", 0)
        val seconds = intent.getIntExtra("seconds", 0)

        // Store set information
        totalSets = sets
        currentSet = 1

        // Store target reps
        targetReps = reps

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
        updateSquatCounter()

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnReset.setOnClickListener {
            resetSquatCounter()
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
        kneeAngleHistory.clear()
        hipAngleHistory.clear()

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

        // Check for squats with orientation awareness
        checkForSquatDirectly(pose, rotation)

        // Run ML classification for squat training
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

                            // Check for any squat-related classification
                            if (counterText.contains("squats_down") || counterText.contains("squat")) {
                                // Extract number from classification result
                                val regex = "\\d+".toRegex()
                                val countStr = regex.find(counterText)?.value
                                Log.d(TAG, "Found ML count: $countStr")
                                countStr?.let {
                                    val mlCount = it.toInt()
                                    if (mlCount > squatCount) {
                                        squatCount = mlCount
                                        updateSquatCounter()
                                        Log.d(TAG, "🎉 ML detected squat! Count updated to: $squatCount")
                                    }
                                }
                            }

                            // Display the pose name and confidence if available
                            if (classificationResult.size > 1) {
                                val confidenceText = classificationResult[1]
                                Log.d(TAG, "Confidence text: $confidenceText")
                                binding.tvPoseStatus.text = confidenceText
                            } else {
                                // Show the counter text as pose status
                                binding.tvPoseStatus.text = counterText
                            }
                        } else {
                            Log.d(TAG, "Empty classification result")
                            binding.tvPoseStatus.text = "Detecting pose..."
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Classification error: ${e.message}", e)
                    e.printStackTrace()
                }
            }
        }
    }

    // Ultra-accurate squat detection with multiple criteria
    private fun checkForSquatDirectly(pose: Pose, rotation: Int) {
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)

        if (leftHip != null && rightHip != null && leftKnee != null &&
            rightKnee != null && leftAnkle != null && rightAnkle != null &&
            leftShoulder != null && rightShoulder != null) {

            // Reset detection state if orientation changed
            if (rotation != lastRotation) {
                Log.d(TAG, "Orientation changed from $lastRotation to $rotation - resetting detection state")
                downFrameCount = 0
                upFrameCount = 0
                kneeAngleHistory.clear()
                hipAngleHistory.clear()
                isDown = false
                hasBeenDown = false // Reset cycle tracking
                lastCountedUp = false
                lastRotation = rotation
            }

            // Higher confidence requirement for squat detection
            val allLandmarks = listOf(leftHip, rightHip, leftKnee, rightKnee, leftAnkle, rightAnkle, leftShoulder, rightShoulder)
            val avgConfidence = allLandmarks.map { it.inFrameLikelihood }.average().toFloat()

            if (avgConfidence < 0.7f) { // High confidence required
                Log.d(TAG, "Low confidence: ${(avgConfidence*100).toInt()}%")
                return
            }

            // Calculate knee angles for squat detection
            val leftKneeAngle = calculateAngle(leftHip.position3D, leftKnee.position3D, leftAnkle.position3D)
            val rightKneeAngle = calculateAngle(rightHip.position3D, rightKnee.position3D, rightAnkle.position3D)
            val avgKneeAngle = (leftKneeAngle + rightKneeAngle) / 2

            // Calculate hip angle (torso to thigh angle)
            val avgHipX = (leftHip.position.x + rightHip.position.x) / 2
            val avgHipY = (leftHip.position.y + rightHip.position.y) / 2
            val avgShoulderY = (leftShoulder.position.y + rightShoulder.position.y) / 2
            val avgKneeY = (leftKnee.position.y + rightKnee.position.y) / 2

            // Check leg symmetry - both legs should be similar
            val legSymmetry = Math.abs(leftKneeAngle - rightKneeAngle)
            val isSymmetric = legSymmetry < 25f // Both legs should move similarly

            // Add to history for smoothing
            kneeAngleHistory.add(avgKneeAngle)

            if (kneeAngleHistory.size > 8) { // Keep history for smoothing
                kneeAngleHistory.removeAt(0)
            }

            val smoothedKneeAngle = kneeAngleHistory.average().toFloat()

            // Squat detection criteria:
            // - Knee angle should be between 70-140 degrees for down position (more lenient)
            // - Hip should be lower than usual (knees closer to hips)
            // - Both legs should be symmetric
            val isDownPosition = smoothedKneeAngle < 140f && smoothedKneeAngle > 70f && // More lenient knee range
                    isSymmetric && // Both legs symmetric
                    avgConfidence > 0.6f // Lowered confidence requirement

            Log.d(TAG, "Squat Detection - Knee Angle: ${smoothedKneeAngle.toInt()}°, Down: $isDownPosition, HasBeenDown: $hasBeenDown, Symmetric: $isSymmetric, Confidence: ${(avgConfidence*100).toInt()}%, Knee Range OK: ${smoothedKneeAngle < 140f && smoothedKneeAngle > 70f}")

            // Additional detailed logging
            Log.d(TAG, "Body Position - avgHipY: ${String.format("%.3f", avgHipY)}, avgKneeY: ${String.format("%.3f", avgKneeY)}, avgShoulderY: ${String.format("%.3f", avgShoulderY)}")
            Log.d(TAG, "Leg Angles - Left: ${leftKneeAngle.toInt()}°, Right: ${rightKneeAngle.toInt()}°, Symmetry Diff: ${Math.abs(leftKneeAngle - rightKneeAngle).toInt()}°")

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
            if (!isDown && downFrameCount >= 3) { // Reduced from 5 to 3 frames
                isDown = true
                hasBeenDown = true // Mark that person has been in down position
                stateChanged = true
                Log.d(TAG, "💪 Position changed to DOWN - squat cycle started")
            } else if (isDown && upFrameCount >= 3 && !lastCountedUp) { // Reduced from 5 to 3 frames
                // Only count if person has been down first (complete cycle) AND we haven't just counted
                if (hasBeenDown) {
                    val currentTime = System.currentTimeMillis()

                    if (currentTime - lastSquatTime >= MIN_SQUAT_INTERVAL) {
                        // Don't count reps during rest period
                        if (!isRestPeriod) {
                            squatCount++
                            lastSquatTime = currentTime
                            lastCountedUp = true // Prevent double counting
                            stateChanged = true

                            Log.d(TAG, "🎉 Squat #$squatCount completed! UP motion detected, Knee Angle: ${smoothedKneeAngle.toInt()}°")

                            mainHandler.post {
                                updateSquatCounter()
                                binding.tvPositionStatus.text = "Position: COUNT +1 (UP)!"
                                binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@SquatActivity, android.R.color.holo_orange_light))
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

                        // Reset cycle tracking for next squat
                        hasBeenDown = false
                    }

                    // Reset cycle tracking for next squat
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
                        binding.tvPositionStatus.text = "Position: Down (${smoothedKneeAngle.toInt()}°) - Hold Position"
                        binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@SquatActivity, android.R.color.holo_blue_light))
                    } else {
                        val statusText = when {
                            lastCountedUp -> "Position: Up - Great Job!"
                            hasBeenDown -> "Position: Up - Squat Down Now!"
                            else -> "Position: Up - Go Down First"
                        }
                        binding.tvPositionStatus.text = statusText
                        binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@SquatActivity, android.R.color.holo_green_light))
                    }
                }
            }

        } else {
            // Reset when landmarks are lost
            if (isDown) {
                isDown = false
                mainHandler.post {
                    binding.tvPositionStatus.text = "Position: Show full body clearly"
                    binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@SquatActivity, android.R.color.holo_red_light))
                }
            }
            downFrameCount = 0
            upFrameCount = 0
            kneeAngleHistory.clear()
            hasBeenDown = false // Reset cycle tracking when pose is lost
            lastCountedUp = false
        }
    }

    private fun resetSquatCounter() {
        squatCount = 0
        isDown = false
        hasBeenDown = false
        lastCountedUp = false
        lastSquatTime = 0L

        // Reset detection variables
        downFrameCount = 0
        upFrameCount = 0
        kneeAngleHistory.clear()
        hipAngleHistory.clear()

        updateSquatCounter()
        binding.tvPositionStatus.text = "Position: Ready - Go Down First"
        binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))

        Log.d(TAG, "Squat counter and detection state reset to 0")
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

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
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
        currentSet++ // Increment set counter when starting rest
        timeRemaining = REST_TIME_SECONDS * 1000L
        updateSetDisplay()
        startCountdownTimer()

        // Reset rep counter for next set
        resetSquatCounter()
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

    private fun updateSquatCounter() {
        binding.tvSquatCounter.text = "Squats: ${squatCount}/${targetReps}"
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