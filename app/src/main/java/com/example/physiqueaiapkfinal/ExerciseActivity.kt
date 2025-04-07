package com.example.physiqueaiapkfinal

import VideoItem
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ExerciseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exercise) // Make sure this XML file has recyclerExercise

        val recycler = findViewById<RecyclerView>(R.id.recyclerExercise)

        val videos = listOf(
            VideoItem("Full Body Warmup", "8 mins", R.drawable.stretch_thumb),
            VideoItem("Cardio Blast", "15 mins", R.drawable.cardio_thumb),
            VideoItem("Strength Training", "10 mins", R.drawable.strength_thumb)
        )

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = VideoAdapter(videos)
    }
}
