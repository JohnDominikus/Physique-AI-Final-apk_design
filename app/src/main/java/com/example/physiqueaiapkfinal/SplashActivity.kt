package com.example.physiqueaiapkfinal

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.ScaleAnimation
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logo: ImageView = findViewById(R.id.logoImage)
        val progressBar: ProgressBar = findViewById(R.id.progressBar)
        val loadingText: TextView = findViewById(R.id.loadingText)

        // Animate logo with scale up/down effect
        val scaleUp = ScaleAnimation(
            1f, 1.1f,
            1f, 1.1f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
            ScaleAnimation.RELATIVE_TO_SELF, 0.5f
        )
        scaleUp.duration = 800
        scaleUp.repeatMode = ScaleAnimation.REVERSE
        scaleUp.repeatCount = ScaleAnimation.INFINITE
        logo.startAnimation(scaleUp)

        // Show progress bar and loading text
        progressBar.visibility = ProgressBar.VISIBLE
        loadingText.visibility = TextView.VISIBLE

        // Safely get target activity class from intent extra
        val targetActivityName = intent.getStringExtra("TARGET_ACTIVITY")
        val targetActivity = try {
            if (!targetActivityName.isNullOrEmpty()) {
                Class.forName(targetActivityName)
            } else {
                DashboardActivity::class.java
            }
        } catch (e: ClassNotFoundException) {
            DashboardActivity::class.java
        }

        // Delay splash for 1.2 seconds before launching target activity
        Handler(Looper.getMainLooper()).postDelayed({
            progressBar.visibility = ProgressBar.GONE
            loadingText.visibility = TextView.GONE

            val intent = Intent(this, targetActivity)
            // Clear back stack to prevent returning to splash or looping
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }, 1200)
    }
}
