@file:OptIn(androidx.camera.core.ExperimentalGetImage::class)

package com.example.physiqueaiapkfinal

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
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
    private val mainHandler = Handler(Looper.getMainLooper())

    // Ultra-accurate front raise detection variables
    private var isRaised = false
    private var lastFrontRaiseTime = 0L
    private val MIN_FRONT_RAISE_INTERVAL = 1000L // Minimum time between reps

    // Advanced detection variables
    private var raisedFrameCount = 0
    private var loweredFrameCount = 0
    private val MIN_STABLE_FRAMES = 6 // Reduced for more responsive detection

    // Simplified history tracking for front raise detection
    private val shoulderAngleHistory = mutableListOf<Float>()

    // Orientation tracking
    private var lastRotation = Surface.ROTATION_0

    // Performance optimization variables
    private var frameSkipCounter = 0
    private val FRAME_SKIP_COUNT = 1 // Reduce skip for better accuracy

    // Camera switching variables
    private var isUsingFrontCamera = false
    private var cameraProvider: ProcessCameraProvider? = null

    // Ultra-accurate front raise detection with multiple criteria
    private var hasBeenLowered = false // Track if arms have been in lowered position first
    private var lastCountedRaise = false // Track if we just counted to prevent double counting

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDumbbellFrontRaiseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Log workout data passed from WorkoutPoseAIFragment
        val workoutId = intent.getStringExtra("workoutId")
        val workoutName = intent.getStringExtra("workout_name")
        Log.d(TAG, "DumbbellFrontRaiseActivity started - Workout ID: $workoutId, Workout Name: $workoutName")

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
        binding.tvFrontRaiseCounter.text = getString(R.string.front_raise_counter_text, 0)

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
            val armsVisible = leftWrist.inFrameLikelihood > 0.3f && rightWrist.inFrameLikelihood > 0.3f

            // Add to history for smoothing
            shoulderAngleHistory.add(wristToShoulderDiff)
            if (shoulderAngleHistory.size > 8) {
                shoulderAngleHistory.removeAt(0)
            }

            val smoothedDiff = shoulderAngleHistory.average().toFloat()

            // ULTRA SIMPLE POSITION DETECTION:
            // Positive values = wrists below shoulders (lowered)
            // Near zero values = wrists at shoulder level (raised)
            // Negative values = wrists above shoulders (over-raised)

            val isLoweredPosition = (
                    smoothedDiff > 50f && // Wrists clearly below shoulders (large positive value)
                            isUpright && armsVisible && avgConfidence > 0.5f
                    )

            val isRaisedPosition = (
                    smoothedDiff >= -20f && smoothedDiff <= 50f && // Wrists at/near shoulder level
                            isUpright && armsVisible && avgConfidence > 0.5f
                    )

            val isOverRaised = (
                    smoothedDiff < -20f && // Wrists clearly above shoulders (negative value)
                            isUpright && armsVisible && avgConfidence > 0.5f
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
            Log.d(TAG, "DETECTION RANGES:")
            Log.d(TAG, "  LOWERED: >50 | RAISED: -20 to 50 | OVER-RAISED: <-20")
            Log.d(TAG, "OTHER CHECKS:")
            Log.d(TAG, "  Is Upright: $isUpright, Arms Visible: $armsVisible")
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

                // NEW LOGIC: Count immediately when first reaching shoulder level from lowered position
                if (!isRaised && hasBeenLowered && !lastCountedRaise && raisedFrameCount >= 2) {
                    // Complete rep: lowered → shoulder level (count immediately!)
                    val currentTime = System.currentTimeMillis()

                    if (currentTime - lastFrontRaiseTime >= MIN_FRONT_RAISE_INTERVAL) {
                        frontRaiseCount++
                        lastFrontRaiseTime = currentTime
                        lastCountedRaise = true

                        Log.d(TAG, "🎉 FRONT RAISE #$frontRaiseCount COUNTED! Lowered → Shoulder Level")

                        mainHandler.post {
                            binding.tvFrontRaiseCounter.text = getString(R.string.front_raise_counter_text, frontRaiseCount)
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

                // Set lowered state when stable
                if (loweredFrameCount >= MIN_STABLE_FRAMES) {
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

            // UI updates - check if we need to update display
            if ((frameSkipCounter % 15 == 0)) {
                mainHandler.post {
                    when {
                        isRaised -> {
                            binding.tvPositionStatus.text = "Position: RAISED ✓ - Lower Arms"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@DumbbellFrontRaiseActivity, android.R.color.holo_blue_light))
                        }
                        lastCountedRaise -> {
                            binding.tvPositionStatus.text = "Position: LOWERED ✓ - Great Job!"
                            binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@DumbbellFrontRaiseActivity, android.R.color.holo_green_light))
                        }
                        hasBeenLowered -> {
                            if (isOverRaised) {
                                binding.tvPositionStatus.text = "Position: TOO HIGH ⚠️ - Lower to Shoulder Level"
                                binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@DumbbellFrontRaiseActivity, android.R.color.holo_red_light))
                            } else {
                                binding.tvPositionStatus.text = "Position: LOWERED ✓ - Raise Arms Now!"
                                binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@DumbbellFrontRaiseActivity, android.R.color.holo_green_light))
                            }
                        }
                        else -> {
                            if (isOverRaised) {
                                binding.tvPositionStatus.text = "Position: TOO HIGH ⚠️ - Lower to Shoulder Level"
                                binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@DumbbellFrontRaiseActivity, android.R.color.holo_red_light))
                            } else {
                                binding.tvPositionStatus.text = "Position: Ready - Lower Arms First"
                                binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this@DumbbellFrontRaiseActivity, android.R.color.holo_orange_light))
                            }
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

        binding.tvFrontRaiseCounter.text = getString(R.string.front_raise_counter_text, 0)
        binding.tvPositionStatus.text = "Position: Ready - Lower Arms First"
        binding.tvPositionStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))

        Log.d(TAG, "Front raise counter and detection state reset to 0")
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
        backgroundExecutor.shutdown()
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}