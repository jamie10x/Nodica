package com.jamie.nodica.features.messages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.WifiOff // Icon for offline state
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.jamie.nodica.features.navigation.Routes // Import routes
import com.jamie.nodica.ui.theme.NodicaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.androidx.compose.koinViewModel
// koinInject is usually for non-ViewModel dependencies
// import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import timber.log.Timber
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class) // imePadding is ExperimentalLayoutApi
@Composable
fun MessageScreen(navController: NavController, groupId: String) {

    // --- ViewModel & State ---
    // Inject ViewModel passing only the groupId parameter
    val messageViewModel: MessageViewModel = koinViewModel(parameters = { parametersOf(groupId) })
    val uiState by messageViewModel.uiState.collectAsState()

    // Extract states for readability
    val messages = uiState.messages
    val isLoading = uiState.isLoading
    val isSending = uiState.isSending
    val error = uiState.error
    val currentUserId = uiState.currentUserId // Get user ID from ViewModel state
    val isRealtimeConnected = uiState.isRealtimeConnected

    // --- Critical Authentication Check (ViewModel handles setting error if null now) ---
    // Check if the ViewModel itself flagged a critical auth error on init
    LaunchedEffect(currentUserId, error) {
        if (currentUserId == null && error?.contains("Authentication error", ignoreCase = true) == true) {
            Timber.e("MessageScreen: Auth error detected from ViewModel. Navigating away.")
            // Snackbar shown via standard error handling effect below
            delay(1500) // Allow snackbar to show
            navController.navigate(Routes.ONBOARDING) { // Navigate to a safe place
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
    // If userId is null but no specific auth error, show loading/appropriate state
    if (currentUserId == null && error?.contains("Authentication error", ignoreCase = true) != true) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return // Stop rendering until user ID is confirmed or error occurs
    }


    // --- UI Setup ---
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var messageContent by rememberSaveable { mutableStateOf("") }

    // --- Side Effects ---
    // Error Handling
    LaunchedEffect(error) {
        error?.let {
            scope.launch {
                snackbarHostState.showSnackbar(message = it, duration = SnackbarDuration.Long)
                messageViewModel.clearError()
            }
        }
    }

    // Autoscroll Logic (unchanged)
    val isUserScrolling = remember { derivedStateOf { listState.isScrollInProgress } }
    LaunchedEffect(messages.size) {
        val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
        if (messages.isNotEmpty() && !isUserScrolling.value &&
            (lastVisibleItem == null || lastVisibleItem.index >= messages.size - 2)) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    // --- UI ---
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MessageScreenTopAppBar(
                // groupName = uiState.groupName ?: "Group Chat", // Use fetched name when available
                groupName = "Group Chat", // Placeholder
                isConnected = isRealtimeConnected,
                onNavigateBack = { navController.navigateUp() }
            )
        },
        // Apply CONSUMED ime padding here to prevent Scaffold from applying its own conflicting padding
        contentWindowInsets = WindowInsets(0,0,0,0)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                // Apply padding provided by Scaffold (for TopAppBar)
                .padding(paddingValues)
                // Apply imePadding first to push content up BEFORE other padding/constraints
                .imePadding()
                .fillMaxSize()
        ) {
            // --- Content Area ---
            Box(modifier = Modifier.weight(1f)) {
                when {
                    isLoading && messages.isEmpty() -> { // Only show full screen loader initially
                        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    }
                    !isLoading && messages.isEmpty() -> {
                        Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                            Text(
                                text = "No messages yet. Start the conversation!",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    else -> { // Display messages
                        MessagesList(
                            listState = listState,
                            messages = messages,
                            // Pass the non-null user ID here, safe due to earlier check
                            currentUserId = currentUserId ?: ""
                        )
                    }
                }
            } // End Content Box

            // --- Input Area ---
            MessageInputArea(
                value = messageContent,
                onValueChange = { messageContent = it },
                onSendClick = {
                    val contentToSend = messageContent.trim()
                    if (contentToSend.isNotEmpty()) {
                        messageContent = "" // Clear input field AFTER retrieving content
                        messageViewModel.sendMessage(contentToSend)
                    }
                },
                isSending = isSending, // Pass sending state
                modifier = Modifier.fillMaxWidth()
            )
        } // End Main Column
    } // End Scaffold
}

/** Top App Bar with optional connection status indicator */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageScreenTopAppBar(
    groupName: String,
    isConnected: Boolean, // For Realtime status
    onNavigateBack: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(groupName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // Optional: Show a small indicator if Realtime is disconnected
                if (!isConnected) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Default.WifiOff,
                        contentDescription = "Chat disconnected",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp).alpha(0.7f)
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            // Adjust container color for better visual hierarchy if needed
            containerColor = MaterialTheme.colorScheme.surface // Or surfaceContainer
        )
    )
}

