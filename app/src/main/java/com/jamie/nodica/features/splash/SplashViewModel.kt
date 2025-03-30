package com.jamie.nodica.features.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jamie.nodica.features.navigation.Routes
import com.jamie.nodica.features.profile.UserProfile // Use the simpler UserProfile DTO
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException // Keep for specific error handling
import io.github.jan.supabase.postgrest.from // Ensure explicit import
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlinx.serialization.SerializationException // Import for specific catch

sealed class SplashDestination {
    object Loading : SplashDestination()
    data class Navigate(val route: String) : SplashDestination()
}

/**
 * ViewModel for the Splash Screen.
 * Determines the initial navigation destination based on authentication status
 * and profile completeness.
 */
class SplashViewModel(private val supabase: SupabaseClient) : ViewModel() {

    // Private mutable state for internal updates
    private val _destination = MutableStateFlow<SplashDestination>(SplashDestination.Loading)
    // Publicly exposed immutable state flow for the UI to observe
    val destination: StateFlow<SplashDestination> = _destination.asStateFlow()

    init {
        Timber.d("SplashViewModel initialized.")
        checkAuthAndProfile() // Start the check process immediately
    }

    /**
     * Checks the current authentication session and user profile completeness
     * to decide the next navigation route.
     */
    private fun checkAuthAndProfile() {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            Timber.d("SplashViewModel: Starting auth and profile check...")

            try {
                // 1. Check Authentication Session
                val session = supabase.auth.currentSessionOrNull()
                Timber.i("SplashViewModel: Session found? ${session != null}. User ID: ${session?.user?.id ?: "N/A"}")

                if (session == null || session.user == null) {
                    // User is not logged in.
                    _destination.value = SplashDestination.Navigate(Routes.ONBOARDING)
                    Timber.i("SplashViewModel: No active session, navigating to Onboarding.")

                } else {
                    // User IS logged in. Now check profile status.
                    val userId = session.user!!.id // Safe non-null assertion

                    // 2. Check Profile Completeness
                    val requiredColumns = "id, name, institution, study_goals"
                    Timber.d("SplashViewModel: Fetching profile columns '$requiredColumns' for user: $userId")

                    val profile = supabase.postgrest.from("users")
                        .select(columns = Columns.raw(requiredColumns)) {
                            filter { eq("id", userId) }
                            limit(1)
                        }
                        .decodeSingleOrNull<UserProfile>() // Handles '[]' -> null

                    Timber.d("SplashViewModel: Profile fetch result: ${if (profile != null) "Found" else "Not Found or Decode Failed"}")

                    // 3. Determine Navigation Destination
                    if (isProfileComplete(profile)) {
                        // Profile exists AND required fields are filled.
                        _destination.value = SplashDestination.Navigate(Routes.HOME)
                        Timber.i("SplashViewModel: Profile exists and is complete. Navigating to Home.")
                    } else {
                        // Profile doesn't exist (null) OR exists but fields are blank.
                        _destination.value = SplashDestination.Navigate(Routes.PROFILE_SETUP)
                        Timber.i("SplashViewModel: Profile incomplete or not found. Navigating to Profile Setup.")
                    }
                }

                // --- Error Handling ---
            } catch (e: HttpRequestException) {
                Timber.e(e, "SplashViewModel: Network error during check, navigating to Onboarding.")
                _destination.value = SplashDestination.Navigate(Routes.ONBOARDING)
            } catch (e: RestException) {
                Timber.e(e, "SplashViewModel: Database/API error during check, navigating to Onboarding. Error: ${e.message}")
                _destination.value = SplashDestination.Navigate(Routes.ONBOARDING)
            } catch (e: SerializationException) {
                Timber.e(e, "SplashViewModel: Error parsing profile data during check, navigating to Onboarding.")
                _destination.value = SplashDestination.Navigate(Routes.ONBOARDING)
            } catch (e: Exception) {
                Timber.e(e, "SplashViewModel: Generic error during check, navigating to Onboarding.")
                _destination.value = SplashDestination.Navigate(Routes.ONBOARDING) // Safe fallback
            } finally {
                // 4. Ensure Minimum Splash Duration
                val elapsed = System.currentTimeMillis() - startTime
                Timber.d("SplashViewModel: Checks took ${elapsed}ms.")
                if (elapsed < 1000L) {
                    val delayNeeded = 1000L - elapsed
                    Timber.d("SplashViewModel: Delaying for ${delayNeeded}ms...")
                    delay(delayNeeded)
                }
                Timber.d("SplashViewModel: Check process finished.")
            }
        }
    }

    /**
     * Determines if the fetched user profile is considered "complete".
     * A null profile is always considered incomplete.
     */
    private fun isProfileComplete(profile: UserProfile?): Boolean {
        if (profile == null) {
            Timber.v("isProfileComplete: Profile is null -> Incomplete.")
            return false
        }
        val complete = profile.name.isNotBlank() &&
                !profile.school.isNullOrBlank() &&
                !profile.studyGoals.isNullOrBlank()
        Timber.v("isProfileComplete Check: Name='${profile.name}', School='${profile.school}', Goals='${profile.studyGoals}' -> Complete: $complete")
        return complete
    }
}