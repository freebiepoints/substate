package com.example.substate

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName
import java.util.Calendar
import java.util.Date

/**
 * Data class representing a user's subscription.
 */
data class Subscription(
    val id: String = "",
    val serviceName: String = "",
    val fee: Double = 0.0,
    val schedule: String = "",
    val category: String = "",
    val paymentAccount: String = "",
    val dueDate: Timestamp? = null,
    val monthlyEquivalent: Double = 0.0,
    val annualEquivalent: Double = 0.0,
    val userId: String = "",
    
    @get:PropertyName("isActive")
    @set:PropertyName("isActive")
    var isActive: Boolean = true,
    
    val iconUrl: String? = null,
    val iconColor: Int = 0xFF2196F3.toInt(), // Default blue
    val notes: String = ""
)

/**
 * KOTLIN FEATURE: Extension Functions
 * 
 * Instead of bloating the Activity or creating a complex 'Manager' class,
 * we attach domain-specific calculations directly to the Subscription model.
 * 
 * This calculates monthly and annual equivalents based on the billing cycle.
 */
fun Subscription.calculateEquivalents(): Pair<Double, Double> {
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
