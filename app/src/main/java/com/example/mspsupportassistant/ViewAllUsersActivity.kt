package com.example.mspsupportassistant

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mspsupportassistant.model.LocalUser
import com.example.mspsupportassistant.model.backendUserToLocalUser
import com.example.mspsupportassistant.network.RetrofitClient
import com.example.mspsupportassistant.ui.settings.UsersAdapter
import kotlinx.coroutines.launch

class ViewAllUsersActivity : AppCompatActivity() {

    private lateinit var searchInput: EditText
    private lateinit var emptyStateText: TextView
    private lateinit var usersRecyclerView: RecyclerView
    private lateinit var usersAdapter: UsersAdapter

    private var allUsers: List<LocalUser> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_all_users)

        initViews()
        setupRecycler()
        setupSearch()

        loadUsersFromServer()
    }

    private fun initViews() {
        searchInput = findViewById(R.id.searchInput)
        emptyStateText = findViewById(R.id.emptyStateText)
        usersRecyclerView = findViewById(R.id.usersRecyclerView)
    }

    private fun setupRecycler() {
        usersAdapter = UsersAdapter()
        usersRecyclerView.layoutManager = LinearLayoutManager(this)
        usersRecyclerView.adapter = usersAdapter
    }

    private fun setupSearch() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadUsersFromServer() {
        val currentUser = CurrentUserHolder.user
        if (currentUser == null) {
            Toast.makeText(this, "User not loaded", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                val backendUsers = RetrofitClient.api.getUsersForCompany(currentUser.companyId)
                allUsers = backendUsers.map { backendUserToLocalUser(it) }

                applyFilter(searchInput.text.toString())

            } catch (e: Exception) {
                Toast.makeText(this@ViewAllUsersActivity, "Failed to load users", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applyFilter(queryRaw: String) {
        val query = queryRaw.trim().lowercase()

        val filtered = if (query.isEmpty()) {
            allUsers
        } else {
            allUsers.filter { user ->
                val fullName = "${user.firstName} ${user.lastName}".lowercase()
                fullName.contains(query) ||
                        user.email.lowercase().contains(query) ||
                        user.nationalId.contains(query)
            }
        }

        usersAdapter.setUsers(filtered)
        updateEmptyState(filtered.isEmpty())
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        emptyStateText.visibility = if (isEmpty) TextView.VISIBLE else TextView.GONE
        usersRecyclerView.visibility = if (isEmpty) RecyclerView.GONE else RecyclerView.VISIBLE
    }
}
