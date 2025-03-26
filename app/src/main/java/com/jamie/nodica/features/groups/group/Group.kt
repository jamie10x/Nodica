package com.jamie.nodica.features.groups.group

import kotlinx.serialization.Serializable

@Serializable
data class Group(
    val id: String,
    val name: String,
    val tags: List<String> = emptyList(),
    val description: String = "",
    val meetingSchedule: String? = null, // <-- fix here
    val creatorId: String? = null,       // <-- fix here
    val members: Int = 0
)