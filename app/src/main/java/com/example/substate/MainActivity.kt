package com.example.substate

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.provider.CalendarContract
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Main dashboard activity displaying the user's subscription list and financial summary.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var viewModel: SubscriptionViewModel
    private lateinit var adapter: SubscriptionAdapter

    private var allSubscriptions = listOf<Subscription>()
    private var filteredSubscriptions = listOf<Subscription>()

    // Current sorting state to maintain consistency when data updates
    private var currentSortField = "serviceName"
    private var isAscending = true

    // Set of selected categories for filtering the list
    private var selectedCategories = mutableSetOf<String>()
    private var currentStatusFilter = "All" // "All", "Active", "Inactive"

    private lateinit var totalMonthlyTextView: TextView
    private lateinit var totalAnnualTextView: TextView
    private lateinit var emptyStateTextView: TextView
    private lateinit var connectionStatusIcon: ImageView
    private lateinit var connectionStatusText: TextView

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
        
        // Explicitly enable offline persistence using the modern API
        val settings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(com.google.firebase.firestore.PersistentCacheSettings.newBuilder().build())
            .build()
        db.firestoreSettings = settings

        val repository = SubscriptionRepository(db, applicationContext)
        val factory = SubscriptionViewModelFactory(repository)
        viewModel = androidx.lifecycle.ViewModelProvider(this, factory)[SubscriptionViewModel::class.java]

        // Redirect to Login if user is not authenticated
        if (auth.currentUser == null) {
            goToLogin()
            return
        }

        setupUI()
        updateSortIcons()
        observeViewModel()
        
        val userId = auth.currentUser?.uid
        if (userId != null) {
            viewModel.loadSubscriptions(userId)
        }
        
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
        connectionStatusIcon = findViewById(R.id.connectionStatusIcon)
        connectionStatusText = findViewById(R.id.connectionStatusText)

        val recyclerView = findViewById<RecyclerView>(R.id.subscriptionRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Initialize adapter with callbacks for updating/deleting from the list
        adapter = SubscriptionAdapter(
            onUpdate = { sub -> viewModel.updateSubscription(sub) },
            onDelete = { sub -> viewModel.deleteSubscription(sub) },
            onExportToCalendar = { sub -> exportToCalendar(sub) }
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

        // Filter buttons
        findViewById<Button>(R.id.filterButton).setOnClickListener { showFilterDialog() }
        findViewById<Button>(R.id.statusFilterButton).setOnClickListener { showStatusFilterDialog() }

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

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.subscriptions.collect { subs ->
                        allSubscriptions = subs
                        applyFiltersAndSort()
                    }
                }

                launch {
                    viewModel.totalMonthly.collect { total ->
                        totalMonthlyTextView.text = String.format(Locale.getDefault(), "$%.2f", total)
                    }
                }

                launch {
                    viewModel.totalAnnual.collect { total ->
                        totalAnnualTextView.text = String.format(Locale.getDefault(), "$%.2f", total)
                    }
                }

                launch {
                    viewModel.isConnected.collect { isConnected ->
                        if (isConnected) {
                            connectionStatusIcon.setImageResource(android.R.drawable.presence_online)
                            connectionStatusIcon.setColorFilter(ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_dark))
                            connectionStatusText.text = "Online"
                        } else {
                            connectionStatusIcon.setImageResource(android.R.drawable.presence_offline)
                            connectionStatusIcon.setColorFilter(ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_dark))
                            connectionStatusText.text = "Offline"
                        }
                    }
                }
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
                val pricePoint = PricePoint(price = fees[i], date = Timestamp.now())
                val sub = Subscription(
                    serviceName = names[i], category = categories[i], iconUrl = icons[i],
                    priceHistory = listOf(pricePoint), schedule = "Monthly", userId = userId, notes = "DUMMY_DATA",
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

    private fun applyFiltersAndSort() {
        // Apply Status Filter (All/Active/Inactive)
        var list = when (currentStatusFilter) {
            "Active" -> allSubscriptions.filter { it.isActive }
            "Inactive" -> allSubscriptions.filter { !it.isActive }
            else -> allSubscriptions
        }

        // Apply Category Filter
        if (selectedCategories.isNotEmpty()) {
            list = list.filter { selectedCategories.contains(it.category) }
        }
        
        // Show/Hide empty state message
        emptyStateTextView.visibility = if (allSubscriptions.isEmpty()) View.VISIBLE else View.GONE

        // Apply Sorting
        filteredSubscriptions = when (currentSortField) {
            "serviceName" -> if (isAscending) list.sortedBy { it.serviceName.lowercase() } else list.sortedByDescending { it.serviceName.lowercase() }
            "fee" -> if (isAscending) list.sortedBy { it.currentPrice } else list.sortedByDescending { it.currentPrice }
            "dueDate" -> if (isAscending) list.sortedBy { it.dueDate } else list.sortedByDescending { it.dueDate }
            else -> list
        }
        adapter.submitList(filteredSubscriptions)
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

    /**
     * Displays a single-choice dialog to filter by Active/Inactive status.
     */
    private fun showStatusFilterDialog() {
        val statuses = arrayOf("All", "Active", "Inactive")
        var selectedIdx = statuses.indexOf(currentStatusFilter)
        
        AlertDialog.Builder(this)
            .setTitle("Filter by Status")
            .setSingleChoiceItems(statuses, selectedIdx) { dialog, which ->
                currentStatusFilter = statuses[which]
                findViewById<MaterialButton>(R.id.statusFilterButton).text = "Status: ${currentStatusFilter}"
                applyFiltersAndSort()
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Intent Logic: Uses CalendarContract to create an ACTION_INSERT intent.
     * This is a "soft" intent that hands off data to the system calendar.
     */
    private fun exportToCalendar(subscription: Subscription) {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, "Subscription: ${subscription.serviceName}")
            putExtra(CalendarContract.Events.DESCRIPTION, "Payment for ${subscription.serviceName}. Notes: ${subscription.notes}")
            
            // Set Start Time (using the due date)
            val startTime = subscription.dueDate?.toDate()?.time ?: System.currentTimeMillis()
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTime)
            
            // Set All Day
            putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
            
            // Set Recurrence Rule (RRULE)
            subscription.getRecurrenceRule()?.let { rrule ->
                putExtra(CalendarContract.Events.RRULE, rrule)
            }
            
            // Set access level to private for financial security
            putExtra(CalendarContract.Events.ACCESS_LEVEL, CalendarContract.Events.ACCESS_PRIVATE)
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No calendar app found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
