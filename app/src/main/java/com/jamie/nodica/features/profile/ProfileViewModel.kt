package com.jamie.nodica.features.profile

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import timber.log.Timber

// --- State Definitions ---
@Immutable
data class ProfileSetupState(
    val loading: Boolean = true,             // Overall loading state (profile check + tag loading)
    val isLoadingTags: Boolean = true,         // Tag loading state
    val profileStatus: ProfileStatus = ProfileStatus.Loading,
    val availableTags: Map<String, List<TagItem>> = emptyMap(),
    val profilePictureUrl: String? = null,
    val error: String? = null
)

// Enum representing profile setup status
enum class ProfileStatus {
    Loading,        // Initial check in progress
    NeedsSetup,     // Profile not found or incomplete
    ProfileExists,  // Profile found and complete; no setup needed
    Saving,         // Save operation in progress
    SaveSuccess,    // Profile saved successfully
    SaveError,      // Save operation failed (validation or network/DB error)
    CriticalError   // Critical error preventing setup (e.g., not logged in)
}

// Sealed class representing the result of checking profile existence
sealed class ProfileCheckResult {
    object FoundAndComplete : ProfileCheckResult()
    object FoundButIncomplete : ProfileCheckResult()
    object NotFound : ProfileCheckResult()
    data class Error(val exception: Exception) : ProfileCheckResult()
}

