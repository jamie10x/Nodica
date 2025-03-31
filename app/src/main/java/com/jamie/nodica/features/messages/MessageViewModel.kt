package com.jamie.nodica.features.messages

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.RealtimeChannel.Status
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
    val groupName: String? = null // TODO: Fetch actual group name
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

    /**
     * Gets the current user ID. If unavailable, updates the UI state with an authentication error
     * and returns null. Also updates the UI state if the fetched userId differs from the current state.
     *
     * @param actionDescription Description of the action requiring the user ID (for logging).
     * @return The user ID string if available, otherwise null.
     */
    private fun getCurrentUserIdOrSetError(actionDescription: String): String? {
        val userId = supabaseClient.auth.currentUserOrNull()?.id
        if (userId == null) {
            Timber.e("MessageViewModel: User not logged in while trying to $actionDescription.")
            // Avoid infinite loops if error is already set
            if (_uiState.value.error != "Authentication error. Please log in again.") {
                _uiState.update { it.copy(
                    isLoading = false, // Stop loading states
                    isSending = false,
                    error = "Authentication error. Please log in again."
                ) }
            }
        } else if (_uiState.value.currentUserId != userId) {
            // Update the state only if the user ID is different or not yet set
            _uiState.update { it.copy(currentUserId = userId) }
        }
        return userId
    }

    init {
        Timber.d("Initializing MessageViewModel for group: $groupId")
        // Perform initial setup only if user is logged in
        if (getCurrentUserIdOrSetError("initialize ViewModel") != null) {
            fetchInitialMessages()
            listenForNewMessages()
            // TODO: Add call to fetch group details (name) here if needed
        } else {
            // Ensure loading state is false if user isn't logged in on init
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    /**
     * Fetches the initial batch of messages for the current group.
     * Prevents concurrent fetches. Updates loading state and handles success/failure.
     */
    private fun fetchInitialMessages() {
        // Prevent starting a new fetch if one is already active
        if (fetchJob?.isActive == true) {
            Timber.d("fetchInitialMessages skipped: Already active.")
            return
        }
        // Ensure user is logged in before proceeding
        val userIdForCheck = getCurrentUserIdOrSetError("fetch initial messages") ?: return
        Timber.v("Proceeding with fetchInitialMessages for user: $userIdForCheck") // Log to satisfy IDE 'unused' warning

        // Set loading state and clear previous errors
        _uiState.update { it.copy(isLoading = true, error = null) }

        fetchJob = viewModelScope.launch {
            Timber.d("Fetching initial messages for group $groupId")
            messageUseCase.fetchMessages(groupId).fold(
                onSuccess = { initialMessages ->
                    // Ensure coroutine wasn't cancelled while fetching
                    if (!isActive) return@fold
                    Timber.d("Fetched ${initialMessages.size} initial messages for group $groupId.")
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

    /**
     * Sets up the Supabase Realtime subscription to listen for new messages in the current group.
     * Handles channel status updates and processes incoming message inserts.
     */
    private fun listenForNewMessages() {
        // Ensure user is logged in
        val userIdForCheck = getCurrentUserIdOrSetError("listen for new messages") ?: return
        Timber.v("Proceeding with listenForNewMessages for user: $userIdForCheck") // Log to satisfy IDE 'unused' warning

        // Cancel any existing listener job and unsubscribe the old channel
        realtimeJob?.cancel()
        viewModelScope.launch {
            try {
                channel?.unsubscribe()
                Timber.d("Unsubscribed from previous Realtime channel before setting up new one.")
            } catch (e: Exception) {
                Timber.e(e, "Error unsubscribing previous Realtime channel.")
            }
        }

        // Launch a new coroutine for the Realtime listener setup
        realtimeJob = viewModelScope.launch {
            try {
                // Channel name is typically 'schema:table' or just 'table' if public
                val channelId = "public:messages"
                Timber.d("Setting up Realtime listener for channel: $channelId")
                _uiState.update { it.copy(isRealtimeConnected = false) } // Assume not connected initially

                // Create the channel instance
                channel = supabaseClient.realtime.channel(channelId)

                // --- Monitor Channel Status ---
                channel?.status?.onEach { status ->
                    Timber.i("Realtime channel '$channelId' status: $status")
                    val isConnected = status == Status.SUBSCRIBED // Check against imported Status enum
                    // Update connection status state only if it changed
                    if (_uiState.value.isRealtimeConnected != isConnected) {
                        _uiState.update { it.copy(isRealtimeConnected = isConnected) }
                    }
                    // If status indicates a problem (no longer SUBSCRIBED), ensure UI reflects it
                    if (!isConnected && _uiState.value.isRealtimeConnected) {
                        Timber.w("Realtime channel '$channelId' is no longer SUBSCRIBED (Status: $status).")
                        // Optionally set a generic connection issue error if not already set
                        _uiState.update { it.copy(error = it.error ?: "Chat connection unstable.") }
                    }
                }?.catch { e -> // Catch errors specifically from the status flow
                    Timber.e(e, "Error in Realtime status flow for channel '$channelId'")
                    _uiState.update { it.copy(isRealtimeConnected = false, error = "Chat connection monitoring failed: ${e.message}") }
                }?.launchIn(viewModelScope) // Launch status listener


                // --- Listen for INSERT Events on the channel, filtered by group ID ---
                channel?.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                    table = "messages"
                    filter("group_id", FilterOperator.EQ, groupId) // Filter for this group
                }
                    ?.catch { e -> // Catch errors specifically from the message insert flow
                        Timber.e(e, "Error in Realtime postgresChangeFlow for channel '$channelId', groupId '$groupId'")
                        _uiState.update { it.copy(error = "Chat connection error: ${e.message}", isRealtimeConnected = false) }
                    }
                    ?.onEach { insertAction -> // Process incoming insert actions
                        Timber.d("Realtime INSERT received on channel '$channelId' for groupId '$groupId'")
                        try {
                            // Decode the incoming record into a Message object
                            val newMessage = insertAction.decodeRecord<Message>()

                            // Paranoid check: ensure the message is actually for this group (filter should prevent this)
                            if (newMessage.groupId != groupId) {
                                Timber.w("Realtime received message for wrong group (${newMessage.groupId} != $groupId), ignoring.")
                                return@onEach
                            }

                            // Update the UI state by adding the new message if it's not already present
                            _uiState.update { currentState ->
                                // --- Refined Duplicate Check ---
                                // Check if message with this specific ID ALREADY exists in our list
                                val alreadyExists = currentState.messages.any { it.id == newMessage.id }
                                if (!alreadyExists) {
                                    Timber.v("Adding new message via Realtime: ID=${newMessage.id}, Sender=${newMessage.senderId}")
                                    currentState.copy(messages = currentState.messages + newMessage)
                                } else {
                                    // This message (likely the one just sent) is already in the list due to optimistic update. Ignore the realtime echo.
                                    Timber.v("Ignoring Realtime message echo (already added optimistically): ID=${newMessage.id}")
                                    currentState // Return state unchanged
                                }
                            }
                        } catch (e: Exception) {
                            // Log errors during message decoding
                            Timber.e(e, "Error decoding Realtime message record")
                            // Optionally update UI state with a decoding error message
                            // _uiState.update { it.copy(error = "Received unreadable message data.")}
                        }
                    }?.launchIn(viewModelScope) // Launch message insert listener

                // --- Subscribe to the Channel ---
                Timber.d("Attempting to subscribe to Realtime channel: $channelId")
                try {
                    // Call subscribe; it's a suspend function but returns Unit. Errors are exceptions.
                    channel?.subscribe(blockUntilSubscribed = false) // Don't block the coroutine here
                    Timber.i("Realtime channel '$channelId' subscription initiation request sent.")
                    // Note: Actual confirmation of SUBSCRIBED state comes from the status flow listening above.
                } catch(subscribeError: Exception) {
                    // Catch errors during the subscribe() call itself
                    Timber.e(subscribeError, "Realtime channel '$channelId' subscription initiation failed.")
                    _uiState.update { it.copy(error = "Failed to initiate chat connection: ${subscribeError.message}", isRealtimeConnected = false)}
                }

            } catch (e: Exception) { // Catch errors during overall channel setup (e.g., channel creation)
                Timber.e(e, "Failed to setup Realtime subscription process for group $groupId")
                _uiState.update { it.copy(error = "Realtime setup failed: ${e.message}", isRealtimeConnected = false) }
            }
        }
    }

    /**
     * Sends a new message using the MessageUseCase.
     * Updates sending state and handles success/failure results. Adds message optimistically on success.
     *
     * @param content The text content of the message to send.
     */
    fun sendMessage(content: String) {
        val currentUserId = getCurrentUserIdOrSetError("send message") ?: return // Exit if user not logged in
        val trimmedContent = content.trim()

        // Prevent sending blank messages or concurrent sends
        if (trimmedContent.isBlank()) {
            Timber.d("sendMessage skipped: Content is blank.")
            return
        }
        if (_uiState.value.isSending) {
            Timber.d("sendMessage skipped: Already sending.")
            return
        }

        // Set sending state and clear previous errors
        _uiState.update { it.copy(isSending = true, error = null) }

        viewModelScope.launch {
            Timber.d("Sending message: '$trimmedContent' to group $groupId by user $currentUserId")
            messageUseCase.sendMessage(groupId = groupId, senderId = currentUserId, content = trimmedContent)
                .fold(
                    onSuccess = { sentMessage ->
                        // --- SOLUTION for Duplicate Messages & Realtime Delay ---
                        // Optimistically add the message returned by the API to the UI state immediately.
                        Timber.i("Message send API call successful: ID=${sentMessage.id}. Adding optimistically to UI.")
                        _uiState.update { currentState ->
                            // Double-check we don't add duplicates even here (highly unlikely but safe)
                            if (currentState.messages.none { it.id == sentMessage.id }) {
                                currentState.copy(
                                    isSending = false, // Stop sending indicator
                                    messages = currentState.messages + sentMessage // Add message immediately
                                )
                            } else {
                                Timber.w("Optimistic update found duplicate ID ${sentMessage.id}, only updating sending state.")
                                currentState.copy(isSending = false) // Already added somehow? Just stop sending indicator.
                            }
                        }
                        // ----------------------------------------------------------
                    },
                    onFailure = { error ->
                        Timber.e(error, "Error sending message to group $groupId")
                        _uiState.update { it.copy(isSending = false, error = "Failed to send message: ${error.message}") }
                    }
                )
        }
    }

    /**
     * Clears any user-facing error message in the UI state.
     */
    fun clearError() {
        if (_uiState.value.error != null) {
            _uiState.update { it.copy(error = null) }
            Timber.d("Cleared error message.")
        }
    }

    /**
     * Cleans up resources when the ViewModel is destroyed.
     * Cancels running jobs and unsubscribes/removes the Realtime channel.
     */
    override fun onCleared() {
        super.onCleared()
        Timber.d("Clearing MessageViewModel for group $groupId, cleaning up resources.")
        fetchJob?.cancel()
        realtimeJob?.cancel() // Cancels the listener coroutine and its flows

        // Perform channel cleanup in the ViewModel's scope
        viewModelScope.launch {
            try {
                channel?.let { // Ensure channel exists before trying to cleanup
                    val topic = it.topic // Get topic for logging before removal
                    Timber.d("Unsubscribing and removing Realtime channel: $topic")
                    it.unsubscribe() // Attempt to gracefully unsubscribe
                    supabaseClient.realtime.removeChannel(it) // Remove the channel instance
                    Timber.i("Realtime channel $topic unsubscribed and removed.")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error cleaning up Realtime channel during onCleared.")
            } finally {
                channel = null // Ensure the reference is nullified
            }
        }
    }
}