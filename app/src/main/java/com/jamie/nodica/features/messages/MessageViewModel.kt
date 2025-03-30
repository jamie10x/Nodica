package com.jamie.nodica.features.messages

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel // Import needed
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber


@Immutable
data class MessageScreenState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isSending: Boolean = false,
    val isRealtimeConnected: Boolean = false,
    val currentUserId: String? = null,
    val groupName: String? = null
)

class MessageViewModel(
    private val messageUseCase: MessageUseCase,
    private val supabaseClient: SupabaseClient,
    private val groupId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(MessageScreenState())
    val uiState: StateFlow<MessageScreenState> = _uiState.asStateFlow()

    private var channel: RealtimeChannel? = null
    private var realtimeJob: Job? = null
    private var fetchJob: Job? = null

    private fun getCurrentUserIdOrSetError(actionDescription: String): String? { /* ... unchanged ... */
        val userId = supabaseClient.auth.currentUserOrNull()?.id
        if (userId == null) {
            Timber.e("MessageViewModel: User not logged in while trying to $actionDescription.")
            _uiState.update { it.copy(
                isLoading = false,
                isSending = false,
                error = "Authentication error. Please log in again."
            ) }
        }
        if (userId != null && _uiState.value.currentUserId != userId) {
            _uiState.update { it.copy(currentUserId = userId) }
        }
        return userId
    }

    init { /* ... unchanged ... */
        Timber.d("Initializing MessageViewModel for group: $groupId")
        if (getCurrentUserIdOrSetError("initialize ViewModel") != null) {
            fetchInitialMessages()
            listenForNewMessages()
            // fetchGroupDetails()
        }
    }

    private fun fetchInitialMessages() { /* ... unchanged ... */
        if (fetchJob?.isActive == true) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        fetchJob = viewModelScope.launch {
            Timber.d("Fetching initial messages for group $groupId")
            messageUseCase.fetchMessages(groupId).fold(
                onSuccess = { initialMessages ->
                    if (!isActive) return@fold
                    Timber.d("Fetched ${initialMessages.size} initial messages.")
                    _uiState.update {
                        it.copy(isLoading = false, messages = initialMessages)
                    }
                },
                onFailure = { error ->
                    if (!isActive) return@fold
                    Timber.e(error, "Error fetching initial messages for group $groupId")
                    _uiState.update {
                        it.copy(isLoading = false, error = "Failed to load messages: ${error.message}")
                    }
                }
            )
        }
    }

    private fun listenForNewMessages() {
        realtimeJob?.cancel()
        viewModelScope.launch {
            try { channel?.unsubscribe() } catch (e: Exception) { Timber.e(e, "Error unsubscribing previous channel") }
        }

        realtimeJob = viewModelScope.launch {
            try {
                val channelId = "realtime:public:messages:group_id=eq.$groupId"
                Timber.d("Setting up Realtime listener for channel: $channelId")
                _uiState.update { it.copy(isRealtimeConnected = false) }

                channel = supabaseClient.realtime.channel(channelId)

                // Status Flow
                channel?.status?.onEach { status ->
                    Timber.i("Realtime channel $channelId status updated: $status") // Log status changes
                    val isConnected = status == RealtimeChannel.Status.SUBSCRIBED
                    _uiState.update { it.copy(isRealtimeConnected = isConnected) }

                    // *** REMOVED Incorrect Check ***
                    // Just log if connection seems lost based on status change away from SUBSCRIBED
                    if (_uiState.value.isRealtimeConnected && !isConnected) { // Was connected, but now isn't
                        Timber.w("Realtime connection potentially lost for $channelId (Status: $status). Waiting for reconnect or error.")
                        // Optionally show a temporary indicator in UI, relying on isRealtimeConnected state
                    }

                }?.launchIn(viewModelScope)


                // Postgres Change Flow (Insertions)
                channel?.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                    table = "messages"
                    filter("group_id", FilterOperator.EQ, groupId)
                }
                    ?.catch { e -> /* ... unchanged error handling ... */
                        Timber.e(e, "Error in Realtime postgresChangeFlow for channel $channelId")
                        _uiState.update { it.copy(error = "Chat connection error: ${e.message}", isRealtimeConnected = false) } // Mark as disconnected on flow error
                    }
                    ?.onEach { insertAction -> /* ... unchanged message handling ... */
                        Timber.d("Realtime INSERT received on channel $channelId")
                        try {
                            val newMessage = insertAction.decodeRecord<Message>()
                            if (newMessage.groupId != groupId) {
                                Timber.w("Realtime received message for wrong group (${newMessage.groupId}), ignoring.")
                                return@onEach
                            }
                            _uiState.update { currentState ->
                                if (currentState.messages.none { it.id == newMessage.id }) {
                                    Timber.v("Adding new message via Realtime: ${newMessage.id}")
                                    currentState.copy(messages = currentState.messages + newMessage)
                                } else {
                                    Timber.w("Duplicate message ${newMessage.id} received via Realtime, ignoring.")
                                    currentState
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error decoding Realtime message record")
                        }
                    }
                    ?.launchIn(viewModelScope)

                Timber.d("Subscribing to Realtime channel: $channelId")
                channel?.subscribe() // Attempt subscription

            } catch (e: Exception) {
                Timber.e(e, "Failed to setup Realtime subscription for group $groupId")
                _uiState.update { it.copy(error = "Realtime setup failed: ${e.message}", isRealtimeConnected = false) }
            }
        }
    }

    fun sendMessage(content: String) { /* ... unchanged ... */
        val currentUserId = getCurrentUserIdOrSetError("send message") ?: return
        val trimmedContent = content.trim()
        if (trimmedContent.isBlank() || _uiState.value.isSending) return
        _uiState.update { it.copy(isSending = true, error = null) }
        viewModelScope.launch {
            messageUseCase.sendMessage(groupId = groupId, senderId = currentUserId, content = trimmedContent)
                .fold(
                    onSuccess = { sentMessage ->
                        Timber.i("Message send successful via API: ${sentMessage.id}")
                        _uiState.update { it.copy(isSending = false) }
                    },
                    onFailure = { error ->
                        Timber.e(error, "Error sending message to group $groupId")
                        _uiState.update { it.copy(isSending = false, error = "Failed to send message: ${error.message}") }
                    }
                )
        }
    }

    fun clearError() { /* ... unchanged ... */
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() { /* ... unchanged ... */
        super.onCleared()
        Timber.d("Clearing MessageViewModel for group $groupId, cleaning up Realtime.")
        fetchJob?.cancel()
        realtimeJob?.cancel()
        viewModelScope.launch {
            try {
                channel?.unsubscribe()
                channel?.let { supabaseClient.realtime.removeChannel(it) }
                Timber.d("Realtime channel unsubscribed and removed.")
            } catch (e: Exception) {
                Timber.e(e, "Error cleaning up Realtime channel onCleared.")
            }
        }
        channel = null
    }
}