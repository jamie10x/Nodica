package com.jamie.nodica.features.messages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed // Use itemsIndexed for efficient keying & potential grouping
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.jamie.nodica.ui.theme.NodicaTheme
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import timber.log.Timber
import kotlin.time.Duration.Companion.minutes // For preview sample data

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MessageScreen(navController: NavController, groupId: String) {

    // --- Dependencies & Initial Setup ---
    val supabaseClient: SupabaseClient = koinInject()
    val currentUserId = remember { supabaseClient.auth.currentUserOrNull()?.id }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // --- Critical Authentication Check ---
    if (currentUserId == null) {
        // This should ideally not happen if navigation guards are proper, but handle defensively
        LaunchedEffect(Unit) {
            Timber.e("MessageScreen: CRITICAL - Current user ID is NULL. Navigating away.")
            snackbarHostState.showSnackbar("Authentication Error. Please login again.")
            delay(1500)
            // Navigate to a safe root screen
            navController.navigate(com.jamie.nodica.features.navigation.Routes.ONBOARDING) {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                launchSingleTop = true
            }
        }
        // Show loading/placeholder during navigation
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return // Stop rendering
    }

    // --- ViewModel & State ---
    val messageViewModel: MessageViewModel = koinViewModel(parameters = { parametersOf(currentUserId, groupId) })
    val uiState by messageViewModel.uiState.collectAsState()
    val messages = uiState.messages
    val isLoading = uiState.isLoading
    val isSending = uiState.isSending
    val error = uiState.error
    // TODO: val groupName = uiState.groupName // Fetch and display this in TopAppBar

    // --- Local UI State ---
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current
    var messageContent by rememberSaveable { mutableStateOf("") } // Remember input across rotation etc.

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

    // Autoscroll Logic
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
                // groupName = groupName ?: "Group Chat", // Use fetched name when available
                groupName = "Group Chat", // Placeholder
                onNavigateBack = { navController.navigateUp() }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .imePadding() // Adjusts for keyboard automatically
        ) {
            // --- Content Area ---
            Box(modifier = Modifier.weight(1f)) {
                when {
                    // Initial Loading
                    isLoading && messages.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    }
                    // Empty State
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
                    // Messages List
                    else -> {
                        MessagesList(
                            listState = listState,
                            messages = messages,
                            currentUserId = currentUserId
                        )
                    }
                }
                // TODO (Future): Add top pagination loading indicator here if implementing load more
            } // End Content Box

            // --- Input Area ---
            MessageInputArea(
                value = messageContent,
                onValueChange = { messageContent = it },
                onSendClick = {
                    val contentToSend = messageContent.trim() // Trim whitespace before sending
                    if (contentToSend.isNotEmpty()) { // Check trimmed content
                        messageContent = "" // Clear input field
                        // Consider hiding keyboard, but sometimes users send multiple messages
                        // keyboardController?.hide()
                        messageViewModel.sendMessage(contentToSend)
                    }
                },
                isSending = isSending,
                modifier = Modifier.fillMaxWidth() // Ensures it spans the bottom width
            )
        } // End Main Column
    } // End Scaffold
}

/**
 * Top App Bar specific to the Message Screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageScreenTopAppBar(
    groupName: String,
    onNavigateBack: () -> Unit
) {
    TopAppBar(
        title = { Text(groupName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        // Add other actions if needed (e.g., group info, search messages)
        // actions = { ... }
        colors = TopAppBarDefaults.topAppBarColors( // Optional theming
            containerColor = MaterialTheme.colorScheme.surfaceContainer // Slight contrast
        )
    )
}

/**
 * Displays the list of messages.
 */
