package com.example.physiqueaiapkfinal

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.physiqueaiapkfinal.databinding.ActivityPostureBinding

class PostureActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPostureBinding
    private var isTimerRunning = false
    private var timer: CountDownTimer? = null
    private var timeLeftInMillis: Long = 60000 // 1 minute timer

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPostureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.ivTimer.setOnClickListener {
            startTimer(binding.tvTimer, binding.ivTimer)
        }

        binding.ivCamera.setOnClickListener {
            captureImage(it)
        }

        // Request camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            startCamera()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview)
            } catch (e: Exception) {
                Log.e("CameraX", "Binding failed", e)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    fun captureImage(view: View) {
        // You can add real capture logic later
        Toast.makeText(this, "Capture clicked!", Toast.LENGTH_SHORT).show()
    }

    private fun startTimer(tvTimer: TextView, ivTimer: ImageView) {
        if (isTimerRunning) {
            timer?.cancel()
            isTimerRunning = false
            ivTimer.setImageResource(R.drawable.timer)
            tvTimer.text = "00:00"
            tvTimer.visibility = View.GONE
        } else {
            timer = object : CountDownTimer(timeLeftInMillis, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    timeLeftInMillis = millisUntilFinished
                    val minutes = (millisUntilFinished / 1000) / 60
                    val seconds = (millisUntilFinished / 1000) % 60
                    tvTimer.text = String.format("%02d:%02d", minutes, seconds)
                }

                override fun onFinish() {
                    isTimerRunning = false
                    ivTimer.setImageResource(R.drawable.timer)
                    tvTimer.visibility = View.GONE
                }
            }.start()

            tvTimer.visibility = View.VISIBLE
            ivTimer.setImageResource(R.drawable.timer) // Change to another icon if you want
            isTimerRunning = true
        }
    }
}
