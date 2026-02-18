package com.example.mspsupportassistant

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.mspsupportassistant.databinding.ActivityMainBinding
import com.example.mspsupportassistant.model.ChatMessage
import com.example.mspsupportassistant.model.ChatSession
import com.example.mspsupportassistant.model.SendMessageRequest
import com.example.mspsupportassistant.model.backendMessageToChatMessage

import com.example.mspsupportassistant.ui.chat.ChatAdapter
import com.example.mspsupportassistant.ui.chat.ChatListAdapter

import com.google.android.material.bottomsheet.BottomSheetDialog
import android.widget.TextView
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import com.example.mspsupportassistant.model.CreateChatRequest
import com.example.mspsupportassistant.model.RenameChatRequest
import com.example.mspsupportassistant.model.backendCompanyToLocalCompany
import com.example.mspsupportassistant.network.RetrofitClient
import com.example.mspsupportassistant.ui.settings.SettingsBottomSheetDialogFragment
import kotlinx.coroutines.launch
import okhttp3.MultipartBody

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import androidx.core.net.toUri
import androidx.recyclerview.widget.RecyclerView
import com.example.mspsupportassistant.model.CreateUserChatRequest
import com.example.mspsupportassistant.model.MessageRole
import com.example.mspsupportassistant.model.backendChatToChatSession
import kotlinx.coroutines.isActive
class MainActivity : AppCompatActivity() {

    // ViewBinding reference for activity_main.xml
    private lateinit var binding: ActivityMainBinding

    // Chat adapter for the RecyclerView
    private lateinit var chatAdapter: ChatAdapter

    // Handler for delayed assistant replies (demo only)
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    // The images that were picked to send to the AI
    private val selectedImages = mutableListOf<android.net.Uri>()

    //Temp Photo Uri for the taken photo
    private var tempPhotoUri: android.net.Uri? = null

    //All the chats that have been started
    private val chatSessions = mutableListOf<ChatSession>()


    //The current chat that is being used
    private var currentChat: ChatSession? = null
    // last message we seen from a current chat

    private var isSendLocked: Boolean = false

    // Chat list adapter for the drawer
    private lateinit var chatListAdapter: ChatListAdapter

    // Makes sure the scroll down button will not pop up when sending a message
    private var isAutoScrollingToBottom = false

    // Job for background listening in user-to-user chats
    private var userPollingJob: kotlinx.coroutines.Job? = null


    // We need this to get the last message we sent in a chat
    private val lastSeenMessageIdByChat = mutableMapOf<Int, Int>()
    private val awaitingAssistantByChat = mutableMapOf<Int, Boolean>()

    private fun getLastSeen(chatId: Int): Int = lastSeenMessageIdByChat[chatId] ?: 0
    private fun setLastSeen(chatId: Int, id: Int) { lastSeenMessageIdByChat[chatId] = id }
    private fun isAwaiting(chatId: Int): Boolean = awaitingAssistantByChat[chatId] == true
    private fun setAwaiting(chatId: Int, awaiting: Boolean) { awaitingAssistantByChat[chatId] = awaiting }


    // We need this to get the oldest message we loaded
    private val oldestLoadedMessageIdByChat = mutableMapOf<Int, Int?>()

    private fun getOldestLoaded(chatId: Int): Int? = oldestLoadedMessageIdByChat[chatId]
    private fun setOldestLoaded(chatId: Int, id: Int?) { oldestLoadedMessageIdByChat[chatId] = id }

    // We need this to not load the same messages twice
    private val isLoadingOlderByChat = mutableMapOf<Int, Boolean>()

    private fun isLoadingOlder(chatId: Int): Boolean = isLoadingOlderByChat[chatId] == true
    private fun setLoadingOlder(chatId: Int, loading: Boolean) { isLoadingOlderByChat[chatId] = loading }


