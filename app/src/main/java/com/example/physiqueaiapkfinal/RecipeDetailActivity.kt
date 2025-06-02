package com.example.physiqueaiapkfinal

import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RecipeDetailActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var likeButton: ImageButton
    private lateinit var backButton: ImageButton
    private lateinit var recipeDetailImage: ImageView
    private lateinit var recipeDetailName: TextView
    private lateinit var recipeDetailMealType: TextView
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recipe_detail)

        val recipeId = intent.getStringExtra("recipeId") ?: run {
            Toast.makeText(this, "Recipe ID not provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        likeButton = findViewById(R.id.likeButton)
        backButton = findViewById(R.id.backButton)
        recipeDetailImage = findViewById(R.id.recipeDetailImage)
        recipeDetailName = findViewById(R.id.recipeDetailName)
        recipeDetailMealType = findViewById(R.id.recipeDetailMealType)
        tabLayout = findViewById(R.id.recipeDetailTabLayout)
        viewPager = findViewById(R.id.recipeDetailViewPager)


        setupBackButton()
        fetchRecipeDetails(recipeId)
        setupTabs(recipeId) // Setup tabs after fetching recipe details
        setupLikeButton(recipeId) // Setup like button with recipe ID
    }

    private fun setupBackButton() {
        backButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun fetchRecipeDetails(recipeId: String) {
        db.collection("dietarylist")
            .document(recipeId)
            .get()
            .addOnSuccessListener { doc ->
                val recipe = doc?.toObject(Recipe::class.java)
                if (recipe != null) {
                    recipeDetailName.text = recipe.mealName ?: ""
                    recipeDetailMealType.text = recipe.mealType ?: ""

                    Glide.with(this)
                        .load(recipe.imageUrl)
                        .placeholder(android.R.drawable.sym_def_app_icon)
                        .error(android.R.drawable.ic_dialog_alert)
                        .into(recipeDetailImage)

                    // Pass recipe data to fragments if needed
                    (viewPager.adapter as? RecipeDetailPagerAdapter)?.setRecipe(recipe)

                } else {
                    Toast.makeText(this, "Recipe not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading recipe: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun setupLikeButton(recipeId: String) {
        val userId = auth.currentUser?.uid ?: return // Need logged in user

        // Check initial liked status and update icon
        db.collection("users").document(userId).collection("likedRecipes").document(recipeId).get()
            .addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    likeButton.setImageResource(R.drawable.fav_filled)
                } else {
                    likeButton.setImageResource(R.drawable.fav)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error checking liked status: ${e.message}", Toast.LENGTH_SHORT).show()
            }


        likeButton.setOnClickListener {
            val userLikedRecipesRef = db.collection("users").document(userId).collection("likedRecipes")

            userLikedRecipesRef.document(recipeId).get()
                .addOnSuccessListener { documentSnapshot ->
                    if (documentSnapshot.exists()) {
                        // Recipe is liked, so unlike it
                        userLikedRecipesRef.document(recipeId).delete()
                            .addOnSuccessListener {
                                likeButton.setImageResource(R.drawable.fav)
                                Toast.makeText(this, "Recipe unliked", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Error unliking recipe: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        // Recipe is not liked, so like it
                        userLikedRecipesRef.document(recipeId).set(mapOf("recipeId" to recipeId))
                            .addOnSuccessListener {
                                likeButton.setImageResource(R.drawable.fav_filled)
                                Toast.makeText(this, "Recipe liked", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Error liking recipe: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error checking liked status: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }


    private fun setupTabs(recipeId: String) {
        val tabTitles = listOf("INFO", "INSTRUCTION", "INGREDIENTS")
        viewPager.adapter = RecipeDetailPagerAdapter(this, recipeId, tabTitles.size)

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()
    }

    // Adapter for the ViewPager2
    private inner class RecipeDetailPagerAdapter(
        fragmentActivity: FragmentActivity,
        private val recipeId: String,
        private val itemCount: Int
    ) : FragmentStateAdapter(fragmentActivity) {

        private var recipe: Recipe? = null

        fun setRecipe(recipe: Recipe) {
            this.recipe = recipe
            // Notify fragments or update their content directly if they are visible
            // This might require finding the currently visible fragment and updating it
            // A more robust solution might involve using a ViewModel
        }

        override fun createFragment(position: Int): Fragment {
            // Create fragments based on position
            return when (position) {
                0 -> RecipeInfoFragment.newInstance(recipeId) // INFO tab
                1 -> RecipeInstructionsFragment.newInstance(recipeId) // INSTRUCTION tab
                2 -> RecipeIngredientsFragment.newInstance(recipeId) // INGREDIENTS tab
                else -> throw IllegalStateException("Invalid tab position")
            }
        }

        override fun getItemCount(): Int = itemCount
    }
}