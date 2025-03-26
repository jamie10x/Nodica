package com.jamie.nodica.features.messages

interface MessageUseCase {
    suspend fun getMessages(groupId: String): Result<List<Message>>
    suspend fun sendMessage(message: Message): Result<Unit>
}
