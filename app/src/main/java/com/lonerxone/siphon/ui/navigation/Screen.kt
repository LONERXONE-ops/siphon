package com.lonerxone.siphon.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Waitlist : Screen("waitlist", "Waitlist", Icons.Default.ListAlt)
    object Browser : Screen("browser", "Browser", Icons.Default.Language)
    object Library : Screen("library", "Library", Icons.Default.Folder)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}
