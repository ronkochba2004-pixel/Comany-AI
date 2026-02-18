package com.example.mspsupportassistant.network

import com.example.mspsupportassistant.model.*
import okhttp3.MultipartBody


import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    // POST /create_chat
    @POST("create_chat")
    suspend fun createChat(@Body request: CreateChatRequest): BackendChatDto

    // For user to user
    @POST("create_user_chat")
    suspend fun createUserChat(@Body request: CreateUserChatRequest): BackendChatDto

    // POST /send_message
    @POST("send_message")
    suspend fun sendMessage(@Body request: SendMessageRequest): BackendMessage

    @GET("/chats/{chat_id}/messages_after")
    suspend fun getMessagesAfter(@Path("chat_id") chatId: Int, @Query("after_id") afterId: Int): List<BackendMessage>

    @GET("/chats/{chat_id}/messages_before")
    suspend fun getMessagesBefore(
        @Path("chat_id") chatId: Int,
        @Query("before_id") beforeId: Int? = null,
        @Query("limit") limit: Int = 30): List<BackendMessage>

    // GET /chats
    @GET("chats")
    suspend fun getChats(@Query("user_id") userId: Int): List<BackendChatDto>


    // DEPRECATED: do not use.
    // Loads all chat messages at once.
    // Replaced by getMessagesBefore(...) for paginated loading.
    //@GET("chats/{chat_id}/messages")
    //suspend fun getMessages(@Path("chat_id") id: Int): List<BackendMessage>

    @DELETE("chats/{chat_id}")
    suspend fun deleteChat(@Path("chat_id") chatId: Int)

    @PATCH("chats/{chat_id}")
    suspend fun renameChat(@Path("chat_id") chatId: Int, @Body body: RenameChatRequest): BackendChatDto

    //////////////////////////////////////////////////////////   USERS  START //////////////////////////////////////////////////////////////
    @POST("create_user")
    suspend fun createUser(@Body request: CreateUserRequest): BackendUserDto

    @GET("users/{user_id}")
    suspend fun getUser(@Path("user_id") userId: Int): BackendUserDto

    // ApiService.kt
    @GET("users/by_national_id/{national_id}")
    suspend fun getUserByNationalId(@Path("national_id") nationalId: String): BackendUserDto



    // All users for a company
    @GET("companies/{company_id}/users")
    suspend fun getUsersForCompany(@Path("company_id") companyId: Int): List<BackendUserDto>

    @DELETE("users/{user_id}")
    suspend fun deleteUser(@Path("user_id") userId: Int)

    @POST("login")
    suspend fun login(@Body request: LoginRequest): LoginResponseDto



//////////////////////////////////////////////////////////   USERS  END //////////////////////////////////////////////////////////////

    @GET("companies/{company_id}")
    suspend fun getCompany(@Path("company_id") companyId: Int): BackendCompanyDto

    @Multipart
    @POST("upload_images")
    suspend fun uploadImages(@Part images: List<MultipartBody.Part>): UploadImagesResponse

}
