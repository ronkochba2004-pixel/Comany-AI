package com.example.mspsupportassistant.model

data class SendMessageRequest(
    val chat_id: Int,
    val sender: String,
    val sender_id: Int?, // For user to user
    val text: String,
    val image_urls: List<String> = emptyList()
)


data class BackendMessage(
    val message_id: Int,
    val sender: String,
    val sender_id: Int?, // For user to user
    val text: String,
    val timestamp: Long,
    val image_urls: List<String> = emptyList()
)

data class BackendChatDto(
    val chat_id: Int,
    val title: String,
    val chat_type: String
)

data class CreateChatRequest(
    val title: String,
    val user_id: Int
)

// For user to user
data class CreateUserChatRequest(
    val user_id: Int,
    val participant_id: Int,
    val title: String = "Private chat with $participant_id"
)

data class RenameChatRequest(
    val title: String
)

data class BackendUserDto(
    val user_id: Int,
    val company_id: Int,
    val email: String,
    val first_name: String,
    val last_name: String,
    val role: String,
    val national_id: String
)


data class CreateUserRequest(
    val company_id: Int,
    val email: String,
    val first_name: String,
    val last_name: String,
    val role: String,
    val national_id: String,
    val password: String
)

data class BackendCompanyDto(
    val company_id: Int,
    val name: String
)

data class UploadImagesResponse(
    val image_urls: List<String>
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponseDto(
    val ok: Boolean,
    val user_id: Int?
)
