package com.jamie.nodica.features.profile_management

data class ProfileManagementUiState(
    val name: String = "",
    val school: String = "",
    val preferredTime: String = "",
    val goals: String = "",
    val tags: List<String> = emptyList(),
    val profilePictureUrl: String? = null,
    val loading: Boolean = true,
    val error: String? = null
)
