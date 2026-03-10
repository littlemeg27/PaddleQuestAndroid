package com.example.paddlequest.operations

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.paddlequest.navigation.Screen
import com.example.paddlequest.ramps.SelectedPinViewModel
import com.example.paddlequest.screens.FloatPlanScreen
import com.example.paddlequest.screens.MapScreen
import com.example.paddlequest.screens.ProfileScreen
import com.example.paddlequest.screens.SettingsScreen
import com.example.paddlequest.screens.SignInScreen
import com.example.paddlequest.screens.SuggestedTripsScreen
import com.example.paddlequest.screens.WeatherScreen
import com.google.android.gms.maps.model.LatLng

@Composable
fun PaddleQuestApp() {
    val navController = rememberNavController()
    val selectedPinViewModel: SelectedPinViewModel = viewModel()

    val bottomNavItems = listOf(
        Screen.Map,
        Screen.FloatPlan,
        Screen.Weather,
        Screen.Profile,
        Screen.SuggestedTripsScreen,
        Screen.Settings
    )

    Scaffold(
        bottomBar =
            {
                NavigationBar {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination

                    bottomNavItems.forEach { screen ->
                        val selected =
                            currentDestination?.hierarchy?.any { it.route == screen.route } == true

                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (selected) screen.icon else when (screen) {
                                        is Screen.Map -> Icons.Outlined.Place
                                        is Screen.FloatPlan -> Icons.Outlined.Create
                                        is Screen.Weather -> Icons.Outlined.Cloud
                                        is Screen.Profile -> Icons.Outlined.Person
                                        is Screen.SuggestedTripsScreen -> Icons.Outlined.Map
                                        is Screen.Settings -> Icons.Outlined.Settings
                                        else -> Icons.Outlined.AccountCircle
                                    },
                                    contentDescription = screen.label
                                )
                            },
                            label = { Text(screen.label) },
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route)
                                {
                                    popUpTo(navController.graph.findStartDestination().id)
                                    {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.SignIn.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.SignIn.route) { SignInScreen(navController) }
            composable(Screen.Map.route) {
                MapScreen(
                    navController = navController,
                    selectedPinViewModel = selectedPinViewModel
                )
            }
            composable(
                route = Screen.FloatPlan.route + "?putIn={putIn}&takeOut={takeOut}",
                arguments = listOf(
                    navArgument("putIn") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("takeOut") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val putIn = backStackEntry.arguments?.getString("putIn")
                val takeOut = backStackEntry.arguments?.getString("takeOut")
                FloatPlanScreen(putIn = putIn, takeOut = takeOut)
            }
            composable(Screen.Weather.route) { WeatherScreen() }
            composable(Screen.Profile.route) { ProfileScreen() }
            composable(
                route = "suggested_trips/{lat}/{lon}",
                arguments = listOf(
                    navArgument("lat") { type = NavType.StringType },
                    navArgument("lon") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val lat = backStackEntry.arguments?.getString("lat")?.toDoubleOrNull() ?: 0.0
                val lon = backStackEntry.arguments?.getString("lon")?.toDoubleOrNull() ?: 0.0

                SuggestedTripsScreen(
                    selectedLocation = LatLng(lat, lon),
                    selectedStateFromMap = null,
                    navController = navController,
                    onDismiss = { navController.popBackStack() },
                    onSelectTrip = { putIn, takeOut ->
                        navController.navigate(
                            Screen.FloatPlan.route + "?putIn=${putIn.accessName}&takeOut=${takeOut.accessName}"
                        )
                    }
                )
            }

            composable(Screen.Settings.route) { SettingsScreen() }

            // Keep your existing SuggestedTripsScreen route for bottom bar taps (no params)
            composable(Screen.SuggestedTripsScreen.route) {
                val selectedLocation by selectedPinViewModel.selectedPin.observeAsState()
                val selectedState by selectedPinViewModel.selectedState.observeAsState()
                SuggestedTripsScreen(
                    selectedLocation = selectedLocation,
                    selectedStateFromMap = selectedState,
                    navController = navController,
                    onDismiss = { navController.popBackStack() },
                    onSelectTrip = { putIn, takeOut ->
                        navController.navigate(
                            Screen.FloatPlan.route + "?putIn=${putIn.accessName}&takeOut=${takeOut.accessName}"
                        )
                    }
                )
            }
        }
    }
}