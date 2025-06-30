@file:OptIn(androidx.camera.core.ExperimentalGetImage::class)

package com.example.physiqueaiapkfinal

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
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
    private var isUsingFrontCamera = false
    private var cameraProvider: ProcessCameraProvider? = null

    // Accurate sit-up detection with multiple criteria
    private var hasBeenDown = false // Track if person has been in down position first
    private var lastCountedUp = false // Track if we just counted to prevent double counting

    companion object {
        private const val TAG = "SitUpsActivity"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val REQUEST_CODE_PERMISSIONS = 10
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySitUpsBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
        binding.tvSitUpCounter.text = getString(R.string.sit_up_counter_text, 0)

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
        sitUpCount++
        binding.tvSitUpCounter.text = getString(R.string.sit_up_counter_text, sitUpCount)
        Log.d(TAG, "Sit-up count: $sitUpCount")
    }

    private fun resetSitUpCounter() {
        sitUpCount = 0
        resetPoseDetectionState()
        binding.tvSitUpCounter.text = getString(R.string.sit_up_counter_text, 0)
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

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        backgroundExecutor.shutdown()
        poseDetector.close()
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()) {
            startCamera()
        }
    }

    override fun onPause() {
        super.onPause()
        resetPoseDetectionState()
    }
}