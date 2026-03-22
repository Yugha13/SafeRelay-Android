package com.saferelay.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saferelay.android.model.SafeRelayMessage
import com.saferelay.android.model.EmergencyMessageType
import com.saferelay.android.model.PriorityLevel
import android.location.Location

// MapLibre Imports
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.style.BaseStyle
import org.maplibre.compose.camera.rememberCameraState
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.lazy.items
import kotlinx.coroutines.launch
import org.maplibre.compose.map.MapOptions
import org.maplibre.compose.map.OrnamentOptions
import androidx.compose.foundation.layout.BoxScope
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.spatialk.geojson.Position
import kotlin.math.roundToInt

import org.maplibre.compose.location.Location as MapLibreLocation
import org.maplibre.compose.location.LocationProvider
import org.maplibre.compose.location.UserLocationState
import org.maplibre.compose.location.LocationPuck
import org.maplibre.compose.location.LocationPuckColors
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.spatialk.geojson.FeatureCollection
import org.maplibre.spatialk.geojson.Feature
import org.maplibre.spatialk.geojson.Point
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.compose.expressions.dsl.feature
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.expressions.dsl.format
import org.maplibre.compose.expressions.dsl.span
import org.maplibre.compose.expressions.dsl.asString
import org.maplibre.compose.layers.SymbolLayer
import kotlinx.coroutines.flow.map
import androidx.compose.ui.unit.em
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlin.time.TimeSource
import org.maplibre.compose.util.ClickResult

