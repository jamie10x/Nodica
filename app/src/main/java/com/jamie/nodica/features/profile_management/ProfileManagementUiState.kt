package com.jamie.nodica.features.profile_management

import com.jamie.nodica.features.profile.TagItem // Reusing TagItem

// Represents the various states the profile management screen can be in, especially regarding operations
sealed class ProfileManagementStatus {
    object Idle : ProfileManagementStatus()        // Default, ready for interaction
    object Loading : ProfileManagementStatus()     // Initial profile data fetch
    object Saving : ProfileManagementStatus()      // Profile update operation in progress
    object Uploading : ProfileManagementStatus()   // Profile picture upload in progress
    object Success : ProfileManagementStatus()     // Generic success feedback if needed (e.g., after save)
    data class Error(val message: String) : ProfileManagementStatus() // Holds specific error message
}

// Holds all the data and the current status for the profile management screen
data class ProfileManagementScreenState(
    // User Profile Data (editable and display-only fields)
    val userId: String = "",
    val name: String = "",
    val school: String = "", // Consistent naming with UI
    val preferredTime: String = "",
    val goals: String = "",
    val email: String = "", // Read-only email
    val profilePictureUrl: String? = null, // Nullable URL

    // Tag Management Data
    val availableTags: Map<String, List<TagItem>> = emptyMap(), // All tags from DB, grouped
    val selectedTagIds: Set<String> = emptySet(), // Set of IDs for currently selected tags

    // Screen Operation Status
    val screenStatus: ProfileManagementStatus = ProfileManagementStatus.Loading, // Start in Loading state
) {
    // Helper to easily check if profile has been successfully loaded at least once
    val isProfileLoaded: Boolean
        get() = userId.isNotBlank() && screenStatus != ProfileManagementStatus.Loading
}