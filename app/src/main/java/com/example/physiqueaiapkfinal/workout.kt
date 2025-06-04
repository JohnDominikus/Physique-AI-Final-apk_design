package com.example.physiqueaiapkfinal

data class Workout(
    var id: String = "",
    val name: String? = "",
    val description: String? = null,
    val difficulty: String? = null,
    val equipment: String? = null,
    val gif_url: String? = null,
    val instructions: String? = null,
    val muscle_groups: String? = null, // Changed from List<String> to String
    val reps: String? = null,
    val safety_warning: String? = null,
    val target: String? = null,
    val thumbmail: String? = null, // Note: you might want to fix this typo to "thumbnail"
    val timer: Int? = null
)