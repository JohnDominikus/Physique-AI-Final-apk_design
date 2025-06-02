package com.example.physiqueaiapkfinal

import com.google.firebase.firestore.DocumentId

data class TodoEntry(
    @DocumentId val id: String = "", // Firestore Document ID
    val date: String? = null, // e.g., "2023-10-27"
    val time: String? = null, // e.g., "08:00"

    val idealBodyGoal: String? = null, // e.g., "Weight Loss", "Muscle Gain"
    val suggestedFluidIntakeMl: Int? = null, // e.g., 500

    val workouts: List<PlannedWorkout>? = null,
    val meals: List<PlannedMeal>? = null
)

data class PlannedWorkout(
    val exerciseId: String? = null, // Reference to the original Exercise document ID
    val name: String? = null,
    val description: String? = null,
    val gifUrl: String? = null,
    val type: String? = null,       // e.g., "Lifting", "Cardio"
    val repsOrLoops: Int? = null,   // Number of reps or loops for this planned workout
    val restTimeSeconds: Int? = null, // Rest time after this workout
    val target: String? = null,     // Target muscle or goal
    val countdownSeconds: Int? = null // Countdown before starting
)

data class PlannedMeal(
    val recipeId: String? = null, // Reference to the original Recipe document ID
    val name: String? = null,
    val description: String? = null,
    val type: String? = null,       // e.g., "Breakfast", "Lunch", "Snacks"
    val calories: Int? = null,
    val carbs: Int? = null, // Including carbs for completeness, based on Recipe class
    val fat: Int? = null,
    val protein: Int? = null
)