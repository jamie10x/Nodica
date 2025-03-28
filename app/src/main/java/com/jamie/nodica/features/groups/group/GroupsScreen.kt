// Corrected: main/java/com/jamie/nodica/features/groups/group/GroupsScreen.kt
package com.jamie.nodica.features.groups.group

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.jamie.nodica.features.groups.user_group.UserGroupsViewModel
import com.jamie.nodica.features.navigation.Routes
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class) // Keep both OptIns
@Composable
fun GroupsScreen(outerNavController: NavHostController) {
    val userGroupsViewModel: UserGroupsViewModel = koinViewModel()
    val uiState by userGroupsViewModel.uiState.collectAsState()
    // Derive values directly from uiState
    val userGroups = uiState.groups
    val isLoading = uiState.isLoading
    val isRefreshing = uiState.isRefreshing
    val error = uiState.error

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Pull-to-refresh state (Ensure imports are correct)
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = { userGroupsViewModel.refresh() }
    )

    LaunchedEffect(error) {
        error?.let { errorMessage ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = errorMessage,
                    duration = SnackbarDuration.Long
                )
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
            FloatingActionButton(
                onClick = { outerNavController.navigate(Routes.CREATE_GROUP) },
                // Render FAB based on whether initial load is done and there are groups or not
                modifier = if (isLoading && userGroups.isEmpty()) Modifier.size(0.dp) else Modifier
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Group")
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                // Apply pullRefresh modifier (Ensure import is correct)
                .pullRefresh(pullRefreshState)
        ) {
            when {
                // Initial Loading indicator
                isLoading && userGroups.isEmpty() && !isRefreshing -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                // Empty State
                userGroups.isEmpty() && !isLoading && !isRefreshing -> {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "You haven't joined any groups yet.\nUse the '+' button to create one or discover groups in the Home tab.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // Groups List
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
                                onActionClicked = {
                                    outerNavController.navigate("${Routes.MESSAGES}/${group.id}")
                                },
                                actionText = "Open Chat"
                            )
                        }
                    }
                }
            }

            // PullRefreshIndicator (Ensure import is correct)
            PullRefreshIndicator(
                refreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}