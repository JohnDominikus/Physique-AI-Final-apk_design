package com.example.physiqueaiapkfinal

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

data class OutdoorActivity(
    val name: String,
    val caloriesPerMinute: Double, // calories burned per minute for a 70kg person
    val description: String,
    val iconResource: String
)

data class ActivityLog(
    val id: String = UUID.randomUUID().toString(),
    val activityName: String = "",
    val duration: Int = 0, // in minutes
    val caloriesBurned: Int = 0,
    val date: String = "",
    val userId: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

class OutdoorActivityActivity : AppCompatActivity() {
    
    private lateinit var spinnerActivity: Spinner
    private lateinit var etDuration: EditText
    private lateinit var tvCaloriesEstimate: TextView
    private lateinit var btnLogActivity: Button
    private lateinit var recyclerActivityLogs: RecyclerView
    
    private val firestore = FirebaseFirestore.getInstance()
    private val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    
    private val outdoorActivities = listOf(
        OutdoorActivity("Walking (3.5 mph)", 4.0, "Leisurely walking pace", "walk"),
        OutdoorActivity("Brisk Walking (4.5 mph)", 5.5, "Fast walking pace", "walk"),
        OutdoorActivity("Jogging (5 mph)", 8.0, "Light jogging", "run"),
        OutdoorActivity("Running (6 mph)", 10.0, "Moderate running", "run"),
        OutdoorActivity("Running (8 mph)", 13.0, "Fast running", "run"),
        OutdoorActivity("Cycling (12-14 mph)", 8.5, "Moderate cycling", "bike"),
        OutdoorActivity("Cycling (16-19 mph)", 12.0, "Vigorous cycling", "bike"),
        OutdoorActivity("Swimming (freestyle)", 11.0, "Swimming laps", "swim"),
        OutdoorActivity("Hiking", 6.0, "Trail hiking with backpack", "hike"),
        OutdoorActivity("Rock Climbing", 11.0, "Outdoor rock climbing", "climb"),
        OutdoorActivity("Tennis", 8.0, "Singles tennis", "tennis"),
        OutdoorActivity("Basketball", 8.5, "Full court basketball", "basketball"),
        OutdoorActivity("Soccer", 10.0, "Competitive soccer", "soccer"),
        OutdoorActivity("Volleyball", 4.0, "Beach/outdoor volleyball", "volleyball"),
        OutdoorActivity("Golf (walking)", 4.5, "Walking and carrying clubs", "golf"),
        OutdoorActivity("Skateboarding", 5.0, "Recreational skateboarding", "skate"),
        OutdoorActivity("Rollerblading", 7.0, "Recreational rollerblading", "rollerblade"),
        OutdoorActivity("Kayaking", 5.0, "Recreational kayaking", "kayak"),
        OutdoorActivity("Dancing", 4.5, "Social dancing", "dance"),
        OutdoorActivity("Yoga (outdoor)", 3.0, "Hatha yoga", "yoga")
    )
    
    private val activityLogs = mutableListOf<ActivityLog>()
    private lateinit var adapter: ActivityLogAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_outdoor_activity)
        
        initViews()
        setupSpinner()
        setupRecyclerView()
        setupClickListeners()
        loadActivityLogs()
    }
    
    private fun initViews() {
        spinnerActivity = findViewById(R.id.spinnerActivity)
        etDuration = findViewById(R.id.etDuration)
        tvCaloriesEstimate = findViewById(R.id.tvCaloriesEstimate)
        btnLogActivity = findViewById(R.id.btnLogActivity)
        recyclerActivityLogs = findViewById(R.id.recyclerActivityLogs)
    }
    
    private fun setupSpinner() {
        val activityNames = outdoorActivities.map { "${it.name} (${it.caloriesPerMinute} cal/min)" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, activityNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerActivity.adapter = adapter
        
        spinnerActivity.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                calculateCalories()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun setupRecyclerView() {
        adapter = ActivityLogAdapter(activityLogs)
        recyclerActivityLogs.layoutManager = LinearLayoutManager(this)
        recyclerActivityLogs.adapter = adapter
    }
    
    private fun setupClickListeners() {
        btnLogActivity.setOnClickListener {
            logActivity()
        }
        
        etDuration.setOnTextChangedListener { _, _, _, _ ->
            calculateCalories()
        }
    }
    
    private fun calculateCalories() {
        val duration = etDuration.text.toString().toIntOrNull() ?: 0
        val selectedPosition = spinnerActivity.selectedItemPosition
        
        if (duration > 0 && selectedPosition >= 0) {
            val activity = outdoorActivities[selectedPosition]
            val estimatedCalories = (activity.caloriesPerMinute * duration).toInt()
            tvCaloriesEstimate.text = "$estimatedCalories calories"
        } else {
            tvCaloriesEstimate.text = "0 calories"
        }
    }
    
    private fun logActivity() {
        val duration = etDuration.text.toString().toIntOrNull()
        val selectedPosition = spinnerActivity.selectedItemPosition
        
        if (duration == null || duration <= 0) {
            Toast.makeText(this, "Please enter a valid duration", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (selectedPosition < 0) {
            Toast.makeText(this, "Please select an activity", Toast.LENGTH_SHORT).show()
            return
        }
        
        val activity = outdoorActivities[selectedPosition]
        val caloriesBurned = (activity.caloriesPerMinute * duration).toInt()
        val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        
        val activityLog = ActivityLog(
            activityName = activity.name,
            duration = duration,
            caloriesBurned = caloriesBurned,
            date = today,
            userId = userId
        )
        
        // Save to Firebase
        firestore.collection("userActivityLogs")
            .document(userId)
            .collection("activities")
            .add(activityLog)
            .addOnSuccessListener {
                Toast.makeText(this, "Activity logged successfully!", Toast.LENGTH_SHORT).show()
                updateDailyCalories(caloriesBurned)
                etDuration.setText("")
                tvCaloriesEstimate.text = "0 calories"
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to log activity: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    
    private fun updateDailyCalories(calories: Int) {
        val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        
        firestore.collection("userStats")
            .document(userId)
            .collection("dailyStats")
            .document(today)
            .get()
            .addOnSuccessListener { document ->
                val currentCalories = document.getLong("caloriesBurned")?.toInt() ?: 0
                val updatedCalories = currentCalories + calories
                
                firestore.collection("userStats")
                    .document(userId)
                    .collection("dailyStats")
                    .document(today)
                    .set(mapOf(
                        "caloriesBurned" to updatedCalories,
                        "lastUpdated" to System.currentTimeMillis()
                    ))
            }
    }
    
    private fun loadActivityLogs() {
        val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        
        firestore.collection("userActivityLogs")
            .document(userId)
            .collection("activities")
            .whereEqualTo("date", today)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener
                
                activityLogs.clear()
                snapshots?.let {
                    for (doc in it) {
                        val log = doc.toObject(ActivityLog::class.java).copy(id = doc.id)
                        activityLogs.add(log)
                    }
                }
                adapter.notifyDataSetChanged()
            }
    }
}

// Extension function for TextWatcher
fun EditText.setOnTextChangedListener(action: (s: CharSequence?, start: Int, before: Int, count: Int) -> Unit) {
    this.addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            action(s, start, before, count)
        }
        override fun afterTextChanged(s: android.text.Editable?) {}
    })
}

class ActivityLogAdapter(
    private val logs: List<ActivityLog>
) : RecyclerView.Adapter<ActivityLogAdapter.ViewHolder>() {
    
    class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val tvActivityName: TextView = view.findViewById(R.id.tvActivityName)
        val tvDuration: TextView = view.findViewById(R.id.tvDuration)
        val tvCalories: TextView = view.findViewById(R.id.tvCalories)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
    }
    
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_activity_log, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val log = logs[position]
        holder.tvActivityName.text = log.activityName
        holder.tvDuration.text = "${log.duration} min"
        holder.tvCalories.text = "${log.caloriesBurned} cal"
        
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        holder.tvTime.text = timeFormat.format(Date(log.timestamp))
    }
    
    override fun getItemCount() = logs.size
} 