package com.jamie.nodica.features.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import timber.log.Timber

@Serializable
data class UserProfile(
    val id: String,
    val email: String,
    val name: String,
    val institution: String? = null,
    // Removed old 'subjects' field; tags are now stored separately
    val preferred_time: String? = null,
    val study_goals: String? = null
)

class ProfileViewModel(
    private val supabase: SupabaseClient
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Idle)
    val uiState: StateFlow<ProfileUiState> = _uiState

    init {
        checkExistingProfile()
    }

    /**
     * Checks if the user already has a profile stored in Supabase.
     * If found, updates the UI state to indicate that the profile exists.
     */
    private fun checkExistingProfile() {
        viewModelScope.launch {
            try {
                val id = supabase.auth.currentUserOrNull()?.id ?: return@launch
                val result = supabase.from("users").select {
                    filter { eq("id", id) }
                }.decodeSingleOrNull<UserProfile>()
                if (result != null && result.name.isNotBlank()) {
                    _uiState.value = ProfileUiState.ProfileExists
                }
            } catch (e: Exception) {
                Timber.e(e, "Error checking existing profile")
            }
        }
    }

    /**
     * Saves or upserts the user's profile in Supabase.
     * After saving the profile, it saves the selected tags (subjects) into the user_tags pivot table.
     */
    fun saveProfile(
        name: String,
        institution: String,
        tagNames: List<String>, // New: tag-based selection from the UI
        preferredTime: String,
        studyGoals: String
    ) {
        _uiState.value = ProfileUiState.Loading
        viewModelScope.launch {
            try {
                val id = supabase.auth.currentUserOrNull()?.id
                    ?: throw Exception("User not logged in")
                val email = supabase.auth.currentUserOrNull()?.email ?: ""
                val profile = UserProfile(
                    id = id,
                    email = email,
                    name = name,
                    institution = institution.ifBlank { null },
                    preferred_time = preferredTime.ifBlank { null },
                    study_goals = studyGoals.ifBlank { null }
                )
                // Save profile in the users table.
                supabase.from("users").upsert(profile)
                // Save tags in the new pivot table.
                saveTagsForUser(id, tagNames)
                _uiState.value = ProfileUiState.Success
            } catch (e: Exception) {
                Timber.e(e, "Error saving profile")
                _uiState.value = ProfileUiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /**
     * Saves the selected tags for the user.
     * For each tag name, the function checks if it exists in the 'tags' table.
     * If not, it inserts the new tag (using determineCategory for categorization).
     * Then, it upserts a record into the user_tags pivot table.
     */
    private suspend fun saveTagsForUser(userId: String, tagNames: List<String>) {
        tagNames.forEach { tagName ->
            try {
                // Try to find an existing tag by name (case-insensitive match can be added here)
                val existingTag = supabase.from("tags").select {
                    filter { eq("name", tagName) }
                }.decodeSingleOrNull<TagItem>()
                val tagId = if (existingTag == null) {
                    // Insert the new tag with a determined category.
                    val insertedTag = supabase.from("tags").insert(
                        mapOf(
                            "name" to tagName,
                            "category" to determineCategory(tagName)
                        )
                    ).decodeSingle<TagItem>()
                    insertedTag.id
                } else {
                    existingTag.id
                }
                // Upsert into the user_tags pivot table.
                supabase.from("user_tags").upsert(
                    mapOf(
                        "user_id" to userId,
                        "tag_id" to tagId
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "Error saving tag '$tagName' for user")
                // Continue processing remaining tags even if one fails.
            }
        }
    }

    /**
     * Dummy function to determine a tag's category.
     * In a real application, this can be derived from UI input or a mapping.
     */
    private fun determineCategory(tagName: String): String {
        // For now, return "Uncategorized" by default.
        return "Uncategorized"
    }
}

@Serializable
data class TagItem(
    val id: String,
    val name: String,
    val category: String,
    val created_at: String? = null
)