// main/java/com/jamie/nodica/features/home/HomeScreen.kt
package com.jamie.nodica.features.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn // Changed to LazyColumn for vertical scroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController // Correct import
import com.jamie.nodica.features.groups.group.GroupItem
import com.jamie.nodica.features.groups.group.GroupViewModel
import com.jamie.nodica.features.groups.user_group.UserGroupsViewModel
import com.jamie.nodica.features.navigation.Routes // Import Routes
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(outerNavController: NavHostController) { // Accept outer NavController
    val groupViewModel: GroupViewModel = koinViewModel()
    val groups by groupViewModel.groups.collectAsState()
    val error by groupViewModel.error.collectAsState()
    val searchQuery by groupViewModel.searchQuery.collectAsState()
    val subjectQuery by groupViewModel.subjectQuery.collectAsState()

    val userGroupsViewModel: UserGroupsViewModel = koinViewModel()
    // Fetch joined groups to determine join status
    LaunchedEffect(Unit) {
        userGroupsViewModel.fetchUserGroups()
    }
    val joinedGroupIds by userGroupsViewModel.joinedGroupIds.collectAsState()


    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Home / Group Discovery") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize() // Allow vertical scrolling if content exceeds screen
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { groupViewModel.onSearchQueryChanged(it) },
                placeholder = { Text("Search groups by name…") }, // Updated placeholder
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = subjectQuery,
                onValueChange = { groupViewModel.onSubjectQueryChanged(it) },
                placeholder = { Text("Filter by subjects or keywords...") }, // Updated placeholder
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            error?.let {
                Text(
                    text = "Error: $it",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Use LazyColumn for vertical arrangement and better performance if many groups
            if (groups.isEmpty() && error == null) {
                Box(modifier = Modifier.fillMaxSize().padding(top = 32.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("No groups found matching your criteria.")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(groups, key = { it.id }) { group ->
                        GroupItem(
                            group = group,
                            joined = group.id in joinedGroupIds,
                            onJoinClicked = {
                                if (group.id in joinedGroupIds) {
                                    // If already joined, navigate to messages
                                    outerNavController.navigate("${Routes.MESSAGES}/${group.id}")
                                } else {
                                    // If not joined, perform join action
                                    groupViewModel.joinGroup(group.id)
                                    // Optional: Navigate immediately or wait for state update?
                                    // Consider showing a loading/success indicator
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}