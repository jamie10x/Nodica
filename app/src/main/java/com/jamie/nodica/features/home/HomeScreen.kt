// main/java/com/jamie/nodica/features/home/HomeScreen.kt
package com.jamie.nodica.features.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear // Import Clear icon
import androidx.compose.material.icons.filled.Search // Import Search icon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager // Import focus manager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController // Import keyboard controller
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.jamie.nodica.features.groups.group.DiscoverGroupViewModel // Renamed ViewModel
import com.jamie.nodica.features.groups.group.GroupItem
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(outerNavController: NavHostController) { // Renamed outerNavController for clarity
// Use the renamed ViewModel for discovering groups
    val groupViewModel: DiscoverGroupViewModel = koinViewModel()

    val groups by groupViewModel.filteredGroups.collectAsState() // Observe filtered groups
    val isLoading by groupViewModel.isLoading.collectAsState()
    val error by groupViewModel.error.collectAsState()
    val searchQuery by groupViewModel.searchQuery.collectAsState()
    val tagQuery by groupViewModel.tagQuery.collectAsState() // Renamed from subjectQuery for consistency

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

// Show errors (especially join errors) in a Snackbar
    LaunchedEffect(error) {
        error?.let {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = it,
                    duration = SnackbarDuration.Short
                )
                groupViewModel.clearError() // Clear error after showing
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // Use SmallTopAppBar for less intrusion if preferred
            TopAppBar(
                title = { Text("Discover Groups") }, // Updated title
                // Optional: Add filter actions here later
                // actions = { ... }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Search and Filter Area
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { groupViewModel.onSearchQueryChanged(it) },
                    label = { Text("Search by name") }, // Updated label
                    placeholder = { Text("e.g., Calculus Study Group") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search Icon"
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { groupViewModel.onSearchQueryChanged("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() })
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = tagQuery,
                    onValueChange = { groupViewModel.onTagQueryChanged(it) }, // Updated method name
                    label = { Text("Filter by tag/subject") }, // Updated label
                    placeholder = { Text("e.g., Physics, IELTS, Kotlin") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Filter Icon"
                        )
                    }, // Can use a filter icon
                    trailingIcon = {
                        if (tagQuery.isNotEmpty()) {
                            IconButton(onClick = { groupViewModel.onTagQueryChanged("") }) { // Updated method name
                                Icon(Icons.Default.Clear, contentDescription = "Clear Filter")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() })
                )
            }

            // Content Area: Loading, Empty, or List
            Box(modifier = Modifier.weight(1f)) { // Make the list area take remaining space
                when {
                    // Loading State
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    // Empty State (after loading, no results)
                    groups.isEmpty() && !isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No groups found matching your criteria.\nTry broadening your search!",
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
                            modifier = Modifier.fillMaxSize() // Fill the Box
                        ) {
                            items(groups, key = { it.id }) { group ->
                                GroupItem(
                                    group = group,
                                    // Groups here are discoverable, so 'joined' is always false initially
                                    // The ViewModel handles filtering out already joined groups.
                                    joined = false,
                                    onActionClicked = {
                                        // Join action
                                        groupViewModel.joinGroup(group.id)
                                        // Provide feedback (snackbar or button state change handled by VM state)
                                    },
                                    actionText = "Join Group" // Explicitly set action text
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}