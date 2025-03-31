package com.jamie.nodica.features.messages

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.datetime.Clock
import timber.log.Timber

class SupabaseMessageRepository(private val client: SupabaseClient) : MessageRepository {

    // fetchMessages remains unchanged from the previous refinement
    override suspend fun fetchMessages(groupId: String, limit: Int): List<Message> {
        Timber.d("Repo: Fetching last $limit messages for group $groupId")
        return try {
            client.from("messages")
                .select { // Fetches columns: id, group_id, sender_id, text, timestamp
                    filter { eq("group_id", groupId) }
                    order("timestamp", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<Message>()
                .reversed()
        } catch (e: RestException) {
            Timber.e(e, "Repo: Rest error fetching messages for group $groupId. Code: ${e.statusCode}, Desc: ${e.description}")
            throw Exception("Database error fetching messages: ${e.description ?: e.message}")
        } catch (e: HttpRequestException) {
            Timber.e(e, "Repo: Network error fetching messages for group $groupId.")
            throw Exception("Network error fetching messages. Please check connection.")
        } catch (e: Exception) {
            Timber.e(e, "Repo: Generic error fetching messages for group $groupId")
            throw Exception("Error loading messages: ${e.message}")
        }
    }

    override suspend fun sendMessage(message: Message): Result<Message> {
        Timber.d("Repo: Sending message to group ${message.groupId} by sender ${message.senderId}")

        // --- SOLUTION: Create a Map for insertion, EXCLUDING the 'id' ---
        val insertData = mapOf(
            "group_id" to message.groupId,
            "sender_id" to message.senderId,
            "text" to message.content, // Use the 'text' key matching the DB column name
            "timestamp" to Clock.System.now().toString() // Send timestamp as ISO string
            // Let the database generate the 'id'
        )
        // Alternative for timestamp: if DB column has DEFAULT now(), omit "timestamp" from the map.

        Timber.v("Repo: Inserting data: $insertData") // Log the data being sent

        return try {
            // Insert the map data.
            val sentMessage = client.from("messages")
                // --- Use the map instead of the full Message object ---
                .insert(insertData) {
                    select() // Select the inserted row to get back DB-generated ID and final timestamp
                }
                .decodeSingle<Message>() // Decode the response back into a full Message object

            Timber.i("Repo: Message sent successfully with ID ${sentMessage.id}")
            Result.success(sentMessage)

        } catch (e: RestException) {
            Timber.e(e, "Repo: Rest error sending message to group ${message.groupId}. Code: ${e.statusCode}, Desc: ${e.description}")
            Result.failure(Exception("Database error sending message: ${e.description ?: e.message}"))
        } catch (e: HttpRequestException) {
            Timber.e(e, "Repo: Network error sending message to group ${message.groupId}.")
            Result.failure(Exception("Network error sending message. Please try again."))
        } catch (e: Exception) { // Catch other errors like SerializationException
            Timber.e(e, "Repo: Generic error sending message to group ${message.groupId}")
            Result.failure(Exception("Error sending message: ${e.message}"))
        }
    }
}