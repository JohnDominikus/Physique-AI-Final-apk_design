package com.example.physiqueaiapkfinal

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var loadingText: TextView
    private var progress = 0
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val logo: ImageView = findViewById(R.id.logoImage)
        progressBar = findViewById(R.id.progressBar)
        loadingText = findViewById(R.id.loadingText)

        progressBar.max = 100
        progressBar.progress = 0

        startLoadingAnimation()
    }

    private fun startLoadingAnimation() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (progress < 100) {
                    progress += 2
                    progressBar.progress = progress
                    loadingText.text = "Loading... $progress%"
                    handler.postDelayed(this, 20)  // Smooth and fast
                } else {
                    launchNextScreen()
                }
            }
        }, 20)
    }

    private fun launchNextScreen() {
        val targetActivity = try {
            val name = intent.getStringExtra("TARGET_ACTIVITY")
            if (!name.isNullOrEmpty()) Class.forName(name) else DashboardActivity::class.java
        } catch (e: Exception) {
            DashboardActivity::class.java
        }

        val intent = Intent(this, targetActivity)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
