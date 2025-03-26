package com.jamie.nodica.features.messages

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from

class SupabaseMessageRepository(private val client: SupabaseClient) : MessageRepository {

    override suspend fun fetchMessages(groupId: String): List<Message> {
        return try {
            client.from("messages")
                .select {
                    filter { eq("group_id", groupId) }
                }
                .decodeList<Message>()
        } catch (e: Exception) {
            throw Exception("Error fetching messages: ${e.message}")
        }
    }

    override suspend fun sendMessage(message: Message): Result<Unit> {
        return try {
            // Create the payload to send
            val payload = mapOf(
                "group_id" to message.groupId,
                "sender_id" to message.senderId,
                "content" to message.content,
                "timestamp" to message.timestamp
            )
            client.from("messages")
                .insert(payload)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("Error sending message: ${e.message}"))
        }
    }
}

