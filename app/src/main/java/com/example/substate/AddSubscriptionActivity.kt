package com.example.substate

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

/**
 * Activity for adding a new subscription to the user's account.
 * Handles form input, icon selection via a grid, and persistence to Firestore.
 */
class AddSubscriptionActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var selectedDate: Date? = null
    private var selectedIconName: String = "Other" // Default icon if none selected
    private var selectedIconColor: Int = 0xFF2196F3.toInt() // Default blue
    
    // Date formatter for consistent UI display (UTC to match MaterialDatePicker)
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_subscription)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Bind UI components
        val serviceIconImageView = findViewById<ImageView>(R.id.serviceIconImageView)
        val changeIconButton = findViewById<Button>(R.id.changeIconButton)
        val changeColorButton = findViewById<Button>(R.id.changeColorButton)

        val serviceNameEditText = findViewById<TextInputEditText>(R.id.serviceNameEditText)
        val feeEditText = findViewById<TextInputEditText>(R.id.feeEditText)
        val scheduleAutoComplete = findViewById<AutoCompleteTextView>(R.id.scheduleAutoComplete)
        val categoryAutoComplete = findViewById<AutoCompleteTextView>(R.id.categoryAutoComplete)
        val dateEditText = findViewById<TextInputEditText>(R.id.dateEditText)
        val accountEditText = findViewById<TextInputEditText>(R.id.accountEditText)
        val notesEditText = findViewById<TextInputEditText>(R.id.notesEditText)
        val saveButton = findViewById<Button>(R.id.saveSubscriptionButton)

        // Trigger the visual icon selector dialog
        changeIconButton.setOnClickListener {
            showIconSelector { iconName, resId ->
                selectedIconName = iconName
                serviceIconImageView.setImageResource(resId)
            }
        }

        changeColorButton.setOnClickListener {
            showColorPicker { color ->
                selectedIconColor = color
                serviceIconImageView.setBackgroundColor(color)
            }
        }

        // Configure dropdown menus for billing schedule and categories
        val schedules = arrayOf("Weekly", "Monthly", "Annually")
        scheduleAutoComplete.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, schedules))

        val categories = arrayOf("Entertainment", "Gaming", "News", "Utilities", "Health & Fitness", "Education", "Software", "Cloud", "Pets", "Other")
        categoryAutoComplete.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, categories))

        // Set up the Material Design Date Picker
        dateEditText.setOnClickListener {
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Billing Date")
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build()

            datePicker.addOnPositiveButtonClickListener { selection ->
                val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                calendar.timeInMillis = selection
                selectedDate = calendar.time
                dateEditText.setText(dateFormat.format(selectedDate!!))
            }
            datePicker.show(supportFragmentManager, "DATE_PICKER")
        }

        /**
         * Real-time validation: Enables the Save button only when all mandatory fields are filled.
         */
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val name = serviceNameEditText.text.toString().trim()
                val fee = feeEditText.text.toString().trim()
                val schedule = scheduleAutoComplete.text.toString().trim()
                val category = categoryAutoComplete.text.toString().trim()
                val date = dateEditText.text.toString().trim()

                saveButton.isEnabled = name.isNotEmpty() && fee.isNotEmpty() && 
                                      schedule.isNotEmpty() && category.isNotEmpty() && 
                                      date.isNotEmpty()
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        serviceNameEditText.addTextChangedListener(textWatcher)
        feeEditText.addTextChangedListener(textWatcher)
        scheduleAutoComplete.addTextChangedListener(textWatcher)
        categoryAutoComplete.addTextChangedListener(textWatcher)
        dateEditText.addTextChangedListener(textWatcher)

        saveButton.isEnabled = false

        saveButton.setOnClickListener {
            val name = serviceNameEditText.text.toString().trim()
            val fee = feeEditText.text.toString().toDoubleOrNull() ?: 0.0
            val schedule = scheduleAutoComplete.text.toString()
            val category = categoryAutoComplete.text.toString()
            val account = accountEditText.text.toString().trim()
            val notes = notesEditText.text.toString().trim()

            saveSubscription(name, fee, schedule, category, account, notes)
        }
    }

    /**
     * Displays a custom AlertDialog containing a GridView of subscription icons.
     * Provides a visual way for users to identify their services.
     */
    private fun showIconSelector(onIconSelected: (String, Int) -> Unit) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_icon_selector, null)
        val gridView = dialogView.findViewById<GridView>(R.id.iconGridView)
        
        val iconList = SubscriptionIcons.availableIcons.toList()
        
        // Simple adapter to display icons in the grid
        val adapter = object : BaseAdapter() {
            override fun getCount(): Int = iconList.size
            override fun getItem(position: Int) = iconList[position]
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val view = convertView ?: LayoutInflater.from(this@AddSubscriptionActivity)
                    .inflate(R.layout.item_icon_choice, parent, false)
                val imageView = view.findViewById<ImageView>(R.id.iconImageView)
                imageView.setImageResource(iconList[position].second)
                return view
            }
        }
        
        gridView.adapter = adapter
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("Choose an Icon")
            .setView(dialogView)
            .create()
            
        gridView.setOnItemClickListener { _, _, position, _ ->
            val selectedIcon = iconList[position]
            onIconSelected(selectedIcon.first, selectedIcon.second)
            dialog.dismiss()
        }
        
        dialog.show()
    }

    /**
     * Simple dialog to pick a color for the icon background using swatches.
     */
    private fun showColorPicker(onColorSelected: (Int) -> Unit) {
        val colors = intArrayOf(
            0xFFF44336.toInt(), 0xFFE91E63.toInt(), 0xFF9C27B0.toInt(), 0xFF673AB7.toInt(),
            0xFF3F51B5.toInt(), 0xFF2196F3.toInt(), 0xFF03A9F4.toInt(), 0xFF00BCD4.toInt(),
            0xFF009688.toInt(), 0xFF4CAF50.toInt(), 0xFF8BC34A.toInt(), 0xFFCDDC39.toInt(),
            0xFFFFEB3B.toInt(), 0xFFFFC107.toInt(), 0xFFFF9800.toInt(), 0xFFFF5722.toInt(),
            0xFF795548.toInt(), 0xFF9E9E9E.toInt(), 0xFF607D8B.toInt()
        )
        
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_icon_selector, null)
        val gridView = dialogView.findViewById<GridView>(R.id.iconGridView)
        
        gridView.adapter = object : BaseAdapter() {
            override fun getCount(): Int = colors.size
            override fun getItem(position: Int) = colors[position]
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val view = convertView ?: LayoutInflater.from(this@AddSubscriptionActivity)
                    .inflate(R.layout.item_color_choice, parent, false)
                val swatch = view.findViewById<View>(R.id.colorSwatch)
                swatch.setBackgroundColor(colors[position])
                return view
            }
        }
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("Choose a Color")
            .setView(dialogView)
            .create()
            
        gridView.setOnItemClickListener { _, _, position, _ ->
            onColorSelected(colors[position])
            dialog.dismiss()
        }
        
        dialog.show()
    }

    /**
     * Compiles the subscription data and saves it to the Firestore collection.
     * Calculates financial equivalents (monthly/annual) before saving.
     */
    private fun saveSubscription(name: String, fee: Double, schedule: String, category: String, account: String, notes: String) {
        val userId = auth.currentUser?.uid ?: return
        
        val sub = Subscription(
            serviceName = name, fee = fee, schedule = schedule, category = category,
            paymentAccount = account, dueDate = selectedDate?.let { Timestamp(it) },
            userId = userId, notes = notes, iconUrl = selectedIconName,
            iconColor = selectedIconColor, isActive = true
        )

        // Ensure the initial due date is set to the next valid occurrence
        val nextDate = sub.getNextOccurrence()
        val (monthly, annual) = sub.calculateEquivalents()

        val finalSubscription = sub.copy(
            dueDate = Timestamp(nextDate),
            monthlyEquivalent = monthly,
            annualEquivalent = annual
        )

        db.collection("subscriptions")
            .add(finalSubscription)
            .addOnSuccessListener {
                Toast.makeText(this, "Subscription saved!", Toast.LENGTH_SHORT).show()
                finish() // Return to the dashboard
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
