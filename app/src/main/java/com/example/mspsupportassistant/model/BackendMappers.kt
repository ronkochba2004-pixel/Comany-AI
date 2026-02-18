package com.example.mspsupportassistant.model


import android.net.Uri
import androidx.core.net.toUri
import com.example.mspsupportassistant.network.RetrofitClient



fun backendChatToChatSession(dto: BackendChatDto): ChatSession {
    return ChatSession(
        backendChatId = dto.chat_id,
        title = dto.title,
        chatType = dto.chat_type, // Now we won't forget the type
        messages = mutableListOf()
    )
}




fun backendMessageToChatMessage(dto: BackendMessage): ChatMessage {
    val base = RetrofitClient.BASE_URL.removeSuffix("/") // "http://10.100.102.150:8000"

    val imageUris = dto.image_urls.map { u ->
        val fullUrl = if (u.startsWith("http")) u else base + u
        fullUrl.toUri()
    }

    return ChatMessage(
        id = dto.message_id,
        role = if (dto.sender == "user") MessageRole.USER else MessageRole.ASSISTANT,
        text = dto.text,
        ts = dto.timestamp,
        senderId = dto.sender_id, // For user to user
        imageUris = imageUris
    )
}


fun backendUserToLocalUser(dto: BackendUserDto): LocalUser {
    val role = when (dto.role.trim().lowercase()) {
        "employee" -> UserRole.EMPLOYEE
        "company_admin" -> UserRole.COMPANY_ADMIN
        else -> {
            // fallback safe: treat unknown role as employee
            UserRole.EMPLOYEE
        }
    }

    return LocalUser(
        id = dto.user_id,
        companyId = dto.company_id,
        firstName = dto.first_name,
        lastName = dto.last_name,
        email = dto.email,
        nationalId = dto.national_id,
        role = role
    )
}


fun backendCompanyToLocalCompany(dto: BackendCompanyDto): LocalCompany {
    return LocalCompany(
        id = dto.company_id,
        name = dto.name
    )
}




