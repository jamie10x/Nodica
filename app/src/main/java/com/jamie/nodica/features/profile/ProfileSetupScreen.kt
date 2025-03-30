package com.jamie.nodica.features.profile

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jamie.nodica.features.navigation.Routes
import com.jamie.nodica.ui.theme.NodicaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber
import java.util.Locale

// --- Helper Composable: TimePickerField ---
// This composable encapsulates a time picker dialog.
// It displays the selected time and opens the TimePickerDialog on click.
@Composable
fun TimePickerField(
    label: String,
    selectedTime: String, // Displayed time as text (e.g., "09:30")
    onTimeSelected: (String) -> Unit
) {
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }

    // When showPicker is true, open a TimePickerDialog.
    if (showPicker) {
        // Parse the current time or use a default value.
        val initialHour = selectedTime.substringBefore(":", missingDelimiterValue = "9").toIntOrNull() ?: 9
        val initialMinute = selectedTime.substringAfter(":", "0").toIntOrNull() ?: 0
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                // Format selected time as "HH:mm"
                val formatted = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
                onTimeSelected(formatted)
                showPicker = false
            },
            initialHour,
            initialMinute,
            true
        ).show()
    }

    OutlinedButton(
        onClick = { showPicker = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (selectedTime.isBlank()) "Select a time" else selectedTime,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileSetupScreen(
    navController: NavController
) {
    val viewModel: ProfileViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    // --- Form state variables ---
    var name by rememberSaveable { mutableStateOf("") }
    var school by rememberSaveable { mutableStateOf("") }
    var preferredTime by rememberSaveable { mutableStateOf("") } // Now set via time picker
    var studyGoals by rememberSaveable { mutableStateOf("") }
    var selectedExistingTagIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var customTagNameInput by rememberSaveable { mutableStateOf("") }
    var enteredCustomTags by rememberSaveable { mutableStateOf(emptySet<String>()) }

    // --- Validation Hint Trigger ---
    val showValidationHints = uiState.profileStatus == ProfileStatus.SaveError

    // Derived error states for highlighting fields
    val nameIsError = name.isBlank() && showValidationHints
    val tagsAreError = (selectedExistingTagIds.isEmpty() && enteredCustomTags.isEmpty()) && showValidationHints

    // --- Side Effects ---
    LaunchedEffect(key1 = uiState.profileStatus) {
        when (uiState.profileStatus) {
            ProfileStatus.ProfileExists, ProfileStatus.SaveSuccess -> {
                val message = if (uiState.profileStatus == ProfileStatus.SaveSuccess) "Profile saved!" else "Profile setup skipped."
                Timber.i("$message Navigating to Home.")
                scope.launch { snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short) }
                delay(if (uiState.profileStatus == ProfileStatus.SaveSuccess) 500 else 100)
                navController.navigate(Routes.HOME) {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    launchSingleTop = true
                }
                if (uiState.profileStatus == ProfileStatus.SaveSuccess) { viewModel.acknowledgeSuccess() }
            }
            ProfileStatus.CriticalError -> {
                Timber.e("Critical error occurred during profile setup.")
            }
            else -> { /* No navigation for Loading, NeedsSetup, Saving, SaveError */ }
        }
    }

    LaunchedEffect(key1 = uiState.error) {
        uiState.error?.let { errorMsg ->
            scope.launch {
                snackbarHostState.showSnackbar(message = errorMsg, duration = SnackbarDuration.Long)
                viewModel.clearError()
            }
        }
    }

    // --- UI ---
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { CenterAlignedTopAppBar(title = { Text("Setup Your Profile") }) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    ) { paddingValues ->
        val currentStatus = uiState.profileStatus

        if (currentStatus == ProfileStatus.Loading) {
            Box(Modifier.fillMaxSize().padding(paddingValues), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Loading profile data...")
                }
            }
        } else if (currentStatus == ProfileStatus.CriticalError) {
            Box(Modifier.fillMaxSize().padding(paddingValues).padding(24.dp), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Error", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        uiState.error ?: "Cannot load profile information.",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Tell us about yourself", style = MaterialTheme.typography.titleLarge)
                Text("Starred (*) fields are required.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; viewModel.clearError() },
                    label = { Text("Name *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    isError = nameIsError,
                    supportingText = if (nameIsError) { { Text("Name cannot be empty", color = MaterialTheme.colorScheme.error) } } else null
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = school,
                    onValueChange = { school = it },
                    label = { Text("School / Institution") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next)
                )
                Spacer(modifier = Modifier.height(16.dp))

                // --- Time Picker Instead of Manual Input ---
                // Replace manual text input for Preferred Study Time with TimePickerField.
                TimePickerField(
                    label = "Preferred Study Time",
                    selectedTime = preferredTime,
                    onTimeSelected = { selected -> preferredTime = selected }
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = studyGoals,
                    onValueChange = { studyGoals = it },
                    label = { Text("What are your study goals?") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default)
                )
                Spacer(modifier = Modifier.height(28.dp))

                // --- Tags Section ---
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Select or Add Subjects & Interests *", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))

                when {
                    uiState.isLoadingTags -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Loading subjects...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    uiState.error?.contains("subjects/interests", ignoreCase = true) == true && uiState.availableTags.isEmpty() -> {
                        Text(uiState.error ?: "Could not load subjects.", color = MaterialTheme.colorScheme.error)
                    }
                    uiState.availableTags.isEmpty() -> {
                        Text("No suggested subjects found. Add your own below!", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> {
                        uiState.availableTags.forEach { (category, tagsInCategory) ->
                            Text(category, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                tagsInCategory.forEach { tag ->
                                    FilterChip(
                                        selected = tag.id in selectedExistingTagIds,
                                        onClick = { selectedExistingTagIds = if (tag.id in selectedExistingTagIds) selectedExistingTagIds - tag.id else selectedExistingTagIds + tag.id },
                                        label = { Text(tag.name) },
                                        leadingIcon = if (tag.id in selectedExistingTagIds) { { Icon(Icons.Filled.Done, "Selected", Modifier.size(FilterChipDefaults.IconSize)) } } else null
                                    )
                                }
                            }
                        }
                    }
                }
                if (tagsAreError) {
                    Spacer(Modifier.height(8.dp))
                    Text("Please select or add at least one subject/interest", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp))
                }
                Spacer(modifier = Modifier.height(20.dp))

                // --- Custom Tag Input ---
                Text("Add Custom Tags", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customTagNameInput,
                        onValueChange = { customTagNameInput = it },
                        label = { Text("Type a tag and press add") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            val trimmed = customTagNameInput.trim()
                            if (trimmed.isNotBlank() && trimmed.length <= 50 && !enteredCustomTags.any { it.equals(trimmed, ignoreCase = true) }) {
                                enteredCustomTags = enteredCustomTags + trimmed
                                customTagNameInput = ""
                            }
                            keyboardController?.hide()
                        })
                    )
                    IconButton(
                        onClick = {
                            val trimmed = customTagNameInput.trim()
                            if (trimmed.isNotBlank() && trimmed.length <= 50 && !enteredCustomTags.any { it.equals(trimmed, ignoreCase = true) }) {
                                enteredCustomTags = enteredCustomTags + trimmed
                                customTagNameInput = ""
                                keyboardController?.hide()
                            } else if (trimmed.length > 50) {
                                scope.launch { snackbarHostState.showSnackbar("Tag is too long (max 50 chars)") }
                            } else if (enteredCustomTags.any { it.equals(trimmed, ignoreCase = true) }) {
                                scope.launch { snackbarHostState.showSnackbar("Tag already added") }
                            }
                        },
                        enabled = customTagNameInput.isNotBlank(),
                        modifier = Modifier.padding(start = 8.dp)
                    ) { Icon(Icons.Filled.AddCircle, contentDescription = "Add Custom Tag") }
                }

                if (enteredCustomTags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        enteredCustomTags.sortedBy { it.lowercase() }.forEach { customTag ->
                            InputChip(
                                selected = false,
                                onClick = { enteredCustomTags = enteredCustomTags - customTag },
                                label = { Text(customTag) },
                                trailingIcon = { Icon(Icons.Filled.Cancel, contentDescription = "Remove $customTag Tag", Modifier.size(InputChipDefaults.IconSize)) }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))

                // --- Save Button ---
                val isSaving = currentStatus == ProfileStatus.Saving
                Button(
                    onClick = {
                        keyboardController?.hide()
                        viewModel.saveProfile(
                            name = name, school = school, preferredTime = preferredTime,
                            studyGoals = studyGoals, selectedExistingTagIds = selectedExistingTagIds.toList(),
                            newCustomTagNames = enteredCustomTags.toList()
                        )
                    },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp).padding(end = 8.dp))
                        Text("Saving...")
                    } else {
                        Text("Save Profile & Continue")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Preview(showBackground = true, name = "Profile Setup Preview")
@Composable
fun ProfileSetupScreenPreview() {
    NodicaTheme {
        ProfileSetupScreen(navController = NavController(LocalContext.current))
    }
}
