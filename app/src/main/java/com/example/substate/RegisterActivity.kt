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
 * Activity that handles new user registration using Firebase Authentication.
 */
class RegisterActivity : AppCompatActivity() {

    // Firebase Auth instance to handle account creation
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Bind UI components from the layout
        val emailEditText = findViewById<EditText>(R.id.emailEditText)
        val passwordEditText = findViewById<EditText>(R.id.passwordEditText)
        val confirmPasswordEditText = findViewById<EditText>(R.id.confirmPasswordEditText)
        val registerButton = findViewById<Button>(R.id.registerButton)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        // Set listener for the registration button
        registerButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()
            val confirmPassword = confirmPasswordEditText.text.toString().trim()

            // 1. Basic input validation
            if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 2. Check if passwords match
            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Show progress and disable button to prevent multiple clicks
            progressBar.visibility = View.VISIBLE
            registerButton.isEnabled = false

            // 3. Create user in Firebase
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    // Hide progress and re-enable button
                    progressBar.visibility = View.GONE
                    registerButton.isEnabled = true

                    if (task.isSuccessful) {
                        // Registration success: show message and redirect to Main page
                        Toast.makeText(this, "Registration successful", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this, MainActivity::class.java)
                        startActivity(intent)
                        finish() // Close RegisterActivity so user can't go back to it
                    } else {
                        // Registration failed: show error message from Firebase
                        Toast.makeText(
                            baseContext, "Registration failed: ${task.exception?.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        }

        // Redirect back to Login page if the user already has an account
        loginButton.setOnClickListener {
            finish() // Returns to the previous activity (LoginActivity)
        }
    }
}
