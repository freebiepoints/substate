package com.example.substate

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Calendar

/**
 * BACKGROUND PROCESSING: SubscriptionWorker
 * 
 * Why this is a highlight:
 * - Extends CoroutineWorker: Allows using Kotlin Coroutines (suspend functions) for 
 *   asynchronous database queries without blocking background threads.
 * - Reliability: Managed by Android's WorkManager, ensuring it runs even if the app 
 *   is closed or the device reboots.
 */
class SubscriptionWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    /**
     * The main execution point for the worker.
     * Queries Firestore for active subscriptions and checks if they are due within 24 hours.
     */
    override suspend fun doWork(): Result {
        // 1. Check if this is a test run triggered via the secret UI interaction
        val isTest = inputData.getBoolean("is_test", false)
        if (isTest) {
            showNotification(Subscription(serviceName = "Test Notification", id = "test_id"))
            return Result.success()
        }

        // 2. Identify the current user; stop if no one is logged in
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val db = FirebaseFirestore.getInstance()

        try {
            // 3. Fetch all active subscriptions belonging to the user from Firestore
            val snapshot = db.collection("subscriptions")
                .whereEqualTo("userId", userId)
                .whereEqualTo("isActive", true)
                .get()
                .await() // Suspends until the query completes

            // 4. Calculate the time window (Now until exactly 24 hours from now)
            val now = Calendar.getInstance()
            val tomorrow = Calendar.getInstance()
            tomorrow.add(Calendar.DAY_OF_YEAR, 1)

            // 5. Iterate through each subscription to check the due date
            for (doc in snapshot.documents) {
                val sub = doc.toObject(Subscription::class.java)
                if (sub != null) {
                    val dueDate = sub.dueDate?.toDate()
                    if (dueDate != null) {
                        val dueCalendar = Calendar.getInstance()
                        dueCalendar.time = dueDate

                        // 6. Trigger a notification if the due date is within the next 24 hours
                        if (dueCalendar.after(now) && dueCalendar.before(tomorrow)) {
                            showNotification(sub)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // 7. If an error occurs (e.g., network failure), retry based on backoff policy
            return Result.retry()
        }

        return Result.success()
    }

    /**
     * Builds and displays a system notification for a specific subscription.
     */
    private fun showNotification(subscription: Subscription) {
        val channelId = "subscription_reminders"
        val notificationManager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create the notification channel (Required for Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Subscription Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminds you of upcoming subscription renewals"
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Configure the notification look and feel
        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Subscription Reminder")
            .setContentText("${subscription.serviceName} is due tomorrow!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        // Show the notification using a unique ID (the hash code of the Firestore document ID)
        notificationManager.notify(subscription.id.hashCode(), builder.build())
    }
}
