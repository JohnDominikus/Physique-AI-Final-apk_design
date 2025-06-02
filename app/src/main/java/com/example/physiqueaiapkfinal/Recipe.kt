package com.example.physiqueaiapkfinal

data class Recipe(
    val id: String = "", // Corresponds to the Firestore Document ID
    val mealName: String? = null, // Corresponds to mealName (string)
    val description: String? = null, // Corresponds to description (string)
    val calories: Int? = null, // Corresponds to calories (number)
    val carbs: Int? = null, // Corresponds to carbs (number)
    val fat: Int? = null, // Corresponds to fat (number)
    val protein: Int? = null, // Corresponds to protein (number)
    val allergies: List<String>? = null, // Corresponds to allergies (array)
    val dietaryTags: List<String>? = null, // Corresponds to dietaryTags (array)
    val mealType: String? = null, // Corresponds to mealType (string)
    val ingredients: List<String>? = null, // Corresponds to ingredients (array)
    val instructions: String? = null, // Corresponds to instructions (string)
    val prepTime: String? = null, // Corresponds to prepTime (string)
    val imageUrl: String? = null // Corresponds to imageUrl (string)
)