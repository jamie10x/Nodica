package com.jamie.nodica.features.groups.user_group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jamie.nodica.features.groups.group.Group
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class UserGroupsViewModel(
    private val useCase: UserGroupUseCase,
    private val currentUserId: String
) : ViewModel() {

    private val _userGroups = MutableStateFlow<List<Group>>(emptyList())
    val userGroups: StateFlow<List<Group>> get() = _userGroups

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> get() = _error

    private val _joinedGroupIds = MutableStateFlow<List<String>>(emptyList())
    val joinedGroupIds: StateFlow<List<String>> get() = _joinedGroupIds

    init {
        fetchUserGroups()
    }

    fun fetchUserGroups() {
        viewModelScope.launch {
            useCase.fetchUserGroups(currentUserId).fold(
                onSuccess = { groups ->
                    _userGroups.value = groups
                    _joinedGroupIds.value = groups.map { it.id }
                },
                onFailure = { error ->
                    _error.value = error.message
                }
            )
        }
    }


    fun refresh() {
        fetchUserGroups()
    }
}
