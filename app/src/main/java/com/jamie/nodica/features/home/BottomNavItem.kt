package com.jamie.nodica.features.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(val route: String, val label: String, val icon: ImageVector) {
    object Home : BottomNavItem("home", "Home", Icons.Default.Home)
    object Groups : BottomNavItem("groups", "Groups", Icons.Default.Favorite)
    object Chat : BottomNavItem("chat", "Chat", Icons.Default.Email)
    object Profile : BottomNavItem("profile", "Profile", Icons.Default.Person)

    companion object {
        val items = listOf(Home, Groups, Chat, Profile)
    }
}
