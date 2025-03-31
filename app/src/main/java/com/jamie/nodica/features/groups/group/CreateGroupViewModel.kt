// main/java/com/jamie/nodica/features/groups/group/CreateGroupViewModel.kt
package com.jamie.nodica.features.groups.group

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jamie.nodica.features.profile.TagItem // Reuse TagItem model
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
// Removed direct postgrest import, should use repository/usecase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import timber.log.Timber

// --- State Definitions ---
@Immutable
data class CreateGroupScreenState(
    val availableTags: Map<String, List<TagItem>> = emptyMap(),
    val isLoadingTags: Boolean = true,
    val opState: CreateGroupOpState = CreateGroupOpState.Idle,
    val error: String? = null // General errors (e.g., tag loading failure)
)

sealed class CreateGroupOpState {
    object Idle : CreateGroupOpState()
    object Loading : CreateGroupOpState() // Specifically for the create operation
    data class ValidationError(val message: String) : CreateGroupOpState()
    data class Success(val group: Group) : CreateGroupOpState() // Group created successfully
    data class Error(val message: String) : CreateGroupOpState() // Error during create operation
}

// --- ViewModel Definition ---
class CreateGroupViewModel(
    private val groupUseCase: GroupUseCase,
    private val groupRepository: GroupRepository, // Inject repository for tag fetching
    private val supabaseClient: SupabaseClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateGroupScreenState())
    val uiState: StateFlow<CreateGroupScreenState> = _uiState.asStateFlow()

    private var fetchTagsJob: Job? = null
    private var createGroupJob: Job? = null

    // Helper to get user ID and handle error state
    private fun getCurrentUserIdOrSetError(actionDesc: String = "perform action"): String? {
        val userId = supabaseClient.auth.currentUserOrNull()?.id
        if (userId == null) {
            Timber.e("CreateGroupViewModel: User not logged in while trying to $actionDesc.")
            _uiState.update {
                if (it.error != "Authentication error." && it.opState !is CreateGroupOpState.Error) {
                    it.copy(
                        isLoadingTags = false, // Stop tag loading
                        opState = CreateGroupOpState.Error("Authentication error."),
                        error = "You must be logged in." // General error for UI display
                    )
                } else it
            }
        } else if (_uiState.value.error == "Authentication error.") {
            // Clear auth error if user is now logged in
            _uiState.update { it.copy(error = null)}
        }
        return userId
    }

    init {
        Timber.d("CreateGroupViewModel initialized.")
        if (getCurrentUserIdOrSetError("initialize ViewModel") != null) {
            fetchAvailableTags()
        } else {
            // Ensure loading stops if not logged in
            _uiState.update { it.copy(isLoadingTags = false) }
        }
    }

    // Fetch available tags from the repository
    private fun fetchAvailableTags() {
        fetchTagsJob?.cancel()
        _uiState.update { it.copy(isLoadingTags = true, error = null) } // Show tag loading
        Timber.d("Fetching available tags for group creation.")

        fetchTagsJob = viewModelScope.launch {
            try {
                val tags = groupRepository.fetchAllTags() // Use repository
                val tagsByCategory = tags
                    .groupBy { it.category.uppercase() }
                    .toSortedMap()
                    .mapValues { entry -> entry.value.sortedBy { it.name } }

                if (!isActive) return@launch
                _uiState.update { it.copy(availableTags = tagsByCategory, isLoadingTags = false) }
                Timber.i("Fetched ${tags.size} tags for Create Group screen.")

            } catch (e: Exception) {
                if (!isActive) return@launch
                Timber.e(e, "Error fetching available tags for Create Group")
                _uiState.update {
                    it.copy(
                        isLoadingTags = false,
                        error = "Failed to load subjects: ${e.message}" // Set general error
                    )
                }
            }
        }
    }

    // Create the group using the UseCase
    fun createGroup(
        name: String,
        description: String,
        meetingSchedule: String,
        selectedTagIds: List<String>
    ) {
        val currentUserId = getCurrentUserIdOrSetError("create group") ?: return
        createGroupJob?.cancel() // Cancel previous attempt if any

        // --- Basic Client-Side Validation ---
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            _uiState.update { it.copy(opState = CreateGroupOpState.ValidationError("Group name cannot be empty.")) }
            return
        }
        if (selectedTagIds.isEmpty()) {
            _uiState.update { it.copy(opState = CreateGroupOpState.ValidationError("Please select at least one tag.")) }
            return
        }
        // --- End Validation ---

        // Update state to show creation is in progress
        _uiState.update { it.copy(opState = CreateGroupOpState.Loading, error = null) }
        // Log the user ID being used for creation
        Timber.d("Attempting to create group. Name: $trimmedName, Tags: $selectedTagIds, Creator: $currentUserId")

        createGroupJob = viewModelScope.launch {
            // Prepare the Group object stub for the use case
            val newGroupData = Group(
                id = "", // Ignored by UseCase/Repo create
                name = trimmedName,
                description = description.trim(),
                meetingSchedule = meetingSchedule.trim().ifBlank { null },
                creatorId = currentUserId, // Ensure this is correctly passed
                createdAt = kotlinx.datetime.Clock.System.now(), // Placeholder
                tags = emptyList(), // Ignored
                membersRelation = emptyList() // Ignored
            )

            // Call the use case
            groupUseCase.createGroup(newGroupData, selectedTagIds.distinct()).fold(
                onSuccess = { createdGroup ->
                    if (!isActive) return@fold
                    // Log the ID of the successfully created group
                    Timber.i("Group created successfully via UseCase: ID=${createdGroup.id}, Name='${createdGroup.name}'")
                    _uiState.update { it.copy(opState = CreateGroupOpState.Success(createdGroup)) }
                },
                onFailure = { error ->
                    if (!isActive) return@fold
                    Timber.e(error, "Failed to create group via UseCase")
                    // Set specific error state related to the operation
                    _uiState.update { it.copy(opState = CreateGroupOpState.Error(error.message ?: "Error creating group")) }
                }
            )
        }
    }

    // Resets the operation state (Loading, Success, Error, ValidationError) back to Idle
    fun resetOperationState() {
        if (_uiState.value.opState != CreateGroupOpState.Idle) {
            _uiState.update { it.copy(opState = CreateGroupOpState.Idle) }
            Timber.v("Resetting create group operation state to Idle.")
        }
    }

    // Clears general errors (like tag loading errors)
    fun clearError() {
        if (_uiState.value.error != null) {
            _uiState.update { it.copy(error = null) }
            Timber.v("Clearing general error.")
        }
    }

    override fun onCleared() {
        super.onCleared()
        fetchTagsJob?.cancel()
        createGroupJob?.cancel()
        Timber.d("CreateGroupViewModel cleared.")
    }
}