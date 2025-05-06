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

        progressBar.visibility = ProgressBar.VISIBLE
        loadingText.visibility = TextView.VISIBLE

        val targetActivity = try {
            Class.forName(intent.getStringExtra("TARGET_ACTIVITY") ?: "")
        } catch (e: ClassNotFoundException) {
            DashboardActivity::class.java
        }

        Handler(Looper.getMainLooper()).postDelayed({
            progressBar.visibility = ProgressBar.GONE
            loadingText.visibility = TextView.GONE

            startActivity(Intent(this, targetActivity))
            finish()
        }, 1200)
    }
}
