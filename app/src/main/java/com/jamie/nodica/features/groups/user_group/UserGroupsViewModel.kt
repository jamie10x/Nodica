package com.jamie.nodica.features.groups.user_group

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jamie.nodica.features.groups.group.Group // Correct import
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive // Import isActive
import timber.log.Timber

@Immutable // Add Immutable for the state
data class UserGroupsUiState(
    val groups: List<Group> = emptyList(),
    val isLoading: Boolean = true,       // True for initial load attempt
    val isRefreshing: Boolean = false,    // True only for pull-to-refresh action
    val error: String? = null
)

class UserGroupsViewModel(
    private val userGroupUseCase: UserGroupUseCase,
    private val supabaseClient: SupabaseClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserGroupsUiState())
    val uiState: StateFlow<UserGroupsUiState> = _uiState.asStateFlow()

    private var fetchJob: Job? = null // Track the current fetch operation

    // Helper to get user ID and set error state if unavailable
    private fun getCurrentUserIdOrSetError(actionDesc: String = "perform action"): String? {
        val userId = supabaseClient.auth.currentUserOrNull()?.id
        if (userId == null) {
            Timber.e("UserGroupsViewModel: User not logged in while trying to $actionDesc.")
            _uiState.update {
                // Ensure we don't trigger update loops if already in this error state
                if (it.error != "Authentication error.") {
                    it.copy(
                        isLoading = false, // Stop loading
                        isRefreshing = false,
                        error = "Authentication error. Please log in again."
                    )
                } else {
                    it // No change needed
                }
            }
        }
        return userId
    }

    init {
        Timber.d("UserGroupsViewModel initialized.")
        // Initial fetch only if user is logged in
        if (getCurrentUserIdOrSetError("initialize ViewModel") != null) {
            fetchUserGroups(isInitialLoad = true)
        } else {
            _uiState.update { it.copy(isLoading = false) } // Set loading false if not logged in initially
        }
    }

    private fun fetchUserGroups(isInitialLoad: Boolean = false, isRefresh: Boolean = false) {
        // 1. Get User ID or exit
        val currentUserId = getCurrentUserIdOrSetError(if(isRefresh) "refresh groups" else "fetch groups") ?: return

        // 2. Prevent duplicate non-refresh fetches
        if (_uiState.value.isLoading && !isRefresh && !isInitialLoad) {
            Timber.d("fetchUserGroups skipped: Already loading and not a refresh/initial.")
            return
        }
        // Cancel previous job if starting a new one (especially for refresh or overlapping calls)
        fetchJob?.cancel()

        // 3. Update Loading State *Before* Launching Coroutine
        _uiState.update {
            it.copy(
                isLoading = isInitialLoad, // True only on the very first load attempt
                isRefreshing = isRefresh,  // True only when pull-to-refresh is triggered
                error = null               // Clear previous errors on new attempt
            )
        }
        Timber.d("Fetching user groups for $currentUserId. InitialLoad: $isInitialLoad, Refresh: $isRefresh")

        // 4. Launch Coroutine for Fetching
        fetchJob = viewModelScope.launch {
            userGroupUseCase.fetchUserGroups(currentUserId).fold( // UseCase returns Result now
                onSuccess = { groups ->
                    if (!isActive) return@fold // Check if coroutine was cancelled
                    Timber.i("Successfully fetched ${groups.size} user groups for $currentUserId.")
                    _uiState.update {
                        it.copy(
                            isLoading = false,    // Stop initial loading indicator
                            isRefreshing = false, // Stop refreshing indicator
                            groups = groups       // Update data
                        )
                    }
                },
                onFailure = { throwable ->
                    if (!isActive) return@fold
                    Timber.e(throwable, "Error fetching user groups for $currentUserId.")
                    _uiState.update {
                        it.copy(
                            isLoading = false,    // Stop initial loading indicator on error
                            isRefreshing = false, // Stop refreshing indicator on error
                            error = throwable.message ?: "Failed to load your groups"
                        )
                    }
                }
            )
            // No need for outer try-catch if UseCase handles it, but can be added for extra safety
        }
    }

    // Public function called by the UI for pull-to-refresh
    fun refresh() {
        Timber.d("Refresh triggered.")
        fetchUserGroups(isInitialLoad = false, isRefresh = true) // Explicitly a refresh action
    }

    // Public function to clear user-visible errors (e.g., after Snackbar dismissal)
    fun clearError() {
        _uiState.update {
            if (it.error != null) { it.copy(error = null) } else { it }
        }
    }

    override fun onCleared() {
        super.onCleared()
        fetchJob?.cancel() // Ensure the fetch job is cancelled if the ViewModel is cleared
        Timber.d("UserGroupsViewModel cleared.")
    }
}