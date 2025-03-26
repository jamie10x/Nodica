package com.jamie.nodica.features.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jamie.nodica.features.navigation.Routes
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(navController: NavController) {
    val viewModel: ProfileViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsState()

    // Form state variables
    var name by remember { mutableStateOf("") }
    var institution by remember { mutableStateOf("") }
    var preferredTime by remember { mutableStateOf("") }
    var studyGoals by remember { mutableStateOf("") }

    // New state for tag-based system:
    // This is a mapping of category names to a list of available tags.
    // In a real app, these might be fetched from the server.
    val categoriesWithTags = remember {
        mutableStateMapOf(
            "Mathematics" to mutableStateListOf("Calculus", "Algebra", "Statistics"),
            "Science" to mutableStateListOf("Physics", "Chemistry", "Biology"),
            "Languages" to mutableStateListOf("English", "Spanish", "Chinese")
        )
    }
    // Holds the selected tags across all categories.
    var selectedTags by remember { mutableStateOf(setOf<String>()) }

    // State for the custom tag dialog.
    var showCustomTagDialog by remember { mutableStateOf(false) }
    var customTagCategory by remember { mutableStateOf("") }
    var customTagName by remember { mutableStateOf("") }

    // Navigate to home if profile exists or save is successful.
    LaunchedEffect(uiState) {
        when (uiState) {
            is ProfileUiState.ProfileExists,
            is ProfileUiState.Success -> {
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.PROFILE_SETUP) { inclusive = true }
                }
            }
            else -> { }
        }
    }

    // Custom tag dialog
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
                        // Add the custom tag to the appropriate category list.
                        categoriesWithTags[customTagCategory]?.add(customTagName)
                        // Also select it.
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
            CenterAlignedTopAppBar(
                title = { Text("Setup Your Profile") }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Section: Personal Information
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Personal Information", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name *") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = institution,
                        onValueChange = { institution = it },
                        label = { Text("Institution/School (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = preferredTime,
                        onValueChange = { preferredTime = it },
                        label = { Text("Preferred Study Time") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = studyGoals,
                        onValueChange = { studyGoals = it },
                        label = { Text("Study Goals") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp),
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            // Section: Tag-based Subjects or Interests
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Subjects or Interests *", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    // Iterate over each category
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
                                Text(tag, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        // Button to add a custom tag for this category
                        TextButton(
                            onClick = {
                                customTagCategory = category
                                showCustomTagDialog = true
                            }
                        ) {
                            Text("Add custom tag to $category")
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            // Save Button with loading indicator; only enable if required fields are present.
            Button(
                onClick = {
                    viewModel.saveProfile(
                        name = name,
                        institution = institution,
                        preferredTime = preferredTime,
                        studyGoals = studyGoals,
                        tagNames = selectedTags.toList()
                    )
                },
                enabled = name.isNotBlank() && selectedTags.isNotEmpty() && uiState !is ProfileUiState.Loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState is ProfileUiState.Loading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(24.dp)
                            .padding(end = 8.dp)
                    )
                }
                Text("Save Profile")
            }
            if (uiState is ProfileUiState.Error) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = (uiState as ProfileUiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
