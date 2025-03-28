package com.jamie.nodica.features.groups.group

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Represents the structure of a tag name when fetched relationally
@Serializable
data class TagNameHolder(val name: String)

// Represents the nested structure when fetching tags relationally: group_tags -> tags -> name
@Serializable
data class RelationalTag(
    @SerialName("tags") // Matches the table name used in the relation tags(...)
    val tagDetails: TagNameHolder? = null
)

@Serializable
data class Group(
    val id: String,
    val name: String,
    val description: String = "",
    @SerialName("meeting_schedule")
    val meetingSchedule: String? = null,
    @SerialName("creator_id")
    val creatorId: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null, // Assuming DB sends timestamp as string

// **IMPORTANT CHANGE:** This field receives the relational data
// The name 'tags' matches the alias used in the select query: `tags:group_tags(...)`
    val tags: List<RelationalTag> = emptyList(),

// Represents the member count fetched via relation `member_count:group_members(count)`
    @SerialName("member_count")
    val members: Long = 0 // Use Long for count
) {
    // Helper property to easily get just the tag names
    val tagNames: List<String>
        get() = tags.mapNotNull { it.tagDetails?.name }
}