package com.jamie.nodica.features.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.jamie.nodica.features.groups.group.GroupsScreen // My Groups List
import com.jamie.nodica.features.messages.AggregatedMessagesScreen // Chat List
import com.jamie.nodica.features.profile_management.ProfileScreen // Profile View/Edit

/**
 * The main container screen after authentication/setup, hosting the bottom navigation bar
 * and the content for each primary section (Discover, My Groups, Chats, Profile).
 *
 * @param outerNavController The main application NavController, used by child screens
 *                           to navigate to destinations outside this main structure (e.g., MessageScreen).
 */
@Composable
fun MainScreen(outerNavController: NavHostController) {
    // This innerNavController manages navigation *between* the main bottom bar destinations.
    val innerNavController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar { // Use NavigationBar for Material 3
                val navBackStackEntry by innerNavController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                BottomNavItem.items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            // Navigate on the *inner* controller for tab switching
                            innerNavController.navigate(screen.route) {
                                // Pop up to the start destination of the inner graph to
                                // avoid building up stack within tabs.
                                popUpTo(innerNavController.graph.findStartDestination().id) {
                                    saveState = true // Save state of popped destinations.
                                }
                                // Avoid multiple copies of the same destination when re-selecting.
                                launchSingleTop = true
                                // Restore state when re-navigating to a previously selected item.
                                restoreState = true
                            }
                        },
                        // Optional: customize colors for selected/unselected states
                        // colors = NavigationBarItemDefaults.colors(...)
                    )
                }
            }
        }
    ) { innerPadding ->
        // This NavHost uses the innerNavController to display the content
        // corresponding to the selected bottom navigation item.
        NavHost(
            navController = innerNavController,
            startDestination = BottomNavItem.Home.route, // Start on Discover/Home tab
            modifier = Modifier.padding(innerPadding) // Apply padding from Scaffold to avoid overlap
        ) {
            // Define composables for each bottom navigation destination.
            // Pass the outerNavController to screens that might need to navigate globally.
            composable(BottomNavItem.Home.route) {
                // Content for the 'Discover' tab
                HomeScreen(outerNavController = outerNavController)
            }
            composable(BottomNavItem.Groups.route) {
                // Content for the 'My Groups' tab
                GroupsScreen(outerNavController = outerNavController)
            }
            composable(BottomNavItem.Chat.route) {
                // Content for the 'Chats' tab
                AggregatedMessagesScreen(outerNavController = outerNavController)
            }
            composable(BottomNavItem.Profile.route) {
                // Content for the 'Profile' tab
                ProfileScreen(outerNavController = outerNavController)
            }
        }
    }
}