class WorkoutActivity : AppCompatActivity() {

    private lateinit var database: DatabaseReference
    private lateinit var workoutListView: ListView
    private lateinit var addSelectedButton: Button
    private val workoutList = mutableListOf<Workout>()
    private val selectedWorkouts = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workout)

        workoutListView = findViewById(R.id.workoutListView)
        addSelectedButton = findViewById(R.id.addSelectedWorkouts)
        database = FirebaseDatabase.getInstance().getReference("exerciseinfos")

        fetchWorkouts()

        workoutListView.setOnItemClickListener { _, _, position, _ ->
            val selectedWorkout = workoutList[position]
            val intent = Intent(this, WorkoutDetailsActivity::class.java)
            intent.putExtra("WORKOUT_ID", selectedWorkout.id)
            intent.putExtra("VIDEO_URL", selectedWorkout.videoUrl)  // Pass video URL
            startActivity(intent)
        }

        workoutListView.setOnItemLongClickListener { _, _, position, _ ->
            val selectedWorkout = workoutList[position]
            if (selectedWorkouts.contains(selectedWorkout.id)) {
                selectedWorkouts.remove(selectedWorkout.id)
            } else {
                selectedWorkouts.add(selectedWorkout.id)
            }
            return@setOnItemLongClickListener true
        }

        addSelectedButton.setOnClickListener {
            if (selectedWorkouts.isNotEmpty()) {
                Toast.makeText(this, "Added ${selectedWorkouts.size} workouts", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No workouts selected", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
}
