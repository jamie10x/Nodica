package com.jamie.nodica.features.groups.group // <-- ***** CORRECT PACKAGE *****

// Necessary Imports (Explicitly added)
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateGroupScreen(navController: NavController) {
    // Koin should find the ViewModel defined for this scope/package
    val viewModel: CreateGroupViewModel = koinViewModel()
    // collectAsState should now work with the explicit import
    val uiState by viewModel.uiState.collectAsState()

    // Accessing state properties - should resolve now
    val availableTags = uiState.availableTags
    val isLoadingTags = uiState.isLoadingTags
    val operationState = uiState.opState
    val generalError = uiState.error

    // Form state remains the same
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var meetingSchedule by rememberSaveable { mutableStateOf("") }
    var selectedTagIds by rememberSaveable { mutableStateOf(emptySet<String>()) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Validation & Derived state - should resolve state classes now
    val showValidationHints = operationState is CreateGroupOpState.ValidationError
    val nameIsError = name.isBlank() && showValidationHints
    val tagsAreError = selectedTagIds.isEmpty() && showValidationHints
    val validationErrorMessage = (operationState as? CreateGroupOpState.ValidationError)?.message

    // Effects - references to state classes and viewModel functions should resolve
    LaunchedEffect(operationState) {
        if (operationState is CreateGroupOpState.Success) {
            Timber.i("Group creation successful (UI Effect), navigating back.")
            scope.launch {
                snackbarHostState.showSnackbar("Group '${operationState.group.name}' created!")
            }
            delay(400)
            navController.popBackStack()
            viewModel.resetOperationState()
        }
    }

    LaunchedEffect(generalError, operationState) {
        val errorToShow = validationErrorMessage ?: generalError ?: (operationState as? CreateGroupOpState.Error)?.message
        errorToShow?.let { message ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = message,
                    duration = if(operationState is CreateGroupOpState.ValidationError) SnackbarDuration.Short else SnackbarDuration.Long
                )
                if (generalError != null) viewModel.clearError() else viewModel.resetOperationState()
            }
        }
    }

    // UI Scaffold and Column - references should resolve
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Create New Study Group") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            // All UI elements... references to uiState, operationState, etc. should work.

            Text("Group Details", style = MaterialTheme.typography.titleLarge)
            Text("Starred (*) fields are required.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it; viewModel.resetOperationState() }, // Reference should resolve
                label = { Text("Group Name *") }, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, capitalization = KeyboardCapitalization.Sentences),
                singleLine = true,
                isError = nameIsError,
                supportingText = if(nameIsError) { { Text("Name is required", color = MaterialTheme.colorScheme.error) } } else null
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = description, onValueChange = { description = it },
                label = { Text("Description (Goals, Topics, etc.)") }, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, capitalization = KeyboardCapitalization.Sentences),
                maxLines = 4
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = meetingSchedule, onValueChange = { meetingSchedule = it },
                label = { Text("Meeting Schedule (Optional)") }, placeholder = {Text("e.g., Tuesdays 7 PM UTC, Weekends")},
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Select Tags *", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))

            when {
                isLoadingTags -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Loading subjects...")
                    }
                }
                (generalError != null && availableTags.isEmpty()) -> {
                    Text(
                        generalError,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                availableTags.isEmpty() && !isLoadingTags -> {
                    Text(
                        "No suggested subjects available.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> { // Display Tags
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
                                        if (operationState is CreateGroupOpState.ValidationError) viewModel.resetOperationState()
                                    },
                                    label = { Text(tag.name) },
                                    leadingIcon = if (tag.id in selectedTagIds) { { Icon(Icons.Filled.Done, "Selected", Modifier.size(FilterChipDefaults.IconSize)) }} else null
                                )
                            }
                        }
                    }
                    if (tagsAreError) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            validationErrorMessage ?: "Please select at least one tag",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            val isCreating = operationState is CreateGroupOpState.Loading
            val canProceed = !(availableTags.isEmpty() && !isLoadingTags && generalError != null)

            Button(
                onClick = {
                    keyboardController?.hide()
                    viewModel.createGroup(
                        name = name, description = description,
                        meetingSchedule = meetingSchedule, selectedTagIds = selectedTagIds.toList()
                    )
                },
                enabled = !isCreating && canProceed,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (isCreating) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Creating Group...")
                    }
                } else {
                    Text("Create Group")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CreateGroupScreenPreview(){
    NodicaTheme {
        CreateGroupScreen(rememberNavController())
    }
}