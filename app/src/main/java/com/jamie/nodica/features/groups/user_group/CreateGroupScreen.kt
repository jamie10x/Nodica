package com.jamie.nodica.features.groups.user_group

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack // Import back arrow
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateGroupScreen(navController: NavController) {
    val viewModel: CreateGroupViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val availableTags = uiState.availableTags
    val isLoadingTags = uiState.isLoadingTags
    val operationState = uiState.opState // Specific state for the create action
    val generalError = uiState.error

// Form state
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var meetingSchedule by rememberSaveable { mutableStateOf("") }
    var selectedTagIds by rememberSaveable { mutableStateOf(emptySet<String>()) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

// Derived state for validation feedback
    val nameIsError = name.isBlank() && operationState == CreateGroupOpState.ValidationError
    val tagsAreError = selectedTagIds.isEmpty() && operationState == CreateGroupOpState.ValidationError

// --- Effects ---

// Navigate back after successful creation
    LaunchedEffect(operationState) {
        if (operationState is CreateGroupOpState.Success) {
            Timber.i("Group creation successful (UI Effect), navigating back.")
            scope.launch {
                snackbarHostState.showSnackbar("Group '${operationState.group.name}' created!")
            }
            // Delay slightly to let snackbar show before popping back stack
            kotlinx.coroutines.delay(400)
            navController.popBackStack()
            // Reset state in ViewModel AFTER navigation (or on dispose)
            // Consider calling viewModel.resetOperationState() in an onDispose block if needed.
        }
    }

// Show general errors (like tag loading) or creation errors
    LaunchedEffect(generalError, operationState) {
        val errorToShow = when {
            generalError != null -> generalError // Show general errors first
            operationState is CreateGroupOpState.Error -> operationState.message // Then creation errors
            operationState is CreateGroupOpState.ValidationError -> "Please check required fields." // Validation error msg
            else -> null
        }

        errorToShow?.let { message ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = message,
                    duration = SnackbarDuration.Long
                )
                // Reset appropriate state after showing error
                if (generalError != null) viewModel.clearError() else viewModel.resetOperationState()
            }
        }
    }


    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Create New Group") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            // --- Form Fields ---
            Text("Group Details", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Group Name *") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, capitalization = KeyboardCapitalization.Sentences),
                singleLine = true,
                isError = nameIsError
            )
            if (nameIsError) {
                Text("Name is required", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.height(if (nameIsError) 4.dp else 12.dp))

            OutlinedTextField(
                value = description, onValueChange = { description = it },
                label = { Text("Description") }, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, capitalization = KeyboardCapitalization.Sentences),
                maxLines = 4
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = meetingSchedule, onValueChange = { meetingSchedule = it },
                label = { Text("Meeting Schedule (Optional)") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), // Last field
                singleLine = true // Assume single line schedule for now
                // keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() })
            )
            Spacer(modifier = Modifier.height(24.dp))

            // --- Tag Selection ---
            Text("Select Tags *", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))

            when {
                isLoadingTags -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Loading tags...")
                    }
                }
                availableTags.isEmpty() && !isLoadingTags -> {
                    Text(
                        "Could not load tags. Please try again later.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {
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
                                        // Reset validation error state if user makes a selection
                                        if (operationState == CreateGroupOpState.ValidationError) viewModel.resetOperationState()
                                    },
                                    label = { Text(tag.name) },
                                    leadingIcon = if (tag.id in selectedTagIds) { { Icon(Icons.Filled.Done, "Selected", Modifier.size(FilterChipDefaults.IconSize)) }} else null
                                )
                            }
                        }
                    }
                    if (tagsAreError) {
                        Spacer(Modifier.height(4.dp))
                        Text("Please select at least one tag", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
            } // End Tag Section Logic

            Spacer(modifier = Modifier.height(if (tagsAreError) 24.dp else 32.dp))

            // --- Create Button ---
            val isCreating = operationState is CreateGroupOpState.Loading
            Button(
                onClick = {
                    keyboardController?.hide()
                    // ViewModel handles validation and state update
                    viewModel.createGroup(
                        name = name,
                        description = description,
                        meetingSchedule = meetingSchedule,
                        selectedTagIds = selectedTagIds.toList()
                    )
                },
                // Button is enabled if not currently creating (validation done in VM)
                enabled = !isCreating,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (isCreating) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp).padding(end = 8.dp))
                    Text("Creating...")
                } else {
                    Text("Create Group")
                }
            }
            Spacer(modifier = Modifier.height(16.dp)) // Bottom padding
        } // End Main Column
    } // End Scaffold
} // End Composable