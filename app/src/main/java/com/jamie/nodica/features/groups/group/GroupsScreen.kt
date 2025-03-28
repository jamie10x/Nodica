package com.jamie.nodica.features.groups.group

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController // Correct import
import com.jamie.nodica.features.groups.user_group.UserGroupsViewModel
import com.jamie.nodica.features.navigation.Routes // Import Routes
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(outerNavController: NavHostController) { // Accept outer NavController
    // Retrieve the ViewModel holding the list of groups the user has joined.
    val userGroupsViewModel: UserGroupsViewModel = koinViewModel()
    val userGroups by userGroupsViewModel.userGroups.collectAsState()
    val error by userGroupsViewModel.error.collectAsState()
    // Simple loading check: assumes empty list means loading until error or data arrives
    val isLoading = userGroups.isEmpty() && error == null

    // Refresh the list when the screen is first composed or recomposed
    LaunchedEffect(Unit) {
        userGroupsViewModel.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("My Groups") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                // Use outerNavController to navigate to the Create Group screen
                outerNavController.navigate(Routes.CREATE_GROUP)
            }) {
                Icon(Icons.Default.Add, contentDescription = "Create Group")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize() // Ensure column takes full space
        ) {
            // Display error if loading failed
            error?.let {
                Text(
                    text = "Error loading your groups: $it",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp) // Padding for the error text
                )
            }

            // Handle Loading and Empty states
            when {
                isLoading && error == null -> {
                    // Show loading indicator
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                userGroups.isEmpty() && error == null -> {
                    // Show empty state message
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "You haven't joined any groups yet.\nUse the '+' button to create one or discover groups in the Home tab.",
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                else -> {
                    // Display the list of joined groups
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(), // List takes remaining space
                        verticalArrangement = Arrangement.spacedBy(12.dp), // Space between group items
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp) // Padding around the list items
                    ) {
                        items(userGroups, key = { it.id }) { group ->
                            GroupItem(
                                group = group,
                                joined = true, // These are groups the user is already a member of
                                onJoinClicked = { // This action now means "Open Group Chat"
                                    // Use outerNavController to navigate to the specific MessageScreen
                                    outerNavController.navigate("${Routes.MESSAGES}/${group.id}")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}