package com.example.physiqueaiapkfinal

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.activity.OnBackPressedCallback

class AboutActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var tvAppName: TextView
    private lateinit var tvVersion: TextView
    private lateinit var tvDescription: TextView
    private lateinit var tvTeamInfo: TextView
    private lateinit var tvFeatures: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)
        
        // Setup modern back press handling
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        initializeViews()
        setupClickListeners()
        loadAboutContent()
    }

    private fun initializeViews() {
        btnBack = findViewById(R.id.btnBack)
        tvAppName = findViewById(R.id.tvAppName)
        tvVersion = findViewById(R.id.tvVersion)
        tvDescription = findViewById(R.id.tvDescription)
        tvTeamInfo = findViewById(R.id.tvTeamInfo)
        tvFeatures = findViewById(R.id.tvFeatures)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            onBackPressed()
        }
    }

    private fun loadAboutContent() {
        tvAppName.text = "Physique AI"
        tvVersion.text = "Version 1.0"
        tvDescription.text = "Your personal fitness companion powered by artificial intelligence. Physique AI revolutionizes your fitness journey with cutting-edge technology and personalized recommendations."
        
        tvTeamInfo.text = """
            🎓 Created by BSCS 4-3
            
            A passionate team of Computer Science students dedicated to bringing innovative fitness technology to everyone.
            
            Our mission is to make fitness accessible, engaging, and effective through the power of AI.
        """.trimIndent()
        
        tvFeatures.text = """
            ✨ Key Features:
            
            🤖 AI-Powered Workout Recommendations
            📱 Real-time Pose Detection & Analysis
            🍽️ Personalized Meal Planning
            📊 Comprehensive Progress Tracking
            🧮 Smart BMI Calculator
            🎯 Goal Setting & Achievement
            📈 Detailed Analytics & Insights
            🔔 Smart Notifications & Reminders
        """.trimIndent()
    }
} 