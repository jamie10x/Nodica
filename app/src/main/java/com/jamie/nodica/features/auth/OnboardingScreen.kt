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
        when (authState) {
            is AuthUiState.Success -> {
                // After sign-in/sign-up, navigate to PROFILE_SETUP.
                // The SplashViewModel will handle the profile completeness check on app launch.
                navController.navigate(Routes.PROFILE_SETUP) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                    launchSingleTop = true
                }
                scope.launch { snackbarHostState.showSnackbar("Authentication successful!") }
                viewModel.resetState()
            }
            is AuthUiState.Error -> {
                scope.launch {
                    snackbarHostState.showSnackbar((authState as AuthUiState.Error).message, duration = SnackbarDuration.Long)
                }
                viewModel.resetState()
            }
            else -> { /* Idle or Loading */ }
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
            Text("Welcome to Nodica!", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { navController.navigate(Routes.AUTH) },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Continue with Email")
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { viewModel.signInOrSignUpWithGoogle() },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Sign In with Google")
            }
        }
    }
}