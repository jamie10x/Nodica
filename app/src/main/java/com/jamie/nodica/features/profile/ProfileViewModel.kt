package com.jamie.nodica.features.profile

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from // Ensure explicit import
import io.github.jan.supabase.postgrest.postgrest
// No Columns import needed here anymore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException // Keep for save error handling
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import timber.log.Timber

// --- State Definitions ---
@Immutable
data class ProfileSetupState(
    // isLoading primarily reflects tag loading status
    val isLoading: Boolean = true, // Start true until tags are loaded/failed
    val isLoadingTags: Boolean = true, // Flag specifically for tag loading UI
    // Assume NeedsSetup if this VM is active. Splash directs here only if needed.
    val profileStatus: ProfileStatus = ProfileStatus.NeedsSetup,
    val availableTags: Map<String, List<TagItem>> = emptyMap(),
    val profilePictureUrl: String? = null, // Not loaded here, managed by ProfileManagementVM
    val error: String? = null // For critical/save errors
)

enum class ProfileStatus {
    // Loading, // No longer used as initial state here
    NeedsSetup,     // The primary state for this screen's UI form.
    // ProfileExists, // Not relevant for this VM anymore
    Saving,         // Save operation in progress.
    SaveSuccess,    // Saved successfully (triggers navigation away).
    SaveError,      // Validation or Save error.
    CriticalError   // E.g., Auth error during save attempt
}

// --- ProfileViewModel (Simplified for Setup ONLY) ---
/**
 * ViewModel specifically for the Profile *Setup* screen.
 * Assumes setup IS needed because SplashViewModel directed the user here.
 * Fetches available tags required for the setup form and handles saving the new profile via RPC.
 */
class ProfileViewModel(
    private val supabase: SupabaseClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileSetupState())
    val uiState: StateFlow<ProfileSetupState> = _uiState.asStateFlow()

    private var fetchTagsJob: Job? = null
    // REMOVED: fetchProfileCheckJob

    init {
        Timber.d("ProfileViewModel initialized for Setup screen.")
        // Directly fetch tags, assuming Splash determined setup is required.
        fetchAvailableTags()
    }

    private fun getCurrentUserId(): String? {
        // Keep for saveProfile
        val userId = supabase.auth.currentUserOrNull()?.id
        if (userId == null) {
            Timber.e("ProfileViewModel: User not logged in for save operation.")
            _uiState.update {
                it.copy(isLoading = false, isLoadingTags = false, error = "Authentication error.", profileStatus = ProfileStatus.CriticalError)
            }
        }
        return userId
    }

    // REMOVED: checkIfProfileNeedsSetup() function entirely
    // REMOVED: isProfileDataComplete() helper function

    /**
     * Fetches available tags and updates loading flags.
     * This now controls the primary isLoading state for this screen.
     */
    private fun fetchAvailableTags() {
        if (fetchTagsJob?.isActive == true) return
        // Set flags: overall loading is true until tags finish
        _uiState.update { it.copy(isLoading = true, isLoadingTags = true, error = null) }
        Timber.d("ProfileViewModel: Fetching available tags...")

        fetchTagsJob = viewModelScope.launch {
            var tagsError: String? = null
            try {
                val tags = supabase.postgrest.from("tags").select().decodeList<TagItem>()
                val tagsByCategory = tags.groupBy { it.category.uppercase() }
                    .toSortedMap().mapValues { entry -> entry.value.sortedBy { it.name } }
                Timber.i("ProfileViewModel: Fetched ${tags.size} tags.")
                _uiState.update { it.copy(availableTags = tagsByCategory) }
            } catch (e: Exception) {
                Timber.e(e, "ProfileViewModel: Error fetching available tags.")
                tagsError = "Failed to load subjects/interests."
            } finally {
                Timber.d("ProfileViewModel: Tag fetch finished.")
                // Always set loading flags false and update error status
                _uiState.update {
                    it.copy(isLoadingTags = false, isLoading = false, error = it.error ?: tagsError)
                }
            }
        }
    }

    /**
     * Saves the user's profile data and tags via the `save_user_profile_and_tags` RPC.
     * Performs validation before attempting the save operation.
     */
    fun saveProfile(
        name: String, school: String, preferredTime: String, studyGoals: String,
        selectedExistingTagIds: List<String>, newCustomTagNames: List<String>
    ) {
        val userId = getCurrentUserId() ?: return

        // --- Input Validation ---
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            _uiState.update { it.copy(error = "Name cannot be empty.", profileStatus = ProfileStatus.SaveError) }
            return
        }
        val finalCustomTags = newCustomTagNames.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.distinct()
        if (selectedExistingTagIds.isEmpty() && finalCustomTags.isEmpty()) {
            _uiState.update { it.copy(error = "Please select or add at least one subject/interest.", profileStatus = ProfileStatus.SaveError) }
            return
        }

        // --- Prepare and Execute Save ---
        _uiState.update { it.copy(error = null, profileStatus = ProfileStatus.Saving) }
        Timber.d("ProfileViewModel: Saving profile for user $userId")

        viewModelScope.launch {
            // Build JSON payloads expected by the RPC function
            val profileData = buildJsonObject {
                put("p_user_id", userId); put("p_name", trimmedName)
                put("p_school", school.trim().ifBlank { null })
                put("p_preferred_time", preferredTime.trim().ifBlank { null })
                put("p_study_goals", studyGoals.trim().ifBlank { null })
                // `p_profile_picture_url` is included in RPC, but value is null initially from state
                put("p_profile_picture_url", _uiState.value.profilePictureUrl) // Likely null here
            }
            val tagsData = buildJsonObject {
                put("existing_ids", buildJsonArray { selectedExistingTagIds.distinct().forEach { add(it) } })
                put("new_names", buildJsonArray { finalCustomTags.forEach { add(it) } })
            }

            // Call the Supabase RPC function
            try {
                Timber.d("ProfileViewModel: Calling RPC 'save_user_profile_and_tags'")
                supabase.postgrest.rpc("save_user_profile_and_tags", buildJsonObject {
                    put("profile_data", profileData)
                    put("tags_data", tagsData)
                })
                Timber.i("ProfileViewModel: Profile saved successfully via RPC for user $userId")
                // Update state to SaveSuccess; UI should observe this and navigate
                _uiState.update { it.copy(profileStatus = ProfileStatus.SaveSuccess, isLoading = false, isLoadingTags = false) }
            } catch (e: Exception) {
                // Handle Errors during RPC call
                Timber.e(e, "ProfileViewModel: Error saving profile via RPC for user: $userId")
                val errorMsg = when(e) {
                    is RestException -> "Database error saving profile."
                    is HttpRequestException -> "Network error saving profile."
                    else -> "Failed to save profile."
                }
                _uiState.update { it.copy(error = errorMsg, profileStatus = ProfileStatus.SaveError, isLoading = false, isLoadingTags = false) }
            }
        }
    }


    /**
     * Clears any user-facing error message and resets status to NeedsSetup.
     */
    fun clearError() {
        if (_uiState.value.error == null) return // Only act if there's an error
        Timber.d("ProfileViewModel: Clearing error and ensuring status is NeedsSetup.")
        // Always reset to NeedsSetup when clearing error on this screen
        _uiState.update { it.copy(error = null, profileStatus = ProfileStatus.NeedsSetup) }
    }

    // Cancel background jobs when ViewModel is destroyed
    override fun onCleared() {
        super.onCleared()
        // REMOVED: fetchProfileCheckJob?.cancel()
        fetchTagsJob?.cancel() // Cancel tag fetching if in progress
        Timber.d("ProfileViewModel cleared and tag job cancelled.")
    }
}