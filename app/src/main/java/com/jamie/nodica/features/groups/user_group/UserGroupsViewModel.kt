package com.jamie.nodica.features.groups.user_group

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jamie.nodica.features.groups.group.Group
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Job // Import Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@Immutable
data class UserGroupsUiState(
    val groups: List<Group> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

class UserGroupsViewModel(
    private val userGroupUseCase: UserGroupUseCase,
    private val supabaseClient: SupabaseClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserGroupsUiState())
    val uiState: StateFlow<UserGroupsUiState> = _uiState.asStateFlow()

    private var fetchJob: Job? = null // Keep track of the fetch job

    private fun getCurrentUserIdOrSetError(): String? {
        val userId = supabaseClient.auth.currentUserOrNull()?.id
        if (userId == null) {
            Timber.e("UserGroupsViewModel: User not logged in.")
            _uiState.update {
                it.copy(
                    isLoading = false, // Ensure loading stops
                    isRefreshing = false,
                    error = "Authentication error. Please log in again."
                )
            }
        }
        return userId
    }

    init {
        Timber.d("UserGroupsViewModel initialized.")
        if (getCurrentUserIdOrSetError() != null) {
            fetchUserGroups(isRefreshing = false)
        } else {
            _uiState.update { it.copy(isLoading = false) } // Stop loading if not logged in
        }
    }

    private fun fetchUserGroups(isRefreshing: Boolean) {
        val currentUserId = getCurrentUserIdOrSetError() ?: return

        // Cancel previous fetch if still running
        fetchJob?.cancel()

        // Prevent fetch if already loading and not a manual refresh action
        if (_uiState.value.isLoading && !isRefreshing) {
            Timber.d("Skipping fetchUserGroups; already loading.")
            return
        }

        Timber.d("Fetching user groups for $currentUserId... Refresh: $isRefreshing")
        _uiState.update {
            it.copy(
                // Show loading indicator only if it's not a background refresh
                isLoading = !isRefreshing,
                isRefreshing = isRefreshing,
                error = null
            )
        }

        fetchJob = viewModelScope.launch { // Assign job
            try {
                val result = userGroupUseCase.fetchUserGroups(currentUserId)
                result.fold(
                    onSuccess = { groups ->
                        Timber.i("Successfully fetched ${groups.size} user groups.")
                        _uiState.update {
                            it.copy(
                                isLoading = false, // Stop loading/refreshing
                                isRefreshing = false,
                                groups = groups
                            )
                        }
                    },
                    onFailure = { throwable ->
                        Timber.e(throwable, "Error fetching user groups.")
                        _uiState.update {
                            it.copy(
                                isLoading = false, // Stop loading/refreshing on failure
                                isRefreshing = false,
                                error = throwable.message ?: "Failed to load your groups"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                // Catch unexpected errors during the use case call itself
                Timber.e(e, "Exception during fetchUserGroups coroutine execution.")
                _uiState.update {
                    it.copy(
                        isLoading = false, // Ensure loading stops
                        isRefreshing = false,
                        error = e.message ?: "An unexpected error occurred"
                    )
                }
            }
            // 'finally' block is generally not needed here as fold handles both paths.
        }
    }

    fun refresh() {
        if (!_uiState.value.isRefreshing) {
            if (getCurrentUserIdOrSetError() != null) {
                fetchUserGroups(isRefreshing = true)
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() { // Cancel job when VM is cleared
        super.onCleared()
        fetchJob?.cancel()
    }
}