    ////////////////////////////////// Messages START /////////////////////////////////////////////////////
    private fun sendUserMessage() {
        val chat = currentChat ?: return
        val backendChatId = chat.backendChatId

        // Prevent sending while this chat is awaiting assistant
        if (isAwaiting(backendChatId)) return

        val rawText = binding.messageInput.text.toString()
        val text = rawText.trim()

        // If empty and no images, do nothing
        if (text.isEmpty() && selectedImages.isEmpty()) {
            return
        }

        val timestamp = System.currentTimeMillis()

        // Copy images list (local URIs)
        val imagesToSend: List<Uri> = selectedImages.toList()

        // Clear UI immediately
        binding.messageInput.text?.clear()
        selectedImages.clear()
        binding.imagePreviewList.removeAllViews()
        binding.imagePreviewContainer.visibility = View.GONE


        val localId = -timestamp.toInt()

        // show user message immediately (optimistic UI)
        val localUserMsg = ChatMessage(
            id = localId,              // temporary local id (negative)
            role = MessageRole.USER,
            text = text,
            ts = timestamp,                       // temporary local timesemp
            imageUris = imagesToSend              // local URIs
        )

        appendMessageToChat(chat, localUserMsg)


        // mark awaiting for THIS chat
        setAwaiting(backendChatId, true)

        // lock send only if we are still on this chat screen
        if (currentChat?.backendChatId == backendChatId) {
            LockSend(true)
        }


        lifecycleScope.launch {
            try {
                // 1) Upload images first (if any) -> get URLs
                val uploadedUrls: List<String> =
                    if (imagesToSend.isNotEmpty()) {
                        val parts = imagesToSend.mapIndexed { index, uri ->
                            uriToMultipartPart(uri, partName = "images", fallbackFileName = "image_$index.jpg")
                        }

                        val uploadResp = RetrofitClient.api.uploadImages(parts)
                        uploadResp.image_urls
                    } else {
                        emptyList()
                    }

                // 2) Send the message with image URLs
                val request = SendMessageRequest(
                    chat_id = backendChatId,
                    sender = "user",
                    sender_id = CurrentUserHolder.user!!.id,
                    text = text,
                    image_urls = uploadedUrls
                )

                val backendMessage = RetrofitClient.api.sendMessage(request)

                // update lastSeen for THIS chat using server message_id
                setLastSeen(backendChatId, maxOf(getLastSeen(backendChatId), backendMessage.message_id))

                // 3) Map backend -> UI model (keep your local image URIs behavior)
                val base = RetrofitClient.BASE_URL.removeSuffix("/")
                val imageUrisFromServer = uploadedUrls.map { u ->
                    val full = if (u.startsWith("http")) u else base + u
                    full.toUri()
                }

                //  Update optimistic message: id + ts + server image URLs
                updateMessageInChat(
                    chat = chat,
                    oldId = localId,
                    newId = backendMessage.message_id,
                    newTs = backendMessage.timestamp,
                    newImageUris = imageUrisFromServer
                )



                // Only show thinking and wait for assistant if the chat is an AI chat
                if (chat.chatType != "ai") {
                    // This is a USER chat - Skip AI logic
                    setAwaiting(backendChatId, false)
                    if (currentChat?.backendChatId == backendChatId) {
                        LockSend(false)
                    }
                } else {
                    val thinkingMsg = makeThinkingMessage()
                    appendMessageToChat(chat, thinkingMsg)

                    // keep your behavior
                    moveChatToTop(chat)

                    // 4) Wait for assistant reply for THIS chat
                    waitForAssistantReply(chat)

                    // 5) done waiting
                    setAwaiting(backendChatId, false)

                    // unlock only if still in this chat
                    if (currentChat?.backendChatId == backendChatId) {
                        LockSend(false)
                    }
                }


            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error sending message", Toast.LENGTH_SHORT).show()

                setAwaiting(backendChatId, false)
                if (currentChat?.backendChatId == backendChatId) {
                    LockSend(false)
                }
            }
        }
    }

    private suspend fun waitForAssistantReply(chat: ChatSession): Boolean {
        val chatId = chat.backendChatId
        val maxTries = 15
        val delayMs = 1000L

        repeat(maxTries) {
            kotlinx.coroutines.delay(delayMs)

            val afterId = getLastSeen(chatId)
            val newMessages = RetrofitClient.api.getMessagesAfter(chatId, afterId)

            if (newMessages.isNotEmpty()) {
                newMessages.forEach { dto ->

                    // Skip user messages from server (we already show them optimistically)
                    if (dto.sender == "user") {
                        setLastSeen(chatId, maxOf(getLastSeen(chatId), dto.message_id))
                        return@forEach
                    }

                    setLastSeen(chatId, maxOf(getLastSeen(chatId), dto.message_id))

                    // assistant arrived: replace thinking placeholder
                    removeThinkingPlaceholder(chat)
                    val uiMsg = backendMessageToChatMessage(dto)
                    appendMessageToChat(chat, uiMsg)

                    return true
                }
            }
        }
        return false
    }

