package com.saferelay.android.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.content.pm.PackageManager
import android.location.Location
import android.os.BatteryManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.rememberScrollState
import com.saferelay.android.model.*
import com.saferelay.android.ui.SosManager.isSosAlert
import com.saferelay.android.ui.theme.SafeRelayTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
// ── Colour constants ───────────────────────────────────────────────────────
val SOSRed     = Color(0xFFFF3B30)
val UrgentOrange = Color(0xFFFF9500)
val SafeGreen    = Color(0xFF34C759)
val InfoGray     = Color(0xFF8E8E93)
val MeshBlue     = Color(0xFF6C63FF) // Updated to Brand Purple
val BrandPurple  = Color(0xFF6C63FF)
val DarkBg       = Color(0xFF0D0D0D)
val CardBg       = Color(0xFF1C1C1E)

data class EmergencyCategory(
    val title: String,
    val emoji: String,
    val color: Color,
    val description: String = ""
)

val EmergencyCategories = listOf(
    EmergencyCategory("Armed Robbery", "🔪", Color(0xFFFF3B30)),
    EmergencyCategory("Break in", "🚪", Color(0xFFFF9500)),
    EmergencyCategory("Fire Outbreak", "🔥", Color(0xFFFFCC00)),
    EmergencyCategory("Medical Emergency", "🚑", Color(0xFF34C759)),
    EmergencyCategory("Suspicious Activity", "👁️", Color(0xFF5856D6)),
    EmergencyCategory("Other Emergency", "🔔", Color(0xFF007AFF))
)

// ── Tabs ───────────────────────────────────────────────────────────────────
enum class SafeRelayTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME("Home", Icons.Default.Home),
    MESSAGES("Messages", Icons.Default.Message),
    MAP("Map", Icons.Default.Map),
    PROFILE("Profile", Icons.Default.Person),
}

