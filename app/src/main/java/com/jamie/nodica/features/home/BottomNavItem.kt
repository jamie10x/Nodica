package com.jamie.nodica.features.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat // AutoMirrored version
import androidx.compose.material.icons.filled.Groups // Specific icon for groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Represents the destinations accessible via the main bottom navigation bar.
 *
 * @param route The unique route string for navigation.
 * @param label The text label displayed for the item.
 * @param icon The vector icon displayed for the item.
 */
sealed class BottomNavItem(val route: String, val label: String, val icon: ImageVector) {
    object Home : BottomNavItem(
        route = "home", // Corresponds to Discover Groups
        label = "Discover", // Renamed label for clarity
        icon = Icons.Default.Home // Or Icons.Default.Search if Discover is primary focus
    )
    object Groups : BottomNavItem(
        route = "groups", // Corresponds to My Joined Groups
        label = "My Groups", // Renamed label
        icon = Icons.Default.Groups // More specific icon
    )
    object Chat : BottomNavItem(
        route = "chat", // Corresponds to Aggregated Chat list
        label = "Chats", // Renamed label
        icon = Icons.AutoMirrored.Filled.Chat // Specific chat icon
    )
    object Profile : BottomNavItem(
        route = "profile", // Corresponds to User's own profile view/edit
        label = "Profile",
        icon = Icons.Default.Person
    )

    companion object {
        /** A list of all defined bottom navigation items in order. */
        val items = listOf(Home, Groups, Chat, Profile)
    }
}