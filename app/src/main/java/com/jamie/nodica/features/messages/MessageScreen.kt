package com.jamie.nodica.features.messages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScreen(navController: NavController, groupId: String) {
    // Retrieve currentUserId dynamically; replace with your actual logic.
    val currentUserId = "CURRENT_USER_ID_PLACEHOLDER"
    // Retrieve MessageViewModel with parameters.
    val messageViewModel: MessageViewModel = koinViewModel(parameters = { parametersOf(currentUserId, groupId) })
    val messages by messageViewModel.messages.collectAsState()
    val error by messageViewModel.error.collectAsState()

    var messageContent by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Group Chat") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (error != null) {
                Text(text = error!!, color = MaterialTheme.colorScheme.error)
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                items(messages) { message ->
                    // Display message sender and content.
                    Text(text = "${message.senderId}: ${message.content}")
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = messageContent,
                    onValueChange = { messageContent = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a message...") }
                )
                IconButton(onClick = {
                    if (messageContent.isNotBlank()) {
                        messageViewModel.sendMessage(messageContent)
                        messageContent = ""
                    }
                }) {
                    Icon(imageVector = Icons.Default.Send, contentDescription = "Send")
                }
            }
        }
    }
}
