// main/java/com/jamie/nodica/features/splash/SplashViewModel.kt
package com.jamie.nodica.features.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jamie.nodica.features.navigation.Routes
import com.jamie.nodica.features.profile.UserProfile // Ensure this matches the actual profile structure
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.HttpRequestException // Import for potentially checking status code
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
// import io.github.jan.supabase.postgrest.query.Count // Unused, remove import
import io.github.jan.supabase.postgrest.query.Columns // Import Columns
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

// Define states for navigation decision
sealed class SplashDestination {
    object Loading : SplashDestination()
    data class Navigate(val route: String) : SplashDestination()
}

class SplashViewModel(private val supabase: SupabaseClient) : ViewModel() {

    private val _destination = MutableStateFlow<SplashDestination>(SplashDestination.Loading)
    val destination: StateFlow<SplashDestination> = _destination.asStateFlow() // Use asStateFlow for external read-only access

    init {
        Timber.d("SplashViewModel initialized. Checking auth and profile...")
        checkAuthAndProfile()
    }

    private fun checkAuthAndProfile() {
        viewModelScope.launch {
            // Add a small delay for splash screen visibility, adjust as needed.
            delay(1500) // Keep for UX, ensure it's not excessively long

            try {
                // Attempt to retrieve the current session. Use currentSessionOrNull() for a non-blocking check.
                val session = supabase.auth.currentSessionOrNull()
                Timber.d("Current session check complete. Session exists: ${session != null}")

                if (session == null || session.user == null) {
                    // Not logged in -> Navigate to Onboarding
                    _destination.value = SplashDestination.Navigate(Routes.ONBOARDING)
                    Timber.i("Splash: No active session, navigating to Onboarding.")
                } else {
                    // Logged in, check if a profile exists and is considered complete
                    val userId = session.user!!.id // User is non-null here
                    Timber.d("Splash: Session active for user ID: $userId. Checking profile...")

                    // Fetch the profile. Use select with Columns.list
                    val profileResult = supabase.from("users")
                        .select(
                            // Select only specific fields needed for the check using Columns.list
                            // Use actual database column names here (e.g., "preferred_time").
                            columns = Columns.list(
                                "name",
                                // Uncomment other fields if needed for the check
                                // "institution", // DB column name
                                // "preferred_time", // DB column name
                                // "study_goals" // DB column name
                            )
                        ) {
                            filter { eq("id", userId) }
                            limit(1)
                            single() // Throws exception if 0 or >1 rows found
                        }
                        .decodeSingleOrNull<UserProfile>() // Use the correct UserProfile data class

                    // Define profile completeness explicitly based on your app's logic
                    val isProfileComplete = profileResult != null && profileResult.name.isNotBlank() // Current check

                    if (isProfileComplete) {
                        // Logged in and profile seems complete -> Navigate to Home
                        _destination.value = SplashDestination.Navigate(Routes.HOME)
                        Timber.i("Splash: Session active, profile found and complete, navigating to Home.")
                    } else {
                        // Logged in but no profile or incomplete -> Navigate to Profile Setup
                        _destination.value = SplashDestination.Navigate(Routes.PROFILE_SETUP)
                        Timber.i("Splash: Session active, profile missing or incomplete, navigating to Profile Setup.")
                    }
                }
            } catch (e: RestException) {
                // Specific handling for RestException, especially when single() finds 0 rows.
                // The Supabase client often signifies "0 rows for single()" via the message/code.
                // Checking the message or just assuming any RestException here (after login check) means no profile is safer than httpCode.
                if (e.message?.contains("PGRST116") == true || e.message?.contains("JWSError JWSInvalidSignature") == false /* Common for 0 rows */ ) {
                    // PGRST116: "Requested range not satisfiable" often indicates 0 rows for single()
                    _destination.value = SplashDestination.Navigate(Routes.PROFILE_SETUP)
                    Timber.i("Splash: Session active, but profile not found (RestException, likely 0 rows), navigating to Profile Setup.")
                } else {
                    // Log other RestExceptions (permission errors, etc.)
                    Timber.e(e, "Splash: Unhandled RestException while checking auth/profile status: ${e.message}")
                    handleAuthCheckError(e) // Fallback to generic error handling
                }
            } catch (e: HttpRequestException) {
                // Handle network or other HTTP-level errors, check status code if needed
                val statusCode = e.cause?.message // Might contain status code, but parsing is fragile
                Timber.e(e, "Splash: HttpRequestException (Status: $statusCode) checking auth/profile status.")
                handleAuthCheckError(e)
            }
            catch (e: Exception) {
                // Catch-all for other unexpected errors (serialization, etc.)
                Timber.e(e, "Splash: Generic error checking auth/profile status")
                handleAuthCheckError(e)
            }
        }
    }

    // handleAuthCheckError remains the same
    private fun handleAuthCheckError(e: Exception) {
        viewModelScope.launch {
            // Fallback to onboarding on any critical error during check.
            Timber.w("Splash: Navigating to Onboarding due to error: ${e.message}")
            try {
                // Attempt to sign out potentially corrupted session as a safety measure.
                supabase.auth.signOut()
                Timber.i("Splash: Signed out user due to error during auth check.")
            } catch (signOutError: Exception) {
                Timber.e(signOutError, "Splash: Error signing out during error fallback")
            }
            _destination.value = SplashDestination.Navigate(Routes.ONBOARDING)
        }
    }
}