package com.jamie.nodica.features.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jamie.nodica.features.navigation.Routes
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun SignInScreen(
    email: String,
    password: String,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    viewModel: AuthViewModel,
    navController: NavController,
    onToggle: () -> Unit,
    snackbarHostState: SnackbarHostState // Receive SnackbarHostState
) {
    val state by viewModel.authState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope() // Coroutine scope for Snackbar
    var passwordVisible by remember { mutableStateOf(false) }

// Handle Auth State changes: Navigation or Error Display
    LaunchedEffect(state) {
        when (val currentState = state) {
            is AuthUiState.Success -> {
                Timber.i("Sign in successful.")
                // Navigate to profile setup after successful sign-in
                // Use PROFILE_SETUP as intermediate step before HOME
                navController.navigate(Routes.PROFILE_SETUP) {
                    // Clear backstack up to onboarding/auth entry point
                    popUpTo(Routes.AUTH) { inclusive = true } // Pop AuthScreen itself
                    popUpTo(Routes.ONBOARDING) { inclusive = true } // Pop Onboarding if it's there
                    launchSingleTop = true
                }
                // Optional: Show success snackbar if needed, though navigation might be sufficient
                // scope.launch { snackbarHostState.showSnackbar("Sign in successful") }
                viewModel.resetState() // Reset state after handling
            }

            is AuthUiState.Error -> {
                Timber.w("Sign in error: ${currentState.message}")
                scope.launch {
                    keyboardController?.hide() // Hide keyboard before showing snackbar
                    snackbarHostState.showSnackbar(
                        message = currentState.message,
                        duration = SnackbarDuration.Long
                    )
                }
                // Keep the error state until the user interacts again
                // viewModel.resetState() // Don't reset immediately, let user see the error
            }

            else -> { /* Handle Loading or Idle if necessary */
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()) // Allow scrolling for small screens
            .padding(vertical = 16.dp), // Padding within the column
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Sign In", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        // Email Field
        OutlinedTextField(
            value = email,
            onValueChange = {
                onEmailChange(it)
                if (state is AuthUiState.Error) viewModel.resetState() // Clear error on input change
            },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Email,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next // Move to password field
            ),
            isError = state is AuthUiState.Error, // Highlight field on error
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Password Field
        OutlinedTextField(
            value = password,
            onValueChange = {
                onPasswordChange(it)
                if (state is AuthUiState.Error) viewModel.resetState() // Clear error on input change
            },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done // Trigger sign-in action
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    keyboardController?.hide()
                    if (email.isNotBlank() && password.isNotBlank()) {
                        viewModel.signIn(email, password)
                    }
                }
            ),
            trailingIcon = {
                val image =
                    if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                val description = if (passwordVisible) "Hide password" else "Show password"
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = image, description)
                }
            },
            isError = state is AuthUiState.Error, // Highlight field on error
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(20.dp))

        // Sign In Button
        Button(
            onClick = {
                keyboardController?.hide()
                viewModel.signIn(email, password)
            },
            enabled = state !is AuthUiState.Loading && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(48.dp) // Standard button height
        ) {
            if (state is AuthUiState.Loading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text("Sign In")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Google Sign In Button (Consistent with Onboarding)
        OutlinedButton(
            onClick = {
                keyboardController?.hide()
                if (state !is AuthUiState.Loading) { // Prevent double taps
                    viewModel.signInWithGoogle()
                }
            },
            enabled = state !is AuthUiState.Loading,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            // Optional: Add Google Icon
            // Icon(...)
            // Spacer(Modifier.width(8.dp))
            Text("Sign In with Google")
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Toggle to Sign Up
        TextButton(onClick = onToggle) {
            Text("Don't have an account? Sign Up")
        }

        // Recommendation: Add "Forgot Password?" TextButton here
        // TextButton(onClick = { /* TODO: Navigate to Forgot Password Screen */ }) {
        //     Text("Forgot Password?")
        // }
    }
}