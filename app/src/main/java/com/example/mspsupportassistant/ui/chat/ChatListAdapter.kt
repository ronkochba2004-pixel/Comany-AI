package com.example.mspsupportassistant.ui.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.mspsupportassistant.databinding.ItemChatSessionBinding
import com.example.mspsupportassistant.model.ChatSession
import androidx.appcompat.widget.PopupMenu
import com.example.mspsupportassistant.R
import android.view.Gravity
import android.view.View

class ChatListAdapter(
    private val onChatClicked: (ChatSession) -> Unit,
    private val onChatDeleteRequest: (ChatSession) -> Unit,
    private val onChatRenameRequest: (ChatSession) -> Unit
) : RecyclerView.Adapter<ChatListAdapter.ChatVH>()
{

    private val items = mutableListOf<ChatSession>()

    private var currentChatId: Int? = null
    fun setCurrentChat(chat: ChatSession?) {
        currentChatId = chat?.backendChatId
        notifyDataSetChanged()
    }

    class ChatVH(val binding: ItemChatSessionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatVH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemChatSessionBinding.inflate(inflater, parent, false)
        return ChatVH(binding)
    }

    override fun onBindViewHolder(holder: ChatVH, position: Int) {
        val chat = items[position]
        val b = holder.binding

        b.textChatTitle.text = chat.title


        val isSelected = (chat.backendChatId == currentChatId)

        if (isSelected) {
            b.root.setBackgroundResource(R.drawable.bg_chat_item_selected)
            b.buttonMore.visibility = View.VISIBLE
        } else {
            b.root.setBackgroundResource(android.R.color.transparent)
            b.buttonMore.visibility = View.GONE
        }


        // Click on the whole row opens the chat
        b.root.setOnClickListener {
            onChatClicked(chat)
        }

        // Click on the three-dots button shows a small menu under it
        b.buttonMore.setOnClickListener { view ->

            val popup = PopupMenu(
                view.context,
                view,
                Gravity.NO_GRAVITY,
                0,
                R.style.ChatPopupMenuStyle
            )

            // Inflate menu resource
            popup.menuInflater.inflate(R.menu.menu_chat_item_actions, popup.menu)

            // Force show icons in PopupMenu
            try {
                val fields = popup.javaClass.declaredFields
                for (field in fields) {
                    if (field.name == "mPopup") {
                        field.isAccessible = true
                        val menuPopupHelper = field.get(popup)
                        val classPopupHelper = Class.forName(menuPopupHelper.javaClass.name)
                        val setForceIcons = classPopupHelper.getMethod("setForceShowIcon", Boolean::class.javaPrimitiveType)
                        setForceIcons.invoke(menuPopupHelper, true)
                        break
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Make "Delete chat" text red
            val deleteItem = popup.menu.findItem(R.id.action_delete_chat)

            // Color the icon
            deleteItem.icon?.setTint(view.context.getColor(R.color.delete_text_color))

            val spanString = android.text.SpannableString(deleteItem.title)
            spanString.setSpan(
                android.text.style.ForegroundColorSpan(view.context.getColor(R.color.delete_text_color)),
                0,
                spanString.length,
                0
            )
            deleteItem.title = spanString

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_rename_chat -> {
                        onChatRenameRequest(chat)
                        true
                    }
                    R.id.action_delete_chat -> {
                        onChatDeleteRequest(chat)
                        true
                    }
                    else -> false
                }
            }

            popup.show()
        }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<ChatSession>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
