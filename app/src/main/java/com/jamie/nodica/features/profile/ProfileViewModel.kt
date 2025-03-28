package com.jamie.nodica.features.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

// Data class TagItem is defined in UserProfile.kt

class ProfileViewModel(
    private val supabase: SupabaseClient
) : ViewModel() {

    // Internal mutable state
    internal val _uiState = MutableStateFlow(ProfileSetupState()) // Renamed internal state for clarity
    // Publicly exposed read-only state
    val uiState: StateFlow<ProfileSetupState> = _uiState.asStateFlow()

    init {
        Timber.d("ProfileViewModel initialized.")
        checkExistingProfileAndFetchTags()
    }

    private suspend fun fetchAvailableTags() {
        Timber.d("Fetching available tags...")
        try {
            val tags = supabase.from("tags").select().decodeList<TagItem>()
            val tagsByCategory = tags.groupBy { it.category }
                .mapValues { entry -> entry.value.sortedBy { it.name } }
            _uiState.update { it.copy(availableTags = tagsByCategory) }
            Timber.i("Fetched ${tags.size} tags, grouped into ${tagsByCategory.size} categories.")
        } catch (e: Exception) {
            Timber.e(e, "Error fetching available tags")
            // Update state to indicate tag loading error
            _uiState.update { it.copy(loading = false, error = "Failed to load subjects/interests: ${e.message}") }
        }
    }

    private fun checkExistingProfileAndFetchTags() {
        _uiState.update { it.copy(loading = true, error = null, profileStatus = ProfileStatus.Loading) }
        viewModelScope.launch {
            val userId = supabase.auth.currentUserOrNull()?.id
            if (userId == null) {
                _uiState.update { it.copy(loading = false, error = "User not logged in.", profileStatus = ProfileStatus.SaveError) }
                return@launch
            }

            var profileExistsAndComplete = false
            try {
                // Check only if a profile record exists with a name
                val profileCheck = supabase.from("users")
                    .select(Columns.list("name")) { filter { eq("id", userId) }; single() }
                    .decodeSingleOrNull<UserProfile>() // Minimal decode

                // Define completeness check (e.g., name is not blank)
                if (profileCheck != null && profileCheck.name.isNotBlank()) {
                    profileExistsAndComplete = true
                    Timber.i("Profile exists and is complete for user: $userId")
                    _uiState.update { it.copy(loading = false, profileStatus = ProfileStatus.ProfileExists) }
                } else {
                    Timber.i("Profile not found or incomplete for user: $userId. Proceeding with setup.")
                }

            } catch (e: RestException) {
                // Handle 0 rows specifically (profile not found is expected for setup)
                if (e.message?.contains("PGRST116") == true || e.message?.contains("JWSError JWSInvalidSignature") == false) {
                    Timber.i("Profile does not exist for user $userId (RestException 0 rows).")
                } else { // Other DB errors
                    _uiState.update { it.copy(loading = false, error = "Error checking profile: ${e.message}", profileStatus = ProfileStatus.SaveError) }
                    return@launch // Don't proceed if profile check failed critically
                }
            } catch (e: Exception) { // Catch other errors like network
                Timber.e(e, "Generic error checking profile")
                _uiState.update { it.copy(loading = false, error = "Error checking profile: ${e.message}", profileStatus = ProfileStatus.SaveError) }
                return@launch
            }

            // If profile doesn't exist/incomplete, fetch tags for setup UI
            if (!profileExistsAndComplete) {
                fetchAvailableTags()
                // Final state update after checks and tag fetch attempt
                // Ensure loading is false regardless of tag fetch success/failure
                _uiState.update { currentState ->
                    currentState.copy(
                        loading = false,
                        profileStatus = ProfileStatus.NeedsSetup // Ready for user input
                        // Error state might already be set by fetchAvailableTags
                    )
                }
            }
            // If profile exists and complete, state is already set to ProfileExists.
        }
    }

    /**
     * Saves profile details and manages tags.
     */
    fun saveProfile(
        name: String,
        school: String, // Use 'school' consistent with UI state
        preferredTime: String,
        studyGoals: String,
        selectedExistingTagIds: List<String>,
        newCustomTagNames: List<String>
    ) {
        _uiState.update { it.copy(error = null, profileStatus = ProfileStatus.Saving) }
        viewModelScope.launch {
            val userId = supabase.auth.currentUserOrNull()?.id
            if (userId == null) {
                _uiState.update { it.copy(loading = false, error = "Cannot save profile: User not logged in.", profileStatus = ProfileStatus.SaveError) }
                return@launch
            }

            try {
                // 1. Resolve Custom Tags
                val customTagIds = newCustomTagNames.mapNotNull { findOrCreateTag(it) }

                // 2. Combine Tag IDs
                val allTagIds = (selectedExistingTagIds + customTagIds).distinct()
                if (allTagIds.isEmpty()) { Timber.w("Saving profile with zero tags selected/added.") }

                // 3. Save Core Profile Data
                val profileData = UserProfile(
                    id = userId,
                    email = supabase.auth.currentUserOrNull()?.email, // Include email if available
                    name = name.trim(),
                    school = school.trim().ifBlank { null }, // Use 'school' here
                    preferredTime = preferredTime.trim().ifBlank { null },
                    studyGoals = studyGoals.trim().ifBlank { null },
                    profilePictureUrl = null // Set profile pic url ONLY during upload/edit, not initial setup
                )
                supabase.from("users").upsert(profileData)
                Timber.i("Profile core data upserted for user: $userId")

                // 4. Update Tag Associations
                updateUserTags(userId, allTagIds)
                Timber.i("User tags updated successfully for user: $userId")

                // 5. Update State to Success
                _uiState.update { it.copy(loading = false, profileStatus = ProfileStatus.SaveSuccess) } // Ensure loading is false

            } catch (e: Exception) {
                Timber.e(e, "Error saving profile for user: $userId")
                _uiState.update {
                    it.copy(
                        loading = false, // Ensure loading is false
                        error = "Failed to save profile: ${e.message}",
                        profileStatus = ProfileStatus.SaveError
                    )
                }
            }
        }
    }

    /** Finds or creates a tag, returning its ID. */
    private suspend fun findOrCreateTag(tagName: String): String? {
        val trimmedName = tagName.trim()
        if (trimmedName.isBlank()) return null

        return try {
            val existingTag = supabase.from("tags").select(Columns.list("id")) {
                filter { ilike("name", trimmedName) }
                limit(1)
            }.decodeSingleOrNull<TagItem>()

            if (existingTag != null) {
                existingTag.id
            } else {
                val category = determineCategory(trimmedName)
                val newTag = supabase.from("tags").insert(
                    mapOf("name" to trimmedName, "category" to category)
                ) { select(Columns.list("id")) }.decodeSingle<TagItem>()
                newTag.id
            }
        } catch (e: Exception) {
            Timber.e(e, "Error finding or creating tag: $trimmedName")
            null
        }
    }

    /** Determines category for a new tag. */
    private fun determineCategory(tagName: String): String {
        // Same logic as before...
        return when {
            tagName.contains("Math", ignoreCase = true) -> "Mathematics"
            tagName.contains("Phys", ignoreCase = true) -> "Science"
            tagName.contains("Chem", ignoreCase = true) -> "Science"
            tagName.contains("Bio", ignoreCase = true) -> "Science"
            tagName.contains("Lang", ignoreCase = true) -> "Languages"
            tagName.contains("Eng", ignoreCase = true) -> "Languages"
            // ... other categories ...
            else -> "General"
        }
    }

    /** Synchronizes user_tags table. */
    private suspend fun updateUserTags(userId: String, desiredTagIds: List<String>) {
        Timber.d("Updating tags for user $userId. Desired tag IDs: $desiredTagIds")
        try {
            supabase.from("user_tags").delete { filter { eq("user_id", userId) } }
            if (desiredTagIds.isNotEmpty()) {
                val linksToInsert = desiredTagIds.map { mapOf("user_id" to userId, "tag_id" to it) }
                supabase.from("user_tags").insert(linksToInsert)
            }
            Timber.d("Tag sync complete for user $userId.")
        } catch (e: Exception) {
            Timber.e(e, "Failed during tag update DB operations for user $userId")
            throw Exception("Database error updating tags.", e)
        }
    }

    /** Clears the error message in the UI state. */
    fun clearError() {
        val currentStatus = _uiState.value.profileStatus
        // Only transition from SaveError back to NeedsSetup, keep other states as they are
        val newStatus = if (currentStatus == ProfileStatus.SaveError) ProfileStatus.NeedsSetup else currentStatus
        _uiState.update { it.copy(error = null, profileStatus = newStatus) }
    }
}

// --- State Definitions --- (Remain the same)
data class ProfileSetupState(
    val loading: Boolean = true,
    val profileStatus: ProfileStatus = ProfileStatus.Loading,
    val availableTags: Map<String, List<TagItem>> = emptyMap(),
    val error: String? = null
)

enum class ProfileStatus { Loading, NeedsSetup, ProfileExists, Saving, SaveSuccess, SaveError }