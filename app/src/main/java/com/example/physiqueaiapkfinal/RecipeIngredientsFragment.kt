package com.example.physiqueaiapkfinal

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import com.google.firebase.firestore.FirebaseFirestore

class RecipeIngredientsFragment : Fragment() {

    private var recipeId: String? = null

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
        val view = inflater.inflate(R.layout.fragment_recipe_ingredients, container, false)
        val ingredientsTextView = view.findViewById<TextView>(R.id.ingredientsTextView)

        val db = FirebaseFirestore.getInstance()

        recipeId?.let { id ->
            db.collection("dietarylist").document(id)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val recipe = document.toObject(Recipe::class.java)
                        if (recipe != null) {
                            // Correctly fetch and display the list of ingredients
                            ingredientsTextView.text = recipe.ingredients?.joinToString("\n") ?: "No ingredients found."
                        } else {
                            Toast.makeText(context, "Error parsing recipe data.", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        ingredientsTextView.text = "Recipe not found."
                    }
                }
                .addOnFailureListener { exception ->
                    ingredientsTextView.text = "Error fetching data: ${exception.message}"
                }
        } ?: run {
            ingredientsTextView.text = "No recipe ID provided."
        }

        return view
    }

    companion object {
        private const val ARG_RECIPE_ID = "recipe_id"

        @JvmStatic
        fun newInstance(recipeId: String) =
            RecipeIngredientsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_RECIPE_ID, recipeId)
                }
            }
    }
}