    private fun appendMessageToUi(message: ChatMessage) {
        isAutoScrollingToBottom = true
        binding.btnScrollToBottom.visibility = View.GONE

        chatAdapter.addMessage(message)

        binding.chatRecyclerView.post {
            binding.chatRecyclerView.smoothScrollToPosition(chatAdapter.itemCount - 1)

            binding.chatRecyclerView.post {
                isAutoScrollingToBottom = false
                binding.btnScrollToBottom.visibility =
                    if (!binding.chatRecyclerView.canScrollVertically(1)) View.GONE else View.VISIBLE
            }
        }
    }

    private fun appendMessageToChat(chat: ChatSession, message: ChatMessage) {
        chat.messages.add(message)

        // Update visible UI only if this chat is currently open
        if (currentChat?.backendChatId == chat.backendChatId) {
            appendMessageToUi(message)
        }
    }

    private fun updateMessageInChat(
        chat: ChatSession,
        oldId: Int,
        newId: Int,
        newTs: Long,
        newImageUris: List<Uri>? = null
    ) {
        val idx = chat.messages.indexOfFirst { it.id == oldId }
        if (idx == -1) return

        val current = chat.messages[idx]
        val updated = current.copy(
            id = newId,
            ts = newTs,
            imageUris = newImageUris ?: current.imageUris
        )

        chat.messages[idx] = updated

        if (currentChat?.backendChatId == chat.backendChatId) {
            chatAdapter.updateMessageAt(idx, updated)
        }
    }


    private fun LockSend(locked: Boolean) {
        isSendLocked = locked
        binding.sendButton.isEnabled = !locked
    }

    private fun loadOlderMessagesForCurrentChat() {
        val chat = currentChat ?: return
        val chatId = chat.backendChatId
        if (isLoadingOlder(chatId)) return

        val oldestLoadedId = getOldestLoaded(chatId) ?: return  // no cursor -> nothing to load

        setLoadingOlder(chatId, true)

        val layoutManager = binding.chatRecyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
            ?: run {
                setLoadingOlder(chatId, false)
                return
            }

        // Anchor: keep user's view stable after prepend
        val firstVisiblePos = layoutManager.findFirstVisibleItemPosition()
        val firstVisibleView = layoutManager.findViewByPosition(firstVisiblePos)
        val topOffset = firstVisibleView?.top ?: 0

        lifecycleScope.launch {
            try {
                val backendOlder = RetrofitClient.api.getMessagesBefore(
                    chatId = chatId,
                    beforeId = oldestLoadedId,
                    limit = 30
                )

                val olderMsgs = backendOlder
                    .map { backendMessageToChatMessage(it) }
                    // extra safety: avoid duplicates
                    .filter { it.id < oldestLoadedId }

                if (olderMsgs.isNotEmpty()) {
                    // Update data first
                    chat.messages.addAll(0, olderMsgs)

                    // Update cursor (new oldest)
                    setOldestLoaded(chatId, chat.messages.firstOrNull()?.id)

                    // Update UI only if we're still viewing this chat
                    if (currentChat?.backendChatId == chatId) {
                        val addedCount = olderMsgs.size

                        // Prepend in adapter
                        chatAdapter.prependMessages(olderMsgs)

                        // Restore scroll position
                        layoutManager.scrollToPositionWithOffset(firstVisiblePos + addedCount, topOffset)
                    }
                }

            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error loading older messages", Toast.LENGTH_SHORT).show()
            } finally {
                setLoadingOlder(chatId, false)
            }
        }
    }

    private fun makeThinkingMessage(): ChatMessage {
        return ChatMessage(
            id = -1, // local-only placeholder
            role = MessageRole.ASSISTANT_THINKING,
            text = "",
            ts = System.currentTimeMillis(),
            imageUris = emptyList()
        )
    }

