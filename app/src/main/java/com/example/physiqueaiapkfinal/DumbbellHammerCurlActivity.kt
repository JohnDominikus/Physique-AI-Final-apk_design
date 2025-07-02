@file:OptIn(androidx.camera.core.ExperimentalGetImage::class)

package com.example.physiqueaiapkfinal

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
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
import com.example.physiqueaiapkfinal.databinding.ActivityDumbbellHammerCurlBinding
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

class DumbbellHammerCurlActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDumbbellHammerCurlBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var backgroundExecutor: ExecutorService
    private lateinit var poseDetector: com.google.mlkit.vision.pose.PoseDetector
    private var poseClassifierProcessor: PoseClassifierProcessor? = null
    private val TAG = "DumbbellHammerCurlActivity"
    private var hammerCurlCount = 0
    private var targetReps: Int = 0
    private val mainHandler = Handler(Looper.getMainLooper())

    // Ultra-accurate hammer curl detection variables
    private var isRaised = false
    private var lastHammerCurlTime = 0L
    private val MIN_HAMMER_CURL_INTERVAL = 500L // Reduced time for more responsive counting

    // Advanced detection variables
    private var raisedFrameCount = 0
    private var loweredFrameCount = 0
    private val MIN_STABLE_FRAMES = 1 // Maximum responsiveness for counting

    // Simplified history tracking for hammer curl detection
    private val shoulderAngleHistory = mutableListOf<Float>()

    // Orientation tracking
    private var lastRotation = Surface.ROTATION_0

    // Performance optimization variables
    private var frameSkipCounter = 0
    private val FRAME_SKIP_COUNT = 3 // Increase skip to improve performance

    // Camera switching variables
    private var isUsingFrontCamera = true
    private var cameraProvider: ProcessCameraProvider? = null

    // Ultra-accurate hammer curl detection with multiple criteria
    private var hasBeenLowered = false // Track if arms have been in lowered position first
    private var lastCountedRaise = false // Track if we just counted to prevent double counting

    // Timer functionality
    private var countDownTimer: CountDownTimer? = null
    private var totalTimeInMillis: Long = 0
    private var timeRemaining: Long = 0
    private var totalSets: Int = 0
    private var currentSet: Int = 1
    private var isRestPeriod: Boolean = false
    private val REST_TIME_SECONDS = 20 // 20 seconds rest between sets

    companion object {
        private const val TAG = "DumbbellHammerCurlActivity"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val REQUEST_CODE_PERMISSIONS = 10
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDumbbellHammerCurlBinding.inflate(layoutInflater)
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
        updateHammerCurlCounter()

        // Early set completion check
        if (!isRestPeriod && hammerCurlCount >= targetReps) {
            countDownTimer?.cancel()
            onSetComplete()
        }

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnReset.setOnClickListener {
            resetHammerCurlCounter()
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
            .setTargetResolution(android.util.Size(480, 360))
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
            mainHandler.post {
                binding.tvCameraStatus.text = if (isUsingFrontCamera) "Camera: Front (Mirrored)" else "Camera: Back"
            }

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
        try {
            // Always clear and update overlay for smooth skeleton display
            binding.graphicOverlay.clear()

            // Get display orientation for proper coordinate handling
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display?.rotation ?: Surface.ROTATION_0
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.rotation
            }

            // Get all landmarks for pose visualization
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

            if (allPoseLandmarks.isNotEmpty()) {
                // Always add the graphic to the overlay for smooth skeleton
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

                // Update the overlay's image source info with correct orientation
                val isImageFlipped = isUsingFrontCamera // Mirror front camera
                binding.graphicOverlay.setImageSourceInfo(width, height, isImageFlipped)
                binding.graphicOverlay.postInvalidate()
            }

            // Frame skipping for detection logic only (not for skeleton display)
            frameSkipCounter++
            if (frameSkipCounter <= FRAME_SKIP_COUNT) {
                return
            }
            frameSkipCounter = 0

            // Check for hammer curls with orientation awareness (only every few frames)
            if (allPoseLandmarks.isNotEmpty()) {
                checkForHammerCurlDirectly(pose, rotation)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing pose: ${e.message}", e)
        }
    }

    // Ultra-accurate hammer curl detection with multiple criteria
    private fun checkForHammerCurlDirectly(pose: Pose, rotation: Int) {
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

            // Balanced confidence check
            val allLandmarks = listOf(leftShoulder, rightShoulder, leftElbow, rightElbow, leftWrist, rightWrist)
            val avgConfidence = allLandmarks.map { it.inFrameLikelihood }.average().toFloat()

            if (avgConfidence < 0.6f) { // Balanced threshold for better detection
                Log.d(TAG, "Low confidence: ${(avgConfidence*100).toInt()}%")
                return
            }

            // HAMMER CURL SPECIFIC DETECTION - Using elbow-to-wrist relationship
            // For hammer curls, we focus on vertical movement of wrists relative to elbows

            // 1. Get raw Y positions (lower Y = higher on screen)
            val leftElbowY = leftElbow.position.y
            val rightElbowY = rightElbow.position.y
            val leftWristY = leftWrist.position.y
            val rightWristY = rightWrist.position.y
            val leftShoulderY = leftShoulder.position.y
            val rightShoulderY = rightShoulder.position.y

            val avgElbowY = (leftElbowY + rightElbowY) / 2
            val avgWristY = (leftWristY + rightWristY) / 2
            val avgShoulderY = (leftShoulderY + rightShoulderY) / 2

            // 2. Enhanced position detection using elbow-to-wrist Y comparison
            // In camera coordinates: smaller Y = higher position
            val wristToElbowDiff = avgWristY - avgElbowY

            // 3. Check if person is standing upright
            val avgHipY = (leftHip.position.y + rightHip.position.y) / 2
            val isUpright = avgShoulderY < avgHipY // Shoulders above hips

            // 4. Balanced both arms detection for hammer curls
            val leftArmVisible = leftWrist.inFrameLikelihood > 0.7f && leftElbow.inFrameLikelihood > 0.7f && leftShoulder.inFrameLikelihood > 0.7f
            val rightArmVisible = rightWrist.inFrameLikelihood > 0.7f && rightElbow.inFrameLikelihood > 0.7f && rightShoulder.inFrameLikelihood > 0.7f
            val bothArmsUsed = leftArmVisible && rightArmVisible

            // Calculate individual arm positions for strict detection
            val leftWristToElbowDiff = leftWristY - leftElbowY
            val rightWristToElbowDiff = rightWristY - rightElbowY

            // NEW: STRICT SINGLE ARM DETECTION - Detect if only one arm is moving
            val leftArmMoving = kotlin.math.abs(leftWristToElbowDiff) > 8f // Reduced threshold for better detection
            val rightArmMoving = kotlin.math.abs(rightWristToElbowDiff) > 8f // Reduced threshold for better detection
            val singleArmDetected = (leftArmMoving && !rightArmMoving) || (rightArmMoving && !leftArmMoving)
            val bothArmsMoving = leftArmMoving && rightArmMoving

            // STRICT ARM SYNCHRONIZATION - Both arms must move in similar amounts
            val armMovementDifference = kotlin.math.abs(leftWristToElbowDiff - rightWristToElbowDiff)
            val armsMovingSynchronously = armMovementDifference < 25f // Much stricter than before (was 80f)

            // 5. HAMMER CURL SPECIFIC CONSTRAINTS to prevent false positives from other exercises:

            // A. Arms reasonably positioned (ultra lenient for easy counting)
            val leftArmToBodyDistance = kotlin.math.abs(leftElbow.position.x - leftShoulder.position.x)
            val rightArmToBodyDistance = kotlin.math.abs(rightElbow.position.x - rightShoulder.position.x)
            val armsCloseToBody = leftArmToBodyDistance < 200f && rightArmToBodyDistance < 200f // Ultra lenient

            // B. Wrists positioned reasonably (ultra lenient)
            val leftWristToElbowHorizontal = kotlin.math.abs(leftWrist.position.x - leftElbow.position.x)
            val rightWristToElbowHorizontal = kotlin.math.abs(rightWrist.position.x - rightElbow.position.x)
            val wristsNearElbows = leftWristToElbowHorizontal < 180f && rightWristToElbowHorizontal < 180f // Ultra lenient

            // C. Basic shoulder check (ultra lenient)
            val shoulderToElbowDiff = avgShoulderY - avgElbowY
            val shouldersNotRaised = shoulderToElbowDiff < 200f // Ultra lenient

            // D. Basic arm synchronization (ultra lenient)
            val armSynchronization = kotlin.math.abs(leftWristToElbowDiff - rightWristToElbowDiff) < 80f // Ultra lenient

            // E. Basic elbow position check (ultra lenient)
            val bodyCenter = (leftShoulder.position.x + rightShoulder.position.x) / 2
            val leftElbowFromCenter = kotlin.math.abs(leftElbow.position.x - bodyCenter)
            val rightElbowFromCenter = kotlin.math.abs(rightElbow.position.x - bodyCenter)
            val elbowsNearCenter = leftElbowFromCenter < 250f && rightElbowFromCenter < 250f // Ultra lenient

            // F. Basic overhead prevention (ultra lenient - only extreme cases)
            val wristsNotOverhead = leftWrist.position.y > (leftShoulder.position.y - 80f) && rightWrist.position.y > (rightShoulder.position.y - 80f) // Allow more overhead

            // COMPREHENSIVE LATERAL RAISE DETECTION: Catch all stages of lateral movement

            // 1. Arms becoming horizontal (any stage)
            val leftArmHorizontal = kotlin.math.abs(leftWrist.position.y - leftShoulder.position.y) < 80f &&
                    kotlin.math.abs(leftElbow.position.y - leftShoulder.position.y) < 80f
            val rightArmHorizontal = kotlin.math.abs(rightWrist.position.y - rightShoulder.position.y) < 80f &&
                    kotlin.math.abs(rightElbow.position.y - rightShoulder.position.y) < 80f

            // 2. Arms extending outward (even partial)
            val leftArmWide = kotlin.math.abs(leftElbow.position.x - leftShoulder.position.x) > 80f
            val rightArmWide = kotlin.math.abs(rightElbow.position.x - rightShoulder.position.x) > 80f

            // 3. Elbows rising above certain level (lateral raise signature)
            val leftElbowRaised = leftElbow.position.y < (leftShoulder.position.y + 30f)
            val rightElbowRaised = rightElbow.position.y < (rightShoulder.position.y + 30f)

            // 4. Wrists moving away from body center (horizontal spread) - reuse bodyCenter
            val leftWristFromCenter = kotlin.math.abs(leftWrist.position.x - bodyCenter)
            val rightWristFromCenter = kotlin.math.abs(rightWrist.position.x - bodyCenter)
            val wristsSpread = leftWristFromCenter > 120f && rightWristFromCenter > 120f

            // COMBINED LATERAL RAISE DETECTION - Any of these indicates lateral raise
            val isLateralRaise = (leftArmHorizontal && rightArmHorizontal) ||
                    (leftArmWide && rightArmWide) ||
                    (leftElbowRaised && rightElbowRaised && wristsSpread) ||
                    (!armsCloseToBody && !elbowsNearCenter && wristsSpread)

            // HAMMER CURL VALIDATION: Strict requirements for proper form
            val isValidHammerCurlMovement = (
                    armsCloseToBody &&
                            wristsNearElbows &&
                            shouldersNotRaised &&
                            bothArmsUsed &&
                            bothArmsMoving && // NEW: Both arms must be moving together
                            !singleArmDetected && // NEW: Block single arm movements
                            armsMovingSynchronously && // NEW: Arms must move in sync
                            isUpright &&
                            wristsNotOverhead && // Critical: prevent elbow raise confusion
                            !isLateralRaise && // Critical: prevent lateral raise
                            elbowsNearCenter // Elbows must stay close to body
                    )

            // Anti-elbow-raise validation: Ensure this is NOT just elbow raising OR lateral raise
            val isNotElbowRaise = (
                    // Wrists must move significantly relative to elbows (not just elbows moving up)
                    kotlin.math.abs(leftWristToElbowDiff) > 5f && kotlin.math.abs(rightWristToElbowDiff) > 5f &&
                            // Shoulders should not move up significantly (elbows staying relatively stable)
                            shouldersNotRaised &&
                            // Elbows should stay reasonably close to body (not flaring out like lateral raise)
                            elbowsNearCenter &&
                            // NOT a lateral raise movement
                            !isLateralRaise
                    )

            // Combined validation: Must be valid hammer curl AND not elbow raise AND not lateral raise
            val isBasicValidMovement = (
                    bothArmsUsed &&
                            bothArmsMoving && // NEW: Both arms must be moving together
                            !singleArmDetected && // NEW: Block single arm movements
                            armsMovingSynchronously && // NEW: Arms must move in sync
                            isUpright &&
                            avgConfidence > 0.3f &&
                            isNotElbowRaise && // Critical addition
                            !isLateralRaise && // Critical: block lateral raise
                            armsCloseToBody // Basic requirement: arms must be close to body
                    )

            // TEMPORARILY calculate smoothed diff for position detection
            val tempHistory = shoulderAngleHistory.toMutableList()
            tempHistory.add(wristToElbowDiff)
            if (tempHistory.size > 7) {
                tempHistory.removeAt(0)
            }
            val smoothedDiff = if (tempHistory.isNotEmpty()) tempHistory.average().toFloat() else wristToElbowDiff

            // HAMMER CURL POSITION DETECTION with STRICT validation:
            // Only count if it's a valid hammer curl movement

            // HAMMER CURL SPECIFIC: Focus on wrist-to-elbow movement only
            val isLoweredPosition = (
                    smoothedDiff > 10f && // Very sensitive - wrists clearly below elbows
                            bothArmsUsed && bothArmsMoving && !singleArmDetected && armsMovingSynchronously && avgConfidence > 0.3f // Strict requirements
                    )

            val isRaisedPosition = (
                    smoothedDiff <= 10f && // Wrists at or slightly above elbow level
                            bothArmsUsed && bothArmsMoving && !singleArmDetected && armsMovingSynchronously && avgConfidence > 0.3f // Strict requirements
                    )

            // Enhanced logging for debugging (reduced frequency)
            if (frameSkipCounter % 15 == 0) { // More frequent logging for debugging
                Log.d(TAG, "Hammer Curl Debug:")
                Log.d(TAG, "  - Smoothed Diff: ${String.format("%.1f", smoothedDiff)}")
                Log.d(TAG, "  - Left W-E Diff: ${String.format("%.1f", leftWristToElbowDiff)}, Right W-E Diff: ${String.format("%.1f", rightWristToElbowDiff)}")
                Log.d(TAG, "  - Valid Movement: $isValidHammerCurlMovement, Basic Valid: $isBasicValidMovement")
                Log.d(TAG, "  - NOT Elbow Raise: $isNotElbowRaise, Lateral Raise: $isLateralRaise")
                Log.d(TAG, "  - Arms Horizontal: L$leftArmHorizontal/R$rightArmHorizontal, Wide: L$leftArmWide/R$rightArmWide")
                Log.d(TAG, "  - Elbows Raised: L$leftElbowRaised/R$rightElbowRaised, Wrists Spread: $wristsSpread")
                Log.d(TAG, "  - Arms Close: $armsCloseToBody, Wrists Near: $wristsNearElbows")
                Log.d(TAG, "  - Shoulders OK: $shouldersNotRaised, Elbows Center: $elbowsNearCenter")
                Log.d(TAG, "  - Wrists Down: $wristsNotOverhead, Both Arms: $bothArmsUsed")
                Log.d(TAG, "  - Arms Moving: L$leftArmMoving/R$rightArmMoving, Both Moving: $bothArmsMoving, Single: $singleArmDetected")
                Log.d(TAG, "  - Arm Sync: Diff=${String.format("%.1f", armMovementDifference)}, Synchronized: $armsMovingSynchronously")
                Log.d(TAG, "  - Confidence: ${String.format("%.2f", avgConfidence)}")
                Log.d(TAG, "  - Positions - Lowered: $isLoweredPosition, Raised: $isRaisedPosition")
                Log.d(TAG, "  - States: R$isRaised/L$hasBeenLowered, Frames: R$raisedFrameCount/L$loweredFrameCount")
                Log.d(TAG, "  - Can Count: ${isValidHammerCurlMovement || (isBasicValidMovement && isNotElbowRaise)}")
            }

            // CRITICAL: Reset ALL detection state immediately if invalid movement detected
            if (singleArmDetected || !bothArmsMoving || !armsMovingSynchronously) {
                raisedFrameCount = 0
                loweredFrameCount = 0
                shoulderAngleHistory.clear() // CRITICAL: Clear history to prevent smoothed detection
                isRaised = false // Reset raised state
                lastCountedRaise = false // Reset counting state
                // Don't reset hasBeenLowered - keep exercise state but prevent counting
                Log.d(TAG, "🚫 BLOCKED: Invalid movement detected - FULL STATE RESET")
                Log.d(TAG, "    Single arm: $singleArmDetected, Both moving: $bothArmsMoving, Synchronized: $armsMovingSynchronously")
                Log.d(TAG, "    Cleared history, reset raised state and count flags")
            }
            // Fixed state machine - more reliable counting
            else when {
                isLoweredPosition -> {
                    loweredFrameCount++
                    raisedFrameCount = 0

                    // ONLY add to history if valid movement
                    shoulderAngleHistory.add(wristToElbowDiff)
                    if (shoulderAngleHistory.size > 7) {
                        shoulderAngleHistory.removeAt(0)
                    }

                    if (loweredFrameCount >= MIN_STABLE_FRAMES) {
                        if (!hasBeenLowered) {
                            hasBeenLowered = true
                            val validationStatus = if (isValidHammerCurlMovement) "validated" else "detected"
                            Log.d(TAG, "✅ LOWERED position established ($validationStatus)")
                        }

                        if (isRaised) {
                            isRaised = false
                            lastCountedRaise = false // Reset counting flag when going down
                            Log.d(TAG, "RAISED → LOWERED transition - ready for next count")
                        }
                    }
                }

                isRaisedPosition -> {
                    raisedFrameCount++
                    loweredFrameCount = 0

                    // ONLY add to history if valid movement
                    shoulderAngleHistory.add(wristToElbowDiff)
                    if (shoulderAngleHistory.size > 7) {
                        shoulderAngleHistory.removeAt(0)
                    }

                    // SIMPLIFIED COUNTING: Just check basic requirements
                    if (raisedFrameCount >= MIN_STABLE_FRAMES && hasBeenLowered && !lastCountedRaise) {
                        // Try validated movement first, then basic movement with anti-elbow-raise check
                        val canCount = isValidHammerCurlMovement || (isBasicValidMovement && isNotElbowRaise)

                        if (canCount) {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastHammerCurlTime >= MIN_HAMMER_CURL_INTERVAL) {
                                // Don't count reps during rest period
                                if (!isRestPeriod) {
                                    hammerCurlCount++
                                    isRaised = true
                                    lastCountedRaise = true
                                    lastHammerCurlTime = currentTime

                                // Play sound feedback in background thread
                                backgroundExecutor.execute {
                                    try {
                                        val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                                        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                                        toneGen.release()
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to play sound: ${e.message}")
                                    }
                                }

                                    val countType = if (isValidHammerCurlMovement) "validated" else "basic (not elbow raise)"
                                    Log.d(TAG, "🎉 HAMMER CURL COUNTED! Total: $hammerCurlCount ($countType)")

                                    updateHammerCurlCounter()
                                }
                            } else {
                                Log.d(TAG, "⏰ Count blocked by time interval (${currentTime - lastHammerCurlTime}ms < ${MIN_HAMMER_CURL_INTERVAL}ms)")
                            }
                        } else {
                            // Position detected but not validated
                            if (isLateralRaise) {
                                Log.d(TAG, "⚠️ BLOCKED: Detected lateral raise instead of hammer curl")
                            } else if (!isNotElbowRaise) {
                                Log.d(TAG, "⚠️ BLOCKED: Detected wrong exercise (elbow raise or other)")
                            } else {
                                Log.d(TAG, "⚠️ RAISED position detected but movement not validated")
                            }
                        }
                    } else {
                        // Debug why counting is not happening
                        Log.d(TAG, "🔍 Count conditions: frames=$raisedFrameCount>=$MIN_STABLE_FRAMES, lowered=$hasBeenLowered, notCounted=${!lastCountedRaise}")
                    }
                }

                else -> {
                    // Don't reset frame counters too aggressively - only if confidence is very low
                    if (avgConfidence < 0.3f) {
                        raisedFrameCount = 0
                        loweredFrameCount = 0
                        Log.d(TAG, "🔄 Reset frame counters due to low confidence")
                    }
                }
            }

            // Enhanced UI updates with detailed cautions and exercise validation (reduced frequency)
            if (frameSkipCounter % 15 == 0) { // Update UI less frequently
                mainHandler.post {
                    when {
                        singleArmDetected -> {
                            // PRIORITY: Single arm detection - highest priority warning
                            val movingArm = if (leftArmMoving && !rightArmMoving) "LEFT" else "RIGHT"
                            binding.tvPositionStatus.text = "❌ ERROR: Use BOTH Arms!"
                            binding.tvPoseStatus.text = "Only $movingArm arm is moving. Hammer curls require BOTH arms together!"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@DumbbellHammerCurlActivity, android.R.color.holo_red_dark))
                        }
                        !bothArmsUsed -> {
                            if (!leftArmVisible && !rightArmVisible) {
                                binding.tvPositionStatus.text = "⚠️ CAUTION: Use Both Arms"
                                binding.tvPoseStatus.text = "Both arms not detected clearly"
                            } else if (!leftArmVisible) {
                                binding.tvPositionStatus.text = "⚠️ CAUTION: Use Both Arms"
                                binding.tvPoseStatus.text = "Left arm not detected clearly"
                            } else if (!rightArmVisible) {
                                binding.tvPositionStatus.text = "⚠️ CAUTION: Use Both Arms"
                                binding.tvPoseStatus.text = "Right arm not detected clearly"
                            } else {
                                binding.tvPositionStatus.text = "⚠️ CAUTION: Use Both Arms"
                                binding.tvPoseStatus.text = "Arms not clearly visible"
                            }
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@DumbbellHammerCurlActivity, android.R.color.holo_red_dark))
                        }
                        bothArmsUsed && !bothArmsMoving -> {
                            binding.tvPositionStatus.text = "⚠️ ERROR: Move BOTH Arms!"
                            binding.tvPoseStatus.text = "Both arms must move together for hammer curls. Only one or no arms are moving!"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@DumbbellHammerCurlActivity, android.R.color.holo_red_dark))
                        }
                        bothArmsUsed && bothArmsMoving && !armsMovingSynchronously -> {
                            binding.tvPositionStatus.text = "⚠️ ERROR: Arms Not Synchronized!"
                            binding.tvPoseStatus.text = "Both arms must move together at the same time and speed!"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@DumbbellHammerCurlActivity, android.R.color.holo_red_dark))
                        }
                        bothArmsUsed && !armSynchronization -> {
                            binding.tvPositionStatus.text = "⚠️ CAUTION: Sync Arms"
                            binding.tvPoseStatus.text = "Move both arms together in sync"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@DumbbellHammerCurlActivity, android.R.color.holo_red_light))
                        }
                        bothArmsUsed && !armsCloseToBody -> {
                            binding.tvPositionStatus.text = "⚠️ CAUTION: Wrong Exercise"
                            binding.tvPoseStatus.text = "Keep arms close to body (not front raise)"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@DumbbellHammerCurlActivity, android.R.color.holo_red_light))
                        }
                        bothArmsUsed && !wristsNearElbows -> {
                            binding.tvPositionStatus.text = "⚠️ CAUTION: Wrong Exercise"
                            binding.tvPoseStatus.text = "Keep wrists near elbows (not front raise)"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@DumbbellHammerCurlActivity, android.R.color.holo_red_light))
                        }
                        bothArmsUsed && !shouldersNotRaised -> {
                            binding.tvPositionStatus.text = "⚠️ CAUTION: Wrong Exercise"
                            binding.tvPoseStatus.text = "Don't raise shoulders (not military press)"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@DumbbellHammerCurlActivity, android.R.color.holo_red_light))
                        }
                        bothArmsUsed && !elbowsNearCenter -> {
                            binding.tvPositionStatus.text = "⚠️ CAUTION: Wrong Exercise"
                            binding.tvPoseStatus.text = "Keep elbows close to body (not lateral raise)"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@DumbbellHammerCurlActivity, android.R.color.holo_red_light))
                        }
                        bothArmsUsed && !wristsNotOverhead -> {
                            binding.tvPositionStatus.text = "⚠️ CAUTION: Wrong Exercise"
                            binding.tvPoseStatus.text = "Keep wrists below shoulders (not overhead press)"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@DumbbellHammerCurlActivity, android.R.color.holo_red_light))
                        }
                        isRaisedPosition && (isValidHammerCurlMovement || (isBasicValidMovement && isNotElbowRaise)) -> {
                            val statusText = if (isValidHammerCurlMovement) "Position: CURLED ✓" else "Position: CURLED (Basic) ✓"
                            binding.tvPositionStatus.text = statusText
                            binding.tvPoseStatus.text = "Lower both arms to complete rep"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@DumbbellHammerCurlActivity, android.R.color.holo_blue_light))
                        }
                        isRaisedPosition && isLateralRaise -> {
                            binding.tvPositionStatus.text = "⚠️ LATERAL RAISE DETECTED"
                            binding.tvPoseStatus.text = "Keep arms close to body for hammer curls!"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@DumbbellHammerCurlActivity, android.R.color.holo_red_dark))
                        }
                        isRaisedPosition && !isNotElbowRaise -> {
                            binding.tvPositionStatus.text = "⚠️ WRONG EXERCISE DETECTED"
                            binding.tvPoseStatus.text = "Use wrists, not just elbows!"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@DumbbellHammerCurlActivity, android.R.color.holo_red_dark))
                        }
                        isRaisedPosition -> {
                            binding.tvPositionStatus.text = "Position: CURLED (⚠️ Check Form)"
                            binding.tvPoseStatus.text = "Make sure both arms are visible and moving"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@DumbbellHammerCurlActivity, android.R.color.holo_orange_light))
                        }
                        isLoweredPosition && (isValidHammerCurlMovement || (isBasicValidMovement && isNotElbowRaise)) -> {
                            val statusText = if (isValidHammerCurlMovement) "Position: DOWN ✓" else "Position: DOWN (Basic) ✓"
                            binding.tvPositionStatus.text = statusText
                            binding.tvPoseStatus.text = "Curl both arms up together"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@DumbbellHammerCurlActivity, android.R.color.holo_green_light))
                        }
                        isLoweredPosition -> {
                            binding.tvPositionStatus.text = "Position: DOWN (⚠️ Check Form)"
                            binding.tvPoseStatus.text = "Make sure both arms are visible and moving"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@DumbbellHammerCurlActivity, android.R.color.holo_orange_light))
                        }
                        hasBeenLowered && (isValidHammerCurlMovement || isBasicValidMovement) -> {
                            binding.tvPositionStatus.text = "Position: Ready to curl"
                            binding.tvPoseStatus.text = "Bring both dumbbells to shoulders"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@DumbbellHammerCurlActivity, android.R.color.holo_green_light))
                        }
                        else -> {
                            binding.tvPositionStatus.text = "Position: Ready"
                            binding.tvPoseStatus.text = "Lower both arms to start hammer curls"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@DumbbellHammerCurlActivity, android.R.color.holo_orange_light))
                        }
                    }
                }
            }
        }
    }

    private fun resetHammerCurlCounter() {
        hammerCurlCount = 0
        isRaised = false
        hasBeenLowered = false
        lastCountedRaise = false
        raisedFrameCount = 0
        loweredFrameCount = 0
        shoulderAngleHistory.clear()
        lastHammerCurlTime = 0L

        mainHandler.post {
            binding.tvHammerCurlCounter.text = getString(R.string.hammer_curl_counter_text, 0)
            binding.tvPositionStatus.text = "Position: Ready"
            binding.tvPoseStatus.text = ""
            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
        }

        Log.d(TAG, "Hammer curl counter reset")
    }

    private fun updateHammerCurlCounter() {
        binding.tvHammerCurlCounter.text = "Hammer Curls: ${hammerCurlCount}/${targetReps}"
        // Early set completion check
        if (!isRestPeriod && hammerCurlCount >= targetReps) {
            countDownTimer?.cancel()
            onSetComplete()
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
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
                    onRestComplete()
                } else {
                    onSetComplete()
                }
            }
        }.start()
    }

    private fun onSetComplete() {
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
            Toast.makeText(this, "✅ Set $currentSet completed! Rest for ${REST_TIME_SECONDS}s", Toast.LENGTH_SHORT).show()
            startRestPeriod()
        } else {
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
        resetHammerCurlCounter()
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
            when {
                millisUntilFinished <= 30000 -> {
                    binding.tvTimeLabel.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
                }
                millisUntilFinished <= 60000 -> {
                    binding.tvTimeLabel.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
                }
                else -> {
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
        poseDetector.close()
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