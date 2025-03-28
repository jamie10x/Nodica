package com.jamie.nodica.features.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.* // Import SnackbarHost, SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class) // Needed for Scaffold
@Composable
fun AuthScreen(navController: NavController) {
    val viewModel: AuthViewModel = koinViewModel()

// Shared state for email & password, persisting across screens
    var isSignIn by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

// Snackbar state for displaying messages
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues) // Apply padding from Scaffold
                .fillMaxSize()
                .padding(16.dp), // Additional padding for content
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Logo or Title could go here
            // Image(...) or Text(...)
            // Spacer(modifier = Modifier.height(32.dp))

            if (isSignIn) {
                SignInScreen(
                    email = email,
                    password = password,
                    onEmailChange = { email = it },
                    onPasswordChange = { password = it },
                    viewModel = viewModel,
                    navController = navController,
                    onToggle = {
                        isSignIn = false
                        viewModel.resetState() // Reset state when toggling
                    },
                    snackbarHostState = snackbarHostState // Pass SnackbarHostState
                )
            } else {
                SignUpScreen(
                    email = email,
                    password = password,
                    onEmailChange = { email = it },
                    onPasswordChange = { password = it },
                    viewModel = viewModel,
                    onToggle = {
                        isSignIn = true
                        viewModel.resetState() // Reset state when toggling
                    },
                    snackbarHostState = snackbarHostState // Pass SnackbarHostState
                )
            }

            // Recommendation: Consider adding a "Forgot Password?" button/link on the SignInScreen
        }
    }
}