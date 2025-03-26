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
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.jamie.nodica.R
import com.jamie.nodica.features.navigation.Routes
import com.jamie.nodica.ui.theme.NodicaTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun SplashScreen(
    navController: NavController,
    viewModel: SplashViewModel = koinViewModel()
) {
    val isLoadingDone by viewModel.isLoadingDone.collectAsState()

    LaunchedEffect(isLoadingDone) {
        if (isLoadingDone) {
            navController.navigate(Routes.ONBOARDING) {
                popUpTo("splash") { inclusive = true }
            }
        }
    }

    SplashContent()
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
        AnimatedVisibility(visible = true, enter = fadeIn()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (!isPreview) {
                    Image(
                        painter = painterResource(id = R.drawable.nodica_icon),
                        contentDescription = "Nodica Logo",
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                        modifier = Modifier
                            .size(250.dp)
                            .padding(bottom = 16.dp)
                    )
                }
                Text(
                    text = "Nodica",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 42.sp),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Connect. Learn. Grow.",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E1512)
@Composable
fun SplashScreenPreview() {
    NodicaTheme(darkTheme = true) {
        SplashContent()
    }
}
