package com.example.substate

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.GridView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.*

/**
 * Adapter for the RecyclerView that displays the list of subscriptions.
 * Supports expandable items, inline editing, and deletion.
 */
class SubscriptionAdapter(
    private val onUpdate: (Subscription) -> Unit,
    private val onDelete: (Subscription) -> Unit,
    private val onExportToCalendar: (Subscription) -> Unit
) : ListAdapter<Subscription, SubscriptionAdapter.ViewHolder>(SubscriptionDiffCallback()) {

    // Date format for the collapsed view item
    private val collapsedDateFormat = SimpleDateFormat("MMM dd", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // Date format for the expanded view item
    private val fullDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    
    // Tracks which item is currently expanded (only one at a time)
    private var expandedId: String? = null
    
    // Tracks if the expanded item is in editing mode
    private var isEditing = false

    // Flag to adapt layout for Grid vs List (Grid is currently disabled in MainActivity)
    var isGridView: Boolean = false
        set(value) {
            field = value
            expandedId = null 
            isEditing = false
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_subscription, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val subscription = getItem(position)
        val isExpanded = subscription.id == expandedId
        holder.bind(subscription, isExpanded, isEditing && isExpanded)
        
        // Handle item clicks to expand/collapse
        holder.itemView.setOnClickListener {
            if (!isEditing && !isGridView) {
                expandedId = if (isExpanded) null else subscription.id
                notifyDataSetChanged()
            }
        }
    }

    /**
     * ViewHolder class that manages the UI for individual subscription items.
     */
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // UI element references
        private val iconImageView: ImageView = itemView.findViewById(R.id.itemIconImageView)
        private val nameTextView: TextView = itemView.findViewById(R.id.itemNameTextView)
        private val categoryTextView: TextView = itemView.findViewById(R.id.itemCategoryTextView)
        private val feeTextView: TextView = itemView.findViewById(R.id.itemFeeTextView)
        private val dueDateCollapsed: TextView = itemView.findViewById(R.id.itemDueDateCollapsedTextView)
        private val expandedLayout: View = itemView.findViewById(R.id.expandedLayout)
        private val readModeLayout: View = itemView.findViewById(R.id.readModeLayout)
        private val accountValue: TextView = itemView.findViewById(R.id.itemAccountValue)
        private val scheduleValue: TextView = itemView.findViewById(R.id.itemScheduleValue)
        private val categoryValue: TextView = itemView.findViewById(R.id.itemCategoryValue)
        private val notesValue: TextView = itemView.findViewById(R.id.itemNotesValue)
        private val dueDateValue: TextView = itemView.findViewById(R.id.itemDueDateValue)
        private val editModeLayout: View = itemView.findViewById(R.id.editModeLayout)
        private val editName: TextInputEditText = itemView.findViewById(R.id.editNameEditText)
        private val editFee: TextInputEditText = itemView.findViewById(R.id.editFeeEditText)
        private val editSchedule: AutoCompleteTextView = itemView.findViewById(R.id.editScheduleAutoComplete)
        private val editCategory: AutoCompleteTextView = itemView.findViewById(R.id.editCategoryAutoComplete)
        private val editDueDate: TextInputEditText = itemView.findViewById(R.id.editDueDateEditText)
        private val editAccount: TextInputEditText = itemView.findViewById(R.id.editAccountEditText)
        private val editNotes: TextInputEditText = itemView.findViewById(R.id.editNotesEditText)
        private val statusLabel: TextView = itemView.findViewById(R.id.statusLabel)
        private val activeSwitch: SwitchMaterial = itemView.findViewById(R.id.activeSwitch)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)
        private val calendarButton: ImageButton = itemView.findViewById(R.id.calendarButton)
        private val modifySaveButton: Button = itemView.findViewById(R.id.modifySaveButton)
        private val priceHikeTextView: TextView = itemView.findViewById(R.id.priceHikeTextView)
        private val pendingSyncTextView: TextView = itemView.findViewById(R.id.pendingSyncTextView)

        private var tempSelectedIcon: String? = null
        private var tempSelectedColor: Int? = null

        /**
         * Binds data to the views and configures interactivity based on expansion/editing state.
         */
        fun bind(subscription: Subscription, isExpanded: Boolean, editingThis: Boolean) {
            // Set basic info
            nameTextView.text = subscription.serviceName
            categoryTextView.text = subscription.category
            
            val suffix = when (subscription.schedule) {
                "Weekly" -> "/wk"
                "Monthly" -> "/mo"
                "Annually" -> "/yr"
                else -> ""
            }
            feeTextView.text = String.format(Locale.getDefault(), "$%.2f%s", subscription.currentPrice, suffix)
            
            subscription.dueDate?.let {
                dueDateCollapsed.text = collapsedDateFormat.format(it.toDate())
            }

            pendingSyncTextView.visibility = if (subscription.isPendingSync) View.VISIBLE else View.GONE

            // Toggle visibility of expanded details
            expandedLayout.visibility = if (isExpanded) View.VISIBLE else View.GONE
            readModeLayout.visibility = if (editingThis) View.GONE else View.VISIBLE
            editModeLayout.visibility = if (editingThis) View.VISIBLE else View.GONE
            modifySaveButton.text = if (editingThis) "Save" else "Modify"

            // Update icon and color based on selection or subscription data
            val currentIcon = if (editingThis && tempSelectedIcon != null) tempSelectedIcon else subscription.iconUrl
            val currentColor = if (editingThis && tempSelectedColor != null) tempSelectedColor!! else subscription.iconColor
            
            iconImageView.setImageResource(SubscriptionIcons.getResourceId(currentIcon))
            iconImageView.setBackgroundColor(currentColor)

            accountValue.text = if (subscription.paymentAccount.isEmpty()) "Not specified" else subscription.paymentAccount
            scheduleValue.text = subscription.schedule
            categoryValue.text = subscription.category
            notesValue.text = if (subscription.notes.isEmpty()) "No notes" else subscription.notes
            dueDateValue.text = subscription.dueDate?.let { fullDateFormat.format(it.toDate()) } ?: "Not set"
            statusLabel.text = if (subscription.isActive) "Active" else "Inactive"

            val hikeString = subscription.getPriceHikeString()
            if (hikeString.isNotEmpty()) {
                priceHikeTextView.text = hikeString
                priceHikeTextView.visibility = View.VISIBLE
            } else {
                priceHikeTextView.visibility = View.GONE
            }

            // Populate edit fields if in editing mode
            if (editingThis) {
                if (editName.tag != subscription.id) {
                    editName.setText(subscription.serviceName)
                    editFee.setText(subscription.currentPrice.toString())
                    
                    val schedules = arrayOf("Weekly", "Monthly", "Annually")
                    editSchedule.setAdapter(android.widget.ArrayAdapter(itemView.context, android.R.layout.simple_list_item_1, schedules))
                    editSchedule.setText(subscription.schedule, false)

                    val categories = arrayOf("Entertainment", "Gaming", "News", "Utilities", "Health & Fitness", "Education", "Software", "Cloud", "Pets", "Other")
                    editCategory.setAdapter(android.widget.ArrayAdapter(itemView.context, android.R.layout.simple_list_item_1, categories))
                    editCategory.setText(subscription.category, false)

                    editAccount.setText(subscription.paymentAccount)
                    editNotes.setText(subscription.notes)
                    subscription.dueDate?.let {
                        editDueDate.setText(fullDateFormat.format(it.toDate()))
                    }
                    editName.tag = subscription.id
                    tempSelectedIcon = subscription.iconUrl
                    tempSelectedColor = subscription.iconColor
                }
            } else {
                editName.tag = null
            }

            // Handle Date Picker for Due Date
            editDueDate.setOnClickListener {
                if (editingThis) {
                    showDatePicker(subscription.dueDate?.toDate() ?: Date()) { newDate ->
                        editDueDate.setText(fullDateFormat.format(newDate))
                        editDueDate.tag = newDate // Store selected date in tag
                    }
                }
            }

            // Handle Active/Inactive toggle
            activeSwitch.setOnCheckedChangeListener(null)
            activeSwitch.isChecked = subscription.isActive
            activeSwitch.setOnCheckedChangeListener { _, isChecked ->
                statusLabel.text = if (isChecked) "Active" else "Inactive"
                onUpdate(subscription.copy(isActive = isChecked))
            }

            // Icon picker trigger
            iconImageView.setOnClickListener {
                if (editingThis) {
                    showIconSelector { name, resId ->
                        tempSelectedIcon = name
                        iconImageView.setImageResource(resId)
                    }
                }
            }

            // Color picker trigger on long click
            iconImageView.setOnLongClickListener {
                if (editingThis) {
                    showColorPicker { color ->
                        tempSelectedColor = color
                        iconImageView.setBackgroundColor(color)
                    }
                    true
                } else false
            }

            deleteButton.setOnClickListener {
                showDeleteConfirmation(subscription)
            }

            calendarButton.setOnClickListener {
                onExportToCalendar(subscription)
            }

            // Handle Modify/Save button clicks
            modifySaveButton.setOnClickListener {
                if (!editingThis) {
                    isEditing = true
                    notifyDataSetChanged()
                } else {
                    val newName = editName.text.toString().trim()
                    val newPrice = editFee.text.toString().toDoubleOrNull() ?: subscription.currentPrice
                    
                    val updatedPriceHistory = if (newPrice != subscription.currentPrice) {
                        subscription.priceHistory + PricePoint(newPrice, com.google.firebase.Timestamp.now())
                    } else {
                        subscription.priceHistory
                    }

                    val newSchedule = editSchedule.text.toString()
                    val newCategory = editCategory.text.toString()
                    val newAccount = editAccount.text.toString().trim()
                    val newNotes = editNotes.text.toString().trim()
                    val newDate = editDueDate.tag as? Date
                    
                    val updatedSub = subscription.copy(
                        serviceName = newName,
                        priceHistory = updatedPriceHistory,
                        schedule = newSchedule,
                        category = newCategory,
                        paymentAccount = newAccount,
                        notes = newNotes,
                        dueDate = newDate?.let { com.google.firebase.Timestamp(it) } ?: subscription.dueDate,
                        iconUrl = tempSelectedIcon ?: subscription.iconUrl,
                        iconColor = tempSelectedColor ?: subscription.iconColor
                    )
                    
                    onUpdate(updatedSub)
                    isEditing = false
                    tempSelectedIcon = null
                    tempSelectedColor = null
                    editDueDate.tag = null
                    notifyDataSetChanged()
                }
            }
        }

        /**
         * Simple dialog to pick a date.
         */
        private fun showDatePicker(initialDate: Date, onDateSelected: (Date) -> Unit) {
            val calendar = Calendar.getInstance().apply { time = initialDate }
            val datePickerDialog = android.app.DatePickerDialog(
                itemView.context,
                { _, year, month, dayOfMonth ->
                    val selectedCalendar = Calendar.getInstance().apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month)
                        set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    }
                    onDateSelected(selectedCalendar.time)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            )
            datePickerDialog.show()
        }

        /**
         * Simple dialog to pick an icon for the subscription.
         */
        private fun showIconSelector(onIconSelected: (String, Int) -> Unit) {
            val iconNames = SubscriptionIcons.availableIcons.keys.toTypedArray()
            AlertDialog.Builder(itemView.context)
                .setTitle("Choose an Icon")
                .setItems(iconNames) { _, which ->
                    val selectedName = iconNames[which]
                    onIconSelected(selectedName, SubscriptionIcons.getResourceId(selectedName))
                }
                .show()
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
            
            val dialogView = LayoutInflater.from(itemView.context).inflate(R.layout.dialog_icon_selector, null)
            val gridView = dialogView.findViewById<GridView>(R.id.iconGridView)
            
            gridView.adapter = object : BaseAdapter() {
                override fun getCount(): Int = colors.size
                override fun getItem(position: Int) = colors[position]
                override fun getItemId(position: Int): Long = position.toLong()
                override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                    val view = convertView ?: LayoutInflater.from(itemView.context)
                        .inflate(R.layout.item_color_choice, parent, false)
                    val swatch = view.findViewById<View>(R.id.colorSwatch)
                    swatch.setBackgroundColor(colors[position])
                    return view
                }
            }
            
            val dialog = AlertDialog.Builder(itemView.context)
                .setTitle("Choose a Color")
                .setView(dialogView)
                .create()
                
            gridView.setOnItemClickListener { _, _, position, _ ->
                onColorSelected(colors[position])
                dialog.dismiss()
            }
            
            dialog.show()
        }

        private fun showDeleteConfirmation(subscription: Subscription) {
            AlertDialog.Builder(itemView.context)
                .setTitle("Delete Subscription")
                .setMessage("Are you sure you want to remove ${subscription.serviceName}?")
                .setPositiveButton("Delete") { _, _ -> 
                    isEditing = false
                    expandedId = null
                    onDelete(subscription) 
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    /**
     * Optimized callback to calculate differences between lists for smooth updates.
     */
    class SubscriptionDiffCallback : DiffUtil.ItemCallback<Subscription>() {
        override fun areItemsTheSame(oldItem: Subscription, newItem: Subscription): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Subscription, newItem: Subscription): Boolean = oldItem == newItem
    }
}
