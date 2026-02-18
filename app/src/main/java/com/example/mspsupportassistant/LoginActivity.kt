package com.example.mspsupportassistant

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.mspsupportassistant.model.LoginRequest
import com.example.mspsupportassistant.model.backendUserToLocalUser
import com.example.mspsupportassistant.network.RetrofitClient
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginErrorText: TextView
    private lateinit var loginButton: Button

    private companion object {
        private const val SKIP_LOGIN = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        // Dev skip: in Debug builds, go straight to MainActivity
        if (SKIP_LOGIN) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        initViews()
        setupValidation()
        setupLoginClick()
    }


    private fun initViews() {
        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        loginErrorText = findViewById(R.id.loginErrorText)
        loginButton = findViewById(R.id.loginButton)
    }

    private fun setupValidation() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validateInputs()
            }
        }

        emailInput.addTextChangedListener(watcher)
        passwordInput.addTextChangedListener(watcher)
    }

    private fun validateInputs() {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()

        loginButton.isEnabled = email.isNotEmpty() && password.isNotEmpty()
    }

    private fun setupLoginClick() {
        loginButton.setOnClickListener {
            performLogin()
        }
    }

    private fun performLogin() {
        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString()

        // Hide previous error
        loginErrorText.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val res = RetrofitClient.api.login(LoginRequest(email = email, password = password))

                if (res.ok && res.user_id != null) {
                    // Load full user info
                    val backendUser = RetrofitClient.api.getUser(res.user_id)
                    val localUser = backendUserToLocalUser(backendUser)
                    CurrentUserHolder.user = localUser

                    // Go to main
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                } else {
                    loginErrorText.visibility = View.VISIBLE
                }

            } catch (e: Exception) {
                // If backend returns 401, Retrofit throws HttpException
                loginErrorText.visibility = View.VISIBLE
            }
        }
    }

}
