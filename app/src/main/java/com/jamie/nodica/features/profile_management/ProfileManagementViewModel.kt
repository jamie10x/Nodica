package com.jamie.nodica.features.profile_management

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jamie.nodica.features.profile.TagItem // Reusing definition
// No longer need UserProfile from features.profile if UserProfileWithTagIds covers it
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.exceptions.UnknownRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay // Import delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID

// Data structure for fetching profile including their associated tag IDs
@kotlinx.serialization.Serializable
data class UserProfileWithTagIds(
    val id: String,
    val name: String,
    val email: String? = null,
    // Use SerialName to map Kotlin 'school' to DB 'institution' during decode
    @kotlinx.serialization.SerialName("institution")
    val school: String? = null,
    @kotlinx.serialization.SerialName("preferred_time")
    val preferredTime: String? = null,
    @kotlinx.serialization.SerialName("study_goals")
    val studyGoals: String? = null,
    @kotlinx.serialization.SerialName("profile_picture_url")
    val profilePictureUrl: String? = null,
    // Maps the 'user_tags' relation, fetching only 'tag_id'
    @kotlinx.serialization.SerialName("user_tags")
    val userTags: List<UserTagIdLink> = emptyList()
)

@kotlinx.serialization.Serializable
data class UserTagIdLink(@kotlinx.serialization.SerialName("tag_id") val tagId: String)

