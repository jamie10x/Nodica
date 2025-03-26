package com.jamie.nodica.features.messages

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val id: String, // Let the backend generate an ID if left empty.
    val groupId: String,
    val senderId: String,
    val content: String,
    val timestamp: String // ISO formatted or epoch string.
)


