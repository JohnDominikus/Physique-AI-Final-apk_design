package com.example.physiqueaiapkfinal

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class RecipeListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var searchEditText: EditText
    private lateinit var mealTypeSpinner: Spinner
    private lateinit var likedRecipesButton: ImageButton // ImageButton for liked recipes

    private val recipeList = mutableListOf<Recipe>()
    private var filteredList = mutableListOf<Recipe>()
    private lateinit var adapter: RecipeAdapter
    private var firestoreListener: ListenerRegistration? = null
    private val db = FirebaseFirestore.getInstance() // Get Firestore instance
    private val auth = FirebaseAuth.getInstance() // Get FirebaseAuth instance
    private val likedRecipeIds = mutableSetOf<String>() // To store liked recipe IDs
    private var likedRecipesListener: ListenerRegistration? = null // Listener for liked recipes

    // Meal types for the dropdown
    private val mealTypes = arrayOf("All", "Breakfast", "Lunch", "Dinner", "Snacks")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recipelist)

        recyclerView = findViewById(R.id.recipeRecycler)
        searchEditText = findViewById(R.id.searchRecipeEditText)
        mealTypeSpinner = findViewById(R.id.mealTypeSpinner)
        likedRecipesButton = findViewById(R.id.likedRecipesButton) // Find the liked recipes button

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = RecipeAdapter(filteredList) { recipe ->
            // Handle item click: Navigate to RecipeDetailActivity
            val intent = Intent(this, RecipeDetailActivity::class.java)
            intent.putExtra("recipeId", recipe.id)
            startActivity(intent)
        }
        recyclerView.adapter = adapter

        setupMealTypeSpinner()
        setupSearchAndFilter()
        listenForRecipesRealtime()
        setupLikedRecipesButton()
        listenForLikedRecipes() // Start listening for liked recipes
    }

    private fun setupMealTypeSpinner() {
        // Create adapter for spinner
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, mealTypes)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mealTypeSpinner.adapter = spinnerAdapter

        // Set listener for spinner selection
        mealTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedMealType = mealTypes[position]
                filterRecipes(category = selectedMealType)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Do nothing
            }
        }
    }

    private fun setupSearchAndFilter() {
        findViewById<ImageButton>(R.id.searchRecipeButton).setOnClickListener {
            val selectedMealType = mealTypes[mealTypeSpinner.selectedItemPosition]
            filterRecipes(category = selectedMealType)
        }
        searchEditText.setOnEditorActionListener { _, _, _ ->
            val selectedMealType = mealTypes[mealTypeSpinner.selectedItemPosition]
            filterRecipes(category = selectedMealType)
            true
        }
    }

    private fun listenForRecipesRealtime() {
        firestoreListener = db.collection("dietarylist")
            .addSnapshotListener { result, error ->
                if (error != null) {
                    Toast.makeText(this, "Error loading recipes", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                recipeList.clear()
                var hadError = false
                if (result != null) {
                    for (doc in result) {
                        try {
                            val recipe = doc.toObject(Recipe::class.java)?.copy(id = doc.id)
                            if (recipe != null) recipeList.add(recipe)
                        } catch (e: Exception) {
                            hadError = true
                            e.printStackTrace()
                        }
                    }
                }
                filterRecipes() // Filter the recipes after fetching
                if (hadError) {
                    Toast.makeText(this, "Some recipes could not be loaded.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun filterRecipes(category: String = "All") {
        val query = searchEditText.text.toString().trim().lowercase()
        filteredList.clear()
        filteredList.addAll(recipeList.filter { recipe ->
            val matchesSearch = (recipe.mealName ?: "").lowercase().contains(query) ||
                    (recipe.description ?: "").lowercase().contains(query) ||
                    (recipe.ingredients?.joinToString() ?: "").lowercase().contains(query)

            val matchesCategory = when (category) {
                "All" -> true
                else -> (recipe.mealType ?: "").equals(category, ignoreCase = true)
            }
            matchesSearch && matchesCategory
        })
        adapter.notifyDataSetChanged()
    }

    private fun setupLikedRecipesButton() {
        likedRecipesButton.setOnClickListener {
            val intent = Intent(this, LikedRecipesActivity::class.java)
            startActivity(intent)
        }
    }

    private fun listenForLikedRecipes() {
        val userId = auth.currentUser?.uid
        if (userId != null) {
            likedRecipesListener = db.collection("users").document(userId).collection("likedRecipes")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        // Handle error
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        likedRecipeIds.clear()
                        for (doc in snapshot.documents) {
                            doc.getString("recipeId")?.let { likedRecipeIds.add(it) }
                        }
                        // Update the adapter to reflect liked status changes
                        adapter.setLikedRecipeIds(likedRecipeIds)
                    }
                }
        } else {
            likedRecipeIds.clear()
            adapter.setLikedRecipeIds(likedRecipeIds)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        firestoreListener?.remove()
        likedRecipesListener?.remove() // Remove liked recipes listener
    }
}