package com.example.paddlequest.screens

import android.Manifest
import android.content.pm.PackageManager
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

// === Helper function ===
fun getStateCenter(state: String): LatLng? {
    return when (state.lowercase().trim()) {
        "alabama" -> LatLng(32.7794, -86.8287)
        "alaska" -> LatLng(63.5888, -154.4931)
        "arizona" -> LatLng(34.0489, -111.0937)
        "arkansas" -> LatLng(34.9697, -92.3731)
        "california" -> LatLng(37.2726, -119.2702)
        "colorado" -> LatLng(39.1130, -105.3580)
        "connecticut" -> LatLng(41.6032, -73.0877)
        "delaware" -> LatLng(39.1582, -75.5244)
        "florida" -> LatLng(28.6305, -82.4497)
        "georgia" -> LatLng(32.9866, -83.6487)
        "hawaii" -> LatLng(19.8968, -155.5828)
        "idaho" -> LatLng(44.3509, -114.6130)
        "illinois" -> LatLng(40.0797, -89.4337)
        "indiana" -> LatLng(39.8282, -86.1581)
        "iowa" -> LatLng(42.0324, -93.5815)
        "kansas" -> LatLng(38.5266, -96.7265)
        "kentucky" -> LatLng(37.5347, -85.3021)
        "louisiana" -> LatLng(31.1695, -91.8678)
        "maine" -> LatLng(45.3695, -69.2428)
        "maryland" -> LatLng(39.0458, -76.6413)
        "massachusetts" -> LatLng(42.4072, -71.3824)
        "michigan" -> LatLng(44.1822, -84.5060)
        "minnesota" -> LatLng(46.2807, -94.3053)
        "mississippi" -> LatLng(32.7416, -89.6787)
        "missouri" -> LatLng(38.5739, -92.6030)
        "montana" -> LatLng(47.0527, -109.6333)
        "nebraska" -> LatLng(41.4925, -99.9018)
        "nevada" -> LatLng(39.8760, -117.2240)
        "new hampshire" -> LatLng(43.6805, -71.5724)
        "new jersey" -> LatLng(40.1900, -74.6728)
        "new mexico" -> LatLng(34.5199, -105.8701)
        "new york" -> LatLng(42.9134, -75.5963)
        "north carolina" -> LatLng(35.7596, -79.0193)
        "north dakota" -> LatLng(47.6506, -100.4370)
        "ohio" -> LatLng(40.4173, -82.9071)
        "oklahoma" -> LatLng(35.4676, -97.5164)
        "oregon" -> LatLng(43.8041, -120.5542)
        "pennsylvania" -> LatLng(40.8960, -77.7097)
        "rhode island" -> LatLng(41.5801, -71.4774)
        "south carolina" -> LatLng(33.8361, -81.1637)
        "south dakota" -> LatLng(44.2998, -99.4388)
        "tennessee" -> LatLng(35.8606, -86.3500)
        "texas" -> LatLng(31.5469, -99.9018)
        "utah" -> LatLng(39.4192, -111.9507)
        "vermont" -> LatLng(44.0459, -72.7107)
        "virginia" -> LatLng(37.7693, -78.1700)
        "washington" -> LatLng(47.4009, -121.4905)
        "west virginia" -> LatLng(38.5976, -80.4549)
        "wisconsin" -> LatLng(44.6243, -89.9941)
        "wyoming" -> LatLng(42.7559, -107.3025)
        else -> null
    }
}

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
    var isLoadingMarkers by remember { mutableStateOf(false) }

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
        position = CameraPosition.fromLatLngZoom(LatLng(35.2271, -80.8431), 10f)
    }

    val selectedPin by selectedPinViewModel.selectedPin.observeAsState()

    // Load current device location
    suspend fun loadCurrentLocation() {
        if (!hasLocationPermission) {
            currentState = "Permission required"
            isLoadingLocation = false
            return
        }

        isLoadingLocation = true
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
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
            } else {
                currentState = "Permission issue"
            }
        } catch (e: SecurityException) {
            currentState = "Permission denied"
            Log.e("MapScreen", "SecurityException", e)
        } catch (e: Exception) {
            currentState = "Location error"
            Log.e("MapScreen", "Location fetch failed", e)
        } finally {
            isLoadingLocation = false
        }
    }

    // Permission launcher
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

    // Initial permission check
    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        hasLocationPermission = hasFine || hasCoarse

        if (hasLocationPermission) {
            loadCurrentLocation()
        } else {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    // Load markers when state changes
    LaunchedEffect(selectedState, currentState) {
        val stateToLoad = selectedState ?: currentState
        Log.d("MapScreen", "Reload triggered - selectedState=$selectedState, currentState=$currentState, loading=$stateToLoad")

        if (stateToLoad.isBlank() || stateToLoad.contains("Detecting", ignoreCase = true) ||
            stateToLoad.contains("No location", ignoreCase = true) ||
            stateToLoad.contains("error", ignoreCase = true) ||
            stateToLoad.contains("Permission", ignoreCase = true) ||
            stateToLoad.contains("Unknown", ignoreCase = true)
        ) {
            markers = emptyList()
            isLoadingMarkers = false
            return@LaunchedEffect
        }

        isLoadingMarkers = true
        try {
            markers = loadMarkersForState(context, stateToLoad)
            Log.d("MapScreen", "Loaded ${markers.size} markers for $stateToLoad")
        } catch (e: Exception) {
            Log.e("MapScreen", "Failed to load markers", e)
            markers = emptyList()
        } finally {
            isLoadingMarkers = false
        }
    }

    // Camera movement when dropdown state changes
    LaunchedEffect(selectedState) {
        selectedState?.let { state ->
            selectedPinViewModel.setSelectedState(state)

            val center = getStateCenter(state)
            center?.let {
                Log.d("MapScreen", "Moving camera to $state center: $it")
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(it, 6.5f),
                    durationMs = 1200
                )
            }
        }
    }

    // Center on device location
    LaunchedEffect(currentLocation) {
        currentLocation?.let {
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 13f), 1000)
        }
    }

    val mapProperties = MapProperties(
        isMyLocationEnabled = hasLocationPermission,
        mapType = MapType.HYBRID
    )

    val mapUiSettings = MapUiSettings(
        myLocationButtonEnabled = false,
        zoomControlsEnabled = false,
        compassEnabled = true
    )

    // UI Layout
    Column(modifier = Modifier.fillMaxSize()) {
        // State Dropdown
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                readOnly = true,
                value = selectedState ?: currentState,
                onValueChange = {},
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
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Use Current Location") },
                    onClick = {
                        selectedState = null
                        expanded = false
                        scope.launch { loadCurrentLocation() }
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
                onMapClick = { selectedPinViewModel.setSelectedPin(it) }
            ) {
                markers.forEach { marker ->
                    Marker(
                        state = MarkerState(LatLng(marker.latitude, marker.longitude)),
                        title = marker.accessName,
                        snippet = "${marker.riverName} • ${marker.type}"
                    )
                }
            }

            if (isLoadingLocation || isLoadingMarkers) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            // Zoom Controls
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FloatingActionButton(
                    onClick = { scope.launch { cameraPositionState.animate(CameraUpdateFactory.zoomIn()) } },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.ZoomIn, "Zoom In")
                }
                FloatingActionButton(
                    onClick = { scope.launch { cameraPositionState.animate(CameraUpdateFactory.zoomOut()) } },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.ZoomOut, "Zoom Out")
                }
            }

            // My Location Button
            if (hasLocationPermission) {
                FloatingActionButton(
                    onClick = {
                        scope.launch {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                try {
                                    val loc = fusedLocationClient.lastLocation.await()
                                    loc?.let {
                                        cameraPositionState.animate(
                                            CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 15f)
                                        )
                                    }
                                } catch (e: Exception) {
                                    Log.e("MapScreen", "Re-center failed", e)
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.MyLocation, "My location")
                }
            }

            // Refresh Button
            FloatingActionButton(
                onClick = { scope.launch { loadCurrentLocation() } },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(Icons.Default.Refresh, "Refresh")
            }
        }

        Button(
            onClick = {
                val stateToUse = selectedState

                val loc = when {
                    selectedPin != null -> selectedPin                           // 1. Clicked marker (best)
                    stateToUse != null -> getStateCenter(stateToUse)             // 2. Selected state from dropdown
                    currentLocation != null -> currentLocation                   // 3. Device location (last resort)
                    else -> null
                }

                if (loc != null) {
                    navController.navigate("suggestedTripsScreen/${loc.latitude}/${loc.longitude}")
                } else {
                    Toast.makeText(
                        context,
                        "Please click a marker on the map or select a state first",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("Suggest Trips Near Location")
        }
    }
}