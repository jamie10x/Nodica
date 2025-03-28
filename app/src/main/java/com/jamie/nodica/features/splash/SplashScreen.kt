package com.jamie.nodica.features.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator // Added for loading indicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.jamie.nodica.R
import com.jamie.nodica.features.navigation.Routes
import com.jamie.nodica.ui.theme.NodicaTheme
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber

@Composable
fun SplashScreen(
    navController: NavController,
    viewModel: SplashViewModel = koinViewModel()
) {
    val destinationState by viewModel.destination.collectAsState()
    var showContent by remember { mutableStateOf(false) } // Control visibility explicitly

// Make content visible after a short delay to allow animation
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100) // Small delay before fade-in starts
        showContent = true
    }

// Navigate when the destination is determined by the ViewModel
    LaunchedEffect(destinationState) {
        if (destinationState is SplashDestination.Navigate) {
            val route = (destinationState as SplashDestination.Navigate).route
            Timber.i("Splash: Navigating to destination route: $route")
            // Add a small delay before navigation to allow users to see the splash screen briefly
            // This delay should be shorter than the one in the ViewModel.
            // Adjust timing based on UX preference.
            // kotlinx.coroutines.delay(500) // Optional delay before navigating away
            navController.navigate(route) {
                // Pop the splash screen off the back stack
                popUpTo(Routes.SPLASH) { inclusive = true }
                // Ensure only one instance of the destination screen
                launchSingleTop = true
            }
        }
    }

// Always render the splash content container
    SplashContent(isVisible = showContent)
}

@Composable
private fun SplashContent(isVisible: Boolean) {
    val isPreview = LocalInspectionMode.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background) // Explicit background
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(durationMillis = 700)) // Slightly longer fade-in
            // Optional: Add fadeOut if needed, though navigation usually handles screen removal
            // exit = fadeOut(animationSpec = tween(durationMillis = 300))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Logo
                if (!isPreview) {
                    Image(
                        painter = painterResource(id = R.drawable.nodica_icon), // Ensure this drawable exists
                        contentDescription = "Nodica Logo",
                        // Use onBackground for high contrast, or primary for brand color tint
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .size(150.dp) // Slightly smaller logo? Adjust as needed.
                            .padding(bottom = 24.dp)
                    )
                } else {
                    // Placeholder for preview
                    Box(Modifier.size(150.dp).padding(bottom = 24.dp))
                }

                // App Name
                Text(
                    text = "Nodica",
                    style = MaterialTheme.typography.displaySmall, // Adjust style for better fit?
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp)) // Reduced space

                // Tagline
                Text(
                    text = "Connect. Learn. Grow.",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(48.dp)) // More space before indicator

                // Loading Indicator
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp
                )
            }
        }
    }
}

// Previews remain the same
@Preview(showBackground = true, name = "Splash Light")
@Composable
fun SplashScreenPreviewLight() {
    NodicaTheme(darkTheme = false) {
// Simulate visible state for preview
        SplashContent(isVisible = true)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E1512, name = "Splash Dark")
@Composable
fun SplashScreenPreviewDark() {
    NodicaTheme(darkTheme = true) {
// Simulate visible state for preview
        SplashContent(isVisible = true)
    }
}