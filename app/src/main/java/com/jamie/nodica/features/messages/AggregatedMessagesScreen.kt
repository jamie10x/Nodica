package com.jamie.nodica.features.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape // For placeholder
import androidx.compose.material.ExperimentalMaterialApi // For PullRefresh
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.jamie.nodica.features.groups.group.Group // Import Group model
import com.jamie.nodica.features.groups.user_group.UserGroupsViewModel
import com.jamie.nodica.features.navigation.Routes
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class) // OptIn for M3 and PullRefresh
@Composable
fun AggregatedMessagesScreen(outerNavController: NavHostController) {
// Use the existing ViewModel that holds the user's groups
    val userGroupsViewModel: UserGroupsViewModel = koinViewModel()
    val uiState by userGroupsViewModel.uiState.collectAsState()
    val userGroups = uiState.groups
    val isLoading = uiState.isLoading
    val isRefreshing = uiState.isRefreshing
    val error = uiState.error

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

// Pull-to-refresh state, triggers ViewModel refresh
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { userGroupsViewModel.refresh() }
    )

// Show errors in Snackbar
    LaunchedEffect(error) {
        error?.let { errorMessage ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = errorMessage,
                    duration = SnackbarDuration.Long
                )
                userGroupsViewModel.clearError() // Clear error after showing
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(title = { Text("My Group Chats") })
        }
        // No FAB needed here usually
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .pullRefresh(pullRefreshState) // Apply pull-to-refresh
        ) {
            // Content: Loading, Empty, or List
            when {
                // Initial Loading state
                isLoading && userGroups.isEmpty() && !isRefreshing -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                // Empty state
                userGroups.isEmpty() && !isLoading && !isRefreshing -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp), // More padding for empty state text
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.ChatBubbleOutline, // Example Icon
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) // Muted icon
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "No chats yet", // Simpler title
                                style = MaterialTheme.typography.titleLarge,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Join or create a study group to start chatting with members.",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant // Muted color
                            )
                        }
                    }
                }
                // Group Chat List
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        // Add padding to list content, not the whole column
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp) // Space between items
                    ) {
                        items(items = userGroups, key = { it.id }) { group ->
                            ConversationItem( // Use the enhanced item
                                group = group,
                                onClick = {
                                    // Use outerNavController to navigate to the specific message screen
                                    outerNavController.navigate("${Routes.MESSAGES}/${group.id}")
                                }
                            )
                        }
                    }
                }
            } // End When

            // Pull-to-refresh indicator at the top center
            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        } // End Box
    } // End Scaffold
}

// Enhanced ConversationItem Composable
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationItem(group: Group, onClick: () -> Unit) {
// Determine a placeholder color based on the group name hash for visual distinction
    val placeholderColor = remember(group.id) {
        val hash = group.name.hashCode()
        Color(
            red = (hash and 0xFF0000 shr 16) / 255f * 0.5f + 0.2f, // Use darker shades
            green = (hash and 0x00FF00 shr 8) / 255f * 0.5f + 0.2f,
            blue = (hash and 0x0000FF) / 255f * 0.5f + 0.2f,
        ).copy(alpha = 0.8f) // Add some transparency
    }

    Card(
        onClick = onClick, // Make the whole card clickable
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), // Subtle elevation
        // Use surface container for slight contrast if needed, or default surface
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp), // Slightly reduced padding
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Placeholder for Group Icon/Avatar
            Box(
                modifier = Modifier
                    .size(48.dp) // Standard avatar size
                    .clip(CircleShape)
                    .background(placeholderColor), // Use derived color
                contentAlignment = Alignment.Center
            ) {
                // Display first letter of the group name
                Text(
                    text = group.name.firstOrNull()?.uppercase() ?: "G",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary // High contrast color
                )
                // TODO: Replace with AsyncImage if group icon URL is available later
                /*
                 AsyncImage(
                     model = ImageRequest.Builder(LocalContext.current)
                         .data(group.iconUrl) // Assuming group has an iconUrl property
                         .crossfade(true)
                         .placeholder(R.drawable.default_group_icon) // Add a default placeholder drawable
                         .error(R.drawable.default_group_icon)
                         .build(),
                     contentDescription = "${group.name} Icon",
                     contentScale = ContentScale.Crop,
                     modifier = Modifier.fillMaxSize()
                 )
                */
            }

            Spacer(Modifier.width(12.dp)) // Space between icon and text

            // Column for Group Name and optional details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, // Make name slightly bolder
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // ** MVP+ Feature Placeholder: Last message preview **
                // if (group.lastMessagePreview != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    // text = group.lastMessagePreview, // Need to fetch this data
                    text = group.description.ifBlank { "Tap to chat" }, // Show description or fallback
                    style = MaterialTheme.typography.bodyMedium, // Use bodyMedium for preview
                    color = MaterialTheme.colorScheme.onSurfaceVariant, // Muted color
                    maxLines = 1, // Keep preview short
                    overflow = TextOverflow.Ellipsis
                )
                // }
            }

            // ** MVP+ Feature Placeholder: Timestamp & Unread Count **
            // Spacer(Modifier.width(8.dp))
            // Column(horizontalAlignment = Alignment.End) {
            // Text(group.lastMessageTime, style = MaterialTheme.typography.labelSmall)
            // if (group.unreadCount > 0) {
            // Badge { Text("${group.unreadCount}") }
            // }
            // }
        }
    }
}