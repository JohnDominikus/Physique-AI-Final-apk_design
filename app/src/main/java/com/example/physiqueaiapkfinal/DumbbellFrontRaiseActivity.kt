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
import com.example.physiqueaiapkfinal.databinding.ActivityDumbbellFrontRaiseBinding
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

class DumbbellFrontRaiseActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDumbbellFrontRaiseBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var backgroundExecutor: ExecutorService
    private lateinit var poseDetector: com.google.mlkit.vision.pose.PoseDetector
    private var poseClassifierProcessor: PoseClassifierProcessor? = null
    private val TAG = "DumbbellFrontRaiseActivity"
    private var frontRaiseCount = 0
    private var targetReps: Int = 0
    private val mainHandler = Handler(Looper.getMainLooper())

    // Ultra-accurate front raise detection variables
    private var isRaised = false
    private var lastFrontRaiseTime = 0L
    private val MIN_FRONT_RAISE_INTERVAL = 1000L // Minimum time between reps

    // Advanced detection variables
    private var raisedFrameCount = 0
    private var loweredFrameCount = 0
    private val MIN_STABLE_FRAMES = 3 // More responsive for better counting

    // Simplified history tracking for front raise detection
    private val shoulderAngleHistory = mutableListOf<Float>()

    // Orientation tracking
    private var lastRotation = Surface.ROTATION_0

    // Performance optimization variables
    private var frameSkipCounter = 0
    private val FRAME_SKIP_COUNT = 1 // Reduce skip for better accuracy

    // Camera switching variables
    private var isUsingFrontCamera = true
    private var cameraProvider: ProcessCameraProvider? = null

    // Ultra-accurate front raise detection with multiple criteria
    private var hasBeenLowered = false // Track if arms have been in lowered position first
    private var lastCountedRaise = false // Track if we just counted to prevent double counting

    // Timer functionality
    private var countDownTimer: CountDownTimer? = null
    private var totalTimeInMillis: Long = 0
    private var timeRemaining: Long = 0
    private var totalSets: Int = 0
    private var currentSet: Int = 1
    private var isRestPeriod: Boolean = false
    private val REST_TIME_SECONDS = 30

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDumbbellFrontRaiseBinding.inflate(layoutInflater)
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
        updateFrontRaiseCounter()

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnReset.setOnClickListener {
            resetFrontRaiseCounter()
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
        isRaised = false
        hasBeenLowered = false
        lastCountedRaise = false
        raisedFrameCount = 0
        loweredFrameCount = 0
        shoulderAngleHistory.clear()

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
            pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
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

        // Check for front raises with orientation awareness
        checkForFrontRaiseDirectly(pose, rotation)
    }

    // Ultra-accurate front raise detection with multiple criteria
    private fun checkForFrontRaiseDirectly(pose: Pose, rotation: Int) {
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
        val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)

        if (leftShoulder != null && rightShoulder != null && leftElbow != null &&
            rightElbow != null && leftWrist != null && rightWrist != null &&
            leftHip != null && rightHip != null) {

            // Reset detection state if orientation changed
            if (rotation != lastRotation) {
                Log.d(TAG, "Orientation changed - resetting detection state")
                raisedFrameCount = 0
                loweredFrameCount = 0
                shoulderAngleHistory.clear()
                isRaised = false
                hasBeenLowered = false
                lastCountedRaise = false
                lastRotation = rotation
            }

            // Basic confidence check
            val allLandmarks = listOf(leftShoulder, rightShoulder, leftElbow, rightElbow, leftWrist, rightWrist)
            val avgConfidence = allLandmarks.map { it.inFrameLikelihood }.average().toFloat()

            if (avgConfidence < 0.5f) { // Lower threshold for better detection
                Log.d(TAG, "Low confidence: ${(avgConfidence*100).toInt()}%")
                return
            }

            // COMPLETELY NEW SIMPLE APPROACH - Direct position comparison
            // No complex calculations, just direct Y-coordinate comparison

            // 1. Get raw Y positions (lower Y = higher on screen)
            val leftShoulderY = leftShoulder.position.y
            val rightShoulderY = rightShoulder.position.y
            val leftWristY = leftWrist.position.y
            val rightWristY = rightWrist.position.y

            val avgShoulderY = (leftShoulderY + rightShoulderY) / 2
            val avgWristY = (leftWristY + rightWristY) / 2

            // 2. Simple position detection using direct Y comparison
            // In camera coordinates: smaller Y = higher position
            val wristToShoulderDiff = avgWristY - avgShoulderY

            // 3. Check if person is standing upright
            val avgHipY = (leftHip.position.y + rightHip.position.y) / 2
            val isUpright = avgShoulderY < avgHipY // Shoulders above hips

            // 4. Check if both arms are visible and detected properly
            val leftArmVisible = leftWrist.inFrameLikelihood > 0.5f && leftElbow.inFrameLikelihood > 0.5f
            val rightArmVisible = rightWrist.inFrameLikelihood > 0.5f && rightElbow.inFrameLikelihood > 0.5f
            val armsVisible = leftArmVisible && rightArmVisible
            val bothArmsUsed = armsVisible

            // Add to history for smoothing
            shoulderAngleHistory.add(wristToShoulderDiff)
            if (shoulderAngleHistory.size > 8) {
                shoulderAngleHistory.removeAt(0)
            }

            val smoothedDiff = shoulderAngleHistory.average().toFloat()

            // IMPROVED POSITION DETECTION with better thresholds:
            // Positive values = wrists below shoulders (lowered)
            // Near zero values = wrists at shoulder level (raised)
            // Negative values = wrists above shoulders (over-raised)

            // Calculate arm straightness for better accuracy
            val leftArmLength = sqrt((leftWrist.position.x - leftShoulder.position.x).pow(2) +
                    (leftWrist.position.y - leftShoulder.position.y).pow(2))
            val rightArmLength = sqrt((rightWrist.position.x - rightShoulder.position.x).pow(2) +
                    (rightWrist.position.y - rightShoulder.position.y).pow(2))
            val avgArmLength = (leftArmLength + rightArmLength) / 2

            // Check if arms are extended (not bent)
            val leftElbowToShoulder = sqrt((leftElbow.position.x - leftShoulder.position.x).pow(2) +
                    (leftElbow.position.y - leftShoulder.position.y).pow(2))
            val leftElbowToWrist = sqrt((leftWrist.position.x - leftElbow.position.x).pow(2) +
                    (leftWrist.position.y - leftElbow.position.y).pow(2))
            val leftArmExtension = (leftElbowToShoulder + leftElbowToWrist) / avgArmLength

            val rightElbowToShoulder = sqrt((rightElbow.position.x - rightShoulder.position.x).pow(2) +
                    (rightElbow.position.y - rightShoulder.position.y).pow(2))
            val rightElbowToWrist = sqrt((rightWrist.position.x - rightElbow.position.x).pow(2) +
                    (rightWrist.position.y - rightElbow.position.y).pow(2))
            val rightArmExtension = (rightElbowToShoulder + rightElbowToWrist) / avgArmLength

            val avgArmExtension = (leftArmExtension + rightArmExtension) / 2
            val armsExtended = avgArmExtension > 1.8f // Arms should be relatively straight

            // Simplified and more reliable detection
            val isLoweredPosition = (
                    smoothedDiff > 60f && // Wrists clearly below shoulders - relaxed threshold
                            isUpright && bothArmsUsed && avgConfidence > 0.5f
                    )

            val isRaisedPosition = (
                    smoothedDiff >= -20f && smoothedDiff <= 60f && // Wider range for shoulder level
                            isUpright && bothArmsUsed && avgConfidence > 0.5f && armsExtended
                    )

            val isOverRaised = (
                    smoothedDiff < -20f && // Wrists clearly above shoulders
                            isUpright && avgConfidence > 0.5f
                    )

            // Detailed logging for debugging
            Log.d(TAG, "=== Front Raise Detection ===")
            Log.d(TAG, "RAW POSITIONS:")
            Log.d(TAG, "  Left Shoulder Y: ${leftShoulderY}, Left Wrist Y: ${leftWristY}")
            Log.d(TAG, "  Right Shoulder Y: ${rightShoulderY}, Right Wrist Y: ${rightWristY}")
            Log.d(TAG, "  Average Shoulder Y: ${avgShoulderY}, Average Wrist Y: ${avgWristY}")
            Log.d(TAG, "POSITION DIFFERENCE:")
            Log.d(TAG, "  Wrist-to-Shoulder Diff: ${String.format("%.1f", wristToShoulderDiff)} (positive=lower, negative=higher)")
            Log.d(TAG, "  Smoothed Diff: ${String.format("%.1f", smoothedDiff)}")
            Log.d(TAG, "ARM EXTENSION:")
            Log.d(TAG, "  Left Extension: ${String.format("%.2f", leftArmExtension)}, Right Extension: ${String.format("%.2f", rightArmExtension)}")
            Log.d(TAG, "  Average Extension: ${String.format("%.2f", avgArmExtension)}, Arms Extended: $armsExtended (>1.8)")
            Log.d(TAG, "DETECTION RANGES:")
            Log.d(TAG, "  LOWERED: >60 | RAISED: -20 to 60 | OVER-RAISED: <-20")
            Log.d(TAG, "OTHER CHECKS:")
            Log.d(TAG, "  Is Upright: $isUpright, Both Arms Used: $bothArmsUsed")
            Log.d(TAG, "  Left Arm Visible: $leftArmVisible, Right Arm Visible: $rightArmVisible")
            Log.d(TAG, "  Confidence: ${(avgConfidence*100).toInt()}%")
            Log.d(TAG, "POSITION DETECTION:")
            Log.d(TAG, "  Position - Raised: $isRaisedPosition, Lowered: $isLoweredPosition, Over-raised: $isOverRaised")
            Log.d(TAG, "STATE:")
            Log.d(TAG, "  State - hasBeenLowered: $hasBeenLowered, lastCountedRaise: $lastCountedRaise")

            // State tracking - count immediately when reaching shoulder level from lowered position
            if (isRaisedPosition) {
                raisedFrameCount++
                loweredFrameCount = 0
                Log.d(TAG, "RAISED frames: $raisedFrameCount/$MIN_STABLE_FRAMES")

                // IMPROVED LOGIC: Count when reaching shoulder level with proper form
                if (!isRaised && hasBeenLowered && !lastCountedRaise && raisedFrameCount >= 2 && armsExtended && bothArmsUsed) {
                    // Complete rep: lowered → shoulder level (count immediately!)
                    val currentTime = System.currentTimeMillis()

                    if (currentTime - lastFrontRaiseTime >= MIN_FRONT_RAISE_INTERVAL) {
                        frontRaiseCount++
                        lastFrontRaiseTime = currentTime
                        lastCountedRaise = true

                        Log.d(TAG, "🎉 FRONT RAISE #$frontRaiseCount COUNTED! Lowered → Shoulder Level")

                        mainHandler.post {
                            updateFrontRaiseCounter()
                            binding.tvPositionStatus.text = "Position: COUNTED +1! Perfect Form ✓"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@DumbbellFrontRaiseActivity, android.R.color.holo_orange_light))
                        }

                        // Audio feedback
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
                    }
                    hasBeenLowered = false // Reset for next cycle
                    isRaised = true // Mark as raised to prevent multiple counts
                }

            } else if (isLoweredPosition) {
                loweredFrameCount++
                raisedFrameCount = 0
                Log.d(TAG, "LOWERED frames: $loweredFrameCount/$MIN_STABLE_FRAMES")

                // Set lowered state when stable (relaxed arm extension requirement for lowered position)
                if (loweredFrameCount >= MIN_STABLE_FRAMES && bothArmsUsed) {
                    if (isRaised) {
                        isRaised = false
                        hasBeenLowered = true
                        lastCountedRaise = false
                        Log.d(TAG, "💪 State changed to LOWERED - ready for next rep")
                    } else if (!hasBeenLowered) {
                        hasBeenLowered = true
                        Log.d(TAG, "💪 Initial LOWERED position established")
                    }
                }

            } else if (isOverRaised) {
                // Reset raised frames when over-raised to prevent counting
                raisedFrameCount = 0
                Log.d(TAG, "OVER-RAISED - resetting raised frames (no count for improper form)")
            } else {
                // In transition - maintain current state
                Log.d(TAG, "IN TRANSITION - maintaining current state")
            }

            // Enhanced UI updates with proper cautions
            if ((frameSkipCounter % 10 == 0)) {
                mainHandler.post {
                    when {
                        !bothArmsUsed -> {
                            if (!leftArmVisible && !rightArmVisible) {
                                binding.tvPositionStatus.text = "⚠️ CAUTION: Use Both Arms\nBoth arms not detected"
                            } else if (!leftArmVisible) {
                                binding.tvPositionStatus.text = "⚠️ CAUTION: Use Both Arms\nLeft arm not detected"
                            } else if (!rightArmVisible) {
                                binding.tvPositionStatus.text = "⚠️ CAUTION: Use Both Arms\nRight arm not detected"
                            } else {
                                binding.tvPositionStatus.text = "⚠️ CAUTION: Use Both Arms\nArms not clearly visible"
                            }
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@DumbbellFrontRaiseActivity, android.R.color.holo_red_dark))
                        }
                        isOverRaised -> {
                            binding.tvPositionStatus.text = "⚠️ CAUTION: Too High!\nLower arms to shoulder level"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@DumbbellFrontRaiseActivity, android.R.color.holo_red_light))
                        }
                        !armsExtended && bothArmsUsed -> {
                            binding.tvPositionStatus.text = "⚠️ CAUTION: Straighten Arms\nKeep arms extended during exercise"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@DumbbellFrontRaiseActivity, android.R.color.holo_red_light))
                        }
                        isRaised -> {
                            binding.tvPositionStatus.text = "✅ RAISED POSITION\nLower arms to complete rep"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@DumbbellFrontRaiseActivity, android.R.color.holo_blue_light))
                        }
                        lastCountedRaise -> {
                            binding.tvPositionStatus.text = "🎉 REP COUNTED!\nGreat job! Count: $frontRaiseCount"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@DumbbellFrontRaiseActivity, android.R.color.holo_green_light))
                        }
                        hasBeenLowered -> {
                            binding.tvPositionStatus.text = "✅ READY TO RAISE\nRaise both arms to shoulder level"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@DumbbellFrontRaiseActivity, android.R.color.holo_green_light))
                        }
                        isLoweredPosition -> {
                            binding.tvPositionStatus.text = "✅ LOWERED POSITION\nGood starting position"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@DumbbellFrontRaiseActivity, android.R.color.holo_orange_light))
                        }
                        else -> {
                            binding.tvPositionStatus.text = "📍 GET READY\nLower both arms to start"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@DumbbellFrontRaiseActivity, android.R.color.holo_orange_light))
                        }
                    }

                    // Show real-time metrics with position indicator
                    val positionIndicator = when {
                        isOverRaised -> "↑TOO HIGH"
                        isRaisedPosition -> "✓GOOD"
                        isLoweredPosition -> "↓LOW"
                        else -> "~MOVE"
                    }

                    binding.tvPoseStatus.text = String.format(
                        "Height: %.2f | %s | R%d/L%d",
                        smoothedDiff, positionIndicator, raisedFrameCount, loweredFrameCount
                    )
                }
            }

        } else {
            // Reset when landmarks are lost
            Log.d(TAG, "❌ Landmarks missing - resetting state")
            if (isRaised) {
                isRaised = false
                mainHandler.post {
                    binding.tvPositionStatus.text = "Position: Show both arms clearly"
                    binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@DumbbellFrontRaiseActivity, android.R.color.holo_red_light))
                }
            }
            raisedFrameCount = 0
            loweredFrameCount = 0
            shoulderAngleHistory.clear()
            hasBeenLowered = false
            lastCountedRaise = false
        }
    }

    private fun resetFrontRaiseCounter() {
        frontRaiseCount = 0
        isRaised = false
        hasBeenLowered = false
        lastCountedRaise = false
        lastFrontRaiseTime = 0L

        // Reset detection variables
        raisedFrameCount = 0
        loweredFrameCount = 0
        shoulderAngleHistory.clear()

        updateFrontRaiseCounter()

        binding.tvPositionStatus.text = "Position: Ready - Lower Arms First"
        binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))

        Log.d(TAG, "Front raise counter and detection state reset to 0")
    }

    private fun updateFrontRaiseCounter() {
        binding.tvFrontRaiseCounter.text = "Front Raises: $frontRaiseCount/$targetReps"
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
            Toast.makeText(this, "🎉 All sets completed! Great workout!", Toast.LENGTH_LONG).show()
        }
    }

    private fun startRestPeriod() {
        isRestPeriod = true
        currentSet++ // Increment set counter when starting rest

        // Reset rep counter for next set
        resetFrontRaiseCounter()

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
        backgroundExecutor.shutdown()
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}