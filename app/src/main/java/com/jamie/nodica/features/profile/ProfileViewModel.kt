package com.jamie.nodica.features.profile

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// No Routes import needed here for navigation, handled by UI observing state
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from // Ensure explicit import
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException // Import for specific error handling
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import timber.log.Timber

// --- State Definitions ---
@Immutable
data class ProfileSetupState(
    val isLoading: Boolean = true, // Overall loading for check + tags
    val isLoadingTags: Boolean = false, // Specific tag loading flag
    val profileStatus: ProfileStatus = ProfileStatus.Loading, // Initial status is Loading
    val availableTags: Map<String, List<TagItem>> = emptyMap(),
    val profilePictureUrl: String? = null,
    val error: String? = null // For critical/save errors
)

enum class ProfileStatus {
    Loading,        // Initial check in progress.
    NeedsSetup,     // Profile not found or incomplete; setup form should be shown.
    ProfileExists,  // Profile found and complete; setup should be skipped (triggers navigation).
    Saving,
    SaveSuccess,    // Saved successfully (triggers navigation).
    SaveError,
    CriticalError
}

// --- ProfileViewModel ---
/**
 * ViewModel for Profile Setup.
 * Checks if profile setup is needed upon initialization. If profile is already complete,
 * it updates the state to ProfileExists, allowing the UI to skip the setup.
 * If setup is needed, it fetches tags and handles saving the profile.
 */
