package com.example.mspsupportassistant

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mspsupportassistant.model.LocalUser
import com.example.mspsupportassistant.model.backendUserToLocalUser
import com.example.mspsupportassistant.network.RetrofitClient
import com.example.mspsupportassistant.ui.settings.DeleteUsersAdapter
import kotlinx.coroutines.launch

class DeleteUsersActivity : AppCompatActivity() {

    private lateinit var searchInput: EditText
    private lateinit var emptyStateText: TextView
    private lateinit var usersRecyclerView: RecyclerView
    private lateinit var adapter: DeleteUsersAdapter

    private var allUsers: List<LocalUser> = emptyList()
    private var currentFiltered: List<LocalUser> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_delete_users)

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
        adapter = DeleteUsersAdapter(onDeleteClicked = { user ->
            confirmDeleteUser(user)
        })
        usersRecyclerView.layoutManager = LinearLayoutManager(this)
        usersRecyclerView.adapter = adapter
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
                Toast.makeText(this@DeleteUsersActivity, "Failed to load users", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applyFilter(queryRaw: String) {
        val query = queryRaw.trim().lowercase()

        currentFiltered = if (query.isEmpty()) {
            allUsers
        } else {
            allUsers.filter { user ->
                val fullName = "${user.firstName} ${user.lastName}".lowercase()
                fullName.contains(query) ||
                        user.email.lowercase().contains(query) ||
                        user.nationalId.contains(query)
            }
        }

        adapter.setUsers(currentFiltered)
        updateEmptyState(currentFiltered.isEmpty())
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        emptyStateText.visibility = if (isEmpty) TextView.VISIBLE else TextView.GONE
        usersRecyclerView.visibility = if (isEmpty) RecyclerView.GONE else RecyclerView.VISIBLE
    }

    private fun confirmDeleteUser(user: LocalUser) {
        AlertDialog.Builder(this)
            .setTitle("Delete user")
            .setMessage("Delete ${user.firstName} ${user.lastName}?\nThis cannot be undone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                deleteUserFromServer(user)
            }
            .show()
    }

    private fun deleteUserFromServer(user: LocalUser) {
        lifecycleScope.launch {
            try {
                RetrofitClient.api.deleteUser(user.id)

                // Remove locally and refresh list
                allUsers = allUsers.filter { it.id != user.id }
                applyFilter(searchInput.text.toString())

                Toast.makeText(this@DeleteUsersActivity, "User deleted", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Toast.makeText(this@DeleteUsersActivity, "Failed to delete user", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
