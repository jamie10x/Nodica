package com.jamie.nodica.features.profile

// Create a separate file for the UserProfile data class for better organization.
import kotlinx.serialization.SerialName // Import for mapping Kotlin names to DB names
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
// id and email are often implicit from the auth user or session,
// but defining them here allows decoding the full row if needed.
    val id: String, // Should match Supabase 'users.id' (usually UUID)
    val email: String? = null, // Make nullable if not always selecting/required

    val name: String, // DB: name (text)

// Use @SerialName to map Kotlin camelCase to DB snake_case if names differ.
    @SerialName("institution")
    val school: String? = null, // Kotlin: school, DB: institution (text)

    @SerialName("preferred_time")
    val preferredTime: String? = null, // Kotlin: preferredTime, DB: preferred_time (text)

    @SerialName("study_goals")
    val studyGoals: String? = null, // Kotlin: studyGoals, DB: study_goals (text)

// Add other fields from the 'users' table if needed, e.g., profile picture URL
    @SerialName("profile_picture_url")
    val profilePictureUrl: String? = null,

    @SerialName("created_at") // Keep track of when the profile was created/updated
    val createdAt: String? = null // Use kotlinx-datetime Instant if parsing dates/times
)

// Data class for tags - used in both profile setup and management
@Serializable
data class TagItem(
    val id: String, // UUID from DB
    val name: String,
    val category: String, // e.g., "Science", "Mathematics"
    @SerialName("created_at")
    val createdAt: String? = null
)

// Data class representing the user_tags pivot table entry
@Serializable
data class UserTagLink(
    @SerialName("user_id")
    val userId: String,
    @SerialName("tag_id")
    val tagId: String,
// You might embed the related TagItem here if fetching with joins
// val tag: TagItem? = null
)