package com.example.physiqueaiapkfinal

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logo: ImageView = findViewById(R.id.logoImage)
        val progressBar: ProgressBar = findViewById(R.id.progressBar)

        // Start breathing animation
        val scaleUp = ScaleAnimation(
            1f, 1.1f, // Scale from 100% to 110%
            1f, 1.1f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f
        )
        scaleUp.duration = 800
        scaleUp.repeatMode = ScaleAnimation.REVERSE
        scaleUp.repeatCount = ScaleAnimation.INFINITE
        logo.startAnimation(scaleUp)

        // Show progress bar
        progressBar.visibility = ProgressBar.VISIBLE

        // Get the target activity class
        val targetActivity = try {
            Class.forName(intent.getStringExtra("TARGET_ACTIVITY") ?: "")
        } catch (e: ClassNotFoundException) {
            DashboardActivity::class.java
        }

        // Navigate after short delay (faster splash)
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, targetActivity))
            finish()
        }, 1200) // ~1.2 seconds
    }
}
