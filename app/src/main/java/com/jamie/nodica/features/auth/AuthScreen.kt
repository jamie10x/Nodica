package com.jamie.nodica.features.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jamie.nodica.R
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(navController: NavController) {
    val viewModel: AuthViewModel = koinViewModel()

    var isSignIn by rememberSaveable { mutableStateOf(true) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }

    DisposableEffect(Unit) { onDispose { viewModel.resetState() } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
        // Optional: Add a simple TopAppBar
        //topBar = { TopAppBar(title = { Text(if (isSignIn) "Sign In" else "Sign Up") }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp), // Adjusted padding
            horizontalAlignment = Alignment.CenterHorizontally,
            // Pushed content slightly down from center if logo/title is added
            verticalArrangement = Arrangement.Center
        ) {

            Spacer(Modifier.height(40.dp)) // Space from top or TopAppBar

            // Optional: App Logo or Title
            androidx.compose.foundation.Image(
                painterResource(R.drawable.nodica_icon),
                contentDescription = "Nodica Logo",
                modifier = Modifier.size(80.dp)
            )
            Text(
                text = if (isSignIn) "Welcome Back!" else "Create Your Account",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))

            // Content Area for Sign In / Sign Up
            Box(modifier = Modifier.weight(1f)) {
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
                            email = ""
                            password = ""
                            viewModel.resetState()
                        },
                        snackbarHostState = snackbarHostState
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
                            password = ""
                            viewModel.resetState()
                        },
                        snackbarHostState = snackbarHostState
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp)) // Space before bottom if needed
        }
    }
}