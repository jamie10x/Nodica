package com.jamie.nodica.features.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jamie.nodica.features.navigation.Routes
import com.jamie.nodica.features.profile.UserProfileWithTags
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
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

class SplashViewModel(private val supabase: SupabaseClient) : ViewModel() {

    private val _destination = MutableStateFlow<SplashDestination>(SplashDestination.Loading)
    val destination: StateFlow<SplashDestination> = _destination.asStateFlow()

    init {
        Timber.d("SplashViewModel initialized.")
        checkAuthAndProfile()
    }

    private fun checkAuthAndProfile() {
        viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            try {
                val session = supabase.auth.currentSessionOrNull()
                if (session == null || session.user == null) {
                    _destination.value = SplashDestination.Navigate(Routes.ONBOARDING)
                    Timber.i("No active session, navigating to Onboarding.")
                } else {
                    val userId = session.user!!.id
                    // Adjust columns based on your actual table structure.
                    val selectColumns = Columns.list(
                        "id", "name", "email", "institution",
                        "preferred_time", "study_goals", "profile_picture_url", "created_at"
                    )
                    val profileData = supabase.postgrest.from("users")
                        .select(columns = Columns.raw("${selectColumns.value}, user_tags(tag_id)")) {
                            filter { eq("id", userId) }
                            single()
                        }
                        .decodeSingle<UserProfileWithTags>()

                    if (isProfileComplete(profileData)) {
                        _destination.value = SplashDestination.Navigate(Routes.HOME)
                        Timber.i("Profile complete. Navigating to Home.")
                    } else {
                        _destination.value = SplashDestination.Navigate(Routes.PROFILE_SETUP)
                        Timber.i("Profile incomplete. Navigating to Profile Setup.")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error during splash checks, navigating to Onboarding.")
                _destination.value = SplashDestination.Navigate(Routes.ONBOARDING)
            }
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed < 1000L) delay(1000L - elapsed)
        }
    }

    /**
     * Determines if the user's profile is complete.
     * Here we require that name, school, and studyGoals are non-empty.
     * Adjust these conditions based on your application's requirements.
     */
    private fun isProfileComplete(profile: UserProfileWithTags): Boolean {
        return profile.name.isNotBlank() &&
                !profile.school.isNullOrBlank() &&
                !profile.studyGoals.isNullOrBlank()
    }
}