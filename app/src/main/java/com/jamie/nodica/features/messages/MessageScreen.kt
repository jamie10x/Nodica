package com.jamie.nodica.features.messages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send // Use AutoMirrored Send icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.jamie.nodica.ui.theme.NodicaTheme // Import your theme
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject // Updated import for Koin Compose
import org.koin.core.parameter.parametersOf
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScreen(navController: NavController, groupId: String) {
    // Retrieve Supabase client to safely get the current user ID
    val supabaseClient: SupabaseClient = koinInject() // Use koinInject for non-ViewModel dependencies
    // Remember the user ID; it won't change during the lifecycle of this screen
    val currentUserId = remember { supabaseClient.auth.currentUserOrNull()?.id }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState() // State for the message list

    // If user ID is null, display an error and prevent further rendering.
    // This could happen if the user gets logged out while this screen is somehow active.
    if (currentUserId == null) {
        LaunchedEffect(Unit) {
            Timber.e("MessageScreen: Current user ID is null. Cannot proceed.")
            // Optionally show a snackbar or navigate back immediately
            // snackbarHostState.showSnackbar("Error: Not logged in.")
            navController.popBackStack() // Navigate back if user is not logged in
        }
        // Display a placeholder or loading indicator while navigating back
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Authentication error...", color = MaterialTheme.colorScheme.error)
        }
        return // Stop rendering the main content
    }

    // Retrieve MessageViewModel with dynamic parameters (currentUserId and groupId)
    val messageViewModel: MessageViewModel = koinViewModel(
        parameters = { parametersOf(currentUserId, groupId) }
    )
    val messages by messageViewModel.messages.collectAsState()
    val error by messageViewModel.error.collectAsState()

    var messageContent by remember { mutableStateOf("") }

    // Show errors in a Snackbar
    LaunchedEffect(error) {
        error?.let {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "Error: $it",
                    duration = SnackbarDuration.Short
                )
                messageViewModel.clearError() // Add a method to clear error after showing
            }
        }
    }

    // Scroll to bottom when new messages arrive or keyboard opens/closes
    LaunchedEffect(messages, /* keyboard state if detectable */) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0) // Scroll to the top (since list is reversed)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    // TODO: Fetch and display the actual group name
                    Text("Group: $groupId", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding) // Apply padding from Scaffold
                .fillMaxSize()
                // Apply padding to handle the device's keyboard insets
                // This pushes the input field up when the keyboard appears.
                .imePadding()
        ) {
            // Message List Area
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f) // Takes up remaining space
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                reverseLayout = true // Shows latest messages at the bottom
            ) {
                // Spacer at the "bottom" (top when reversed) for padding
                item { Spacer(modifier = Modifier.height(8.dp)) }

                items(messages.reversed(), key = { it.id }) { message -> // Reverse list for reverseLayout
                    MessageBubble(
                        message = message,
                        isCurrentUser = message.senderId == currentUserId
                    )
                    Spacer(modifier = Modifier.height(8.dp)) // Spacing between bubbles
                }
            }

            // Message Input Area - Improved Styling
            Surface(
                shadowElevation = 4.dp, // Add elevation for visual separation
                color = MaterialTheme.colorScheme.surface // Use surface color
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp), // Consistent padding
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = messageContent,
                        onValueChange = { messageContent = it },
                        modifier = Modifier.weight(1f), // TextField takes available space
                        placeholder = { Text("Type a message...") },
                        maxLines = 5, // Allow up to 5 lines before scrolling
                        shape = RoundedCornerShape(24.dp) // Rounded corners for input field
                    )
                    Spacer(Modifier.width(8.dp)) // Space between input and button
                    // Send Button - Enabled only when there's content
                    Button(
                        onClick = {
                            if (messageContent.isNotBlank()) {
                                messageViewModel.sendMessage(messageContent.trim()) // Trim whitespace
                                messageContent = "" // Clear input field
                                // Optional: Manually trigger scroll after sending if needed
                                // scope.launch { listState.animateScrollToItem(0) }
                            }
                        },
                        enabled = messageContent.isNotBlank(), // Disable button if input is blank
                        shape = RoundedCornerShape(50), // Circular button shape
                        contentPadding = PaddingValues(12.dp) // Adjust padding for icon size
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send, // Use AutoMirrored icon
                            contentDescription = "Send Message"
                        )
                    }
                }
            }
        }
    }
}