    private fun removeThinkingPlaceholder(chat: ChatSession) {
        // Remove from data
        val idx = chat.messages.indexOfFirst { it.role == MessageRole.ASSISTANT_THINKING }
        if (idx >= 0) chat.messages.removeAt(idx)

        // Remove from UI only if this chat is visible
        if (currentChat?.backendChatId == chat.backendChatId) {
            chatAdapter.removeFirstMatching { it.role == MessageRole.ASSISTANT_THINKING }
        }
    }


    private fun startUserChatPolling() {
        // Stop any previous job to avoid duplicates
        stopUserChatPolling()

        val chat = currentChat ?: return
        // Only poll if it's a user-to-user chat
        if (chat.chatType != "user") return

        userPollingJob = lifecycleScope.launch {
            while (isActive) {
                // Safety: if the user switched chat while we were delaying, stop this loop
                if (currentChat?.backendChatId != chat.backendChatId) break

                try {
                    val lastId = getLastSeen(chat.backendChatId)
                    val newMessages = RetrofitClient.api.getMessagesAfter(chat.backendChatId, lastId)

                    if (newMessages.isNotEmpty()) {
                        newMessages.forEach { dto ->
                            // Skip our own messages (already added optimistically)
                            if (dto.sender_id == CurrentUserHolder.user?.id) {
                                setLastSeen(chat.backendChatId, maxOf(getLastSeen(chat.backendChatId), dto.message_id))
                                return@forEach
                            }

                            setLastSeen(chat.backendChatId, maxOf(getLastSeen(chat.backendChatId), dto.message_id))

                            val uiMsg = backendMessageToChatMessage(dto)
                            appendMessageToChat(chat, uiMsg)
                        }
                    }
                } catch (e: Exception) {
                    // Silent fail for background polling
                }

                kotlinx.coroutines.delay(4000) // Poll every 4 seconds
            }
        }
    }

    private fun stopUserChatPolling() {
        userPollingJob?.cancel()
        userPollingJob = null
    }


