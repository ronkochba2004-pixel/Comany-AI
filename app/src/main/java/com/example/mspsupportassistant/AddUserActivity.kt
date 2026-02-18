package com.example.mspsupportassistant

import android.os.Bundle
import android.util.Patterns
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.mspsupportassistant.model.CreateUserRequest
import com.example.mspsupportassistant.network.RetrofitClient
import kotlinx.coroutines.launch

class AddUserActivity : AppCompatActivity() {

    // Views (lateinit so we can use them in helper functions)
    private lateinit var firstNameEdit: EditText
    private lateinit var lastNameEdit: EditText
    private lateinit var emailEdit: EditText
    private lateinit var passwordEdit: EditText
    private lateinit var confirmPasswordEdit: EditText
    private lateinit var passwordErrorText: TextView
    private lateinit var confirmPasswordErrorText: TextView
    private lateinit var nationalIDEdit: EditText
    private lateinit var nationalIdErrorText: TextView

    private lateinit var roleGroup: RadioGroup
    private lateinit var createButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_user)

        // 1) Find all views
        initViews()

        // 2) Set up click listener for the "Create user" button
        setupCreateUserButton()
    }

    // ------------------------ View setup ------------------------

    // Find all views by ID once, and store them in properties
    private fun initViews() {
        firstNameEdit = findViewById(R.id.editFirstName)
        lastNameEdit = findViewById(R.id.editLastName)
        emailEdit = findViewById(R.id.editEmail)
        passwordEdit = findViewById(R.id.editPassword)
        confirmPasswordEdit = findViewById(R.id.editConfirmPassword)
        passwordErrorText = findViewById(R.id.textPasswordError)
        confirmPasswordErrorText = findViewById(R.id.textConfirmPasswordError)
        nationalIDEdit = findViewById(R.id.editNationalId)
        nationalIdErrorText = findViewById(R.id.textNationalIdError)

        roleGroup = findViewById(R.id.radioGroupRole)
        createButton = findViewById(R.id.buttonCreateUser)
    }

    private fun setupCreateUserButton() {
        createButton.setOnClickListener {
            handleCreateUserClick()
        }
    }

    // ------------------------ Main click logic ------------------------

    private fun handleCreateUserClick() {
        // Read raw input from views
        val firstNameRaw = firstNameEdit.text.toString()
        val lastNameRaw = lastNameEdit.text.toString()
        val emailRaw = emailEdit.text.toString()
        val password = passwordEdit.text.toString()
        val confirmPassword = confirmPasswordEdit.text.toString()
        val nationalIdRaw = nationalIDEdit.text.toString()

        // Clear previous errors
        clearErrors()

        // Format names (trim, lowercase, capitalize first letter)
        val firstName = formatName(firstNameRaw)
        val lastName = formatName(lastNameRaw)
        val email = emailRaw.trim()
        val nationalId = nationalIdRaw.trim()

        // Validate all inputs
        val isValid = validateInputs(
            firstName = firstName,
            lastName = lastName,
            email = email,
            password = password,
            confirmPassword = confirmPassword,
            nationalId = nationalId
        )

        if (!isValid) {
            // Stop here, do not continue to "create user" logic
            return
        }

        // Determine role based on selected radio button
        val role = getSelectedRole()

        // For now we can hardcode the company id (e.g. 1),
        // later you can pass it via Intent or from logged-in user:
        val companyId = CurrentUserHolder.user?.companyId
        if (companyId == null) {
            Toast.makeText(this, "No current user loaded", Toast.LENGTH_LONG).show()
            return
        }

        // Call backend in a coroutine
        lifecycleScope.launch {
            try {
                val request = CreateUserRequest(
                    company_id = companyId,
                    email = email,
                    first_name = firstName,
                    last_name = lastName,
                    role = role,
                    national_id = nationalId,
                    password = password
                )

                // Call backend: POST /create_user
                val backendUser = RetrofitClient.api.createUser(request)

                // Optionally map to LocalUser if you need it:
                // val localUser = backendUserToLocalUser(backendUser)

                Toast.makeText(this@AddUserActivity, "User created", Toast.LENGTH_SHORT).show()

                // Close this screen and go back to AdminActivity
                finish()

            } catch (e: Exception) { Toast.makeText(this@AddUserActivity, "Error creating user", Toast.LENGTH_LONG).show() }
        }
    }

    // ------------------------ Helper functions ------------------------

    // Remove previous error texts and EditText error states
    private fun clearErrors() {
        firstNameEdit.error = null
        lastNameEdit.error = null
        emailEdit.error = null

        passwordErrorText.text = ""
        confirmPasswordErrorText.text = ""
        nationalIdErrorText.text = ""
    }

    // Format a name: trim spaces, lowercase, then uppercase first letter
    private fun formatName(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""

        val lower = trimmed.lowercase()
        return lower.replaceFirstChar { it.uppercase() }
    }

    // Validate all user inputs. Return true if everything is OK.
    private fun validateInputs(
        firstName: String,
        lastName: String,
        email: String,
        password: String,
        confirmPassword: String,
        nationalId: String,
    ): Boolean {
        var ok = true

        // First name required
        if (firstName.isEmpty()) {
            firstNameEdit.error = "First name is required"
            ok = false
        }

        // Last name required
        if (lastName.isEmpty()) {
            lastNameEdit.error = "Last name is required"
            ok = false
        }

        // Email must be valid
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailEdit.error = "Invalid email address"
            ok = false
        }

        // Password rules
        val passwordIssue = validatePassword(password)
        if (passwordIssue != null) {
            passwordErrorText.text = passwordIssue
            ok = false
        }

        // Passwords must match
        if (password != confirmPassword) {
            confirmPasswordErrorText.text = "Passwords do not match"
            ok = false
        }

        if (nationalId.isEmpty()) {
            nationalIdErrorText.text = "ID is required"
            ok = false
        } else if (!nationalId.all { it.isDigit() }) {
            nationalIdErrorText.text = "ID must contain digits only"
            ok = false
        } else if (nationalId.length != 9) {
            // Optional, but makes sense for Israeli ID
            nationalIdErrorText.text = "ID must be exactly 9 digits"
            ok = false
        }



        return ok
    }

    // Simple password rules: at least 8 chars, upper, lower, digit, symbol
    private fun validatePassword(password: String): String? {
        if (password.length < 8) {
            return "Password must be at least 8 characters"
        }
        if (!password.any { it.isUpperCase() }) {
            return "Password must contain at least one uppercase letter"
        }
        if (!password.any { it.isLowerCase() }) {
            return "Password must contain at least one lowercase letter"
        }
        if (!password.any { it.isDigit() }) {
            return "Password must contain at least one digit"
        }
        if (!password.any { !it.isLetterOrDigit() }) {
            return "Password must contain at least one symbol"
        }
        return null
    }

    // Read which role radio button is selected and return the corresponding string
    private fun getSelectedRole(): String {
        val selectedId = roleGroup.checkedRadioButtonId
        return when (selectedId) {
            R.id.radioAdmin -> "company_admin"
            else -> "employee"
        }
    }
}