// ─────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafeRelayMainScreen(
    viewModel: ChatViewModel,
    onOpenMeshChat: () -> Unit = {},
    onOpenPrivateChat: (String, String) -> Unit = { _, _ -> },
    onOpenProfile: () -> Unit = {}
) {
    val context = LocalContext.current
    val profileManager = remember { UserProfileManager.getInstance(context) }
    val profile by profileManager.profile.collectAsState()
    val messages by viewModel.messages.collectAsState(emptyList())
    val connectedPeers by viewModel.connectedPeers.collectAsState(emptyList())
    val peerNicknames by viewModel.peerNicknames.collectAsState(emptyMap())

    var selectedTab by remember { mutableStateOf(SafeRelayTab.HOME) }
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    var showDisasterMap by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var showIncomingSosAlert by remember { mutableStateOf<SafeRelayMessage?>(null) }
    // --- SOS Spam Prevention ---
    val notifiedSosIds = remember { mutableStateOf(setOf<String>()) }

    // --- Reporting Flow State ---
    var showReportCategorySheet by remember { mutableStateOf(false) }
    var showReportDetailsScreen by remember { mutableStateOf(false) }
    var selectedReportCategory by remember { mutableStateOf<EmergencyCategory?>(null) }
    var reportLocation by remember { mutableStateOf<GeoLocation?>(null) }
    var isPickingLocationFromMap by remember { mutableStateOf(false) }
    var locationAddress by remember { mutableStateOf("Fetching location...") }

    // Sync profile name → chat nickname
    LaunchedEffect(profile.fullName) {
        if (profile.fullName.isNotBlank()) {
            viewModel.setNickname(profile.fullName)
        }
    }

    // Watch for incoming SOS
    val geo = remember(messages) { getLastLocation(context) }
    
    LaunchedEffect(geo) {
        if (geo != null) {
            try {
                val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                val addresses = geocoder.getFromLocation(geo.latitude, geo.longitude, 1)
                if (addresses != null && addresses.isNotEmpty()) {
                    val addr = addresses[0]
                    val line1 = addr.getAddressLine(0) ?: "Unknown Street"
                    locationAddress = line1
                } else {
                    locationAddress = "at %.4f, %.4f".format(geo.latitude, geo.longitude)
                }
            } catch (e: Exception) {
                locationAddress = "at %.4f, %.4f".format(geo.latitude, geo.longitude)
            }
        } else {
            locationAddress = "Location access required"
        }
    }

    LaunchedEffect(messages) {
        val latestSos = messages.lastOrNull { it.isSosAlert() && it.sender != viewModel.myNickname }
        if (latestSos != null && !notifiedSosIds.value.contains(latestSos.id)) {
            notifiedSosIds.value = notifiedSosIds.value + latestSos.id
            showIncomingSosAlert = latestSos
            SosManager.triggerIncomingSosHaptic(context)
        }
    }

    // Map Unread Sos logic
    var lastSeenSosId by remember { mutableStateOf<String?>(null) }
    val latestMapSos = remember(messages) { messages.lastOrNull { it.isSosAlert() && it.sender != viewModel.myNickname } }
    val hasUnreadSos = remember(latestMapSos, lastSeenSosId, showDisasterMap) {
        if (showDisasterMap) false else latestMapSos != null && latestMapSos.id != lastSeenSosId
    }
    LaunchedEffect(showDisasterMap) {
        if (showDisasterMap && latestMapSos != null) {
            lastSeenSosId = latestMapSos.id
        }
    }

    // Use statusBarsPadding + imePadding at root level
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF8F9FA)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()   // ← fixes top spacing gap
        ) {
            // ── Header (Hidden on Home tab) ────────────────
            if (selectedTab != SafeRelayTab.HOME) {
                val headerTitle = when (selectedTab) {
                    SafeRelayTab.HOME -> "SafeRelay"
                    SafeRelayTab.MESSAGES -> "Messages"
                    SafeRelayTab.MAP -> "Map"
                    SafeRelayTab.PROFILE -> "Profile"
                }
                SafeRelayHeader(
                    title = headerTitle,
                    profile = profile,
                    connectedPeerCount = connectedPeers.size,
                    hasUnreadSos = hasUnreadSos,
                    onMapClick = { showDisasterMap = true },
                    onProfileClick = { selectedTab = SafeRelayTab.PROFILE }, // Profile restored
                    onBrandClick = { selectedTab = SafeRelayTab.HOME }
                )
            }

            // ── Body ───────────────────────────────────────────────────
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    SafeRelayTab.HOME -> StatusTab(
                        viewModel = viewModel,
                        profile = profile,
                        hasUnreadSos = hasUnreadSos,
                        onProfileClick = { selectedTab = SafeRelayTab.PROFILE },
                        onMapClick = { showDisasterMap = true },
                        onReportClick = { showReportCategorySheet = true } // Report via sheet now
                    )
                    SafeRelayTab.MESSAGES -> {
                        EmergencyFeedTab(messages = messages, viewModel = viewModel)
                    }
                    SafeRelayTab.MAP    -> DisasterMapTab(
                        messages = messages,
                        myNickname = viewModel.myNickname,
                        peerNicknames = peerNicknames,
                        onOpenChat = { pid: String, nick: String ->
                            onOpenPrivateChat(pid, nick)
                        },
                        isPickingLocation = isPickingLocationFromMap,
                        onLocationPicked = { geo ->
                            reportLocation = geo
                            isPickingLocationFromMap = false
                            selectedTab = SafeRelayTab.HOME
                            showReportCategorySheet = true
                        }
                    )
                    SafeRelayTab.PROFILE -> {
                        ProfileTab(
                            viewModel = viewModel,
                            profile = profile,
                            profileManager = profileManager,
                            onEditProfile = { showProfile = true },
                            onReportClick = { 
                                selectedTab = SafeRelayTab.HOME
                                showReportCategorySheet = true 
                            }
                        )
                    }
                }
            }

            // ── Bottom Navigation (hidden when keyboard up) ──
            if (!imeVisible) {
                HorizontalDivider(color = Color(0xFFE5E7EB), thickness = 1.dp)

                NavigationBar(
                    containerColor = Color.White,
                    tonalElevation = 0.dp, // Flat appearance
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    SafeRelayTab.values().forEach { tab ->
                        val selected = selectedTab == tab
                        NavigationBarItem(
                            selected = selected,
                            onClick = { selectedTab = tab },
                            icon = {
                                if (tab == SafeRelayTab.MAP && hasUnreadSos) {
                                    androidx.compose.material3.BadgedBox(
                                        badge = {
                                            Box(modifier = Modifier.size(8.dp).background(SOSRed, CircleShape))
                                        }
                                    ) {
                                        Icon(
                                            tab.icon,
                                            contentDescription = tab.label,
                                            tint = if (selected) BrandPurple else Color(0xFF9CA3AF),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                } else {
                                    Icon(
                                        tab.icon,
                                        contentDescription = tab.label,
                                        tint = if (selected) BrandPurple else Color(0xFF9CA3AF),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            },
                            label = {
                                Text(
                                    tab.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) BrandPurple else Color(0xFF9CA3AF)
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Color.Transparent, // Removes the pill
                                selectedIconColor = BrandPurple,
                                unselectedIconColor = Color(0xFF9CA3AF),
                                selectedTextColor = BrandPurple,
                                unselectedTextColor = Color(0xFF9CA3AF)
                            )
                        )
                    }
                }
            }
        }

        // Incoming SOS overlay (full-screen)
        showIncomingSosAlert?.let { sos ->
            IncomingSosOverlay(message = sos, onDismiss = { showIncomingSosAlert = null })
        }
    }

    if (showDisasterMap) {
        DisasterMapSheet(
            messages = messages,
            myNickname = viewModel.myNickname,
            peerNicknames = peerNicknames,
            onOpenChat = { pid: String, nick: String ->
                showDisasterMap = false
                onOpenPrivateChat(pid, nick)
            },
            onDismiss = { showDisasterMap = false }
        )
    }
    if (showProfile) {
        UserProfileSheet(profileManager = profileManager, onDismiss = { showProfile = false })
    }

    // --- Reporting Flow Overlays ---
    if (showReportCategorySheet) {
        ReportEmergencySheet(
            onCategorySelected = { cat ->
                selectedReportCategory = cat
                showReportCategorySheet = false
                showReportDetailsScreen = true
                reportLocation = getLastLocation(context)
            },
            onDismiss = { showReportCategorySheet = false }
        )
    }

    if (showReportDetailsScreen) {
        EmergencyDetailsScreen(
            category = selectedReportCategory ?: EmergencyCategories.first(),
            locationAddress = locationAddress,
            onBack = {
                showReportDetailsScreen = false
                showReportCategorySheet = true
            },
            onPickLocation = {
                showReportDetailsScreen = false
                isPickingLocationFromMap = true
                selectedTab = SafeRelayTab.MAP
            },
            onSend = { info ->
                val cat = selectedReportCategory ?: EmergencyCategories.first()
                val msg = SafeRelayMessage(
                    sender = viewModel.myNickname,
                    content = "🚨 [${cat.title}]: $info",
                    type = SafeRelayMessageType.Message,
                    timestamp = java.util.Date(),
                    emergencyType = EmergencyMessageType.NORMAL,
                    priorityLevel = PriorityLevel.URGENT,
                    geoLocation = reportLocation
                )
                viewModel.sendEmergencyMessage(msg)
                showReportDetailsScreen = false
                android.widget.Toast.makeText(context, "Emergency Alert Sent via Mesh!", android.widget.Toast.LENGTH_LONG).show()
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Header
// ─────────────────────────────────────────────────────────────────────────
@Composable
private fun SafeRelayHeader(
    title: String,
    profile: UserProfile,
    connectedPeerCount: Int,
    hasUnreadSos: Boolean,
    onMapClick: () -> Unit,
    onProfileClick: () -> Unit,
    onBrandClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFF8F9FA),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A2E),
                modifier = Modifier.clickable { onBrandClick() }
            )

            Spacer(Modifier.weight(1f))

            // Connection status indicator
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (connectedPeerCount > 0) Color(0xFFE8F5E9) else Color(0xFFF3F4F6)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (connectedPeerCount > 0) SafeGreen else InfoGray)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "$connectedPeerCount",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (connectedPeerCount > 0) Color(0xFF4CAF50) else Color(0xFF6B7280)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            IconButton(onClick = onMapClick) {
                if (hasUnreadSos) {
                    androidx.compose.material3.BadgedBox(
                        badge = { androidx.compose.foundation.layout.Box(modifier = Modifier.size(8.dp).background(SOSRed, CircleShape)) }
                    ) {
                        Icon(Icons.Filled.Map, contentDescription = "Map", tint = Color(0xFF6B7280))
                    }
                } else {
                    Icon(Icons.Filled.Map, contentDescription = "Map", tint = Color(0xFF6B7280))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// STATUS TAB – home screen with "I'm Safe" / SOS broadcast
// ─────────────────────────────────────────────────────────────────────────
@Composable
fun StatusTab(
    viewModel: ChatViewModel,
    profile: UserProfile,
    hasUnreadSos: Boolean,
    onProfileClick: () -> Unit = {},
    onMapClick: () -> Unit = {},
    onReportClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val userName = if (profile.fullName.isNotBlank()) profile.fullName.split(" ").first() else "User"
    val messages by viewModel.messages.collectAsState(emptyList())
    val myLocation = remember(messages) { getLastLocation(context) }
    
    val myActiveSos = remember(messages) {
        val lastEmergencyMsg = messages.findLast { 
            (it.emergencyType == EmergencyMessageType.SOS || it.emergencyType == EmergencyMessageType.SAFE_STATUS) && 
            it.sender == viewModel.myNickname 
        }
        if (lastEmergencyMsg?.emergencyType == EmergencyMessageType.SOS) lastEmergencyMsg else null
    }

    // 20km Radius filtering
    val nearbyIncidents = remember(messages, myLocation) {
        messages.filter { msg ->
            val isIncident = (msg.emergencyType != EmergencyMessageType.NORMAL && msg.emergencyType != EmergencyMessageType.SAFE_STATUS) || 
                             msg.content.contains("🚨")
            if (!isIncident) return@filter false
            
            val loc = msg.geoLocation ?: return@filter true
            if (myLocation == null) return@filter true
            
            val dist = calculateDistance(myLocation.latitude, myLocation.longitude, loc.latitude, loc.longitude)
            dist <= 20.0
        }.sortedByDescending { it.timestamp }
    }

    var selectedFilter by remember { mutableStateOf("All") }
    val filteredIncidents = remember(nearbyIncidents, selectedFilter) {
        when (selectedFilter) {
            "Active" -> nearbyIncidents.filter { it.emergencyType != EmergencyMessageType.SAFE_STATUS }
            "Resolved" -> emptyList()
            else -> nearbyIncidents
        }
    }

    val scope = rememberCoroutineScope()
    var isSendingSos by remember { mutableStateOf(false) }
    var showCancelSosDialog by remember { mutableStateOf(false) }
    var showRePushSosDialog by remember { mutableStateOf(false) }
    var sosHoldProgress by remember { mutableStateOf(0f) }
    var isHoldingSosButton by remember { mutableStateOf(false) }
    
    // Quick Action Dialog State
    var showEmergencyActionDialog by remember { mutableStateOf(false) }
    var showEmergencyMessageInputDialog by remember { mutableStateOf(false) }
    var selectedEmergencyType by remember { mutableStateOf("Ambulance") }
    var selectedEmergencyNumber by remember { mutableStateOf("911") }

    Scaffold(
        containerColor = Color.White
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- Custom Home Header ---
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SafeRelay",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Report Button
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFFEF2F2),
                        modifier = Modifier.size(40.dp).clickable { onReportClick() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Error, null, tint = SOSRed, modifier = Modifier.size(20.dp))
                        }
                    }
                    // Map Button
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFEFF6FF),
                        modifier = Modifier.size(40.dp).clickable { onMapClick() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (hasUnreadSos) {
                                androidx.compose.material3.BadgedBox(
                                    badge = { androidx.compose.foundation.layout.Box(modifier = Modifier.size(8.dp).background(SOSRed, CircleShape)) }
                                ) {
                                    Icon(Icons.Default.Map, null, tint = MeshBlue, modifier = Modifier.size(20.dp))
                                }
                            } else {
                                Icon(Icons.Default.Map, null, tint = MeshBlue, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                }
            }

            // --- Greeting ---
            Text(
                text = "Hi, $userName!",
                fontSize = 42.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = FontFamily.Monospace,
                color = Color.Black,
                modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp)
            )

            // --- Quick Actions ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                QuickActionItem("Ambulance", "🚑", Color(0xFFFEF2F2), onClick = {
                    selectedEmergencyType = "Ambulance"
                    selectedEmergencyNumber = "911"
                    showEmergencyActionDialog = true
                })
                QuickActionItem("Fire", "🔥", Color(0xFFFEF2F2), onClick = {
                    selectedEmergencyType = "Fire"
                    selectedEmergencyNumber = "911"
                    showEmergencyActionDialog = true
                })
                QuickActionItem("Police", "👮", Color(0xFFEFF6FF), onClick = {
                    selectedEmergencyType = "Police"
                    selectedEmergencyNumber = "911"
                    showEmergencyActionDialog = true
                })
            }

            Spacer(Modifier.height(64.dp))

            // --- Central SOS Button with ripples ---
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                // Outer Ripple
                Box(
                    modifier = Modifier.size(280.dp).background(SOSRed.copy(alpha = 0.03f), CircleShape)
                )
                // Middle Ripple
                Box(
                    modifier = Modifier.size(240.dp).background(SOSRed.copy(alpha = 0.05f), CircleShape)
                )
                
                Surface(
                    shape = CircleShape,
                    color = SOSRed.copy(alpha = 0.8f),
                    modifier = Modifier
                        .size(180.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    if (myActiveSos != null) {
                                        showRePushSosDialog = true
                                        return@detectTapGestures
                                    }
                                    if (isSendingSos) return@detectTapGestures
                                    
                                    if (viewModel.isSosCooldownActive()) {
                                        android.widget.Toast.makeText(context, "SOS Cooldown: ${viewModel.getRemainingSosCooldown()}s remaining", android.widget.Toast.LENGTH_SHORT).show()
                                        return@detectTapGestures
                                    }

                                    isHoldingSosButton = true
                                    val startTime = System.currentTimeMillis()
                                    val holdJob = scope.launch {
                                        while (isHoldingSosButton) {
                                            val elapsed = System.currentTimeMillis() - startTime
                                            sosHoldProgress = (elapsed / 3000f).coerceIn(0f, 1f)
                                            if (sosHoldProgress >= 1f) {
                                                isSendingSos = true
                                                val geo = getLastLocationAsync(context)
                                                val bat = getBatteryPercentAsync(context)
                                                val msg = SosManager.buildSosMessage(viewModel.myNickname, geo, bat)
                                                viewModel.sendEmergencyMessage(msg)
                                                SosManager.triggerSosHaptic(context)
                                                isHoldingSosButton = false
                                                sosHoldProgress = 0f
                                                delay(2000)
                                                isSendingSos = false
                                                break
                                            }
                                            delay(16)
                                        }
                                    }
                                    try {
                                        awaitRelease()
                                    } finally {
                                        isHoldingSosButton = false
                                        sosHoldProgress = 0f
                                        holdJob.cancel()
                                    }
                                }
                            )
                        },
                    shadowElevation = 0.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // Progress Arc
                        if (sosHoldProgress > 0f) {
                            Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                                drawArc(
                                    brush = Brush.sweepGradient(listOf(Color.White.copy(0.5f), Color.White)),
                                    startAngle = -90f,
                                    sweepAngle = 360f * sosHoldProgress,
                                    useCenter = false,
                                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                        }
                        
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Wifi, null, tint = Color.White, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "SOS",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            
            Text(
                text = "Hold for 3s to send SOS",
                color = Color.Gray,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(32.dp))

            // --- Bottom I'M SAFE NOW Pill Button ---
            Button(
                onClick = {
                    showCancelSosDialog = true
                },
                enabled = myActiveSos != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
                shape = RoundedCornerShape(32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black,
                    disabledContainerColor = Color(0xFFE5E7EB)
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = if (myActiveSos != null) Color(0xFF34C759) else Color.LightGray,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp).padding(4.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "I'M SAFE NOW",
                        color = if (myActiveSos != null) Color.White else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
            
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showRePushSosDialog) {
        AlertDialog(
            onDismissRequest = { showRePushSosDialog = false },
            title = { Text("Already Pushed") },
            text = { Text("SOS is already pushed and waiting for response. Do you want to push again?") },
            confirmButton = {
                val isCooldownActive = viewModel.isSosCooldownActive()
                val cooldownSeconds = viewModel.getRemainingSosCooldown()
                
                TextButton(
                    enabled = !isCooldownActive,
                    onClick = {
                        showRePushSosDialog = false
                        isSendingSos = true
                        scope.launch {
                            val geo = getLastLocationAsync(context)
                            val bat = getBatteryPercentAsync(context)
                            val msg = SosManager.buildSosMessage(viewModel.myNickname, geo, bat)
                            viewModel.sendEmergencyMessage(msg)
                            SosManager.triggerSosHaptic(context)
                            delay(2000)
                            isSendingSos = false
                        }
                    }
                ) {
                    Text(
                        if (isCooldownActive) "Wait ${cooldownSeconds}s" else "Push Again",
                        color = if (isCooldownActive) Color.Gray else SOSRed
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showRePushSosDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCancelSosDialog) {
        CancelSosDialog(
            onDismiss = { showCancelSosDialog = false },
            onConfirm = {
                showCancelSosDialog = false
                scope.launch {
                    val name = if (profile.fullName.isBlank()) viewModel.myNickname else profile.fullName
                    val geo = getLastLocationAsync(context)
                    val msg = SafeRelayMessage(
                        sender = viewModel.myNickname,
                        content = "✅ SAFE: @${viewModel.myNickname} ($name) is SAFE.",
                    type = SafeRelayMessageType.Message,
                    timestamp = java.util.Date(),
                    emergencyType = EmergencyMessageType.SAFE_STATUS,
                    priorityLevel = PriorityLevel.URGENT,
                    geoLocation = geo
                )
                viewModel.sendEmergencyMessage(msg)
                }
            }
        )
    }

    if (showEmergencyActionDialog) {
        EmergencyActionDialog(
            contactType = selectedEmergencyType,
            contactNumber = selectedEmergencyNumber,
            onCall = { number ->
                showEmergencyActionDialog = false
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                context.startActivity(intent)
            },
            onMessage = { type ->
                showEmergencyActionDialog = false
                showEmergencyMessageInputDialog = true
            },
            onDismiss = { showEmergencyActionDialog = false }
        )
    }

    if (showEmergencyMessageInputDialog) {
        EmergencyMessageInputDialog(
            contactType = selectedEmergencyType,
            onSend = { text ->
                showEmergencyMessageInputDialog = false
                scope.launch {
                    val geo = getLastLocationAsync(context)
                    val bat = getBatteryPercentAsync(context)
                    val msg = SafeRelayMessage(
                        sender = viewModel.myNickname,
                        content = "🚨 EMERGENCY REQUEST ($selectedEmergencyType):\n$text",
                    type = SafeRelayMessageType.Message,
                    timestamp = java.util.Date(),
                    emergencyType = EmergencyMessageType.SOS,
                    priorityLevel = PriorityLevel.CRITICAL,
                    geoLocation = geo
                )
                viewModel.sendEmergencyMessage(msg)
                SosManager.triggerSosHaptic(context)
                }
            },
            onDismiss = { showEmergencyMessageInputDialog = false }
        )
    }
}

@Composable
fun ReportEmergencyTab(
    onReportSent: () -> Unit,
    onPickLocation: () -> Unit,
    locationAddress: String,
    geoLocation: com.saferelay.android.model.GeoLocation?
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(EmergencyCategories.first()) }
    
    Column(modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
        Text("Report Emergency", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Help keep your community safe", fontSize = 14.sp, color = Color.Gray)
        
        Spacer(Modifier.height(32.dp))
        
        Text("Emergency Type", fontWeight = FontWeight.Bold)
        LazyRow(modifier = Modifier.padding(vertical = 12.dp)) {
            items(EmergencyCategories) { cat ->
                FilterChip(
                    selected = category == cat,
                    onClick = { category = cat },
                    label = { Text("${cat.emoji} ${cat.title}") },
                    modifier = Modifier.padding(end = 8.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = cat.color,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }
        
        Spacer(Modifier.height(16.dp))
        Text("Emergency Title", fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            placeholder = { Text("e.g Building fire on Main Street") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            shape = RoundedCornerShape(12.dp)
        )
        
        Spacer(Modifier.height(24.dp))
        Text("Location", fontWeight = FontWeight.Bold)
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clickable { onPickLocation() },
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F6)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, null, tint = BrandPurple)
                Spacer(Modifier.width(12.dp))
                Text(locationAddress, modifier = Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
            }
        }
        
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onReportSent, // Simplified for now
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
        ) {
            Text("Send Emergency Alert", color = Color.White, fontWeight = FontWeight.Bold)
        }
        
        Spacer(Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F3FF)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                "Note: Your report will be reviewed... Call local emergency services first.",
                modifier = Modifier.padding(16.dp),
                fontSize = 12.sp,
                color = BrandPurple
            )
        }
    }
}

@Composable
fun IncidentCard(msg: SafeRelayMessage) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = SOSRed.copy(alpha = 0.1f), modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text(if (msg.content.contains("Fire")) "🔥" else "🚨", fontSize = 20.sp) }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(msg.content, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Avoid the area. Stay safe.", fontSize = 13.sp, color = Color.Gray)
                }
                StatusBadge(isActive = true)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(msg.sender, fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.width(16.dp))
                Icon(Icons.Default.AccessTime, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Just now", fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun StatusBadge(isActive: Boolean) {
    Surface(
        color = if (isActive) Color(0xFFFFE4E6) else Color(0xFFF0FDF4),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(6.dp).background(if (isActive) SOSRed else SafeGreen, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(if (isActive) "Active" else "Resolved", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isActive) SOSRed else SafeGreen)
        }
    }
}

fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return r * c
}

