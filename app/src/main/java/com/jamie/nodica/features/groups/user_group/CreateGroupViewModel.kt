package com.jamie.nodica.features.groups.user_group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jamie.nodica.features.groups.group.Group
import com.jamie.nodica.features.groups.group.GroupUseCase
import com.jamie.nodica.features.profile.TagItem // Reuse TagItem data class
import io.github.jan.supabase.SupabaseClient // Inject client for tag fetching (or use a repository)
import io.github.jan.supabase.postgrest.from // Import postgrest 'from'
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class CreateGroupViewModel(
    private val groupUseCase: GroupUseCase,
// Option: Inject a TagRepository/UseCase if created, otherwise inject SupabaseClient directly for now
    private val supabaseClient: SupabaseClient,
    private val currentUserId: String
) : ViewModel() {

    // Combined state holder for this screen
    private val _uiState = MutableStateFlow(CreateGroupScreenState())
    val uiState: StateFlow<CreateGroupScreenState> = _uiState.asStateFlow()

// Keep separate creationState for specific operation feedback if needed,
// or merge it into CreateGroupScreenState. Let's merge it for simplicity.
// private val _creationState = MutableStateFlow<CreateGroupOpState>(CreateGroupOpState.Idle)
// val creationState: StateFlow<CreateGroupOpState> get() = _creationState.asStateFlow()

    init {
        Timber.d("CreateGroupViewModel initialized for user $currentUserId")
        fetchAvailableTags() // Fetch tags needed for the selection UI
    }

    private fun fetchAvailableTags() {
        _uiState.update { it.copy(isLoadingTags = true, error = null) } // Indicate tags are loading
        viewModelScope.launch {
            try {
                val tags = supabaseClient.from("tags")
                    .select()
                    .decodeList<TagItem>()

                val tagsByCategory = tags.groupBy { it.category }
                    .mapValues { entry -> entry.value.sortedBy { it.name } }

                _uiState.update {
                    it.copy(
                        availableTags = tagsByCategory,
                        isLoadingTags = false // Tags loaded
                    )
                }
                Timber.i("Fetched ${tags.size} tags for Create Group screen.")

            } catch (e: Exception) {
                Timber.e(e, "Error fetching available tags for Create Group")
                _uiState.update {
                    it.copy(
                        isLoadingTags = false,
                        error = "Failed to load available tags: ${e.message}"
                    )
                }
            }
        }
    }


    fun createGroup(
        name: String,
        description: String,
        meetingSchedule: String,
        selectedTagIds: List<String> // Accept Tag IDs from UI
    ) {
        // Simple client-side validation
        if (name.isBlank() || selectedTagIds.isEmpty()) {
            _uiState.update { it.copy(error = "Group name and at least one tag are required.", opState = CreateGroupOpState.ValidationError) }
            return
        }

        _uiState.update { it.copy(opState = CreateGroupOpState.Loading, error = null) } // Indicate creation loading
        Timber.d("Attempting to create group. Name: $name, Tags: $selectedTagIds")
        viewModelScope.launch {
            val newGroup = Group(
                id = "", // DB generates ID
                name = name.trim(),
                description = description.trim(),
                meetingSchedule = meetingSchedule.trim().ifBlank { null },
                creatorId = currentUserId,
                tags = emptyList() // Not used directly in creation via repo method
            )

            groupUseCase.createGroup(newGroup, selectedTagIds).fold(
                onSuccess = { createdGroup ->
                    Timber.i("Group created successfully: ${createdGroup.id}")
                    _uiState.update { it.copy(opState = CreateGroupOpState.Success(createdGroup)) }
                    // Navigation is handled via LaunchedEffect in the Screen observing the state
                },
                onFailure = { error ->
                    Timber.e(error, "Failed to create group")
                    _uiState.update {
                        it.copy(
                            opState = CreateGroupOpState.Error(error.message ?: "Error creating group"),
                            error = error.message ?: "Error creating group" // Also set general error maybe?
                        )
                    }
                }
            )
        }
    }

    // Reset operation state (e.g., after error snackbar shown or navigation)
    fun resetOperationState() {
        _uiState.update { it.copy(opState = CreateGroupOpState.Idle) }
    }
    // Reset error state (e.g., after snackbar shown)
    fun clearError() {
        _uiState.update { it.copy(error = null, opState = if (uiState.value.opState is CreateGroupOpState.ValidationError) CreateGroupOpState.Idle else uiState.value.opState ) }
    }
}

// Operation State specific to the Create Group action
sealed class CreateGroupOpState {
    object Idle : CreateGroupOpState()
    object Loading : CreateGroupOpState()
    object ValidationError: CreateGroupOpState() // Specific state for client validation failure
    data class Success(val group: Group) : CreateGroupOpState()
    data class Error(val message: String) : CreateGroupOpState()
}

// Combined UI State for the Create Group Screen
data class CreateGroupScreenState(
    val availableTags: Map<String, List<TagItem>> = emptyMap(),
    val isLoadingTags: Boolean = true,
    val opState: CreateGroupOpState = CreateGroupOpState.Idle, // Holds state of the create operation
    val error: String? = null // General error messages (like tag loading failure)
)