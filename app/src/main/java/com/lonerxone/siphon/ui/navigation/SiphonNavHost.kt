package com.lonerxone.siphon.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lonerxone.siphon.ui.browser.BrowserScreen
import com.lonerxone.siphon.ui.home.HomeScreen
import com.lonerxone.siphon.ui.library.LibraryScreen
import com.lonerxone.siphon.ui.settings.SettingsScreen
import com.lonerxone.siphon.ui.theme.DarkBackground
import com.lonerxone.siphon.ui.theme.DarkBorder
import com.lonerxone.siphon.ui.theme.DarkElevated
import com.lonerxone.siphon.ui.theme.TextMuted
import com.lonerxone.siphon.ui.theme.TextPrimary
import com.lonerxone.siphon.ui.waitlist.WaitlistScreen

@Composable
fun SiphonNavHost(sharedUrl: String? = null) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // The browser is created on first visit and then kept alive, so its page and sound state survive tab changes
    var browserOpened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(currentRoute) {
        if (currentRoute == Screen.Browser.route) browserOpened = true
    }
    val browserVisible = currentRoute == Screen.Browser.route
    val showBrowser = browserOpened || browserVisible

    val screens = listOf(
        Screen.Home,
        Screen.Waitlist,
        Screen.Browser,
        Screen.Library,
        Screen.Settings
    )

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        bottomBar = {
            Column {
                HorizontalDivider(thickness = 0.5.dp, color = DarkBorder)
                NavigationBar(
                    containerColor = DarkBackground,
                    contentColor = TextPrimary,
                    tonalElevation = 0.dp
                ) {
                    screens.forEach { screen ->
                        val isSelected = currentRoute == screen.route
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { navigateTo(screen.route) },
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = {
                                Text(
                                    text = screen.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = TextPrimary,
                                selectedTextColor = TextPrimary,
                                indicatorColor = DarkElevated,
                                unselectedIconColor = TextMuted,
                                unselectedTextColor = TextMuted
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { fadeIn(animationSpec = tween(180)) },
                exitTransition = { fadeOut(animationSpec = tween(100)) }
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        sharedUrl = sharedUrl,
                        onOpenBrowser = { navigateTo(Screen.Browser.route) }
                    )
                }
                composable(Screen.Waitlist.route) {
                    WaitlistScreen()
                }
                composable(Screen.Browser.route) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(DarkBackground)
                    )
                }
                composable(Screen.Library.route) {
                    LibraryScreen()
                }
                composable(Screen.Settings.route) {
                    SettingsScreen()
                }
            }

            if (showBrowser) {
                Box(
                    modifier = if (browserVisible) Modifier.fillMaxSize() else Modifier.size(0.dp)
                ) {
                    BrowserScreen(
                        visible = browserVisible,
                        onOpenDownloads = { navigateTo(Screen.Home.route) }
                    )
                }
            }
        }
    }
}