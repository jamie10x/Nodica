package com.jamie.nodica.features.messages

interface MessageRepository {
    suspend fun fetchMessages(groupId: String): List<Message>
    suspend fun sendMessage(message: Message): Result<Unit>
}
