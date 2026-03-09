package com.saferelay.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
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
    onOpenChat: (String, String) -> Unit = { _, _ -> }
) {
    val sosMessages = remember(messages, myNickname) {
        messages.filter {
            it.sender != myNickname &&
            ((it.emergencyType != EmergencyMessageType.NORMAL && it.emergencyType != EmergencyMessageType.SAFE_STATUS) || 
            it.priorityLevel == PriorityLevel.CRITICAL)
        }
    }
    var selectedMessage by remember { mutableStateOf<SafeRelayMessage?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF050505))) {
        SOSMarkerMap(
            sosMessages = sosMessages,
            modifier = Modifier.fillMaxSize(),
            onMarkerClick = { selectedMessage = it }
        )

        /* // Removed local SOS badge for unified header
        if (sosMessages.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .background(SOSRed.copy(alpha = 0.92f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text("🚨 ${sosMessages.size} SOS", fontSize = 12.sp,
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color.White)
            }
        }
        */
        
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

            SOSMarkerMap(
                sosMessages = sosMessages,
                modifier = Modifier.fillMaxSize(),
                onMarkerClick = { selectedMessage = it }
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
    onMarkerClick: (SafeRelayMessage) -> Unit
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
    val cameraState = rememberCameraState(
        firstPosition = soupCameraPosition(sosMessages, myLocation)
    )

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
            options = MapOptions(ornamentOptions = OrnamentOptions.AllDisabled)
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
                onClick = {
                    coroutineScope.launch {
                        cameraState.animateTo(
                            finalPosition = CameraPosition(target = it.position, zoom = 14.0)
                        )
                    }
                }
            )

            // 2. Native SOS Markers
            if (sosMessages.isNotEmpty()) {
                val features = sosMessages.mapNotNull { msg ->
                    msg.geoLocation?.let { geo ->
                        Feature(
                            geometry = Point(longitude = geo.longitude, latitude = geo.latitude),
                            properties = buildJsonObject {
                                put("emoji", JsonPrimitive(msg.emergencyType.emoji))
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
                    textSize = const(2f.em),
                    onClick = { clickedFeatures ->
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
                )
            }
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
    val firstMsg = messages.firstOrNull { it.geoLocation != null }
    val target = if (myLocation != null) {
        Position(myLocation.longitude, myLocation.latitude)
    } else {
        firstMsg?.geoLocation?.let { Position(it.longitude, it.latitude) } ?: Position(78.9629, 20.5937)
    }
    
    return CameraPosition(
        target = target,
        zoom = if (myLocation != null || firstMsg != null) 12.0 else 4.0
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
