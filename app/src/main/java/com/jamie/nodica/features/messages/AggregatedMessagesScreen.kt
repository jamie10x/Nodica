package com.jamie.nodica.features.messages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items // <-- Add this import explicitly
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jamie.nodica.features.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AggregatedMessagesScreen(navController: NavController) {
    // Example conversation IDs (replace this with dynamic data from your ViewModel or backend)
    val conversationIds = listOf("groupId1", "groupId2", "groupId3")

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("All Conversations") })
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items = conversationIds, key = { it }) { groupId ->
                ConversationItem(groupId = groupId) {
                    navController.navigate("${Routes.MESSAGES}/$groupId")
                }
            }
        }
    }
}

@Composable
private fun ConversationItem(groupId: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Text(
            text = "Conversation: $groupId",
            modifier = Modifier.padding(16.dp)
        )
    }
}