class ProfileManagementViewModel(
    private val supabase: SupabaseClient,
    private val currentUserId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileManagementScreenState(userId = currentUserId))
    val uiState: StateFlow<ProfileManagementScreenState> = _uiState.asStateFlow()

    private var originalState: ProfileManagementScreenState? = null // For detecting unsaved changes
    private var fetchProfileJob: Job? = null
    private var fetchTagsJob: Job? = null

    init {
        Timber.d("ProfileManagementViewModel initialized for user $currentUserId")
        loadInitialData()
    }

    private fun loadInitialData() {
        // Reset state to loading, clear previous data/errors
        _uiState.update { ProfileManagementScreenState(userId = currentUserId, screenStatus = ProfileManagementStatus.Loading) }
        originalState = null // Clear original state on reload
        fetchProfileJob?.cancel()
        fetchTagsJob?.cancel()
        // Fetch profile and available tags concurrently
        fetchProfileData()
        fetchAvailableTags()
    }

    // Allows UI to trigger a refresh
    fun refreshData() = loadInitialData()

    private fun fetchProfileData() {
        if (fetchProfileJob?.isActive == true) return // Prevent concurrent fetch
        fetchProfileJob = viewModelScope.launch {
            try {
                Timber.d("Fetching profile data for user $currentUserId")
                // FIX: Select specific columns explicitly, map `institution` to `school` via data class
                val selectColumns = Columns.list(
                    "id", "name", "email", "institution",
                    "preferred_time", "study_goals", "profile_picture_url"
                )
                val profileWithTagIds = supabase.from("users")
                    .select(columns = Columns.raw("${selectColumns.value}, user_tags(tag_id)")) {
                        filter { eq("id", currentUserId) }
                        single() // Expect exactly one row
                    }
                    .decodeSingle<UserProfileWithTagIds>()

                // Update the state based on fetched data
                _uiState.update { currentState ->
                    val newState = currentState.copy(
                        userId = profileWithTagIds.id,
                        name = profileWithTagIds.name,
                        school = profileWithTagIds.school.orEmpty(), // Maps from 'institution' field
                        preferredTime = profileWithTagIds.preferredTime.orEmpty(),
                        goals = profileWithTagIds.studyGoals.orEmpty(),
                        email = profileWithTagIds.email ?: supabase.auth.currentUserOrNull()?.email.orEmpty(),
                        profilePictureUrl = profileWithTagIds.profilePictureUrl,
                        selectedTagIds = profileWithTagIds.userTags.map { it.tagId }.toSet(),
                        // Only change status if still Loading, otherwise preserve Saving/Uploading etc.
                        screenStatus = if (currentState.screenStatus == ProfileManagementStatus.Loading) ProfileManagementStatus.Idle else currentState.screenStatus
                    )
                    originalState = newState // Store initial loaded state
                    newState
                }
                Timber.i("Profile data loaded successfully.")

            } catch (e: Exception) {
                val errorMessage = when (e) {
                    is RestException -> "Error loading profile data: ${e.message}" // DB error
                    is HttpRequestException -> "Network error loading profile." // Network error
                    else -> "Failed to load profile data. Please try again." // Generic error
                }
                Timber.e(e, "Error fetching profile data for $currentUserId")
                _uiState.update { it.copy(screenStatus = ProfileManagementStatus.Error(errorMessage)) }
            }
        }
    }

    private fun fetchAvailableTags() {
        if (fetchTagsJob?.isActive == true) return
        fetchTagsJob = viewModelScope.launch {
            try {
                Timber.d("Fetching all available tags...")
                val tags = supabase.from("tags").select().decodeList<TagItem>()
                val tagsByCategory = tags.groupBy { it.category }.mapValues { it.value.sortedBy { tag -> tag.name } }
                _uiState.update { it.copy(availableTags = tagsByCategory) }
                Timber.i("Available tags loaded successfully (${tags.size} tags).")
            } catch (e: Exception) {
                Timber.e(e, "Error fetching available tags.")
                // Update status only if profile loading wasn't already in error
                if (_uiState.value.screenStatus !is ProfileManagementStatus.Error) {
                    // Set error only if not already in an error state from profile fetch
                    _uiState.update { it.copy(screenStatus = ProfileManagementStatus.Error("Could not load available tags.")) }
                }
            }
        }
    }

    // --- UI State Update Functions ---
    fun onNameChange(new: String) = updateState { copy(name = new) }
    fun onSchoolChange(new: String) = updateState { copy(school = new) }
    fun onTimeChange(new: String) = updateState { copy(preferredTime = new) }
    fun onGoalsChange(new: String) = updateState { copy(goals = new) }
    fun toggleTagSelection(tagId: String) = updateState {
        copy(selectedTagIds = if (tagId in selectedTagIds) selectedTagIds - tagId else selectedTagIds + tagId)
    }

    // --- Profile Picture Upload ---
    fun uploadProfilePicture(context: Context, uri: Uri) {
        val currentStatus = _uiState.value.screenStatus
        if (currentStatus == ProfileManagementStatus.Uploading || currentStatus == ProfileManagementStatus.Saving) return

        _uiState.update { it.copy(screenStatus = ProfileManagementStatus.Uploading) }
        viewModelScope.launch {
            val fileExtension = context.contentResolver.getType(uri)?.substringAfter('/') ?: "jpg"
            val uniqueFileName = "profile_${UUID.randomUUID()}.$fileExtension"
            val storagePath = "$currentUserId/$uniqueFileName"

            try {
                val fileBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw Exception("Could not read image data from Uri.")

                Timber.d("Uploading profile picture to path: $storagePath")
                supabase.storage.from("profile_pictures").upload(storagePath, fileBytes) { upsert = false }

                // Get public URL (adjust if bucket is private - needs signed URLs)
                val publicUrl = supabase.storage.from("profile_pictures").publicUrl(storagePath)
                Timber.i("Profile picture uploaded. Public URL: $publicUrl")

                // Update UI and save URL to DB
                updateState { copy(profilePictureUrl = publicUrl, screenStatus = ProfileManagementStatus.Idle) }
                saveProfilePictureUrlToDb(publicUrl)

            } catch (e: UnknownRestException) {
                Timber.e(e, "Supabase Storage Error (Bucket/Policy?): ${e.message}")
                updateState { copy(screenStatus = ProfileManagementStatus.Error("Storage error: ${e.message}")) }
            } catch (e: HttpRequestException) {
                Timber.e(e, "Network error uploading picture")
                updateState { copy(screenStatus = ProfileManagementStatus.Error("Network error uploading image.")) }
            } catch (e: Exception) {
                Timber.e(e, "Failed to upload profile picture")
                updateState { copy(screenStatus = ProfileManagementStatus.Error("Image upload failed: ${e.message}")) }
            }
        }
    }

    private suspend fun saveProfilePictureUrlToDb(url: String?) {
        try {
            supabase.from("users").update(mapOf("profile_picture_url" to url)) {
                filter { eq("id", currentUserId) }
            }
            // Update original state as well so change detection works correctly after pic upload + other edits
            originalState = originalState?.copy(profilePictureUrl = url)
            Timber.i("Profile picture URL saved to DB.")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save profile picture URL to database.")
            // Update state to show error, URL save failed
            _uiState.update { it.copy(screenStatus = ProfileManagementStatus.Error("Failed to update profile picture link.")) }
        }
    }

    // --- Save All Changes ---
    fun saveChanges() {
        val currentState = _uiState.value
        if (currentState.screenStatus == ProfileManagementStatus.Saving || currentState.screenStatus == ProfileManagementStatus.Uploading) return

        _uiState.update { it.copy(screenStatus = ProfileManagementStatus.Saving) }
        viewModelScope.launch {
            try {
                // 1. Update User Profile Fields
                val profileUpdates = mapOf(
                    "name" to currentState.name.trim(),
                    "institution" to currentState.school.trim().ifBlank { null }, // Map UI 'school' back to DB 'institution'
                    "preferred_time" to currentState.preferredTime.trim().ifBlank { null },
                    "study_goals" to currentState.goals.trim().ifBlank { null },
                    // Make sure the picture URL currently in state is included
                    "profile_picture_url" to currentState.profilePictureUrl
                )
                supabase.from("users").update(profileUpdates) { filter { eq("id", currentUserId) } }
                Timber.d("User profile core fields updated in DB.")

                // 2. Synchronize User Tags
                updateUserTagsInDb(currentState.selectedTagIds)
                Timber.d("User tags synchronized in DB.")

                // 3. Update state to reflect success
                val successState = currentState.copy(screenStatus = ProfileManagementStatus.Success)
                originalState = successState // Update original state
                _uiState.value = successState
                Timber.i("Profile changes saved successfully.")

                // Optional delay then back to Idle
                delay(1000)
                if (_uiState.value.screenStatus == ProfileManagementStatus.Success) {
                    _uiState.update { it.copy(screenStatus = ProfileManagementStatus.Idle) }
                }

            } catch (e: Exception) {
                Timber.e(e, "Failed to save profile changes")
                _uiState.update { it.copy(screenStatus = ProfileManagementStatus.Error("Failed to save changes: ${e.message}")) }
            }
        }
    }

    // Tag sync logic: Delete old, insert new. Needs transaction for full atomicity.
    private suspend fun updateUserTagsInDb(desiredTagIds: Set<String>) {
        Timber.d("Syncing tags for user $currentUserId. Desired: $desiredTagIds")
        try {
            // For simplicity, delete all then insert all.
            // A more optimized way would fetch current IDs, calc diff, and do targeted deletes/inserts.
            supabase.from("user_tags").delete { filter { eq("user_id", currentUserId) } }
            Timber.v("Deleted existing tag links for user $currentUserId.")

            if (desiredTagIds.isNotEmpty()) {
                val linksToInsert = desiredTagIds.map { tagId -> mapOf("user_id" to currentUserId, "tag_id" to tagId) }
                supabase.from("user_tags").insert(linksToInsert)
                Timber.d("Inserted ${linksToInsert.size} new tag links for user $currentUserId.")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during DB tag update operation for $currentUserId")
            throw Exception("Database error updating tags.", e) // Re-throw to fail the saveChanges operation
        }
    }


    // --- Logout ---
    fun logout(onComplete: () -> Unit) {
        val currentStatus = _uiState.value.screenStatus
        if (currentStatus == ProfileManagementStatus.Saving || currentStatus == ProfileManagementStatus.Uploading) {
            // Prevent logout during critical operations, or handle cancellation gracefully
            Timber.w("Logout attempted while Saving/Uploading.")
            updateState { copy(screenStatus = ProfileManagementStatus.Error("Please wait for the current operation to finish.")) }
            return
        }

        viewModelScope.launch {
            try {
                supabase.auth.signOut()
                Timber.i("User $currentUserId logged out successfully.")
                // Optional: Clear sensitive state if needed, though navigation usually handles this.
                // _uiState.value = ProfileManagementScreenState() // Reset state
                onComplete() // Trigger navigation in the UI
            } catch (e: Exception) {
                Timber.e(e, "Logout failed")
                _uiState.update { it.copy(screenStatus = ProfileManagementStatus.Error("Logout failed: ${e.message}")) }
            }
        }
    }

    // Helper to update state immutably
    private fun updateState(block: ProfileManagementScreenState.() -> ProfileManagementScreenState) { _uiState.update(block) }

    // Helper to clear error status, returning to Idle
    fun clearErrorStatus() {
        if (_uiState.value.screenStatus is ProfileManagementStatus.Error) {
            _uiState.update { it.copy(screenStatus = ProfileManagementStatus.Idle) }
        }
    }
}