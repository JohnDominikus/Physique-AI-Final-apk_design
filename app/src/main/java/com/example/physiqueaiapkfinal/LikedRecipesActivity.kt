package com.example.physiqueaiapkfinal

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.ProgressBar
import com.google.firebase.firestore.FieldPath

class LikedRecipesActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: LikedRecipeAdapter
    private val likedRecipeList = mutableListOf<Recipe>()
    private var firestoreListener: ListenerRegistration? = null
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var backButton: ImageButton
    private lateinit var emptyListMessage: TextView
    private lateinit var loadingIndicator: ProgressBar

    private val TAG = "LikedRecipesActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_liked_recipes)

        recyclerView = findViewById(R.id.likedRecipesRecycler)
        recyclerView.layoutManager = LinearLayoutManager(this)
        backButton = findViewById(R.id.backButton)
        emptyListMessage = findViewById(R.id.emptyListMessage)
        loadingIndicator = findViewById(R.id.loadingIndicator)


        adapter = LikedRecipeAdapter(likedRecipeList, {
            val intent = Intent(this, RecipeDetailActivity::class.java)
            intent.putExtra("recipeId", it.id)
            startActivity(intent)
        }, {
            removeRecipeFromLiked(it)
        })
        recyclerView.adapter = adapter

        setupBackButton()
        fetchLikedRecipes()
    }

    private fun setupBackButton() {
        backButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }


    private fun fetchLikedRecipes() {
        val userId = auth.currentUser?.uid

        loadingIndicator.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyListMessage.visibility = View.GONE
        emptyListMessage.text = "Loading liked recipes..."

        if (userId != null) {
            Log.d(TAG, "Fetching liked recipes for user: $userId")
            firestoreListener = db.collection("users").document(userId).collection("likedRecipes")
                .addSnapshotListener { likedRecipeSnapshot, error ->
                    loadingIndicator.visibility = View.GONE

                    if (error != null) {
                        Log.e(TAG, "Error fetching liked recipes", error)
                        Toast.makeText(this, "Error fetching liked recipes", Toast.LENGTH_SHORT).show()
                        recyclerView.visibility = View.GONE
                        emptyListMessage.visibility = View.VISIBLE
                        emptyListMessage.text = "Error loading liked recipes."

                        return@addSnapshotListener
                    }

                    if (likedRecipeSnapshot != null) {
                        Log.d(TAG, "Liked recipes snapshot received. Number of documents: ${likedRecipeSnapshot.documents.size}")
                        val likedRecipeIds = likedRecipeSnapshot.documents.mapNotNull { doc ->
                            val recipeId = doc.getString("recipeId")
                            Log.d(TAG, "Found liked recipe ID: $recipeId")
                            recipeId
                        }

                        likedRecipeList.clear()
                        if (likedRecipeIds.isNotEmpty()) {
                            Log.d(TAG, "Fetching details for ${likedRecipeIds.size} liked recipes")
                            db.collection("dietarylist")
                                .whereIn(FieldPath.documentId(), likedRecipeIds) // Changed to query by document ID
                                .get()
                                .addOnSuccessListener { recipeDetailsSnapshot ->
                                    Log.d(TAG, "Recipe details snapshot received. Number of documents: ${recipeDetailsSnapshot.documents.size}")
                                    for (doc in recipeDetailsSnapshot) {
                                        val recipe = doc.toObject(Recipe::class.java)?.copy(id = doc.id)
                                        if (recipe != null) {
                                            likedRecipeList.add(recipe)
                                            Log.d(TAG, "Added recipe to list: ${recipe.mealName}")
                                        }
                                    }
                                    adapter.notifyDataSetChanged()

                                    if (likedRecipeList.isEmpty()) {
                                        recyclerView.visibility = View.GONE
                                        emptyListMessage.visibility = View.VISIBLE
                                        emptyListMessage.text = "You haven't liked any recipes yet."
                                    } else {
                                        recyclerView.visibility = View.VISIBLE
                                        emptyListMessage.visibility = View.GONE
                                    }

                                }
                                .addOnFailureListener { e ->
                                    Log.e(TAG, "Error fetching liked recipe details", e)
                                    Toast.makeText(this, "Error fetching liked recipe details: ${e.message}", Toast.LENGTH_SHORT).show()

                                    recyclerView.visibility = View.GONE
                                    emptyListMessage.visibility = View.VISIBLE
                                    emptyListMessage.text = "Error loading liked recipe details."
                                }
                        } else {
                            Log.d(TAG, "No liked recipe IDs found.")
                            likedRecipeList.clear()
                            adapter.notifyDataSetChanged()
                            recyclerView.visibility = View.GONE
                            emptyListMessage.visibility = View.VISIBLE
                            emptyListMessage.text = "You haven't liked any recipes yet."
                        }
                    } else {
                        Log.d(TAG, "Liked recipes snapshot is null.")
                        likedRecipeList.clear()
                        adapter.notifyDataSetChanged()
                        recyclerView.visibility = View.GONE
                        emptyListMessage.visibility = View.VISIBLE
                        emptyListMessage.text = "Error fetching liked recipes data."
                    }
                }
        } else {
            Log.d(TAG, "User is not logged in. Cannot fetch liked recipes.")
            Toast.makeText(this, "Please log in to view liked recipes", Toast.LENGTH_SHORT).show()
            likedRecipeList.clear()
            adapter.notifyDataSetChanged()
            loadingIndicator.visibility = View.GONE
            recyclerView.visibility = View.GONE
            emptyListMessage.visibility = View.VISIBLE
            emptyListMessage.text = "Please log in to view liked recipes."
        }
    }

    private fun removeRecipeFromLiked(recipe: Recipe) {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            Log.d(TAG, "Attempting to remove liked recipe: ${recipe.id}")
            db.collection("users").document(userId).collection("likedRecipes").document(recipe.id)
                .delete()
                .addOnSuccessListener {
                    Log.d(TAG, "Successfully removed liked recipe: ${recipe.id}")
                    Toast.makeText(this, "${recipe.mealName} removed from liked recipes", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error removing liked recipe: ${recipe.id}", e)
                    Toast.makeText(this, "Error removing recipe: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Log.d(TAG, "User not logged in. Cannot remove liked recipe.")
            Toast.makeText(this, "Please log in to remove liked recipes", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Activity destroyed. Removing Firestore listeners.")
        firestoreListener?.remove()
    }
}