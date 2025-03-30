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
import com.jamie.nodica.features.groups.group.CreateGroupScreen
import com.jamie.nodica.features.home.MainScreen // Import MainScreen, which contains bottom nav
import com.jamie.nodica.features.messages.MessageScreen
import com.jamie.nodica.features.profile.ProfileSetupScreen
import com.jamie.nodica.features.splash.SplashScreen
import timber.log.Timber

/**
 * Defines the main navigation graph for the application.
 *
 * @param navController The primary NavHostController for the application.
 * @param modifier Modifier for the NavHost container.
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH, // Start with splash for auth/profile check
        modifier = modifier
    ) {
        // Initial screens
        composable(Routes.SPLASH) {
            Timber.v("Navigating to Splash")
            SplashScreen(navController)
        }

        composable(Routes.ONBOARDING) {
            Timber.v("Navigating to Onboarding")
            OnboardingScreen(navController)
        }
        composable(Routes.AUTH) {
            Timber.v("Navigating to Auth")
            AuthScreen(navController)
        }
        composable(Routes.PROFILE_SETUP) {
            Timber.v("Navigating to Profile Setup")
            ProfileSetupScreen(navController)
        }

        // Main application hub screen with bottom navigation
        composable(Routes.HOME) {
            Timber.v("Navigating to Home (MainScreen)")
            // MainScreen manages its own internal navigation for the bottom bar tabs.
            // Pass the main navController (as outerNavController) to allow tabs
            // to navigate to destinations outside the bottom nav structure (e.g., MessageScreen).
            MainScreen(outerNavController = navController)
        }

        // Detail screen for individual group messages
        composable(
            route = "${Routes.MESSAGES}/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId")
            Timber.v("Navigating to Messages for groupId: $groupId")
            if (groupId != null) {
                // MessageScreen uses the main navController for 'Up' navigation.
                MessageScreen(navController = navController, groupId = groupId)
            } else {
                // Fallback if groupId is missing (should ideally not happen with proper navigation calls)
                Timber.e("Error: MessageScreen destination called without groupId argument.")
                Text("Error: Group ID missing.") // Simple error display
                // Consider navigating back automatically: LaunchedEffect(Unit) { navController.popBackStack() }
            }
        }

        // Screen for creating a new group
        composable(Routes.CREATE_GROUP) {
            Timber.v("Navigating to Create Group")
            // CreateGroupScreen uses the main navController to navigate 'Up' or back on completion.
            CreateGroupScreen(navController = navController)
        }

        // Note: Routes for individual tabs (Home content, Groups list, Chat list, Profile view/edit)
        // are defined within the NavHost *inside* the MainScreen composable.
    }
}