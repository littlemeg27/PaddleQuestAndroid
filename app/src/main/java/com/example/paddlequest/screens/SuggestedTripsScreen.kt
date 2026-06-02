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
    selectedStateFromMap: String?,       // from dropdown
    navController: NavController,
    onDismiss: () -> Unit,
    onSelectTrip: (MarkerData, MarkerData) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // State
    var effectiveLocation by remember { mutableStateOf(selectedLocation) }
    var currentState by remember { mutableStateOf(selectedStateFromMap ?: "Detecting…") }
    var locationLoading by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }

    var markers by remember { mutableStateOf(emptyList<MarkerData>()) }
    var isLoadingMarkers by remember { mutableStateOf(true) }

    // Priority logic: dropdown > selected pin > device GPS
    LaunchedEffect(selectedLocation, selectedStateFromMap) {
        Log.d("SuggestedTrips", "Init - pin=$selectedLocation, dropdownState=$selectedStateFromMap")

        // 1. Highest priority: State from dropdown
        if (!selectedStateFromMap.isNullOrBlank()) {
            currentState = selectedStateFromMap
            Log.d("SuggestedTrips", "Using dropdown state: $currentState")
        }
        // 2. Use selected pin location
        else if (selectedLocation != null) {
            effectiveLocation = selectedLocation
            val state = getStateFromLatLng(context, selectedLocation)
            currentState = state ?: "Unknown area"
            Log.d("SuggestedTrips", "State from pin: $currentState")
        }
        // 3. Fallback to device location
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
                        currentState = state ?: "Unknown area"
                        Log.d("SuggestedTrips", "Device location state: $currentState")
                    }
                } catch (e: Exception) {
                    locationError = "Failed to get device location"
                    Log.e("SuggestedTrips", "Location error", e)
                }
            }
            locationLoading = false
        }
    }

    // Load markers
    LaunchedEffect(currentState, effectiveLocation) {
        Log.d("SuggestedTrips", "Loading markers → state='$currentState', loc=$effectiveLocation")

        isLoadingMarkers = true

        if (effectiveLocation == null || currentState.contains("Detecting", ignoreCase = true) ||
            currentState.contains("Unknown", ignoreCase = true) || currentState.contains("No ", ignoreCase = true)) {
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
            } else if (effectiveLocation == null) {
                Text("No location selected.")
            } else if (markers.isEmpty()) {
                Text("No kayak ramps found in $currentState near this location.")
                Spacer(Modifier.height(8.dp))
                Text("Try selecting a different pin or state.", style = MaterialTheme.typography.bodySmall)
            } else {
                Text(
                    "Ramps near ${String.format(Locale.US, "%.4f", effectiveLocation!!.latitude)}, " +
                            "${String.format(Locale.US, "%.4f", effectiveLocation!!.longitude)} ($currentState)",
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))

                LazyColumn {
                    val grouped = groupRampsByWaterbody(markers)
                    grouped.forEach { group ->
                        item { Text(group.waterbody, style = MaterialTheme.typography.titleLarge) }

                        val sorted = group.ramps.sortedBy { haversineDistance(effectiveLocation!!, it.getLatLng()) }

                        sorted.windowed(2, 1).forEach { (putIn, takeOut) ->
                            val distMiles = haversineDistance(putIn.getLatLng(), takeOut.getLatLng()) * 0.621371
                            item {
                                Card(
                                    onClick = { onSelectTrip(putIn, takeOut) },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                                ) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text("${putIn.accessName} → ${takeOut.accessName}", style = MaterialTheme.typography.titleMedium)
                                        Text("Distance: ${String.format(Locale.US, "%.1f", distMiles)} miles")
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