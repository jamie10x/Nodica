package com.jamie.nodica.features.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
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

    // Navigate when the destination is determined by the ViewModel
    LaunchedEffect(destinationState) {
        Timber.d("Splash: Destination state changed to $destinationState")
        if (destinationState is SplashDestination.Navigate) {
            val route = (destinationState as SplashDestination.Navigate).route
            Timber.d("Splash: Navigating to $route")
            navController.navigate(route) {
                // Pop the splash screen off the back stack so user can't navigate back to it
                popUpTo(Routes.SPLASH) { inclusive = true }
                // Avoid multiple instances of the destination screen
                launchSingleTop = true
            }
        }
    }

    // Show the splash content only while loading.
    // Once navigation happens, this screen will leave the composition.
    if (destinationState == SplashDestination.Loading) {
        SplashContent()
    } else {
        // Optionally keep showing SplashContent briefly even during navigation
        // Or display nothing / a placeholder while navigation transition occurs
        // SplashContent() // Uncomment if you want to keep showing it during transition
        Timber.d("Splash: Navigation triggered, SplashContent will recompose out.")
    }
}

@Composable
private fun SplashContent() {
    val isPreview = LocalInspectionMode.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        // Use a simpler visibility check, as the screen itself handles visibility via navigation state
        AnimatedVisibility(visible = true, enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(durationMillis = 500))) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // In preview mode, the painter resource might not load correctly depending on setup.
                // Using a placeholder or conditional logic might be needed for complex previews.
                if (!isPreview) {
                    Image(
                        painter = painterResource(id = R.drawable.nodica_icon), // Ensure this drawable exists
                        contentDescription = "Nodica Logo",
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                        modifier = Modifier
                            .size(180.dp) // Adjusted size
                            .padding(bottom = 24.dp) // Adjusted padding
                    )
                } else {
                    // Placeholder for preview if image doesn't load
                    Box(Modifier.size(180.dp).padding(bottom = 24.dp))
                }
                Text(
                    text = "Nodica",
                    style = MaterialTheme.typography.displayMedium, // Adjusted style
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Connect. Learn. Grow.",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant // Use a slightly muted color
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF4FBF6) // Light theme preview
@Composable
fun SplashScreenPreviewLight() {
    NodicaTheme(darkTheme = false) {
        SplashContent()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E1512) // Dark theme preview
@Composable
fun SplashScreenPreviewDark() {
    NodicaTheme(darkTheme = true) {
        SplashContent()
    }
}