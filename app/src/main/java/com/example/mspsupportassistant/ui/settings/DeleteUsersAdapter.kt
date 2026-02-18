package com.example.mspsupportassistant.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.mspsupportassistant.R
import com.example.mspsupportassistant.model.LocalUser

class DeleteUsersAdapter(
    private val items: MutableList<LocalUser> = mutableListOf(),
    private val onDeleteClicked: (LocalUser) -> Unit
) : RecyclerView.Adapter<DeleteUsersAdapter.UserViewHolder>() {

    fun setUsers(newItems: List<LocalUser>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_row_delete, parent, false)
        return UserViewHolder(view, onDeleteClicked)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class UserViewHolder(
        itemView: View,
        private val onDeleteClicked: (LocalUser) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val deleteIcon: ImageView = itemView.findViewById(R.id.deleteIcon)
        private val nameText: TextView = itemView.findViewById(R.id.userNameText)
        private val emailText: TextView = itemView.findViewById(R.id.userEmailText)
        private val idText: TextView = itemView.findViewById(R.id.userIdText)
        private val roleText: TextView = itemView.findViewById(R.id.userRoleText)

        fun bind(user: LocalUser) {
            nameText.text = "${user.firstName} ${user.lastName}"
            emailText.text = user.email
            idText.text = "ID: ${user.nationalId}"
            roleText.text = "Role: ${user.role.name}"

            deleteIcon.setOnClickListener {
                onDeleteClicked(user)
            }
        }
    }
}
