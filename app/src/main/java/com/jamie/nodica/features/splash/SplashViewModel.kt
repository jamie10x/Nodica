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
                // Attempt to retrieve the currently stored session.
                val session = supabase.auth.currentSessionOrNull()
                Timber.i("SplashViewModel: Session found? ${session != null}. User ID: ${session?.user?.id ?: "N/A"}")

                if (session == null || session.user == null) {
                    // User is not logged in.
                    _destination.value = SplashDestination.Navigate(Routes.ONBOARDING)
                    Timber.i("SplashViewModel: No active session, navigating to Onboarding.")

                } else {
                    // User IS logged in. Now check profile status.
                    val userId = session.user!!.id // Safe non-null assertion due to check above

                    // 2. Check Profile Completeness
                    // Explicitly define the columns needed to determine completeness.
                    // CRITICAL: Ensure 'id' is included if your UserProfile data class requires it.
                    val requiredColumns = "id, name, institution, study_goals"
                    Timber.d("SplashViewModel: Fetching profile columns '$requiredColumns' for user: $userId")

                    // Fetch the user's profile data, selecting only the necessary columns.
                    // Use decodeSingleOrNull: Handles the case where the user exists in auth
                    // but might not have a corresponding row in the 'users' table yet (returns null),
                    // or if the query returns exactly one row.
                    // Assumes RLS policies are correctly configured to allow reading these columns.
                    val profile = supabase.postgrest.from("users")
                        .select(columns = Columns.raw(requiredColumns)) { // Explicit columns via raw string
                            filter { eq("id", userId) } // Filter for the logged-in user
                            limit(1)                   // Expect only one row
                            // Ensure NO .single() modifier is used here!
                        }
                        .decodeSingleOrNull<UserProfile>() // Safely decodes to UserProfile?

                    Timber.d("SplashViewModel: Profile fetch result: ${if (profile != null) "Found" else "Not Found or Decode Failed"}")

                    // 3. Determine Navigation Destination
                    if (isProfileComplete(profile)) {
                        // Profile exists and has the required fields filled.
                        _destination.value = SplashDestination.Navigate(Routes.HOME)
                        Timber.i("SplashViewModel: Profile exists and is complete. Navigating to Home.")
                    } else {
                        // Profile doesn't exist (null) or is missing required fields.
                        _destination.value = SplashDestination.Navigate(Routes.PROFILE_SETUP)
                        Timber.i("SplashViewModel: Profile incomplete or not found. Navigating to Profile Setup.")
                    }
                }

                // --- Error Handling ---
            } catch (e: HttpRequestException) {
                // Network-related errors during the request.
                Timber.e(e, "SplashViewModel: Network error during check, navigating to Onboarding.")
                _destination.value = SplashDestination.Navigate(Routes.ONBOARDING)
            } catch (e: RestException) {
                // Errors specifically from the Supabase API (e.g., RLS violation, invalid query).
                Timber.e(e, "SplashViewModel: Database/API error during check, navigating to Onboarding. Error: ${e.message}")
                _destination.value = SplashDestination.Navigate(Routes.ONBOARDING)
            } catch (e: Exception) {
                // Catch any other unexpected errors (e.g., kotlinx.serialization errors if JSON is malformed
                // despite passing initial checks, other runtime exceptions).
                Timber.e(e, "SplashViewModel: Generic/Decoding error during check, navigating to Onboarding.")
                _destination.value = SplashDestination.Navigate(Routes.ONBOARDING) // Safe fallback
            } finally {
                // 4. Ensure Minimum Splash Duration (runs even if errors occurred)
                val elapsed = System.currentTimeMillis() - startTime
                Timber.d("SplashViewModel: Checks took ${elapsed}ms.")
                if (elapsed < 1000L) { // Ensure splash shows for at least 1 second
                    val delayNeeded = 1000L - elapsed
                    Timber.d("SplashViewModel: Delaying for ${delayNeeded}ms...")
                    delay(delayNeeded)
                }
                Timber.d("SplashViewModel: Check process finished.")
            }
        }
    }

    /**
     * Determines if the fetched user profile is considered "complete"
     * based on the application's requirements.
     *
     * A null profile (user row doesn't exist or couldn't be fetched/decoded)
     * is always considered incomplete.
     *
     * @param profile The fetched UserProfile object, or null if not found/error.
     * @return True if the profile exists and required fields are non-blank, False otherwise.
     */
    private fun isProfileComplete(profile: UserProfile?): Boolean {
        if (profile == null) {
            Timber.v("isProfileComplete: Profile is null -> Incomplete.")
            return false // Profile doesn't exist, definitely incomplete.
        }

        // Check if essential fields required after login are filled.
        // Adjust these conditions based on your app's specific definition of a "complete" profile.
        val complete = profile.name.isNotBlank() &&
                !profile.school.isNullOrBlank() && // 'institution' maps to 'school' field
                !profile.studyGoals.isNullOrBlank()

        Timber.v("isProfileComplete: Name='${profile.name}', School='${profile.school}', Goals='${profile.studyGoals}' -> Complete: $complete")
        return complete
    }
}