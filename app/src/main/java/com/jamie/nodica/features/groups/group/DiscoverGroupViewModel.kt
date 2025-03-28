// Verified: main/java/com/jamie/nodica/features/groups/group/GroupViewModel.kt (DiscoverGroupViewModel class)

package com.jamie.nodica.features.groups.group

// ... imports ...
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jamie.nodica.features.groups.user_group.UserGroupUseCase // Import needed UseCase
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber


class DiscoverGroupViewModel(
    private val groupUseCase: GroupUseCase, // UseCase Interface MUST contain fetchDiscoverGroups
    private val userGroupUseCase: UserGroupUseCase,
    private val currentUserId: String
) : ViewModel() {

    // ... states ...
    private val _discoverGroups = MutableStateFlow<List<Group>>(emptyList())
    private val _joinedGroupIds = MutableStateFlow<Set<String>>(emptySet())
    private val _filteredGroups = MutableStateFlow<List<Group>>(emptyList())
    val filteredGroups: StateFlow<List<Group>> get() = _filteredGroups

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> get() = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> get() = _error

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> get() = _searchQuery

    private val _tagQuery = MutableStateFlow("")
    val tagQuery: StateFlow<String> get() = _tagQuery

    private var fetchJob: Job? = null


    init {
        Timber.d("DiscoverGroupViewModel initialized for user $currentUserId")
        fetchJoinedGroupIds()
        triggerGroupFetch(debounceMillis = 0)
    }

    private fun fetchJoinedGroupIds() {
        viewModelScope.launch { /* ... implementation ... */ }
    }

    @OptIn(FlowPreview::class)
    private fun triggerGroupFetch(debounceMillis: Long = 500L) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            delay(debounceMillis)
            _isLoading.value = true
            _error.value = null
            Timber.d("Fetching discover groups. Search: '${_searchQuery.value}', Tag: '${_tagQuery.value}'")
            try {
                // This line assumes fetchDiscoverGroups was correctly added to GroupUseCase interface and impl
                val result = groupUseCase.fetchDiscoverGroups( // Now calling the use case method
                    searchQuery = _searchQuery.value,
                    tagQuery = _tagQuery.value,
                    currentUserId = currentUserId
                )

                _discoverGroups.value = result
                applyLocalFilter()

            } catch (e: Exception) { /* ... error handling ... */ }
            finally { _isLoading.value = false }
        }
    }

    private fun applyLocalFilter() { /* ... implementation ... */ }

    fun joinGroup(groupId: String) { /* ... implementation ... */ }

    fun onSearchQueryChanged(query: String) { _searchQuery.value = query; triggerGroupFetch() }
    fun onTagQueryChanged(query: String) { _tagQuery.value = query; triggerGroupFetch() } // Updated name
    fun clearError() { _error.value = null }
}