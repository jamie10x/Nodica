package com.jamie.nodica.features.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MessageViewModel(
    private val messageUseCase: MessageUseCase,
    private val currentUserId: String,
    private val groupId: String
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> get() = _messages

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> get() = _error

    init {
        fetchMessages()
    }

    fun fetchMessages() {
        viewModelScope.launch {
            messageUseCase.getMessages(groupId).fold(
                onSuccess = { _messages.value = it },
                onFailure = { _error.value = it.message }
            )
        }
    }

    fun sendMessage(content: String) {
        // Create a new Message with current timestamp
        val message = Message(
            id = "",
            groupId = groupId,
            senderId = currentUserId,
            content = content,
            timestamp = System.currentTimeMillis().toString()
        )
        viewModelScope.launch {
            messageUseCase.sendMessage(message).fold(
                onSuccess = { fetchMessages() },
                onFailure = { _error.value = it.message }
            )
        }
    }
}
