package com.example.physiqueaiapkfinal

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class RecipeAdapter(
    private val recipes: List<Recipe>,
    private val onItemClick: (Recipe) -> Unit
) : RecyclerView.Adapter<RecipeAdapter.RecipeViewHolder>() {

    private var likedRecipeIds: Set<String> = emptySet() // To store liked recipe IDs

    inner class RecipeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.recipeName)
        val mealType: TextView = view.findViewById(R.id.mealType)
        val thumbnail: ImageView = view.findViewById(R.id.recipeImage)
        val dietaryTags: TextView = view.findViewById(R.id.recipeDietaryTags)
        val infoIcon: ImageView = view.findViewById(R.id.infoIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecipeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recipe, parent, false)
        return RecipeViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecipeViewHolder, position: Int) {
        val recipe = recipes[position]
        holder.name.text = recipe.mealName ?: ""
        holder.mealType.text = recipe.mealType ?: ""
        holder.dietaryTags.text = recipe.dietaryTags?.joinToString(", ") ?: ""

        // Load image using Glide
        Glide.with(holder.itemView.context)
            .load(recipe.imageUrl)
            .placeholder(android.R.drawable.sym_def_app_icon) // Optional placeholder
            .error(android.R.drawable.ic_dialog_alert) // Optional error image
            .into(holder.thumbnail)

        holder.itemView.setOnClickListener { onItemClick(recipe) }

        holder.infoIcon.setOnClickListener {
            // TODO: Implement action for info icon click (e.g., show a dialog with more details)
            Toast.makeText(holder.itemView.context, "Info clicked for ${recipe.mealName}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun getItemCount() = recipes.size

    // Method to update the set of liked recipe IDs
    fun setLikedRecipeIds(likedIds: Set<String>) {
        this.likedRecipeIds = likedIds
        notifyDataSetChanged() // Notify adapter to rebind items
    }
}