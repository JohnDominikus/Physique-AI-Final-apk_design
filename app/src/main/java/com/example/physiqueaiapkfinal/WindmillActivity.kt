@file:OptIn(androidx.camera.core.ExperimentalGetImage::class)

package com.example.physiqueaiapkfinal

import androidx.annotation.OptIn

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
import com.example.physiqueaiapkfinal.databinding.ActivityWindmillBinding
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
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.pow

class WindmillActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWindmillBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var backgroundExecutor: ExecutorService
    private lateinit var poseDetector: com.google.mlkit.vision.pose.PoseDetector
    private var poseClassifierProcessor: PoseClassifierProcessor? = null
    private val TAG = "WindmillActivity"
    private var windmillCount = 0
    private var targetReps: Int = 0
    private val mainHandler = Handler(Looper.getMainLooper())

    // Direct windmill detection variables (hybrid approach)
    private var lastWindmillTime = 0L
    private val MIN_WINDMILL_INTERVAL = 500L // Reduced from 1000L to 500L for better responsiveness
    private var frameSkipCounter = 0
    private val FRAME_SKIP_COUNT = 1 // Reduced for better responsiveness

    // Windmill state tracking - improved
    private var lastArmState = ""
    private var validWindmillFrames = 0
    private val MIN_VALID_FRAMES = 1 // Reduced from 3 to 1 for instant counting
    private var lastDetectedSide = "" // Track which side was last detected

    // Timer functionality
    private var countDownTimer: CountDownTimer? = null
    private var totalTimeInMillis: Long = 0
    private var timeRemaining: Long = 0
    private var totalSets: Int = 0
    private var currentSet: Int = 1
    private var isRestPeriod: Boolean = false
    private val REST_TIME_SECONDS = 20

    // Camera switching variables
    private var isUsingFrontCamera = true
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var lensFacing: Int = CameraSelector.LENS_FACING_FRONT
    private var camera: Camera? = null

    companion object {
        private const val TAG = "WindmillActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWindmillBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Read values from intent and display them
        val sets = intent.getIntExtra("sets", 0)
        val reps = intent.getIntExtra("reps", 0)
        val minutes = intent.getIntExtra("minutes", 0)
        val seconds = intent.getIntExtra("seconds", 0)

        // Store set information
        totalSets = sets
        currentSet = 1

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

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Initialize executors
        cameraExecutor = Executors.newSingleThreadExecutor()
        backgroundExecutor = Executors.newSingleThreadExecutor()

        // Initialize pose detector
        val options = PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
        poseDetector = PoseDetection.getClient(options)

        // Set up UI controls
        setupControls()
    }

    private fun setupControls() {
        // Reset button
        binding.resetButton.setOnClickListener {
            resetCount()
        }

        // Switch camera button
        binding.switchCameraButton.setOnClickListener {
            switchCamera()
        }

        // Update initial UI
        updateCountDisplay()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        val preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

        imageCapture = ImageCapture.Builder().build()

        val imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor) { imageProxy ->
                    processImageProxy(imageProxy)
                }
            }

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageCapture, imageAnalyzer
            )
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    @OptIn(androidx.camera.core.ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        // Frame skipping for better performance
        frameSkipCounter++
        if (frameSkipCounter <= FRAME_SKIP_COUNT) {
            imageProxy.close()
            return
        }
        frameSkipCounter = 0

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            poseDetector.process(image)
                .addOnSuccessListener { pose ->
                    // Fix overlay alignment by using imageProxy dimensions and rotation
                    val sourceInfo = when (imageProxy.imageInfo.rotationDegrees) {
                        90, 270 -> Pair(imageProxy.height, imageProxy.width)
                        else -> Pair(imageProxy.width, imageProxy.height)
                    }
                    processPose(pose, sourceInfo.first, sourceInfo.second, imageProxy.imageInfo.rotationDegrees)
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

    private fun processPose(pose: Pose, width: Int, height: Int, rotation: Int) {
        // Clear overlay first
        runOnUiThread {
            binding.graphicOverlay.clear()

            // Proper overlay alignment setup
            val isImageFlipped = lensFacing == CameraSelector.LENS_FACING_FRONT
            binding.graphicOverlay.setImageSourceInfo(width, height, isImageFlipped)

            // Get all landmarks for pose visualization
            val allPoseLandmarks = pose.allPoseLandmarks

            if (allPoseLandmarks.isEmpty()) {
                binding.statusText.text = "No pose detected"
                binding.statusText.setTextColor(Color.WHITE) // Normal color for instructions
                return@runOnUiThread
            }

            // Add the graphic to the overlay for pose visualization
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

        // Direct windmill detection (simplified and more responsive)
        detectWindmill(pose)
    }

    private fun detectWindmill(pose: Pose) {
        // Get essential landmarks including legs for standing check
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)

        if (leftWrist == null || rightWrist == null || leftShoulder == null ||
            rightShoulder == null || leftHip == null || rightHip == null ||
            leftKnee == null || rightKnee == null || leftAnkle == null || rightAnkle == null) {
            runOnUiThread {
                binding.statusText.text = "Position yourself fully in camera view (feet must be visible)"
                binding.statusText.setTextColor(Color.WHITE) // Normal color for instructions
            }
            return
        }

        // Relaxed confidence threshold
        val minConfidence = 0.4f
        if (leftWrist.inFrameLikelihood < minConfidence || rightWrist.inFrameLikelihood < minConfidence ||
            leftShoulder.inFrameLikelihood < minConfidence || rightShoulder.inFrameLikelihood < minConfidence ||
            leftKnee.inFrameLikelihood < minConfidence || rightKnee.inFrameLikelihood < minConfidence ||
            leftAnkle.inFrameLikelihood < minConfidence || rightAnkle.inFrameLikelihood < minConfidence) {
            runOnUiThread {
                binding.statusText.text = "Move closer - all body parts must be clearly visible"
                binding.statusText.setTextColor(Color.WHITE) // Normal color for instructions
            }
            return
        }

        // Get precise positions
        val leftWristPos = leftWrist.position
        val rightWristPos = rightWrist.position
        val leftShoulderPos = leftShoulder.position
        val rightShoulderPos = rightShoulder.position
        val leftHipPos = leftHip.position
        val rightHipPos = rightHip.position
        val leftKneePos = leftKnee.position
        val rightKneePos = rightKnee.position
        val leftAnklePos = leftAnkle.position
        val rightAnklePos = rightAnkle.position

        val avgShoulderY = (leftShoulderPos.y + rightShoulderPos.y) / 2
        val avgHipY = (leftHipPos.y + rightHipPos.y) / 2
        val avgKneeY = (leftKneePos.y + rightKneePos.y) / 2
        val avgAnkleY = (leftAnklePos.y + rightAnklePos.y) / 2

        // Relaxed standing position check
        val isStanding = avgKneeY > avgHipY + 30 && avgAnkleY > avgKneeY + 20

        if (!isStanding) {
            runOnUiThread {
                binding.statusText.text = "Please STAND UP straight for windmill exercise!"
                binding.statusText.setTextColor(Color.WHITE) // Normal color for instructions
            }
            return
        }

        // T-POSE DETECTION  
        val leftArmExtended = leftWristPos.x < leftShoulderPos.x - 100 &&
                abs(leftWristPos.y - avgShoulderY) < 100
        val rightArmExtended = rightWristPos.x > rightShoulderPos.x + 100 &&
                abs(rightWristPos.y - avgShoulderY) < 100
        val isTpose = leftArmExtended && rightArmExtended

        // PROPER WINDMILL DETECTION - CROSSED HAND PATTERN
        // Right hand must touch LEFT foot area, Left hand must touch RIGHT foot area

        // Define foot positions and touch zones
        val leftFootX = leftAnklePos.x
        val rightFootX = rightAnklePos.x
        val groundLevel = avgAnkleY
        val touchZoneVertical = 180f // Vertical distance tolerance
        val touchZoneHorizontal = 120f // Horizontal distance tolerance for foot area

        // ARM POSITION DETECTION
        val leftArmUp = leftWristPos.y < avgShoulderY // Left arm raised
        val rightArmUp = rightWristPos.y < avgShoulderY // Right arm raised

        // CROSSED HAND DETECTION - This is the key fix!
        // Right hand reaching towards LEFT foot (crossed pattern)
        val rightHandReachingLeftFoot = rightWristPos.y > avgShoulderY + 80 && // Right hand going down
                abs(rightWristPos.x - leftFootX) < touchZoneHorizontal && // Near left foot horizontally
                rightWristPos.y > groundLevel - touchZoneVertical // Near ground level

        // Left hand reaching towards RIGHT foot (crossed pattern) 
        val leftHandReachingRightFoot = leftWristPos.y > avgShoulderY + 80 && // Left hand going down
                abs(leftWristPos.x - rightFootX) < touchZoneHorizontal && // Near right foot horizontally
                leftWristPos.y > groundLevel - touchZoneVertical // Near ground level

        // PROPER WINDMILL PATTERNS - CROSSED HANDS
        val isLeftWindmill = leftArmUp && rightHandReachingLeftFoot // Left up, right hand touches LEFT foot
        val isRightWindmill = rightArmUp && leftHandReachingRightFoot // Right up, left hand touches RIGHT foot

        // ALTERNATE SIDE DETECTION - prevent double counting on same side
        val shouldCountLeft = isLeftWindmill && lastDetectedSide != "left"
        val shouldCountRight = isRightWindmill && lastDetectedSide != "right"

        // Calculate distances for feedback - CROSSED PATTERN
        val rightHandToLeftFootDistance = sqrt((rightWristPos.x - leftFootX).pow(2) + (rightWristPos.y - groundLevel).pow(2))
        val leftHandToRightFootDistance = sqrt((leftWristPos.x - rightFootX).pow(2) + (leftWristPos.y - groundLevel).pow(2))

        val currentTime = System.currentTimeMillis()

        runOnUiThread {
            when {
                isTpose -> {
                    binding.statusText.text = "Perfect T-pose! Now: One arm UP, other arm DOWN to touch ground"
                    binding.statusText.setTextColor(Color.BLUE) // Blue text for T-pose indication
                    if (lastArmState != "tpose") {
                        lastArmState = "tpose"
                        lastDetectedSide = "" // Reset side tracking on T-pose
                    }
                }
                shouldCountLeft -> {
                    binding.statusText.text = "EXCELLENT! Left arm up, right hand touching LEFT foot - COUNTED!"
                    binding.statusText.setTextColor(Color.BLUE) // Blue text for counting indication
                    if (lastArmState != "left_windmill" && currentTime - lastWindmillTime > MIN_WINDMILL_INTERVAL) {
                        incrementCount()
                        lastWindmillTime = currentTime
                        lastArmState = "left_windmill"
                        lastDetectedSide = "left"
                    }
                }
                shouldCountRight -> {
                    binding.statusText.text = "EXCELLENT! Right arm up, left hand touching RIGHT foot - COUNTED!"
                    binding.statusText.setTextColor(Color.BLUE) // Blue text for counting indication
                    if (lastArmState != "right_windmill" && currentTime - lastWindmillTime > MIN_WINDMILL_INTERVAL) {
                        incrementCount()
                        lastWindmillTime = currentTime
                        lastArmState = "right_windmill"
                        lastDetectedSide = "right"
                    }
                }
                isLeftWindmill && lastDetectedSide == "left" -> {
                    binding.statusText.text = "Left windmill detected but already counted - switch to RIGHT side!"
                    binding.statusText.setTextColor(Color.WHITE) // Reset to normal color
                }
                isRightWindmill && lastDetectedSide == "right" -> {
                    binding.statusText.text = "Right windmill detected but already counted - switch to LEFT side!"
                    binding.statusText.setTextColor(Color.WHITE) // Reset to normal color
                }
                leftArmUp && rightHandReachingLeftFoot -> {
                    binding.statusText.text = "Almost there! Left arm up, right hand near LEFT foot (${rightHandToLeftFootDistance.toInt()}px) - keep reaching!"
                    binding.statusText.setTextColor(Color.WHITE) // Reset to normal color
                    lastArmState = "left_reaching"
                }
                rightArmUp && leftHandReachingRightFoot -> {
                    binding.statusText.text = "Almost there! Right arm up, left hand near RIGHT foot (${leftHandToRightFootDistance.toInt()}px) - keep reaching!"
                    binding.statusText.setTextColor(Color.WHITE) // Reset to normal color
                    lastArmState = "right_reaching"
                }
                leftArmUp -> {
                    binding.statusText.text = "Left arm up! Now reach RIGHT hand down to touch LEFT foot (crossed pattern)"
                    binding.statusText.setTextColor(Color.WHITE) // Reset to normal color
                }
                rightArmUp -> {
                    binding.statusText.text = "Right arm up! Now reach LEFT hand down to touch RIGHT foot (crossed pattern)"
                    binding.statusText.setTextColor(Color.WHITE) // Reset to normal color
                }
                rightHandReachingLeftFoot -> {
                    binding.statusText.text = "Right hand reaching LEFT foot (${rightHandToLeftFootDistance.toInt()}px) - now lift LEFT arm up!"
                    binding.statusText.setTextColor(Color.WHITE) // Reset to normal color
                }
                leftHandReachingRightFoot -> {
                    binding.statusText.text = "Left hand reaching RIGHT foot (${leftHandToRightFootDistance.toInt()}px) - now lift RIGHT arm up!"
                    binding.statusText.setTextColor(Color.WHITE) // Reset to normal color
                }
                else -> {
                    val timeLeft = ((MIN_WINDMILL_INTERVAL - (currentTime - lastWindmillTime)) / 1000f).coerceAtLeast(0f)
                    if (timeLeft > 0) {
                        binding.statusText.text = "Wait ${String.format("%.1f", timeLeft)}s before next count. Do T-pose then CROSSED windmill."
                    } else {
                        binding.statusText.text = "T-pose first, then: CROSSED pattern - Left up + Right hand to LEFT foot, OR Right up + Left hand to RIGHT foot"
                        if (currentTime - lastWindmillTime > 3000) {
                            lastArmState = ""
                            lastDetectedSide = "" // Reset after 3 seconds of no detection
                        }
                    }
                    binding.statusText.setTextColor(Color.WHITE) // Reset to normal color
                }
            }
        }

        // Simple and clear debug logging
        Log.d(TAG, "WINDMILL Detection (crossed pattern):")
        Log.d(TAG, "Standing: $isStanding, T-pose: $isTpose")
        Log.d(TAG, "Left arm up: $leftArmUp (Y: ${leftWristPos.y.toInt()}, shoulder: ${avgShoulderY.toInt()})")
        Log.d(TAG, "Right arm up: $rightArmUp (Y: ${rightWristPos.y.toInt()}, shoulder: ${avgShoulderY.toInt()})")
        Log.d(TAG, "Right hand reaching LEFT foot: $rightHandReachingLeftFoot")
        Log.d(TAG, "Left hand reaching RIGHT foot: $leftHandReachingRightFoot")
        Log.d(TAG, "Ground level: ${groundLevel.toInt()}, Touch zone H: ${touchZoneHorizontal.toInt()}px, V: ${touchZoneVertical.toInt()}px")
        Log.d(TAG, "COUNTING - Left windmill: $isLeftWindmill, Right windmill: $isRightWindmill")
        Log.d(TAG, "Distance to feet - Right hand to LEFT foot: ${rightHandToLeftFootDistance.toInt()}px, Left hand to RIGHT foot: ${leftHandToRightFootDistance.toInt()}px")
    }

    private fun incrementCount() {
        // Don't count reps during rest period
        if (!isRestPeriod) {
            windmillCount++
            updateCountDisplay()
            playBeep()
            Log.d(TAG, "Windmill count: $windmillCount")
        }
    }

    private fun playBeep() {
        try {
            val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
        } catch (e: Exception) {
            Log.e(TAG, "Error playing beep: ${e.message}")
        }
    }

    private fun resetCount() {
        windmillCount = 0
        lastArmState = ""
        lastDetectedSide = "" // Reset side tracking
        validWindmillFrames = 0
        lastWindmillTime = 0L // Reset timing
        updateCountDisplay()

        binding.statusText.text = "Ready to detect windmills! Start with T-pose."
        binding.statusText.setTextColor(Color.WHITE) // Reset to normal color
        Log.d(TAG, "Windmill count reset - all states cleared")

        Toast.makeText(this, "Windmill count reset!", Toast.LENGTH_SHORT).show()
    }

    private fun updateCountDisplay() {
        binding.countText.text = "Windmills: ${windmillCount}/${targetReps}"
    }

    private fun switchCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }

        isUsingFrontCamera = lensFacing == CameraSelector.LENS_FACING_FRONT
        bindCameraUseCases()

        val cameraType = if (isUsingFrontCamera) "Front" else "Back"
        Toast.makeText(this, "$cameraType camera", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Switched to $cameraType camera")
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
            binding.tvTimeLabel.setTextColor(Color.GREEN)
            binding.tvSetLabel.text = "🎉 Workout Complete!"
            binding.tvSetLabel.setTextColor(Color.GREEN)
            Toast.makeText(this, "Done!", Toast.LENGTH_LONG).show()
            
            // Remove exercise from dashboard and return
            removeExerciseAndFinish()
        }
    }

    private fun startRestPeriod() {
        isRestPeriod = true
        currentSet++
        resetCount()
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
            binding.tvSetLabel.setTextColor(Color.parseColor("#FFA500")) // Orange
        } else {
            binding.tvSetLabel.text = "Set: $currentSet/$totalSets x $reps"
            binding.tvSetLabel.setTextColor(Color.RED)
        }
    }

    private fun updateTimeDisplay(millisUntilFinished: Long) {
        val minutes = (millisUntilFinished / 1000) / 60
        val seconds = (millisUntilFinished / 1000) % 60
        
        if (isRestPeriod) {
            binding.tvTimeLabel.text = "Rest: ${seconds}s"
            binding.tvTimeLabel.setTextColor(Color.parseColor("#FFA500")) // Orange
        } else {
            binding.tvTimeLabel.text = "Time: ${minutes}m ${seconds}s"
            // Change color as time runs out
            when {
                millisUntilFinished <= 30000 -> { // Last 30 seconds - red
                    binding.tvTimeLabel.setTextColor(Color.RED)
                }
                millisUntilFinished <= 60000 -> { // Last minute - orange
                    binding.tvTimeLabel.setTextColor(Color.parseColor("#FFA500"))
                }
                else -> { // Normal - red
                    binding.tvTimeLabel.setTextColor(Color.RED)
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

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
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