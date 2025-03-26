package com.jamie.nodica.features.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.jamie.nodica.features.auth.AuthScreen
import com.jamie.nodica.features.auth.OnboardingScreen
import com.jamie.nodica.features.home.MainScreen
import com.jamie.nodica.features.messages.MessageScreen
import com.jamie.nodica.features.profile.ProfileSetupScreen
import com.jamie.nodica.features.splash.SplashScreen
import com.jamie.nodica.features.groups.group.CreateGroupScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        modifier = modifier
    ) {
        composable(Routes.SPLASH) { SplashScreen(navController) }
        composable(Routes.ONBOARDING) { OnboardingScreen(navController) }
        composable(Routes.AUTH) { AuthScreen(navController) }
        composable(Routes.PROFILE_SETUP) { ProfileSetupScreen(navController) }
        composable(Routes.HOME) { MainScreen() }
        composable("${Routes.MESSAGES}/{groupId}") { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId")
            if (groupId != null) {
                MessageScreen(navController = navController, groupId = groupId)
            } else {
                // Optional fallback
                Text("Group ID is missing. Cannot open chat.")
            }
        }
        // New Create Group screen
        composable(Routes.CREATE_GROUP) { CreateGroupScreen(navController) }
    }
}
