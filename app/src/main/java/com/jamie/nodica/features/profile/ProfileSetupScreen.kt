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
import androidx.navigation.compose.rememberNavController // Added for Preview
import com.jamie.nodica.features.navigation.Routes
import com.jamie.nodica.ui.theme.NodicaTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber
import java.util.Locale

// --- Helper Composable: TimePickerField ---
/**
 * A composable that displays selected time and shows a TimePickerDialog on click.
 *
 * @param label The text label to display for the field.
 * @param selectedTime The currently selected time string (e.g., "HH:mm") or empty if none selected.
 * @param onTimeSelected Lambda function called with the selected time string ("HH:mm").
 * @param enabled Controls if the field is interactive.
 */
@Composable
fun TimePickerField(
    label: String,
    selectedTime: String,
    onTimeSelected: (String) -> Unit,
    enabled: Boolean = true
) {
    val context = LocalContext.current
    // State to control the visibility of the TimePickerDialog
    var showPicker by remember { mutableStateOf(false) }

    // Show the TimePickerDialog when showPicker becomes true
    if (showPicker) {
        // Determine initial values for the picker
        val initialHour = selectedTime.substringBefore(":", "9").toIntOrNull() ?: 9 // Default to 9 AM
        val initialMinute = selectedTime.substringAfter(":", "0").toIntOrNull() ?: 0  // Default to 00 minutes

        // Create and show the dialog
        TimePickerDialog(
            context,
            { _, hourOfDay, minute -> // Listener for time selection
                // Format the selected time into "HH:mm" string
                val formattedTime = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
                onTimeSelected(formattedTime) // Call the callback with the formatted time
                showPicker = false // Hide the picker after selection
            },
            initialHour,
            initialMinute,
            true // Use 24-hour format
        ).apply {
            // Set a listener to ensure showPicker is false if the dialog is dismissed
            setOnDismissListener { showPicker = false }
            show() // Display the dialog
        }
    }

    // The button that triggers the picker
    OutlinedButton(
        onClick = { if (enabled) showPicker = true }, // Show picker only if enabled
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled, // Apply enabled state to the button appearance
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp) // Adjusted padding for better look
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween, // Space out label and time
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Display the label
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                // Adjust color based on enabled state
                color = if (enabled) LocalContentColor.current else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
            // Display the selected time or placeholder text
            Text(
                text = if (selectedTime.isBlank()) "Select Time" else selectedTime,
                style = MaterialTheme.typography.bodyLarge,
                // Adjust color based on enabled state
                color = if (enabled) LocalContentColor.current else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        }
    }
}


