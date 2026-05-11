package com.example.substate

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class SubscriptionRepository(private val db: FirebaseFirestore, private val context: Context) {

    /**
     * Observes the device's network connection status.
     * This is the most reliable way to show an 'Offline' icon in a Firestore app.
     */
    fun observeConnectionStatus(): Flow<Boolean> = callbackFlow {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
            
        connectivityManager.registerNetworkCallback(request, callback)

        // Set initial state
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        trySend(capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    /**
     * Firestore Query: Fetch ALL subscriptions for the user.
     */
    fun getSubscriptions(userId: String): Flow<List<Subscription>> = callbackFlow {
        val subscriptionRef = db.collection("subscriptions")
            .whereEqualTo("userId", userId)

        // Crucial: Use MetadataChanges.INCLUDE to detect when 'hasPendingWrites' changes
        val listener = subscriptionRef.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }

            val subscriptions = snapshot?.documents?.mapNotNull { doc ->
                val sub = doc.toObject(Subscription::class.java)?.copy(
                    id = doc.id,
                    isPendingSync = doc.metadata.hasPendingWrites()
                )
                if (sub != null) {
                    val now = java.util.Calendar.getInstance()
                    val due = sub.dueDate?.toDate()
                    if (due != null && java.util.Calendar.getInstance().apply { time = due }.before(now)) {
                        val nextDate = sub.getNextOccurrence()
                        db.collection("subscriptions").document(doc.id).update("dueDate", com.google.firebase.Timestamp(nextDate))
                        sub.copy(dueDate = com.google.firebase.Timestamp(nextDate))
                    } else {
                        sub
                    }
                } else null
            } ?: emptyList()
            
            trySend(subscriptions)
        }

        awaitClose { listener.remove() }
    }

    fun updateSubscription(subscription: Subscription) {
        val (monthly, annual) = subscription.calculateEquivalents()
        db.collection("subscriptions").document(subscription.id)
            .set(subscription.copy(monthlyEquivalent = monthly, annualEquivalent = annual))
    }

    fun deleteSubscription(subscriptionId: String) {
        db.collection("subscriptions").document(subscriptionId).delete()
    }
}
