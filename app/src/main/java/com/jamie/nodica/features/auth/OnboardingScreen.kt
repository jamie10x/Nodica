package com.jamie.nodica.features.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jamie.nodica.R // Import R for drawable
import com.jamie.nodica.features.navigation.Routes
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    navController: NavController,
    viewModel: AuthViewModel = koinViewModel(),
) {
    val authState by viewModel.authState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

// Handle Auth State changes: Navigation or Error Display
    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthUiState.Success -> {
                Timber.i("Onboarding: Google sign-in successful.")
                // Navigate to profile setup after Google sign-in success
                navController.navigate(Routes.PROFILE_SETUP) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                    launchSingleTop = true
                }
                // Optionally show a brief success message
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = "Google sign-in successful!",
                        duration = SnackbarDuration.Short
                    )
                }
                viewModel.resetState() // Reset state after handling
            }

            is AuthUiState.Error -> {
                Timber.w("Onboarding: Auth error: ${state.message}")
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = state.message,
                        duration = SnackbarDuration.Long // Show errors longer
                    )
                }
                viewModel.resetState() // Reset state after handling
            }

            AuthUiState.Loading -> {
                Timber.d("Onboarding: Auth state is Loading...")
                // Loading indicator is handled implicitly by button state below
            }

            AuthUiState.Idle -> { /* Do nothing */
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(horizontal = 32.dp), // Consistent horizontal padding
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo
            Image(
                painter = painterResource(id = R.drawable.nodica_icon),
                contentDescription = "Nodica Logo",
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .size(120.dp)
                    .padding(bottom = 16.dp)
            )

            // Welcome Text
            Text(
                text = "Welcome to Nodica!",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Connect with peers and study together.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(48.dp)) // Increased spacing

            // Continue with Email Button
            Button(
                onClick = { navController.navigate(Routes.AUTH) },
                modifier = Modifier.fillMaxWidth(),
                // Disable button if Google Sign-in is in progress
                enabled = authState !is AuthUiState.Loading
            ) {
                Text("Continue with Email")
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Sign in with Google Button
            OutlinedButton( // Use OutlinedButton for secondary action
                onClick = { viewModel.signInWithGoogle() },
                modifier = Modifier.fillMaxWidth(),
                enabled = authState !is AuthUiState.Loading
            ) {
                if (authState is AuthUiState.Loading) {
                    // Show loading indicator inside the button when loading
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary // Match progress color
                    )
                    Spacer(Modifier.width(8.dp)) // Space between indicator and text
                    Text("Signing in...")
                } else {
                    // Optional: Add Google Icon
                    // Icon(painter = painterResource(id = R.drawable.ic_google_logo), contentDescription = "Google logo")
                    // Spacer(Modifier.width(8.dp))
                    Text("Sign in with Google")
                }
            }

            // Recommendation: Add links to Terms of Service and Privacy Policy here
            // Spacer(modifier = Modifier.height(24.dp))
            // Row(...) { Text(...) }
        }
    }
}