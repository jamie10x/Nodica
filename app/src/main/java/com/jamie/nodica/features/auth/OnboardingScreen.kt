package com.jamie.nodica.features.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jamie.nodica.features.navigation.Routes
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber // Added Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    navController: NavController,
    viewModel: AuthViewModel = koinViewModel()
) {
    val authState by viewModel.authState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(authState) {
        when (val currentAuthState = authState) { // Give it a name
            is AuthUiState.Success -> {
                Timber.i("OnboardingScreen: Auth Successful. Navigating back to SPLASH to re-evaluate destination.")
                // Navigate back to SPLASH route. This forces SplashViewModel
                // to re-run its check with the now valid session.
                navController.navigate(Routes.SPLASH) {
                    // Clear the entire back stack up to the graph's start destination
                    // including the onboarding/auth screens themselves.
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    // Ensure Splash isn't added multiple times if navigated quickly
                    launchSingleTop = true
                }
                // Show Snackbar AFTER triggering navigation if needed, or rely on Splash/Target screen feedback
                // scope.launch { snackbarHostState.showSnackbar("Authentication successful!") }
                // Resetting state might happen too quickly before navigation completes,
                // maybe reset in Splash or upon reaching final destination.
                // viewModel.resetState() // Reset moved or handled differently
            }
            is AuthUiState.Error -> {
                Timber.w("OnboardingScreen: Auth Error: ${currentAuthState.message}")
                scope.launch {
                    snackbarHostState.showSnackbar(currentAuthState.message, duration = SnackbarDuration.Long)
                }
                viewModel.resetState() // Reset state after showing error
            }
            AuthUiState.Loading -> {
                Timber.d("OnboardingScreen: Auth Loading...")
                // Optionally show a loading indicator on the OnboardingScreen itself
            }
            AuthUiState.Idle -> {
                Timber.d("OnboardingScreen: Auth Idle.")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Display Loading state if needed
            if (authState == AuthUiState.Loading) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Authenticating...")
            } else {
                Text("Welcome to Nodica!", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { navController.navigate(Routes.AUTH) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = authState != AuthUiState.Loading // Disable buttons while loading
                ) {
                    Text("Continue with Email")
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.signInOrSignUpWithGoogle() },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = authState != AuthUiState.Loading // Disable buttons while loading
                ) {
                    // Optional: Show different text or indicator during Google sign-in attempt
                    Text("Sign In with Google")
                }
            }
        }
    }
}