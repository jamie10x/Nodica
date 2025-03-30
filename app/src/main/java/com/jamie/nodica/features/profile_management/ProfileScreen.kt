// ProfileScreen.kt

package com.jamie.nodica.features.profile_management

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.jamie.nodica.R
import com.jamie.nodica.features.navigation.Routes
import com.jamie.nodica.features.profile.TimePickerField
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen(outerNavController: NavHostController) {
    val viewModel: ProfileManagementViewModel = koinViewModel()
    val state by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    var isEditing by rememberSaveable { mutableStateOf(false) }
    var showLogoutConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var showUnsavedChangesDialog by rememberSaveable { mutableStateOf(false) }
    var newCustomTags by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var customTagInput by rememberSaveable { mutableStateOf("") }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? -> uri?.let { viewModel.uploadProfilePicture(context, it) } }
    )

    LaunchedEffect(state.screenStatus) {
        when (state.screenStatus) {
            is ProfileManagementStatus.Error -> {
                scope.launch {
                    snackbarHostState.showSnackbar((state.screenStatus as ProfileManagementStatus.Error).message, duration = SnackbarDuration.Long)
                    viewModel.clearErrorStatus()
                }
            }
            ProfileManagementStatus.Success -> {
                if (isEditing) isEditing = false
                newCustomTags = emptySet()
                customTagInput = ""
                scope.launch { snackbarHostState.showSnackbar("Profile updated!", duration = SnackbarDuration.Short) }
            }
            else -> { /* no side-effects for Loading/Idle */ }
        }
    }

    if (showLogoutConfirmDialog) {
        ConfirmationDialog(
            title = "Confirm Logout",
            text = "Are you sure you want to log out?",
            confirmButtonText = "Logout",
            onConfirm = {
                showLogoutConfirmDialog = false
                viewModel.logout {
                    // When logout, clear the back stack to onboarding
                    outerNavController.navigate(Routes.ONBOARDING) {
                        popUpTo(outerNavController.graph.startDestinationId) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            },
            onDismiss = { showLogoutConfirmDialog = false }
        )
    }

    if (showUnsavedChangesDialog) {
        ConfirmationDialog(
            title = "Unsaved Changes",
            text = "Discard changes and go back?",
            confirmButtonText = "Discard",
            onConfirm = {
                showUnsavedChangesDialog = false
                isEditing = false
                viewModel.refreshData()
            },
            onDismiss = { showUnsavedChangesDialog = false }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("My Profile") },
                navigationIcon = {
                    IconButton(onClick = {
                        val hasUnsaved = isEditing && (state.hasUnsavedChanges(viewModel.originalState) || newCustomTags.isNotEmpty())
                        if (hasUnsaved) {
                            showUnsavedChangesDialog = true
                        } else {
                            // Instead of simply navigating up, we want to ensure that we do not go back to sign in.
                            // For example, pop up to the Home route.
                            outerNavController.navigate(Routes.HOME) {
                                popUpTo(Routes.HOME) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val actionIcon = if (isEditing) Icons.Default.Done else Icons.Default.Edit
                    val actionText = if (isEditing) "Save" else "Edit"
                    val isSavingOrUploading = state.screenStatus == ProfileManagementStatus.Saving || state.screenStatus == ProfileManagementStatus.Uploading
                    IconButton(
                        onClick = {
                            if (isEditing) {
                                focusManager.clearFocus()
                                viewModel.saveChanges(newCustomTagNames = newCustomTags.toList())
                            } else {
                                isEditing = true
                                newCustomTags = emptySet()
                                customTagInput = ""
                            }
                        },
                        enabled = !isSavingOrUploading
                    ) {
                        if (isSavingOrUploading && state.screenStatus == ProfileManagementStatus.Saving) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        } else {
                            Icon(actionIcon, contentDescription = actionText)
                        }
                    }
                    IconButton(onClick = { showLogoutConfirmDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout")
                    }
                }
            )
        }
    ) { padding ->
        if (state.screenStatus == ProfileManagementStatus.Loading && !state.isProfileLoaded) {
            Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
        } else {
            val fieldsEnabled = isEditing && state.screenStatus != ProfileManagementStatus.Saving && state.screenStatus != ProfileManagementStatus.Uploading
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile Picture Section
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(128.dp)
                            .clickable(enabled = isEditing) { imagePickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(state.profilePictureUrl ?: R.drawable.nodica_icon)
                                .crossfade(true)
                                .placeholder(R.drawable.nodica_icon)
                                .error(R.drawable.nodica_icon)
                                .build(),
                            contentDescription = "Profile Picture",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                        if (isEditing && state.screenStatus != ProfileManagementStatus.Uploading) {
                            Box(
                                Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit picture", tint = Color.White.copy(alpha = 0.8f))
                            }
                        }
                    }
                    if (state.screenStatus == ProfileManagementStatus.Uploading) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(state.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))

                // Editable Text Fields
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChange,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = fieldsEnabled,
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.school,
                    onValueChange = viewModel::onSchoolChange,
                    label = { Text("School / Institution") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = fieldsEnabled,
                    singleLine = true
                )
                Spacer(Modifier.height(12.dp))
                // Replace Preferred Study Time text field with TimePickerField
                TimePickerField(
                    label = "Preferred Study Time",
                    selectedTime = state.preferredTime,
                    onTimeSelected = viewModel::onTimeChange
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.goals,
                    onValueChange = viewModel::onGoalsChange,
                    label = { Text("Study Goals") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                    enabled = fieldsEnabled,
                    maxLines = 5
                )
                Spacer(Modifier.height(24.dp))

                // Tags Section
                Text("My Subjects & Interests", style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth())
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                if (state.availableTags.isEmpty() && state.screenStatus != ProfileManagementStatus.Loading) {
                    Text(
                        text = "No subjects found or failed to load.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.screenStatus is ProfileManagementStatus.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    state.availableTags.forEach { (category, tagsInCategory) ->
                        Text(category, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            tagsInCategory.forEach { tag ->
                                FilterChip(
                                    selected = tag.id in state.selectedTagIds,
                                    onClick = { if (fieldsEnabled) viewModel.toggleTagSelection(tag.id) },
                                    enabled = fieldsEnabled,
                                    label = { Text(tag.name) },
                                    leadingIcon = if (tag.id in state.selectedTagIds) {
                                        { Icon(Icons.Filled.Done, contentDescription = "Selected", Modifier.size(FilterChipDefaults.IconSize)) }
                                    } else null
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))

                // Custom Tags Section (Visible in Edit Mode)
                AnimatedVisibility(visible = isEditing) {
                    Column {
                        Spacer(Modifier.height(20.dp))
                        Text("Add Custom Tags", style = MaterialTheme.typography.titleMedium)
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customTagInput,
                                onValueChange = { customTagInput = it },
                                label = { Text("Type a tag...") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                enabled = fieldsEnabled,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    val trimmed = customTagInput.trim()
                                    if (trimmed.isNotBlank() && trimmed.length <= 50 && !newCustomTags.any { it.equals(trimmed, ignoreCase = true) }) {
                                        newCustomTags = newCustomTags + trimmed
                                        customTagInput = ""
                                    } else {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = when {
                                                    trimmed.length > 50 -> "Tag too long (max 50 chars)"
                                                    trimmed.isBlank() -> "Tag cannot be empty"
                                                    else -> "Tag already added"
                                                }
                                            )
                                        }
                                    }
                                })
                            )
                            IconButton(
                                onClick = {
                                    val trimmed = customTagInput.trim()
                                    if (trimmed.isNotBlank() && trimmed.length <= 50 && !newCustomTags.any { it.equals(trimmed, ignoreCase = true) }) {
                                        newCustomTags = newCustomTags + trimmed
                                        customTagInput = ""
                                    } else {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = when {
                                                    trimmed.length > 50 -> "Tag too long (max 50 chars)"
                                                    trimmed.isBlank() -> "Tag cannot be empty"
                                                    else -> "Tag already added"
                                                }
                                            )
                                        }
                                    }
                                },
                                enabled = customTagInput.isNotBlank() && fieldsEnabled,
                                modifier = Modifier.padding(start = 8.dp)
                            ) { Icon(Icons.Filled.AddCircle, contentDescription = "Add Custom Tag") }
                        }
                        if (newCustomTags.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            FlowRow(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                newCustomTags.sortedBy { it.lowercase() }.forEach { customTag ->
                                    InputChip(
                                        selected = false,
                                        onClick = { if (fieldsEnabled) newCustomTags = newCustomTags - customTag },
                                        enabled = fieldsEnabled,
                                        label = { Text(customTag) },
                                        trailingIcon = { Icon(Icons.Filled.Cancel, contentDescription = "Remove $customTag Tag", Modifier.size(InputChipDefaults.IconSize)) }
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun ConfirmationDialog(
    title: String,
    text: String,
    confirmButtonText: String = "Confirm",
    dismissButtonText: String = "Cancel",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = text) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = confirmButtonText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = dismissButtonText)
            }
        }
    )
}