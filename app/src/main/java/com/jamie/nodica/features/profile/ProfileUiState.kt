package com.jamie.nodica.features.profile

sealed class ProfileUiState {
    object Idle : ProfileUiState()
    object Loading : ProfileUiState()
    object Success : ProfileUiState()
    object ProfileExists : ProfileUiState()
    data class Error(val message: String) : ProfileUiState()
}