@Composable
fun QuickActionItem(label: String, emoji: String, bgColor: Color, onClick: () -> Unit = {}) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            onClick = onClick,
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        )
    ) {
        Surface(
            shape = CircleShape,
            color = bgColor,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(emoji, fontSize = 28.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.Black,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun AlertSentDialog(onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFFEF2F2),
                    modifier = Modifier.size(100.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.SettingsInputAntenna, 
                            null, 
                            tint = SOSRed, 
                            modifier = Modifier.size(50.dp)
                        )
                    }
                }
                
                Spacer(Modifier.height(24.dp))
                
                Text(
                    text = "Alert Sent",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )
                
                Spacer(Modifier.height(8.dp))
                
                Text(
                    text = "The Neighbourhood is Being notified, stay strong",
                    fontSize = 15.sp,
                    color = Color(0xFF6B7280),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 22.sp
                )
                
                Spacer(Modifier.height(32.dp))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827))
                ) {
                    Text("Got it", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun MyActiveSosCard(msg: SafeRelayMessage, onCancelClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SOSRed.copy(alpha = 0.05f)),
        border = BorderStroke(1.dp, SOSRed.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = SOSRed, modifier = Modifier.size(40.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Warning, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("My Active SOS", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = SOSRed)
                    Text("Broadcasted to ${msg.priorityLevel.label} peers", fontSize = 12.sp, color = Color(0xFF4B5563))
                }
                Text(
                    "View All",
                    color = MeshBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { /* View All */ }
                )
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onCancelClick,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SOSRed)
            ) {
                Text("Cancel SOS", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun CancelSosDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp).offset(x = 8.dp, y = (-8).dp)) {
                        Icon(Icons.Filled.Close, null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(20.dp))
                    }
                }
                
                Text(
                    "Are you sure you want to cancel?",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    color = Color(0xFF111827)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "If you cancel, you will stop receiving updates about this emergency.",
                    fontSize = 14.sp,
                    color = Color(0xFF6B7280),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                    ) {
                        Text("Keep SOS", color = Color(0xFF4B5563), fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SOSRed)
                    ) {
                        Text("Cancel SOS", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun EmergencyCard(msg: SafeRelayMessage, onForget: () -> Unit = {}) {
    val (type, icon, color) = when {
        msg.content.contains("Armed robbery", ignoreCase = true) -> Triple("Armed robbery", Icons.Default.Dining, SOSRed) // Close enough to knife
        msg.content.contains("Break in", ignoreCase = true) -> Triple("Break in", Icons.Default.DoorSliding, UrgentOrange)
        msg.content.contains("Medical Emergency", ignoreCase = true) -> Triple("Medical Emergency", Icons.Default.MedicalServices, Color(0xFF8B5CF6)) // Purple
        else -> Triple("Security Alert", Icons.Default.Warning, SOSRed)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, Color(0xFFF3F4F6))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = color.copy(alpha = 0.1f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
                    }
                }
                
                Spacer(Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = type,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = Color(0xFF111827)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccessTime, null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(text = "2m ago", fontSize = 12.sp, color = Color(0xFF9CA3AF), fontWeight = FontWeight.Medium)
                        }
                    }
                    
                    Spacer(Modifier.height(6.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, null, tint = Color(0xFF6B7280), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Parliament Street - Block 5",
                            fontSize = 13.sp,
                            color = Color(0xFF4B5563),
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(20.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onForget,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                ) {
                    Text("Forget", color = Color(0xFF4B5563), fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = { /* Respond */ },
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111827))
                ) {
                    Text("Respond", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun InfoCard(modifier: Modifier, emoji: String, number: String, label: String, color: Color) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F0F)),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(emoji, fontSize = 22.sp)
            Text(number, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace, color = color)
            Text(label, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                color = Color(0xFF666666))
        }
    }
}

