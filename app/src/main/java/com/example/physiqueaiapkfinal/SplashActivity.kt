package com.example.physiqueaiapkfinal

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.firebase.auth.FirebaseAuth
import kotlin.random.Random

class SplashActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var loadingText: TextView
    private lateinit var progressText: TextView
    private lateinit var appTitle: TextView
    private lateinit var logoCard: CardView
    private lateinit var logoImage: ImageView
    private lateinit var contentContainer: ConstraintLayout
    
    private var progress = 0
    private val handler = Handler(Looper.getMainLooper())
    
    private val loadingPhases = listOf(
        "Initializing AI..." to 0..15,
        "Loading Workouts..." to 15..35,
        "Preparing Nutrition Data..." to 35..55,
        "Setting up Pose Detection..." to 55..75,
        "Connecting to Firebase..." to 75..90,
        "Almost Ready..." to 90..100
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        
        initializeViews()
        setupInitialState()
        startSplashSequence()
    }

    private fun initializeViews() {
        progressBar = findViewById(R.id.progressBar)
        loadingText = findViewById(R.id.loadingText)
        progressText = findViewById(R.id.progressText)
        appTitle = findViewById(R.id.appTitle)
        logoCard = findViewById(R.id.logoCard)
        logoImage = findViewById(R.id.logoImage)
        contentContainer = findViewById(R.id.contentContainer)
    }

    private fun setupInitialState() {
        progressBar.max = 100
        progressBar.progress = 0
        contentContainer.alpha = 0f
        logoCard.scaleX = 0.3f
        logoCard.scaleY = 0.3f
    }

    private fun startSplashSequence() {
        startContentAnimation()
        startLogoAnimation()
        startSmartLoadingAnimation()
    }

    private fun startContentAnimation() {
        val contentFadeIn = ObjectAnimator.ofFloat(contentContainer, "alpha", 0f, 1f)
        contentFadeIn.duration = 1000
        contentFadeIn.interpolator = DecelerateInterpolator()
        contentFadeIn.start()
    }

    private fun startLogoAnimation() {
        val logoScaleX = ObjectAnimator.ofFloat(logoCard, "scaleX", 0.3f, 1.1f, 1f)
        val logoScaleY = ObjectAnimator.ofFloat(logoCard, "scaleY", 0.3f, 1.1f, 1f)
        logoScaleX.duration = 1200
        logoScaleY.duration = 1200
        logoScaleX.interpolator = DecelerateInterpolator()
        logoScaleY.interpolator = DecelerateInterpolator()
        logoScaleX.start()
        logoScaleY.start()
    }

    private fun startSmartLoadingAnimation() {
        handler.postDelayed({
            smartLoadingAlgorithm()
        }, 1500)
    }

    private fun smartLoadingAlgorithm() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (progress < 100) {
                    val progressIncrement = Random.nextInt(1, 4)
                    progress = (progress + progressIncrement).coerceAtMost(100)
                    
                    updateProgressBar(progress)
                    updateLoadingPhase()
                    
                    val nextDelay = Random.nextLong(60, 150)
                    handler.postDelayed(this, nextDelay)
                } else {
                    finishLoading()
                }
            }
        }, 50)
    }

    private fun updateProgressBar(targetProgress: Int) {
        val animator = ObjectAnimator.ofInt(progressBar, "progress", progressBar.progress, targetProgress)
        animator.duration = 300
        animator.start()
        progressText.text = targetProgress.toString() + "%"
    }

    private fun updateLoadingPhase() {
        val currentPhase = loadingPhases.find { (_, range) -> progress in range }
        currentPhase?.let { (phaseText, _) ->
            if (loadingText.text != phaseText) {
                loadingText.text = phaseText
            }
        }
    }

    private fun finishLoading() {
        loadingText.text = "Welcome back, fitness champion! "
        
        handler.postDelayed({
            navigateToNextScreen()
        }, 800)
    }

    private fun navigateToNextScreen() {
        val targetActivity = if (FirebaseAuth.getInstance().currentUser != null) {
            DashboardActivity::class.java
        } else {
            LandingActivity::class.java
        }
        
        val intent = Intent(this, targetActivity)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    @android.annotation.SuppressLint("MissingSuperCall")
    override fun onBackPressed() {
        // Disable back press during splash - do nothing
        // Don't call super.onBackPressed() to prevent going back during splash
    }
}
