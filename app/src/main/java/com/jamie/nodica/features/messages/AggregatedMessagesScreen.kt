package com.jamie.nodica.features.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat // Consistent icon choice
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.jamie.nodica.features.groups.group.Group
import com.jamie.nodica.features.groups.user_group.UserGroupsViewModel
import com.jamie.nodica.features.navigation.Routes
import com.jamie.nodica.ui.theme.NodicaTheme
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock // For preview
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun AggregatedMessagesScreen(outerNavController: NavHostController) {
    val userGroupsViewModel: UserGroupsViewModel = koinViewModel()
    val uiState by userGroupsViewModel.uiState.collectAsState()
    val userGroups = uiState.groups
    val isLoading = uiState.isLoading
    val isRefreshing = uiState.isRefreshing
    val error = uiState.error

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { userGroupsViewModel.refresh() }
    )

    LaunchedEffect(error) {
        error?.let { errorMessage ->
            scope.launch {
                snackbarHostState.showSnackbar(errorMessage, duration = SnackbarDuration.Long)
                userGroupsViewModel.clearError()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(title = { Text("My Group Chats") })
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .pullRefresh(pullRefreshState)
        ) {
            // Content: Loading, Empty, or List
            when {
                isLoading && userGroups.isEmpty() && !isRefreshing -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                userGroups.isEmpty() && !isLoading && !isRefreshing -> {
                    Box( /* ... Empty State Column ... */
                        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.AutoMirrored.Filled.Chat, // Consistent icon
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text("No Chats Yet", style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Join or create a study group to start chatting.",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                else -> { // Show list
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp) // Slightly less space
                    ) {
                        items(items = userGroups, key = { it.id }) { group ->
                            ConversationItem(
                                group = group,
                                onClick = { outerNavController.navigate("${Routes.MESSAGES}/${group.id}") }
                            )
                        }
                    }
                }
            } // End When

            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        } // End Box
    } // End Scaffold
}

// ConversationItem Composable - Refined slightly
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationItem(group: Group, onClick: () -> Unit) {
    val placeholderColor = remember(group.id) { /* ... placeholder color logic ... */
        val hash = group.name.hashCode()
        Color(
            red = (hash and 0xFF0000 shr 16) / 255f * 0.5f + 0.2f,
            green = (hash and 0x00FF00 shr 8) / 255f * 0.5f + 0.2f,
            blue = (hash and 0x0000FF) / 255f * 0.5f + 0.2f,
        ).copy(alpha = 0.8f)
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) // Slightly higher contrast
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), // Standard padding
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box( /* ... Avatar Placeholder Box ... */
                modifier = Modifier.size(48.dp).clip(CircleShape).background(placeholderColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = group.name.firstOrNull()?.uppercase() ?: "?", // Use ? as fallback
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer // Adjust color based on placeholder bg
                    // Ideally, calculate contrast and choose onPrimary/onPrimaryContainer
                )
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    // Use group description as preview, fallback text if empty
                    text = group.description.ifBlank { "Tap to open chat" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Optional: Add unread count / last message time indicators here if data available
            // Spacer(Modifier.width(8.dp))
            // Column(horizontalAlignment = Alignment.End) { /* ... Time / Badge ... */ }
        }
    }
}

// Preview
@Preview(showBackground = true)
@Composable
fun AggregatedMessagesScreenPreview() {
    val sampleGroup = Group("1", "Sample Chat Group", "A description", null, "u1", Clock.System.now())
    // Simulate state for preview if needed, or just show the basic structure
    NodicaTheme {
        // Previewing the ConversationItem directly might be easier
        // AggregatedMessagesScreen(rememberNavController())
        Surface {
            Column(Modifier.padding(16.dp)) {
                ConversationItem(group = sampleGroup, onClick = {})
            }
        }
    }
}