// --- Main Screen Composable ---
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileSetupScreen(
    navController: NavController
) {
    // Obtain ViewModel using Koin
    val viewModel: ProfileViewModel = koinViewModel()
    // Collect UI state from ViewModel
    val uiState by viewModel.uiState.collectAsState()
    // State for managing Snackbars
    val snackbarHostState = remember { SnackbarHostState() }
    // Coroutine scope for launching effects
    val scope = rememberCoroutineScope()
    // Controller for managing the software keyboard
    val keyboardController = LocalSoftwareKeyboardController.current

    // --- Form State Variables ---
    // Use rememberSaveable to preserve state across configuration changes/process death
    var name by rememberSaveable { mutableStateOf("") }
    var school by rememberSaveable { mutableStateOf("") }
    var preferredTime by rememberSaveable { mutableStateOf("") }
    var studyGoals by rememberSaveable { mutableStateOf("") }
    var selectedExistingTagIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var customTagNameInput by rememberSaveable { mutableStateOf("") }
    var enteredCustomTags by rememberSaveable { mutableStateOf(emptySet<String>()) }

    // --- UI State & Derived Values ---
    val currentStatus = uiState.profileStatus
    val isLoading = uiState.isLoading // Reflects initial/tag loading
    val isSaving = currentStatus == ProfileStatus.Saving
    val isCriticalError = currentStatus == ProfileStatus.CriticalError
    // Determine if form fields should be interactive
    val fieldsEnabled = !isLoading && !isSaving && !isCriticalError

    // Determine if validation error hints should be shown
    val showValidationHints = currentStatus == ProfileStatus.SaveError
    // Specific error flags for highlighting fields
    val nameIsError = name.isBlank() && showValidationHints
    val tagsAreError = (selectedExistingTagIds.isEmpty() && enteredCustomTags.isEmpty()) && showValidationHints

    // --- Side Effects ---

    // Handle navigation triggered by ViewModel status changes
    LaunchedEffect(key1 = currentStatus) {
        when (currentStatus) {
            ProfileStatus.SaveSuccess -> {
                Timber.i("ProfileSetupScreen: SaveSuccess detected. Navigating to Home.")
                scope.launch { snackbarHostState.showSnackbar("Profile saved!", duration = SnackbarDuration.Short) }
                delay(500) // Allow time to see Snackbar
                navController.navigate(Routes.HOME) { // Navigate to Home screen
                    popUpTo(navController.graph.startDestinationId) { inclusive = true } // Clear back stack
                    launchSingleTop = true
                }
            }
            // ProfileExists is primarily handled by SplashViewModel directing away,
            // but if reached here, navigate away as well.
            ProfileStatus.ProfileExists -> {
                Timber.i("ProfileSetupScreen: ProfileExists detected. Navigating to Home.")
                // No Snackbar needed usually, just navigate
                navController.navigate(Routes.HOME) {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    launchSingleTop = true
                }
            }
            ProfileStatus.CriticalError -> {
                Timber.e("ProfileSetupScreen: Observed CriticalError state.")
                // Error message shown via the other LaunchedEffect.
            }
            else -> {
                // No navigation for NeedsSetup, Saving, SaveError
                Timber.d("ProfileSetupScreen: Observed status = $currentStatus.")
            }
        }
    }

    // Show error messages from ViewModel in a Snackbar
    LaunchedEffect(key1 = uiState.error) {
        uiState.error?.let { errorMsg ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = errorMsg,
                    // Show SaveErrors briefly, others longer
                    duration = if (currentStatus == ProfileStatus.SaveError) SnackbarDuration.Short else SnackbarDuration.Long
                )
                // Clear error in ViewModel after it's shown
                viewModel.clearError()
            }
        }
    }

    // --- UI Composition ---
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(title = { Text("Setup Your Profile") })
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest // Set background
    ) { paddingValues ->

        // Display content based on the current loading/error/form state
        when {
            // Loading indicator (shown during initial load / tag fetch)
            isLoading -> {
                Box(
                    Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Loading setup...")
                    }
                }
            }
            // Critical error display
            isCriticalError -> {
                Box(
                    Modifier.fillMaxSize().padding(paddingValues).padding(24.dp), // Extra padding for error message
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Error", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            uiState.error ?: "An critical error occurred.",
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        // Consider adding a retry mechanism/button here
                    }
                }
            }
            // Form display
            else -> {
                val scrollState = rememberScrollState() // Remember scroll state for the column
                Column(
                    modifier = Modifier
                        .padding(paddingValues) // Apply padding from Scaffold
                        .fillMaxSize()
                        .verticalScroll(scrollState) // Make the form scrollable
                        .padding(horizontal = 16.dp, vertical = 20.dp), // Inner content padding
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Tell us about yourself", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Starred (*) fields are required.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    // --- Form Fields ---
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; viewModel.clearError() },
                        label = { Text("Name *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                        enabled = fieldsEnabled,
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
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                        enabled = fieldsEnabled
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    TimePickerField(
                        label = "Preferred Study Time",
                        selectedTime = preferredTime,
                        onTimeSelected = { selected -> preferredTime = selected },
                        enabled = fieldsEnabled
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = studyGoals,
                        onValueChange = { studyGoals = it },
                        label = { Text("What are your study goals?") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default),
                        enabled = fieldsEnabled
                    )
                    Spacer(modifier = Modifier.height(28.dp))

                    // --- Tags Section ---
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("Select or Add Subjects & Interests *", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))

                    when {
                        uiState.isLoadingTags -> { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Loading subjects...") }}
                        uiState.error?.contains("subjects/interests") == true && uiState.availableTags.isEmpty() -> { Text(uiState.error ?: "Could not load subjects.", color = MaterialTheme.colorScheme.error) }
                        uiState.availableTags.isEmpty() && !uiState.isLoadingTags -> { Text("No suggested subjects found. Add your own below!", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        else -> {
                            uiState.availableTags.forEach { (category, tagsInCategory) ->
                                Text(category, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp).fillMaxWidth())
                                FlowRow( modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp) ) {
                                    tagsInCategory.forEach { tag ->
                                        FilterChip(
                                            selected = tag.id in selectedExistingTagIds,
                                            onClick = { if(fieldsEnabled) { selectedExistingTagIds = if (tag.id in selectedExistingTagIds) selectedExistingTagIds - tag.id else selectedExistingTagIds + tag.id; viewModel.clearError() } },
                                            label = { Text(tag.name) },
                                            leadingIcon = if (tag.id in selectedExistingTagIds) { { Icon(Icons.Filled.Done, "Selected", Modifier.size(FilterChipDefaults.IconSize)) } } else null,
                                            enabled = fieldsEnabled
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (tagsAreError) { Spacer(Modifier.height(8.dp)); Text("Please select or add at least one subject/interest", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp)) }
                    Spacer(modifier = Modifier.height(20.dp))

                    // --- Custom Tag Input ---
                    Text("Add Custom Tags", style = MaterialTheme.typography.titleMedium)
                    Row( Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically ) {
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
                                    enteredCustomTags += trimmed; customTagNameInput = ""; viewModel.clearError()
                                }
                                keyboardController?.hide()
                            }),
                            enabled = fieldsEnabled
                        )
                        IconButton(
                            onClick = {
                                val trimmed = customTagNameInput.trim()
                                if (trimmed.isNotBlank() && trimmed.length <= 50 && !enteredCustomTags.any { it.equals(trimmed, ignoreCase = true) }) {
                                    enteredCustomTags += trimmed; customTagNameInput = ""; viewModel.clearError(); keyboardController?.hide()
                                } else if (trimmed.isBlank()) { scope.launch { snackbarHostState.showSnackbar("Tag cannot be empty") }
                                } else if (trimmed.length > 50) { scope.launch { snackbarHostState.showSnackbar("Tag is too long (max 50 chars)") }
                                } else { scope.launch { snackbarHostState.showSnackbar("Tag already added") } }
                            },
                            enabled = customTagNameInput.isNotBlank() && fieldsEnabled,
                            modifier = Modifier.padding(start = 8.dp)
                        ) { Icon(Icons.Filled.AddCircle, contentDescription = "Add Custom Tag") }
                    } // End Row
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
                                    onClick = { if(fieldsEnabled) enteredCustomTags -= customTag }, // Remove tag on click
                                    label = { Text(customTag) },
                                    trailingIcon = { Icon(Icons.Filled.Cancel, "Remove $customTag Tag", Modifier.size(InputChipDefaults.IconSize)) },
                                    enabled = fieldsEnabled
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))

                    // --- Save Button ---
                    Button(
                        onClick = {
                            keyboardController?.hide() // Hide keyboard first
                            viewModel.saveProfile(
                                name = name,
                                school = school,
                                preferredTime = preferredTime,
                                studyGoals = studyGoals,
                                selectedExistingTagIds = selectedExistingTagIds.toList(),
                                newCustomTagNames = enteredCustomTags.toList()
                            )
                        },
                        enabled = fieldsEnabled, // Enable button when form is enabled
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        if (isSaving) { // Show saving indicator
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp).padding(end = 8.dp))
                                Text("Saving...")
                            }
                        } else { // Show normal text
                            Text("Save Profile & Continue")
                        }
                    } // End Button
                } // End Form Column
            } // End Else
        } // End When
    } // End Scaffold
}

// --- Preview (Unchanged) ---
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Preview(showBackground = true, name = "Profile Setup Preview")
@Composable
fun ProfileSetupScreenPreview() {
    NodicaTheme {
        ProfileSetupScreen(navController = rememberNavController())
    }
}