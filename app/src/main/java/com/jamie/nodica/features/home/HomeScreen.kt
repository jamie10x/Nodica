// main/java/com/jamie/nodica/features/home/HomeScreen.kt
package com.jamie.nodica.features.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label // AutoMirrored icon
import androidx.compose.material.icons.filled.* // Import common icons like Search, Clear, SearchOff, ErrorOutline
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun HomeScreen(outerNavController: NavHostController) {
    // Inject ViewModel
    val groupViewModel: DiscoverGroupViewModel = koinViewModel()
    // Observe the public UI state object
    val uiState by groupViewModel.uiState.collectAsState()

    // Extract states for readability
    val availableGroups = uiState.availableGroups // Use the filtered list (groups not joined)
    val isLoading = uiState.isLoading
    val isJoining = uiState.isJoining // General joining indicator
    val joiningGroupId = uiState.joiningGroupId // Specific group being joined
    val error = uiState.error
    val searchQuery = uiState.searchQuery
    val tagQuery = uiState.tagQuery

    // UI helpers
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current // To clear focus

    // Effect to show errors (fetch errors, join errors) in a Snackbar
    LaunchedEffect(error) {
        error?.let { errorMessage ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = errorMessage,
                    duration = SnackbarDuration.Long // Show errors longer
                )
                groupViewModel.clearError() // Clear error after showing
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Discover Study Groups") }
                // Optional: Add refresh action? Usually search implies refresh
                // actions = { IconButton(onClick = { groupViewModel.refreshDiscover() }, enabled = !isLoading) { Icon(Icons.Default.Refresh, "Refresh") }}
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                // Click outside text fields to dismiss keyboard
                .clickable(onClick = { focusManager.clearFocus() }, enabled = true, onClickLabel = null)
        ) {
            // --- Search and Filter Area ---
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                // Search by Name TextField
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { groupViewModel.onSearchQueryChanged(it) }, // Debounce handled in VM
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
                    keyboardActions = KeyboardActions(onSearch = {
                        focusManager.clearFocus() // Hide keyboard on search action
                    })
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Filter by Tag TextField
                OutlinedTextField(
                    value = tagQuery,
                    onValueChange = { groupViewModel.onTagQueryChanged(it) }, // Debounce handled in VM
                    label = { Text("Filter by tag/subject") },
                    placeholder = { Text("e.g., Physics, IELTS, Kotlin") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, "Filter Icon") },
                    trailingIcon = {
                        if (tagQuery.isNotEmpty()) {
                            IconButton(onClick = { groupViewModel.onTagQueryChanged("") }) {
                                Icon(Icons.Default.Clear, "Clear Filter")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        focusManager.clearFocus() // Hide keyboard on search action
                    })
                )
            } // End Search/Filter Column

            // --- Content Area: Loading, Empty, Error, or List ---
            Box(modifier = Modifier.weight(1f)) { // Takes remaining space
                when {
                    // 1. Loading State (Show only when list is still empty)
                    isLoading && availableGroups.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    }
                    // 2. Error State (Show only when list is still empty)
                    error != null && availableGroups.isEmpty() -> {
                        ErrorStateDiscover(
                            message = error,
                            onRetry = { groupViewModel.refreshDiscover() } // Retry refreshes all
                        )
                    }
                    // 3. Empty State (Show if not loading and list is empty)
                    availableGroups.isEmpty() && !isLoading -> {
                        EmptyStateDiscover() // Show empty state
                    }
                    // 4. Groups List (Show available groups to join)
                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(availableGroups, key = { group -> group.id }) { group ->
                                // Determine if the join button for *this* group should show loading
                                val isJoiningThisGroup = isJoining && joiningGroupId == group.id

                                GroupItem(
                                    group = group,
                                    joined = false, // Always false for discover screen items
                                    onActionClicked = { groupViewModel.joinGroup(group.id) }, // Action is always join
                                    actionTextOverride = "Join Group", // Button text is always "Join Group"
                                    isActionInProgress = isJoiningThisGroup // Pass joining state for this specific item
                                )
                            }
                        } // End LazyColumn
                    }
                } // End When
            } // End Content Box
        } // End Main Column
    } // End Scaffold
}


// --- Helper Composables for Discover Screen States ---

@Composable
private fun EmptyStateDiscover(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.SearchOff, // Icon indicating nothing found
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "No Groups Found",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Try adjusting your search or filter criteria, or create the first group for this topic!",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorStateDiscover(modifier: Modifier = Modifier, message: String, onRetry: () -> Unit) {
    Box(
        modifier = modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.ErrorOutline, // Error icon
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Error Finding Groups",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                // Consider color contrast for error messages
                color = MaterialTheme.colorScheme.error // Or onErrorContainer if on error container background
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
fun HomeScreenPreview() {
    NodicaTheme {
        // In a real preview, inject a fake ViewModel using Koin test modules or pass fake state
        HomeScreen(rememberNavController())
    }
}