package com.example.physiqueaiapkfinal

import java.util.*

// Shared data classes for the application
data class WorkoutTodo(
    val id: String = UUID.randomUUID().toString(),
    val workoutId: String = "",
    val workoutName: String = "",
    val sets: Int = 0,
    val reps: Int = 0,
    val minutes: Int = 0,
    val seconds: Int = 0,
    val scheduledDate: String = "",
    val isCompleted: Boolean = false,
    val userId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val muscleGroups: List<String> = listOf(),
    val safetyWarning: String = "",
    val estimatedCalories: Int = 0,
    val durationMinutes: Int = 0
)

data class MealTodo(
    val id: String = UUID.randomUUID().toString(),
    val mealId: String = "",
    val mealName: String = "",
    val mealType: String = "",
    val scheduledDate: String = "",
    val scheduledTime: String = "", // e.g., "08:00"
    val isCompleted: Boolean = false,
    val userId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val calories: Int = 0,
    val allergies: List<String> = listOf(),
    val prepTime: Int = 0,
    val imageUrl: String = ""
)

data class UserMedicalInfo(
    val userId: String = "",
    val injuries: List<String> = listOf(),
    val allergies: List<String> = listOf(),
    val medicalConditions: List<String> = listOf(),
    val fitnessLevel: String = ""
) 