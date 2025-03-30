package com.jamie.nodica.features.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant

/**
 * Represents the data structure for the 'users' table in Supabase.
 * Includes fields for profile information.
 */
@Serializable
data class UserProfile(
    val id: String,
    val email: String? = null,
    var name: String,
    @SerialName("institution")
    var school: String? = null,
    @SerialName("preferred_time")
    var preferredTime: String? = null,
    @SerialName("study_goals")
    var studyGoals: String? = null,
    @SerialName("profile_picture_url")
    var profilePictureUrl: String? = null,
    @SerialName("created_at")
    val createdAt: Instant? = null
)

/**
 * Represents the data structure for the 'tags' table in Supabase.
 */
@Serializable
data class TagItem(
    val id: String,
    val name: String,
    val category: String,
    @SerialName("created_at")
    val createdAt: Instant? = null
)

/**
 * Represents the structure of the 'user_tags' many-to-many join table.
 * Used primarily for relational queries or if direct interaction with the table is needed.
 */
@Serializable
data class UserTagLink(
    @SerialName("user_id")
    val userId: String,
    @SerialName("tag_id")
    val tagId: String
)

/**
 * Represents the structure required for fetching the profile along with associated tag IDs,
 * often used in profile management view models. This combines UserProfile data with
 * the IDs from the UserTagLink relation.
 */
@Serializable
data class UserProfileWithTags(
    val id: String,
    val name: String,
    val email: String? = null,
    @SerialName("institution")
    val school: String? = null,
    @SerialName("preferred_time")
    val preferredTime: String? = null,
    @SerialName("study_goals")
    val studyGoals: String? = null,
    @SerialName("profile_picture_url")
    val profilePictureUrl: String? = null,
    @SerialName("created_at")
    val createdAt: Instant? = null,

    @SerialName("user_tags")
    val tagLinks: List<UserTagIdOnlyLink> = emptyList()
) {
    val tagIds: Set<String>
        get() = tagLinks.map { it.tagId }.toSet()
}

/**
 * Helper data class specifically for decoding the result of a select query
 * like `user_tags(tag_id)`. Only contains the tagId.
 */
@Serializable
data class UserTagIdOnlyLink(
    @SerialName("tag_id")
    val tagId: String
)