@Composable
fun DisasterMapTab(
    messages: List<SafeRelayMessage>,
    myNickname: String,
    peerNicknames: Map<String, String> = emptyMap(),
    onOpenChat: (String, String) -> Unit = { _, _ -> },
    isPickingLocation: Boolean = false,
    onLocationPicked: (com.saferelay.android.model.GeoLocation) -> Unit = {}
) {
    val context = LocalContext.current
    val locationManager = remember { com.saferelay.android.geohash.LocationChannelManager.getInstance(context) }
    val myLocation by locationManager.currentLocation.collectAsState()
    
    // Filtering logic: 20km radius + status
    var selectedFilter by remember { mutableStateOf("All") }
    val radiusKm = 20.0
    
    val filteredSosMessages = remember(messages, myNickname, myLocation, selectedFilter) {
        messages.filter { msg ->
            if (msg.emergencyType == EmergencyMessageType.SOS || msg.priorityLevel == PriorityLevel.CRITICAL) return@filter false
            
            // Base filter: Include Reports, and Admin Disasters
            val isIncident = (msg.emergencyType != EmergencyMessageType.NORMAL && msg.emergencyType != EmergencyMessageType.SAFE_STATUS) || 
                             msg.content.contains("🚨 [")
            
            if (!isIncident) return@filter false
            if (msg.sender == myNickname) return@filter false
            
            // Radius filter
            val loc = msg.geoLocation ?: return@filter true 
            val dist = if (myLocation != null) {
                calculateDistance(myLocation!!.latitude, myLocation!!.longitude, loc.latitude, loc.longitude)
            } else 0.0
            
            if (dist > radiusKm && myLocation != null) return@filter false
            
            // Status filter
            when (selectedFilter) {
                "Active" -> true // All current SOS/Reports are active until we have "Resolved" state logic
                "Resolved" -> false
                else -> true
            }
        }
    }
    
    var selectedMessage by remember { mutableStateOf<SafeRelayMessage?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF050505))) {
        val context = LocalContext.current
        val locationManager = remember { com.saferelay.android.geohash.LocationChannelManager.getInstance(context) }
        val myLocation by locationManager.currentLocation.collectAsState()
        val cameraState = rememberCameraState(
            firstPosition = soupCameraPosition(filteredSosMessages, myLocation)
        )
        val coroutineScope = rememberCoroutineScope()

        // Auto-zoom to SOS or current location once it's available
        LaunchedEffect(myLocation, filteredSosMessages.size) {
            val targetSos = filteredSosMessages.lastOrNull { it.geoLocation != null }
            if (targetSos?.geoLocation != null) {
                cameraState.animateTo(
                    finalPosition = CameraPosition(
                        target = Position(targetSos.geoLocation.longitude, targetSos.geoLocation.latitude),
                        zoom = 14.0
                    )
                )
            } else {
                val loc = myLocation
                if (loc != null) {
                    cameraState.animateTo(
                        finalPosition = CameraPosition(
                            target = Position(loc.longitude, loc.latitude),
                            zoom = 14.0
                        )
                    )
                }
            }
        }

        SOSMarkerMap(
            sosMessages = filteredSosMessages,
            modifier = Modifier.fillMaxSize(),
            onMarkerClick = { selectedMessage = it },
            isPickingLocation = isPickingLocation,
            onLocationPicked = onLocationPicked,
            cameraState = cameraState
        )

        // --- UI Overlays ---
        var searchQuery by remember { mutableStateOf("") }
        var searchResults by remember { mutableStateOf<List<android.location.Address>>(emptyList()) }
        val geocoder = remember { android.location.Geocoder(context) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .statusBarsPadding()
        ) {
            // "Incidents Near You" Floating Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f)),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = Color(0xFFFFE4E6), modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Notifications, null, tint = Color(0xFFFF3B30), modifier = Modifier.padding(8.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Incidents Near You", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("${filteredSosMessages.size} Active Incidents", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }

            // Search Bar
            MapSearchBar(
                query = searchQuery,
                onQueryChange = { query ->
                    searchQuery = query
                    if (query.length > 2) {
                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            try {
                                val addresses = geocoder.getFromLocationName(query, 5)
                                if (addresses != null) {
                                    searchResults = addresses
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    } else {
                        searchResults = emptyList()
                    }
                },
                onClear = {
                    searchQuery = ""
                    searchResults = emptyList()
                }
            )

            Spacer(Modifier.height(12.dp))

            // Filter Chips
            Row {
                listOf("All", "Active", "Resolved").forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) },
                        modifier = Modifier.padding(end = 8.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF6C63FF),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            if (searchResults.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(searchResults) { address: android.location.Address ->
                            val fullAddress = address.getAddressLine(0) ?: "Unknown location"
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        searchQuery = fullAddress
                                        searchResults = emptyList()
                                        coroutineScope.launch {
                                            cameraState.animateTo(
                                                finalPosition = CameraPosition(
                                                    target = Position(address.longitude, address.latitude),
                                                    zoom = 15.0
                                                )
                                            )
                                        }
                                    }
                                    .padding(16.dp)
                            ) {
                                Text(fullAddress, color = Color.Black, fontSize = 14.sp)
                                address.locality?.let {
                                    Text(it, color = Color.Gray, fontSize = 12.sp)
                                }
                            }
                            HorizontalDivider(color = Color(0xFFF3F4F6))
                        }
                    }
                }
            }
        }
        
        selectedMessage?.let { msg ->
            val senderId = msg.senderPeerID ?: msg.sender
            val senderNick = peerNicknames[senderId] ?: msg.sender
            
            MarkerDetailDialog(
                senderId = senderId,
                senderNick = senderNick,
                content = msg.content,
                emoji = msg.emergencyType.emoji,
                onDismiss = { selectedMessage = null },
                onOpenChat = onOpenChat
            )
        }
    }
}

