// main/java/com/jamie/nodica/features/groups/group/GroupsScreen.kt
package com.jamie.nodica.features.groups.group

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudOff // Icon for error state
import androidx.compose.material.icons.filled.Groups // Icon for empty state
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.jamie.nodica.features.groups.user_group.UserGroupsViewModel // Correct ViewModel import
import com.jamie.nodica.features.navigation.Routes
import com.jamie.nodica.ui.theme.NodicaTheme
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun GroupsScreen(outerNavController: NavHostController) {
    // Use the correct ViewModel for fetching joined groups
    val userGroupsViewModel: UserGroupsViewModel = koinViewModel()
    // Collect the state from the ViewModel
    val uiState by userGroupsViewModel.uiState.collectAsState()

    // Extract state properties for readability
    val userGroups = uiState.groups       // These are the joined groups
    val isLoading = uiState.isLoading       // True during initial data load attempt
    val isRefreshing = uiState.isRefreshing // True during pull-to-refresh action
    val error = uiState.error

    // UI elements state
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing, // Bind pull-to-refresh state
        onRefresh = { userGroupsViewModel.refresh() } // Call ViewModel's refresh function
    )

    // Effect to show error messages in Snackbar
    LaunchedEffect(error) {
        error?.let { errorMessage ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = errorMessage,
                    duration = SnackbarDuration.Long
                )
                userGroupsViewModel.clearError() // Clear the error in ViewModel after showing
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("My Groups") }
            )
        },
        floatingActionButton = {
            // Show FAB unless loading initially or in critical error state
            if (!isLoading && error == null) {
                FloatingActionButton(
                    onClick = { outerNavController.navigate(Routes.CREATE_GROUP) },
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create New Group")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues) // Apply padding from Scaffold
                .fillMaxSize()
                .pullRefresh(pullRefreshState) // Enable pull-to-refresh
        ) {
            // --- Content Display Logic ---
            when {
                // 1. Initial Loading State
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                // 2. Error State (Show if error occurred and list is empty)
                error != null && userGroups.isEmpty() -> {
                    ErrorState(
                        message = error,
                        onRetry = { userGroupsViewModel.refresh() }
                    )
                }

                // 3. Empty State (Show if loaded, not refreshing, and list is empty)
                userGroups.isEmpty() && !isLoading && !isRefreshing -> {
                    EmptyState(
                        message = "You haven't joined any groups yet.",
                        details = "Join groups from the 'Discover' tab or create your own using the '+' button."
                    )
                }

                // 4. Groups List (Show joined groups)
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp) // Padding around the list
                    ) {
                        items(userGroups, key = { group -> group.id }) { group ->
                            GroupItem(
                                group = group,
                                joined = true, // Always true for this screen
                                // Action navigates to the specific chat screen
                                onActionClicked = {
                                    outerNavController.navigate("${Routes.MESSAGES}/${group.id}")
                                },
                                actionTextOverride = "Open Chat", // Button text is always "Open Chat"
                                isActionInProgress = false // No async action needed for navigation
                            )
                        }
                    }
                }
            } // --- End Content Display Logic ---

            // Pull-to-refresh indicator aligned at the top center
            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                scale = true // Animate the indicator size
            )
        } // End Box
    } // End Scaffold
}

// --- Reusable Helper Composables for States (ensure these are defined) ---

@Composable
private fun EmptyState(modifier: Modifier = Modifier, message: String, details: String? = null) {
    Box(
        modifier = modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Groups, // Relevant icon
                contentDescription = null, // Decorative
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            if (details != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = details,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ErrorState(modifier: Modifier = Modifier, message: String, onRetry: () -> Unit) {
    Box(
        modifier = modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.CloudOff, // Error icon
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Error Loading Groups",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message, // Specific error message
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error // Keep error color for message too
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}


// --- Preview ---
@Preview(showBackground = true)
@Composable
fun GroupsScreenPreview() {
    NodicaTheme {
        GroupsScreen(rememberNavController())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, name = "GroupsScreen Empty State")
@Composable
fun GroupsScreenEmptyPreview() {
    NodicaTheme {
        Scaffold(topBar = { TopAppBar(title = { Text("My Groups") }) }) { padding ->
            EmptyState(Modifier.padding(padding), "No Groups Yet", "Join groups or create one!")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, name = "GroupsScreen Error State")
@Composable
fun GroupsScreenErrorPreview() {
    NodicaTheme {
        Scaffold(topBar = { TopAppBar(title = { Text("My Groups") }) }) { padding ->
            ErrorState(Modifier.padding(padding), "Network connection failed.", onRetry = {})
        }
    }
}