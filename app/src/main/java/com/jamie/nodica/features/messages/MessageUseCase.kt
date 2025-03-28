package com.jamie.nodica.features.messages

interface MessageUseCase {
    // Fetches initial messages. Limit might be decided here or passed down.
    suspend fun getMessages(groupId: String): Result<List<Message>>

    // Sends a message.
    suspend fun sendMessage(
        groupId: String,
        senderId: String,
        content: String
    ): Result<Message> // Return the confirmed message
}