package com.jamie.nodica.features.messages

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.datetime.Clock // Import Clock for timestamp generation
import timber.log.Timber

class SupabaseMessageRepository(private val client: SupabaseClient) : MessageRepository {

    override suspend fun fetchMessages(groupId: String, limit: Int): List<Message> {
        Timber.d("Fetching last $limit messages for group $groupId")
        return try {
            client.from("messages")
                .select { // Optionally join with sender profile: ("*, users(*)")
                    filter { eq("group_id", groupId) }
                    order("timestamp", Order.DESCENDING) // Fetch newest first
                    limit(limit.toLong())
                }
                // Decode directly into List<Message>. kotlinx-datetime handles Instant conversion.
                .decodeList<Message>()
                // Reverse locally to have oldest first in the list (for UI appending/display)
                .reversed()
        } catch (e: Exception) {
            Timber.e(e, "Error fetching messages for group $groupId")
            throw Exception("Error fetching messages: ${e.message}") // Rethrow wrapped
        }
    }

    override suspend fun sendMessage(message: Message): Result<Message> {
        Timber.d("Sending message to group ${message.groupId} by sender ${message.senderId}")
        // Ensure timestamp is set (prefer server time via default value or trigger if possible)
        // If client generates timestamp, ensure it's accurate. Using Clock.System.now() here.
        val messageToSend = message.copy(
            timestamp = Clock.System.now() // Override with current time just before sending
        )

        return try {
            // Insert the message data.
            // Supabase generates 'id' and potentially 'timestamp' if DEFAULT now() is set.
            // Select the inserted row to get back the DB-generated values.
            val sentMessage = client.from("messages")
                .insert(messageToSend) {
                    select() // Select the inserted row(s)
                }
                .decodeSingle<Message>() // Decode the single inserted message

            Timber.i("Message sent successfully with ID ${sentMessage.id}")
            Result.success(sentMessage)

        } catch (e: Exception) {
            Timber.e(e, "Error sending message to group ${message.groupId}")
            Result.failure(Exception("Error sending message: ${e.message}"))
        }
    }
}