package com.example.paddlequest.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object SignIn : Screen("signin", "Sign In", Icons.Default.Login)
    object Map : Screen("map", "Map", Icons.Default.Place)
    object FloatPlan : Screen("floatplan", "Float Plan", Icons.Default.Create)
    object Weather : Screen("weather", "Weather", Icons.Default.Cloud)
    object Profile : Screen("profile", "Profile", Icons.Default.Person)

    // Base route for bottom nav + deep links
    object SuggestedTripsScreen : Screen("suggestedTripsScreen", "Trips", Icons.Default.Map)

    object Settings : Screen("settings", "Settings", Icons.Default.Settings)

    // Helper for parameterized navigation
    companion object {
        const val SUGGESTED_TRIPS_ROUTE = "suggestedTripsScreen/{lat}/{lon}/{state?}"

        fun suggestedTripsDestination(lat: Double, lon: Double, state: String? = null): String {
            return if (state != null) {
                "suggestedTripsScreen/$lat/$lon/$state"
            } else {
                "suggestedTripsScreen/$lat/$lon"
            }
        }
    }
}

data class NavItem(
    val screen: Screen,
    val unselectedIcon: ImageVector,
    val hasNews: Boolean = false
)

val BottomNavItems = listOf(
    NavItem(
        screen = Screen.Map,
        unselectedIcon = Icons.Outlined.Place,
        hasNews = false
    ),
    NavItem(
        screen = Screen.FloatPlan,
        unselectedIcon = Icons.Outlined.Create,
        hasNews = false
    ),
    NavItem(
        screen = Screen.Weather,
        unselectedIcon = Icons.Outlined.Cloud,
        hasNews = true
    ),
    NavItem(
        screen = Screen.Profile,
        unselectedIcon = Icons.Outlined.Person,
        hasNews = false
    ),
    NavItem(
        screen = Screen.SuggestedTripsScreen,
        unselectedIcon = Icons.Outlined.Map,
        hasNews = false
    ),
    NavItem(
        screen = Screen.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        hasNews = false
    )
)