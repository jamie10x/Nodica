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
@Composable
fun TimePickerField(
    label: String,
    selectedTime: String, // Displayed time as text (e.g., "09:30")
    onTimeSelected: (String) -> Unit,
    enabled: Boolean = true // Added enabled parameter
) {
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }

    if (showPicker) {
        val initialHour = selectedTime.substringBefore(":", missingDelimiterValue = "9").toIntOrNull() ?: 9
        val initialMinute = selectedTime.substringAfter(":", "0").toIntOrNull() ?: 0
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                val formatted = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
                onTimeSelected(formatted)
                showPicker = false
            },
            initialHour,
            initialMinute,
            true // Use 24-hour format
        ).apply {
            // Handle dismiss action if needed
            setOnDismissListener { showPicker = false }
            show()
        }
    }

    // Use OutlinedButton for consistency with TextFields, or Button/TextButton as desired
    OutlinedButton(
        onClick = { if (enabled) showPicker = true }, // Only show picker if enabled
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled, // Apply enabled state to the button
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp) // Adjust padding
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween, // Align text and time
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label, // Use label directly
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) LocalContentColor.current else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) // Adjust color when disabled
            )
            Text(
                text = if (selectedTime.isBlank()) "Select Time" else selectedTime,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) LocalContentColor.current else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) // Adjust color when disabled
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class) // For FlowRow, imePadding
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
    // These hold the current values entered by the user in the form fields.
    var name by rememberSaveable { mutableStateOf("") }
    var school by rememberSaveable { mutableStateOf("") }
    var preferredTime by rememberSaveable { mutableStateOf("") }
    var studyGoals by rememberSaveable { mutableStateOf("") }
    var selectedExistingTagIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var customTagNameInput by rememberSaveable { mutableStateOf("") }
    var enteredCustomTags by rememberSaveable { mutableStateOf(emptySet<String>()) }

    // --- UI State & Derived Values ---
    val currentStatus = uiState.profileStatus
    val isLoadingOverall = currentStatus == ProfileStatus.Loading
    val isSaving = currentStatus == ProfileStatus.Saving
    val isCriticalError = currentStatus == ProfileStatus.CriticalError
    val fieldsEnabled = !isLoadingOverall && !isSaving && !isCriticalError // Enable fields when not loading/saving/error

    // Show validation hints only if a save attempt failed due to validation.
    val showValidationHints = currentStatus == ProfileStatus.SaveError

    // Derived error states for highlighting specific fields.
    val nameIsError = name.isBlank() && showValidationHints
    val tagsAreError = (selectedExistingTagIds.isEmpty() && enteredCustomTags.isEmpty()) && showValidationHints

    // --- Side Effects ---

    // Effect to handle navigation based on ProfileStatus changes from the ViewModel.
    LaunchedEffect(key1 = currentStatus) {
        when (currentStatus) {
            ProfileStatus.ProfileExists, ProfileStatus.SaveSuccess -> {
                // Profile exists or was just saved successfully, navigate to Home.
                val message = if (currentStatus == ProfileStatus.SaveSuccess) "Profile saved!" else "Profile setup skipped."
                Timber.i("ProfileSetupScreen: $message Navigating to Home.")
                // Show quick feedback
                scope.launch { snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short) }
                // Short delay to allow Snackbar visibility
                delay(if (currentStatus == ProfileStatus.SaveSuccess) 500 else 100)
                // Navigate to Home, clearing the setup/auth backstack.
                navController.navigate(Routes.HOME) {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true } // Pop everything up to the start
                    launchSingleTop = true // Avoid multiple Home instances
                }
                // No need to call viewModel.acknowledgeSuccess() as navigation replaces the screen.
            }
            ProfileStatus.CriticalError -> {
                Timber.e("ProfileSetupScreen: Observed CriticalError state.")
                // Error message is shown by the other LaunchedEffect.
            }
            else -> {
                // No navigation needed for Loading, NeedsSetup, Saving, SaveError states.
                Timber.d("ProfileSetupScreen: Observed ProfileStatus = $currentStatus, no navigation.")
            }
        }
    }

    // Effect to show error messages from the ViewModel in a Snackbar.
    LaunchedEffect(key1 = uiState.error) {
        uiState.error?.let { errorMsg ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = errorMsg,
                    duration = if (currentStatus == ProfileStatus.SaveError) SnackbarDuration.Short else SnackbarDuration.Long
                )
                // Clear the error in the ViewModel after showing it.
                viewModel.clearError()
            }
        }
    }

    // --- UI Composition ---
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Setup Your Profile") }
                // Optional: Add back button if needed, but flow usually prevents going back here easily.
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest // Base background
    ) { paddingValues ->

        // Handle Loading and Critical Error states fullscreen
        when {
            isLoadingOverall -> {
                Box(Modifier.fillMaxSize().padding(paddingValues), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Loading profile data...")
                    }
                }
            }
            isCriticalError -> {
                Box(Modifier.fillMaxSize().padding(paddingValues).padding(24.dp), Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Error", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            uiState.error ?: "Cannot load profile information.", // Show specific error if available
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        // Optional: Add a retry button here?
                    }
                }
            }
            // Otherwise, show the main form content
            else -> {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .padding(paddingValues) // Apply padding from Scaffold
                        .fillMaxSize()
                        .verticalScroll(scrollState) // Allow scrolling for form content
                        .padding(horizontal = 16.dp, vertical = 20.dp), // Inner padding for content
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
                        onValueChange = { name = it; viewModel.clearError() }, // Clear error on input change
                        label = { Text("Name *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next // Move to next field
                        ),
                        enabled = fieldsEnabled,
                        isError = nameIsError,
                        supportingText = if (nameIsError) {
                            { Text("Name cannot be empty", color = MaterialTheme.colorScheme.error) }
                        } else null
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = school,
                        onValueChange = { school = it },
                        label = { Text("School / Institution") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next
                        ),
                        enabled = fieldsEnabled
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Time Picker Field
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
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp), // Set min height
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Default // Default action (may show Done or Next depending on IME)
                        ),
                        enabled = fieldsEnabled
                    )
                    Spacer(modifier = Modifier.height(28.dp))

                    // --- Tags Section ---
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("Select or Add Subjects & Interests *", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Conditional rendering for tags based on loading/error/data states
                    when {
                        uiState.isLoadingTags -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Loading subjects...", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        // Check for tag-specific error and empty tags list
                        uiState.error?.contains("subjects/interests", ignoreCase = true) == true && uiState.availableTags.isEmpty() -> {
                            Text(uiState.error ?: "Could not load subjects.", color = MaterialTheme.colorScheme.error)
                        }
                        // No suggested tags available (after loading finished without error)
                        uiState.availableTags.isEmpty() && !uiState.isLoadingTags -> {
                            Text(
                                "No suggested subjects found. Add your own below!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Display available tags grouped by category
                        else -> {
                            uiState.availableTags.forEach { (category, tagsInCategory) ->
                                Text(
                                    category,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp).fillMaxWidth() // Ensure title takes full width
                                )
                                // Use FlowRow for chip layout that wraps
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp) // Spacing between rows of chips
                                ) {
                                    tagsInCategory.forEach { tag ->
                                        FilterChip(
                                            selected = tag.id in selectedExistingTagIds,
                                            onClick = {
                                                // Toggle selection state only if fields are enabled
                                                if(fieldsEnabled) {
                                                    selectedExistingTagIds = if (tag.id in selectedExistingTagIds) {
                                                        selectedExistingTagIds - tag.id
                                                    } else {
                                                        selectedExistingTagIds + tag.id
                                                    }
                                                    viewModel.clearError() // Clear validation error if selecting a tag fixes it
                                                }
                                            },
                                            label = { Text(tag.name) },
                                            leadingIcon = if (tag.id in selectedExistingTagIds) {
                                                { Icon(Icons.Filled.Done, "Selected", Modifier.size(FilterChipDefaults.IconSize)) }
                                            } else null,
                                            enabled = fieldsEnabled // Disable chips if form is not enabled
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // Display validation error for tags if applicable
                    if (tagsAreError) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Please select or add at least one subject/interest",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 4.dp) // Align with text field padding
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))

                    // --- Custom Tag Input ---
                    Text("Add Custom Tags", style = MaterialTheme.typography.titleMedium)
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customTagNameInput,
                            onValueChange = { customTagNameInput = it },
                            label = { Text("Type a tag and press add") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words,
                                imeAction = ImeAction.Done // Use Done action
                            ),
                            keyboardActions = KeyboardActions(onDone = {
                                val trimmed = customTagNameInput.trim()
                                if (trimmed.isNotBlank() && trimmed.length <= 50 && !enteredCustomTags.any { it.equals(trimmed, ignoreCase = true) }) {
                                    enteredCustomTags = enteredCustomTags + trimmed
                                    customTagNameInput = ""
                                    viewModel.clearError() // Clear validation error if adding a tag fixes it
                                }
                                keyboardController?.hide() // Hide keyboard on Done
                            }),
                            enabled = fieldsEnabled
                        )
                        IconButton(
                            onClick = {
                                val trimmed = customTagNameInput.trim()
                                if (trimmed.isNotBlank() && trimmed.length <= 50 && !enteredCustomTags.any { it.equals(trimmed, ignoreCase = true) }) {
                                    enteredCustomTags = enteredCustomTags + trimmed
                                    customTagNameInput = ""
                                    viewModel.clearError() // Clear validation error
                                    keyboardController?.hide()
                                } else if (trimmed.isBlank()) {
                                    scope.launch { snackbarHostState.showSnackbar("Tag cannot be empty") }
                                } else if (trimmed.length > 50) {
                                    scope.launch { snackbarHostState.showSnackbar("Tag is too long (max 50 chars)") }
                                } else { // Already added
                                    scope.launch { snackbarHostState.showSnackbar("Tag already added") }
                                }
                            },
                            enabled = customTagNameInput.isNotBlank() && fieldsEnabled,
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Icon(Icons.Filled.AddCircle, contentDescription = "Add Custom Tag")
                        }
                    }

                    // Display entered custom tags
                    if (enteredCustomTags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Sort alphabetically for consistent display
                            enteredCustomTags.sortedBy { it.lowercase() }.forEach { customTag ->
                                InputChip(
                                    selected = false, // Input chips aren't really "selected" here
                                    onClick = {
                                        if(fieldsEnabled) {
                                            enteredCustomTags = enteredCustomTags - customTag
                                        }
                                    }, // Click to remove
                                    label = { Text(customTag) },
                                    trailingIcon = { // Use trailing icon for remove action
                                        Icon(
                                            Icons.Filled.Cancel,
                                            contentDescription = "Remove $customTag Tag",
                                            Modifier.size(InputChipDefaults.IconSize)
                                        )
                                    },
                                    enabled = fieldsEnabled
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))

                    // --- Save Button ---
                    Button(
                        onClick = {
                            keyboardController?.hide() // Hide keyboard before saving
                            // Call ViewModel to save the profile data collected in the form state variables
                            viewModel.saveProfile(
                                name = name,
                                school = school,
                                preferredTime = preferredTime,
                                studyGoals = studyGoals,
                                selectedExistingTagIds = selectedExistingTagIds.toList(),
                                newCustomTagNames = enteredCustomTags.toList()
                            )
                        },
                        enabled = fieldsEnabled, // Button enabled only when fields are enabled (not loading/saving)
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        if (isSaving) {
                            Row(verticalAlignment = Alignment.CenterVertically) { // Align items in the button
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onPrimary, // Use contrast color
                                    modifier = Modifier.size(24.dp).padding(end = 8.dp) // Size and spacing
                                )
                                Text("Saving...")
                            }
                        } else {
                            Text("Save Profile & Continue")
                        }
                    }
                } // End Column for form content
            } // End Else block for showing form
        } // End When for Loading/Error/Form
    } // End Scaffold
}

// Preview Composable - Minimal setup for previewing the layout
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Preview(showBackground = true, name = "Profile Setup Preview")
@Composable
fun ProfileSetupScreenPreview() {
    NodicaTheme {
        // Provide a dummy NavController for the preview
        ProfileSetupScreen(navController = rememberNavController())
        // In a real preview, you might inject a dummy ViewModel using a PreviewParameterProvider
        // or configure Koin differently for previews if needed.
    }
}