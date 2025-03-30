package com.jamie.nodica.features.groups.group

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant

// --- Helper classes for decoding relational data ---

@Serializable
data class NestedTagDetails(
    @SerialName("tags")
    val tagInfo: TagNameHolder? = null
)

@Serializable
data class TagNameHolder(
    val name: String
)

@Serializable
data class MemberCountHolder(
    val count: Long
)

// --- Main Group Data Class ---

/**
 * Represents a study group, including relational data like tags and member count
 * fetched via Supabase queries. Designed to be immutable after creation/fetch.
 *
 * @property id Unique identifier (UUID) for the group.
 * @property name Display name of the group.
 * @property description Optional description of the group's purpose or topics.
 * @property meetingSchedule Optional text field for meeting times/days.
 * @property creatorId UUID of the user who created the group.
 * @property createdAt Timestamp of group creation.
 * @property tags List of nested structures containing tag names, decoded from relational query.
 * @property membersRelation List containing member count holder, decoded from relational query.
 */
@Serializable
data class Group(
    val id: String,
    val name: String,
    val description: String = "", // Default to empty

    @SerialName("meeting_schedule")
    val meetingSchedule: String? = null,

    @SerialName("creator_id")
    val creatorId: String? = null,

    @SerialName("created_at")
    val createdAt: Instant? = null,

    // Relational: Fetched via `tags:group_tags!inner(tags(name))`
    val tags: List<NestedTagDetails> = emptyList(),

    // Relational: Fetched via `member_count:group_members(count)`
    @SerialName("member_count")
    val membersRelation: List<MemberCountHolder> = emptyList()

) {
    // --- Computed Properties for Convenience ---

    /** Returns a sorted list of tag names associated with the group. */
    val tagNames: List<String> by lazy { // Use lazy delegate for efficiency
        tags.mapNotNull { it.tagInfo?.name }.sorted()
    }

    /** Returns the number of members in the group. */
    val memberCount: Long by lazy { // Use lazy delegate
        membersRelation.firstOrNull()?.count ?: 0L
    }

    // --- Equality based on ID ---

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Group
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}