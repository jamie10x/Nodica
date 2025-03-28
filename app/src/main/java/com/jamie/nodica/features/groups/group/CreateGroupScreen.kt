// Corrected: main/java/com/jamie/nodica/features/groups/group/CreateGroupScreen.kt

package com.jamie.nodica.features.groups.group

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons // Import Icons
import androidx.compose.material.icons.filled.Done // Import Done icon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.foundation.layout.FlowRow // Specific import
import androidx.compose.foundation.layout.ExperimentalLayoutApi // Import layout API
import androidx.compose.runtime.saveable.rememberSaveable // Import rememberSaveable
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateGroupScreen(navController: NavController) {
    val viewModel: CreateGroupViewModel = koinViewModel()
    val creationState by viewModel.creationState.collectAsState()
    val availableTags by viewModel.availableTags.collectAsState() // Observe available tags

    // Use rememberSaveable for simple state that needs to survive config changes/process death
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var meetingSchedule by rememberSaveable { mutableStateOf("") }
    var selectedTagIds by rememberSaveable { mutableStateOf(emptySet<String>()) } // Remember selected IDs

    // Snackbar for feedback
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Navigate back or show error on state change
    LaunchedEffect(creationState) {
        when (val state = creationState) {
            is CreateGroupUiState.Success -> {
                Timber.i("Group creation successful, navigating back.")
                scope.launch { snackbarHostState.showSnackbar("Group '${state.group.name}' created!") }
                kotlinx.coroutines.delay(300) // Allow snackbar to show briefly
                // Navigate back to the previous screen (likely GroupsScreen or HomeScreen)
                navController.popBackStack()
                viewModel.resetState() // Reset state after handling
            }
            is CreateGroupUiState.Error -> {
                Timber.w("Group creation error: ${state.message}")
                scope.launch { snackbarHostState.showSnackbar("Error: ${state.message}") }
                // Don't reset automatically, allow user to retry
            }
            else -> { /* Idle or Loading */ }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Create New Group") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Group Name *") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                isError = name.isBlank() && creationState is CreateGroupUiState.Error // Basic validation feedback
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = description, onValueChange = { description = it },
                label = { Text("Description") }, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), maxLines = 4
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = meetingSchedule, onValueChange = { meetingSchedule = it },
                label = { Text("Meeting Schedule (Optional)") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )
            Spacer(modifier = Modifier.height(16.dp))

            // --- Tag Selection Section ---
            Text("Select Tags *", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))

            val isTagsError = selectedTagIds.isEmpty() && creationState is CreateGroupUiState.Error

            // Check if tags have loaded
            if (availableTags.isEmpty()) { // Simplified check - assuming loading happens before screen is usable
                // TODO: Add a loading indicator for tags if fetching is asynchronous
                Text("Loading tags...", style = MaterialTheme.typography.bodyMedium)
            } else {
                availableTags.forEach { (category, tagsInCategory) ->
                    Text(category, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        tagsInCategory.forEach { tag ->
                            FilterChip(
                                selected = tag.id in selectedTagIds,
                                onClick = {
                                    selectedTagIds = if (tag.id in selectedTagIds) selectedTagIds - tag.id else selectedTagIds + tag.id
                                },
                                label = { Text(tag.name) },
                                leadingIcon = if (tag.id in selectedTagIds) { { Icon(Icons.Filled.Done, "Selected", Modifier.size(FilterChipDefaults.IconSize)) }} else null
                            )
                        }
                    }
                }
            }
            if (isTagsError) {
                Spacer(Modifier.height(4.dp))
                Text("Please select at least one tag", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.height(if (isTagsError) 24.dp else 32.dp))

            // --- Create Button ---
            val isLoading = creationState is CreateGroupUiState.Loading
            Button(
                onClick = {
                    keyboardController?.hide()
                    if (name.isNotBlank() && selectedTagIds.isNotEmpty()) {
                        // FIX: Pass selectedTagIds to the correct parameter
                        viewModel.createGroup(
                            name = name,
                            description = description,
                            meetingSchedule = meetingSchedule,
                            selectedTagIds = selectedTagIds.toList() // Pass the list of IDs
                        )
                    } else {
                        scope.launch { snackbarHostState.showSnackbar("Please enter a name and select at least one tag.") }
                    }
                },
                enabled = !isLoading && name.isNotBlank() && selectedTagIds.isNotEmpty(), // Add validation to enabled state
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp).padding(end = 8.dp))
                    Text("Creating...")
                } else {
                    Text("Create Group")
                }
            }
            // Display general error message below button if needed
            if (!isLoading && creationState is CreateGroupUiState.Error) {
                Spacer(modifier = Modifier.height(8.dp))
                // Text(text = (creationState as CreateGroupUiState.Error).message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) // Already shown in snackbar
            }
        }
    }
}