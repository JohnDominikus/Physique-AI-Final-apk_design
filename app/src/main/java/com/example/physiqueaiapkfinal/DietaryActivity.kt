package com.example.physiqueaiapkfinal


import VideoItem
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.physiqueaiapkfinal.R
import com.example.physiqueaiapkfinal.VideoAdapter



class DietaryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dietary)

        val recycler = findViewById<RecyclerView>(R.id.recyclerDietary)
        val videos = listOf(
            VideoItem("Healthy Breakfasts", "5 mins", R.drawable.breakfast_thumb),
            VideoItem("Meal Prep Tips", "9 mins", R.drawable.meal_prep_thumb),
            VideoItem("Protein Smoothies", "4 mins", R.drawable.smoothie_thumb)
        )

        recycler.adapter = VideoAdapter(videos)
        recycler.layoutManager = LinearLayoutManager(this)
    }
}
