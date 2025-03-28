// Fully Refined & Robust: main/java/com/jamie/nodica/features/profile/ProfileSetupScreen.kt

package com.jamie.nodica.features.profile

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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileSetupScreen(
    navController: NavController // Used in LaunchedEffect for navigation
) {
    val viewModel: ProfileViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current // Used in Button onClick and KeyboardActions

    // --- Form state variables ---
    var name by rememberSaveable { mutableStateOf("") }
    var school by rememberSaveable { mutableStateOf("") }
    var preferredTime by rememberSaveable { mutableStateOf("") }
    var studyGoals by rememberSaveable { mutableStateOf("") }
    var selectedExistingTagIds by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var customTagNameInput by rememberSaveable { mutableStateOf("") } // Used in custom tag TextField
    var enteredCustomTags by rememberSaveable { mutableStateOf(emptySet<String>()) } // Used to display/remove custom tags

    // --- Derived validation state ---
    val nameIsError = name.isBlank() && uiState.profileStatus == ProfileStatus.SaveError
    val tagsAreError = (selectedExistingTagIds.isEmpty() && enteredCustomTags.isEmpty()) && uiState.profileStatus == ProfileStatus.SaveError

    // --- Effects ---
    LaunchedEffect(key1 = uiState.profileStatus) { // key1 for clarity
        when (uiState.profileStatus) {
            ProfileStatus.ProfileExists, ProfileStatus.SaveSuccess -> {
                val message = if (uiState.profileStatus == ProfileStatus.SaveSuccess) "Profile saved!" else "Profile already exists."
                Timber.i("$message Navigating to Home.")
                scope.launch { snackbarHostState.showSnackbar(message) }
                delay(if (uiState.profileStatus == ProfileStatus.SaveSuccess) 500 else 100) // Shorter delay if just skipping
                navController.navigate(Routes.HOME) {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    launchSingleTop = true
                }
            }
            else -> { /* No navigation for Loading, NeedsSetup, Saving, SaveError */ }
        }
    }

    LaunchedEffect(key1 = uiState.error) { // key1 for clarity
        uiState.error?.let { errorMsg ->
            scope.launch {
                snackbarHostState.showSnackbar(message = errorMsg, duration = SnackbarDuration.Long)
                viewModel.clearError() // Clear error after display
            }
        }
    }

    // --- UI ---
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { CenterAlignedTopAppBar(title = { Text("Setup Your Profile") }) },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
    ) { paddingValues ->

        val currentStatus = uiState.profileStatus // Cache current status

        // --- Full Screen States (Loading / Critical Error) ---
        if (currentStatus == ProfileStatus.Loading) {
            Box(Modifier.fillMaxSize().padding(paddingValues), Alignment.Center) {
                CircularProgressIndicator()
                Spacer(Modifier.height(8.dp))
                Text("Loading...")
            }
        } else if (uiState.error != null && currentStatus != ProfileStatus.NeedsSetup && currentStatus != ProfileStatus.SaveError) {
            Box(Modifier.fillMaxSize().padding(paddingValues).padding(16.dp), Alignment.Center) {
                Text("Error loading profile information. Please restart the app.", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
        }
        // --- Form Display State (NeedsSetup or SaveError allows showing the form) ---
        else if (currentStatus == ProfileStatus.NeedsSetup || currentStatus == ProfileStatus.SaveError) {
            Column(
                modifier = Modifier
                    .padding(paddingValues) // Apply scaffold padding first
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()) // Then enable scrolling
                    // Then apply content padding
                    .padding(start = 16.dp, end = 16.dp, bottom = 32.dp, top = 20.dp)
            ) {
                // --- Core Profile Fields ---
                Text("Tell us about yourself", style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name *") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next), isError = nameIsError)
                if (nameIsError) { Text("Name cannot be empty", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
                Spacer(modifier = Modifier.height(if (nameIsError) 4.dp else 16.dp))

                OutlinedTextField(value = school, onValueChange = { school = it }, label = { Text("School / Institution") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next))
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(value = preferredTime, onValueChange = { preferredTime = it }, label = { Text("Preferred Study Time") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(value = studyGoals, onValueChange = { studyGoals = it }, label = { Text("Study Goals") }, modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp), maxLines = 5, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default)) // Changed ImeAction
                Spacer(modifier = Modifier.height(28.dp))

                // --- Tags Section ---
                Text("Select or Add Subjects & Interests *", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))

                // Display Logic for Available Tags
                when {
                    uiState.availableTags.isEmpty() && uiState.error?.contains("subjects/interests") == false -> {
                        Text("Loading subjects...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    uiState.error?.contains("subjects/interests") == true -> {
                        Text(uiState.error ?: "Could not load subjects.", color = MaterialTheme.colorScheme.error)
                    }
                    uiState.availableTags.isEmpty() -> {
                        // Handles case where fetch succeeded but returned 0 tags (after loading is done)
                        Text("No suggested subjects found. Add your own below!", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> { // Display Chips if tags are available
                        uiState.availableTags.forEach { (category, tagsInCategory) ->
                            Text(category, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                tagsInCategory.forEach { tag ->
                                    FilterChip(
                                        selected = tag.id in selectedExistingTagIds,
                                        onClick = { selectedExistingTagIds = if (tag.id in selectedExistingTagIds) selectedExistingTagIds - tag.id else selectedExistingTagIds + tag.id },
                                        label = { Text(tag.name) },
                                        leadingIcon = if (tag.id in selectedExistingTagIds) { { Icon(Icons.Filled.Done, "Selected", Modifier.size(FilterChipDefaults.IconSize)) }} else null
                                    )
                                }
                            }
                        }
                    }
                } // End When for available tags display
                Spacer(modifier = Modifier.height(20.dp))

                // --- Custom Tag Input ---
                Text("Add Custom Tags", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customTagNameInput,
                        onValueChange = { customTagNameInput = it },
                        label = { Text("Type a tag...") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done), // Use Done action
                        keyboardActions = KeyboardActions(onDone = { // Action on pressing Done
                            val trimmed = customTagNameInput.trim()
                            if (trimmed.isNotBlank() && trimmed.length <= 50) {
                                enteredCustomTags = enteredCustomTags + trimmed
                                customTagNameInput = "" // Clear input
                            }
                            keyboardController?.hide() // Hide keyboard
                        })
                    )
                    IconButton(
                        onClick = { // Action on clicking button
                            val trimmed = customTagNameInput.trim()
                            if (trimmed.isNotBlank() && trimmed.length <= 50) {
                                enteredCustomTags = enteredCustomTags + trimmed
                                customTagNameInput = ""
                            }
                        },
                        enabled = customTagNameInput.isNotBlank(),
                        modifier = Modifier.padding(start = 8.dp)
                    ) { Icon(Icons.Filled.AddCircle, contentDescription = "Add Custom Tag") }
                }

                // --- Display Entered Custom Tags ---
                if (enteredCustomTags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Your Custom Tags:", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Sort alphabetically for consistent display
                        enteredCustomTags.sorted().forEach { customTag ->
                            InputChip(
                                selected = false,
                                onClick = { enteredCustomTags = enteredCustomTags - customTag },
                                label = { Text(customTag) },
                                trailingIcon = { Icon(Icons.Filled.Cancel, contentDescription = "Remove $customTag Tag", Modifier.size(InputChipDefaults.IconSize)) }
                            )
                        }
                    }
                }

                // --- Validation Error Message for Tags ---
                if (tagsAreError) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Please select or add at least one subject/interest", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(modifier = Modifier.height(if (tagsAreError) 28.dp else 32.dp)) // Adjusted spacing

                // --- Save Button ---
                val isSaving = currentStatus == ProfileStatus.Saving
                Button(
                    onClick = {
                        keyboardController?.hide()
                        if (name.isNotBlank() && (selectedExistingTagIds.isNotEmpty() || enteredCustomTags.isNotEmpty())) {
                            viewModel.saveProfile(
                                name = name, school = school, preferredTime = preferredTime,
                                studyGoals = studyGoals, selectedExistingTagIds = selectedExistingTagIds.toList(),
                                newCustomTagNames = enteredCustomTags.toList()
                            )
                        } else {
                            scope.launch { snackbarHostState.showSnackbar("Please enter your name and select/add at least one interest.") }
                            // Explicitly update the state to trigger validation hint re-evaluation
                            viewModel._uiState.update { it.copy(profileStatus = ProfileStatus.SaveError) }
                        }
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
            } // End Form Column
        } // End NeedsSetup/SaveError state
    } // End Scaffold
}

// --- Preview --- (Fixed usage of preview variables)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Preview(showBackground = true, name = "Profile Setup Preview")
@Composable
fun ProfileSetupScreenPreview() {
    NodicaTheme {
        var name by rememberSaveable { mutableStateOf("Jane Doe") }
        var school by rememberSaveable { mutableStateOf("University Example") }
        var time by rememberSaveable { mutableStateOf("Evenings") }
        var goals by rememberSaveable { mutableStateOf("Pass exams") }
        var selectedExisting by rememberSaveable { mutableStateOf(setOf("tag_2")) }
        var customInput by rememberSaveable { mutableStateOf("") }
        var customTags by rememberSaveable { mutableStateOf(setOf("Kotlin", "Compose Basics")) }

        // Use the variable selectedExisting here
        val mockAvailableTags = mapOf(
            "Mathematics" to listOf(TagItem("tag_1","Algebra", "Mathematics",""), TagItem("tag_2","Calculus", "Mathematics","")),
            "Science" to listOf(TagItem("tag_3","Physics", "Science",""))
        )

        Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Setup Your Profile Preview") }) }) { padding ->
            Column(modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
                // Use state variables for value and onValueChange
                OutlinedTextField(value = name, onValueChange = {name=it}, label = { Text("Name *")}, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(value = school, onValueChange = {school=it}, label = { Text("School")}, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(value = time, onValueChange = {time=it}, label = { Text("Preferred Time")}, modifier = Modifier.fillMaxWidth()) // Used `time`
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(value = goals, onValueChange = {goals=it}, label = { Text("Goals")}, modifier = Modifier.fillMaxWidth().height(100.dp)) // Used `goals`
                Spacer(Modifier.height(28.dp))

                Text("Select Subjects & Interests *", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                // Use mockAvailableTags here
                mockAvailableTags.forEach { (category, tags) ->
                    Text(category, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top=12.dp, bottom = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        tags.forEach { tag ->
                            FilterChip(
                                // Use selectedExisting here
                                selected = tag.id in selectedExisting,
                                // Use selectedExisting here
                                onClick = { selectedExisting = if(tag.id in selectedExisting) selectedExisting - tag.id else selectedExisting + tag.id },
                                label = { Text(tag.name) },
                                leadingIcon = if (tag.id in selectedExisting) { { Icon(Icons.Filled.Done, "", Modifier.size(FilterChipDefaults.IconSize))}} else null,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))

                Text("Add Custom Tags (Optional)", style = MaterialTheme.typography.titleLarge)
                // Use customInput here
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically){
                    OutlinedTextField(
                        value = customInput,
                        onValueChange = { customInput = it }, /* ... */
                    ) // Use customInput
                    IconButton(onClick = { /* Use customInput */ }, enabled=customInput.isNotBlank()) { /* ... */ }
                }
                if(customTags.isNotEmpty()) { // Use customTags
                    Spacer(Modifier.height(12.dp))
                    Text("Your Custom Tags:", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    // Use customTags here
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)){
                        customTags.sorted().forEach { ct ->
                            InputChip(
                                selected = false, onClick = { customTags -= ct },
                                label = TODO(),
                                modifier = TODO(),
                                enabled = TODO(),
                                leadingIcon = TODO(),
                                avatar = TODO(),
                                trailingIcon = TODO(),
                                shape = TODO(),
                                colors = TODO(),
                                elevation = TODO(),
                                border = TODO(),
                                interactionSource = TODO(), /* ... */
                            ) // Use customTags
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
                Button(onClick = { /* Preview doesn't save */ }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Save Profile & Continue") }
            }
        }
    }
}