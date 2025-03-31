// main/java/com/jamie/nodica/features/messages/MessageUseCaseImpl.kt
package com.jamie.nodica.features.messages

import kotlinx.datetime.Clock // Import Clock
import timber.log.Timber // Optional: Add logging if needed

class MessageUseCaseImpl(private val repository: MessageRepository) : MessageUseCase {

    /**
     * Fetches messages for a group via the repository.
     */
    override suspend fun fetchMessages(groupId: String): Result<List<Message>> {
        if (groupId.isBlank()) {
            Timber.w("UseCase: fetchMessages called with blank groupId.")
            return Result.failure(IllegalArgumentException("Group ID cannot be blank."))
        }
        Timber.d("UseCase: Fetching messages for group $groupId")
        return try {
            val messages = repository.fetchMessages(groupId) // Limit is handled by repo default
            Timber.i("UseCase: Successfully fetched ${messages.size} messages for group $groupId.")
            Result.success(messages)
        } catch (e: Exception) {
            Timber.e(e, "UseCase: Error fetching messages for group $groupId from repository.")
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
            Timber.w("UseCase: sendMessage called with blank fields. Group: '$groupId', Sender: '$senderId', Content: '$content'")
            return Result.failure(IllegalArgumentException("Group ID, Sender ID, and Content cannot be blank."))
        }

        // Create the Message object, repository handles ID/final timestamp
        val messageToSend = Message(
            id = "", // <<<<<====== PROBLEM HERE
            groupId = groupId,
            senderId = senderId,
            content = trimmedContent,
            timestamp = Clock.System.now()
        )
        repository.sendMessage(messageToSend)

        Timber.d("UseCase: Sending message to group $groupId")
        // Delegate sending to the repository
        return repository.sendMessage(messageToSend).onFailure { e->
            Timber.e(e, "UseCase: Error sending message for group $groupId from repository.")
        }.onSuccess {
            Timber.i("UseCase: Successfully sent message ${it.id} to group $groupId.")
        }
        // Repository result (Success<Message> or Failure<Exception>) is returned directly
    }
}