package com.jamie.nodica.features.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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

private const val MIN_SIGNUP_PASSWORD_LENGTH = 6

@Composable
fun SignUpScreen(
    email: String,
    password: String,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    viewModel: AuthViewModel,
    onToggle: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val state by viewModel.authState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var confirmPasswordVisible by rememberSaveable { mutableStateOf(false) }

    // --- Client-side Validation ---
    val isEmailPotentiallyValid = remember(email) {
        email.isBlank() || android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
    val isPasswordLongEnough = remember(password) {
        password.length >= MIN_SIGNUP_PASSWORD_LENGTH
    }
    val passwordsMatch = remember(password, confirmPassword) {
        password == confirmPassword
    }
    // Determine if the signup button should be enabled based on client checks
    val canAttemptSignUp = isEmailPotentiallyValid && email.isNotBlank() &&
            isPasswordLongEnough && passwordsMatch && state !is AuthUiState.Loading


    LaunchedEffect(state) {
        when (val currentState = state) {
            is AuthUiState.Success -> {
                // Let user know to check email if verification is enabled
                val message = "Sign up successful! Please check your email to verify your account." // Assuming verification is enabled
                Timber.i("Sign up success reported.")
                scope.launch { snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long) }
                onToggle() // Go back to Sign In screen after showing message
                // VM state reset in AuthScreen's onToggle
            }
            is AuthUiState.Error -> {
                Timber.w("Sign up error: ${currentState.message}")
                scope.launch {
                    keyboardController?.hide()
                    snackbarHostState.showSnackbar(currentState.message, duration = SnackbarDuration.Long)
                }
                viewModel.resetState() // Reset after showing error
            }
            else -> { /* Loading/Idle */ }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Email Field
        OutlinedTextField(
            value = email,
            onValueChange = {
                onEmailChange(it)
                if (state is AuthUiState.Error) viewModel.resetState()
            },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Email,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Next
            ),
            isError = !isEmailPotentiallyValid && email.isNotEmpty(),
            supportingText = {
                if (!isEmailPotentiallyValid && email.isNotEmpty()) {
                    Text("Enter a valid email format")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Password Field
        OutlinedTextField(
            value = password,
            onValueChange = {
                onPasswordChange(it)
                if (state is AuthUiState.Error) viewModel.resetState()
            },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next // Go to Confirm Password
            ),
            trailingIcon = {
                val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                val description = if (passwordVisible) "Hide password" else "Show password"
                IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(imageVector = image, description) }
            },
            isError = !isPasswordLongEnough && password.isNotEmpty(),
            supportingText = {
                if (password.isNotEmpty() && !isPasswordLongEnough) {
                    Text("Password must be at least $MIN_SIGNUP_PASSWORD_LENGTH characters")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Confirm Password Field
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirm Password") },
            singleLine = true,
            visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done // Last field
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    keyboardController?.hide()
                    if (canAttemptSignUp) {
                        viewModel.signUp(email, password)
                    } else {
                        // Show specific validation feedback if needed
                        scope.launch { snackbarHostState.showSnackbar("Please check your inputs.") }
                    }
                }
            ),
            trailingIcon = {
                val image = if (confirmPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                val description = if (confirmPasswordVisible) "Hide password" else "Show password"
                IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) { Icon(imageVector = image, description) }
            },
            isError = !passwordsMatch && confirmPassword.isNotEmpty(),
            supportingText = {
                if (confirmPassword.isNotEmpty() && !passwordsMatch) {
                    Text("Passwords do not match")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(20.dp))

        // Sign Up Button
        Button(
            onClick = {
                keyboardController?.hide()
                viewModel.signUp(email, password)
            },
            enabled = canAttemptSignUp, // Enable based on client-side checks
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            if (state is AuthUiState.Loading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
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