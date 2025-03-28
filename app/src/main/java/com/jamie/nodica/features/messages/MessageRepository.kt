package com.jamie.nodica.features.messages

interface MessageRepository {
    // Fetch initial messages for a group, ordered by timestamp
    suspend fun fetchMessages(groupId: String, limit: Int = 50): List<Message> // Added limit

    // Send a new message
// Changed signature: ViewModel constructs full Message object before sending
    suspend fun sendMessage(message: Message): Result<Message> // Return the sent message (with DB ID/timestamp)
}