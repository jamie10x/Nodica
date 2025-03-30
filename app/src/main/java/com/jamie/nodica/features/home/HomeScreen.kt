package com.jamie.nodica.features.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.* // Import common icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.jamie.nodica.features.groups.group.DiscoverGroupViewModel
import com.jamie.nodica.features.groups.group.GroupItem
import com.jamie.nodica.ui.theme.NodicaTheme
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(outerNavController: NavHostController) {
    val groupViewModel: DiscoverGroupViewModel = koinViewModel()
    // Observe the public UI state object
    val uiState by groupViewModel.uiState.collectAsState()

    // Derive values from uiState
    val groups = uiState.filteredGroups
    val isLoading = uiState.isLoading
    val error = uiState.error
    val searchQuery = uiState.searchQuery
    val tagQuery = uiState.tagQuery
    val isJoining = uiState.isJoining // General joining indicator
    val joiningGroupId = uiState.joiningGroupId // Specific group being joined

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Show errors (search/fetch errors, join errors) in a Snackbar
    LaunchedEffect(error) {
        error?.let { errorMessage ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = errorMessage,
                    duration = SnackbarDuration.Short
                )
                groupViewModel.clearError() // Clear error after showing
            }
        }
    }

    // Launched effect to refresh data when the screen becomes visible?
    // DisposableEffect(Unit) { onDispose { } } // Can be useful

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(title = { Text("Discover Study Groups") })
            // Optional: Add filter/refresh actions here
            // actions = { IconButton(onClick = { groupViewModel.refreshDiscover() }) { Icon(Icons.Default.Refresh, null)} }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Search and Filter Area
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                // Search by Name
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { groupViewModel.onSearchQueryChanged(it) },
                    label = { Text("Search by name") },
                    placeholder = { Text("e.g., Calculus Study Group") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, "Search Icon") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { groupViewModel.onSearchQueryChanged("") }) {
                                Icon(Icons.Default.Clear, "Clear Search")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() })
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Filter by Tag
                OutlinedTextField(
                    value = tagQuery,
                    onValueChange = { groupViewModel.onTagQueryChanged(it) },
                    label = { Text("Filter by tag/subject") },
                    placeholder = { Text("e.g., Physics, IELTS, Kotlin") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, "Filter Icon") }, // Label icon for tags
                    trailingIcon = {
                        if (tagQuery.isNotEmpty()) {
                            IconButton(onClick = { groupViewModel.onTagQueryChanged("") }) {
                                Icon(Icons.Default.Clear, "Clear Filter")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() })
                )
            }

            // Content Area: Loading, Empty, or List
            Box(modifier = Modifier.weight(1f)) {
                when {
                    // Loading State
                    isLoading && groups.isEmpty() -> { // Show only if list is empty during load
                        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    }
                    // Empty State (after loading, no results matching filters)
                    groups.isEmpty() && !isLoading -> {
                        Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
                            Text(
                                text = "No groups found matching your criteria.\nTry broadening your search or creating a new group!",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    // Groups List
                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(groups, key = { it.id }) { group ->
                                // Determine if the join button for *this* group should show loading
                                val isJoiningThisGroup = isJoining && joiningGroupId == group.id

                                GroupItem(
                                    group = group,
                                    joined = false, // These are always discoverable, not joined yet
                                    onActionClicked = { groupViewModel.joinGroup(group.id) },
                                    actionText = "Join Group",
                                    // Pass joining state to the item if needed for button state
                                    isActionInProgress = isJoiningThisGroup
                                )
                            }
                        }
                    }
                }
                // Show pull-to-refresh indicator if needed (might be redundant with search loading)
                // PullRefreshIndicator(...)
            }
        }
    }
}

// Example Preview (can be more elaborate)
@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    NodicaTheme {
        HomeScreen(rememberNavController())
    }
}