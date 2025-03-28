package com.jamie.nodica.features.home

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue // Import required
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy // Import required
import androidx.navigation.NavGraph.Companion.findStartDestination // Import required
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.jamie.nodica.features.groups.group.GroupsScreen
import com.jamie.nodica.features.messages.AggregatedMessagesScreen
import com.jamie.nodica.features.profile_management.ProfileScreen // Import ProfileScreen

@Composable
fun MainScreen(outerNavController: NavHostController) { // Accept the outer NavController
    // Internal NavController for managing the bottom bar destinations
    val innerNavController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by innerNavController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                BottomNavItem.items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        // Check hierarchy for nested graph support (optional but good practice)
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            innerNavController.navigate(screen.route) {
                                // Pop up to the start destination of the graph to
                                // avoid building up a large stack of destinations
                                // on the back stack as users select items
                                popUpTo(innerNavController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                // Avoid multiple copies of the same destination when
                                // re-selecting the same item
                                launchSingleTop = true
                                // Restore state when re-selecting a previously selected item
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        // Internal NavHost for the main tabs/sections
        NavHost(
            navController = innerNavController, // Use the internal controller here
            startDestination = BottomNavItem.Home.route,
            modifier = Modifier.padding(innerPadding) // Apply padding from Scaffold
        ) {
            // Pass the outerNavController to screens that need to navigate outside the tabs
            composable(BottomNavItem.Home.route) { HomeScreen(outerNavController = outerNavController) }
            composable(BottomNavItem.Groups.route) { GroupsScreen(outerNavController = outerNavController) }
            composable(BottomNavItem.Chat.route) { AggregatedMessagesScreen(outerNavController = outerNavController) }
            composable(BottomNavItem.Profile.route) { ProfileScreen(outerNavController = outerNavController) }
        }
    }
}