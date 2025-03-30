// ProfileManagementViewModel.kt

package com.jamie.nodica.features.profile_management

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jamie.nodica.features.profile.TagItem
import com.jamie.nodica.features.profile.UserProfileWithTags
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.exceptions.UnknownRestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
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
import java.util.UUID

class ProfileManagementViewModel(
    private val supabase: SupabaseClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileManagementScreenState())
    val uiState: StateFlow<ProfileManagementScreenState> = _uiState.asStateFlow()

    internal var originalState: ProfileManagementScreenState? = null

    private val currentUser: UserInfo?
        get() = supabase.auth.currentUserOrNull()

    private val currentUserId: String?
        get() {
            val id = currentUser?.id
            if (id == null) {
                Timber.e("ProfileManagementViewModel: User ID is null.")
                _uiState.update {
                    it.copy(
                        screenStatus = ProfileManagementStatus.Error("Authentication Error."),
                        error = "Authentication Error.",
                        userId = ""
                    )
                }
            } else if (_uiState.value.userId != id) {
                _uiState.update { it.copy(userId = id) }
            }
            return id
        }

    init {
        Timber.d("ProfileManagementViewModel initialized.")
        _uiState.update { it.copy(userId = currentUser?.id ?: "") }
        loadInitialData()
    }

    /**
     * Loads profile data and available tags concurrently.
     */
    private fun loadInitialData() {
        val userId = currentUserId ?: run {
            _uiState.update {
                ProfileManagementScreenState(
                    userId = "",
                    screenStatus = ProfileManagementStatus.Error("Please log in to view your profile."),
                    error = "Please log in to view your profile."
                )
            }
            return
        }
        _uiState.update { it.copy(screenStatus = ProfileManagementStatus.Loading, error = null) }
        originalState = null

        viewModelScope.launch {
            // Launch concurrent async tasks for profile and tags
            val profileDeferred = async { fetchProfileData(userId) }
            val tagsDeferred = async { fetchTagsData() }

            val profileResult = profileDeferred.await()
            val tagsResult = tagsDeferred.await()

            // Process profile fetch result
            if (profileResult.isSuccess) {
                val profileData = profileResult.getOrThrow()
                _uiState.update { currentState ->
                    currentState.copy(
                        userId = profileData.id,
                        name = profileData.name,
                        school = profileData.school.orEmpty(),
                        preferredTime = profileData.preferredTime.orEmpty(),
                        goals = profileData.studyGoals.orEmpty(),
                        email = profileData.email ?: currentUser?.email.orEmpty(),
                        profilePictureUrl = profileData.profilePictureUrl,
                        selectedTagIds = profileData.tagIds,
                        error = null
                    )
                }
                originalState = _uiState.value
                Timber.i("Profile data loaded successfully.")
            } else {
                val errorMsg = profileResult.exceptionOrNull()?.message ?: "Failed to load profile data."
                Timber.e("Error fetching profile data: $errorMsg")
                _uiState.update { it.copy(screenStatus = ProfileManagementStatus.Error(errorMsg), error = errorMsg) }
            }

            // Process tags fetch result
            if (tagsResult.isSuccess) {
                val tags = tagsResult.getOrThrow()
                val tagsByCategory = tags.groupBy { it.category.uppercase() }
                    .toSortedMap()
                    .mapValues { entry -> entry.value.sortedBy { it.name } }
                _uiState.update { it.copy(availableTags = tagsByCategory) }
                Timber.i("Available tags loaded successfully.")
            } else {
                val errorMsg = tagsResult.exceptionOrNull()?.message ?: "Could not load available tags."
                Timber.e("Error fetching tags: $errorMsg")
                _uiState.update { currentState ->
                    if (currentState.screenStatus !is ProfileManagementStatus.Error)
                        currentState.copy(screenStatus = ProfileManagementStatus.Error(errorMsg), error = errorMsg)
                    else currentState
                }
            }

            if (_uiState.value.screenStatus !is ProfileManagementStatus.Error) {
                _uiState.update { it.copy(screenStatus = ProfileManagementStatus.Idle) }
            }
            Timber.d("Initial data load complete. Final status: ${_uiState.value.screenStatus}")
        }
    }

    // Helper function to fetch profile data.
    private suspend fun fetchProfileData(userId: String): Result<UserProfileWithTags> {
        return runCatching {
            val selectColumns = Columns.list("id", "name", "email", "institution", "preferred_time", "study_goals", "profile_picture_url", "created_at")
            val profiles = supabase.postgrest.from("users")
                .select(columns = Columns.raw("${selectColumns.value}, user_tags(tag_id)")) {
                    filter { eq("id", userId) }
                }
                .decodeList<UserProfileWithTags>()
            if (profiles.isNotEmpty()) profiles.first() else throw Exception("Profile not found")
        }
    }

    // Helper function to fetch available tags.
    private suspend fun fetchTagsData(): Result<List<TagItem>> {
        return runCatching {
            supabase.postgrest.from("tags").select().decodeList<TagItem>()
        }
    }

    fun onNameChange(new: String) = _uiState.update { it.copy(name = new) }
    fun onSchoolChange(new: String) = _uiState.update { it.copy(school = new) }
    fun onTimeChange(new: String) = _uiState.update { it.copy(preferredTime = new) }
    fun onGoalsChange(new: String) = _uiState.update { it.copy(goals = new) }
    fun toggleTagSelection(tagId: String) = _uiState.update {
        it.copy(selectedTagIds = if (tagId in it.selectedTagIds) it.selectedTagIds - tagId else it.selectedTagIds + tagId)
    }

    fun uploadProfilePicture(context: Context, uri: Uri) {
        val userId = currentUserId ?: return
        if (_uiState.value.screenStatus == ProfileManagementStatus.Uploading || _uiState.value.screenStatus == ProfileManagementStatus.Saving) return

        _uiState.update { it.copy(screenStatus = ProfileManagementStatus.Uploading) }
        viewModelScope.launch {
            val fileExtension = MimeTypeMap.getSingleton().getExtensionFromMimeType(context.contentResolver.getType(uri))
                ?: uri.lastPathSegment?.substringAfterLast('.') ?: "jpg"
            val uniqueFileName = "profile_${UUID.randomUUID()}.$fileExtension"
            val storagePath = "$userId/$uniqueFileName"
            var publicUrl: String? = null

            try {
                val fileBytes = context.contentResolver.openInputStream(uri)?.readBytes() ?: throw Exception("Could not read image data.")
                Timber.d("Uploading profile picture to: $storagePath")
                supabase.storage.from("profile_pictures").upload(storagePath, fileBytes) { upsert = false }
                publicUrl = supabase.storage.from("profile_pictures").publicUrl(storagePath)
                Timber.i("Uploaded profile picture. Public URL: $publicUrl")
                saveProfilePictureUrlToDb(publicUrl)
                _uiState.update { it.copy(profilePictureUrl = publicUrl, screenStatus = ProfileManagementStatus.Success, error = null) }
                originalState = originalState?.copy(profilePictureUrl = publicUrl)
                delay(500)
                if (_uiState.value.screenStatus == ProfileManagementStatus.Success) {
                    _uiState.update { it.copy(screenStatus = ProfileManagementStatus.Idle) }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed during profile picture upload/save.")
                val errorMsg = when (e) {
                    is UnknownRestException -> "Storage error: ${e.message}"
                    is HttpRequestException -> "Network error."
                    is SecurityException -> "Permission denied."
                    else -> e.message ?: "Image upload/save failed."
                }
                _uiState.update { it.copy(screenStatus = ProfileManagementStatus.Error(errorMsg), error = errorMsg, profilePictureUrl = publicUrl ?: it.profilePictureUrl) }
            }
        }
    }

    private suspend fun saveProfilePictureUrlToDb(url: String?) {
        val userId = currentUserId ?: throw IllegalStateException("User not logged in")
        try {
            supabase.postgrest.from("users").update(mapOf("profile_picture_url" to url)) {
                filter { eq("id", userId) }
            }
            Timber.i("Profile picture URL saved to DB.")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save profile picture URL to database.")
            throw Exception("Failed to update profile picture link.", e)
        }
    }

    fun saveChanges(newCustomTagNames: List<String>) {
        val userId = currentUserId ?: return
        val currentState = _uiState.value
        val hasBaseChanges = currentState.hasUnsavedChanges(originalState)
        val hasNewCustomTags = newCustomTagNames.any { it.isNotBlank() }

        if (!hasBaseChanges && !hasNewCustomTags) {
            Timber.i("No changes detected, skipping save.")
            _uiState.update { it.copy(screenStatus = ProfileManagementStatus.Idle) }
            return
        }
        if (currentState.screenStatus == ProfileManagementStatus.Saving || currentState.screenStatus == ProfileManagementStatus.Uploading) return

        _uiState.update { it.copy(screenStatus = ProfileManagementStatus.Saving, error = null) }
        viewModelScope.launch {
            try {
                if (currentState.name.isBlank()) throw IllegalArgumentException("Name cannot be empty.")

                val profileData = buildJsonObject {
                    put("p_user_id", userId)
                    put("p_name", currentState.name.trim())
                    put("p_school", currentState.school.trim().ifBlank { null })
                    put("p_preferred_time", currentState.preferredTime.trim().ifBlank { null })
                    put("p_study_goals", currentState.goals.trim().ifBlank { null })
                    put("p_profile_picture_url", currentState.profilePictureUrl)
                }

                val tagsData = buildJsonObject {
                    put("existing_ids", buildJsonArray { currentState.selectedTagIds.distinct().forEach { add(it) } })
                    put("new_names", buildJsonArray { newCustomTagNames.mapNotNull { it.trim().takeIf(String::isNotEmpty) }.distinct().forEach { add(it) } })
                }

                Timber.d("Calling save_user_profile_and_tags RPC for user $userId")
                supabase.postgrest.rpc("save_user_profile_and_tags", buildJsonObject {
                    put("profile_data", profileData)
                    put("tags_data", tagsData)
                })

                Timber.i("Profile changes saved successfully via RPC.")
                val successState = currentState.copy(screenStatus = ProfileManagementStatus.Success, error = null)
                originalState = successState
                _uiState.value = successState

                delay(1000)
                if (_uiState.value.screenStatus == ProfileManagementStatus.Success) {
                    _uiState.update { it.copy(screenStatus = ProfileManagementStatus.Idle) }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to save profile changes")
                val errorMsg = when (e) {
                    is IllegalArgumentException -> e.message ?: "Validation failed."
                    is RestException -> "Database error saving profile: ${e.message}"
                    else -> "Failed to save changes: ${e.message}"
                }
                _uiState.update { it.copy(screenStatus = ProfileManagementStatus.Error(errorMsg), error = errorMsg) }
            }
        }
    }

    fun logout(onComplete: () -> Unit) {
        val userId = currentUserId
        if (_uiState.value.screenStatus == ProfileManagementStatus.Saving || _uiState.value.screenStatus == ProfileManagementStatus.Uploading) {
            Timber.w("Logout attempted while Saving/Uploading.")
            _uiState.update { it.copy(screenStatus = ProfileManagementStatus.Error("Please wait for the current operation to finish."), error = "Please wait.") }
            return
        }
        viewModelScope.launch {
            try {
                supabase.auth.signOut()
                Timber.i("User $userId logged out successfully.")
                _uiState.value = ProfileManagementScreenState()
                originalState = null
                onComplete()
            } catch (e: Exception) {
                Timber.e(e, "Logout failed")
                _uiState.update { it.copy(screenStatus = ProfileManagementStatus.Error("Logout failed: ${e.message}"), error = "Logout failed.") }
            }
        }
    }

    fun clearErrorStatus() {
        if (_uiState.value.screenStatus is ProfileManagementStatus.Error) {
            _uiState.update { it.copy(screenStatus = ProfileManagementStatus.Idle, error = null) }
        }
    }

    fun refreshData() {
        // Prevent refresh if an operation is in progress.
        val currentStatus = _uiState.value.screenStatus
        if (currentStatus == ProfileManagementStatus.Loading ||
            currentStatus == ProfileManagementStatus.Saving ||
            currentStatus == ProfileManagementStatus.Uploading) return
        Timber.d("Refreshing profile data...")
        loadInitialData()
    }
}