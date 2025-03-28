package com.jamie.nodica.features.profile_management

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items // Import the correct items overload
import androidx.compose.foundation.shape.CircleShape // For circular profile picture
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip // For clipping image
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale // For image scaling
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController // Correct import
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jamie.nodica.R // Import R for placeholder drawable
import com.jamie.nodica.features.navigation.Routes // Import Routes for logout navigation
import org.koin.androidx.compose.koinViewModel
import kotlinx.coroutines.launch // For launching coroutines

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(outerNavController: NavHostController) { // Accept outer NavController
    val viewModel: ProfileManagementViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsState()
    var isEditing by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Image picker launcher
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            // Let ViewModel handle file name generation
            viewModel.uploadProfilePicture(context, uri)
        }
    }

    // Hardcoded tags for display (Ideally fetch from ViewModel/Backend)
    val categoriesWithTags = remember {
        mapOf(
            "Mathematics" to listOf("Algebra", "Calculus", "Statistics"),
            "Science" to listOf("Physics", "Biology", "Chemistry"),
            "Languages" to listOf("English", "IELTS", "TOEFL", "Spanish"),
            "Coding" to listOf("Kotlin", "Android", "DSA", "Java", "Python")
            // Add more categories and tags as needed
        )
    }

    // Show error messages in Snackbar
    LaunchedEffect(state.error) {
        state.error?.let { errorMsg ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = errorMsg,
                    duration = SnackbarDuration.Short
                )
                // Optional: Clear error in ViewModel after showing
                // viewModel.clearError()
            }
        }
    }


    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("My Profile") },
                actions = {
                    // Toggle Edit/Save button
                    TextButton(onClick = {
                        if (isEditing) {
                            viewModel.saveChanges() // Save changes when done editing
                        }
                        isEditing = !isEditing // Toggle edit mode
                    }) {
                        Text(if (isEditing) "Save" else "Edit")
                    }
                }
            )
        }
    ) { padding ->
        // Use LazyColumn for potentially long list of tags and profile fields
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp), // Padding for the content
            horizontalAlignment = Alignment.CenterHorizontally // Center profile pic
        ) {
            // Profile Picture Section
            item {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape) // Clip to circle
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) // Add border
                        .clickable(enabled = isEditing) { // Only clickable when editing
                            launcher.launch("image/*") // Launch image picker
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(state.profilePictureUrl)
                            .crossfade(true)
                            .error(R.drawable.ic_launcher_foreground) // Placeholder if error or null
                            .placeholder(R.drawable.ic_launcher_foreground) // Placeholder while loading
                            .build(),
                        contentDescription = "Profile Picture",
                        contentScale = ContentScale.Crop, // Crop to fit circle
                        modifier = Modifier.fillMaxSize()
                    )
                    // Optional overlay to indicate editability
                    if (isEditing) {
                        Box(
                            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Tap to change", color = Color.White)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp)) // Space after picture
            }

            // Editable Text Fields Section
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isEditing, // Enable/disable based on edit mode
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                OutlinedTextField(
                    value = state.school,
                    onValueChange = viewModel::onSchoolChange,
                    label = { Text("School / Institution") }, // Consistent label
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isEditing,
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                OutlinedTextField(
                    value = state.goals,
                    onValueChange = viewModel::onGoalsChange,
                    label = { Text("Study Goals") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp), // Min height
                    enabled = isEditing,
                    maxLines = 5
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            item {
                OutlinedTextField(
                    value = state.preferredTime,
                    onValueChange = viewModel::onTimeChange,
                    label = { Text("Preferred Study Time") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isEditing,
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(24.dp)) // Space before tags
            }

            // Tags Section Header
            item {
                Text(
                    "My Subjects & Interests",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
            }

            // Iterate through categories and display tags with checkboxes
            categoriesWithTags.forEach { (category, tagsInCategory) ->
                // Category Title Item
                item(key = "category_header_$category") { // Add a key for stability
                    Text(
                        category,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp)
                    )
                }

                // Use items function for the CHUNKED list
                items(
                    items = tagsInCategory.chunked(2), // Pass the list of chunks
                    key = { chunk -> "chunk_${category}_${chunk.joinToString("-")}" } // Unique key per chunk
                ) { rowTags -> // This lambda receives one chunk (a List<String>)
                    // Create ONE Row for the current chunk
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp), // Padding for the row
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Iterate WITHIN the chunk using standard forEach inside the Row's composable scope
                        rowTags.forEach { tag ->
                            // Create the UI for each individual tag (Checkbox + Text)
                            // Apply weight here to distribute space within this Row
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f) // Each tag container takes equal space in the row
                                    .clickable(enabled = isEditing) { // Make the row area clickable
                                        viewModel.toggleTag(tag)
                                    }
                                    .padding(vertical = 4.dp) // Padding inside the tag's row
                            ) {
                                Checkbox(
                                    checked = tag in state.tags,
                                    onCheckedChange = { viewModel.toggleTag(tag) }, // Checkbox directly toggles
                                    enabled = isEditing
                                )
                                Spacer(Modifier.width(4.dp)) // Space between checkbox and text
                                Text(tag, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        // If the chunk has only one tag, add an empty weighted Box
                        // to make the single tag take up half the space correctly.
                        if (rowTags.size == 1) {
                            Box(Modifier.weight(1f)) // Empty Box takes the other half
                        }
                    }
                }
                // TODO: Add "Add custom tag" button here if needed for profile editing
                // item(key="add_custom_$category") { ... }
            }

            // Logout Button Section
            item {
                Spacer(modifier = Modifier.height(32.dp)) // More space before logout
                Button(
                    onClick = {
                        viewModel.logout {
                            // Use outerNavController to navigate after logout
                            // Navigate back to Onboarding or Auth screen
                            outerNavController.navigate(Routes.ONBOARDING) {
                                // Clear the entire back stack up to the root
                                popUpTo(outerNavController.graph.startDestinationId) { // Use startDestinationId
                                    inclusive = true
                                }
                                launchSingleTop = true
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Logout", color = MaterialTheme.colorScheme.onError)
                }
                Spacer(modifier = Modifier.height(16.dp)) // Bottom padding
            }
        } // End LazyColumn

        // Show loading overlay if saving or uploading
        if (state.loading && !isEditing) { // Show loading indicator only when *not* in editing mode (avoids overlay during typing)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)), // Semi-transparent overlay
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    } // End Scaffold
} // End ProfileScreen Composable