/** Displays the list of messages (Unchanged from previous steps) */
@Composable
private fun MessagesList(
    listState: LazyListState,
    messages: List<Message>,
    currentUserId: String // Expect non-null ID here
) {
    LazyColumn( /* ... LazyColumn setup ... */
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
            val prevMessage = messages.getOrNull(index - 1)
            // Basic grouping: Add extra space between messages from different senders
            val addSpacing = prevMessage != null && message.senderId != prevMessage.senderId
            if (addSpacing) {
                Spacer(modifier = Modifier.height(6.dp))
            }
            // TODO: Add Date separators based on timestamp logic

            MessageBubble(
                message = message,
                isCurrentUser = message.senderId == currentUserId // Compare with non-null ID
            )
        }
        item { Spacer(Modifier.height(4.dp)) } // Padding at the bottom
    }
}

/** Message input area - pass isSending state (Unchanged except added isSending parameter) */
@Composable
private fun MessageInputArea(
    value: String,
    onValueChange: (String) -> Unit,
    onSendClick: () -> Unit,
    isSending: Boolean, // Added parameter
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceContainer // Or surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Send a message") }, // Changed placeholder
                maxLines = 5,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(onSend = { if (!isSending) onSendClick() }), // Prevent send if already sending
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    // Customize container color if needed to match Surface
                    // focusedContainerColor = MaterialTheme.colorScheme.surface,
                    // unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                // Disable text field briefly while sending? Optional.
                // enabled = !isSending
            )
            Spacer(Modifier.width(8.dp))

            // Send Button - uses isSending state
            val sendButtonEnabled = value.isNotBlank() && !isSending
            IconButton( // Using IconButton for better alignment with TextField height
                onClick = onSendClick,
                enabled = sendButtonEnabled,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    // Optional: provide distinct background/content color when enabled
                    // containerColor = if(sendButtonEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    // contentColor = if(sendButtonEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = ContentAlpha.disabled)
                )
            ) {
                // Use Box to potentially overlay indicator if needed, but simpler is better.
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp
                        // Use content color decided by IconButton
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send Message"
                        // Tint is handled by IconButton contentColor
                    )
                }
            }
        }
    }
}


/** Message bubble (Unchanged from previous steps) */
@Composable
fun MessageBubble(message: Message, isCurrentUser: Boolean) { /* ... MessageBubble code ... */
    val alignment = if (isCurrentUser) Alignment.End else Alignment.Start
    val horizontalGravity = if (isCurrentUser) Alignment.End else Alignment.Start
    val backgroundColor = if (isCurrentUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer // Adjusted colors
    val textColor = if (isCurrentUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer // Adjusted colors
    val bubbleShape = remember(isCurrentUser) {
        RoundedCornerShape(
            topStart = 16.dp, topEnd = 16.dp,
            bottomStart = if (isCurrentUser) 16.dp else 4.dp,
            bottomEnd = if (isCurrentUser) 4.dp else 16.dp
        )
    }
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalAlignment = horizontalGravity
    ) {
        Card(
            modifier = Modifier.widthIn(max = screenWidth * 0.8f),
            shape = bubbleShape,
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp) // Remove elevation for cleaner look
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
        Text(
            text = formatTimestampFriendly(message.timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp)
        )
    }
}

/** Timestamp formatter (Unchanged from previous steps) */
fun formatTimestampFriendly(timestamp: Instant): String { /* ... formatTimestampFriendly code ... */
    return try {
        val localDateTime = timestamp.toLocalDateTime(TimeZone.currentSystemDefault())
        val hour = localDateTime.hour.toString().padStart(2, '0')
        val minute = localDateTime.minute.toString().padStart(2, '0')
        "$hour:$minute"
    } catch (e: Exception) {
        Timber.w(e, "Error formatting timestamp: $timestamp")
        "---"
    }
}


// Previews (Unchanged)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Preview(showBackground = true, name = "Message Screen Light")
@Composable
fun MessageScreenPreviewLight() { /* ... Preview code ... */
    val navController = rememberNavController()
    val groupId = "preview_group_123"
    val currentUserId = "user_me"
    val now = Clock.System.now()
    val sampleMessages = listOf(
        Message("1", groupId, "user_other", "Hey!", now.minus(5.minutes)),
        Message("2", groupId, currentUserId, "Hi there! How are you?", now.minus(2.minutes)),
        Message("3", groupId, "user_other", "Good, just testing this amazing chat UI.", now)
    )

    NodicaTheme(darkTheme = false) {
        Scaffold(
            topBar = { MessageScreenTopAppBar("Preview Chat", isConnected = true, onNavigateBack = {}) },
            snackbarHost = {}
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    MessagesList(rememberLazyListState(), sampleMessages, currentUserId)
                }
                MessageInputArea(value = "Type here...", onValueChange = {}, onSendClick = {}, isSending = false)
            }
        }
    }
}