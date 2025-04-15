package com.example.physiqueaiapkfinal

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ProgressBar
import android.widget.ImageView
import android.view.animation.AlphaAnimation
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Initialize the logo animation
        val logo: ImageView = findViewById(R.id.logoImage)
        val fadeIn = AlphaAnimation(0f, 1f)
        fadeIn.duration = 1000 // 1 second fade-in animation
        logo.startAnimation(fadeIn)

        // ProgressBar setup
        val progressBar: ProgressBar = findViewById(R.id.progressBar)

        // Show progress bar
        progressBar.visibility = ProgressBar.VISIBLE

        // Get the target activity from intent extra
        val targetActivityName = intent.getStringExtra("TARGET_ACTIVITY")
        val targetActivity: Class<*> = try {
            Class.forName(targetActivityName ?: "")
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
            DashboardActivity::class.java // Default to DashboardActivity if something goes wrong
        }

        // Simulate loading by delaying the activity transition
        Handler(Looper.getMainLooper()).postDelayed({
            // Navigate to the target activity after the delay
            val intent = Intent(this, targetActivity)
            startActivity(intent)
            finish() // Finish SplashActivity so it doesn't remain in the backstack
        }, 2000) // 3 seconds delay

        // Hide progress bar after 3 seconds (it will disappear just before transitioning)
        Handler(Looper.getMainLooper()).postDelayed({
            progressBar.visibility = ProgressBar.GONE
        }, 2000) // Hide after 3 seconds to ensure it's visible during the full 3 seconds
    }
}
