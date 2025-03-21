package com.example.physiqueaiapkfinal

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.physiqueaiapkfinal.databinding.ActivityLandingBinding

class LandingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLandingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Use ViewBinding for cleaner code and better performance
        binding = ActivityLandingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Button listeners
        binding.btnLogin.setOnClickListener {
            navigateTo(LoginActivity::class.java)
        }

        binding.btnRegister.setOnClickListener {
            navigateTo(RegisterActivity::class.java)
        }
    }

    // Helper function to navigate to activities
    private fun navigateTo(activity: Class<*>) {
        val intent = Intent(this, activity)
        startActivity(intent)
    }
}
