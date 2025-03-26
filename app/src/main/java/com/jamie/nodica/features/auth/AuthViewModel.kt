package com.jamie.nodica.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class AuthViewModel(
    private val supabase: SupabaseClient
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val authState: StateFlow<AuthUiState> = _authState

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _authState.value = AuthUiState.Error("Email and password must not be empty")
            return
        }
        _authState.value = AuthUiState.Loading
        viewModelScope.launch {
            try {
                supabase.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
                _authState.value = AuthUiState.Success
            } catch (e: Exception) {
                Timber.e(e, "Sign in failed")
                _authState.value = AuthUiState.Error(sanitizeError(e))
            }
        }
    }

    fun signUp(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _authState.value = AuthUiState.Error("Email and password must not be empty")
            return
        }
        _authState.value = AuthUiState.Loading
        viewModelScope.launch {
            try {
                supabase.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password
                }
                _authState.value = AuthUiState.Success
            } catch (e: Exception) {
                Timber.e(e, "Sign up failed")
                _authState.value = AuthUiState.Error(sanitizeError(e))
            }
        }
    }

    fun signInWithGoogle() {
        _authState.value = AuthUiState.Loading
        viewModelScope.launch {
            try {
                supabase.auth.signInWith(Google)
                _authState.value = AuthUiState.Success
            } catch (e: Exception) {
                Timber.e(e, "Google sign in failed")
                _authState.value = AuthUiState.Error(sanitizeError(e))
            }
        }
    }

    fun resetState() {
        _authState.value = AuthUiState.Idle
    }

    private fun sanitizeError(e: Exception): String {
        val message = e.message ?: ""
        return if (message.contains("invalid_credentials", ignoreCase = true) ||
            message.contains("invalid login credentials", ignoreCase = true)
        ) {
            "Invalid email or password. Please try again."
        } else {
            "Something went wrong. Please try again later."
        }
    }
}
