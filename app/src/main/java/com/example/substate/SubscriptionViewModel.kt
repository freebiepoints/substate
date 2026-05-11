package com.example.substate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SubscriptionViewModel(private val repository: SubscriptionRepository) : ViewModel() {

    private val _subscriptions = MutableStateFlow<List<Subscription>>(emptyList())
    val subscriptions: StateFlow<List<Subscription>> = _subscriptions.asStateFlow()

    private val _totalMonthly = MutableStateFlow(0.0)
    val totalMonthly: StateFlow<Double> = _totalMonthly.asStateFlow()

    private val _totalAnnual = MutableStateFlow(0.0)
    val totalAnnual: StateFlow<Double> = _totalAnnual.asStateFlow()

    private val _isConnected = MutableStateFlow(true)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    fun loadSubscriptions(userId: String) {
        viewModelScope.launch {
            repository.observeConnectionStatus().collect { connected ->
                _isConnected.value = connected
            }
        }
        viewModelScope.launch {
            repository.getSubscriptions(userId).collect { subs ->
                _subscriptions.value = subs
                calculateTotals(subs)
            }
        }
    }

    /**
     * Dashboard Math:
     * Calculates total monthly/annual for active subscriptions using latest price.
     */
    private fun calculateTotals(subs: List<Subscription>) {
        var monthly = 0.0
        var annual = 0.0
        
        // Use only 'isActive' subscriptions for totals
        for (sub in subs) {
            if (sub.isActive) {
                val (m, a) = sub.calculateEquivalents()
                monthly += m
                annual += a
            }
        }
        
        _totalMonthly.value = monthly
        _totalAnnual.value = annual
    }

    fun deleteSubscription(subscription: Subscription) {
        repository.deleteSubscription(subscription.id)
    }
    
    fun updateSubscription(subscription: Subscription) {
        repository.updateSubscription(subscription)
    }
}
