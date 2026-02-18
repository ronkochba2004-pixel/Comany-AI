package com.example.mspsupportassistant

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class AdminActivity : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)


        val addUser = findViewById<LinearLayout>(R.id.rowAddUser)
        val deleteUser = findViewById<LinearLayout>(R.id.rowDeleteUser)
        val viewUsers = findViewById<LinearLayout>(R.id.rowViewUsers)

        addUser.setOnClickListener {
            val intent = Intent(this, AddUserActivity::class.java)
            startActivity(intent)
        }

        deleteUser.setOnClickListener {
            val intent = Intent(this, DeleteUsersActivity::class.java)
            startActivity(intent)
        }

        viewUsers.setOnClickListener {
            val intent = Intent(this, ViewAllUsersActivity::class.java)
            startActivity(intent)
        }
    }


}
