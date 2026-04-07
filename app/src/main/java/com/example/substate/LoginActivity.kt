package com.example.substate

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

/**
 * Activity that handles user login using Firebase Authentication.
 * It also checks for an existing session on startup.
 */
class LoginActivity : AppCompatActivity() {

    // Firebase Auth instance used for authentication tasks
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Bind UI components from the layout XML
        val emailEditText = findViewById<EditText>(R.id.emailEditText)
        val passwordEditText = findViewById<EditText>(R.id.passwordEditText)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val registerButton = findViewById<Button>(R.id.registerButton)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        // Set listener for the Login button
        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            // Validate that inputs are not empty
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Show loading state
            progressBar.visibility = View.VISIBLE
            loginButton.isEnabled = false

            // Attempt to sign in with Firebase
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    // Reset UI state after task completion
                    progressBar.visibility = View.GONE
                    loginButton.isEnabled = true

                    if (task.isSuccessful) {
                        // Success: Navigate to the Main Activity
                        val intent = Intent(this, MainActivity::class.java)
                        startActivity(intent)
                        finish() // Prevent user from returning to login page via back button
                    } else {
                        // Failure: Show error message to the user
                        Toast.makeText(
                            baseContext, "Authentication failed: ${task.exception?.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        }

        // Navigate to the Register Activity when the register button is clicked
        registerButton.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    /**
     * Check if the user is already signed in when the activity starts.
     */
    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // User is already logged in, redirect to MainActivity
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }
}
