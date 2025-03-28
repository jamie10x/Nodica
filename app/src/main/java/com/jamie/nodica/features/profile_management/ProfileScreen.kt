package com.jamie.nodica.features.profile_management

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack // Added import
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager // To hide keyboard on save
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jamie.nodica.R // Assuming a placeholder drawable exists
import com.jamie.nodica.features.navigation.Routes
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(outerNavController: NavHostController) { // Accept outer NavController
    val viewModel: ProfileManagementViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

// Separate UI state for edit mode control
    var isEditing by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

// Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                Timber.d("Image selected: $it")
                viewModel.uploadProfilePicture(context, it)
            }
        }
    )

// Handle error messages via Snackbar
    LaunchedEffect(state.screenStatus) {
        if (state.screenStatus is ProfileManagementStatus.Error) {
            val errorMessage = (state.screenStatus as ProfileManagementStatus.Error).message
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = errorMessage,
                    duration = SnackbarDuration.Long
                )
                // Reset status to Idle after showing error, allowing retry/editing
                // viewModel.updateState { copy(screenStatus = ProfileManagementStatus.Idle) }
            }
        } else if (state.screenStatus == ProfileManagementStatus.Success) {
            // Show general success message if needed (though often implied by saving finishing)
            scope.launch {
                snackbarHostState.showSnackbar("Profile saved!", duration = SnackbarDuration.Short)
                // Reset to Idle status after showing success
                // viewModel.updateState { copy(screenStatus = ProfileManagementStatus.Idle) }
            }
        }
    }

    // Function to handle save/edit toggle
    fun toggleEditSave() {
        if (isEditing) {
            focusManager.clearFocus() // Hide keyboard before saving
            viewModel.saveChanges() // Trigger save
            // UI will show Saving state from ViewModel
        }
        // Always toggle edit mode, successful save will also exit edit mode implicitly when state updates
        isEditing = !isEditing
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("My Profile") },
                // Add back navigation if this isn't the root screen in this tab
                navigationIcon = {
                    IconButton(onClick = { outerNavController.navigateUp() }) { // Or navigate to specific route
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Display appropriate icon based on mode
                    val actionIcon = if (isEditing) Icons.Default.Done else Icons.Default.Edit
                    val actionText = if (isEditing) "Save" else "Edit"

                    // Show progress indicator within the button during Saving/Uploading
                    val isSavingOrUploading = state.screenStatus == ProfileManagementStatus.Saving ||
                            state.screenStatus == ProfileManagementStatus.Uploading

                    IconButton(
                        onClick = { toggleEditSave() },
                        enabled = !isSavingOrUploading // Disable during operations
                    ) {
                        if (isSavingOrUploading) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(actionIcon, contentDescription = actionText)
                        }
                    }
                }
            )
        }
    ) { padding ->
        // Show main loading indicator only if profile hasn't loaded at all
        if (!state.isProfileLoaded && state.screenStatus == ProfileManagementStatus.Loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        // Show main content if profile data is available (even if editing/saving/uploading)
        else if (state.isProfileLoaded) {
            Column( // Use Column instead of LazyColumn if content isn't excessively long
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()) // Allow scrolling
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile Picture Section
                Box(contentAlignment = Alignment.Center) { // Box to overlay progress indicator
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant) // Placeholder background
                            .clickable(enabled = isEditing) { // Only clickable when editing
                                imagePickerLauncher.launch("image/*") // Launch image picker
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(state.profilePictureUrl)
                                .crossfade(true)
                                // Use a default vector drawable or generic icon
                                .error(R.drawable.nodica_icon)
                                .placeholder(R.drawable.nodica_icon) // Display while loading
                                .build(),
                            contentDescription = "Profile Picture",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Show overlay text only when editing and not uploading
                        if (isEditing && state.screenStatus != ProfileManagementStatus.Uploading) {
                            Box(
                                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Tap to change", color = Color.White, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } // End Inner Box
                    // Show circular progress indicator over the image while uploading
                    if (state.screenStatus == ProfileManagementStatus.Uploading) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                } // End Outer Box

                Spacer(modifier = Modifier.height(16.dp))
                // Display Email (read-only)
                Text(
                    text = state.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))

                // --- Editable Text Fields Section ---
                OutlinedTextField(value = state.name, onValueChange = viewModel::onNameChange, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), enabled = isEditing, singleLine = true)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(value = state.school, onValueChange = viewModel::onSchoolChange, label = { Text("School / Institution") }, modifier = Modifier.fillMaxWidth(), enabled = isEditing, singleLine = true)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(value = state.goals, onValueChange = viewModel::onGoalsChange, label = { Text("Study Goals") }, modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp), enabled = isEditing, maxLines = 5)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(value = state.preferredTime, onValueChange = viewModel::onTimeChange, label = { Text("Preferred Study Time") }, modifier = Modifier.fillMaxWidth(), enabled = isEditing, singleLine = true)
                Spacer(modifier = Modifier.height(24.dp))

                // --- Tags Section ---
                Text("My Subjects & Interests", style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp)) // Visual separator

                if (state.availableTags.isEmpty()) {
                    // Show placeholder if tags haven't loaded (might be covered by main loader)
                    Text("Loading tags...")
                } else {
                    state.availableTags.forEach { (category, tagsInCategory) ->
                        Text(category, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                        FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            tagsInCategory.forEach { tag ->
                                FilterChip(
                                    selected = tag.id in state.selectedTagIds,
                                    onClick = { if (isEditing) viewModel.toggleTagSelection(tag.id) },
                                    enabled = isEditing, // Enable/disable chip based on edit mode
                                    label = { Text(tag.name) },
                                    leadingIcon = if (tag.id in state.selectedTagIds) { { Icon(Icons.Filled.Done, "Selected", Modifier.size(FilterChipDefaults.IconSize)) }} else null
                                )
                            }
                        }
                    }
                }

                // --- Logout Button Section ---
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = {
                        viewModel.logout {
                            // Navigate back to Onboarding or Auth after logout
                            outerNavController.navigate(Routes.ONBOARDING) {
                                popUpTo(outerNavController.graph.startDestinationId) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    },
                    // Only allow logout when not saving/uploading/editing? Or always allow? Usually always allow.
                    // enabled = !isEditing && state.screenStatus != ProfileManagementStatus.Saving && state.screenStatus != ProfileManagementStatus.Uploading,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Logout", color = MaterialTheme.colorScheme.onError)
                }
                Spacer(modifier = Modifier.height(16.dp)) // Bottom padding
            } // End Main Content Column
        }
    } // End Scaffold
} // End ProfileScreen Composable