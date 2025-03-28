package com.jamie.nodica.features.messages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign // Import required
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController // Correct import
import com.jamie.nodica.features.groups.group.Group // Import Group model
import com.jamie.nodica.features.groups.user_group.UserGroupsViewModel // Import UserGroupsViewModel
import com.jamie.nodica.features.navigation.Routes // Import Routes
import org.koin.androidx.compose.koinViewModel // Use Koin ViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AggregatedMessagesScreen(outerNavController: NavHostController) { // Accept outer NavController
    // Get the ViewModel that holds the user's groups
    val userGroupsViewModel: UserGroupsViewModel = koinViewModel()
    val userGroups by userGroupsViewModel.userGroups.collectAsState()
    val error by userGroupsViewModel.error.collectAsState()
    val isLoading = userGroups.isEmpty() && error == null // Simple loading state check

    // Refresh groups when the screen becomes visible (or recomposes after navigation)
    LaunchedEffect(Unit) {
        userGroupsViewModel.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("My Group Chats") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize() // Ensure Column takes full size
        ) {
            // Display error message if any
            error?.let {
                Text(
                    text = "Error loading groups: $it",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // Handle loading and empty states
            when {
                isLoading && error == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                userGroups.isEmpty() && error == null -> {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "You haven't joined any groups yet.", // Simplified message
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                else -> {
                    // Display the list of groups (conversations)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize() // Fill remaining space in the Column
                            .padding(horizontal = 16.dp), // Padding for the list itself
                        verticalArrangement = Arrangement.spacedBy(12.dp), // Space between items
                        contentPadding = PaddingValues(vertical = 16.dp) // Padding top/bottom of list
                    ) {
                        items(items = userGroups, key = { it.id }) { group ->
                            ConversationItem(group = group) {
                                // Use outerNavController to navigate to the specific message screen
                                outerNavController.navigate("${Routes.MESSAGES}/${group.id}")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationItem(group: Group, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Optional TODO: Add Group Icon/Image here
            // Image(...)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (group.description.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = group.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // Optional TODO: Add last message preview & time
                // Spacer(Modifier.height(4.dp))
                // Text("Last message preview...", style = MaterialTheme.typography.bodySmall)
            }
            // Optional TODO: Add unread message count badge
            // Badge(...)
        }
    }
}