package com.example.substate

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth

/**
 * Activity that handles new user registration using Firebase Authentication.
 * Includes real-time password requirement validation and visual feedback.
 */
class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()

        // Bind UI components
        val emailEditText = findViewById<EditText>(R.id.emailEditText)
        val passwordEditText = findViewById<EditText>(R.id.passwordEditText)
        val confirmPasswordEditText = findViewById<EditText>(R.id.confirmPasswordEditText)
        val registerButton = findViewById<Button>(R.id.registerButton)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        // Password requirement labels for visual feedback
        val reqLength = findViewById<TextView>(R.id.reqLength)
        val reqUppercase = findViewById<TextView>(R.id.reqUppercase)
        val reqLowercase = findViewById<TextView>(R.id.reqLowercase)
        val reqNumber = findViewById<TextView>(R.id.reqNumber)
        val reqSpecial = findViewById<TextView>(R.id.reqSpecial)

        registerButton.isEnabled = false

        val colorSuccess = ContextCompat.getColor(this, android.R.color.holo_green_dark)
        val colorError = ContextCompat.getColor(this, android.R.color.holo_red_dark)

        /**
         * UX HIGHLIGHT: Real-time Password Validation
         * 
         * We use a TextWatcher to provide instantaneous feedback as the user types.
         * This improves the user experience by clarifying exactly which security
         * requirements have been met (visualized via color changes).
         */
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val email = emailEditText.text.toString().trim()
                val password = passwordEditText.text.toString().trim()
                val confirm = confirmPasswordEditText.text.toString().trim()
                
                // Update Requirement UI colors based on current password input
                updateRequirementUI(reqLength, password.length in 8..32, colorSuccess, colorError)
                updateRequirementUI(reqUppercase, password.any { it.isUpperCase() }, colorSuccess, colorError)
                updateRequirementUI(reqLowercase, password.any { it.isLowerCase() }, colorSuccess, colorError)
                updateRequirementUI(reqNumber, password.any { it.isDigit() }, colorSuccess, colorError)
                updateRequirementUI(reqSpecial, password.any { !it.isLetterOrDigit() }, colorSuccess, colorError)

                // Enable button only if all criteria (including complexity and match) are met
                registerButton.isEnabled = email.isNotEmpty() && 
                                        ValidationUtils.isValidPassword(password) && 
                                        confirm == password
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        emailEditText.addTextChangedListener(textWatcher)
        passwordEditText.addTextChangedListener(textWatcher)
        confirmPasswordEditText.addTextChangedListener(textWatcher)

        confirmPasswordEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (registerButton.isEnabled) registerButton.performClick()
                true
            } else false
        }

        /**
         * Executes the account creation process with Firebase.
         */
        registerButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (!ValidationUtils.isValidEmail(email)) {
                emailEditText.error = "Invalid email format"
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE
            registerButton.isEnabled = false

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    progressBar.visibility = View.GONE
                    registerButton.isEnabled = true

                    if (task.isSuccessful) {
                        Toast.makeText(this, "Welcome to SubState!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this, "Registration failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }

        loginButton.setOnClickListener {
            finish() // Return to LoginActivity
        }
    }

    private fun updateRequirementUI(textView: TextView, isMet: Boolean, colorSuccess: Int, colorError: Int) {
        textView.setTextColor(if (isMet) colorSuccess else colorError)
    }
}