@Composable
private fun MessagesList(
    listState: LazyListState,
    messages: List<Message>,
    currentUserId: String
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp) // Spacing between bubbles/timestamps
    ) {
        itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
            // Basic grouping logic: Add space if sender changes or time gap is large (optional)
            val prevMessage = messages.getOrNull(index - 1)
            val addSpacing = prevMessage != null && message.senderId != prevMessage.senderId
            // TODO: Add Date Divider logic here based on timestamp changes (e.g., new day)

            if (addSpacing) {
                Spacer(modifier = Modifier.height(6.dp)) // Extra space between different senders
            }

            MessageBubble(
                message = message,
                isCurrentUser = message.senderId == currentUserId
            )
        }
        // Padding at the bottom of the list
        item { Spacer(Modifier.height(4.dp)) }
    }
}


/**
 * Displays the message input field and send button.
 */
@Composable
private fun MessageInputArea(
    value: String,
    onValueChange: (String) -> Unit,
    onSendClick: () -> Unit,
    isSending: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message...") },
                maxLines = 5, // Allow multi-line input up to 5 lines
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send // Show send action on keyboard
                ),
                keyboardActions = KeyboardActions(onSend = { onSendClick() }), // Trigger send
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            )
            Spacer(Modifier.width(8.dp))

            // Send Button - handles enabled state and loading indicator
            val sendButtonEnabled = value.isNotBlank() && !isSending
            Button(
                onClick = onSendClick,
                enabled = sendButtonEnabled,
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp), // Remove padding to size icon correctly
                modifier = Modifier.size(48.dp) // Consistent size for the button
            ) {
                Box(contentAlignment = Alignment.Center) { // Use Box to overlay indicator
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp), // Consistent indicator size
                            color = LocalContentColor.current,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Message"
                            // Tint can be adjusted if needed: tint = LocalContentColor.current
                        )
                    }
                }
            }
        }
    }
}


/**
 * Displays a single message bubble with timestamp.
 */
@Composable
fun MessageBubble(message: Message, isCurrentUser: Boolean) {
    val alignment = if (isCurrentUser) Alignment.End else Alignment.Start
    val horizontalGravity = if (isCurrentUser) Alignment.End else Alignment.Start // For Column alignment
    val backgroundColor = if (isCurrentUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    val textColor = if (isCurrentUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
    val bubbleShape = remember(isCurrentUser) {
        RoundedCornerShape(
            topStart = 16.dp, topEnd = 16.dp,
            bottomStart = if (isCurrentUser) 16.dp else 4.dp,
            bottomEnd = if (isCurrentUser) 4.dp else 16.dp
        )
    }
    // Use LocalConfiguration to get screen width for bubble sizing
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Add less horizontal padding here, let bubble width constraints handle indentation
            .padding(horizontal = 4.dp),
        horizontalAlignment = horizontalGravity
    ) {
        Card(
            modifier = Modifier.widthIn(max = screenWidth * 0.8f), // Max width 80%
            shape = bubbleShape,
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            // Add selectable text if needed using SelectionContainer
            // SelectionContainer {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
            // }
        }
        Text(
            text = formatTimestampFriendly(message.timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 3.dp, start = 4.dp, end = 4.dp) // Adjust padding for alignment
        )
    }
}

/**
 * Formats an Instant timestamp into a user-friendly HH:mm string.
 * Includes basic error handling.
 */
fun formatTimestampFriendly(timestamp: Instant): String {
    return try {
        val localDateTime = timestamp.toLocalDateTime(TimeZone.currentSystemDefault())
        // Consider locale-aware formatting for production using platform APIs
        // For simplicity, using HH:mm with padding
        val hour = localDateTime.hour.toString().padStart(2, '0')
        val minute = localDateTime.minute.toString().padStart(2, '0')
        "$hour:$minute"
    } catch (e: Exception) {
        Timber.w(e, "Error formatting timestamp: $timestamp")
        "---" // Placeholder on error
    }
}

// --- Previews ---
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Preview(showBackground = true, name = "Message Screen Light")
@Composable
fun MessageScreenPreviewLight() {
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
            topBar = { MessageScreenTopAppBar("Preview Chat", onNavigateBack = {}) },
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