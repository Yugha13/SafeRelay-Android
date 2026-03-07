package com.saferelay.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.spatialk.geojson.Position
import kotlin.math.roundToInt

@Composable
fun DisasterMapTab(
    messages: List<SafeRelayMessage>,
    peerNicknames: Map<String, String> = emptyMap(),
    onOpenChat: (String, String) -> Unit = { _, _ -> }
) {
    val sosMessages = remember(messages) {
        messages.filter {
            (it.emergencyType != EmergencyMessageType.NORMAL && it.emergencyType != EmergencyMessageType.SAFE_STATUS) || 
            it.priorityLevel == PriorityLevel.CRITICAL
        }
    }
    var selectedMessage by remember { mutableStateOf<SafeRelayMessage?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF050505))) {
        SOSMarkerMap(
            sosMessages = sosMessages,
            modifier = Modifier.fillMaxSize(),
            onMarkerClick = { selectedMessage = it }
        )

        // SOS count badge
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
    peerNicknames: Map<String, String> = emptyMap(),
    onOpenChat: (String, String) -> Unit = { _, _ -> },
    onDismiss: () -> Unit
) {
    val sosMessages = remember(messages) {
        messages.filter { it.emergencyType == EmergencyMessageType.SOS || it.priorityLevel == PriorityLevel.CRITICAL }
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
            baseStyle = BaseStyle.Uri(styleUri)
        )

        // Overlay markers using projection
        sosMessages.forEach { msg ->
            msg.geoLocation?.let { geo ->
                val pos = Position(geo.longitude, geo.latitude)
                val offset = cameraState.projection?.screenLocationFromPosition(pos)
                
                if (offset != null) {
                    Text(
                        text = msg.emergencyType.emoji,
                        fontSize = 28.sp,
                        modifier = Modifier
                            .offset(
                                x = offset.x - 14.dp, // Center approx (28sp ~ 14dp radius)
                                y = offset.y - 28.dp  // Bottom anchor approx
                            )
                            .clickable { onMarkerClick(msg) }
                    )
                }
            }
        }

        // Current User Location Marker
        myLocation?.let { loc ->
            val pos = Position(loc.longitude, loc.latitude)
            val offset = cameraState.projection?.screenLocationFromPosition(pos)
            
            if (offset != null) {
                Box(
                    modifier = Modifier
                        .offset(
                            x = offset.x - 12.dp,
                            y = offset.y - 12.dp
                        )
                        .size(24.dp)
                        .background(Color.White, CircleShape)
                        .padding(2.dp)
                        .background(com.saferelay.android.ui.MeshBlue, CircleShape)
                        .border(1.dp, Color.White, CircleShape)
                ) {
                    Text(
                        "YOU",
                        color = Color.White,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
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
