// main/java/com/jamie/nodica/features/groups/group/DiscoverGroupViewModel.kt
package com.jamie.nodica.features.groups.group

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jamie.nodica.features.groups.user_group.UserGroupUseCase // Use UserGroupUseCase
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import timber.log.Timber

// Private state to hold all data needed for processing
private data class DiscoverGroupsInternalState(
    val discoverableGroups: List<Group> = emptyList(),
    val joinedGroupIds: Set<String> = emptySet(),
    val isLoadingDiscover: Boolean = false,
    val isLoadingJoinedIds: Boolean = false, // Separate loading flag
    val joiningGroupId: String? = null, // Track which group join is in progress
    val error: String? = null,
    val searchQuery: String = "",
    val tagQuery: String = ""
)

// Public state exposed to the UI
@Immutable
data class DiscoverGroupUiState(
    // Groups filtered to exclude those the user already joined
    val availableGroups: List<Group> = emptyList(),
    // Combined loading state for initial view
    val isLoading: Boolean = false,
    // Indicates if a 'join' operation is specifically in progress
    val isJoining: Boolean = false,
    // ID of the group currently being joined
    val joiningGroupId: String? = null,
    val error: String? = null,
    val searchQuery: String = "",
    val tagQuery: String = ""
)

@FlowPreview
class DiscoverGroupViewModel(
    private val groupUseCase: GroupUseCase,
    private val userGroupUseCase: UserGroupUseCase, // Use the specific use case
    private val supabaseClient: SupabaseClient
) : ViewModel() {

    private val _internalState = MutableStateFlow(DiscoverGroupsInternalState())

    // Map internal state to the public UI state
    val uiState: StateFlow<DiscoverGroupUiState> = _internalState.map { internal ->
        DiscoverGroupUiState(
            availableGroups = internal.discoverableGroups.filterNot { group -> group.id in internal.joinedGroupIds },
            isLoading = internal.isLoadingDiscover || internal.isLoadingJoinedIds, // Loading if either fetch is active initially
            isJoining = internal.joiningGroupId != null,
            joiningGroupId = internal.joiningGroupId,
            error = internal.error,
            searchQuery = internal.searchQuery,
            tagQuery = internal.tagQuery
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = DiscoverGroupUiState(isLoading = true) // Start loading initially
    )

    // Track jobs to prevent overlaps and allow cancellation
    private var fetchDiscoverJob: Job? = null
    private var fetchJoinedJob: Job? = null
    private var joinGroupJob: Job? = null

    // Helper to get user ID or handle error state
    private fun getCurrentUserIdOrSetError(actionDesc: String = "perform action"): String? {
        val userId = supabaseClient.auth.currentUserOrNull()?.id
        if (userId == null) {
            Timber.e("DiscoverGroupViewModel: User not logged in while trying to $actionDesc.")
            _internalState.update {
                if (it.error != "Authentication error.") { // Prevent state loops
                    it.copy(
                        isLoadingDiscover = false,
                        isLoadingJoinedIds = false,
                        joiningGroupId = null, // Cancel join if auth lost
                        error = "Authentication error. Please log in again."
                    )
                } else it
            }
        }
        return userId
    }

    init {
        Timber.d("DiscoverGroupViewModel initialized.")
        // Fetch initial data only if logged in
        if (getCurrentUserIdOrSetError("initialize ViewModel") != null) {
            // Start both fetches concurrently
            fetchJoinedGroupIds()
            triggerGroupFetch(debounceMillis = 0L) // Initial fetch without debounce
        } else {
            // Ensure loading stops if not logged in
            _internalState.update { it.copy(isLoadingDiscover = false, isLoadingJoinedIds = false)}
        }
    }

    // Fetches IDs of groups the current user has joined
    private fun fetchJoinedGroupIds() {
        val currentUserId = getCurrentUserIdOrSetError("fetch joined groups") ?: return

        fetchJoinedJob?.cancel() // Cancel previous job if any
        _internalState.update { it.copy(isLoadingJoinedIds = true, error = null) } // Set loading specific flag
        Timber.d("Fetching joined group IDs for user $currentUserId")

        fetchJoinedJob = viewModelScope.launch {
            userGroupUseCase.fetchUserGroups(currentUserId).fold(
                onSuccess = { joinedGroups ->
                    if (!isActive) return@fold
                    val ids = joinedGroups.map { it.id }.toSet()
                    Timber.d("Fetched ${ids.size} joined group IDs.")
                    _internalState.update { it.copy(joinedGroupIds = ids, isLoadingJoinedIds = false) }
                },
                onFailure = { error ->
                    if (!isActive) return@fold
                    Timber.e(error, "Error fetching joined group IDs.")
                    _internalState.update {
                        it.copy(
                            isLoadingJoinedIds = false,
                            // Only set error if no other critical error exists
                            error = it.error ?: "Could not check your group memberships: ${error.message}"
                        )
                    }
                }
            )
        }
    }

    // Triggers fetching discoverable groups, applying debounce
    private fun triggerGroupFetch(debounceMillis: Long = 400L) {
        fetchDiscoverJob?.cancel() // Cancel previous fetch job

        // Update loading state immediately *if* debounce is zero (initial load/refresh)
        if (debounceMillis == 0L) {
            _internalState.update { it.copy(isLoadingDiscover = true, error = null)}
        }

        fetchDiscoverJob = viewModelScope.launch {
            delay(debounceMillis) // Apply debounce
            if (!isActive) return@launch // Check if cancelled during delay

            // If we had a debounce, set loading state *now*
            if (debounceMillis > 0L) {
                _internalState.update { it.copy(isLoadingDiscover = true, error = null)}
            }

            Timber.d("Fetching discover groups. Search: '${_internalState.value.searchQuery}', Tag: '${_internalState.value.tagQuery}'")

            groupUseCase.fetchDiscoverGroups(
                searchQuery = _internalState.value.searchQuery,
                tagQuery = _internalState.value.tagQuery
            ).fold( // UseCase returns Result now
                onSuccess = { discoverableGroups ->
                    if (!isActive) return@fold
                    Timber.i("Fetched ${discoverableGroups.size} discoverable groups from repository.")
                    _internalState.update { it.copy(discoverableGroups = discoverableGroups, isLoadingDiscover = false) }
                },
                onFailure = { e ->
                    if (!isActive) return@fold
                    Timber.e(e, "Error fetching discover groups.")
                    _internalState.update { it.copy(isLoadingDiscover = false, error = e.message ?: "Failed to load groups") }
                }
            )
        }
    }

    // Initiates joining a group
    fun joinGroup(groupId: String) {
        val currentUserId = getCurrentUserIdOrSetError("join group") ?: return
        if (groupId.isBlank() || _internalState.value.joiningGroupId != null || groupId in _internalState.value.joinedGroupIds) {
            Timber.w("Join group ($groupId) aborted. Blank ID, already joining, or already joined.")
            return
        }

        joinGroupJob?.cancel() // Cancel any previous join attempt
        _internalState.update { it.copy(joiningGroupId = groupId, error = null) } // Set joining state
        Timber.d("Attempting to join group $groupId for user $currentUserId")

        joinGroupJob = viewModelScope.launch {
            groupUseCase.joinGroup(groupId, currentUserId).fold(
                onSuccess = {
                    if (!isActive) return@fold
                    Timber.i("Successfully joined group $groupId")
                    // Update internal state to reflect joined status
                    _internalState.update {
                        it.copy(
                            joiningGroupId = null, // Clear joining indicator
                            joinedGroupIds = it.joinedGroupIds + groupId // Add to joined set
                        )
                    }
                    // Optionally: trigger a refresh of joined IDs for absolute certainty
                    // fetchJoinedGroupIds()
                },
                onFailure = { error ->
                    if (!isActive) return@fold
                    Timber.e(error, "Failed to join group $groupId")
                    val errorMessage = when (error) {
                        is AlreadyJoinedException -> error.message // Use specific message
                        else -> error.message ?: "Failed to join group"
                    }
                    _internalState.update { it.copy(joiningGroupId = null, error = errorMessage) }
                }
            )
        }
    }

    // Updates search query and triggers fetch
    fun onSearchQueryChanged(query: String) {
        val trimmedQuery = query.trim()
        if (trimmedQuery != _internalState.value.searchQuery) {
            _internalState.update { it.copy(searchQuery = trimmedQuery) }
            triggerGroupFetch() // Fetch with debounce
        }
    }

    // Updates tag query and triggers fetch
    fun onTagQueryChanged(query: String) {
        val trimmedQuery = query.trim()
        if (trimmedQuery != _internalState.value.tagQuery) {
            _internalState.update { it.copy(tagQuery = trimmedQuery) }
            triggerGroupFetch() // Fetch with debounce
        }
    }

    // Clears user-facing errors
    fun clearError() {
        _internalState.update { if (it.error != null) it.copy(error = null) else it }
    }

    // Refreshes all data for the discovery screen
    fun refreshDiscover() {
        Timber.d("Refresh Discover triggered.")
        if (getCurrentUserIdOrSetError("refresh discover") != null) {
            // Force refresh of both joined IDs and discoverable groups
            fetchJoinedGroupIds()
            triggerGroupFetch(debounceMillis = 0L) // Fetch immediately
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Cancel all jobs when ViewModel is cleared
        fetchDiscoverJob?.cancel()
        fetchJoinedJob?.cancel()
        joinGroupJob?.cancel()
        Timber.d("DiscoverGroupViewModel cleared.")
    }
}