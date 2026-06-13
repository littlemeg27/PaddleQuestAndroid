package com.example.paddlequest.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.paddlequest.location.getStateFromLatLng
import com.example.paddlequest.ramps.MarkerData
import com.example.paddlequest.ramps.groupRampsByWaterbody
import com.example.paddlequest.ramps.haversineDistance
import com.example.paddlequest.ramps.loadMarkersForState
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.tasks.await
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestedTripsScreen(
    selectedLocation: LatLng?,           // from map pin
    selectedStateFromMap: String?,       // from dropdown (highest priority)
    navController: NavController,
    onDismiss: () -> Unit,
    onSelectTrip: (MarkerData, MarkerData) -> Unit
) {
    val context = LocalContext.current

    var effectiveLocation by remember { mutableStateOf(selectedLocation) }
    var currentState by remember { mutableStateOf(selectedStateFromMap ?: "Detecting…") }
    var locationLoading by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }

    var markers by remember { mutableStateOf(emptyList<MarkerData>()) }
    var isLoadingMarkers by remember { mutableStateOf(true) }

    // Priority: Dropdown > Pin > Device GPS
    LaunchedEffect(selectedLocation, selectedStateFromMap) {
        Log.d("SuggestedTrips", "Init - pin=$selectedLocation, dropdown=$selectedStateFromMap")

        // 1. Dropdown state has highest priority
        if (!selectedStateFromMap.isNullOrBlank()) {
            currentState = selectedStateFromMap
            // Keep pin location if available for better distance sorting
            if (selectedLocation != null) effectiveLocation = selectedLocation
            Log.d("SuggestedTrips", "Using dropdown state: $currentState")
        }
        // 2. Pin location with strong regional fallback
        else if (selectedLocation != null) {
            effectiveLocation = selectedLocation

            val state = getStateFromLatLng(context, selectedLocation)
            currentState = state ?: when {
                // Catawba River area (your main testing zone)
                selectedLocation.latitude in 34.5..36.0 && selectedLocation.longitude in -81.5..-80.0 -> "North Carolina"
                selectedLocation.latitude in 34.7..35.3 && selectedLocation.longitude in -81.3..-80.7 -> "South Carolina"
                // Default emulator fallback
                selectedLocation.latitude in 37.0..38.0 && selectedLocation.longitude in -123.0..-122.0 -> "California"
                else -> "North Carolina"
            }
            Log.d("SuggestedTrips", "Resolved from pin: $currentState (lat=${selectedLocation.latitude}, lon=${selectedLocation.longitude})")
        }
        // 3. Device location fallback
        else {
            Log.d("SuggestedTrips", "No data passed → trying device location")
            locationLoading = true

            val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

            if (hasFine || hasCoarse) {
                try {
                    val fused = LocationServices.getFusedLocationProviderClient(context)
                    val loc = fused.lastLocation.await()
                    if (loc != null) {
                        effectiveLocation = LatLng(loc.latitude, loc.longitude)
                        val state = getStateFromLatLng(context, effectiveLocation!!)
                        currentState = state ?: "North Carolina"
                        Log.d("SuggestedTrips", "Device location state: $currentState")
                    }
                } catch (e: Exception) {
                    locationError = "Failed to get device location"
                    Log.e("SuggestedTrips", "Location error", e)
                    currentState = "North Carolina"
                }
            } else {
                locationError = "Location permission needed"
                currentState = "North Carolina"
            }
            locationLoading = false
        }
    }

    // Load markers when state or location changes
    LaunchedEffect(currentState, effectiveLocation) {
        Log.d("SuggestedTrips", "Loading markers → state='$currentState', loc=$effectiveLocation")

        isLoadingMarkers = true

        if (effectiveLocation == null ||
            currentState.contains("Detecting", ignoreCase = true) ||
            currentState.contains("Unknown", ignoreCase = true)) {
            markers = emptyList()
            isLoadingMarkers = false
            return@LaunchedEffect
        }

        try {
            val loaded = loadMarkersForState(context, currentState)
            markers = loaded
            Log.d("SuggestedTrips", "Loaded ${loaded.size} ramps for $currentState")
        } catch (e: Throwable) {
            Log.e("SuggestedTrips", "Load failed", e)
            markers = emptyList()
        } finally {
            isLoadingMarkers = false
        }
    }

    // UI
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Suggested Trips") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (locationLoading || isLoadingMarkers) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Loading nearby ramps...")
            } else if (locationError != null) {
                Text(locationError!!, color = MaterialTheme.colorScheme.error)
            } else if (markers.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No kayak ramps found in $currentState near this location.")
                    Spacer(Modifier.height(8.dp))
                    Text("Try selecting a different pin on the Catawba River or using the state dropdown.",
                        style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
            } else {
                Text(
                    "Ramps near ${String.format(Locale.US, "%.4f", effectiveLocation?.latitude ?: 0.0)}, " +
                            "${String.format(Locale.US, "%.4f", effectiveLocation?.longitude ?: 0.0)} ($currentState)",
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))

                LazyColumn {
                    val grouped = groupRampsByWaterbody(markers)
                    grouped.forEach { group ->
                        item {
                            Text(group.waterbody, style = MaterialTheme.typography.titleLarge)
                        }

                        val sortedRamps = group.ramps.sortedBy {
                            haversineDistance(effectiveLocation!!, it.getLatLng())
                        }

                        for (i in 0 until sortedRamps.size - 1) {
                            val putIn = sortedRamps[i]
                            val takeOut = sortedRamps[i + 1]
                            val distMiles = haversineDistance(putIn.getLatLng(), takeOut.getLatLng()) * 0.621371
                            val minTime = (distMiles / 4).toInt().coerceAtLeast(1)
                            val maxTime = (distMiles / 2).toInt().coerceAtLeast(minTime)

                            item {
                                Card(
                                    onClick = { onSelectTrip(putIn, takeOut) },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                                ) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text("${putIn.accessName} → ${takeOut.accessName}", style = MaterialTheme.typography.titleMedium)
                                        Text("Distance: ${String.format(Locale.US, "%.1f", distMiles)} miles")
                                        Text("Estimated time: $minTime–$maxTime hours")
                                        Text("River: ${putIn.riverName}")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Close")
            }
        }
    }
}