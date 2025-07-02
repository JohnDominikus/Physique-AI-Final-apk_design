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
import com.example.physiqueaiapkfinal.databinding.ActivityHipThrustsBinding
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

class HipThrustsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHipThrustsBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var backgroundExecutor: ExecutorService
    private lateinit var poseDetector: com.google.mlkit.vision.pose.PoseDetector
    private var poseClassifierProcessor: PoseClassifierProcessor? = null
    private val TAG = "HipThrustsActivity"
    private var hipThrustCount = 0
    private var targetReps: Int = 0
    private val mainHandler = Handler(Looper.getMainLooper())

    // Ultra-accurate hip thrust detection variables
    private var isUp = false
    private var lastHipThrustTime = 0L
    private val MIN_HIP_THRUST_INTERVAL = 1000L // Increased to 1 second to prevent false rapid counting

    // Advanced detection variables
    private var upFrameCount = 0
    private var downFrameCount = 0
    private val MIN_STABLE_FRAMES = 6 // Minimum frames for stable detection

    // Multi-dimensional history tracking for hip thrust detection
    private val hipAngleHistory = mutableListOf<Float>()
    private val HISTORY_SIZE = 5 // Reduced history size for simpler detection

    // Biomechanical validation for hip thrusts
    private var previousHipY = 0f
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

    // Ultra-accurate hip thrust detection with multiple criteria
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
        binding = ActivityHipThrustsBinding.inflate(layoutInflater)
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
        updateHipThrustCounter()

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnReset.setOnClickListener {
            resetHipThrustCounter()
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
        isUp = false
        hasBeenDown = false
        lastCountedUp = false
        upFrameCount = 0
        downFrameCount = 0
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

        // Add the graphic to the overlay
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

        // Check for hip thrusts with orientation awareness
        checkForHipThrustDirectly(pose, rotation)
    }

    // CLEAR & ACCURATE hip thrust detection using only Y-coordinates
    private fun checkForHipThrustDirectly(pose: Pose, rotation: Int) {
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)

        if (leftHip != null && rightHip != null && leftShoulder != null && rightShoulder != null) {
            val avgHipY = (leftHip.position.y + rightHip.position.y) / 2
            val avgShoulderY = (leftShoulder.position.y + rightShoulder.position.y) / 2

            // DOWN: hips at or below shoulder level (nakahiga)
            val isDownPosition = avgHipY > avgShoulderY - 10
            // UP: hips clearly above shoulders (naka-bridge)
            val isUpPosition = avgHipY < avgShoulderY - 40

            // State machine using existing variables
            if (isDownPosition && !isUp) {
                // DOWN state
                hasBeenDown = true
                isUp = false
                mainHandler.post {
                    binding.tvPositionStatus.text = "DOWN - Push your hips up!"
                    binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@HipThrustsActivity, android.R.color.holo_green_light))
                }
            } else if (isUpPosition && hasBeenDown && !isUp) {
                // Transition from DOWN to UP (count rep)
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastHipThrustTime > 1000L) { // 1 second debounce
                                            // Don't count reps during rest period
                        if (!isRestPeriod) {
                            hipThrustCount++
                    lastHipThrustTime = currentTime
                    mainHandler.post {
                        updateHipThrustCounter()
                        binding.tvPositionStatus.text = "COUNTED! Rep #$hipThrustCount - Go back down."
                        binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@HipThrustsActivity, android.R.color.holo_blue_light))
                    }
                    // Audio feedback
                    try {
                        val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                        backgroundExecutor.execute {
                            Thread.sleep(200)
                            toneGenerator.release()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Audio failed: ${e.message}")
                    }
                    }
                }
                isUp = true
            } else if (!isDownPosition && !isUpPosition) {
                // Neutral state
                mainHandler.post {
                    binding.tvPositionStatus.text = "Fix your position: Lie down straight."
                    binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@HipThrustsActivity, android.R.color.holo_orange_light))
                }
            } else if (isDownPosition && isUp) {
                // If bumaba na ulit from UP, reset to DOWN
                isUp = false
                hasBeenDown = true
            }

            // Debug info
            mainHandler.post {
                binding.tvPoseStatus.text = String.format(
                    "HipY: %.1f | ShoulderY: %.1f | State: %s | Count: %d",
                    avgHipY, avgShoulderY,
                    if (isUp) "UP" else if (hasBeenDown) "DOWN" else "NEUTRAL",
                    hipThrustCount
                )
            }
        } else {
            mainHandler.post {
                binding.tvPositionStatus.text = "Show your full body to the camera."
                binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@HipThrustsActivity, android.R.color.holo_red_light))
            }
        }
    }

    private fun resetHipThrustCounter() {
        hipThrustCount = 0
        isUp = false
        hasBeenDown = false
        lastCountedUp = false
        lastHipThrustTime = 0L

        // Reset detection variables
        upFrameCount = 0
        downFrameCount = 0
        hipAngleHistory.clear()

        updateHipThrustCounter()
        binding.tvPositionStatus.text = "Position: Ready - Lie flat on floor"
        binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))

        Log.d(TAG, "Floor hip thrust counter and detection state reset to 0")
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
        currentSet++
        resetHipThrustCounter()
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

    private fun updateHipThrustCounter() {
        binding.tvHipThrustCounter.text = "Hip Thrusts: ${hipThrustCount}/${targetReps}"
        // Early set completion check
        if (!isRestPeriod && hipThrustCount >= targetReps) {
            countDownTimer?.cancel()
            onSetComplete()
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