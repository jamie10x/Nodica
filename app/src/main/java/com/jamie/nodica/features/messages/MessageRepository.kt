package com.jamie.nodica.features.messages

/**
 * Interface defining data operations for chat messages.
 */
interface MessageRepository {
    /**
     * Fetches the most recent messages for a specific group, ordered by timestamp ascending (oldest first).
     *
     * @param groupId The ID of the group whose messages are to be fetched.
     * @param limit The maximum number of messages to retrieve.
     * @return A list of [Message] objects, sorted oldest to newest.
     * @throws Exception if fetching fails (e.g., network or database error).
     */
    suspend fun fetchMessages(groupId: String, limit: Int = 50): List<Message>

    /**
     * Sends a new message to the specified group.
     * The implementation is responsible for handling ID generation and final timestamp assignment.
     *
     * @param message The [Message] object to be sent. The `id` field might be ignored,
     *                and the `timestamp` might be overridden by the repository or database.
     * @return A [Result] containing the confirmed [Message] (with database-assigned ID and timestamp)
     *         on success, or an Exception on failure.
     */
    suspend fun sendMessage(message: Message): Result<Message>
}