class ProfileViewModel(
    private val supabase: SupabaseClient
) : ViewModel() {

    internal val _uiState = MutableStateFlow(ProfileSetupState())
    val uiState: StateFlow<ProfileSetupState> = _uiState.asStateFlow()

    private var fetchTagsJob: Job? = null
    private var fetchProfileCheckJob: Job? = null

    init {
        Timber.d("ProfileViewModel initialized (for Setup).")
        checkIfProfileNeedsSetup()
    }

    // Retrieves the current user ID from Supabase
    private fun getCurrentUserId(): String? {
        val userId = supabase.auth.currentUserOrNull()?.id
        if (userId == null) {
            Timber.e("Cannot perform profile setup: User not logged in.")
            _uiState.update {
                it.copy(
                    loading = false,
                    isLoadingTags = false,
                    error = "Authentication error. Please log in again.",
                    profileStatus = ProfileStatus.CriticalError
                )
            }
        }
        return userId
    }

    // Checks if the profile exists and is complete, using a sealed class to model results.
    private fun checkIfProfileNeedsSetup() {
        if (fetchProfileCheckJob?.isActive == true) return
        val userId = getCurrentUserId() ?: return

        _uiState.update { it.copy(loading = true, error = null, profileStatus = ProfileStatus.Loading) }
        fetchProfileCheckJob = viewModelScope.launch {
            val profileCheckResult: ProfileCheckResult = try {
                val profileResult = supabase.from("users")
                    .select(Columns.list("name")) {
                        filter { eq("id", userId) }
                        limit(1)
                        single()
                    }
                    .decodeSingle<UserProfile>()
                if (profileResult.name.isNotBlank()) {
                    ProfileCheckResult.FoundAndComplete
                } else {
                    ProfileCheckResult.FoundButIncomplete
                }
            } catch (e: RestException) {
                if (e.message == "PGRST116" || e.message?.contains("0 rows") == true) {
                    ProfileCheckResult.NotFound
                } else {
                    ProfileCheckResult.Error(e)
                }
            } catch (e: HttpRequestException) {
                ProfileCheckResult.Error(e)
            } catch (e: Exception) {
                ProfileCheckResult.Error(e)
            }

            when (profileCheckResult) {
                is ProfileCheckResult.FoundAndComplete -> {
                    Timber.i("Profile exists and is complete for user: $userId. Skipping setup.")
                    _uiState.update { it.copy(loading = false, profileStatus = ProfileStatus.ProfileExists) }
                }
                is ProfileCheckResult.FoundButIncomplete,
                is ProfileCheckResult.NotFound -> {
                    Timber.i("Profile needs setup for user: $userId. Fetching available tags...")
                    _uiState.update { it.copy(profileStatus = ProfileStatus.NeedsSetup) }
                    fetchAvailableTags()
                }
                is ProfileCheckResult.Error -> {
                    Timber.e(profileCheckResult.exception, "Error checking profile for user $userId.")
                    _uiState.update {
                        it.copy(
                            loading = false,
                            error = "Error checking profile: ${profileCheckResult.exception.message}",
                            profileStatus = ProfileStatus.CriticalError
                        )
                    }
                }
            }

            // If a critical error occurred or profile exists, ensure loading flags are off.
            if (_uiState.value.profileStatus == ProfileStatus.CriticalError ||
                _uiState.value.profileStatus == ProfileStatus.ProfileExists
            ) {
                _uiState.update { it.copy(loading = false, isLoadingTags = false) }
            }
        }
    }

    // Fetches available tags from the 'tags' table and groups them by category.
    private fun fetchAvailableTags() {
        if (fetchTagsJob?.isActive == true) return
        _uiState.update { it.copy(isLoadingTags = true, error = null) }
        fetchTagsJob = viewModelScope.launch {
            try {
                val tags = supabase.from("tags").select().decodeList<TagItem>()
                val tagsByCategory = tags.groupBy { it.category.uppercase() }
                    .toSortedMap()
                    .mapValues { entry -> entry.value.sortedBy { it.name } }

                _uiState.update { currentState ->
                    currentState.copy(
                        availableTags = tagsByCategory,
                        isLoadingTags = false,
                        loading = if (currentState.profileStatus == ProfileStatus.NeedsSetup) false else currentState.loading,
                        profileStatus = if (currentState.profileStatus == ProfileStatus.Loading) ProfileStatus.NeedsSetup else currentState.profileStatus
                    )
                }
                Timber.i("Fetched ${tags.size} tags, grouped into ${tagsByCategory.size} categories.")
            } catch (e: Exception) {
                Timber.e(e, "Error fetching available tags.")
                _uiState.update {
                    it.copy(
                        isLoadingTags = false,
                        loading = false,
                        error = "Failed to load subjects/interests: ${e.message}"
                    )
                }
            }
        }
    }

    // Saves the user's profile and associated tags using an RPC call.
    fun saveProfile(
        name: String,
        school: String,
        preferredTime: String,
        studyGoals: String,
        selectedExistingTagIds: List<String>,
        newCustomTagNames: List<String>
    ) {
        val userId = getCurrentUserId() ?: return

        // Client-side validations
        if (name.isBlank()) {
            _uiState.update { it.copy(error = "Name cannot be empty.", profileStatus = ProfileStatus.SaveError) }
            return
        }
        if (selectedExistingTagIds.isEmpty() && newCustomTagNames.all { it.isBlank() }) {
            _uiState.update { it.copy(error = "Please select or add at least one subject/interest.", profileStatus = ProfileStatus.SaveError) }
            return
        }

        _uiState.update { it.copy(error = null, profileStatus = ProfileStatus.Saving) }
        viewModelScope.launch {
            val profileData = buildJsonObject {
                put("p_user_id", userId)
                put("p_name", name.trim())
                put("p_school", school.trim().ifBlank { null })
                put("p_preferred_time", preferredTime.trim().ifBlank { null })
                put("p_study_goals", studyGoals.trim().ifBlank { null })
                put("p_profile_picture_url", _uiState.value.profilePictureUrl)
            }

            val tagsData = buildJsonObject {
                put("existing_ids", buildJsonArray {
                    selectedExistingTagIds.distinct().forEach { add(it) }
                })
                put("new_names", buildJsonArray {
                    newCustomTagNames.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
                        .distinct()
                        .forEach { add(it) }
                })
            }

            try {
                Timber.d("Calling save_user_profile_and_tags RPC for user: $userId")
                supabase.postgrest.rpc("save_user_profile_and_tags", buildJsonObject {
                    put("profile_data", profileData)
                    put("tags_data", tagsData)
                })
                Timber.i("Profile and tags saved successfully for user: $userId")
                _uiState.update {
                    it.copy(
                        profileStatus = ProfileStatus.SaveSuccess,
                        loading = false,
                        isLoadingTags = false
                    )
                }
            } catch (e: RestException) {
                Timber.e(e, "Database error saving profile for user: $userId")
                _uiState.update {
                    it.copy(
                        error = "Database error: ${e.message}",
                        profileStatus = ProfileStatus.SaveError,
                        loading = false,
                        isLoadingTags = false
                    )
                }
            } catch (e: HttpRequestException) {
                Timber.e(e, "Network error saving profile for user: $userId")
                _uiState.update {
                    it.copy(
                        error = "Network error saving profile.",
                        profileStatus = ProfileStatus.SaveError,
                        loading = false,
                        isLoadingTags = false
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error saving profile for user: $userId")
                _uiState.update {
                    it.copy(
                        error = "Failed to save profile: ${e.message}",
                        profileStatus = ProfileStatus.SaveError,
                        loading = false,
                        isLoadingTags = false
                    )
                }
            }
        }
    }

    // Clears the current error and resets profileStatus if appropriate.
    fun clearError() {
        val currentStatus = _uiState.value.profileStatus
        val newStatus = if (currentStatus == ProfileStatus.SaveError || currentStatus == ProfileStatus.CriticalError) {
            ProfileStatus.NeedsSetup
        } else {
            currentStatus
        }
        _uiState.update { it.copy(error = null, profileStatus = newStatus) }
    }

    // Acknowledges a successful save by transitioning the state to ProfileExists.
    fun acknowledgeSuccess() {
        if (_uiState.value.profileStatus == ProfileStatus.SaveSuccess) {
            _uiState.update { it.copy(profileStatus = ProfileStatus.ProfileExists) }
        }
    }
}
