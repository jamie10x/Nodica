package com.jamie.nodica.features.messages

import kotlinx.datetime.Clock // Import Clock

// Implementation using the repository
class MessageUseCaseImpl(private val repository: MessageRepository) : MessageUseCase {
    override suspend fun getMessages(groupId: String): Result<List<Message>> = try {
// Delegate fetching to the repository. Add sorting/error mapping if needed.
        Result.success(repository.fetchMessages(groupId))
    } catch (e: Exception) {
        Result.failure(e) // Repository should throw specific errors if possible
    }

    override suspend fun sendMessage(
        groupId: String,
        senderId: String,
        content: String
    ): Result<Message> {
        // Create the Message object here, ready for the repository
        val message = Message(
            id = "", // Repository/DB handles ID generation
            groupId = groupId,
            senderId = senderId,
            content = content.trim(), // Trim content
            timestamp = Clock.System.now() // Initial timestamp (repo might override)
        )
        // Delegate sending to the repository
        return repository.sendMessage(message)
    }
}