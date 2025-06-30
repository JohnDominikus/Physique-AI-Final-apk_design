package com.example.physiqueaiapkfinal

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityOptionsCompat
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
        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_splash)
            
            initializeViews()
            setupInitialState()
            startSplashSequence()
            
            // Safety timeout - if something goes wrong, navigate after 6 seconds
            handler.postDelayed({
                try {
                    if (!isFinishing) {
                        Log.w("SplashActivity", "Safety timeout reached, navigating...")
                        navigateToNextScreen()
                    }
                } catch (e: Exception) {
                    Log.e("SplashActivity", "Error in safety timeout", e)
                    finish()
                }
            }, 6000) // 6 second safety timeout
            
        } catch (e: Exception) {
            Log.e("SplashActivity", "Critical error in onCreate", e)
            // Emergency fallback
            try {
                startActivity(Intent(this, LandingActivity::class.java))
                finish()
            } catch (e2: Exception) {
                Log.e("SplashActivity", "Emergency fallback failed", e2)
                finish()
            }
        }
    }

    private fun initializeViews() {
        try {
            progressBar = findViewById(R.id.progressBar)
            loadingText = findViewById(R.id.loadingText)
            progressText = findViewById(R.id.progressText)
            appTitle = findViewById(R.id.appTitle)
            logoCard = findViewById(R.id.logoCard)
            logoImage = findViewById(R.id.logoImage)
            contentContainer = findViewById(R.id.contentContainer)
        } catch (e: Exception) {
            Log.e("SplashActivity", "Error initializing views", e)
            // Emergency fallback
            startActivity(Intent(this, LandingActivity::class.java))
            finish()
        }
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
            try {
                smartLoadingAlgorithm()
            } catch (e: Exception) {
                Log.e("SplashActivity", "Error in smart loading", e)
                // Fallback: navigate immediately
                navigateToNextScreen()
            }
        }, 800) // Reduced from 1500ms to 800ms for faster start
    }

    private fun smartLoadingAlgorithm() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (progress < 100) {
                    // Faster progress for 4-second total duration
                    val progressIncrement = Random.nextInt(3, 8) // Increased increment
                    progress = (progress + progressIncrement).coerceAtMost(100)
                    
                    updateProgressBar(progress)
                    updateLoadingPhase()
                    
                    // Faster delays for quicker loading
                    val nextDelay = Random.nextLong(30, 80) // Reduced delay
                    handler.postDelayed(this, nextDelay)
                } else {
                    finishLoading()
                }
            }
        }, 30) // Reduced initial delay
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
        try {
            loadingText.text = "Welcome back, fitness champion! "
            
            handler.postDelayed({
                try {
                    navigateToNextScreen()
                } catch (e: Exception) {
                    Log.e("SplashActivity", "Error navigating after loading", e)
                    // Emergency fallback
                    startActivity(Intent(this, LandingActivity::class.java))
                    finish()
                }
            }, 400) // Reduced from 800ms to 400ms
        } catch (e: Exception) {
            Log.e("SplashActivity", "Error in finishLoading", e)
            // Emergency fallback
            startActivity(Intent(this, LandingActivity::class.java))
            finish()
        }
    }

    private fun navigateToNextScreen() {
        try {
            // Check if user is authenticated
            val auth = FirebaseAuth.getInstance()
            val currentUser = auth.currentUser
            
            val nextIntent = if (currentUser != null) {
                // User is logged in, go to Dashboard
                Intent(this, DashboardActivity::class.java)
            } else {
                // No user logged in, go to Landing
                Intent(this, LandingActivity::class.java)
            }

            // Use modern activity transition
            val options = ActivityOptionsCompat.makeCustomAnimation(
                this,
                R.anim.slide_in_right,
                R.anim.slide_out_left
            )

            startActivity(nextIntent, options.toBundle())
            finish()
        } catch (e: Exception) {
            Log.e("SplashActivity", "Error navigating to next screen", e)
            // Fallback to simple navigation
            startActivity(Intent(this, LandingActivity::class.java))
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    @android.annotation.SuppressLint("MissingSuperCall")
    override fun onBackPressed() {
        // Disable back press during splash - do nothing
        // Don't call super.onBackPressed() to prevent going back during splash
    }
}
