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
 * @property content The textual content of the message. Maps to DB `content` column (TEXT).
 * @property timestamp The time the message was created. Maps to DB `timestamp` column (TIMESTAMPTZ).
 */
@Serializable
data class Message(
    // Use val for immutable fields
    val id: String,

    @SerialName("group_id")
    val groupId: String,

    @SerialName("sender_id")
    val senderId: String,

    // Ensure DB column is 'content' or use @SerialName("db_column_name") if different
    @SerialName("text") // <--- Change this
    val content: String,

    // kotlinx-serialization handles Instant <-> TIMESTAMPTZ string conversion
    val timestamp: Instant,

    // **Optional Enhancement**: Embed sender info if fetched via JOIN
    // @SerialName("users") // Assumes the relation in the query is named 'users'
    // val sender: SenderProfile? = null
)

/*
// Optional minimal sender profile data if joined in query
@Serializable
data class SenderProfile(
    val id: String, // User ID
    val name: String, // User display name
    @SerialName("profile_picture_url")
    val profilePictureUrl: String? = null
)
*/