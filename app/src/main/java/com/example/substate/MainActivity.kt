package com.example.substate

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth

/**
 * Main application screen, accessible only to logged-in users.
 */
class MainActivity : AppCompatActivity() {

    // Firebase Auth instance to check the current user's session
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge layout for modern UI look
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        
        // Handle window insets (like status bar and navigation bar) to avoid UI overlap
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        // 1. Session Check: If no user is logged in, redirect to the login screen immediately
        if (currentUser == null) {
            goToLogin()
            return
        }

        // Bind UI components
        val welcomeTextView = findViewById<TextView>(R.id.welcomeTextView)
        val logoutButton = findViewById<Button>(R.id.logoutButton)

        // 2. Display the logged-in user's email
        welcomeTextView.text = "Welcome, ${currentUser.email}!"

        // 3. Logout Listener: Signs the user out of Firebase and returns to Login page
        logoutButton.setOnClickListener {
            auth.signOut()
            goToLogin()
        }
    }

    /**
     * Helper function to navigate back to LoginActivity and clear the current activity stack.
     */
    private fun goToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish() // Closes MainActivity so the user cannot navigate back to it via back button
    }
}
