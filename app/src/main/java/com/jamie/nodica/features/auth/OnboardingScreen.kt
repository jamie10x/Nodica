package com.jamie.nodica.features.auth

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jamie.nodica.features.navigation.Routes
import org.koin.androidx.compose.koinViewModel

@Composable
fun OnboardingScreen(
    navController: NavController,
    viewModel: AuthViewModel = koinViewModel()
) {
    val authState by viewModel.authState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(authState) {
        if (authState is AuthUiState.Success) {
            Toast.makeText(context, "Google sign in successful!", Toast.LENGTH_SHORT).show()
            navController.navigate(Routes.PROFILE_SETUP) {
                popUpTo(Routes.ONBOARDING) { inclusive = true }
            }
            viewModel.resetState()
        } else if (authState is AuthUiState.Error) {
            Toast.makeText(context, (authState as AuthUiState.Error).message, Toast.LENGTH_SHORT).show()
            viewModel.resetState()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Study Buddy"
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Welcome to Study Buddy! Choose how you'd like to continue."
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { navController.navigate(Routes.AUTH) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Continue with Email")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.signInWithGoogle() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign in with Google")
            }
        }
    }
}