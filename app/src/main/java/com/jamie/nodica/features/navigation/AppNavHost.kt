package com.jamie.nodica.features.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.jamie.nodica.features.auth.AuthScreen
import com.jamie.nodica.features.auth.OnboardingScreen
import com.jamie.nodica.features.home.MainScreen // Import MainScreen
import com.jamie.nodica.features.messages.MessageScreen
import com.jamie.nodica.features.profile.ProfileSetupScreen
import com.jamie.nodica.features.splash.SplashScreen
import com.jamie.nodica.features.groups.group.CreateGroupScreen
// import com.jamie.nodica.features.home.BottomNavItem // Not needed here
// import com.jamie.nodica.features.profile_management.ProfileScreen // Profile is inside MainScreen now

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH, // Assuming Splash handles auth check
        modifier = modifier
    ) {
        composable(Routes.SPLASH) { SplashScreen(navController) }
        composable(Routes.ONBOARDING) { OnboardingScreen(navController) }
        composable(Routes.AUTH) { AuthScreen(navController) }
        composable(Routes.PROFILE_SETUP) { ProfileSetupScreen(navController) }

        // Route for the main screen containing bottom navigation
        composable(Routes.HOME) {
            // Pass the AppNavHost's NavController to MainScreen
            MainScreen(outerNavController = navController)
        }

        // Route for individual message screens (navigated FROM within MainScreen's tabs)
        composable(
            route = "${Routes.MESSAGES}/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId")
            if (groupId != null) {
                // Pass the AppNavHost's NavController for potential "up" navigation
                MessageScreen(navController = navController, groupId = groupId)
            } else {
                // Handle missing group ID - navigate back or show error
                Text("Error: Group ID missing.")
                // Consider navController.popBackStack() here
            }
        }

        // Route for creating a group (navigated FROM within MainScreen's tabs)
        composable(Routes.CREATE_GROUP) {
            // Pass the AppNavHost's NavController for navigation
            CreateGroupScreen(navController = navController)
        }

        // Note: The routes for the individual tabs (Home, Groups, Chat, Profile)
        // are now handled *inside* the NavHost within MainScreen.
        // composable(BottomNavItem.Profile.route) { ... } // REMOVE from here
    }
}