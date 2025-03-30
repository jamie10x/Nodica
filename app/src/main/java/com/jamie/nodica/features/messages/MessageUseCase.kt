package com.jamie.nodica.features.messages

/**
 * Defines the use cases (business logic) for interacting with chat messages.
 */
interface MessageUseCase {
    /**
     * Fetches the initial batch of messages for a given group.
     *
     * @param groupId The ID of the group.
     * @return A [Result] containing a list of [Message] objects (oldest first) on success,
     *         or an Exception on failure.
     */
    suspend fun fetchMessages(groupId: String): Result<List<Message>> // Renamed

    /**
     * Sends a message from a specific user to a specific group.
     *
     * @param groupId The ID of the target group.
     * @param senderId The ID of the user sending the message.
     * @param content The text content of the message.
     * @return A [Result] containing the confirmed [Message] on success, or an Exception on failure.
     */
    suspend fun sendMessage(
        groupId: String,
        senderId: String,
        content: String
    ): Result<Message>
}