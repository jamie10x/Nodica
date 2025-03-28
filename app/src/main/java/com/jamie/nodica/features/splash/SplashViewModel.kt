package com.jamie.nodica.features.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jamie.nodica.features.navigation.Routes
import com.jamie.nodica.features.profile.UserProfile // Make sure UserProfile is imported correctly
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

// Define states for navigation decision
sealed class SplashDestination {
    object Loading : SplashDestination()
    data class Navigate(val route: String) : SplashDestination()
}

class SplashViewModel(private val supabase: SupabaseClient) : ViewModel() {

    private val _destination = MutableStateFlow<SplashDestination>(SplashDestination.Loading)
    val destination: StateFlow<SplashDestination> = _destination

    init {
        checkAuthAndProfile()
    }

    private fun checkAuthAndProfile() {
        viewModelScope.launch {
            // Ensure Supabase client is initialized, delay might not be strictly needed
            // if Koin ensures SupabaseClient is ready, but keep for safety/simulated load.
            delay(1500)

            try {
                // Check current session (more reliable than currentUserOrNull for initial check)
                val session = supabase.auth.currentSessionOrNull()

                if (session == null || session.user == null) {
                    // Not logged in -> Onboarding
                    _destination.value = SplashDestination.Navigate(Routes.ONBOARDING)
                    Timber.d("Splash: No active session, navigating to Onboarding.")
                } else {
                    // Logged in, check for profile
                    val userId = session.user!!.id // User is non-null if session is valid

                    // Check if a profile exists with a non-blank name (adjust logic as needed)
                    val profileResult = supabase.from("users").select {
                        filter { eq("id", userId) }
                        limit(1)
                    }.decodeSingleOrNull<UserProfile>() // Use your UserProfile data class

                    if (profileResult != null && profileResult.name.isNotBlank()) {
                        // Logged in and profile seems complete -> Home
                        _destination.value = SplashDestination.Navigate(Routes.HOME)
                        Timber.d("Splash: Session active, profile found, navigating to Home.")
                    } else {
                        // Logged in but no profile (or incomplete) -> Profile Setup
                        _destination.value = SplashDestination.Navigate(Routes.PROFILE_SETUP)
                        Timber.d("Splash: Session active, profile missing/incomplete, navigating to Profile Setup.")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Splash: Error checking auth/profile status")
                // Fallback to onboarding on any critical error during check
                try {
                    // Attempt to sign out potentially corrupted session
                    supabase.auth.signOut()
                } catch (signOutError: Exception) {
                    Timber.e(signOutError, "Splash: Error signing out during error fallback")
                }
                _destination.value = SplashDestination.Navigate(Routes.ONBOARDING)
            }
        }
    }
}