package com.example.paddlequest.screens

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
import androidx.navigation.NavController
import com.example.paddlequest.ramps.MarkerData
import com.example.paddlequest.ramps.groupRampsByWaterbody
import com.example.paddlequest.ramps.haversineDistance
import com.example.paddlequest.ramps.loadMarkersForState
import com.google.android.gms.maps.model.LatLng
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestedTripsScreen(
    selectedLocation: LatLng?,
    selectedStateFromMap: String?,
    navController: NavController,
    onDismiss: () -> Unit,
    onSelectTrip: (MarkerData, MarkerData) -> Unit
) {
    val context = LocalContext.current

    var effectiveLocation by remember { mutableStateOf(selectedLocation) }
    var currentState by remember { mutableStateOf(selectedStateFromMap ?: "Detecting…") }
    var markers by remember { mutableStateOf(emptyList<MarkerData>()) }
    var anchorRampName by remember { mutableStateOf("your selected boat ramp") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(selectedLocation, selectedStateFromMap) {
        Log.d("SuggestedTrips", "=== GRABBING CORRECT BOAT RAMP ===")
        Log.d("SuggestedTrips", "Clicked location: $selectedLocation")

        val state = selectedStateFromMap ?: "North Carolina"
        currentState = state
        effectiveLocation = selectedLocation

        isLoading = true
        try {
            val allRamps = loadMarkersForState(context, state)

            if (selectedLocation == null) {
                markers = allRamps.take(30)
                anchorRampName = "North Carolina"
            } else {
                // === STEP 1: Find the SINGLE closest ramp to the exact tap ===
                val closestRamp = allRamps.minByOrNull {
                    haversineDistance(selectedLocation, it.getLatLng())
                }

                val distanceToClosest = if (closestRamp != null) {
                    haversineDistance(selectedLocation, closestRamp.getLatLng())
                } else 999.0

                Log.d("SuggestedTrips", "Closest ramp in DB: ${closestRamp?.accessName} (distance: ${"%.2f".format(distanceToClosest)} km)")

                // === STEP 2: Only trust it as the anchor if it is VERY close (< 1.5 km) ===
                anchorRampName = if (distanceToClosest < 1.5 && closestRamp != null) {
                    closestRamp.accessName
                } else {
                    "your selected location"
                }

                // === STEP 3: Prefer ramps on the same waterbody as the closest ramp (if we trust it) ===
                val preferredWaterbody = if (distanceToClosest < 1.5 && closestRamp != null) {
                    closestRamp.riverName.ifBlank { closestRamp.otherName }
                } else null

                val filtered = if (preferredWaterbody != null) {
                    allRamps.filter { ramp ->
                        val dist = haversineDistance(selectedLocation, ramp.getLatLng())
                        val sameWaterbody = ramp.riverName.equals(preferredWaterbody, ignoreCase = true) ||
                                ramp.otherName.equals(preferredWaterbody, ignoreCase = true)
                        dist < 50.0 && sameWaterbody
                    }
                } else {
                    // Fallback: just use distance from the clicked point
                    allRamps.filter { haversineDistance(selectedLocation, it.getLatLng()) < 50.0 }
                }

                markers = filtered.ifEmpty {
                    allRamps.filter { haversineDistance(selectedLocation, it.getLatLng()) < 50.0 }
                }

                Log.d("SuggestedTrips", "Final ramps used: ${markers.size} (anchored to: $anchorRampName)")
            }
        } catch (e: Exception) {
            Log.e("SuggestedTrips", "Failed to load", e)
            markers = emptyList()
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Suggested Trips") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            if (isLoading) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("Finding trips near $anchorRampName...")
            } else if (markers.isEmpty()) {
                Text(
                    "No suitable ramps found near the boat ramp you tapped.\n\nTry clicking directly on a marker on the Catawba River or Mountain Island Lake.",
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    "Trips near $anchorRampName",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))

                LazyColumn {
                    val grouped = groupRampsByWaterbody(markers)

                    grouped.forEach { group ->
                        item {
                            Text(
                                group.waterbody,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        val sorted = group.ramps.sortedBy {
                            haversineDistance(effectiveLocation ?: it.getLatLng(), it.getLatLng())
                        }

                        for (i in 0 until sorted.size - 1) {
                            val putIn = sorted[i]
                            val takeOut = sorted[i + 1]
                            val distMiles = haversineDistance(putIn.getLatLng(), takeOut.getLatLng()) * 0.621371
                            val minTime = (distMiles / 4).toInt().coerceAtLeast(1)
                            val maxTime = (distMiles / 2).toInt().coerceAtLeast(minTime)

                            item {
                                Card(
                                    onClick = { onSelectTrip(putIn, takeOut) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
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