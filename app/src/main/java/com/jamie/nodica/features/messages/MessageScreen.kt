// main/java/com/jamie/nodica/features/messages/MessageScreen.kt
package com.jamie.nodica.features.messages

// --- Keep necessary imports, remove ones confirmed unused by IDE/build ---
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape // Keep for TopAppBar indicator
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
// import androidx.compose.material.icons.filled.WifiOff // Keep if using icon indicator
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha // Keep for icon indicator if used
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController // Keep this
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.jamie.nodica.features.navigation.Routes
import com.jamie.nodica.ui.theme.NodicaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.* // Import specific datetime components if needed, or wildcard
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import java.util.Locale // Import Locale for String.format
import androidx.compose.foundation.background // Keep for TopAppBar indicator
import androidx.compose.ui.graphics.Color
import kotlin.time.Duration.Companion.hours

// --- Rest of the file remains largely the same as the previous refinement ---
// --- Key changes are marked below ---

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MessageScreen(navController: NavController, groupId: String) {
    // ... (ViewModel, State, UI Setup - unchanged) ...
    val messageViewModel: MessageViewModel = koinViewModel(parameters = { parametersOf(groupId) })
    val uiState by messageViewModel.uiState.collectAsState()
    val messages = uiState.messages
    val isLoading = uiState.isLoading
    val isSending = uiState.isSending
    val error = uiState.error
    val currentUserId = uiState.currentUserId
    val isRealtimeConnected = uiState.isRealtimeConnected
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var messageContent by rememberSaveable { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    // ... (Auth Check - unchanged) ...
    LaunchedEffect(currentUserId, error) {
        val isAuthError = error?.contains("Authentication error", ignoreCase = true) == true
        if (isAuthError) {
            Timber.e("MessageScreen: Auth error detected from ViewModel. Navigating away.")
            delay(1500)
            navController.navigate(Routes.ONBOARDING) {
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    var showLoadingForUserId by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(500.milliseconds); showLoadingForUserId = false }

    if (currentUserId == null && !showLoadingForUserId && error?.contains("Authentication error", ignoreCase = true) != true) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text("Loading user...")
            }
        }
        return
    }

    // ... (Error Snackbar Effect - unchanged) ...
    LaunchedEffect(error) {
        error?.let {
            if (!it.contains("Authentication error", ignoreCase = true)) {
                scope.launch {
                    snackbarHostState.showSnackbar(message = it, duration = SnackbarDuration.Long)
                    messageViewModel.clearError()
                }
            } else {
                messageViewModel.clearError()
            }
        }
    }

    // ... (Autoscroll Effect - unchanged) ...
    val isUserScrolling = remember { derivedStateOf { listState.isScrollInProgress } }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && !isUserScrolling.value) {
            val lastIndex = messages.lastIndex
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            if (lastVisibleItem == null || lastVisibleItem.index < lastIndex - 1 ||
                (lastVisibleItem.index == lastIndex && lastVisibleItem.offset + lastVisibleItem.size < layoutInfo.viewportEndOffset - 10)
            ) {
                try { listState.animateScrollToItem(lastIndex) }
                catch (e: Exception) { Timber.w(e, "Error animating scroll to item $lastIndex") }
            }
        }
    }


    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            MessageScreenTopAppBar(
                groupName = uiState.groupName ?: "Group Chat", // TODO: Fetch actual group name
                isConnected = isRealtimeConnected,
                onNavigateBack = { navController.navigateUp() }
            )
        },
        contentWindowInsets = WindowInsets(0,0,0,0)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .imePadding()
                .fillMaxSize()
        ) {
            Box(modifier = Modifier.weight(1f)) {
                when {
                    isLoading && messages.isEmpty() -> {
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
                    else -> {
                        currentUserId?.let { userId ->
                            MessagesList(
                                listState = listState,
                                messages = messages,
                                currentUserId = userId
                            )
                        } ?: run {
                            Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Error: User not identified.") }
                        }
                    }
                }
            } // End Content Box

            MessageInputArea(
                value = messageContent,
                onValueChange = { messageContent = it },
                onSendClick = {
                    val contentToSend = messageContent.trim()
                    if (contentToSend.isNotEmpty()) {
                        messageContent = ""
                        keyboardController?.hide()
                        messageViewModel.sendMessage(contentToSend)
                    }
                },
                isSending = isSending,
                modifier = Modifier.fillMaxWidth()
            )
        } // End Main Column
    } // End Scaffold
}

