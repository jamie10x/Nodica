package com.jamie.nodica.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

private const val MIN_PASSWORD_LENGTH = 6
private const val PASSWORD_LENGTH_ERROR = "Password must be at least $MIN_PASSWORD_LENGTH characters"
private val EMAIL_REGEX = "^[A-Za-z](.*)(@)(.+)(\\.)(.+)".toRegex()

class AuthViewModel(private val supabase: SupabaseClient) : ViewModel() {

    private val _authState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

    fun signIn(email: String, password: String) {
        if (!EMAIL_REGEX.matches(email.trim())) {
            _authState.value = AuthUiState.Error("Invalid email format")
            return
        }
        if (email.isBlank() || password.isBlank()) {
            _authState.value = AuthUiState.Error("Email and password must not be empty")
            return
        }
        _authState.value = AuthUiState.Loading
        viewModelScope.launch {
            try {
                supabase.auth.signInWith(Email) {
                    this.email = email.trim()
                    this.password = password
                }
                _authState.value = AuthUiState.Success
                Timber.i("User signed in successfully.")
            } catch (e: Exception) {
                Timber.e(e, "Sign in failed")
                _authState.value = AuthUiState.Error(sanitizeAuthError(e))
            }
        }
    }

    fun signUp(email: String, password: String) {
        if (!EMAIL_REGEX.matches(email.trim())) {
            _authState.value = AuthUiState.Error("Invalid email format")
            return
        }
        if (email.isBlank()) {
            _authState.value = AuthUiState.Error("Email must not be empty")
            return
        }
        if (password.length < MIN_PASSWORD_LENGTH) {
            _authState.value = AuthUiState.Error(PASSWORD_LENGTH_ERROR)
            return
        }
        _authState.value = AuthUiState.Loading
        viewModelScope.launch {
            try {
                supabase.auth.signUpWith(Email) {
                    this.email = email.trim()
                    this.password = password
                }
                _authState.value = AuthUiState.Success
                Timber.i("User signed up successfully. Check your email for verification if enabled.")
            } catch (e: Exception) {
                Timber.e(e, "Sign up failed")
                _authState.value = AuthUiState.Error(sanitizeAuthError(e))
            }
        }
    }

    fun signInOrSignUpWithGoogle() {
        if (_authState.value == AuthUiState.Loading) return
        _authState.value = AuthUiState.Loading
        viewModelScope.launch {
            try {
                supabase.auth.signInWith(Google)
                _authState.value = AuthUiState.Success
                Timber.i("Google sign-in initiated successfully.")
            } catch (e: Exception) {
                Timber.e(e, "Google sign-in failed")
                _authState.value = AuthUiState.Error(sanitizeAuthError(e, isGoogleSignIn = true))
            }
        }
    }

    fun resetState() {
        _authState.value = AuthUiState.Idle
    }

    private fun sanitizeAuthError(e: Exception, isGoogleSignIn: Boolean = false): String {
        val genericMessage = if (isGoogleSignIn) "Google Sign-In failed. Please try again." else "Authentication failed. Please try again."
        val message = e.message ?: return genericMessage
        Timber.d("Sanitizing error: $message")
        return when {
            message.contains("invalid_grant", ignoreCase = true) -> "Invalid email or password."
            message.contains("Invalid login credentials", ignoreCase = true) -> "Invalid email or password."
            message.contains("Email rate limit exceeded", ignoreCase = true) -> "Too many attempts. Please wait and try again."
            message.contains("User already registered", ignoreCase = true) -> "An account with this email already exists. Please sign in."
            message.contains("Password should be at least 6 characters", ignoreCase = true) -> PASSWORD_LENGTH_ERROR
            message.contains("Unable to validate email address", ignoreCase = true) -> "Invalid email format."
            e is java.net.UnknownHostException -> "Network error. Please check your connection."
            else -> genericMessage
        }
    }
}