// main/java/com/jamie/nodica/features/messages/Message.kt
// No changes needed based on the review. Code remains the same.
package com.jamie.nodica.features.messages

import kotlinx.datetime.Instant // Use kotlinx-datetime for robust time handling
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a single chat message within a group.
 * Designed to be immutable after creation.
 *
 * @property id Unique identifier (UUID) for the message, typically generated by the database.
 * @property groupId The ID of the group this message belongs to. Foreign key to `groups.id`.
 * @property senderId The ID of the user who sent the message. Foreign key to `users.id`.
 * @property content The textual content of the message. Maps to DB `text` column (TEXT).
 * @property timestamp The time the message was created. Maps to DB `timestamp` column (TIMESTAMPTZ).
 */
@Serializable
data class Message(
    val id: String,

    @SerialName("group_id")
    val groupId: String,

    @SerialName("sender_id")
    val senderId: String,

    // Ensure DB column is 'text'. This maps the DB 'text' column to the Kotlin 'content' field.
    @SerialName("text")
    val content: String,

    // kotlinx-serialization handles Instant <-> TIMESTAMPTZ string conversion
    val timestamp: Instant,
)