    ////////////////////////////////// Messages END /////////////////////////////////////////////////////
    private fun createNewChat(initialMessage: ChatMessage? = null) {
        lifecycleScope.launch {
            try {
                // Decide the chat title on the client
                val localTitle = "Chat #${chatSessions.size + 1}"

                val currentUser = CurrentUserHolder.user
                if (currentUser == null) {
                    Toast.makeText(this@MainActivity, "User not loaded yet", Toast.LENGTH_SHORT).show()
                    return@launch  // <-- labeled return, exit from this coroutine
                }

                // 1) Call backend: POST /create_chat with title in the body
                val backendChat = RetrofitClient.api.createChat(
                    CreateChatRequest(title = localTitle, user_id = currentUser.id)  // <-- use currentUser
                )

                // 2) Create local ChatSession with backend chat_id and title from server
                val chat = backendChatToChatSession(backendChat)

                // 3) If we got an initial message, add it locally
                if (initialMessage != null) {
                    chat.messages.add(initialMessage)
                }

                // 4) Add to local list and set as current
                chatSessions.add(0, chat)
                currentChat = chat

                // 5) Update UI with current chat messages (might be empty)
                chatAdapter.setMessages(chat.messages)
                if (chatAdapter.itemCount > 0) {
                    binding.chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
                }

                // 6) Refresh drawer list
                refreshChatListInDrawer()

                // 7) Highlight this chat in the drawer
                chatListAdapter.setCurrentChat(currentChat)

            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error creating new chat", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshChatListInDrawer() {
        chatListAdapter.submitList(chatSessions.toList())
    }

    private fun openChat(chatId: Int) {
        val chat = chatSessions.find { it.backendChatId == chatId } ?: return
        currentChat = chat

        // Update drawer highlight
        chatListAdapter.setCurrentChat(currentChat)

        // Close drawer immediately
        binding.drawerLayout.closeDrawer(GravityCompat.START)

        // If we already have messages cached, just show them
        if (chat.messages.isNotEmpty()) {
            chatAdapter.setMessages(chat.messages)
            if (chatAdapter.itemCount > 0) {
                binding.chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
            }

            // Init per-chat cursors
            setLastSeen(chat.backendChatId, chat.messages.lastOrNull()?.id ?: 0)
            setOldestLoaded(chat.backendChatId, chat.messages.firstOrNull()?.id)

            // lock/unlock send based on whether THIS chat is awaiting assistant
            LockSend(isAwaiting(chat.backendChatId))

            stopUserChatPolling()
            startUserChatPolling()
            return
        }

        // Otherwise, load only the latest page (e.g., last 30 messages)
        lifecycleScope.launch {
            try {
                val backendMessages = RetrofitClient.api.getMessagesBefore(
                    chatId = chat.backendChatId,
                    beforeId = null,
                    limit = 30
                )
                val chatMessages = backendMessages.map { backendMessageToChatMessage(it) }

                chat.messages.clear()
                chat.messages.addAll(chatMessages)

                chatAdapter.setMessages(chat.messages)
                if (chatAdapter.itemCount > 0) {
                    binding.chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
                }

                // Init per-chat cursors
                setLastSeen(chat.backendChatId, chat.messages.lastOrNull()?.id ?: 0)
                setOldestLoaded(chat.backendChatId, chat.messages.firstOrNull()?.id)

                // Lock/unlock send based on whether THIS chat is awaiting assistant
                LockSend(isAwaiting(chat.backendChatId))

                // Start polling if needed
                stopUserChatPolling()
                startUserChatPolling()

            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error loading chat messages", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private suspend fun loadExistingChatsFromServer(): Int {
        return try {
            val currentUser = CurrentUserHolder.user
            if (currentUser == null) {
                Toast.makeText(this@MainActivity, "User not loaded yet", Toast.LENGTH_SHORT).show()
                0
            } else {
                // 1) Get list of chats from backend
                val backendChats = RetrofitClient.api.getChats(currentUser.id)

                // 2) Rebuild local sessions
                chatSessions.clear()

                backendChats.forEach { dto ->
                    val chat = backendChatToChatSession(dto)
                    chatSessions.add(chat)
                }

                // 3) Update UI
                refreshChatListInDrawer()

                // return count
                chatSessions.size
            }
        } catch (e: Exception) {
            Toast.makeText(this@MainActivity, "Error loading chats from server", Toast.LENGTH_SHORT).show()
            0
        }
    }


    private fun moveChatToTop(chat: ChatSession) {
        val index = chatSessions.indexOf(chat)
        if (index > 0) {
            chatSessions.removeAt(index)
            chatSessions.add(0, chat)
            refreshChatListInDrawer()
            chatListAdapter.setCurrentChat(chat)
        }
    }

    private fun setupScrollToBottomButton() {

        fun isAtBottom(): Boolean {
            return !binding.chatRecyclerView.canScrollVertically(1)
        }

        fun updateVisibility() {
            if (isAutoScrollingToBottom) {
                binding.btnScrollToBottom.visibility = View.GONE
                return
            }
            binding.btnScrollToBottom.visibility = if (isAtBottom()) View.GONE else View.VISIBLE
        }

        // IMPORTANT: prevent accumulating listeners
        //binding.chatRecyclerView.clearOnScrollListeners()

        binding.chatRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // During auto scroll/layout shifts -> never show
                if (isAutoScrollingToBottom || dy > 0) {
                    binding.btnScrollToBottom.visibility = View.GONE
                    return
                }
                updateVisibility()
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    updateVisibility()
                }
            }
        })

        binding.btnScrollToBottom.setOnClickListener {
            val count = chatAdapter.itemCount
            if (count > 0) {
                isAutoScrollingToBottom = true
                binding.btnScrollToBottom.visibility = View.GONE

                binding.chatRecyclerView.smoothScrollToPosition(count - 1)

                binding.chatRecyclerView.post {
                    isAutoScrollingToBottom = false
                    updateVisibility()
                }
            }
        }

        binding.chatRecyclerView.post { updateVisibility() }
    }

