package com.example.substate

import com.example.substate.R

/**
 * Centralized registry for subscription-related icons.
 * Maps human-readable names to their corresponding drawable resource IDs.
 */
object SubscriptionIcons {
    /**
     * Map of available icons for selection.
     */
    val availableIcons = mapOf(
        "Bank" to R.drawable.ic_bank,
        "Book" to R.drawable.ic_book,
        "Cloud" to R.drawable.ic_cloud,
        "Computer" to R.drawable.ic_computer,
        "Fitness" to R.drawable.ic_fitness,
        "Food" to R.drawable.ic_food,
        "Gaming" to R.drawable.ic_gaming,
        "Health" to R.drawable.ic_mask,
        "Music" to R.drawable.ic_music,
        "News" to R.drawable.ic_news,
        "Pets" to R.drawable.ic_pets,
        "Shopping" to R.drawable.ic_shopbagfast,
        "Software" to R.drawable.ic_software,
        "Streaming" to R.drawable.ic_streaming,
        "Utilities" to R.drawable.ic_utilities
    )

    /**
     * Helper function to retrieve a drawable resource ID by its string key.
     * Returns a default gallery icon if the key is not found.
     */
    fun getResourceId(name: String?): Int {
        return availableIcons[name] ?: android.R.drawable.ic_menu_gallery
    }
}
