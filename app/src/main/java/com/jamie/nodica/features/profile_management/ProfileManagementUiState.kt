package com.jamie.nodica.features.profile_management

import androidx.compose.runtime.Immutable
import com.jamie.nodica.features.profile.TagItem

// Sealed class representing different states of the profile management process.
@Immutable
sealed class ProfileManagementStatus {
    object Idle : ProfileManagementStatus()
    object Loading : ProfileManagementStatus()
    object Saving : ProfileManagementStatus()
    object Uploading : ProfileManagementStatus()
    object Success : ProfileManagementStatus()
    data class Error(val message: String) : ProfileManagementStatus()
}

// New sealed class for representing errors in profile management.
@Immutable
sealed class ProfileError {
    object None : ProfileError()
    data class Validation(val message: String) : ProfileError()
    data class Network(val message: String) : ProfileError()
    data class Unknown(val message: String) : ProfileError()
}

// Data class holding the state for profile management in the profile tab.
@Immutable
data class ProfileManagementScreenState(
    val userId: String = "",
    val name: String = "",
    val school: String = "",
    val preferredTime: String = "",
    val goals: String = "",
    val email: String = "",
    val profilePictureUrl: String? = null,
    val error: String? = ProfileError.None.toString(),
    val availableTags: Map<String, List<TagItem>> = emptyMap(),
    val selectedTagIds: Set<String> = emptySet(),
    val screenStatus: ProfileManagementStatus = ProfileManagementStatus.Loading,
) {
    // Returns true if profile data is loaded (userId is not blank and loading is finished).
    val isProfileLoaded: Boolean
        get() = userId.isNotBlank() && screenStatus != ProfileManagementStatus.Loading

    // Checks for unsaved changes, comparing name case-insensitively.
    fun hasUnsavedChanges(originalState: ProfileManagementScreenState?): Boolean {
        if (originalState == null) return true
        return !name.equals(originalState.name, ignoreCase = true) ||
                school != originalState.school ||
                preferredTime != originalState.preferredTime ||
                goals != originalState.goals ||
                selectedTagIds != originalState.selectedTagIds ||
                profilePictureUrl != originalState.profilePictureUrl
    }
}
