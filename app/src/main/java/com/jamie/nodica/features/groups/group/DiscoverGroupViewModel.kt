package com.jamie.nodica.features.groups.group

import androidx.compose.runtime.Immutable // Import Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jamie.nodica.features.groups.user_group.UserGroupUseCase
import io.github.jan.supabase.SupabaseClient // Import client
import io.github.jan.supabase.auth.auth // Import auth extension
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlinx.coroutines.isActive


@OptIn(FlowPreview::class)
class DiscoverGroupViewModel(
    private val groupUseCase: GroupUseCase,
    private val userGroupUseCase: UserGroupUseCase,
    private val supabaseClient: SupabaseClient // Inject client
    // Removed currentUserId from constructor
) : ViewModel() {

    // Internal mutable state
    private data class DiscoverGroupsInternalState(
        val discoverableGroups: List<Group> = emptyList(),
        val joinedGroupIds: Set<String> = emptySet(),
        val isLoading: Boolean = false,
        val joiningGroupId: String? = null,
        val error: String? = null,
        val searchQuery: String = "",
        val tagQuery: String = ""
    )

    // Public immutable state exposed to the UI
    @Immutable // Add Immutable annotation
    data class DiscoverGroupUiState(
        val filteredGroups: List<Group> = emptyList(),
        val isLoading: Boolean = false,
        val isJoining: Boolean = false,
        val joiningGroupId: String? = null,
        val error: String? = null,
        val searchQuery: String = "",
        val tagQuery: String = ""
    )

    private val _internalState = MutableStateFlow(DiscoverGroupsInternalState())

    val uiState: StateFlow<DiscoverGroupUiState> = _internalState.map { internal ->
        DiscoverGroupUiState(
            filteredGroups = internal.discoverableGroups.filterNot { it.id in internal.joinedGroupIds },
            isLoading = internal.isLoading,
            isJoining = internal.joiningGroupId != null,
            joiningGroupId = internal.joiningGroupId,
            error = internal.error,
            searchQuery = internal.searchQuery,
            tagQuery = internal.tagQuery
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DiscoverGroupUiState()
    )

    private var fetchDiscoverJob: Job? = null
    private var fetchJoinedJob: Job? = null

    // Function to safely get user ID or update state with error
    private fun getCurrentUserIdOrSetError(actionDescription: String): String? {
        val userId = supabaseClient.auth.currentUserOrNull()?.id
        if (userId == null) {
            Timber.e("DiscoverGroupViewModel: User not logged in while trying to $actionDescription.")
            _internalState.update { it.copy(
                isLoading = false, // Ensure loading stops
                joiningGroupId = null,
                error = "Authentication error. Please log in again."
            ) }
        }
        return userId
    }

    init {
        Timber.d("DiscoverGroupViewModel initialized.")
        // Only proceed if logged in
        if (getCurrentUserIdOrSetError("initialize ViewModel") != null) {
            fetchJoinedGroupIds()
            triggerGroupFetch(debounceMillis = 0)
        }
    }

    private fun fetchJoinedGroupIds() {
        val currentUserId = getCurrentUserIdOrSetError("fetch joined groups") ?: return

        if (fetchJoinedJob?.isActive == true) return
        fetchJoinedJob = viewModelScope.launch {
            Timber.d("Fetching joined group IDs for user $currentUserId")
            // Don't necessarily set global loading=true here, could conflict with discover loading
            // _internalState.update { it.copy(isLoading = true) }
            userGroupUseCase.fetchUserGroups(currentUserId).fold(
                onSuccess = { joinedGroups ->
                    val ids = joinedGroups.map { it.id }.toSet()
                    Timber.d("Fetched ${ids.size} joined group IDs.")
                    _internalState.update { it.copy(joinedGroupIds = ids /*, isLoading = false */) }
                },
                onFailure = { error ->
                    Timber.e(error, "Error fetching joined group IDs.")
                    // Don't overwrite discovery errors if they exist
                    _internalState.update { current ->
                        if (current.error == null) {
                            current.copy(error = "Could not check your group memberships.")
                        } else current
                    }
                }
            )
        }
    }

    private fun triggerGroupFetch(debounceMillis: Long = 400L) {
        // No need to check userId here, as filtering happens locally based on joinedGroupIds
        // The actual fetch can proceed even if userId is briefly null during init races

        fetchDiscoverJob?.cancel()
        fetchDiscoverJob = viewModelScope.launch {
            delay(debounceMillis)
            if (!isActive) return@launch

            _internalState.update { it.copy(isLoading = true, error = null) }
            Timber.d("Fetching discover groups. Search: '${_internalState.value.searchQuery}', Tag: '${_internalState.value.tagQuery}'")

            try {
                // Pass user ID to use case
                val currentUserId = getCurrentUserIdOrSetError("fetch discover groups") ?: "" // Pass "" if null? Or handle in use case? Better to fetch regardless

                val result = groupUseCase.fetchDiscoverGroups(
                    searchQuery = _internalState.value.searchQuery,
                    tagQuery = _internalState.value.tagQuery,
                    currentUserId = currentUserId // Pass ID here
                )
                Timber.i("Fetched ${result.size} discoverable groups from repository.")
                _internalState.update { it.copy(discoverableGroups = result, isLoading = false) }

            } catch (e: Exception) {
                Timber.e(e, "Error fetching discover groups.")
                _internalState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load groups") }
            }
        }
    }

    fun joinGroup(groupId: String) {
        val currentUserId = getCurrentUserIdOrSetError("join group") ?: return

        if (groupId.isBlank()) return
        if (_internalState.value.joiningGroupId != null || groupId in _internalState.value.joinedGroupIds) return

        _internalState.update { it.copy(joiningGroupId = groupId, error = null) }
        viewModelScope.launch {
            groupUseCase.joinGroup(groupId, currentUserId).fold(
                onSuccess = {
                    Timber.i("Successfully joined group $groupId")
                    _internalState.update { it.copy(
                        joiningGroupId = null,
                        joinedGroupIds = it.joinedGroupIds + groupId
                    ) }
                    // Show success feedback via Snackbar in UI (triggered by state change potentially)
                    // or set a temporary success message in state if needed
                    // _internalState.update { it.copy(error = "Joined successfully!") } // Temporary feedback via error channel
                },
                onFailure = { error ->
                    Timber.e(error, "Failed to join group $groupId")
                    val errorMessage = when (error) {
                        is AlreadyJoinedException -> error.message // Show specific message
                        else -> error.message ?: "Failed to join group"
                    }
                    _internalState.update { it.copy(joiningGroupId = null, error = errorMessage) }
                }
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        if (query != _internalState.value.searchQuery) {
            _internalState.update { it.copy(searchQuery = query) }
            triggerGroupFetch()
        }
    }

    fun onTagQueryChanged(query: String) {
        if (query != _internalState.value.tagQuery) {
            _internalState.update { it.copy(tagQuery = query) }
            triggerGroupFetch()
        }
    }

    fun clearError() {
        _internalState.update { it.copy(error = null) }
    }

    fun refreshDiscover() {
        // Re-fetch joined IDs and discoverable groups
        if (getCurrentUserIdOrSetError("refresh discover") != null) {
            fetchJoinedGroupIds() // Update knowledge of joined groups
            triggerGroupFetch(debounceMillis = 0) // Force immediate fetch
        }
    }
}