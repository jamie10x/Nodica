package com.jamie.nodica.features.groups.group

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jamie.nodica.features.groups.user_group.UserGroupsViewModel
import com.jamie.nodica.features.navigation.Routes
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(navController: NavController) {
    // Retrieve the UserGroupsViewModel via Koin.
    val userGroupsViewModel: UserGroupsViewModel = koinViewModel()
    val userGroups by userGroupsViewModel.userGroups.collectAsState()
    val error by userGroupsViewModel.error.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("My Groups") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
        ) {
            error?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(16.dp))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(userGroups) { group ->
                    GroupItem(
                        group = group,
                        joined = true, // ✅ User is already a member of this group
                        onJoinClicked = {
                            navController.navigate("${Routes.MESSAGES}/${group.id}")
                            // Optionally navigate to chat or group details
                            // navController.navigate("${Routes.MESSAGES}/${group.id}")
                        }
                    )

                }
            }
        }
    }
}
