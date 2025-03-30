package com.jamie.nodica.features.groups.group

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
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
import com.jamie.nodica.features.groups.user_group.UserGroupsViewModel
import com.jamie.nodica.features.navigation.Routes
import com.jamie.nodica.ui.theme.NodicaTheme
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun GroupsScreen(outerNavController: NavHostController) {
    val userGroupsViewModel: UserGroupsViewModel = koinViewModel()
    val uiState by userGroupsViewModel.uiState.collectAsState()

    val userGroups = uiState.groups
    val isLoading = uiState.isLoading // True only during initial load when list is potentially empty
    val isRefreshing = uiState.isRefreshing // True only during pull-to-refresh action
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
            TopAppBar(title = { Text("My Groups") })
        },
        floatingActionButton = {
            // Show FAB unless it's the very first load attempt
            // Once loading is false, always show it, regardless of empty list or errors
            if (!isLoading) {
                FloatingActionButton(
                    onClick = { outerNavController.navigate(Routes.CREATE_GROUP) },
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create Group")
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .pullRefresh(pullRefreshState)
        ) {
            when {
                // Initial Loading indicator
                isLoading /* && userGroups.isEmpty() */ -> { // Simplified: Show loader whenever isLoading is true initially
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                // Empty State (Show ONLY if loading is finished AND list is empty AND not currently refreshing)
                userGroups.isEmpty() && !isLoading && !isRefreshing -> {
                    Box( /* ... Empty State Column ... */
                        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                            Spacer(Modifier.height(16.dp))
                            Text("No Groups Yet", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(8.dp))
                            Text("Join groups from the 'Discover' tab or create your own using the '+' button.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                // Groups List (Show if not initial loading, even if refreshing, includes empty list during refresh)
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        items(userGroups, key = { it.id }) { group ->
                            GroupItem(
                                group = group,
                                joined = true,
                                onActionClicked = { outerNavController.navigate("${Routes.MESSAGES}/${group.id}") },
                                actionText = "Open Chat"
                            )
                        }
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

// Example Preview (Unchanged)
@Preview(showBackground = true)
@Composable
fun GroupsScreenPreview() {
    NodicaTheme {
        GroupsScreen(rememberNavController())
    }
}