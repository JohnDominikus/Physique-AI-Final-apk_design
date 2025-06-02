package com.example.physiqueaiapkfinal

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast // Import Toast
import com.google.firebase.firestore.FirebaseFirestore // Import FirebaseFirestore

class RecipeInfoFragment : Fragment() {

    private var recipeId: String? = null
    private val db = FirebaseFirestore.getInstance() // Get Firestore instance

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            recipeId = it.getString(ARG_RECIPE_ID)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_recipe_info, container, false)

        // You'll need to fetch and display recipe details here,
        // possibly using the recipeId or receiving the Recipe object
        val caloriesTextView: TextView = view.findViewById(R.id.recipeDetailCalories)
        val carbsTextView: TextView = view.findViewById(R.id.recipeDetailCarbs)
        val fatTextView: TextView = view.findViewById(R.id.recipeDetailFat)
        val proteinTextView: TextView = view.findViewById(R.id.recipeDetailProtein)
        val descriptionTextView: TextView = view.findViewById(R.id.recipeDetailDescription)
        val prepTimeTextView: TextView = view.findViewById(R.id.recipeDetailPrepTime)
        val allergiesTextView: TextView = view.findViewById(R.id.recipeDetailAllergies)
        val dietaryTagsTextView: TextView = view.findViewById(R.id.recipeDetailDietaryTags)


        recipeId?.let { id ->
            db.collection("dietarylist").document(id)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val recipe = document.toObject(Recipe::class.java)
                        if (recipe != null) {
                            caloriesTextView.text = "Calories: ${recipe.calories ?: "N/A"}"
                            carbsTextView.text = "Carbs: ${recipe.carbs ?: "N/A"}g"
                            fatTextView.text = "Fat: ${recipe.fat ?: "N/A"}g"
                            proteinTextView.text = "Protein: ${recipe.protein ?: "N/A"}g"
                            descriptionTextView.text = recipe.description ?: "No description available."
                            prepTimeTextView.text = recipe.prepTime ?: "N/A"
                            allergiesTextView.text = "Allergies: ${recipe.allergies?.joinToString(", ") ?: "None"}"
                            dietaryTagsTextView.text = "Dietary Tags: ${recipe.dietaryTags?.joinToString(", ") ?: "None"}"

                        } else {
                            Toast.makeText(context, "Error parsing recipe data.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Recipe not found.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(context, "Error fetching data: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        } ?: run {
            Toast.makeText(context, "No recipe ID provided.", Toast.LENGTH_SHORT).show()
        }


        return view
    }

    companion object {
        private const val ARG_RECIPE_ID = "recipe_id"

        @JvmStatic
        fun newInstance(recipeId: String) =
            RecipeInfoFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_RECIPE_ID, recipeId)
                }
            }
    }
}