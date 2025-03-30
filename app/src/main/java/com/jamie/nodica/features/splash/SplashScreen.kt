package com.jamie.nodica.features.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import com.jamie.nodica.R
import com.jamie.nodica.features.navigation.Routes
import com.jamie.nodica.ui.theme.NodicaTheme
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import timber.log.Timber

@Composable
fun SplashScreen(
    navController: NavController,
    viewModel: SplashViewModel = koinViewModel()
) {
    // Observe destination state from the ViewModel
    val destinationState by viewModel.destination.collectAsState()

    // Local flag to ensure navigation happens only once
    var hasNavigated by remember { mutableStateOf(false) }

    // Control visibility for fade-in animation
    var showContent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100) // Delay before fade-in starts
        showContent = true
    }

    // Navigate when destination is determined and not already navigated
    LaunchedEffect(destinationState) {
        if (!hasNavigated && destinationState is SplashDestination.Navigate) {
            hasNavigated = true
            val route = (destinationState as SplashDestination.Navigate).route
            Timber.i("Splash: Navigating to $route")
            navController.navigate(route) {
                popUpTo(Routes.SPLASH) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    // Render the splash UI content
    SplashContent(isVisible = showContent)
}

@Composable
private fun SplashContent(isVisible: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp)
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(durationMillis = 700))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // App Logo
                Image(
                    painter = painterResource(id = R.drawable.nodica_icon),
                    contentDescription = "Nodica Logo",
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .size(150.dp)
                        .padding(bottom = 24.dp)
                )
                // App Name
                Text(
                    text = "Nodica",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Connect. Learn. Grow.",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(48.dp))
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Splash Light")
@Composable
fun SplashScreenPreviewLight() {
    NodicaTheme(darkTheme = false) {
        SplashContent(isVisible = true)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E1512, name = "Splash Dark")
@Composable
fun SplashScreenPreviewDark() {
    NodicaTheme(darkTheme = true) {
        SplashContent(isVisible = true)
    }
}