// Large SOS hold button for Status tab
@Composable
private fun SosHoldButtonLarge(onSosTriggered: () -> Unit) {
    val scope = rememberCoroutineScope()
    var isHolding by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }

    val scale by animateFloatAsState(
        targetValue = if (isHolding) 1.08f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size((200 * scale).dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isHolding = true
                            val start = System.currentTimeMillis()
                            var done = false
                            scope.launch {
                                while (isHolding) {
                                    val elapsed = System.currentTimeMillis() - start
                                    progress = (elapsed / 3000f).coerceIn(0f, 1f)
                                    if (progress >= 1f && !done) {
                                        done = true
                                        isHolding = false
                                        progress = 0f
                                        onSosTriggered()
                                    }
                                    delay(40)
                                }
                                if (!done) progress = 0f
                            }
                            tryAwaitRelease()
                            isHolding = false
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                // Outer glow ring
                drawCircle(
                    color = SOSRed.copy(alpha = if (isHolding) 0.15f else 0.06f),
                    radius = size.minDimension / 2 - 4.dp.toPx()
                )
                // Progress arc
                if (isHolding && progress > 0f) {
                    drawArc(
                        color = SOSRed,
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
                        topLeft = Offset(8.dp.toPx(), 8.dp.toPx()),
                        size = Size(size.width - 16.dp.toPx(), size.height - 16.dp.toPx())
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🆘", fontSize = 52.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (isHolding) "Hold… ${((1f - progress) * 3).toInt() + 1}s" else "Hold for SOS",
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (isHolding) SOSRed else Color(0xFF666666),
                    fontWeight = if (isHolding) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
        Text(
            "Hold 3 seconds to broadcast emergency SOS",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF555555),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
// FEED TAB
// ─────────────────────────────────────────────────────────────────────────
@Composable
fun EmergencyFeedTab(messages: List<SafeRelayMessage>, viewModel: ChatViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val sorted = remember(messages) {
        messages.sortedByDescending { it.timestamp }
    }

    var isHoldingSOS by remember { mutableStateOf(false) }
    var sosProgress by remember { mutableStateOf(0f) }
    var messageText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        Box(modifier = Modifier.weight(1f)) {
            if (sorted.isEmpty()) {
                EmptyFeedPlaceholder()
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize(),
                    reverseLayout = true
                ) {
                    items(sorted, key = { it.id }) { msg ->
                        EmergencyFeedCard(message = msg)
                    }
                }
            }
        }

        HorizontalDivider(color = SOSRed.copy(alpha = 0.15f), thickness = 0.5.dp)
        FeedInputBar(
            text = messageText,
            onTextChange = { messageText = it },
            onSend = {
                if (messageText.isNotBlank()) {
                    viewModel.sendMessage(messageText)
                    messageText = ""
                }
            },
            isHoldingSOS = isHoldingSOS,
            sosProgress = sosProgress,
            onSosHoldStart = {
                if (viewModel.isSosCooldownActive()) {
                    android.widget.Toast.makeText(context, "SOS Cooldown: ${viewModel.getRemainingSosCooldown()}s remaining", android.widget.Toast.LENGTH_SHORT).show()
                    return@FeedInputBar
                }
                isHoldingSOS = true
                scope.launch {
                    val start = System.currentTimeMillis()
                    while (isHoldingSOS) {
                        val elapsed = System.currentTimeMillis() - start
                        sosProgress = (elapsed / 3000f).coerceIn(0f, 1f)
                        if (sosProgress >= 1f) {
                            val geo = getLastLocationAsync(context)
                            val bat = getBatteryPercentAsync(context)
                            val sos = SosManager.buildSosMessage(viewModel.myNickname, geo, bat)
                            viewModel.sendEmergencyMessage(sos)
                            SosManager.triggerSosHaptic(context)
                            isHoldingSOS = false
                            sosProgress = 0f
                            break
                        }
                        delay(40)
                    }
                }
            },
            onSosHoldEnd = { isHoldingSOS = false; sosProgress = 0f }
        )
    }
}

@Composable
private fun EmptyFeedPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📡", fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text("No emergency reports", color = Color(0xFF555555),
                fontSize = 14.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(4.dp))
            Text("Mesh is listening…", color = Color(0xFF444444),
                fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// FEED CARD
// ─────────────────────────────────────────────────────────────────────────
@Composable
fun EmergencyFeedCard(message: SafeRelayMessage) {
    val priorityColor = when (message.priorityLevel) {
        PriorityLevel.CRITICAL -> SOSRed
        PriorityLevel.URGENT -> UrgentOrange
        PriorityLevel.INFO -> InfoGray
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val glowAlpha by if (message.priorityLevel == PriorityLevel.CRITICAL) {
        infiniteTransition.animateFloat(
            initialValue = 0.35f, targetValue = 0.9f,
            animationSpec = infiniteRepeatable(
                tween(700, easing = FastOutSlowInEasing),
                RepeatMode.Reverse
            ), label = "glow"
        )
    } else remember { mutableStateOf(0.5f) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (message.priorityLevel == PriorityLevel.CRITICAL) 1.5.dp else 0.5.dp,
                color = priorityColor.copy(alpha = if (message.priorityLevel == PriorityLevel.CRITICAL) glowAlpha else 0.3f),
                shape = RoundedCornerShape(10.dp)
            ),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (message.priorityLevel) {
                PriorityLevel.CRITICAL -> Color(0xFFFFEBEE)
                PriorityLevel.URGENT -> Color(0xFFFFF3E0)
                PriorityLevel.INFO -> Color.White
            }
        )
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .width(3.dp).height(50.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(priorityColor.copy(alpha = if (message.priorityLevel == PriorityLevel.CRITICAL) glowAlpha else 0.7f))
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(message.emergencyType.emoji, fontSize = 13.sp)
                    Spacer(Modifier.width(5.dp))
                    Text("@${message.sender}", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace, color = priorityColor,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(message.priorityLevel.label, fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace, color = priorityColor,
                        modifier = Modifier
                            .background(priorityColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(message.timestamp),
                        fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF555555)
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(message.content, fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace, color = Color(0xFF1A1A1A), lineHeight = 18.sp)
                message.geoLocation?.let { geo ->
                    Spacer(Modifier.height(3.dp))
                    Text("📍 ${String.format("%.4f", geo.latitude)}, ${String.format("%.4f", geo.longitude)}",
                        fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MeshBlue)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// FEED INPUT BAR
// ─────────────────────────────────────────────────────────────────────────
@Composable
private fun FeedInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isHoldingSOS: Boolean,
    sosProgress: Float,
    onSosHoldStart: () -> Unit,
    onSosHoldEnd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = {
                Text("type message…", color = Color.Gray,
                    fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            },
            modifier = Modifier.weight(1f),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color(0xFFE0E0E0),
                focusedBorderColor = SOSRed.copy(alpha = 0.5f),
                cursorColor = SOSRed,
                unfocusedTextColor = Color.Black,
                focusedTextColor = Color.Black
            ),
            shape = RoundedCornerShape(22.dp),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Monospace, fontSize = 13.sp),
            singleLine = true
        )
        Spacer(Modifier.width(4.dp))
        SosHoldButton(isHolding = isHoldingSOS, progress = sosProgress,
            onHoldStart = onSosHoldStart, onHoldEnd = onSosHoldEnd)
        Spacer(Modifier.width(2.dp))
        IconButton(onClick = onSend, enabled = text.isNotBlank()) {
            Icon(Icons.Filled.Send, contentDescription = "Send",
                tint = if (text.isNotBlank()) SOSRed else Color(0xFF333333))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// SOS HOLD BUTTON (small — for feed input bar)
// ─────────────────────────────────────────────────────────────────────────
@Composable
fun SosHoldButton(
    isHolding: Boolean,
    progress: Float,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onHoldStart()
                        tryAwaitRelease()
                        onHoldEnd()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (isHolding) {
            Box(modifier = Modifier.size(42.dp).clip(CircleShape)
                .background(SOSRed.copy(alpha = 0.18f)))
        }
        androidx.compose.foundation.Canvas(modifier = Modifier.size(38.dp)) {
            if (isHolding && progress > 0f) {
                drawArc(
                    color = SOSRed, startAngle = -90f, sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                    topLeft = Offset(2.dp.toPx(), 2.dp.toPx()),
                    size = Size(size.width - 4.dp.toPx(), size.height - 4.dp.toPx())
                )
            }
        }
        Text("⚠️", fontSize = if (isHolding) 16.sp else 18.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────
// NEARBY DEVICES TAB
// ─────────────────────────────────────────────────────────────────────────
@Composable
fun NearbyDevicesTab(
    connectedPeers: List<String>,
    peerNicknames: Map<String, String>,
    peerRSSI: Map<String, Int>,
    peerDirect: Map<String, Boolean>,
    onStartChat: (String) -> Unit,
    onScan: () -> Unit = {}
) {
    LaunchedEffect(Unit) {
        onScan() // Initial announcement on tab open
    }
    Column(
        modifier = Modifier.fillMaxSize().background(DarkBg)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("📡 Nearby Mesh Nodes", fontSize = 15.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace, color = SOSRed)
            Spacer(Modifier.weight(1f))
            // Signal badge
            Box(
                modifier = Modifier
                    .background(
                        if (connectedPeers.isNotEmpty()) MeshBlue.copy(alpha = 0.15f) else Color(0xFF1A1A1A),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    "${connectedPeers.size} node${if (connectedPeers.size != 1) "s" else ""}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (connectedPeers.isNotEmpty()) MeshBlue else Color(0xFF555555)
                )
            }
        }

        HorizontalDivider(color = Color(0xFF1E1E1E))

        if (connectedPeers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📡", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("No devices nearby", fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace, color = Color(0xFF555555))
                    Spacer(Modifier.height(6.dp))
                    Text("Bluetooth mesh is scanning…", fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace, color = Color(0xFF444444))
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(connectedPeers) { peerId ->
                    val nick = peerNicknames[peerId] ?: peerId.take(8)
                    val rssi = peerRSSI[peerId]
                    val isDirect = peerDirect[peerId] == true

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F0F)),
                        border = BorderStroke(0.5.dp, if (isDirect) MeshBlue.copy(alpha = 0.4f) else Color(0xFF2A2A2A))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Avatar
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape)
                                    .background(if (isDirect) MeshBlue.copy(alpha = 0.15f) else Color(0xFF1A1A1A))
                                    .border(1.dp, if (isDirect) MeshBlue.copy(alpha = 0.5f) else Color(0xFF333333), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    nick.take(1).uppercase(),
                                    fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                    color = if (isDirect) MeshBlue else Color(0xFF666666)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("@$nick", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isDirect) Color(0xFFEEEEEE) else Color(0xFF999999))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Connection type
                                    val connIcon = if (isDirect) "📶 Direct BLE" else "⟳ Mesh Relay"
                                    Text(connIcon, fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace, color = Color(0xFF666666))
                                    // RSSI signal strength
                                    rssi?.let {
                                        Spacer(Modifier.width(8.dp))
                                        val sigColor = when {
                                            it > -60 -> SafeGreen
                                            it > -80 -> UrgentOrange
                                            else -> SOSRed
                                        }
                                        Text("${it}dBm", fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace, color = sigColor)
                                    }
                                }
                            }
                            // Chat button
                            IconButton(
                                onClick = { onStartChat(peerId) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Filled.Chat, contentDescription = "Chat",
                                    tint = SOSRed, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// INCOMING SOS OVERLAY
// ─────────────────────────────────────────────────────────────────────────
@Composable
private fun IncomingSosOverlay(message: SafeRelayMessage, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SOSRed.copy(alpha = 0.93f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🆘", fontSize = 80.sp)
            Spacer(Modifier.height(16.dp))
            Text("SOS RECEIVED", fontSize = 28.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text("@${message.sender}", fontSize = 18.sp,
                fontFamily = FontFamily.Monospace, color = Color.White.copy(alpha = 0.9f))
            Spacer(Modifier.height(10.dp))
            Text(message.content.take(120), fontSize = 13.sp,
                fontFamily = FontFamily.Monospace, color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 30.dp))
            Spacer(Modifier.height(32.dp))
            Text("Tap anywhere to dismiss", fontSize = 12.sp,
                fontFamily = FontFamily.Monospace, color = Color.White.copy(alpha = 0.55f))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────
fun getBatteryPercent(context: Context): Int {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    return if (level >= 0 && scale > 0) ((level.toFloat() / scale) * 100).toInt() else -1
}

fun getLastLocation(context: Context): GeoLocation? {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        != PackageManager.PERMISSION_GRANTED) return null
    return try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
        val providers = lm?.getProviders(true) ?: return null
        var best: Location? = null
        for (p in providers) {
            val loc = lm.getLastKnownLocation(p) ?: continue
            if (best == null || loc.accuracy < best.accuracy) best = loc
        }
        best?.let { GeoLocation(it.latitude, it.longitude) }
    } catch (_: Exception) { null }
}

suspend fun getBatteryPercentAsync(context: Context): Int = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    getBatteryPercent(context)
}


suspend fun getLastLocationAsync(context: Context): GeoLocation? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    getLastLocation(context)
}

// ─────────────────────────────────────────────────────────────────────────
// UI Components for Home Screen
// ─────────────────────────────────────────────────────────────────────────
@Composable
fun EmergencyContactButton(
    emoji: String,
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(containerColor)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 32.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF333333)
        )
    }
}

@Composable
fun EmergencyActionDialog(
    contactType: String,
    contactNumber: String,
    onCall: (String) -> Unit,
    onMessage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Contact $contactType",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Text(
                "Do you want to Call $contactType or broadcast a custom Message to nearby devices?",
                fontSize = 14.sp
            )
        },
        confirmButton = {
            Button(
                onClick = { onCall(contactNumber) },
                colors = ButtonDefaults.buttonColors(containerColor = SOSRed)
            ) {
                Icon(Icons.Filled.Call, contentDescription = "Call", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Call $contactNumber")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = { onMessage(contactType) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SOSRed),
                border = BorderStroke(1.dp, SOSRed)
            ) {
                Icon(Icons.Filled.Message, contentDescription = "Message", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Message")
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyMessageInputDialog(
    contactType: String,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var messageText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Request $contactType",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column {
                Text(
                    "Describe your emergency needs. This will be broadcasted to all nearby devices.",
                    fontSize = 14.sp,
                    color = Color.DarkGray
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { Text("E.g., Need immediate medical help, severe bleeding...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SOSRed,
                        cursorColor = SOSRed
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (messageText.isNotBlank()) {
                        onSend(messageText)
                    }
                },
                enabled = messageText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = SOSRed)
            ) {
                Icon(Icons.Filled.Send, contentDescription = "Send", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Send Broadcast")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray)
            ) {
                Text("Cancel")
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp)
    )
}

// ─────────────────────────────────────────────────────────────────────────
// New Light Mode UI Components
// ─────────────────────────────────────────────────────────────────────────
@Composable
fun QuickStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    label: String,
    bgColor: Color,
    iconColor: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A2E)
            )
            Text(
                text = label,
                fontSize = 10.sp,
                color = Color(0xFF6B7280)
            )
        }
    }
}

@Composable
fun EmergencyServiceButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    number: String,
    bgColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable { onClick() }
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            color = bgColor
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF1A1A2E),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF1A1A2E)
        )
        Text(
            text = number,
            fontSize = 10.sp,
            color = Color(0xFF9CA3AF)
        )
    }
}

