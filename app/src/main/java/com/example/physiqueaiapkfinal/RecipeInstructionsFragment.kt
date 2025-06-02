package com.example.physiqueaiapkfinal

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast // Import Toast
import com.google.firebase.firestore.FirebaseFirestore // Import FirebaseFirestore

class RecipeInstructionsFragment : Fragment() {

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
        val view = inflater.inflate(R.layout.fragment_recipe_instructions, container, false)
        val instructionsTextView = view.findViewById<TextView>(R.id.recipeInstructionsTextView) // Corrected ID

        // You'll need to fetch and display instructions here
        recipeId?.let { id ->
            db.collection("dietarylist").document(id)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val recipe = document.toObject(Recipe::class.java)
                        if (recipe != null) {
                            instructionsTextView.text = recipe.instructions ?: "No instructions available."
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
            instructionsTextView.text = "No recipe ID provided."
        }

        return view
    }

    companion object {
        private const val ARG_RECIPE_ID = "recipe_id"

        @JvmStatic
        fun newInstance(recipeId: String) =
            RecipeInstructionsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_RECIPE_ID, recipeId)
                }
            }
    }
}