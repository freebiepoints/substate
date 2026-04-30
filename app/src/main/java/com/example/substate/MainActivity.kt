package com.example.substate

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Main dashboard activity displaying the user's subscription list and financial summary.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: SubscriptionAdapter

    private var allSubscriptions = listOf<Subscription>()
    private var filteredSubscriptions = listOf<Subscription>()

    // Current sorting state to maintain consistency when data updates
    private var currentSortField = "serviceName"
    private var isAscending = true

    // Set of selected categories for filtering the list
    private var selectedCategories = mutableSetOf<String>()

    private lateinit var totalMonthlyTextView: TextView
    private lateinit var totalAnnualTextView: TextView
    private lateinit var emptyStateTextView: TextView

    // Counters and timers for hidden "easter egg" triggers (secret debug features)
    private var monthlyCardClickCount = 0
    private var annualCardClickCount = 0
    private var lastMonthlyClickTime: Long = 0
    private var lastAnnualClickTime: Long = 0

    /**
     * Permission launcher for Android 13+ notifications.
     */
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, "Notifications disabled. You won't receive reminders.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        // Handle window insets for edge-to-edge display
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Redirect to Login if user is not authenticated
        if (auth.currentUser == null) {
            goToLogin()
            return
        }

        setupUI()
        updateSortIcons()
        fetchSubscriptions() // Start listening for Firestore updates
        
        checkNotificationPermission()
        scheduleSubscriptionReminders() // Ensure the background worker is active
    }

    /**
     * Request notification permission on Android 13 and above.
     */
    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * Schedules a periodic background task to check for upcoming renewals every 24 hours.
     */
    private fun scheduleSubscriptionReminders() {
        val workRequest = PeriodicWorkRequestBuilder<SubscriptionWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "SubscriptionReminders",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    /**
     * Initializes UI components and sets up click listeners.
     */
    private fun setupUI() {
        totalMonthlyTextView = findViewById(R.id.totalMonthlyTextView)
        totalAnnualTextView = findViewById(R.id.totalAnnualTextView)
        emptyStateTextView = findViewById(R.id.emptyStateTextView)

        val recyclerView = findViewById<RecyclerView>(R.id.subscriptionRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Initialize adapter with callbacks for updating/deleting from the list
        adapter = SubscriptionAdapter(
            onUpdate = { sub -> updateSubscriptionInFirestore(sub) },
            onDelete = { sub -> deleteSubscriptionFromFirestore(sub) }
        )
        recyclerView.adapter = adapter

        // Floating Action Button to add a new subscription
        findViewById<FloatingActionButton>(R.id.addSubscriptionFab).setOnClickListener {
            startActivity(Intent(this, AddSubscriptionActivity::class.java))
        }

        findViewById<Button>(R.id.logoutButton).setOnClickListener {
            auth.signOut()
            goToLogin()
        }

        // Sorting buttons
        findViewById<MaterialButton>(R.id.sortNameButton).setOnClickListener { toggleSort("serviceName") }
        findViewById<MaterialButton>(R.id.sortFeeButton).setOnClickListener { toggleSort("fee") }
        findViewById<MaterialButton>(R.id.sortDateButton).setOnClickListener { toggleSort("dueDate") }

        // PRESENTATION TIP: Highlighting filtering & sorting logic
        findViewById<Button>(R.id.filterButton).setOnClickListener { showFilterDialog() }

        /**
         * DEBUG FEATURE: "Easter Eggs"
         * These hidden triggers allow the presenter to demonstrate features without manual setup.
         */
        
        // 5 clicks on Monthly Card toggles a set of realistic dummy data for the demo.
        findViewById<View>(R.id.monthlyCard).setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastMonthlyClickTime < 1000) monthlyCardClickCount++ else monthlyCardClickCount = 1
            lastMonthlyClickTime = currentTime
            if (monthlyCardClickCount == 5) {
                monthlyCardClickCount = 0
                handleDummyDataToggle()
            }
        }

        // 5 clicks on Annual Card triggers an immediate background work execution.
        // Useful for demonstrating notifications without waiting 24 hours.
        findViewById<View>(R.id.annualCard).setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastAnnualClickTime < 1000) annualCardClickCount++ else annualCardClickCount = 1
            lastAnnualClickTime = currentTime
            if (annualCardClickCount == 5) {
                annualCardClickCount = 0
                triggerNotificationTest()
            }
        }
    }

    /**
     * Immediately triggers a one-time execution of the SubscriptionWorker for testing purposes.
     */
    private fun triggerNotificationTest() {
        Toast.makeText(this, "Testing notifications...", Toast.LENGTH_SHORT).show()
        val data = Data.Builder().putBoolean("is_test", true).build()
        val testWorkRequest = OneTimeWorkRequestBuilder<SubscriptionWorker>().setInputData(data).build()
        WorkManager.getInstance(this).enqueue(testWorkRequest)
    }

    /**
     * Populates or clears dummy subscription data for demonstration purposes.
     */
    private fun handleDummyDataToggle() {
        val userId = auth.currentUser?.uid ?: return
        val dummySubs = allSubscriptions.filter { it.notes == "DUMMY_DATA" }

        if (dummySubs.isEmpty()) {
            Toast.makeText(this, "Generating dummy data...", Toast.LENGTH_SHORT).show()
            val batch = db.batch()
            val names = listOf("Netflix", "Spotify", "Gym Membership", "Adobe CC", "iCloud", "Amazon Prime")
            val categories = listOf("Entertainment", "Music", "Health", "Software", "Cloud", "Shopping")
            val icons = listOf("Streaming", "Music", "Health", "Computer", "Bank", "Shopping")
            val fees = listOf(15.99, 9.99, 45.0, 52.99, 2.99, 14.99)
            
            for (i in names.indices) {
                val ref = db.collection("subscriptions").document()
                val sub = Subscription(
                    serviceName = names[i], category = categories[i], iconUrl = icons[i],
                    fee = fees[i], schedule = "Monthly", userId = userId, notes = "DUMMY_DATA",
                    isActive = true, dueDate = Timestamp.now()
                )
                val (monthly, annual) = sub.calculateEquivalents()
                batch.set(ref, sub.copy(monthlyEquivalent = monthly, annualEquivalent = annual))
            }
            batch.commit()
        } else {
            Toast.makeText(this, "Removing dummy data...", Toast.LENGTH_SHORT).show()
            val batch = db.batch()
            for (sub in dummySubs) batch.delete(db.collection("subscriptions").document(sub.id))
            batch.commit()
        }
    }

    /**
     * Real-time listener for user subscriptions from Firestore.
     * 
     * TECHNICAL HIGHLIGHT: addSnapshotListener provides a "live" stream. 
     * Any change in the database (even from another device) triggers this 
     * callback, ensuring the UI is always in sync without manual refreshing.
     */
    private fun fetchSubscriptions() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("subscriptions")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                if (snapshot != null) {
                    val now = Calendar.getInstance()
                    val subscriptions = mutableListOf<Subscription>()
                    for (doc in snapshot.documents) {
                        val sub = doc.toObject(Subscription::class.java)?.copy(id = doc.id)
                        if (sub != null) {
                            // DOMAIN LOGIC: If a due date has passed, we don't just show it as 'late'.
                            // We automatically advance it to the next occurrence (Monthly/Weekly/etc).
                            val due = sub.dueDate?.toDate()
                            if (due != null && Calendar.getInstance().apply { time = due }.before(now)) {
                                val nextDate = sub.getNextOccurrence()
                                db.collection("subscriptions").document(doc.id).update("dueDate", Timestamp(nextDate))
                                subscriptions.add(sub.copy(dueDate = Timestamp(nextDate)))
                            } else {
                                subscriptions.add(sub)
                            }
                        }
                    }
                    allSubscriptions = subscriptions
                    applyFiltersAndSort() // Re-apply current sort/filter to the new data set
                }
            }
    }

    private fun updateSubscriptionInFirestore(sub: Subscription) {
        val (monthly, annual) = sub.calculateEquivalents()
        db.collection("subscriptions").document(sub.id).set(sub.copy(monthlyEquivalent = monthly, annualEquivalent = annual))
    }

    private fun deleteSubscriptionFromFirestore(sub: Subscription) {
        db.collection("subscriptions").document(sub.id).delete()
    }

    /**
     * Filters and sorts the local list of subscriptions before submitting to the RecyclerView adapter.
     */
    private fun applyFiltersAndSort() {
        filteredSubscriptions = if (selectedCategories.isEmpty()) allSubscriptions else allSubscriptions.filter { selectedCategories.contains(it.category) }
        
        // Show/Hide empty state message
        emptyStateTextView.visibility = if (allSubscriptions.isEmpty()) View.VISIBLE else View.GONE

        filteredSubscriptions = when (currentSortField) {
            "serviceName" -> if (isAscending) filteredSubscriptions.sortedBy { it.serviceName.lowercase() } else filteredSubscriptions.sortedByDescending { it.serviceName.lowercase() }
            "fee" -> if (isAscending) filteredSubscriptions.sortedBy { it.fee } else filteredSubscriptions.sortedByDescending { it.fee }
            "dueDate" -> if (isAscending) filteredSubscriptions.sortedBy { it.dueDate } else filteredSubscriptions.sortedByDescending { it.dueDate }
            else -> filteredSubscriptions
        }
        adapter.submitList(filteredSubscriptions)
        updateSummary()
    }

    private fun toggleSort(field: String) {
        if (currentSortField == field) isAscending = !isAscending else { currentSortField = field; isAscending = true }
        applyFiltersAndSort()
        updateSortIcons()
    }

    /**
     * Updates sort button icons to reflect current sorting direction (ASC/DESC).
     */
    private fun updateSortIcons() {
        val buttons = mapOf("serviceName" to R.id.sortNameButton, "fee" to R.id.sortFeeButton, "dueDate" to R.id.sortDateButton)
        buttons.values.forEach { findViewById<MaterialButton>(it).icon = null }
        val iconRes = if (isAscending) android.R.drawable.arrow_up_float else android.R.drawable.arrow_down_float
        findViewById<MaterialButton>(buttons[currentSortField]!!).setIconResource(iconRes)
    }

    /**
     * Recalculates the total monthly and annual costs for all active subscriptions.
     */
    private fun updateSummary() {
        var totalMonthly = 0.0; var totalAnnual = 0.0
        for (sub in filteredSubscriptions) if (sub.isActive) { totalMonthly += sub.monthlyEquivalent; totalAnnual += sub.annualEquivalent }
        totalMonthlyTextView.text = String.format(Locale.getDefault(), "$%.2f", totalMonthly)
        totalAnnualTextView.text = String.format(Locale.getDefault(), "$%.2f", totalAnnual)
    }

    /**
     * Displays a multi-choice dialog to filter subscriptions by category.
     */
    private fun showFilterDialog() {
        val categories = arrayOf("Entertainment", "Gaming", "News", "Utilities", "Health & Fitness", "Education", "Software", "Cloud", "Pets", "Other")
        val checkedItems = BooleanArray(categories.size) { i -> selectedCategories.contains(categories[i]) }
        AlertDialog.Builder(this)
            .setTitle("Filter by Category")
            .setMultiChoiceItems(categories, checkedItems) { _, which, isChecked ->
                if (isChecked) selectedCategories.add(categories[which]) else selectedCategories.remove(categories[which])
            }
            .setPositiveButton("Apply") { _, _ -> applyFiltersAndSort() }
            .setNegativeButton("Clear All") { _, _ -> selectedCategories.clear(); applyFiltersAndSort() }
            .show()
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
