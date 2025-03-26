package com.jamie.nodica.features.groups.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class GroupViewModel(
    private val groupUseCase: GroupUseCase,
    private val currentUserId: String
) : ViewModel() {

    private val _groups = MutableStateFlow<List<Group>>(emptyList())
    val groups: StateFlow<List<Group>> get() = _groups

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> get() = _error

    // State for search queries – used to filter groups locally.
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> get() = _searchQuery

    private val _subjectQuery = MutableStateFlow("")
    val subjectQuery: StateFlow<String> get() = _subjectQuery


    init {
        fetchGroups()
    }

    fun fetchGroups() {
        viewModelScope.launch {
            groupUseCase.getGroups().fold(
                onSuccess = { list ->
                    val search = _searchQuery.value
                    val subject = _subjectQuery.value
                    // Filter groups: by name (ignoring case) and by tags if a subject query is provided.
                    val filtered = list.filter { group ->
                        val matchesName = group.name.contains(search, ignoreCase = true)
                        val matchesTags = subject.isBlank() ||
                                group.tags.any { it.contains(subject, ignoreCase = true) }
                        matchesName && matchesTags
                    }
                    _groups.value = filtered
                },
                onFailure = { _error.value = it.message }
            )
        }
    }

    fun joinGroup(groupId: String) {
        viewModelScope.launch {
            groupUseCase.joinGroup(groupId, currentUserId).fold(
                onSuccess = { fetchGroups() },
                onFailure = { error ->
                    _error.value = error.message
                }
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        fetchGroups() // Consider debouncing for production.
    }

    fun onSubjectQueryChanged(query: String) {
        _subjectQuery.value = query
        fetchGroups() // Consider debouncing as well.
    }
}
