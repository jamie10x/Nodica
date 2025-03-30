package com.jamie.nodica.features.auth

import android.util.Patterns
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jamie.nodica.features.navigation.Routes
import kotlinx.coroutines.launch
import com.jamie.nodica.R

@Composable
fun SignInScreen(
    email: String,
    password: String,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    viewModel: AuthViewModel,
    navController: NavController,
    onToggle: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val state by viewModel.authState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    fun isValidEmail(email: String): Boolean =
        Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()

    val isEmailValid = remember(email) { email.isBlank() || isValidEmail(email) }

    LaunchedEffect(state) {
        when (state) {
            is AuthUiState.Success -> {
                navController.navigate(Routes.HOME) {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    launchSingleTop = true
                }
                viewModel.resetState()
            }
            is AuthUiState.Error -> {
                scope.launch {
                    keyboardController?.hide()
                    snackbarHostState.showSnackbar((state as AuthUiState.Error).message)
                }
                viewModel.resetState()
            }
            else -> {}
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
            isError = !isEmailValid && email.isNotEmpty(),
            supportingText = {
                if (!isEmailValid && email.isNotEmpty()) {
                    Text("Enter a valid email format")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
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
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    keyboardController?.hide()
                    if (email.isNotBlank() && password.isNotBlank() && isEmailValid) {
                        viewModel.signIn(email, password)
                    } else {
                        scope.launch { snackbarHostState.showSnackbar("Please enter a valid email and password.") }
                    }
                }
            ),
            trailingIcon = {
                val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                val description = if (passwordVisible) "Hide password" else "Show password"
                IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(imageVector = image, contentDescription = description) }
            },
            isError = state is AuthUiState.Error && password.isBlank(),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = { scope.launch { snackbarHostState.showSnackbar("Forgot Password feature not implemented yet.") } },
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                Text("Forgot Password?", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                keyboardController?.hide()
                if (email.isNotBlank() && password.isNotBlank() && isEmailValid) {
                    viewModel.signIn(email, password)
                } else {
                    scope.launch { snackbarHostState.showSnackbar("Please enter a valid email and password.") }
                }
            },
            enabled = state !is AuthUiState.Loading,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            if (state is AuthUiState.Loading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            } else {
                Text("Sign In")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        var isGoogleLoading by remember { mutableStateOf(false) }
        OutlinedButton(
            onClick = {
                keyboardController?.hide()
                isGoogleLoading = true
                viewModel.signInOrSignUpWithGoogle()
            },
            enabled = state !is AuthUiState.Loading,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            if (isGoogleLoading && state is AuthUiState.Loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Signing in...")
                }
            } else {
                val googleIcon = painterResource(id = R.drawable.google_icon)
                Icon(painter = googleIcon, contentDescription = "Google logo", modifier = Modifier.size(20.dp))
                Text("Sign In with Google")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onToggle) {
            Text("Don't have an account? Sign Up")
        }
    }
}