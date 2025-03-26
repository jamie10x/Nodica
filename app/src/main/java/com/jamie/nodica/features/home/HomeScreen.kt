package com.jamie.nodica.features.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jamie.nodica.features.groups.group.GroupItem
import com.jamie.nodica.features.groups.group.GroupViewModel
import com.jamie.nodica.features.groups.user_group.UserGroupsViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val groupViewModel: GroupViewModel = koinViewModel()
    val groups by groupViewModel.groups.collectAsState()
    val error by groupViewModel.error.collectAsState()
    val searchQuery by groupViewModel.searchQuery.collectAsState()
    val subjectQuery by groupViewModel.subjectQuery.collectAsState()

    val userGroupsViewModel: UserGroupsViewModel = koinViewModel()
    val joinedGroupIds by userGroupsViewModel.joinedGroupIds.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Home / Group Discovery") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { groupViewModel.onSearchQueryChanged(it) },
                placeholder = { Text("Search for groups…") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = subjectQuery,
                onValueChange = { groupViewModel.onSubjectQueryChanged(it) },
                placeholder = { Text("Search for subjects or keywords") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            error?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(16.dp))
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(groups) { group ->
                    GroupItem(
                        group = group,
                        joined = group.id in joinedGroupIds,
                        onJoinClicked = {
                            groupViewModel.joinGroup(group.id)
                        }
                    )
                }
            }
        }
    }
}
