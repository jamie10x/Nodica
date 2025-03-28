package com.jamie.nodica.features.messages

import androidx.compose.runtime.Immutable // Mark state as Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.postgrest.query.filter.FilterOperator // Import filter op
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@Immutable // Mark state as immutable for Compose stability
data class MessageScreenState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val isSending: Boolean = false, // Track if a message is currently being sent
// Optionally add group details like name if fetched
// val groupName: String? = null
)

class MessageViewModel(
    private val messageUseCase: MessageUseCase,
    private val supabaseClient: SupabaseClient, // Inject SupabaseClient for Realtime
    val currentUserId: String, // Make public if needed by UI directly
    private val groupId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(MessageScreenState())
    val uiState: StateFlow<MessageScreenState> = _uiState.asStateFlow()

    private var channel: RealtimeChannel? = null
    private var realtimeJob: Job? = null

    init {
        Timber.d("Initializing MessageViewModel for group: $groupId, user: $currentUserId")
        // Fetch initial messages and then start listening for realtime updates.
        fetchInitialMessages()
        listenForNewMessages()
        // TODO: Optionally fetch group details (name) here if needed for TopAppBar
    }

    private fun fetchInitialMessages() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            Timber.d("Fetching initial messages for group $groupId")
            messageUseCase.getMessages(groupId).fold(
                onSuccess = { initialMessages ->
                    Timber.d("Fetched ${initialMessages.size} initial messages.")
                    // Messages from repo are already sorted oldest first
                    _uiState.update {
                        it.copy(isLoading = false, messages = initialMessages)
                    }
                },
                onFailure = { error ->
                    Timber.e(error, "Error fetching initial messages for group $groupId")
                    _uiState.update {
                        it.copy(isLoading = false, error = "Failed to load messages: ${error.message}")
                    }
                }
            )
        }
    }

    private fun listenForNewMessages() {
        realtimeJob?.cancel() // Cancel previous listener job
        viewModelScope.launch { channel?.unsubscribe() } // Unsubscribe previous channel

        realtimeJob = viewModelScope.launch {
            try {
                val channelId = "realtime:public:messages:group_id=eq.$groupId" // Standard channel ID format
                Timber.d("Creating or joining Realtime channel: $channelId")

                channel = supabaseClient.realtime.channel(channelId) {
                    // Channel configuration if needed
                    // config -> config.broadcast = ...
                }

                // Listen specifically for INSERTS on the messages table matching the groupId
                channel?.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                    table = "messages"
                    // Filter on the server side for efficiency
                    // Use the filter parameter block
                    filter("group_id", FilterOperator.EQ, groupId)
                }?.catch { e -> // Catch errors in the flow
                    Timber.e(e, "Error in Realtime flow for channel $channelId")
                    _uiState.update { it.copy(error = "Realtime connection error: ${e.message}") }
                    // Consider retry logic here or informing the user persistently
                }?.onEach { insertAction -> // Process successful inserts
                    Timber.d("Realtime INSERT received for group $groupId")
                    try {
                        val newMessage = insertAction.decodeRecord<Message>() // Throws on decode error
                        // Add the new message only if it doesn't already exist
                        _uiState.update { currentState ->
                            if (currentState.messages.none { it.id == newMessage.id }) {
                                Timber.v("Adding new message: ${newMessage.id}")
                                currentState.copy(messages = currentState.messages + newMessage)
                            } else {
                                Timber.w("Duplicate message received via Realtime, ignoring: ${newMessage.id}")
                                currentState // No change
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error decoding inserted message record via Realtime")
                        // Update error state without crashing the listener
                        _uiState.update { it.copy(error = "Error processing new message.") }
                    }
                }?.launchIn(viewModelScope) // Launch the flow collector

                // Subscribe to the channel *after* setting up the flow listener
                channel?.subscribe()
                Timber.d("Subscribed to Realtime channel: $channelId. Status: ${channel?.status?.value}")

            } catch (e: Exception) {
                Timber.e(e, "Failed to setup Realtime subscription for group $groupId")
                _uiState.update { it.copy(error = "Realtime setup failed: ${e.message}") }
            }
        }
    }

    fun sendMessage(content: String) {
        if (content.isBlank() || _uiState.value.isSending) {
            return // Prevent sending empty messages or duplicates
        }

        _uiState.update { it.copy(isSending = true) } // Indicate sending state

        // Optional: Optimistic UI update - add a temporary message
        // val optimisticId = "temp_${System.currentTimeMillis()}"
        // val optimisticMessage = Message(optimisticId, groupId, currentUserId, content.trim(), Clock.System.now())
        // _uiState.update { it.copy(messages = it.messages + optimisticMessage) }

        viewModelScope.launch {
            messageUseCase.sendMessage(
                groupId = groupId,
                senderId = currentUserId,
                content = content // UseCase will trim
            ).fold(
                onSuccess = { sentMessage ->
                    Timber.i("Message send successful (UseCase): ${sentMessage.id}")
                    // If Realtime is working, the message *should* arrive via the listener.
                    // If using optimistic update, remove the temp message when the actual one arrives.
                    // If *not* using optimistic UI or Realtime might be unreliable,
                    // manually add the confirmed message here:
                    /*_uiState.update { currentState ->
                        if (currentState.messages.none { it.id == sentMessage.id }) { // Check if Realtime already added it
                            currentState.copy(
                                isSending = false,
                                // Remove optimistic message if used:
                                // messages = currentState.messages.filterNot { it.id == optimisticId } + sentMessage
                                messages = currentState.messages + sentMessage // Add confirmed message
                            )
                        } else {
                            currentState.copy(isSending = false) // Already added by Realtime
                        }
                    }*/
                    _uiState.update { it.copy(isSending = false) } // Assume Realtime handles adding

                },
                onFailure = { error ->
                    Timber.e(error, "Error sending message to group $groupId")
                    // Remove optimistic message if used
                    // _uiState.update { it.copy(messages = it.messages.filterNot { m -> m.id == optimisticId }) }
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            error = "Failed to send message: ${error.message}"
                        )
                    }
                }
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("Clearing MessageViewModel for group $groupId, unsubscribing from Realtime.")
        realtimeJob?.cancel()
        viewModelScope.launch {
            try {
                channel?.unsubscribe()
                channel?.let { supabaseClient.realtime.removeChannel(it) } // Clean up channel instance
                Timber.d("Unsubscribed and removed Realtime channel.")
            } catch (e: Exception) {
                Timber.e(e, "Error unsubscribing/removing Realtime channel onCleared.")
            }
        }
        channel = null
    }
}