    private fun confirmDeleteChat(chat: ChatSession) {
        AlertDialog.Builder(this)
            .setTitle("Delete chat")
            .setMessage("Are you sure you want to delete this chat?")
            .setPositiveButton("Delete") { _, _ ->
                deleteChatFromServer(chat)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteChatFromServer(chat: ChatSession) {
        lifecycleScope.launch {
            try {
                // 1) Delete from backend
                RetrofitClient.api.deleteChat(chat.backendChatId)

                // 2) Remove from local list
                chatSessions.remove(chat)

                // 3) If no chats left → create a new one automatically
                if (chatSessions.isEmpty()) {

                    // Clear UI
                    chatAdapter.setMessages(emptyList())
                    currentChat = null

                    // Create a new chat
                    createNewChat()

                } else {
                    // 4) Otherwise, switch to the first remaining chat
                    currentChat = chatSessions.first()

                    // Show its messages
                    openChat(currentChat!!.backendChatId)

                    // Refresh drawer
                    refreshChatListInDrawer()
                    chatListAdapter.setCurrentChat(currentChat)
                }

            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Failed to delete chat", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showRenameChatDialog(chat: ChatSession) {
        val editText = EditText(this).apply {
            setText(chat.title)
            setSelection(chat.title.length) // Select all text
            setPadding(50, 40, 50, 40)
        }

        AlertDialog.Builder(this)
            .setTitle("Rename chat")
            .setView(editText)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("OK") { _, _ ->
                val newTitle = editText.text.toString().trim()
                if (newTitle.isNotEmpty()) {
                    renameChat(chat, newTitle)
                }
            }
            .show()
    }

    private fun showNewUserChatDialog() {
        // 1. Create the EditText for National ID input
        val input = android.widget.EditText(this).apply {
            hint = "Enter National ID"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            // Optional: Add some padding so it doesn't touch the dialog edges
            setPadding(50, 40, 50, 40)
        }

        // 2. Build the Dialog using the same style as your delete dialog
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Start User Chat")
            .setMessage("Please enter the National ID of the user you want to chat with:")
            .setView(input) // Add the input field to the dialog
            .setPositiveButton("Create") { _, _ ->
                val nationalId = input.text.toString().trim()
                if (nationalId.isNotEmpty()) {
                    // Start the process: Search user -> Create chat
                    performUserSearchAndCreateChat(nationalId)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performUserSearchAndCreateChat(nationalId: String) {
        lifecycleScope.launch {
            try {
                // 1. Find the target user
                val targetUser = RetrofitClient.api.getUserByNationalId(nationalId)
                val myId = CurrentUserHolder.user?.id ?: return@launch

                // 2. Create the chat on the server
                val request = CreateUserChatRequest(
                    user_id = myId,
                    participant_id = targetUser.user_id,
                    title = "Chat with ${targetUser.first_name}"
                )
                val backendChat = RetrofitClient.api.createUserChat(request)

                // 3. Create local ChatSession (Similar to your createNewChat logic)
                val chat = backendChatToChatSession(backendChat)

                // Note: For User Chats, we don't have a specific chat_type in the DTO yet,
                // but we know it's a user chat because we just created it here.

                // 4. Update local list and set as current
                chatSessions.add(0, chat)
                currentChat = chat

                // 5. Update UI and Drawer
                chatAdapter.setMessages(chat.messages)
                refreshChatListInDrawer()
                chatListAdapter.setCurrentChat(currentChat)

                // Close drawer to show the new chat immediately
                binding.drawerLayout.closeDrawers()

            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "User not found or error", Toast.LENGTH_SHORT).show()
            }
        }
    }




    private fun renameChat(chat: ChatSession, newTitle: String) {
        lifecycleScope.launch {
            try {
                // Send PATCH request to backend to update the chat title
                val updatedChat = RetrofitClient.api.renameChat(
                    chat.backendChatId,
                    RenameChatRequest(title = newTitle)
                )

                // Update the local chat session with the new title returned from backend
                chat.title = updatedChat.title

                // Refresh the drawer list to show the updated title
                refreshChatListInDrawer()

                // Keep the same chat selected in the drawer
                chatListAdapter.setCurrentChat(chat)

            } catch (e: Exception) {
                // Show error if rename failed (e.g., network or backend issue)
                Toast.makeText(this@MainActivity, "Error renaming chat", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setLocalUser() {
        lifecycleScope.launch {
            try {
                val currentUser = CurrentUserHolder.user
                if (currentUser == null) {
                    Toast.makeText(this@MainActivity, "No logged-in user", Toast.LENGTH_LONG).show()
                    finish()
                    return@launch
                }

                // Load company only if missing
                if (CurrentUserHolder.company == null) {
                    val backendCompany = RetrofitClient.api.getCompany(currentUser.companyId)
                    val localCompany = backendCompanyToLocalCompany(backendCompany)
                    CurrentUserHolder.company = localCompany
                }

                val count = loadExistingChatsFromServer()

                if (count == 0) {
                    createNewChat()
                } else {
                    // open the most recent chat (first in list)
                    val mostRecentChat = chatSessions.first()
                    openChat(mostRecentChat.backendChatId)
                }

            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Failed to load current user", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupChatPaginationScrollListener() {
        binding.chatRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val firstVisible = lm.findFirstVisibleItemPosition()

                if (firstVisible <= 2) {
                    loadOlderMessagesForCurrentChat()
                }
            }

        })
    }





    ///////////////////////////////// On Create SART ////////////////////////////////////////////////////////////////


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflate layout via ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ----- Left drawer: chat list setup -----
        chatListAdapter = ChatListAdapter(
            onChatClicked = { chat -> openChat(chat.backendChatId) },
            onChatDeleteRequest = { chat -> confirmDeleteChat(chat) },
            onChatRenameRequest = { chat -> showRenameChatDialog(chat) }
        )

        binding.chatsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = chatListAdapter
        }

        // ----- Drawer top buttons -----

        // Open/close drawer with the top menu button
        binding.menuButton.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        // "New chat" in the left drawer
        binding.buttonNewChat.setOnClickListener {
            createNewChat()
            binding.messageInput.text?.clear()
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }

        // "Settings" in the left drawer
        binding.buttonSettings.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)

            // give the drawer time to close before opening the settings sheet
            binding.drawerLayout.postDelayed({ openSettingsBottomSheet() }, 200)
        }

        // *User Chat* button in the drawer
        binding.buttonNewUserChat.setOnClickListener {
            // Close the drawer before showing the dialog
            binding.drawerLayout.closeDrawers()
            // Call the dialog function we created
            showNewUserChatDialog()
        }


        // Plus button (media picker)
        binding.plusButton.setOnClickListener {
            showMediaPickerSheet()
        }

        // ----- Main chat RecyclerView (center) -----

        // Create adapter instance for messages
        chatAdapter = ChatAdapter(currentUserId = CurrentUserHolder.user!!.id)

        // Configure RecyclerView: vertical list, stacked from bottom like a chat
        binding.chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                stackFromEnd = true // show items starting from bottom
            }
            adapter = chatAdapter
        }

        setupScrollToBottomButton()
        setupChatPaginationScrollListener()


        // setting who is the user we use
        setLocalUser()


        // Send button for user messages
        binding.sendButton.setOnClickListener {
            sendUserMessage()
        }
    }

    override fun onResume() {
        super.onResume()
        startUserChatPolling()
    }

    override fun onPause() {
        super.onPause()
        stopUserChatPolling()
    }


////////////////////////////////// On Create END /////////////////////////////////////////////////



////////////////////////////////// Settings START /////////////////////////////////////////////////////////////
    fun openSettingsBottomSheet() {
        if(CurrentUserHolder.user == null){
            return
        }

        val isAdmin = CurrentUserHolder.user!!.isAdmin
        Log.e("SettingsDebug", "isAdmin = $isAdmin")
        val email = CurrentUserHolder.user!!.email
        val name = CurrentUserHolder.user!!.firstName + " " + CurrentUserHolder.user!!.lastName
        val company = CurrentUserHolder.company!!.name



        val dialog = SettingsBottomSheetDialogFragment.newInstance(isAdmin, email, name, company)
        dialog.show(supportFragmentManager, "settings_bottom_sheet")
    }

////////////////////////////////// Settings END /////////////////////////////////////////////////////




////////////////////////////////// Camera and media START /////////////////////////////////////////////////////
    private fun createMediaPickerView(dialog: BottomSheetDialog): View {
        val parent = findViewById<ViewGroup>(android.R.id.content)

        val view = layoutInflater.inflate(
            R.layout.bottom_sheet_media,
            parent,
            false
        )


        val takePhoto = view.findViewById<TextView>(R.id.optionTakePhoto)
        val chooseGallery = view.findViewById<TextView>(R.id.optionChooseGallery)

    takePhoto.setOnClickListener {
        dialog.dismiss()

        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            openCamera()
        } else {
            requestCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }


    chooseGallery.setOnClickListener {
            dialog.dismiss()
            openGallery()
        }

        return view
    }

    private fun showMediaPickerSheet() {
        val dialog = BottomSheetDialog(this)

        val view = createMediaPickerView(dialog)

        dialog.setContentView(view)
        dialog.show()
    }

    private val pickImageLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.GetContent()
        ) { uri: android.net.Uri? ->
            if (uri != null) {
                addImagePreview(uri)
            }
        }

    private val takePhotoLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.TakePicture()
        ) { success: Boolean ->
            if (success && tempPhotoUri != null) {
                addImagePreview(tempPhotoUri!!)
            } else {
                //Toast.makeText(this, "Failed to take photo", Toast.LENGTH_SHORT).show()
                //We will not show this for now
            }
        }

    private val requestCameraPermissionLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { granted: Boolean ->
            if (granted) {
                openCamera()
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    private fun openCamera() {
        val uri = createImageUri()
        if (uri == null) {
            Toast.makeText(this, "Could not create image file", Toast.LENGTH_SHORT).show()
            return
        }
        tempPhotoUri = uri
        takePhotoLauncher.launch(uri)
    }

    private fun createImageUri(): android.net.Uri? {
        return try {
            val imagesDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            val file = java.io.File.createTempFile(
                "photo_", ".jpg",
                imagesDir
            )

            androidx.core.content.FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }


    private fun openGallery() {
        // Open system picker for images
        pickImageLauncher.launch("image/*")
    }


    private fun addImagePreview(uri: android.net.Uri) {
        // Limit to 5 images
        if (selectedImages.size >= 5) {
            Toast.makeText(this, "You can attach up to 5 images", Toast.LENGTH_SHORT).show()
            return
        }

        selectedImages.add(uri)

        val density = resources.displayMetrics.density
        val sizePx = (96 * density).toInt()

        // Frame that will contain the image and the close (X) button
        val frame = android.widget.FrameLayout(this)
        val frameParams = android.widget.LinearLayout.LayoutParams(sizePx, sizePx)
        frameParams.rightMargin = (8 * density).toInt()
        frame.layoutParams = frameParams

        // The image thumbnail
        val imageView = android.widget.ImageView(this)
        val imageParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        )
        imageView.layoutParams = imageParams
        imageView.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        imageView.setImageURI(uri)

        imageView.background = androidx.core.content.ContextCompat.getDrawable(
            this,
            R.drawable.bg_image_preview
        )
        imageView.clipToOutline = true



        // The X button in the top-right corner
        val closeBtn = android.widget.ImageView(this)
        closeBtn.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
        val closeParams = android.widget.FrameLayout.LayoutParams(
            (28 * density).toInt(),
            (28 * density).toInt(),
            android.view.Gravity.TOP or android.view.Gravity.END
        )
        closeParams.marginEnd = (4 * density).toInt()
        closeParams.topMargin = (4 * density).toInt()
        closeBtn.layoutParams = closeParams
        closeBtn.setPadding(4, 4, 4, 4)
        closeBtn.setBackgroundColor("#66000000".toColorInt()) // semi-transparent dark background

        closeBtn.setOnClickListener {
            // Remove this preview from the layout and from the list
            binding.imagePreviewList.removeView(frame)
            selectedImages.remove(uri)

            if (selectedImages.isEmpty()) {
                binding.imagePreviewContainer.visibility = View.GONE
            }
        }

        // Build the final preview item
        frame.addView(imageView)
        frame.addView(closeBtn)

        // Add to the horizontal list
        binding.imagePreviewList.addView(frame)
        binding.imagePreviewContainer.visibility = View.VISIBLE
    }



    private fun uriToMultipartPart(
        uri: Uri,
        partName: String,
        fallbackFileName: String
    ): MultipartBody.Part {
        val inputStream = contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open input stream for URI: $uri")

        val bytes = inputStream.use { it.readBytes() }

        val mimeType = contentResolver.getType(uri) ?: "image/*"
        val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())

        return MultipartBody.Part.createFormData(partName, fallbackFileName, requestBody)
    }

////////////////////////////////// Camera and media END /////////////////////////////////////////////////////






}