// Composable for displaying a single message bubble
@Composable
fun MessageBubble(message: Message, isCurrentUser: Boolean) {
    // Determine alignment based on whether the message is from the current user
    val alignment = if (isCurrentUser) Alignment.CenterEnd else Alignment.CenterStart
    // Choose colors based on the sender
    val backgroundColor = if (isCurrentUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isCurrentUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    // Custom shape for message bubbles
    val bubbleShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isCurrentUser) 16.dp else 4.dp, // Pointy corner for received messages
        bottomEnd = if (isCurrentUser) 4.dp else 16.dp  // Pointy corner for sent messages
    )

    Box(modifier = Modifier.fillMaxWidth()) { // Use Box to align the Card
        Card(
            modifier = Modifier
                .align(alignment)
                .widthIn(max = 300.dp), // Limit the maximum width of the bubble
            shape = bubbleShape,
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp) // Subtle elevation
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                // Optionally display sender name for messages not from the current user
                if (!isCurrentUser) {
                    Text(
                        // TODO: Fetch sender's display name from profile instead of raw ID
                        text = "User: ${message.senderId.take(6)}...", // Show truncated ID for now
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary // Use primary color for sender name
                    )
                    Spacer(Modifier.height(2.dp))
                }
                // The actual message content
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge, // Slightly larger text for content
                    color = textColor
                )
                // Optional: Display timestamp (needs formatting)
                // Spacer(Modifier.height(4.dp))
                // Text(
                //     text = formatTimestamp(message.timestamp), // Implement formatTimestamp function
                //     style = MaterialTheme.typography.labelSmall,
                //     color = textColor.copy(alpha = 0.7f)
                // )
            }
        }
    }
}


// --- Preview Function ---
@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun MessageScreenPreview() {
    // Mock NavController and data for preview
    val navController = rememberNavController()
    val groupId = "preview_group_123"
    val currentUserId = "user_me"

    // Sample messages for preview
    val sampleMessages = listOf(
        Message("1", groupId, "user_other", "Hey! How's the studying going?", (System.currentTimeMillis() - 100000).toString()),
        Message("2", groupId, currentUserId, "Pretty good! Just finished the calculus chapter.", (System.currentTimeMillis() - 50000).toString()),
        Message("3", groupId, "user_other", "Nice! Need help with the next one?", System.currentTimeMillis().toString())
    )

    // Mock ViewModel state for preview
    val mockViewModel: MessageViewModel = koinViewModel(parameters = { parametersOf(currentUserId, groupId)}) // Use koinViewModel even in preview if setup allows
    // Or manually create a mock state:
    val messagesState = remember { mutableStateOf(sampleMessages) }
    val errorState = remember { mutableStateOf<String?>(null) }

    NodicaTheme {
        // Manually recreate Scaffold structure for preview if direct ViewModel injection is tricky
        Scaffold(
            topBar = { TopAppBar(title = { Text("Group: $groupId") }) }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
                LazyColumn(modifier = Modifier.weight(1f), reverseLayout = true) {
                    items(messagesState.value.reversed(), key = { it.id }) { msg ->
                        MessageBubble(message = msg, isCurrentUser = msg.senderId == currentUserId)
                        Spacer(Modifier.height(8.dp))
                    }
                }
                Surface(shadowElevation = 4.dp) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = "", onValueChange = {}, modifier = Modifier.weight(1f), placeholder = { Text("Type a message...")})
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { /* */ }) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        }
    }
}