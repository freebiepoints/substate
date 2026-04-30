package com.example.substate

import android.util.Patterns

/**
 * BEST PRACTICE: Centralized Validation Logic
 * 
 * We use an 'object' (Singleton) to house validation rules. This ensures
 * that password complexity requirements are enforced identically across both
 * the Registration and Reset Password screens, reducing bugs.
 */
object ValidationUtils {

    /**
     * Validates email format using Android's built-in Patterns utility.
     */
    fun isValidEmail(email: String): Boolean {
        return email.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    /**
     * Enforces strict security requirements for user passwords:
     * - Length between 8 and 32 characters.
     * - Must contain at least one: Uppercase, Lowercase, Digit, and Special Character.
     */
    fun isValidPassword(password: String): Boolean {
        if (password.length !in 8..32) return false
        
        val hasUppercase = password.any { it.isUpperCase() }
        val hasLowercase = password.any { it.isLowerCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecial = password.any { !it.isLetterOrDigit() }
        
        return hasUppercase && hasLowercase && hasDigit && hasSpecial
    }
    
    /**
     * Returns a user-friendly error message detailing password requirements.
     */
    fun getPasswordErrorMessage(): String {
        return "Password must be 8-32 characters and include uppercase, lowercase, a number, and a special character."
    }
}
