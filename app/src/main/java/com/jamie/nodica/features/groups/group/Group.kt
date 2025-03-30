// main/java/com/jamie/nodica/features/groups/group/Group.kt
package com.jamie.nodica.features.groups.group

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.Transient // For lazy delegate

// --- Helper classes for decoding relational data ---

/** Helper to decode nested tag info like `tags:group_tags!inner(tags(name))` */
@Serializable
data class NestedTagDetails(
    @SerialName("tags") // Adjust if query alias differs
    val tagInfo: TagNameHolder? = null
)

/** Holds the actual tag name from the nested structure */
@Serializable
data class TagNameHolder(
    val name: String
)

/** Helper to decode member count like `member_count:group_members(count)` */
@Serializable
data class MemberCountHolder(
    val count: Long
)

// --- Main Group Data Class ---

/**
 * Represents a study group.
 * Ensure @SerialName annotations match your actual Supabase column names.
 */
@Serializable
data class Group(
    val id: String,
    val name: String,
    val description: String = "",

    @SerialName("meeting_schedule")
    val meetingSchedule: String? = null,

    @SerialName("creator_id")
    val creatorId: String, // Non-nullable

    @SerialName("created_at")
    val createdAt: Instant, // Non-nullable

    // Relational Data Fields (ensure names/aliases match your queries)
    val tags: List<NestedTagDetails> = emptyList(),

    @SerialName("member_count")
    val membersRelation: List<MemberCountHolder> = emptyList()

) {
    // --- Computed Properties for Convenience ---

    /** Returns a sorted list of tag names. Transient avoids serialization issues. */
    @delegate:Transient
    val tagNames: List<String> by lazy {
        tags.mapNotNull { it.tagInfo?.name }.sorted()
    }

    /** Returns the number of members. Transient avoids serialization issues. */
    @delegate:Transient
    val memberCount: Long by lazy {
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


// --- Tag Item Data Model ---
// Defined in profile package, but shown here for context if needed
// package com.jamie.nodica.features.profile
// import kotlinx.datetime.Instant
// import kotlinx.serialization.SerialName
// import kotlinx.serialization.Serializable
//
// @Serializable
// data class TagItem(
//     val id: String,
//     val name: String,
//     val category: String,
//     @SerialName("created_at")
//     val createdAt: Instant? = null
// )