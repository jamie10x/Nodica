// Corrected: main/java/com/jamie/nodica/features/groups/user_group/UserGroupsViewModel.kt
package com.jamie.nodica.features.groups.user_group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jamie.nodica.features.groups.group.Group
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class UserGroupsViewModel(
    private val userGroupUseCase: UserGroupUseCase, // Use the UseCase
    private val currentUserId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserGroupsUiState())
    val uiState: StateFlow<UserGroupsUiState> = _uiState.asStateFlow()

    // REMOVED: These derived properties are no longer needed as UI observes uiState directly
    // val userGroups: StateFlow<List<Group>> = _uiState.mapState { it.groups }
    // val isLoading: StateFlow<Boolean> = _uiState.mapState { it.isLoading }
    // val error: StateFlow<String?> = _uiState.mapState { it.error }

    // Keep joinedGroupIds internal or remove if not needed elsewhere
    private val _joinedGroupIds = MutableStateFlow<Set<String>>(emptySet())
    // REMOVED: Public accessor if not used elsewhere
    // val joinedGroupIds: StateFlow<Set<String>> = _joinedGroupIds.asStateFlow()

    init {
        Timber.d("UserGroupsViewModel initialized for user $currentUserId")
        fetchUserGroups()
    }

    fun fetchUserGroups(isRefreshing: Boolean = false) {
        if (_uiState.value.isLoading && !isRefreshing) return

        Timber.d("Fetching user groups... Refresh: $isRefreshing")
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            try {
                val result = userGroupUseCase.fetchUserGroups(currentUserId)
                result.fold(
                    onSuccess = { groups ->
                        Timber.i("Successfully fetched ${groups.size} user groups.")
                        val sortedGroups = groups.sortedBy { it.name }
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            groups = sortedGroups,
                            isRefreshing = false
                        )
                        _joinedGroupIds.value = sortedGroups.map { it.id }.toSet() // Update internal state if needed
                    },
                    onFailure = { throwable ->
                        Timber.e(throwable, "Error fetching user groups.")
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = throwable.message ?: "Failed to load your groups",
                            isRefreshing = false
                        )
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Exception in fetchUserGroups.")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "An unexpected error occurred",
                    isRefreshing = false
                )
            }
        }
    }

    fun refresh() {
        if (!_uiState.value.isRefreshing) {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            fetchUserGroups(isRefreshing = true)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    // REMOVED: mapState helper functions are no longer needed as UI consumes uiState directly
    /*
    private fun <T> StateFlow<UserGroupsUiState>.mapState(mapper: (UserGroupsUiState) -> T): StateFlow<T> {
        return mapState(viewModelScope, mapper)
    }
    // FIX: Correct the initialValue parameter usage in stateIn
    private fun <T> Flow<UserGroupsUiState>.mapState(scope: CoroutineScope, mapper: (UserGroupsUiState) -> T): StateFlow<T> {
        return map(mapper).stateIn(
            scope = scope,
            // Correctly provide the initial value by applying the mapper to the current value of the source flow
            started = SharingStarted.Eagerly,
            initialValue = mapper(this@UserGroupsViewModel._uiState.value) // Apply mapper to current value
        )
    }
    */
}

// State holder data class (remains the same)
data class UserGroupsUiState(
    val groups: List<Group> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null
)