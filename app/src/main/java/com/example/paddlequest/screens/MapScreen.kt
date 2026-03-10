package com.example.paddlequest.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.paddlequest.location.getStateFromLatLng
import com.example.paddlequest.navigation.Screen
import com.example.paddlequest.ramps.MarkerData
import com.example.paddlequest.ramps.SelectedPinViewModel
import com.example.paddlequest.ramps.loadMarkersForState
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    navController: NavController,
    selectedPinViewModel: SelectedPinViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var hasLocationPermission by remember { mutableStateOf(false) }

    var currentLocation by remember { mutableStateOf<LatLng?>(null) }
    var currentState by remember { mutableStateOf("Detecting location…") }
    var isLoadingLocation by remember { mutableStateOf(true) }

    var selectedState by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }

    var markers by remember { mutableStateOf(emptyList<MarkerData>()) }
    var isLoadingMarkers by remember { mutableStateOf(false) } // start false to avoid initial spin

    val availableStates = listOf(
        "Alabama", "Alaska", "Arizona", "Arkansas", "California", "Colorado", "Connecticut", "Delaware",
        "Florida", "Georgia", "Hawaii", "Idaho", "Illinois", "Indiana", "Iowa", "Kansas", "Kentucky",
        "Louisiana", "Maine", "Maryland", "Massachusetts", "Michigan", "Minnesota", "Mississippi",
        "Missouri", "Montana", "Nebraska", "Nevada", "New Hampshire", "New Jersey", "New Mexico",
        "New York", "North Carolina", "North Dakota", "Ohio", "Oklahoma", "Oregon", "Pennsylvania",
        "Rhode Island", "South Carolina", "South Dakota", "Tennessee", "Texas", "Utah", "Vermont",
        "Virginia", "Washington", "West Virginia", "Wisconsin", "Wyoming"
    )

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(35.2271, -80.8431), 10f) // Charlotte default
    }

    val selectedPin by selectedPinViewModel.selectedPin.observeAsState()

    suspend fun loadCurrentLocation() {
        if (!hasLocationPermission) {
            currentState = "Permission required"
            isLoadingLocation = false
            return
        }

        isLoadingLocation = true
        try {
            val location = fusedLocationClient.lastLocation.await()
            if (location != null) {
                val latLng = LatLng(location.latitude, location.longitude)
                currentLocation = latLng
                val state = getStateFromLatLng(context, latLng)
                currentState = state ?: "Unknown state"
                selectedPinViewModel.setSelectedState(currentState)
                Log.d("MapScreen", "Device location set: $latLng, state=$currentState")
            } else {
                currentState = "No recent location"
            }
        } catch (e: Exception) {
            currentState = "Location error"
            Log.e("MapScreen", "Location fetch failed", e)
        } finally {
            isLoadingLocation = false
        }
    }

    // Permission handling (unchanged)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasLocationPermission = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (hasLocationPermission) {
            scope.launch { loadCurrentLocation() }
        } else {
            currentState = "Location permission denied"
            isLoadingLocation = false
        }
    }

    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        hasLocationPermission = hasFine || hasCoarse

        if (hasLocationPermission) {
            loadCurrentLocation()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // ───────────────────────────────────────────────
    // Load markers when selectedState or currentState changes
    // ───────────────────────────────────────────────
    LaunchedEffect(key1 = selectedState, key2 = currentState) {
        Log.d("MapScreen", "Reload triggered - selectedState=$selectedState, currentState=$currentState")

        val stateToLoad = selectedState ?: currentState

        if (stateToLoad.isBlank() ||
            stateToLoad.contains("Detecting", ignoreCase = true) ||
            stateToLoad.contains("No location", ignoreCase = true) ||
            stateToLoad.contains("error", ignoreCase = true) ||
            stateToLoad.contains("Permission", ignoreCase = true) ||
            stateToLoad.contains("Unknown", ignoreCase = true)
        ) {
            Log.d("MapScreen", "Invalid state → clearing markers")
            markers = emptyList()
            isLoadingMarkers = false
            return@LaunchedEffect
        }

        Log.d("MapScreen", "Loading markers for state: $stateToLoad")
        isLoadingMarkers = true

        try {
            markers = loadMarkersForState(context, stateToLoad)
            Log.d("MapScreen", "Loaded ${markers.size} markers for $stateToLoad")
        } catch (e: Exception) {
            Log.e("MapScreen", "Marker load failed for $stateToLoad", e)
            markers = emptyList()
        } finally {
            isLoadingMarkers = false
        }
    }

    // ───────────────────────────────────────────────
    // NEW: Move camera when selected state changes
    // ───────────────────────────────────────────────
    LaunchedEffect(selectedState) {
        selectedState?.let { state ->
            selectedPinViewModel.setSelectedState(state)
            val stateCenter = when (state.lowercase()) {
                "alabama" -> LatLng(32.3182, -86.9023)
                "alaska" -> LatLng(64.2008, -149.4937)
                "arizona" -> LatLng(34.0489, -111.0937)
                "arkansas" -> LatLng(35.2010, -91.8318)
                "california" -> LatLng(36.7783, -119.4179)
                "colorado" -> LatLng(39.5501, -105.7821)
                "connecticut" -> LatLng(41.6032, -73.0877)
                "delaware" -> LatLng(38.9108, -75.5277)
                "florida" -> LatLng(27.6648, -81.5158)
                "georgia" -> LatLng(32.1656, -82.9001)
                "hawaii" -> LatLng(19.8968, -155.5828)
                "idaho" -> LatLng(44.0682, -114.7420)
                "illinois" -> LatLng(40.6331, -89.3985)
                "indiana" -> LatLng(40.5512, -85.6024)
                "iowa" -> LatLng(41.8780, -93.0977)
                "kansas" -> LatLng(39.0119, -98.4842)
                "kentucky" -> LatLng(37.8393, -84.2700)
                "louisiana" -> LatLng(30.9843, -91.9623)
                "maine" -> LatLng(45.2538, -69.4455)
                "maryland" -> LatLng(39.0458, -76.6413)
                "massachusetts" -> LatLng(42.4072, -71.3824)
                "michigan" -> LatLng(44.3148, -85.6024)
                "minnesota" -> LatLng(46.7296, -94.6859)
                "mississippi" -> LatLng(32.3547, -89.3985)
                "missouri" -> LatLng(37.9643, -91.8318)
                "montana" -> LatLng(46.8797, -110.3626)
                "nebraska" -> LatLng(41.4925, -99.9018)
                "nevada" -> LatLng(38.8026, -116.4194)
                "new hampshire" -> LatLng(43.1939, -71.5724)
                "new jersey" -> LatLng(40.0583, -74.4057)
                "new mexico" -> LatLng(34.5199, -105.8701)
                "new york" -> LatLng(40.7128, -74.0060)
                "north carolina" -> LatLng(35.7596, -79.0193) // Charlotte / Rock Hill area
                "north dakota" -> LatLng(47.1164, -101.2996)
                "ohio" -> LatLng(40.4173, -82.9071)
                "oklahoma" -> LatLng(35.4676, -97.5164)
                "oregon" -> LatLng(43.8041, -120.5542)
                "pennsylvania" -> LatLng(41.2033, -77.1945)
                "rhode island" -> LatLng(41.5801, -71.4774)
                "south carolina" -> LatLng(33.8361, -81.1637) // near Rock Hill
                "south dakota" -> LatLng(43.9695, -99.9018)
                "tennessee" -> LatLng(35.5175, -86.5804)
                "texas" -> LatLng(31.9686, -99.9018)
                "utah" -> LatLng(39.3209, -111.0937)
                "vermont" -> LatLng(44.5588, -72.5778)
                "virginia" -> LatLng(37.4316, -78.6569)
                "washington" -> LatLng(47.7511, -120.7401)
                "west virginia" -> LatLng(38.5976, -80.4549)
                "wisconsin" -> LatLng(43.7844, -88.7879)
                "wyoming" -> LatLng(43.0760, -107.2903)
                else -> null
            }

            stateCenter?.let { center ->
                Log.d("MapScreen", "Animating camera to $state center: $center")
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(center, 6f), // 6f = good state-level zoom
                    durationMs = 1000
                )
            }
        }
    }

    // Center on device location when it updates (fallback)
    LaunchedEffect(currentLocation) {
        currentLocation?.let { loc ->
            Log.d("MapScreen", "Centering on device location: $loc")
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(loc, 13f),
                durationMs = 1000
            )
        }
    }

    val mapProperties by remember(hasLocationPermission) {
        mutableStateOf(
            MapProperties(
                isMyLocationEnabled = hasLocationPermission,
                mapType = MapType.HYBRID
            )
        )
    }

    val mapUiSettings by remember(hasLocationPermission) {
        mutableStateOf(
            MapUiSettings(
                myLocationButtonEnabled = hasLocationPermission,
                zoomControlsEnabled = false,
                compassEnabled = true,
                mapToolbarEnabled = false
            )
        )
    }

    // ───────────────────────────────────────────────
    // UI Layout
    // ───────────────────────────────────────────────
    Column(modifier = Modifier.fillMaxSize()) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                readOnly = true,
                value = selectedState ?: currentState,
                onValueChange = { },
                label = { Text("State") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                availableStates.forEach { state ->
                    DropdownMenuItem(
                        text = { Text(state) },
                        onClick = {
                            selectedState = state
                            expanded = false
                            Log.d("MapScreen", "Dropdown selected state: $state")
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Use Current Location") },
                    onClick = {
                        selectedState = null
                        expanded = false
                        Log.d("MapScreen", "Switched to current location")
                        scope.launch { loadCurrentLocation() } // force refresh device location
                    }
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = mapProperties,
                uiSettings = mapUiSettings,
                onMapClick = { latLng ->
                    selectedPinViewModel.setSelectedPin(latLng)
                }
            ) {
                markers.forEach { marker ->
                    Marker(
                        state = MarkerState(position = LatLng(marker.latitude, marker.longitude)),
                        title = marker.accessName.ifBlank { "Unnamed ramp" },
                        snippet = buildString {
                            append(marker.riverName.ifBlank { "No river" })
                            if (marker.otherName.isNotBlank()) append(" • ${marker.otherName}")
                            append("\n${marker.type} • ${marker.county}, ${marker.state}")
                        }
                    )
                }
            }

            if (isLoadingLocation || isLoadingMarkers) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            if (hasLocationPermission) {
                FloatingActionButton(
                    onClick = {
                        scope.launch {
                            try {
                                val loc = fusedLocationClient.lastLocation.await()
                                loc?.let {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLngZoom(
                                            LatLng(it.latitude, it.longitude), 15f
                                        )
                                    )
                                }
                            } catch (e: Exception) {
                                Log.e("MapScreen", "Re-center failed", e)
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "My location")
                }
            }

            FloatingActionButton(
                onClick = { scope.launch { loadCurrentLocation() } },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }

            // Zoom controls
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        scope.launch {
                            cameraPositionState.animate(CameraUpdateFactory.zoomIn(), 300)
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.ZoomIn, "Zoom In")
                }

                FloatingActionButton(
                    onClick = {
                        scope.launch {
                            cameraPositionState.animate(CameraUpdateFactory.zoomOut(), 300)
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.ZoomOut, "Zoom Out")
                }
            }
        }

        Button(
            onClick =
                {
                val loc = currentLocation ?: selectedPin

                if (loc != null)
                {
                    navController.navigate("suggested_trips/${loc.latitude}/${loc.longitude}")
                }
                else
                {
                    Toast.makeText(context, "No location available", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        )
        {
            Text("Suggest Trips Near Location")
        }
    }
}