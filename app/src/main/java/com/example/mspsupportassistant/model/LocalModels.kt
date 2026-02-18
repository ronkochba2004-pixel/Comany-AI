package com.example.mspsupportassistant.model


enum class MessageRole { USER, ASSISTANT, ASSISTANT_THINKING }

enum class UserRole { EMPLOYEE, COMPANY_ADMIN}

// One chat message model
data class ChatMessage(
    val id: Int,     // ID from the server
    val role: MessageRole,     // USER or ASSISTANT
    val text: String,   // the message content
    val ts: Long,        // timestamp (epoch millis)
    val senderId: Int? = null, // For user to user
    val imageUris: List<android.net.Uri> = emptyList()   // list of image URIs
)

data class ChatSession(
    val backendChatId: Int, // We are getting an ID from the backend
    var title: String,
    val chatType: String = "ai", // Default to "ai" for safety
    val messages: MutableList<ChatMessage> = mutableListOf()
)

data class LocalUser(
    val id: Int,
    val companyId: Int,
    val firstName: String,
    val lastName: String,
    val email: String,
    val nationalId: String,
    val role: UserRole
) {
    val isAdmin: Boolean
        get() = role != UserRole.EMPLOYEE
}

data class LocalCompany(
    val id: Int,
    val name: String
)


