package com.jamie.nodica.features.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
// import io.github.jan.supabase.realtime.PostgresChangeFilter // No longer needed for EQ string
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel // Extension function import
import io.github.jan.supabase.realtime.decodeRecord // Extension function import
import io.github.jan.supabase.realtime.postgresChangeFlow // Extension function import
import io.github.jan.supabase.realtime.realtime // Extension property/function import
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

class MessageViewModel(
    private val messageUseCase: MessageUseCase,
    private val supabaseClient: SupabaseClient, // Inject SupabaseClient for Realtime
    private val currentUserId: String,
    private val groupId: String
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> get() = _messages

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> get() = _error

    private var channel: RealtimeChannel? = null
    private var realtimeJob: Job? = null

    init {
        Timber.d("Initializing MessageViewModel for group: $groupId")
        // Fetch initial messages and then start listening for realtime updates.
        fetchInitialMessages()
        listenForNewMessages()
    }

    // Fetch messages only once on initialization
    private fun fetchInitialMessages() {
        viewModelScope.launch {
            Timber.d("Fetching initial messages for group $groupId")
            try {
                val initialMessages = messageUseCase.getMessages(groupId).getOrElse {
                    throw it // Rethrow to be caught by the catch block
                }
                // Sort messages by timestamp before updating the state
                _messages.value = initialMessages.sortedBy { it.timestamp }
                Timber.d("Fetched ${initialMessages.size} initial messages.")
            } catch (e: Exception) {
                Timber.e(e, "Error fetching initial messages for group $groupId")
                _error.value = "Failed to load initial messages: ${e.message}"
            }
        }
    }

    // Subscribe to realtime updates for new messages
    private fun listenForNewMessages() {
        // Cancel any existing job and unsubscribe from channel first
        realtimeJob?.cancel()
        viewModelScope.launch { channel?.unsubscribe() }

        realtimeJob = viewModelScope.launch {
            try {
                // Create a unique channel name for this group chat
                val channelId = "chat_group_$groupId"
                Timber.d("Creating or joining Realtime channel: $channelId")
                // Use the extension function `channel()` on the realtime instance
                channel = supabaseClient.realtime.channel(channelId)

                // Listen for INSERT operations on the 'messages' table for this group_id
                channel?.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                    table = "messages"
                    // Use the filter function with column, operator string, and value
                    filter(column = "group_id", operator = FilterOperator.EQ, value = groupId) // <-- CORRECTED LINE
                }?.onEach { insertAction ->
                    Timber.d("Realtime INSERT received for group $groupId")
                    try {
                        // Use the decodeRecord extension function
                        val newMessage = insertAction.decodeRecord<Message>()
                        _messages.update { currentList ->
                            // Add new message only if it's not already present (handles potential race conditions)
                            if (currentList.none { it.id == newMessage.id }) {
                                // Add and re-sort the list by timestamp
                                (currentList + newMessage).sortedBy { it.timestamp }
                            } else {
                                Timber.w("Received duplicate message via Realtime: ${newMessage.id}")
                                currentList // Return unchanged list if duplicate
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error decoding inserted message record")
                        // Optionally set an error state, but avoid crashing the listener
                        _error.value = "Error processing new message."
                    }
                    // TODO: Add handling for UPDATE and DELETE if needed
                }?.catch { e ->
                    Timber.e(e, "Error in Realtime flow for channel $channelId")
                    _error.value = "Realtime connection error: ${e.message}"
                    // Consider attempting to resubscribe after a delay
                }?.launchIn(viewModelScope) // Launch the flow collection in the ViewModel's scope

                // Subscribe to the channel after setting up the listener flow
                channel?.subscribe()
                Timber.d("Subscribed to Realtime channel: $channelId")

            } catch (e: Exception) {
                Timber.e(e, "Failed to setup Realtime subscription for group $groupId")
                _error.value = "Realtime setup failed: ${e.message}"
            }
        }
    }

    fun sendMessage(content: String) {
        // Optimistic UI update (optional): Add message locally immediately
        // val optimisticMessage = Message(...)
        // _messages.update { (it + optimisticMessage).sortedBy { m -> m.timestamp } }

        viewModelScope.launch {
            // Create a new Message object
            val message = Message(
                id = "", // ID will be generated by DB
                groupId = groupId,
                senderId = currentUserId,
                content = content,
                // Use a reliable timestamp format, e.g., ISO 8601 or ensure DB uses now()
                // Consider using server timestamp `now()` in Supabase insert policy/trigger if possible
                timestamp = kotlinx.datetime.Clock.System.now().toString() // Example using kotlinx-datetime
            )
            Timber.d("Sending message to group $groupId: $content")

            messageUseCase.sendMessage(message).fold(
                onSuccess = {
                    Timber.d("Message sent successfully.")
                    // No explicit fetch needed if Realtime is working correctly.
                    // If optimistic update was used, Realtime might send the same message back; handle duplicates.
                },
                onFailure = { error ->
                    Timber.e(error, "Error sending message to group $groupId")
                    _error.value = "Failed to send message: ${error.message}"
                    // Optional: Remove optimistic message if sending failed
                    // _messages.update { list -> list.filterNot { it.id == optimisticMessage.id } }
                }
            )
        }
    }

    // Function to clear the error state after it's been shown in the UI
    fun clearError() {
        _error.value = null
    }

    // Unsubscribe from the Realtime channel when the ViewModel is cleared
    override fun onCleared() {
        super.onCleared()
        Timber.d("Clearing MessageViewModel for group $groupId, unsubscribing from Realtime.")
        realtimeJob?.cancel() // Cancel the collection job
        viewModelScope.launch {
            try {
                channel?.let { // Check if channel is not null before removing
                    it.unsubscribe() // Unsubscribe from the channel
                    supabaseClient.realtime.removeChannel(it) // Clean up the channel instance
                    Timber.d("Unsubscribed and removed Realtime channel successfully.")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error unsubscribing from Realtime channel onCleared.")
            }
        }
        channel = null // Ensure reference is cleared
    }
}