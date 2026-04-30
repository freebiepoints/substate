package com.example.substate

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.android.material.button.MaterialButton
import com.facebook.login.widget.LoginButton

/**
 * Activity that handles user login using Firebase Authentication.
 * It provides fields for email/password and redirects to the dashboard upon success.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        // Bind UI components
        val emailEditText = findViewById<EditText>(R.id.emailEditText)
        val passwordEditText = findViewById<EditText>(R.id.passwordEditText)
        val forgotPasswordButton = findViewById<Button>(R.id.forgotPasswordButton)
        val stayLoggedInCheckBox = findViewById<CheckBox>(R.id.stayLoggedInCheckBox)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val registerButton = findViewById<Button>(R.id.registerButton)
        val googleLoginButton = findViewById<MaterialButton>(R.id.googleLoginButton)
        val progressBar = findViewById<ProgressBar>(R.id.loginProgressBar)

        loginButton.isEnabled = false

        /**
         * Real-time field validation to enable/disable the Login button.
         */
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val email = emailEditText.text.toString().trim()
                val password = passwordEditText.text.toString().trim()
                loginButton.isEnabled = email.isNotEmpty() && password.isNotEmpty()
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        emailEditText.addTextChangedListener(textWatcher)
        passwordEditText.addTextChangedListener(textWatcher)

        // Trigger login when the 'Done' key is pressed on the keyboard
        passwordEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (loginButton.isEnabled) loginButton.performClick()
                true
            } else false
        }

        // Logic for sending a password reset email via Firebase
        forgotPasswordButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            if (email.isEmpty()) {
                emailEditText.error = "Enter your email to reset password"
                return@setOnClickListener
            }
            auth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
                if (task.isSuccessful) Toast.makeText(this, "Reset email sent!", Toast.LENGTH_SHORT).show()
                else Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
            }
        }

        /**
         * Core login execution: validates input and signs in with Firebase.
         */
        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (!ValidationUtils.isValidEmail(email)) {
                emailEditText.error = "Invalid email format"
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE
            loginButton.isEnabled = false

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    progressBar.visibility = View.GONE
                    loginButton.isEnabled = true

                    if (task.isSuccessful) {
                        // Persist the "Stay logged in" preference
                        val sharedPref = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        sharedPref.edit().putBoolean("stay_logged_in", stayLoggedInCheckBox.isChecked).apply()

                        startActivity(Intent(this, MainActivity::class.java))
                        finish() // Exit login screen
                    } else {
                        Toast.makeText(this, "Login failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }

        googleLoginButton.setOnClickListener {
            Toast.makeText(this, "Google Sign-In integration pending...", Toast.LENGTH_SHORT).show()
        }

        registerButton.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    /**
     * Automatic session recovery if "Stay logged in" was previously enabled.
     */
    override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        val stayLoggedIn = getSharedPreferences("app_prefs", Context.MODE_PRIVATE).getBoolean("stay_logged_in", false)

        if (currentUser != null && stayLoggedIn) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        } else if (currentUser != null) {
            auth.signOut() // Clear session if not explicitly staying logged in
        }
    }
}
