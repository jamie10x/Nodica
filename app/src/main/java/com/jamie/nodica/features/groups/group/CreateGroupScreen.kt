package com.jamie.nodica.features.groups.group

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(navController: NavController) {
    val viewModel: CreateGroupViewModel = koinViewModel()
    val creationState by viewModel.creationState.collectAsState()

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var meetingSchedule by remember { mutableStateOf("") }

    // For tags, we simulate a tag-based selection similar to the ProfileSetupScreen.
    val categoriesWithTags = remember {
        mutableStateMapOf(
            "Mathematics" to mutableStateListOf("Calculus", "Algebra", "Statistics"),
            "Science" to mutableStateListOf("Physics", "Chemistry", "Biology"),
            "Languages" to mutableStateListOf("English", "Spanish", "Chinese")
        )
    }
    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    var showCustomTagDialog by remember { mutableStateOf(false) }
    var customTagCategory by remember { mutableStateOf("") }
    var customTagName by remember { mutableStateOf("") }

    if (creationState is CreateGroupUiState.Success) {
        // Navigate back to Home (or group details) when creation succeeds.
        LaunchedEffect(Unit) {
            navController.navigate("groups") {
                popUpTo("groups") { inclusive = true }
            }
            viewModel.resetState()
        }
    }

    if (creationState is CreateGroupUiState.Error) {
        val errorMsg = (creationState as CreateGroupUiState.Error).message
        LaunchedEffect(errorMsg) {
            // Show error message, for example with a snackbar.
        }
    }

    if (showCustomTagDialog) {
        AlertDialog(
            onDismissRequest = { showCustomTagDialog = false },
            title = { Text("Add Tag to $customTagCategory") },
            text = {
                OutlinedTextField(
                    value = customTagName,
                    onValueChange = { customTagName = it },
                    label = { Text("Tag Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (customTagName.isNotBlank()) {
                        categoriesWithTags[customTagCategory]?.add(customTagName)
                        selectedTags = selectedTags + customTagName
                    }
                    customTagName = ""
                    showCustomTagDialog = false
                }) {
                    Text("Add")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    customTagName = ""
                    showCustomTagDialog = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Create New Group") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Group Name *") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = meetingSchedule,
                onValueChange = { meetingSchedule = it },
                label = { Text("Meeting Schedule") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )
            Spacer(modifier = Modifier.height(16.dp))
            // Tag-based selection section
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Select Tags", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    categoriesWithTags.forEach { (category, tagList) ->
                        Text(text = category, style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        tagList.forEach { tag ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = tag in selectedTags,
                                    onCheckedChange = { checked ->
                                        selectedTags = if (checked) selectedTags + tag else selectedTags - tag
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(tag)
                            }
                        }
                        TextButton(onClick = {
                            customTagCategory = category
                            showCustomTagDialog = true
                        }) {
                            Text("Add custom tag to $category")
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    viewModel.createGroup(
                        name = name,
                        tags = selectedTags.toList(),
                        description = description,
                        meetingSchedule = meetingSchedule
                    )
                },
                enabled = name.isNotBlank() && selectedTags.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (creationState is CreateGroupUiState.Loading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp).padding(end = 8.dp)
                    )
                }
                Text("Create Group")
            }
            if (creationState is CreateGroupUiState.Error) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = (creationState as CreateGroupUiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