class ProfileViewModel(
    private val supabase: SupabaseClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileSetupState())
    val uiState: StateFlow<ProfileSetupState> = _uiState.asStateFlow()

    private var fetchTagsJob: Job? = null
    private var fetchProfileCheckJob: Job? = null

    init {
        Timber.d("ProfileViewModel initialized.")
        // Reinstate the profile check on initialization
        checkIfProfileNeedsSetup()
    }

    // Safely gets the current user's ID, updating state to CriticalError if not logged in.
    private fun getCurrentUserId(): String? {
        val userId = supabase.auth.currentUserOrNull()?.id
        if (userId == null) {
            Timber.e("ProfileViewModel: User not logged in.")
            _uiState.update {
                it.copy(isLoading = false, isLoadingTags = false, error = "Authentication error.", profileStatus = ProfileStatus.CriticalError)
            }
        }
        return userId
    }

    /**
     * Checks the profile status for the current user. THIS IS THE RESTORED VERSION.
     * - If complete -> sets ProfileExists state (UI should navigate away).
     * - If incomplete/not found -> sets NeedsSetup state and fetches tags.
     * - If error -> sets CriticalError state.
     */
    private fun checkIfProfileNeedsSetup() {
        if (fetchProfileCheckJob?.isActive == true) return
        val userId = getCurrentUserId() ?: return

        // Start loading, clear previous errors
        _uiState.update { it.copy(isLoading = true, error = null, profileStatus = ProfileStatus.Loading) }
        Timber.d("ProfileViewModel: Checking profile status for user $userId")

        fetchProfileCheckJob = viewModelScope.launch {
            try {
                // --- Step 1: Attempt to fetch and decode profile ---
                val requiredColumns = "id, name, institution, study_goals, profile_picture_url"
                Timber.v("ProfileViewModel: Fetching columns: $requiredColumns")

                // Use decodeSingleOrNull - Handles '[]' or potential decode issues gracefully by returning null
                val profile: UserProfile? = supabase.postgrest.from("users")
                    .select(columns = Columns.raw(requiredColumns)) {
                        filter { eq("id", userId) }
                        limit(1)
                        // NO .single() modifier!
                    }
                    .decodeSingleOrNull<UserProfile>()

                Timber.d("ProfileViewModel: decodeSingleOrNull result: ${if (profile != null) "Found Profile Object" else "Result is null"}")

                // --- Step 2: Process based on the (nullable) profile result ---
                if (profile == null) {
                    // Profile Not Found (or decode failed gracefully) -> Needs Setup
                    Timber.i("ProfileViewModel: Profile is null. Needs setup.")
                    // Update status FIRST, indicate tags need loading
                    _uiState.update { it.copy(profileStatus = ProfileStatus.NeedsSetup, isLoadingTags = true) }
                    fetchAvailableTagsAndUpdateLoading() // This will set isLoading = false when done

                } else if (isProfileDataComplete(profile)) {
                    // Profile Found and IS Complete -> Set ProfileExists state to trigger navigation away
                    Timber.i("ProfileViewModel: Profile found and complete. Setup not needed.")
                    _uiState.update {
                        it.copy(isLoading = false, isLoadingTags = false, profileStatus = ProfileStatus.ProfileExists, profilePictureUrl = profile.profilePictureUrl)
                    } // UI observes ProfileExists and navigates

                } else {
                    // Profile Found but IS Incomplete -> Needs Setup
                    Timber.i("ProfileViewModel: Profile found but incomplete. Needs setup.")
                    _uiState.update { it.copy(profileStatus = ProfileStatus.NeedsSetup, profilePictureUrl = profile.profilePictureUrl, isLoadingTags = true) }
                    fetchAvailableTagsAndUpdateLoading() // Fetch tags (will set isLoading = false)
                }

            } catch (e: Exception) {
                // --- Step 3: Catch only UNEXPECTED/CRITICAL exceptions ---
                Timber.e(e, "ProfileViewModel: CRITICAL Error during profile check for user $userId. Type: ${e::class.java.simpleName}")
                val errorMsg = when (e) {
                    is HttpRequestException -> "Network error checking profile."
                    is RestException -> "Database error checking profile."
                    is SerializationException -> "Unexpected error reading profile data." // Should be rare now
                    else -> "An unexpected error occurred."
                }
                _uiState.update {
                    it.copy(isLoading = false, isLoadingTags = false, error = errorMsg, profileStatus = ProfileStatus.CriticalError)
                }
            }
        }
    }


    // Helper function to check if essential profile data fields are filled.
    private fun isProfileDataComplete(profile: UserProfile): Boolean {
        val complete = profile.name.isNotBlank() &&
                !profile.school.isNullOrBlank() &&
                !profile.studyGoals.isNullOrBlank()
        Timber.v("isProfileDataComplete Check: Name='${profile.name}', School='${profile.school}', Goals='${profile.studyGoals}' -> Complete: $complete")
        return complete
    }


    /**
     * Fetches available tags and updates the main isLoading flag upon completion or error.
     */
    private fun fetchAvailableTagsAndUpdateLoading() {
        if (fetchTagsJob?.isActive == true) return
        Timber.d("ProfileViewModel: Fetching tags (will update loading state afterwards)...")
        // isLoadingTags was set true by the caller

        fetchTagsJob = viewModelScope.launch {
            var tagsError: String? = null
            try {
                val tags = supabase.postgrest.from("tags").select().decodeList<TagItem>()
                val tagsByCategory = tags.groupBy { it.category.uppercase() }
                    .toSortedMap().mapValues { entry -> entry.value.sortedBy { it.name } }
                _uiState.update { it.copy(availableTags = tagsByCategory) }
            } catch (e: Exception) {
                Timber.e(e, "ProfileViewModel: Error fetching available tags.")
                tagsError = "Failed to load subjects/interests."
            } finally {
                Timber.d("ProfileViewModel: Tag fetch finished.")
                _uiState.update {
                    // Stop both loading flags, set error if one occurred during tag fetch
                    it.copy(isLoadingTags = false, isLoading = false, error = it.error ?: tagsError)
                }
            }
        }
    }

    /**
     * Saves the user's profile data and tags via RPC.
     */
    fun saveProfile(
        name: String, school: String, preferredTime: String, studyGoals: String,
        selectedExistingTagIds: List<String>, newCustomTagNames: List<String>
    ) {
        val userId = getCurrentUserId() ?: return
        // Validation
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) { _uiState.update { it.copy(error = "Name cannot be empty.", profileStatus = ProfileStatus.SaveError) }; return }
        val finalCustomTags = newCustomTagNames.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.distinct()
        if (selectedExistingTagIds.isEmpty() && finalCustomTags.isEmpty()) { _uiState.update { it.copy(error = "Please select or add at least one subject/interest.", profileStatus = ProfileStatus.SaveError) }; return }

        _uiState.update { it.copy(error = null, profileStatus = ProfileStatus.Saving) }
        Timber.d("ProfileViewModel: Saving profile for user $userId")
        viewModelScope.launch {
            val profileData = buildJsonObject { /* build object */
                put("p_user_id", userId); put("p_name", trimmedName)
                put("p_school", school.trim().ifBlank { null }); put("p_preferred_time", preferredTime.trim().ifBlank { null })
                put("p_study_goals", studyGoals.trim().ifBlank { null }); put("p_profile_picture_url", _uiState.value.profilePictureUrl)
            }
            val tagsData = buildJsonObject { /* build object */
                put("existing_ids", buildJsonArray { selectedExistingTagIds.distinct().forEach { add(it) } })
                put("new_names", buildJsonArray { finalCustomTags.forEach { add(it) } })
            }
            try {
                supabase.postgrest.rpc("save_user_profile_and_tags", buildJsonObject { put("profile_data", profileData); put("tags_data", tagsData) })
                Timber.i("ProfileViewModel: Profile saved successfully via RPC for user $userId")
                _uiState.update { it.copy(profileStatus = ProfileStatus.SaveSuccess) }
            } catch (e: Exception) {
                val errorMsg = when(e) { /* handle exceptions */
                    is RestException -> "Database error saving profile."
                    is HttpRequestException -> "Network error saving profile."
                    else -> "Failed to save profile." }
                _uiState.update { it.copy(error = errorMsg, profileStatus = ProfileStatus.SaveError) }
            }
        }
    }


    /**
     * Clears any user-facing error message and resets status to NeedsSetup if needed.
     */
    fun clearError() {
        if (_uiState.value.error == null) return
        val currentStatus = _uiState.value.profileStatus
        val newStatus = if (currentStatus == ProfileStatus.SaveError || currentStatus == ProfileStatus.CriticalError) ProfileStatus.NeedsSetup else currentStatus
        Timber.d("ProfileViewModel: Clearing error. Resetting status from $currentStatus to $newStatus")
        _uiState.update { it.copy(error = null, profileStatus = newStatus) }
    }


    // Cancel background jobs when ViewModel is destroyed
    override fun onCleared() {
        super.onCleared()
        fetchProfileCheckJob?.cancel() // Cancel profile check job too
        fetchTagsJob?.cancel()
        Timber.d("ProfileViewModel cleared and jobs cancelled.")
    }
} // End of ProfileViewModel