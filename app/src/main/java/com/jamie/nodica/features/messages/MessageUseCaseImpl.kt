package com.jamie.nodica.features.messages

import kotlinx.datetime.Clock // Import Clock
import timber.log.Timber // Optional: Add logging if needed

class MessageUseCaseImpl(private val repository: MessageRepository) : MessageUseCase {

    /**
     * Fetches messages for a group via the repository.
     */
    override suspend fun fetchMessages(groupId: String): Result<List<Message>> { // Renamed
        // Input validation (optional here, could be in ViewModel too)
        if (groupId.isBlank()) {
            return Result.failure(IllegalArgumentException("Group ID cannot be blank."))
        }
        return try {
            // Delegate fetching to the repository. Limit is handled by repo default.
            val messages = repository.fetchMessages(groupId)
            Result.success(messages)
        } catch (e: Exception) {
            Timber.e(e, "UseCase: Error fetching messages for group $groupId")
            Result.failure(Exception("Failed to load messages.", e)) // Wrap exception
        }
    }

    /**
     * Creates and sends a message via the repository.
     */
    override suspend fun sendMessage(
        groupId: String,
        senderId: String,
        content: String
    ): Result<Message> {
        // Input validation
        val trimmedContent = content.trim()
        if (groupId.isBlank() || senderId.isBlank() || trimmedContent.isBlank()) {
            return Result.failure(IllegalArgumentException("Group ID, Sender ID, and Content cannot be blank."))
        }

        // Create the Message object, repository handles ID/final timestamp
        val messageToSend = Message(
            id = "", // Ignored by repository insert
            groupId = groupId,
            senderId = senderId,
            content = trimmedContent, // Use trimmed content
            timestamp = Clock.System.now() // Provide client time as initial value
        )

        // Delegate sending to the repository
        return repository.sendMessage(messageToSend)
        // Repository result (Success<Message> or Failure<Exception>) is returned directly
    }
}