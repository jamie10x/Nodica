package com.jamie.nodica.features.groups.group

import androidx.compose.runtime.Immutable // Import Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jamie.nodica.features.profile.TagItem
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth // Import auth extension
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

// --- State Definitions (Moved to Top-Level in this file) ---

@Immutable // Add Immutable annotation HERE
data class CreateGroupScreenState(
    val availableTags: Map<String, List<TagItem>> = emptyMap(),
    val isLoadingTags: Boolean = true,
    val opState: CreateGroupOpState = CreateGroupOpState.Idle,
    val error: String? = null
)

sealed class CreateGroupOpState {
    object Idle : CreateGroupOpState()
    object Loading : CreateGroupOpState()
    data class ValidationError(val message: String) : CreateGroupOpState()
    data class Success(val group: Group) : CreateGroupOpState()
    data class Error(val message: String) : CreateGroupOpState()
}

// --- ViewModel Definition ---

class CreateGroupViewModel(
    private val groupUseCase: GroupUseCase,
    private val supabaseClient: SupabaseClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateGroupScreenState())
    val uiState: StateFlow<CreateGroupScreenState> = _uiState.asStateFlow()

    // Function to safely get user ID or update state with error
    private fun getCurrentUserIdOrSetError(): String? {
        val userId = supabaseClient.auth.currentUserOrNull()?.id
        if (userId == null) {
            Timber.e("CreateGroup: User not logged in.")
            // Update state correctly if user is null
            _uiState.update { prevState ->
                // Avoid infinite loops if already in error state
                if (prevState.opState !is CreateGroupOpState.Error || prevState.error != "Authentication error.") {
                    prevState.copy(
                        opState = CreateGroupOpState.Error("You must be logged in to create a group."),
                        error = "Authentication error.",
                        isLoadingTags = false // Ensure loading stops
                    )
                } else {
                    prevState
                }
            }
        }
        return userId
    }


    init {
        Timber.d("CreateGroupViewModel initialized.")
        // Check login status before fetching tags
        if (getCurrentUserIdOrSetError() != null) {
            fetchAvailableTags()
        } else {
            // Ensure loading stops if user wasn't logged in on init
            _uiState.update { it.copy(isLoadingTags = false) }
        }
    }

    private fun fetchAvailableTags() {
        _uiState.update { it.copy(isLoadingTags = true, error = null) }
        viewModelScope.launch {
            try {
                val tags = supabaseClient.from("tags").select().decodeList<TagItem>()
                val tagsByCategory = tags
                    .groupBy { it.category.uppercase() }
                    .toSortedMap()
                    .mapValues { entry -> entry.value.sortedBy { it.name } }

                _uiState.update { it.copy(availableTags = tagsByCategory, isLoadingTags = false) }
                Timber.i("Fetched ${tags.size} tags for Create Group screen.")
            } catch (e: Exception) {
                Timber.e(e, "Error fetching available tags for Create Group")
                _uiState.update { it.copy(
                    isLoadingTags = false,
                    error = "Failed to load available subjects: ${e.message}"
                ) }
            }
        }
    }

    fun createGroup(
        name: String,
        description: String,
        meetingSchedule: String,
        selectedTagIds: List<String>
    ) {
        val currentUserId = getCurrentUserIdOrSetError() ?: return // Exit if user ID is null

        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            _uiState.update { it.copy(opState = CreateGroupOpState.ValidationError("Group name cannot be empty.")) }
            return
        }
        if (selectedTagIds.isEmpty()) {
            _uiState.update { it.copy(opState = CreateGroupOpState.ValidationError("Please select at least one tag.")) }
            return
        }

        _uiState.update { it.copy(opState = CreateGroupOpState.Loading, error = null) }
        Timber.d("Attempting to create group. Name: $trimmedName, Tags: $selectedTagIds")

        viewModelScope.launch {
            val newGroupData = Group(
                id = "", name = trimmedName, description = description.trim(),
                meetingSchedule = meetingSchedule.trim().ifBlank { null },
                creatorId = currentUserId, // Use fetched ID
                tags = emptyList(), membersRelation = emptyList()
            )

            groupUseCase.createGroup(newGroupData, selectedTagIds.distinct()).fold(
                onSuccess = { createdGroup ->
                    Timber.i("Group created successfully: ${createdGroup.id}")
                    _uiState.update { it.copy(opState = CreateGroupOpState.Success(createdGroup)) }
                },
                onFailure = { error ->
                    Timber.e(error, "Failed to create group")
                    _uiState.update { it.copy(opState = CreateGroupOpState.Error(error.message ?: "Error creating group")) }
                }
            )
        }
    }

    fun resetOperationState() {
        if (_uiState.value.opState != CreateGroupOpState.Idle) {
            _uiState.update { it.copy(opState = CreateGroupOpState.Idle) }
        }
    }

    fun clearError() {
        if (_uiState.value.error != null) {
            _uiState.update { it.copy(error = null) }
        }
        // Reset validation state as well when clearing general error
        if (_uiState.value.opState is CreateGroupOpState.ValidationError) {
            resetOperationState()
        }
    }
}