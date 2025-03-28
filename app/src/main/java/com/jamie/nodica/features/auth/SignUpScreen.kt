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
import kotlinx.coroutines.launch
import timber.log.Timber

@Composable
fun SignUpScreen(
    email: String,
    password: String,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    viewModel: AuthViewModel,
    onToggle: () -> Unit,
    snackbarHostState: SnackbarHostState // Receive SnackbarHostState
) {
    val state by viewModel.authState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope() // Coroutine scope for Snackbar
    var passwordVisible by remember { mutableStateOf(false) }
// Consider adding a confirm password field
// var confirmPassword by remember { mutableStateOf("") }

// Handle Auth State changes: Toggle or Error Display
    LaunchedEffect(state) {
        when (val currentState = state) {
            is AuthUiState.Success -> {
                Timber.i("Sign up successful. User should verify email (if enabled) and can now sign in.")
                scope.launch {
                    snackbarHostState.showSnackbar(
                        // Clarify next step (depends on Supabase email verification settings)
                        message = "Sign up successful! Check email or sign in.",
                        duration = SnackbarDuration.Long
                    )
                }
                onToggle() // Switch back to Sign In screen
                // viewModel.resetState() // Reset done by onToggle in AuthScreen
            }

            is AuthUiState.Error -> {
                Timber.w("Sign up error: ${currentState.message}")
                scope.launch {
                    keyboardController?.hide()
                    snackbarHostState.showSnackbar(
                        message = currentState.message,
                        duration = SnackbarDuration.Long
                    )
                }
                // Don't reset immediately, let user see error
                // viewModel.resetState()
            }

            else -> { /* Handle Loading or Idle if necessary */
            }
        }
    }

// **Recommendation**: Add client-side validation for email format and password strength.
// val isEmailValid = remember(email) { /* Add email regex check */ true }
// val isPasswordStrong = remember(password) { /* Add strength check (length, chars) */ true }
// val passwordsMatch = remember(password, confirmPassword) { password == confirmPassword }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Sign Up", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))

        // Email Field
        OutlinedTextField(
            value = email,
            onValueChange = {
                onEmailChange(it)
                if (state is AuthUiState.Error) viewModel.resetState() // Clear error on input
            },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Email,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next
            ),
            isError = state is AuthUiState.Error, // Consider more specific field errors
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Password Field
        OutlinedTextField(
            value = password,
            onValueChange = {
                onPasswordChange(it)
                if (state is AuthUiState.Error) viewModel.resetState() // Clear error on input
            },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                // imeAction = ImeAction.Next // Change if adding confirm password field
                imeAction = ImeAction.Done // Or Done if this is the last field
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    keyboardController?.hide()
                    if (email.isNotBlank() && password.isNotBlank()) { // Add validation checks here
                        viewModel.signUp(email, password)
                    }
                }
            ),
            trailingIcon = {
                val image =
                    if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = image,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            },
            isError = state is AuthUiState.Error, // Consider more specific field errors
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(20.dp))

        // Sign Up Button
        Button(
            onClick = {
                keyboardController?.hide()
                // Add client-side validation check before calling viewModel
                // if (isEmailValid && isPasswordStrong /* && passwordsMatch */) {
                viewModel.signUp(email, password)
                // } else {
                // scope.launch { snackbarHostState.showSnackbar("Please check input fields.") }
                // }
            },
            // Enable button only if fields are valid and not loading
            enabled = state !is AuthUiState.Loading && email.isNotBlank() && password.isNotBlank(), // && isEmailValid && isPasswordStrong,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            if (state is AuthUiState.Loading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Text("Sign Up")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Toggle to Sign In
        TextButton(onClick = onToggle) {
            Text("Already have an account? Sign In")
        }
    }
}