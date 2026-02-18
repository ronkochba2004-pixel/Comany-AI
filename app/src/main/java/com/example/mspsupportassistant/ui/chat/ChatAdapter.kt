package com.example.mspsupportassistant.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.mspsupportassistant.databinding.ItemMessageBinding
import com.example.mspsupportassistant.model.ChatMessage
import com.example.mspsupportassistant.model.MessageRole
import com.example.mspsupportassistant.R
import coil.load
import io.noties.markwon.Markwon

// RecyclerView adapter that renders chat messages using item_message.xml
class ChatAdapter(private val currentUserId: Int) : RecyclerView.Adapter<ChatAdapter.MessageVH>() {

    // In-memory list of messages
    private val items = mutableListOf<ChatMessage>()

    private lateinit var markwon: Markwon


    // Optional: stable ids improve animations/scroll position
    // init { setHasStableIds(true) }

    // ViewHolder holds a binding reference to avoid findViewById calls
    class MessageVH(val binding: ItemMessageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageVH {
        // Inflate the XML row via ViewBinding
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemMessageBinding.inflate(inflater, parent, false)

        // Initialize Markwon once if not initialized
        if (!::markwon.isInitialized) {
            markwon = Markwon.create(parent.context)
        }

        return MessageVH(binding)
    }

    override fun onBindViewHolder(holder: MessageVH, position: Int) {
        val msg = items[position]
        val b = holder.binding


        // Logic to determine if the message belongs to the local user.
        // In AI chats, senderId is usually null or the current user.
        // In User-to-User chats, we check if the senderId matches our ID.
        val isMe = msg.role == MessageRole.USER && (msg.senderId == null || msg.senderId == currentUserId)


        if (!isMe) {

            // ----- Received Message (Assistant or Other User) -----
            b.containerBot.visibility = View.VISIBLE
            b.containerUser.visibility = View.GONE

            val isThinking = (msg.role == MessageRole.ASSISTANT_THINKING)


            if (isThinking) {
                b.textBotMessage.text = "AI is thinking..."
            } else {
                // USE MARKWON HERE for AI responses
                markwon.setMarkdown(b.textBotMessage, msg.text)
            }


            b.botThinkingSpinner.visibility = if (isThinking) View.VISIBLE else View.GONE

            // Assistant never shows user images
            b.userImagesScroll.visibility = View.GONE
            b.userImagesContainer.removeAllViews()

        } else {
            // ----- User message -----
            b.containerBot.visibility = View.GONE
            b.containerUser.visibility = View.VISIBLE

            b.textUserMessage.text = msg.text

            // Handle images display for the sender
            b.userImagesContainer.removeAllViews()
            if (msg.imageUris.isNotEmpty()) {
                b.userImagesScroll.visibility = View.VISIBLE
                renderUserImages(b, msg.imageUris)
            }
            else {
                b.userImagesScroll.visibility = View.GONE
            }
        }
    }

    // Helper method to keep onBindViewHolder clean
    private fun renderUserImages(b: ItemMessageBinding, uris: List<android.net.Uri>) {
        val density = b.root.resources.displayMetrics.density
        val sizePx = (72 * density).toInt()
        val marginPx = (4 * density).toInt()

        for (uri in uris) {
            val imageView = android.widget.ImageView(b.root.context)
            val params = android.widget.LinearLayout.LayoutParams(sizePx, sizePx)
            params.rightMargin = marginPx
            imageView.layoutParams = params
            imageView.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            imageView.load(uri.toString())
            imageView.background = androidx.core.content.ContextCompat.getDrawable(
                b.root.context,
                R.drawable.bg_image_preview
            )
            imageView.clipToOutline = true
            b.userImagesContainer.addView(imageView)
        }
    }


    override fun getItemCount(): Int = items.size


    // Derive a stable id from the message id (hashCode is OK for demo)
   // override fun getItemId(position: Int): Long {
    //    return items[position].id.hashCode().toLong()
    //}

    // Append a single message and notify RecyclerView
    fun addMessage(message: ChatMessage) {
        val insertAt = items.size
        items.add(message)
        notifyItemInserted(insertAt)
    }

    // Replace the whole list (if needed later)

    fun setMessages(newItems: List<ChatMessage>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()  // Refresh everything instantly — no animations
    }

    fun prependMessages(newItems: List<ChatMessage>) {
        if (newItems.isEmpty()) return
        items.addAll(0, newItems)
        notifyItemRangeInserted(0, newItems.size)
    }

    fun removeFirstMatching(predicate: (ChatMessage) -> Boolean) {
        val index = items.indexOfFirst(predicate)
        if (index >= 0) {
            items.removeAt(index)
            notifyItemRemoved(index)
        }
    }
    fun updateMessageAt(index: Int, message: ChatMessage) {
        items[index] = message
        notifyItemChanged(index)
    }


}