@Composable
fun OverlappingAvatars(peers: List<String>, peerNicknames: Map<String, String>) {
    val displayPeers = peers.take(4)
    if (displayPeers.isEmpty()) {
        Text("No nearby devices", fontSize = 13.sp, color = Color.Gray)
        return
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.CenterStart) {
            displayPeers.forEachIndexed { index, peer ->
                val initials = (peerNicknames[peer] ?: peer).take(2).uppercase()
                Box(
                    modifier = Modifier
                        .padding(start = (index * 24).dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE0E0E0))
                        .border(2.dp, Color(0xFFF5F5F5), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(initials, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
                }
            }
            if (peers.size > 4) {
                Box(
                    modifier = Modifier
                        .padding(start = (4 * 24).dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFB300))
                        .border(2.dp, Color(0xFFF5F5F5), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+${peers.size - 4}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun SafetyActionButton(
    isSosActive: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isSosActive) SafeGreen else Color.Black
    val textColor = Color.White
    val text = if (isSosActive) "I'M SAFE NOW" else "SHARE SAFETY STATUS"
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable { onClick() }
            .shadow(4.dp, RoundedCornerShape(32.dp)),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isSosActive) {
                    Icon(
                        Icons.Filled.VerifiedUser,
                        contentDescription = null,
                        tint = SafeGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    text = text,
                    color = textColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp
                )
            }
        }
    }
}
@Composable
fun ProfessionalSosButton(onSosTriggered: () -> Unit) {
    Box(
        modifier = Modifier.size(260.dp),
        contentAlignment = Alignment.Center
    ) {
        // Static Outer Ring (Transparent/Soft)
        Box(
            modifier = Modifier
                .size(240.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF5252).copy(alpha = 0.05f))
                .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.1f), CircleShape)
        )
        
        // Inner Subtle Glow
        Box(
            modifier = Modifier
                .size(190.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF5252).copy(alpha = 0.12f))
        )

        // Main Professional Button
        Box(
            modifier = Modifier
                .size(150.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = CircleShape,
                    ambientColor = Color(0xFFFF2D55).copy(alpha = 0.5f),
                    spotColor = Color(0xFFFF2D55)
                )
                .clip(CircleShape)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color(0xFFFF5E3A), Color(0xFFFF2D55))
                    )
                )
                .clickable { onSosTriggered() },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.Wifi,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "SOS",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }
    }
}

