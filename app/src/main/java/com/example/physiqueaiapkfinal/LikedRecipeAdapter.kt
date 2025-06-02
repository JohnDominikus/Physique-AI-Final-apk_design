package com.example.physiqueaiapkfinal

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class LikedRecipeAdapter(
    private val recipes: List<Recipe>,
    private val onViewClick: (Recipe) -> Unit,
    private val onRemoveClick: (Recipe) -> Unit
) : RecyclerView.Adapter<LikedRecipeAdapter.LikedRecipeViewHolder>() {

    inner class LikedRecipeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.likedRecipeName)
        val mealType: TextView = view.findViewById(R.id.likedMealType)
        val thumbnail: ImageView = view.findViewById(R.id.likedRecipeImage)
        val dietaryTags: TextView = view.findViewById(R.id.likedRecipeDietaryTags)
        val viewButton: Button = view.findViewById(R.id.viewRecipeButton)
        val removeButton: ImageButton = view.findViewById(R.id.removeRecipeButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LikedRecipeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_liked_recipe, parent, false)
        return LikedRecipeViewHolder(view)
    }

    override fun onBindViewHolder(holder: LikedRecipeViewHolder, position: Int) {
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

        // Set click listeners for the buttons
        holder.viewButton.setOnClickListener { onViewClick(recipe) }
        holder.removeButton.setOnClickListener { onRemoveClick(recipe) }
    }

    override fun getItemCount() = recipes.size
}