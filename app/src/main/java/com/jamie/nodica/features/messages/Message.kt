package com.jamie.nodica.features.messages

import kotlinx.datetime.Instant // Use kotlinx-datetime for robust time handling
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Message(
// Let DB generate ID (usually UUID), handle decoding potentially null ID on fetch?
// Or require ID from DB always. Assume DB provides it.
    val id: String,

    @SerialName("group_id")
    val groupId: String,

    @SerialName("sender_id")
    val senderId: String,

// Ensure DB column is also 'content' for consistency
// If DB column is 'text', change this to 'text' or use @SerialName("text")
    val content: String,

// Use Instant for timestamp handling internally. Supabase stores as TIMESTAMPTZ.
// Kotlinx serialization handles ISO 8601 string conversion automatically.
    val timestamp: Instant, // Change from String

// **Optional**: Add sender information (if fetching with user profile join)
// This reduces lookups in the UI layer. Requires adjusting repository fetch.
// @SerialName("users") // Assuming relation name is 'users' from 'sender_id'
// val sender: SenderProfile? = null
)

// Optional minimal sender profile data if joined in query
// @Serializable
// data class SenderProfile(
// val id: String,
// val name: String,
// @SerialName("profile_picture_url")
// val profilePictureUrl: String? = null
// )