@Composable
fun ProfileTab(
    viewModel: ChatViewModel,
    profile: UserProfile,
    profileManager: UserProfileManager,
    onEditProfile: () -> Unit,
    onReportClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var alertRadius by remember { mutableFloatStateOf(20f) }
    var localNotifications by remember { mutableStateOf(true) }
    var sosAlerts by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9FAFB))
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))
        
        // Large Profile Avatar
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(BrandPurple.copy(alpha = 0.1f))
                .border(3.dp, BrandPurple.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            val initials = if (profile.fullName.isNotBlank()) {
                profile.fullName.split(" ").filter { it.isNotBlank() }.take(2).map { it[0] }.joinToString("")
            } else {
                viewModel.myNickname.take(2).uppercase()
            }
            Text(
                text = initials,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = BrandPurple
            )
        }
        
        Spacer(Modifier.height(16.dp))
        
        Text(
            text = if (profile.fullName.isBlank()) viewModel.myNickname else profile.fullName,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        Text(
            text = "@${viewModel.myNickname}",
            fontSize = 15.sp,
            color = Color.Gray
        )
        
        Spacer(Modifier.height(40.dp))
        
        // Settings Section
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Alert Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Radar, null, tint = BrandPurple)
                        Spacer(Modifier.width(12.dp))
                        Text("Incident Radius", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.weight(1f))
                        Text("${alertRadius.toInt()} km", fontWeight = FontWeight.Bold, color = BrandPurple)
                    }
                    Slider(
                        value = alertRadius,
                        onValueChange = { alertRadius = it },
                        valueRange = 5f..50f,
                        modifier = Modifier.padding(top = 8.dp),
                        colors = SliderDefaults.colors(thumbColor = BrandPurple, activeTrackColor = BrandPurple)
                    )
                    
                    Divider(Modifier.padding(vertical = 16.dp), thickness = 0.5.dp, color = Color.LightGray)
                    
                    ToggleRow("Local Incidents", "Notify about nearby reports", localNotifications) { localNotifications = it }
                    Spacer(Modifier.height(16.dp))
                    ToggleRow("SOS Alerts", "Priority critical alerts", sosAlerts) { sosAlerts = it }
                }
            }
        }
        
        Spacer(Modifier.height(32.dp))
        
        // Action Sections
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ProfileMenuRow(
                icon = Icons.Filled.Edit,
                title = "Edit Profile",
                subtitle = "Update your info and emergency contacts",
                onClick = onEditProfile
            )
            
            ProfileMenuRow(
                icon = Icons.Filled.Report,
                title = "Report Incident",
                subtitle = "Help your community stay safe",
                onClick = onReportClick
            )

            ProfileMenuRow(
                icon = Icons.Filled.Share,
                title = "Share SafeRelay",
                subtitle = "Invite friends to the mesh network",
                onClick = { /* Share logic */ }
            )
        }
        
        Spacer(Modifier.height(40.dp))
        Text("SafeRelay v1.8.0", fontSize = 12.sp, color = Color.LightGray)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 12.sp, color = Color.Gray)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = BrandPurple)
        )
    }
}