@Composable
fun DisasterMapSheet(
    messages: List<SafeRelayMessage>,
    myNickname: String,
    peerNicknames: Map<String, String> = emptyMap(),
    onOpenChat: (String, String) -> Unit = { _, _ -> },
    onDismiss: () -> Unit
) {
    val sosMessages = remember(messages, myNickname) {
        messages.filter { it.sender != myNickname && (it.emergencyType == EmergencyMessageType.SOS || it.priorityLevel == PriorityLevel.CRITICAL) }
    }
    var selectedMessage by remember { mutableStateOf<SafeRelayMessage?>(null) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

            val cameraState = rememberCameraState(
                firstPosition = soupCameraPosition(sosMessages, null)
            )
            SOSMarkerMap(
                sosMessages = sosMessages,
                modifier = Modifier.fillMaxSize(),
                onMarkerClick = { selectedMessage = it },
                cameraState = cameraState
            )

            // Top overlay bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(12.dp)
                    .align(Alignment.TopStart),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.75f))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White,
                        modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Disaster Map", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace, color = Color.White)
                    Text("Live SOS from mesh network", fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace, color = Color.White.copy(alpha = 0.65f))
                }
            }

            selectedMessage?.let { msg ->
                val senderId = msg.senderPeerID ?: msg.sender
                val senderNick = peerNicknames[senderId] ?: msg.sender
                
                MarkerDetailDialog(
                    senderId = senderId,
                    senderNick = senderNick,
                    content = msg.content,
                    emoji = msg.emergencyType.emoji,
                    onDismiss = { selectedMessage = null },
                    onOpenChat = { pid, nick ->
                        onOpenChat(pid, nick)
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
fun SOSMarkerMap(
    sosMessages: List<SafeRelayMessage>,
    modifier: Modifier = Modifier,
    onMarkerClick: (SafeRelayMessage) -> Unit,
    isPickingLocation: Boolean = false,
    onLocationPicked: (com.saferelay.android.model.GeoLocation) -> Unit = {},
    cameraState: org.maplibre.compose.camera.CameraState
) {
    val context = LocalContext.current
    val locationManager = remember { com.saferelay.android.geohash.LocationChannelManager.getInstance(context) }
    val myLocation by locationManager.currentLocation.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
    // Bridge our location flow to MapLibre's LocationProvider
    val mapLibreLocationProvider = remember(locationManager) {
        object : LocationProvider {
            override val location = locationManager.currentLocation.map { loc ->
                loc?.let {
                    MapLibreLocation(
                        position = Position(it.longitude, it.latitude),
                        accuracy = it.accuracy.toDouble(),
                        bearing = it.bearing.toDouble(),
                        bearingAccuracy = null, // Android Location API doesn't guarantee bearing accuracy directly without SDK checks
                        speed = it.speed.toDouble(),
                        speedAccuracy = null,
                        timestamp = TimeSource.Monotonic.markNow()
                    )
                }
            }.stateIn(coroutineScope, SharingStarted.WhileSubscribed(5000), null)
        }
    }
    
    val userLocationState = org.maplibre.compose.location.rememberUserLocationState(locationProvider = mapLibreLocationProvider)
    val styleUri = "https://api.protomaps.com/styles/v5/light/en.json?key=73c45a97eddd43fb"


    // Keep location fresh while map is open
    DisposableEffect(Unit) {
        locationManager.beginLiveRefresh()
        onDispose {
            locationManager.endLiveRefresh()
        }
    }

    Box(modifier = modifier) {
        MaplibreMap(
            modifier = Modifier.fillMaxSize(),
            cameraState = cameraState,
            baseStyle = BaseStyle.Uri(styleUri),
            options = MapOptions(ornamentOptions = OrnamentOptions.AllDisabled),
            onMapClick = { geoPoint, _ ->
                if (isPickingLocation) {
                    onLocationPicked(com.saferelay.android.model.GeoLocation(geoPoint.latitude, geoPoint.longitude))
                }
                org.maplibre.compose.util.ClickResult.Consume
            }
        ) {
            // 1. Native My Location Puck
            LocationPuck(
                idPrefix = "user-location",
                locationState = userLocationState,
                cameraState = cameraState,
                colors = LocationPuckColors(
                    dotFillColorCurrentLocation = com.saferelay.android.ui.MeshBlue,
                    accuracyFillColor = com.saferelay.android.ui.MeshBlue.copy(alpha = 0.3f),
                    accuracyStrokeColor = com.saferelay.android.ui.MeshBlue
                ),
                onClick = { event ->
                    coroutineScope.launch {
                        cameraState.animateTo(
                            finalPosition = CameraPosition(target = event.position, zoom = 14.0)
                        )
                    }
                }
            )

            // 2. Native SOS / Report / Disaster Markers
            if (sosMessages.isNotEmpty()) {
                val features = sosMessages.mapNotNull { msg ->
                    msg.geoLocation?.let { geo ->
                        val emoji = if (msg.content.contains("Armed Robbery")) "🔪"
                                   else if (msg.content.contains("Fire Outbreak")) "🔥"
                                   else if (msg.content.contains("Break in")) "🚪"
                                   else if (msg.content.contains("Medical Emergency")) "🚑"
                                   else if (msg.content.contains("Suspicious Activity")) "👁️"
                                   else msg.emergencyType.emoji

                        val pinEmoji = "📍 $emoji"

                        Feature(
                            geometry = Point(longitude = geo.longitude, latitude = geo.latitude),
                            properties = buildJsonObject {
                                put("emoji", JsonPrimitive(pinEmoji))
                                put("id", JsonPrimitive(msg.id))
                            }
                        )
                    }
                }
                
                val source = rememberGeoJsonSource(
                    data = GeoJsonData.Features(FeatureCollection(features = features))
                )
                
                SymbolLayer(
                    id = "sos-markers",
                    source = source,
                    textField = format(span(feature["emoji"].asString())),
                    textSize = const(2.2f.em),
                    onClick = { clickedFeatures ->
                        if (isPickingLocation) {
                            ClickResult.Pass
                        } else {
                            val props = clickedFeatures.firstOrNull()?.properties?.let { 
                                if (it is kotlinx.serialization.json.JsonObject) it else null 
                            }

                            val clickedId = props?.get("id")?.toString()?.trim('"')

                            if (clickedId != null) {
                                val msg = sosMessages.find { it.id == clickedId }
                                if (msg != null) onMarkerClick(msg)
                            }
                            ClickResult.Consume
                        }
                    }
                )
            }
        }

        // Picking location overlay
        if (isPickingLocation) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    "Tap on map to select incident location",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Center crosshair
            Icon(
                Icons.Default.MyLocation,
                null,
                tint = SOSRed,
                modifier = Modifier.align(Alignment.Center).size(32.dp)
            )
        }
        
        // My Location Button
        FloatingActionButton(
            onClick = {
                myLocation?.let { loc ->
                    coroutineScope.launch {
                        val pos = Position(loc.longitude, loc.latitude)
                        cameraState.animateTo(
                            finalPosition = CameraPosition(target = pos, zoom = 14.0)
                        )
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .padding(bottom = 90.dp), // Clear bottom nav
            containerColor = Color(0xFF222222),
            contentColor = Color.White
        ) {
            Icon(
                imageVector = Icons.Filled.MyLocation,
                contentDescription = "My Location"
            )
        }
    }
}

private fun soupCameraPosition(messages: List<SafeRelayMessage>, myLocation: Location?): CameraPosition {
    val targetSos = messages.lastOrNull { it.geoLocation != null }
    val target = if (targetSos?.geoLocation != null) {
        Position(targetSos.geoLocation.longitude, targetSos.geoLocation.latitude)
    } else if (myLocation != null) {
        Position(myLocation.longitude, myLocation.latitude)
    } else {
        Position(0.0, 0.0)
    }
    
    return CameraPosition(
        target = target,
        zoom = 14.0
    )
}

@Composable
private fun MarkerDetailDialog(
    senderId: String,
    senderNick: String,
    content: String,
    emoji: String,
    onDismiss: () -> Unit,
    onOpenChat: (String, String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text(emoji, fontSize = 32.sp) },
        title = { Text(senderNick, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold) },
        text = { Text(content, fontSize = 14.sp) },
        confirmButton = {
            Button(
                onClick = {
                    onOpenChat(senderId, senderNick)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = SOSRed)
            ) {
                Text("SEND MESSAGE", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CLOSE")
            }
        },
        containerColor = Color(0xFF151515),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFCCCCCC)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search location...", color = Color.Gray) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Clear, null, tint = Color.Gray)
                }
            }
        },
        shape = RoundedCornerShape(24.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = Color.Black
        ),
        singleLine = true
    )
}
