@file:OptIn(androidx.camera.core.ExperimentalGetImage::class)

package com.example.physiqueaiapkfinal

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Vibrator
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
import com.example.physiqueaiapkfinal.databinding.ActivityMilitaryPressBinding
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

class MilitaryPressActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMilitaryPressBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var backgroundExecutor: ExecutorService
    private lateinit var poseDetector: com.google.mlkit.vision.pose.PoseDetector
    private var poseClassifierProcessor: PoseClassifierProcessor? = null
    private val TAG = "MilitaryPressActivity"
    private var militaryPressCount = 0
    private var targetReps: Int = 0
    private val mainHandler = Handler(Looper.getMainLooper())

    // Military press detection variables
    private var isDown = false
    private var lastPressTime = 0L
    private val MIN_PRESS_INTERVAL = 500L // Even faster counting - reduced to 500ms

    // Advanced detection variables
    private var downFrameCount = 0
    private var upFrameCount = 0
    private val MIN_STABLE_FRAMES = 2 // Faster response for continuous counting
    private val MIN_DOWN_HOLD_FRAMES = 2 // Even faster reset for smooth continuous counting

    // Multi-dimensional history tracking for military press detection
    private val shoulderAngleHistory = mutableListOf<Float>()
    private val elbowAngleHistory = mutableListOf<Float>()
    private val armHeightHistory = mutableListOf<Float>()
    private val velocityHistory = mutableListOf<Float>()
    private val HISTORY_SIZE = 10 // History size for smoothing

    // Biomechanical validation for military press
    private var previousArmY = 0f
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

    // Ultra-accurate military press detection with multiple criteria
    private var hasBeenDown = false // Track if person has been in down position first
    private var lastCountedUp = false // Track if we just counted to prevent double counting
    private var stableDownFrames = 0 // Track stable frames in down position
    private var hasResetAfterCount = false // Track if properly reset after counting

    // Timer functionality
    private var countDownTimer: CountDownTimer? = null
    private var totalTimeInMillis: Long = 0
    private var timeRemaining: Long = 0
    private var totalSets: Int = 0
    private var currentSet: Int = 1
    private var isRestPeriod: Boolean = false
    private val REST_TIME_SECONDS = 20

    // Required permissions
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA
    )
    private val REQUEST_CODE_PERMISSIONS = 10

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMilitaryPressBinding.inflate(layoutInflater)
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
        updateMilitaryPressCounter()

        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnReset.setOnClickListener {
            resetPressCounter()
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

        // Clear detection state when switching cameras
        resetDetectionState()
    }

    private fun resetDetectionState() {
        hasBeenDown = false
        lastCountedUp = false
        stableDownFrames = 0
        hasResetAfterCount = false
        downFrameCount = 0
        upFrameCount = 0
        shoulderAngleHistory.clear()
        elbowAngleHistory.clear()
        armHeightHistory.clear()
        velocityHistory.clear()
        motionPhases.clear()
        highConfidenceFrames = 0

        Log.d(TAG, "Detection state reset")
    }

    private fun resetPressCounter() {
        militaryPressCount = 0
        updateMilitaryPressCounter()
        resetDetectionState()

        // Play reset sound
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP2, 150)
            toneGen.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error playing reset sound", e)
        }

        Log.d(TAG, "Dumbbell Shoulder Press counter reset")
        Toast.makeText(this, "Counter reset!", Toast.LENGTH_SHORT).show()
    }

    private inner class PoseAnalyzer : ImageAnalysis.Analyzer {
        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            frameSkipCounter++
            if (frameSkipCounter % (FRAME_SKIP_COUNT + 1) != 0) {
                imageProxy.close()
                return
            }

            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                poseDetector.process(image)
                    .addOnSuccessListener { pose ->
                        processPose(pose, imageProxy)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Pose detection failed", e)
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }

    private fun processPose(pose: Pose, imageProxy: ImageProxy) {
        try {
            val currentTime = System.currentTimeMillis()

            // Optimized frame processing - skip every 3rd frame for balance
            frameSkipCounter++
            if (frameSkipCounter % 3 == 0) { // Process 2 out of 3 frames
                imageProxy.close()
                return
            }

            // Clear overlay and draw pose with proper setup
            binding.graphicOverlay.clear()

            // Set up the overlay with correct image source info
            val imageWidth = imageProxy.width
            val imageHeight = imageProxy.height
            val isImageFlipped = isUsingFrontCamera

            binding.graphicOverlay.setImageSourceInfo(imageWidth, imageHeight, isImageFlipped)

            if (pose.allPoseLandmarks.isNotEmpty()) {
                binding.graphicOverlay.add(
                    PoseGraphic(
                        binding.graphicOverlay,
                        pose,
                        false, // showInFrameLikelihood - disabled to remove numbers and improve performance
                        false, // visualizeZ - disabled for performance
                        false, // rescaleZForVisualization - disabled for performance
                        JavaArrayList<String>() // poseClassification
                    )
                )
            }

            // Skip frequent overlay redraws to maintain FPS
            // binding.graphicOverlay.postInvalidate()

            // Get key landmarks for military press
            val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
            val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
            val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
            val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
            val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
            val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)

            if (leftShoulder != null && rightShoulder != null &&
                leftElbow != null && rightElbow != null &&
                leftWrist != null && rightWrist != null) {

                // Balanced confidence check
                val allLandmarks = listOf(leftShoulder, rightShoulder, leftElbow, rightElbow, leftWrist, rightWrist)
                val avgConfidence = allLandmarks.map { it.inFrameLikelihood }.average().toFloat()

                if (avgConfidence < 0.4f) {
                    Log.d(TAG, "Low confidence: ${(avgConfidence*100).toInt()}%")
                    imageProxy.close()
                    return
                }

                // MILITARY PRESS SPECIFIC DETECTION

                // Calculate wrist positions relative to shoulders (key for military press)
                val leftWristY = leftWrist.position.y
                val rightWristY = rightWrist.position.y
                val leftShoulderY = leftShoulder.position.y
                val rightShoulderY = rightShoulder.position.y

                val avgWristY = (leftWristY + rightWristY) / 2
                val avgShoulderY = (leftShoulderY + rightShoulderY) / 2

                // Key measurement: wrist height relative to shoulders
                val wristToShoulderDiff = avgShoulderY - avgWristY // Positive when wrists above shoulders

                // Check if both arms are visible and being used
                val leftArmVisible = leftWrist.inFrameLikelihood > 0.6f && leftElbow.inFrameLikelihood > 0.6f
                val rightArmVisible = rightWrist.inFrameLikelihood > 0.6f && rightElbow.inFrameLikelihood > 0.6f
                val bothArmsUsed = leftArmVisible && rightArmVisible

                // MILITARY PRESS: Calculate ELBOW ANGLES first
                val leftElbowAngle = calculateAngle(leftShoulder, leftElbow, leftWrist)
                val rightElbowAngle = calculateAngle(rightShoulder, rightElbow, rightWrist)

                // Note: leftWristAboveShoulder and rightWristAboveShoulder will be calculated later

                // SINGLE ARM DETECTION - Very relaxed thresholds
                val leftArmUp = leftElbowAngle > 130f // Left arm extended
                val rightArmUp = rightElbowAngle > 130f // Right arm extended
                val leftArmDown = leftElbowAngle < 120f // Left arm bent
                val rightArmDown = rightElbowAngle < 120f // Right arm bent

                // Single arm usage detection
                val onlyLeftArmUsed = leftArmUp && rightArmDown && leftArmVisible && rightArmVisible
                val onlyRightArmUsed = rightArmUp && leftArmDown && leftArmVisible && rightArmVisible
                val singleArmDetected = onlyLeftArmUsed || onlyRightArmUsed

                // Add to history for smoothing
                shoulderAngleHistory.add(wristToShoulderDiff)
                if (shoulderAngleHistory.size > 7) {
                    shoulderAngleHistory.removeAt(0)
                }

                val smoothedDiff = shoulderAngleHistory.average().toFloat()

                // Calculate how far above shoulders the wrists are (using existing variables)
                val leftWristAboveShoulder = leftShoulderY - leftWristY
                val rightWristAboveShoulder = rightShoulderY - rightWristY
                val avgWristAboveShoulder = (leftWristAboveShoulder + rightWristAboveShoulder) / 2

                // READY POSITION: Arms up at sides with elbows bent (like picture - flexing pose)
                val isReadyPosition = (
                        leftElbowAngle > 60f && leftElbowAngle < 120f && // Elbows bent like in picture
                                rightElbowAngle > 60f && rightElbowAngle < 120f &&
                                leftWristAboveShoulder > -20f && leftWristAboveShoulder < 80f && // Wrists around shoulder level
                                rightWristAboveShoulder > -20f && rightWristAboveShoulder < 80f &&
                                bothArmsUsed && avgConfidence > 0.25f &&
                                !singleArmDetected
                        )

                // TOO HIGH POSITION: Arms extended too high (wrists significantly above head)
                val isTooHighPosition = (
                        leftElbowAngle > 160f && rightElbowAngle > 160f && // Very extended arms
                                leftWristAboveShoulder > 150f && rightWristAboveShoulder > 150f && // Very high above shoulders
                                bothArmsUsed && avgConfidence > 0.2f
                        )

                // SIMPLIFIED form validation - focus on basic overhead position
                val leftWristX = leftWrist.position.x
                val rightWristX = rightWrist.position.x
                val leftShoulderX = leftShoulder.position.x
                val rightShoulderX = rightShoulder.position.x
                val leftElbowX = leftElbow.position.x
                val rightElbowX = rightElbow.position.x

                // Calculate basic distances
                val wristDistance = kotlin.math.abs(leftWristX - rightWristX)
                val elbowDistance = kotlin.math.abs(leftElbowX - rightElbowX)
                val shoulderDistance = kotlin.math.abs(leftShoulderX - rightShoulderX)

                // SUPER RELAXED - almost no shape restrictions
                val isDiamondShape = true // Remove shape restriction completely

                // SUPER RELAXED positioning - almost no restriction
                val isCenteredAboveHead = true // Remove centering restriction

                // Check for reasonable arm positioning - not crossing
                val isNotCrossed = (leftWristX < rightWristX + 50f) // More accurate crossing check

                // IMPROVED form validation - check reasonable positioning
                val isCrossedArms = (leftWristX > rightWristX - 20f) // Check for crossing - left should be clearly left
                val isTooWide = (wristDistance > shoulderDistance * 2.5f) // Too wide apart
                val isTooHigh = false // Remove high detection completely

                val isMilitaryPressForm = (
                        avgConfidence > 0.25f && // Lower confidence requirement
                                !isCrossedArms && // Arms not crossing
                                !isTooWide // Not extremely wide
                        )

                // UP POSITION: SIMPLIFIED - just need arms up and reasonable positioning
                val isUpPosition = (
                        leftElbowAngle > 130f && rightElbowAngle > 130f && // Lower angle requirement
                                leftWristAboveShoulder > 40f && rightWristAboveShoulder > 40f && // Lower height requirement
                                bothArmsUsed && avgConfidence > 0.15f && // Even lower confidence
                                !singleArmDetected
                        // Removed diamond shape and crossing checks temporarily
                        )

                // MINIMAL VALIDATION - Just check basic requirements
                val isValidMilitaryPress = bothArmsUsed && avgConfidence > 0.2f

                // ENHANCED DEBUG logging for troubleshooting
                if (frameSkipCounter % 1 == 0) { // Every frame logging
                    Log.d(TAG, "═══ MILITARY PRESS DEBUG ═══")
                    Log.d(TAG, "💪 ELBOW ANGLES: L=${String.format("%.1f", leftElbowAngle)}° | R=${String.format("%.1f", rightElbowAngle)}°")
                    Log.d(TAG, "📍 READY=$isReadyPosition | UP=$isUpPosition | Valid=$isValidMilitaryPress")
                    Log.d(TAG, "🎯 State: hasBeenDown=$hasBeenDown | lastCountedUp=$lastCountedUp")
                    Log.d(TAG, "⏱️ Frames: down=$downFrameCount | up=$upFrameCount")
                    Log.d(TAG, "✅ BothArms=$bothArmsUsed | SingleArm=$singleArmDetected | Confidence=${String.format("%.2f", avgConfidence)}")
                    Log.d(TAG, "📏 Wrist Heights: L=${String.format("%.1f", leftWristAboveShoulder)}px | R=${String.format("%.1f", rightWristAboveShoulder)}px")

                    // DETAILED UP POSITION CHECK
                    val leftAngleOK = leftElbowAngle > 130f
                    val rightAngleOK = rightElbowAngle > 130f
                    val bothArmsOK = bothArmsUsed
                    val confidenceOK = avgConfidence > 0.15f
                    val singleArmOK = !singleArmDetected
                    val heightOK = leftWristAboveShoulder > 40f && rightWristAboveShoulder > 40f

                    // DIAMOND SHAPE CHECK
                    val diamondOK = wristDistance < shoulderDistance * 1.2f && wristDistance > 20f
                    val wristHeightOK = leftWristAboveShoulder > 60f && rightWristAboveShoulder > 60f
                    val notCrossedOK = !isCrossedArms
                    val ratio = if (shoulderDistance > 0) wristDistance / shoulderDistance else 0f

                    Log.d(TAG, "🔍 UP CHECK: LeftAngle=$leftAngleOK(${String.format("%.1f", leftElbowAngle)}) | RightAngle=$rightAngleOK(${String.format("%.1f", rightElbowAngle)}) | BothArms=$bothArmsOK | Confidence=$confidenceOK(${String.format("%.2f", avgConfidence)})")
                    Log.d(TAG, "📏 HEIGHT: HeightOK=$heightOK | L-wrist=${String.format("%.1f", leftWristAboveShoulder)} | R-wrist=${String.format("%.1f", rightWristAboveShoulder)}")
                    Log.d(TAG, "✅ FINAL: SingleArmOK=$singleArmOK | UP POSITION=$isUpPosition")
                    Log.d(TAG, "🎯 STATE: hasBeenDown=$hasBeenDown | lastCountedUp=$lastCountedUp | Count=$militaryPressCount")

                    if (singleArmDetected) {
                        Log.d(TAG, "⚠️ SINGLE ARM DETECTED: left=$onlyLeftArmUsed, right=$onlyRightArmUsed")
                    }
                    Log.d(TAG, "═══════════════════════════════════")
                }

                // State machine for military press counting
                handleMilitaryPressStateMachine(isUpPosition, isReadyPosition, isValidMilitaryPress, singleArmDetected, isTooHighPosition, currentTime)

                // Update UI with enhanced status
                updatePoseStatusEnhanced(isUpPosition, isReadyPosition, isValidMilitaryPress, isTooHighPosition, isCrossedArms, isTooWide, isTooHigh, smoothedDiff, avgConfidence, pose, upFrameCount)

                highConfidenceFrames++
            } else {
                // Not enough landmarks detected
                mainHandler.post {
                    binding.tvPositionStatus.text = "Position: Invalid - Stand facing camera"
                    binding.tvPositionStatus.setTextColor(resources.getColor(android.R.color.holo_red_light, theme))
                }

                // Reset detection state if landmarks are lost for too long
                highConfidenceFrames = 0
                if (currentTime - lastFrameTime > 2000) { // 2 seconds
                    resetDetectionState()
                }
            }

            lastFrameTime = currentTime

        } catch (e: Exception) {
            Log.e(TAG, "Error processing pose", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun calculateAngle(point1: PoseLandmark, point2: PoseLandmark, point3: PoseLandmark): Float {
        val vector1 = Pair(point1.position.x - point2.position.x, point1.position.y - point2.position.y)
        val vector2 = Pair(point3.position.x - point2.position.x, point3.position.y - point2.position.y)

        val dotProduct = vector1.first * vector2.first + vector1.second * vector2.second
        val magnitude1 = sqrt(vector1.first.pow(2) + vector1.second.pow(2))
        val magnitude2 = sqrt(vector2.first.pow(2) + vector2.second.pow(2))

        val cosAngle = dotProduct / (magnitude1 * magnitude2)
        val clampedCos = cosAngle.coerceIn(-1f, 1f)

        return Math.toDegrees(kotlin.math.acos(clampedCos.toDouble())).toFloat()
    }

    private fun handleMilitaryPressStateMachine(isUpPosition: Boolean, isReadyPosition: Boolean, isValidMilitaryPress: Boolean, singleArmDetected: Boolean, isTooHighPosition: Boolean, currentTime: Long) {
        // FIXED: ENSURE COUNTING ONLY HAPPENS IN UP POSITION
        when {
            isReadyPosition && isValidMilitaryPress -> {
                downFrameCount++
                upFrameCount = 0

                if (downFrameCount >= MIN_STABLE_FRAMES) {
                    stableDownFrames++

                    // Set base position established
                    if (!hasBeenDown) {
                        hasBeenDown = true
                        hasResetAfterCount = true // Ready to count from start
                        Log.d(TAG, "✅ READY position established - Ready to count")
                    }

                    // IMMEDIATE RESET: Reset counting flag when back to ready position
                    if (lastCountedUp && stableDownFrames >= 1) { // Reset after just 1 stable frame
                        lastCountedUp = false
                        Log.d(TAG, "🔄 RESET COMPLETE - Ready for next rep (Count: $militaryPressCount)")
                    }
                }

                // DEBUG: This should NEVER count in ready position
                Log.d(TAG, "📍 In READY position - NO COUNTING here")
            }

            isTooHighPosition && isValidMilitaryPress -> {
                // TOO HIGH WARNING - arms over-extended
                upFrameCount = 0 // Reset count frames
                downFrameCount = 0
                Log.d(TAG, "🚨 TOO HIGH - Arms over-extended! Bring arms down slightly")
            }

            isUpPosition && isValidMilitaryPress -> {
                upFrameCount++
                downFrameCount = 0
                stableDownFrames = 0 // Reset down stability when moving up

                // DEBUG: Show when we're in UP position
                Log.d(TAG, "⬆️ In UP position - CHECKING for count...")

                // CHECK: Must establish ready position first
                if (!hasBeenDown) {
                    Log.d(TAG, "🚨 UP POSITION detected but NO READY POSITION established first - must go to flexing pose!")
                    return // Exit early, don't count
                }

                // FIXED COUNTING: Count only ONCE when first reaching UP position
                if (upFrameCount >= MIN_STABLE_FRAMES && hasBeenDown && !lastCountedUp) {
                    val timeSinceLastPress = currentTime - lastPressTime

                    if (timeSinceLastPress > MIN_PRESS_INTERVAL) {
                        // Don't count reps during rest period
                        if (!isRestPeriod) {
                            militaryPressCount++
                        lastCountedUp = true // Mark as counted - will stay true until they go down
                        lastPressTime = currentTime

                        // Play sound feedback
                        backgroundExecutor.execute {
                            try {
                                val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                                toneGen.release()
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to play sound: ${e.message}")
                            }
                        }

                        Log.d(TAG, "🎉 MILITARY PRESS COUNTED! Total: $militaryPressCount - NOW GO DOWN TO RESET")

                        mainHandler.post {
                            updateMilitaryPressCounter()
                            // Immediately show "Perfect" in GREEN when counted
                            binding.tvPositionStatus.text = "Perfect"
                            binding.tvPoseStatus.text = "Great rep! Lower arms to start position for next"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@MilitaryPressActivity, android.R.color.holo_green_light))
                        }
                        }
                    } else {
                        Log.d(TAG, "⏰ Count blocked by time interval (${timeSinceLastPress}ms < ${MIN_PRESS_INTERVAL}ms)")
                    }
                } else {
                    // Enhanced debugging
                    when {
                        !hasBeenDown -> Log.d(TAG, "⚠️ Must establish base position first")
                        lastCountedUp -> Log.d(TAG, "✅ Already counted this rep - GO DOWN to reset for next count")
                        upFrameCount < MIN_STABLE_FRAMES -> Log.d(TAG, "⚠️ Not enough stable UP frames (${upFrameCount}/${MIN_STABLE_FRAMES})")
                    }
                }
            }

            // Reset frame counters when not in valid position
            else -> {
                Log.d(TAG, "❓ In TRANSITION/INVALID position - no counting")
                if (upFrameCount > 0) upFrameCount--
                if (downFrameCount > 0) downFrameCount--
                if (stableDownFrames > 0) stableDownFrames--
            }
        }
    }

    private fun updatePoseStatusEnhanced(isUpPosition: Boolean, isReadyPosition: Boolean,
                                         isValidMilitaryPress: Boolean, isTooHighPosition: Boolean, isCrossedArms: Boolean, isTooWide: Boolean, isTooHigh: Boolean, smoothedDiff: Float, avgConfidence: Float,
                                         pose: Pose, currentUpFrameCount: Int) {
        // Enhanced UI updates with detailed feedback for single arm detection
        if (frameSkipCounter % 5 == 0) { // Update UI more frequently for better responsiveness
            mainHandler.post {
                // Get the required landmarks for single arm detection using ELBOW ANGLES
                val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
                val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
                val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
                val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
                val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
                val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)

                if (leftShoulder != null && rightShoulder != null && leftElbow != null && rightElbow != null && leftWrist != null && rightWrist != null) {
                    val leftElbowAngle = calculateAngle(leftShoulder, leftElbow, leftWrist)
                    val rightElbowAngle = calculateAngle(rightShoulder, rightElbow, rightWrist)

                    val leftArmUp = leftElbowAngle > 130f && leftWrist.inFrameLikelihood > 0.15f
                    val rightArmUp = rightElbowAngle > 130f && rightWrist.inFrameLikelihood > 0.15f
                    val leftArmDown = leftElbowAngle < 120f && leftWrist.inFrameLikelihood > 0.15f
                    val rightArmDown = rightElbowAngle < 120f && rightWrist.inFrameLikelihood > 0.15f

                    val onlyLeftArmUsed = leftArmUp && rightArmDown
                    val onlyRightArmUsed = rightArmUp && leftArmDown
                    val singleArmDetected = onlyLeftArmUsed || onlyRightArmUsed

                    // COLOR-CODED STATUS SYSTEM as requested by user:
                    // BLUE = Start position (ready)
                    // ORANGE = Go up (transition)
                    // GREEN = Perfect (counted)
                    // RED = Too High (over-extended)
                    // RED = Wrong Form (single arm detection)
                    // RED = Go Back to first position (must establish ready position first)
                    // PRIORITY ORDER: Wrong Form > Too High > Must Go to First Position > Normal Flow
                    when {
                        singleArmDetected && onlyLeftArmUsed -> {
                            binding.tvPositionStatus.text = "Wrong Form"
                            binding.tvPoseStatus.text = "Only LEFT arm detected - Use BOTH arms together"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@MilitaryPressActivity, android.R.color.holo_red_light))
                        }
                        singleArmDetected && onlyRightArmUsed -> {
                            binding.tvPositionStatus.text = "Wrong Form"
                            binding.tvPoseStatus.text = "Only RIGHT arm detected - Use BOTH arms together"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@MilitaryPressActivity, android.R.color.holo_red_light))
                        }
                        // TOO HIGH - RED indication when arms are over-extended
                        isTooHighPosition && isValidMilitaryPress -> {
                            binding.tvPositionStatus.text = "Too High"
                            binding.tvPoseStatus.text = "Lower your arms slightly - don't over-extend"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@MilitaryPressActivity, android.R.color.holo_red_light))
                        }
                        // BLUE - Start position (ready/flexing pose)
                        isReadyPosition && isValidMilitaryPress && !lastCountedUp -> {
                            binding.tvPositionStatus.text = "Start Position"
                            binding.tvPoseStatus.text = "Perfect! Now press arms up to diamond shape"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@MilitaryPressActivity, android.R.color.holo_blue_bright))
                        }
                        // GREEN - Perfect (when movement is counted)
                        isUpPosition && isValidMilitaryPress && lastCountedUp -> {
                            binding.tvPositionStatus.text = "Perfect"
                            binding.tvPoseStatus.text = "Great rep! Lower arms to start position for next"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@MilitaryPressActivity, android.R.color.holo_green_light))
                        }
                        // RED - Must go to first position if trying to do military press without establishing ready position
                        isUpPosition && isValidMilitaryPress && !hasBeenDown -> {
                            binding.tvPositionStatus.text = "Go Back to first position"
                            binding.tvPoseStatus.text = "Start with flexing pose first before pressing up"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@MilitaryPressActivity, android.R.color.holo_red_light))
                        }
                        // ORANGE - Go up (when in transition from ready to up, before counting)
                        // Show this when up position is detected but not enough frames for counting yet
                        isUpPosition && isValidMilitaryPress && !lastCountedUp && hasBeenDown && currentUpFrameCount < MIN_STABLE_FRAMES -> {
                            binding.tvPositionStatus.text = "Go Up"
                            binding.tvPoseStatus.text = "Keep going up to complete diamond shape"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@MilitaryPressActivity, android.R.color.holo_orange_light))
                        }
                        // BLUE - Back to ready position after counting (reset for next rep)
                        isReadyPosition && isValidMilitaryPress && lastCountedUp -> {
                            binding.tvPositionStatus.text = "Start Position"
                            binding.tvPoseStatus.text = "Ready for next rep - press up again"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@MilitaryPressActivity, android.R.color.holo_blue_bright))
                        }
                        // Form issues - keep orange for guidance
                        isReadyPosition && !isValidMilitaryPress -> {
                            binding.tvPositionStatus.text = "Position: Adjusting"
                            binding.tvPoseStatus.text = "Position both arms in flexing pose like the picture"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@MilitaryPressActivity, android.R.color.holo_orange_light))
                        }
                        isUpPosition && !isValidMilitaryPress -> {
                            binding.tvPositionStatus.text = "Position: Adjusting"
                            binding.tvPoseStatus.text = "Ensure both arms move together with good form"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@MilitaryPressActivity, android.R.color.holo_orange_light))
                        }
                        // Default state - neutral orange
                        else -> {
                            binding.tvPositionStatus.text = "Get Ready"
                            binding.tvPoseStatus.text = "Start with flexing pose, then press to diamond shape"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@MilitaryPressActivity, android.R.color.holo_orange_light))
                        }
                    }
                } else {
                    binding.tvPositionStatus.text = "⚠️ ARMS NOT VISIBLE"
                    binding.tvPoseStatus.text = "Make sure both arms are clearly visible to camera"
                    binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@MilitaryPressActivity, android.R.color.holo_red_light))
                }
            }
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
                Toast.makeText(this, "Permissions not granted.", Toast.LENGTH_SHORT).show()
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
        currentSet++ // New set
        resetMilitaryPressCounter()
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

    private fun updateMilitaryPressCounter() {
        binding.tvMilitaryPressCounter.text = "Military Press: ${militaryPressCount}/${targetReps}"
    }

    private fun resetMilitaryPressCounter() {
        militaryPressCount = 0
        updateMilitaryPressCounter()
        resetDetectionState()
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

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
        cameraExecutor.shutdown()
        poseDetector.close()
    }
}