@Composable
fun ProfileMenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF0F0F0)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Text(subtitle, fontSize = 12.sp, color = Color.Gray)
            }
            
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color.LightGray)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Modern UI Components
// ─────────────────────────────────────────────────────────────────────────
@Composable
fun StatusInfoCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ModernEmergencyButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}

@Composable
fun ModernSosButton(onSosTriggered: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            // Outer glow ring
            Box(
                modifier = Modifier
                    .size(190.dp)
                    .clip(CircleShape)
                    .background(SOSRed.copy(alpha = 0.08f))
            )
            
            // Middle ring
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(SOSRed.copy(alpha = 0.12f))
            )
            
            // Main SOS button
            Surface(
                modifier = Modifier
                    .size(130.dp)
                    .clickable { onSosTriggered() },
                shape = CircleShape,
                color = SOSRed,
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "SOS",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
        Text(
            text = "Tap to broadcast emergency alert",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ModernPeerStatus(
    connectedPeers: List<String>,
    peerNicknames: Map<String, String>
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Animated peer indicators
        if (connectedPeers.isNotEmpty()) {
            connectedPeers.take(3).forEachIndexed { index, peer ->
                val initials = (peerNicknames[peer] ?: peer).take(2).uppercase()
                Box(
                    modifier = Modifier
                        .offset(x = (-index * 8).dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MeshBlue.copy(alpha = 0.2f))
                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MeshBlue
                    )
                }
            }
            
            if (connectedPeers.size > 3) {
                Box(
                    modifier = Modifier
                        .offset(x = (-3 * 8).dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(UrgentOrange)
                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+${connectedPeers.size - 3}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            
            Spacer(Modifier.width(12.dp))
            
            Text(
                text = "${connectedPeers.size} trusted ${if (connectedPeers.size == 1) "peer" else "peers"} nearby",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Icon(
                imageVector = Icons.Filled.SignalWifi0Bar,
                contentDescription = null,
                tint = InfoGray,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "No peers connected",
                fontSize = 14.sp,
                color = InfoGray
            )
        }
    }
}

@Composable
fun ModernSafetyButton(
    isSosActive: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isSosActive) SafeGreen else MeshBlue
    val text = if (isSosActive) "I'M SAFE NOW" else "CHECK IN SAFELY"
    
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
    ) {
        Icon(
            imageVector = if (isSosActive) Icons.Filled.CheckCircle else Icons.Filled.Shield,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
// REPORT EMERGENCY SHEET (Category Selection)
// ─────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportEmergencySheet(
    onCategorySelected: (EmergencyCategory) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Report Emergency",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, null, tint = Color.Gray)
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            // Grid of Categories
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(350.dp)
            ) {
                items(EmergencyCategories) { category ->
                    CategoryCard(category) { onCategorySelected(category) }
                }
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun CategoryCard(category: EmergencyCategory, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
        modifier = Modifier.fillMaxWidth().height(140.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start
        ) {
            Surface(
                shape = CircleShape,
                color = category.color.copy(alpha = 0.1f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(category.emoji, fontSize = 20.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                category.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                lineHeight = 18.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// EMERGENCY DETAILS SCREEN
// ─────────────────────────────────────────────────────────────────────────
@Composable
fun EmergencyDetailsScreen(
    category: EmergencyCategory,
    locationAddress: String,
    onBack: () -> Unit,
    onPickLocation: () -> Unit,
    onSend: (String) -> Unit
) {
    var additionalInfo by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF9FAFB))
            .statusBarsPadding()
    ) {
        // Custom Top Bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ChevronLeft, null, tint = Color.Black)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Report Emergency",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(48.dp)) // Placeholder for balance
        }
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            
            // Large Category Icon
            Surface(
                shape = CircleShape,
                color = if (category.title == "Armed Robbery") Color(0xFFFF3B30) else category.color,
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(category.emoji, fontSize = 40.sp)
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            Text(
                category.title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            
            Spacer(Modifier.height(8.dp))
            
            Text(
                "You are about to send an emergency alert. Emergency services will be contacted immediately.",
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            
            Spacer(Modifier.height(32.dp))
            
            // Location Picker Card
            Card(
                onClick = onPickLocation,
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.LocationOn, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Your current Location", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                        Spacer(Modifier.height(4.dp))
                        Text(locationAddress, fontSize = 13.sp, color = Color.Gray, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray)
                }
            }
            
            Spacer(Modifier.height(20.dp))
            
            // Additional Info
            Text(
                "Additional info (optional)",
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = additionalInfo,
                onValueChange = { additionalInfo = it },
                placeholder = { Text("Type here...", color = Color.LightGray) },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White,
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = MeshBlue
                )
            )
            
            Spacer(Modifier.height(24.dp))
            
            // Safety Guidelines
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFF3E8FF),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PrivacyTip, null, tint = Color(0xFF7C3AED), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Safety Guidelines", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7C3AED))
                    }
                    Spacer(Modifier.height(12.dp))
                    val guidelines = listOf(
                        "Stay in a safe location until help arrives",
                        "Keep your phone charged and accessible",
                        "Be ready to provide additional information",
                        "Call 911 if situation becomes critical"
                    )
                    guidelines.forEach { rule ->
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text("•", color = Color(0xFF7C3AED), modifier = Modifier.padding(end = 8.dp))
                            Text(rule, fontSize = 13.sp, color = Color(0xFF6B21A8))
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(24.dp))
            
            // Emergency Contacts
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7ED)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Phone, null, tint = Color(0xFFC2410C), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Emergency Contacts", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC2410C))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Police", fontSize = 13.sp, color = Color(0xFF9A3412))
                        Text("911", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9A3412))
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Fire Department", fontSize = 13.sp, color = Color(0xFF9A3412))
                        Text("911", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF9A3412))
                    }
                }
            }
            
            Spacer(Modifier.height(40.dp))
            
            // Bottom Actions
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.LightGray)
                ) {
                    Text("Cancel", color = Color.Black)
                }
                
                Button(
                    onClick = { onSend(additionalInfo) },
                    modifier = Modifier.weight(2f).height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F2937))
                ) {
                    Icon(Icons.Default.ArrowOutward, null, tint = Color.LightGray, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Send Emergency Alert", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
