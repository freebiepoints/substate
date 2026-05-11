package com.example.substate

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName
import java.util.Calendar
import java.util.Date

/**
 * Data class representing a single price point in time.
 */
data class PricePoint(
    val price: Double = 0.0,
    val date: Timestamp = Timestamp.now()
)

/**
 * Data class representing a user's subscription.
 */
data class Subscription(
    val id: String = "",
    val serviceName: String = "",
    val priceHistory: List<PricePoint> = emptyList(),
    val schedule: String = "",
    val category: String = "",
    val paymentAccount: String = "",
    val dueDate: Timestamp? = null,
    val monthlyEquivalent: Double = 0.0,
    val annualEquivalent: Double = 0.0,
    val userId: String = "",
    
    @get:Exclude
    val isPendingSync: Boolean = false,
    
    @get:PropertyName("isActive")
    @set:PropertyName("isActive")
    var isActive: Boolean = true,
    
    val iconUrl: String? = null,
    val iconColor: Int = 0xFF2196F3.toInt(), // Default blue
    val notes: String = ""
) {
    // Helper to get the most recent price
    val currentPrice: Double
        get() = priceHistory.sortedByDescending { it.date }.firstOrNull()?.price ?: 0.0

    /**
     * Maps the subscription schedule to a standard iCalendar RRULE.
     */
    fun getRecurrenceRule(): String? {
        return when (schedule) {
            "Weekly" -> "FREQ=WEEKLY"
            "Monthly" -> "FREQ=MONTHLY"
            "Annually" -> "FREQ=YEARLY"
            else -> null
        }
    }

    /**
     * UI LOGIC: Price History
     * Calculates if the latest price is higher than the previous one.
     */
    fun getPriceHikeString(): String {
        if (priceHistory.size < 2) return ""
        val sortedHistory = priceHistory.sortedByDescending { it.date }
        val latest = sortedHistory[0].price
        val previous = sortedHistory[1].price

        if (latest > previous) {
            val increase = ((latest - previous) / previous) * 100
            return "Price increased by ${String.format(java.util.Locale.getDefault(), "%.1f", increase)}%"
        }
        return ""
    }
}

/**
 * KOTLIN FEATURE: Extension Functions
 * 
 * Instead of bloating the Activity or creating a complex 'Manager' class,
 * we attach domain-specific calculations directly to the Subscription model.
 * 
 * This calculates monthly and annual equivalents based on the billing cycle.
 */
fun Subscription.calculateEquivalents(): Pair<Double, Double> {
    val fee = currentPrice
    return when (schedule) {
        "Weekly" -> Pair(fee * (52.0 / 12.0), fee * 52.0)
        "Monthly" -> Pair(fee, fee * 12.0)
        "Annually" -> Pair(fee / 12.0, fee)
        else -> Pair(fee, fee * 12.0)
    }
}

/**
 * BUSINESS LOGIC: Automatic Date Management
 * 
 * Ensures the subscription "dueDate" is always in the future.
 * If today's date has passed the original due date, this function 
 * increments it by the schedule (Weekly/Monthly/Annually) iteratively.
 */
fun Subscription.getNextOccurrence(): Date {
    val calendar = Calendar.getInstance()
    val due = dueDate?.toDate() ?: Date()
    calendar.time = due

    val now = Calendar.getInstance()
    
    if (calendar.before(now)) {
        while (calendar.before(now)) {
            when (schedule) {
                "Weekly" -> calendar.add(Calendar.WEEK_OF_YEAR, 1)
                "Monthly" -> calendar.add(Calendar.MONTH, 1)
                "Annually" -> calendar.add(Calendar.YEAR, 1)
                else -> break
            }
        }
    }
    return calendar.time
}