// TopAppBar (Unchanged from previous refinement)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageScreenTopAppBar( /* ... code ... */
                                    groupName: String,
                                    isConnected: Boolean,
                                    onNavigateBack: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(groupName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = if (isConnected) Color.Green.copy(alpha = 0.7f) else Color.Gray,
                            shape = CircleShape
                        )
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    )
}


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
        verticalArrangement = Arrangement.spacedBy(10.dp),
        reverseLayout = false
    ) {
        itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
            val prevMessage = messages.getOrNull(index - 1)
            // --- FIX: Remove unused nextMessage variable ---
            // val nextMessage = messages.getOrNull(index + 1)

            val showSenderSpacing = prevMessage != null && message.senderId != prevMessage.senderId
            // val isLastInBlock = nextMessage == null || message.senderId != nextMessage.senderId

            if (showSenderSpacing) {
                Spacer(modifier = Modifier.height(8.dp))
            }

            MessageBubble(
                message = message,
                isCurrentUser = message.senderId == currentUserId,
                // isLastInBlock = isLastInBlock // Removed
            )
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

// MessageInputArea (Unchanged from previous refinement)
@Composable
private fun MessageInputArea( /* ... code ... */
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
            OutlinedTextField( /* ... */
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Send a message...") },
                maxLines = 5,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(onSend = { if (!isSending && value.isNotBlank()) onSendClick() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                ),
                enabled = !isSending
            )
            Spacer(Modifier.width(8.dp))
            IconButton( /* ... */
                onClick = onSendClick,
                enabled = value.isNotBlank() && !isSending,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if(value.isNotBlank() && !isSending) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    contentColor = if(value.isNotBlank() && !isSending) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send Message")
                }
            }
        }
    }
}


// MessageBubble (Unchanged from previous refinement, alignment/gravity usage is correct)
@Composable
fun MessageBubble( /* ... */
                   message: Message,
                   isCurrentUser: Boolean,
    // isLastInBlock: Boolean // Removed
) {
    val alignment = if (isCurrentUser) Alignment.End else Alignment.Start // IDE might flag if not directly used in Modifier, but used for horizontalGravity
    val horizontalGravity = if (isCurrentUser) Alignment.End else Alignment.Start
    val backgroundColor = if (isCurrentUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = if (isCurrentUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val bubbleShape = remember(isCurrentUser) {
        RoundedCornerShape(
            topStart = 16.dp, topEnd = 16.dp,
            bottomStart = if (isCurrentUser) 4.dp else 16.dp,
            bottomEnd = if (!isCurrentUser) 4.dp else 16.dp
        )
    }
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalAlignment = horizontalGravity // This uses horizontalGravity
    ) {
        Card(
            modifier = Modifier.widthIn(max = screenWidth * 0.8f),
            shape = bubbleShape,
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
            modifier = Modifier.padding(top = 3.dp, start = 6.dp, end = 6.dp)
        )
    }
}

// formatTimestampFriendly (Added Locale)
fun formatTimestampFriendly(timestamp: Instant): String {
    return try {
        val now = Clock.System.now()
        val duration = now - timestamp
        val zone = TimeZone.currentSystemDefault()
        val localDateTime = timestamp.toLocalDateTime(zone)
        val nowDate = now.toLocalDateTime(zone).date

        when {
            duration < 1.minutes -> "Just now"
            duration < 1.hours -> "${duration.inWholeMinutes}m ago"
            localDateTime.date == nowDate ->
                // --- FIX: Added Locale.getDefault() ---
                String.format(Locale.getDefault(), "%02d:%02d", localDateTime.hour, localDateTime.minute)
            // Add yesterday logic if needed
            // localDateTime.date == nowDate.minus(1, DateTimeUnit.DAY) -> "Yesterday ${String.format(Locale.getDefault(), "%02d:%02d", localDateTime.hour, localDateTime.minute)}"
            else -> {
                // --- FIX: Added Locale.getDefault() ---
                String.format(
                    Locale.getDefault(),
                    "%d/%d %02d:%02d",
                    localDateTime.dayOfMonth,
                    localDateTime.monthNumber,
                    localDateTime.hour,
                    localDateTime.minute
                )
            }
        }
    } catch (e: Exception) {
        Timber.w(e, "Error formatting timestamp: $timestamp")
        "---"
    }
}


// Previews (Unchanged)
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

@Preview(showBackground = true, name = "Message Screen Dark")
@Composable
fun MessageScreenPreviewDark() {
    val navController = rememberNavController()
    val groupId = "preview_group_123"
    val currentUserId = "user_me"
    val now = Clock.System.now()
    val sampleMessages = listOf(
        Message("1", groupId, "user_other", "Hey!", now.minus(5.minutes)),
        Message("2", groupId, currentUserId, "Hi there! How are you?", now.minus(2.minutes)),
        Message("3", groupId, "user_other", "Good, just testing this amazing chat UI.", now)
    )

    NodicaTheme(darkTheme = true) {
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