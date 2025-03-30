// main/java/com/jamie/nodica/features/groups/group/CreateGroupScreen.kt
package com.jamie.nodica.features.groups.group

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle // Success Icon
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.jamie.nodica.ui.theme.NodicaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalLayoutApi::class) // FlowRow is Experimental
@Composable
fun CreateGroupScreen(navController: NavController) {
    // Obtain ViewModel using Koin
    val viewModel: CreateGroupViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()

    // Extract state properties for clarity
    val availableTags = uiState.availableTags
    val isLoadingTags = uiState.isLoadingTags
    val operationState = uiState.opState
    val generalError = uiState.error // Errors not related to the create operation itself (e.g., tag loading)

    // Form state variables preserved across configuration changes
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var meetingSchedule by rememberSaveable { mutableStateOf("") }
    var selectedTagIds by rememberSaveable { mutableStateOf(emptySet<String>()) }

    // UI helpers
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    // --- Input Validation Feedback ---
    // Check if the current state indicates a validation error was found by the ViewModel
    val isValidationError = operationState is CreateGroupOpState.ValidationError
    val validationErrorMessage = (operationState as? CreateGroupOpState.ValidationError)?.message
    // Determine if specific fields are in error based on validation state
    val nameIsError = name.isBlank() && isValidationError && validationErrorMessage?.contains("name", ignoreCase = true) == true
    val tagsAreError = selectedTagIds.isEmpty() && isValidationError && validationErrorMessage?.contains("tag", ignoreCase = true) == true

    // --- Side Effects ---

    // Effect to handle navigation or UI feedback upon successful group creation
    LaunchedEffect(operationState) {
        if (operationState is CreateGroupOpState.Success) {
            Timber.i("CreateGroupScreen: Success state observed. Group '${operationState.group.name}' created.")
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "Group '${operationState.group.name}' created successfully!",
                    duration = SnackbarDuration.Short,
                    withDismissAction = true
                )
            }
            // Give user time to see snackbar before navigating back
            delay(600L) // Adjust delay as needed
            navController.popBackStack() // Navigate back to the previous screen
            viewModel.resetOperationState() // Reset state after handling
        }
    }

    // Effect to show Snackbar messages for general errors or operation errors
    LaunchedEffect(generalError, operationState) {
        // Prioritize operation errors, then general errors
        val errorToShow = (operationState as? CreateGroupOpState.Error)?.message
            ?: (operationState as? CreateGroupOpState.ValidationError)?.message // Show validation errors briefly
            ?: generalError

        errorToShow?.let { message ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = message,
                    duration = if (operationState is CreateGroupOpState.ValidationError) SnackbarDuration.Short else SnackbarDuration.Long
                )
                // Clear the specific error source in the ViewModel
                if (generalError != null) viewModel.clearError()
                // Always reset operation state if it was an error/validation state
                if (operationState is CreateGroupOpState.Error || operationState is CreateGroupOpState.ValidationError) {
                    viewModel.resetOperationState()
                }
            }
        }
    }

    // --- UI Composition ---
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Create New Study Group") },
                navigationIcon = {
                    // Only navigate up if not currently creating the group
                    IconButton(onClick = {
                        if (operationState !is CreateGroupOpState.Loading) {
                            navController.navigateUp()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(paddingValues) // Apply padding from Scaffold
                .fillMaxSize()
                .verticalScroll(scrollState) // Make content scrollable
                .padding(horizontal = 16.dp, vertical = 20.dp), // Inner padding
        ) {
            // Group Details Section Header
            Text("Group Details", style = MaterialTheme.typography.titleLarge)
            Text(
                "Starred (*) fields are required.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            // --- Form Fields ---
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    // Reset validation state when user types
                    if (operationState is CreateGroupOpState.ValidationError) viewModel.resetOperationState()
                },
                label = { Text("Group Name *") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, capitalization = KeyboardCapitalization.Sentences),
                singleLine = true,
                isError = nameIsError, // Highlight field if specific validation error occurred
                supportingText = if (nameIsError) { { Text(validationErrorMessage, color = MaterialTheme.colorScheme.error) } } else null,
                enabled = operationState !is CreateGroupOpState.Loading // Disable when creating
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (Goals, Topics, etc.)") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 160.dp), // Set min/max height
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, capitalization = KeyboardCapitalization.Sentences),
                maxLines = 5, // Allow multiple lines
                enabled = operationState !is CreateGroupOpState.Loading
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = meetingSchedule,
                onValueChange = { meetingSchedule = it },
                label = { Text("Meeting Schedule (Optional)") },
                placeholder = { Text("e.g., Tuesdays 7 PM UTC, Weekends") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences),
                keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }), // Hide keyboard on Done
                singleLine = true,
                enabled = operationState !is CreateGroupOpState.Loading
            )

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) // Visual separator
            Text("Select Tags *", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))

            // --- Tags Selection Section ---
            when {
                isLoadingTags -> { // Show loading indicator for tags
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Loading subjects...")
                    }
                }
                // Show general error if tag loading failed and list is empty
                generalError != null && availableTags.isEmpty() -> {
                    Text(generalError, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
                }
                // Message if no tags were found after loading successfully
                availableTags.isEmpty() && !isLoadingTags -> {
                    Text("No suggested subjects available.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                }
                // Display tags grouped by category
                else -> {
                    availableTags.forEach { (category, tagsInCategory) ->
                        // Category Header
                        Text(category, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                        // FlowRow for chips
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp), // Spacing between chips
                            verticalArrangement = Arrangement.spacedBy(4.dp)   // Spacing between rows
                        ) {
                            tagsInCategory.forEach { tag ->
                                FilterChip(
                                    selected = tag.id in selectedTagIds,
                                    onClick = {
                                        // Toggle selection only if not loading/creating
                                        if (operationState !is CreateGroupOpState.Loading) {
                                            selectedTagIds = if (tag.id in selectedTagIds) selectedTagIds - tag.id else selectedTagIds + tag.id
                                            // Reset validation state if user modifies selection
                                            if (operationState is CreateGroupOpState.ValidationError) viewModel.resetOperationState()
                                        }
                                    },
                                    label = { Text(tag.name) },
                                    leadingIcon = if (tag.id in selectedTagIds) {
                                        { Icon(Icons.Filled.Done, "Selected", Modifier.size(FilterChipDefaults.IconSize)) }
                                    } else null,
                                    enabled = operationState !is CreateGroupOpState.Loading // Disable chip interaction while creating
                                )
                            }
                        } // End FlowRow
                    } // End ForEach Category
                    // Display validation error specific to tags if applicable
                    if (tagsAreError) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            validationErrorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                } // End Else (Display Tags)
            } // End When (Tags Loading/Error/Display)

            Spacer(modifier = Modifier.height(32.dp)) // Space before the button

            // --- Create Button ---
            val isButtonEnabled = operationState !is CreateGroupOpState.Loading && operationState !is CreateGroupOpState.Success && generalError == null && !isLoadingTags
            Button(
                onClick = {
                    keyboardController?.hide() // Hide keyboard before action
                    viewModel.createGroup(
                        name = name,
                        description = description,
                        meetingSchedule = meetingSchedule,
                        selectedTagIds = selectedTagIds.toList() // Pass selected IDs
                    )
                },
                enabled = isButtonEnabled, // Disable during loading/success or if tags failed to load
                modifier = Modifier.fillMaxWidth().height(48.dp) // Standard button height
            ) {
                // Show different content based on operation state
                when (operationState) {
                    is CreateGroupOpState.Loading -> { // Loading indicator
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary, // Ensure contrast
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Creating Group...")
                        }
                    }
                    is CreateGroupOpState.Success -> { // Success indicator (briefly shown due to navigation)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = "Success")
                            Spacer(Modifier.width(8.dp))
                            Text("Group Created!")
                        }
                    }
                    is CreateGroupOpState.Error -> { // Show error icon temporarily? (Snackbar handles message)
                        // Text("Creation Failed") // Or rely solely on Snackbar
                        Text("Create Group") // Revert to default text after error shown
                    }
                    else -> { // Idle or ValidationError
                        Text("Create Group")
                    }
                } // End When (Button Content)
            } // End Button
            Spacer(modifier = Modifier.height(16.dp)) // Bottom padding
        } // End Column
    } // End Scaffold
}

// --- Preview ---
@Preview(showBackground = true)
@Composable
fun CreateGroupScreenPreview() {
    NodicaTheme {
        // Use rememberNavController for preview, real app uses injected controller
        CreateGroupScreen(navController = rememberNavController())
    }
}