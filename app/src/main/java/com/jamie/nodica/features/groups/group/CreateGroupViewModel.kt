package com.jamie.nodica.features.groups.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CreateGroupViewModel(
    private val groupUseCase: GroupUseCase,
    private val currentUserId: String
) : ViewModel() {

    private val _creationState = MutableStateFlow<CreateGroupUiState>(CreateGroupUiState.Idle)
    val creationState: StateFlow<CreateGroupUiState> get() = _creationState

    fun createGroup(
        name: String,
        tags: List<String>,
        description: String,
        meetingSchedule: String
    ) {
        _creationState.value = CreateGroupUiState.Loading
        viewModelScope.launch {
            // Build the new group object.
            val newGroup = Group(
                id = "", // Let Supabase generate the id
                name = name,
                tags = tags,
                description = description,
                meetingSchedule = meetingSchedule,
                creatorId = currentUserId,
                members = 1 // Creator is the first member
            )
            groupUseCase.createGroup(newGroup).fold(
                onSuccess = { createdGroup ->
                    _creationState.value = CreateGroupUiState.Success(createdGroup)
                },
                onFailure = { error ->
                    _creationState.value = CreateGroupUiState.Error(error.message ?: "Error creating group")
                }
            )
        }
    }

    fun resetState() {
        _creationState.value = CreateGroupUiState.Idle
    }
}

sealed class CreateGroupUiState {
    object Idle : CreateGroupUiState()
    object Loading : CreateGroupUiState()
    data class Success(val group: Group) : CreateGroupUiState()
    data class Error(val message: String) : CreateGroupUiState()
}