private fun fetchWorkouts() {
    database.addValueEventListener(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            workoutList.clear()
            for (workoutSnapshot in snapshot.children) {
                val workout = workoutSnapshot.getValue(Workout::class.java)
                if (workout != null) {
                    workoutList.add(workout)
                }
            }
            val workoutNames = workoutList.map { it.name }
            val adapter = ArrayAdapter(this@WorkoutActivity, android.R.layout.simple_list_item_multiple_choice, workoutNames)
            workoutListView.adapter = adapter
        }

        override fun onCancelled(error: DatabaseError) {
            Toast.makeText(this@WorkoutActivity, "Failed to load workouts", Toast.LENGTH_SHORT).show()
        }
    })
}
