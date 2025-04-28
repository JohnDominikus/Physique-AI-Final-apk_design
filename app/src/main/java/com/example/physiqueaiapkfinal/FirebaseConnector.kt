package com.example.physiqueaiapkfinal

import android.os.Handler
import android.os.Looper
import com.google.firebase.auth.FirebaseAuth

// --- Firebase Initialization ---
object FirebaseManager {
    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

}

