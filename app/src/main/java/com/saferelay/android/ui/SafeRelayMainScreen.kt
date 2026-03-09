package com.saferelay.android.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.os.BatteryManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
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
val SafeGreen  = Color(0xFF30D158)
val InfoGray   = Color(0xFF8E8E93)
val DarkBg     = Color(0xFF000000)
val CardBg     = Color(0xFF111111)
val MeshBlue   = Color(0xFF3A8FFF)

// ── Tabs ───────────────────────────────────────────────────────────────────
enum class SafeRelayTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME("Home", Icons.Filled.Home),
    CHAT("Messages", Icons.Filled.Chat),
    MAP("Map", Icons.Filled.Map),
    PROFILE("Profile", Icons.Filled.Person),
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

    // Sync profile name → chat nickname
    LaunchedEffect(profile.fullName) {
        if (profile.fullName.isNotBlank()) {
            viewModel.setNickname(profile.fullName)
        }
    }

    // Watch for incoming SOS
    LaunchedEffect(messages) {
        val latestSos = messages.lastOrNull { it.isSosAlert() && it.sender != viewModel.myNickname }
        if (latestSos != null && showIncomingSosAlert?.id != latestSos.id) {
            showIncomingSosAlert = latestSos
            SosManager.triggerIncomingSosHaptic(context)
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
                    SafeRelayTab.CHAT -> "Chat"
                    SafeRelayTab.MAP -> "Map"
                    SafeRelayTab.PROFILE -> "Profile"
                }
                SafeRelayHeader(
                    title = headerTitle,
                    profile = profile,
                    connectedPeerCount = connectedPeers.size,
                    onMapClick = { showDisasterMap = true },
                    onProfileClick = { selectedTab = SafeRelayTab.PROFILE }, // Profile restored
                    onBrandClick = { selectedTab = SafeRelayTab.HOME }
                )
            }

            // ── Body ───────────────────────────────────────────────────
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    SafeRelayTab.HOME -> StatusTab(viewModel = viewModel, profile = profile, onProfileClick = { selectedTab = SafeRelayTab.PROFILE })
                    SafeRelayTab.CHAT   -> SafeRelayTheme(darkTheme = false) {
                        ChatScreen(viewModel = viewModel, embedded = true)
                    }
                    SafeRelayTab.MAP    -> DisasterMapTab(
                        messages = messages,
                        myNickname = viewModel.myNickname,
                        peerNicknames = peerNicknames,
                        onOpenChat = { pid: String, nick: String ->
                            onOpenPrivateChat(pid, nick)
                        }
                    )
                    SafeRelayTab.PROFILE -> ProfileTab(
                        viewModel = viewModel,
                        profile = profile,
                        profileManager = profileManager,
                        onEditProfile = { showProfile = true }
                    )
                }
            }

            // ── Bottom Navigation (hidden when keyboard up) ──
            if (!imeVisible) {
                HorizontalDivider(color = Color(0xFFE5E7EB), thickness = 1.dp)

                NavigationBar(
                    containerColor = Color.White,
                    tonalElevation = 4.dp,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    SafeRelayTab.values().forEach { tab ->
                        val selected = selectedTab == tab
                        NavigationBarItem(
                            selected = selected,
                            onClick = { selectedTab = tab },
                            icon = {
                                Icon(
                                    tab.icon,
                                    contentDescription = tab.label,
                                    tint = if (selected) MeshBlue else Color(0xFF9CA3AF),
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            label = {
                                Text(
                                    tab.label,
                                    fontSize = 10.sp,
                                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                    color = if (selected) MeshBlue else Color(0xFF9CA3AF)
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MeshBlue.copy(alpha = 0.1f)
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
}

// ─────────────────────────────────────────────────────────────────────────
// Header
// ─────────────────────────────────────────────────────────────────────────
@Composable
private fun SafeRelayHeader(
    title: String,
    profile: UserProfile,
    connectedPeerCount: Int,
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
                Icon(
                    Icons.Filled.Map,
                    contentDescription = "Map",
                    tint = Color(0xFF6B7280)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// STATUS TAB – home screen with "I'm Safe" / SOS broadcast
// ─────────────────────────────────────────────────────────────────────────
@Composable
fun StatusTab(viewModel: ChatViewModel, profile: UserProfile, onProfileClick: () -> Unit = {}) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsState(emptyList())
    val sosMessages = messages.filter { it.isSosAlert() }
    
    val userName = if (profile.fullName.isNotBlank()) profile.fullName.split(" ").first() else "User"

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFBFBFB))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- Custom Header ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFF3F4F6),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Face, "Avatar", tint = Color(0xFF374151), modifier = Modifier.size(24.dp))
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Hi $userName",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1F2937)
                    )
                }
                
                IconButton(onClick = { /* Notifications */ }) {
                    Icon(Icons.Filled.NotificationsNone, "Notifications", tint = Color(0xFF374151))
                }
            }

            Spacer(Modifier.height(24.dp))

            // --- Location Card ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, Color(0xFFF3F4F6))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color(0xFFF3F4F6), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.LocationOn, null, tint = Color(0xFF6B7280), modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Your current Location",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1F2937)
                        )
                        Text(
                            text = "181 Dutsinma-Malumfashi Road, Dutsinma, Katsina", // Mock address from image
                            fontSize = 12.sp,
                            color = Color(0xFF9CA3AF),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = Color(0xFFD1D5DB))
                }
            }

            Spacer(Modifier.height(32.dp))

            // --- Active Emergencies Section ---
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Active Emergencies",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F2937)
                )
                
                Spacer(Modifier.height(16.dp))

                if (sosMessages.isEmpty()) {
                    // Empty State matching image redesign
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFF3F4F6))
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 40.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Custom Face Illustration
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .background(Color(0xFFF9FAFB), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.size(60.dp)) {
                                    val strokeWidth = 2.dp.toPx()
                                    // Rounded square box for face
                                    drawRoundRect(
                                        color = Color(0xFF6B7280),
                                        topLeft = Offset(10.dp.toPx(), 10.dp.toPx()),
                                        size = Size(40.dp.toPx(), 40.dp.toPx()),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
                                        style = Stroke(width = strokeWidth)
                                    )
                                    // X X eyes using generic Line - mockup
                                    drawLine(Color(0xFF6B7280), Offset(18.dp.toPx(), 22.dp.toPx()), Offset(24.dp.toPx(), 28.dp.toPx()), strokeWidth, StrokeCap.Round)
                                    drawLine(Color(0xFF6B7280), Offset(24.dp.toPx(), 22.dp.toPx()), Offset(18.dp.toPx(), 28.dp.toPx()), strokeWidth, StrokeCap.Round)
                                    drawLine(Color(0xFF6B7280), Offset(36.dp.toPx(), 22.dp.toPx()), Offset(42.dp.toPx(), 28.dp.toPx()), strokeWidth, StrokeCap.Round)
                                    drawLine(Color(0xFF6B7280), Offset(42.dp.toPx(), 22.dp.toPx()), Offset(36.dp.toPx(), 28.dp.toPx()), strokeWidth, StrokeCap.Round)
                                    // Smile
                                    drawArc(
                                        color = Color(0xFF6B7280),
                                        startAngle = 10f,
                                        sweepAngle = 160f,
                                        useCenter = false,
                                        topLeft = Offset(22.dp.toPx(), 32.dp.toPx()),
                                        size = Size(16.dp.toPx(), 10.dp.toPx()),
                                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Text("Its Amazing", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F2937))
                            Spacer(Modifier.height(4.dp))
                            Text("Looks like thier are no active emergencies", fontSize = 12.sp, color = Color(0xFF9CA3AF))
                        }
                    }
                } else {
                    // Active list based on image card design
                    sosMessages.forEach { msg ->
                        EmergencyCard(msg)
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
            
            Spacer(Modifier.height(80.dp)) // Space for FAB
        }

        // --- Custom SOS FAB ---
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 24.dp)
                .size(70.dp)
                .shadow(12.dp, CircleShape)
                .clickable {
                    val geo = getLastLocation(context)
                    val bat = getBatteryPercent(context)
                    val sos = SosManager.buildSosMessage(viewModel.myNickname, geo, bat)
                    viewModel.sendEmergencyMessage(sos)
                    SosManager.triggerSosHaptic(context)
                },
            shape = CircleShape,
            color = SOSRed
        ) {
            Box(contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.NotificationsActive, null, tint = Color.White, modifier = Modifier.size(24.dp).offset(y = 2.dp))
                    Text("SOS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun EmergencyCard(msg: SafeRelayMessage) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, Color(0xFFF3F4F6))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = SOSRed, modifier = Modifier.size(36.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Warning, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(text = "Security Alert", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1F2937))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocationOn, null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(text = "Parliament Street - Block 5", fontSize = 12.sp, color = Color(0xFF9CA3AF))
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Filled.AccessTime, null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(text = "2 Minutes ago", fontSize = 12.sp, color = Color(0xFF9CA3AF))
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { /* Forget */ },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFD1D5DB))
                ) {
                    Text("Forget", color = Color(0xFF374151))
                }
                Button(
                    onClick = { /* Respond */ },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F2937))
                ) {
                    Text("Respond", color = Color.White)
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
                    modifier = Modifier.fillMaxSize()
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
                isHoldingSOS = true
                scope.launch {
                    val start = System.currentTimeMillis()
                    while (isHoldingSOS) {
                        val elapsed = System.currentTimeMillis() - start
                        sosProgress = (elapsed / 3000f).coerceIn(0f, 1f)
                        if (sosProgress >= 1f) {
                            val geo = getLastLocation(context)
                            val bat = getBatteryPercent(context)
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
                PriorityLevel.CRITICAL -> Color(0xFF1A0000)
                PriorityLevel.URGENT -> Color(0xFF1A0D00)
                PriorityLevel.INFO -> CardBg
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
                    fontFamily = FontFamily.Monospace, color = Color(0xFFDDDDDD), lineHeight = 18.sp)
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
            .background(Color(0xFF0A0A0A))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = {
                Text("type message…", color = Color(0xFF444444),
                    fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            },
            modifier = Modifier.weight(1f),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color(0xFF2A2A2A),
                focusedBorderColor = SOSRed.copy(alpha = 0.5f),
                cursorColor = SOSRed,
                unfocusedTextColor = Color(0xFFCCCCCC),
                focusedTextColor = Color.White
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
    onEditProfile: () -> Unit
) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Large Profile Avatar
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(SOSRed.copy(alpha = 0.1f))
                .border(2.dp, SOSRed.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            val initials = if (profile.fullName.isNotBlank()) {
                profile.fullName.split(" ").filter { it.isNotBlank() }.take(2).map { it[0] }.joinToString("")
            } else {
                viewModel.myNickname.take(2).uppercase()
            }
            Text(
                text = initials,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = SOSRed
            )
        }
        
        Spacer(Modifier.height(16.dp))
        
        Text(
            text = if (profile.fullName.isBlank()) viewModel.myNickname else profile.fullName,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.Black
        )
        Text(
            text = "SafeRelay Verified User",
            fontSize = 14.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Medium
        )
        
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
                icon = Icons.Filled.Info,
                title = "Personal Information",
                subtitle = "Age, Gender, Blood Group, etc.",
                onClick = { /* Could show restricted read-only view */ }
            )
            
            ProfileMenuRow(
                icon = Icons.Filled.Settings,
                title = "App Settings",
                subtitle = "Notifications, Mesh Networking, Data",
                onClick = { /* Settings logic */ }
            )
            
            ProfileMenuRow(
                icon = Icons.Filled.PrivacyTip,
                title = "Privacy & Security",
                subtitle = "Encryption keys and visibility",
                onClick = { /* Privacy logic */ }
            )
        }
        
        Spacer(Modifier.weight(1f))
        
        Text(
            text = "SafeRelay Android v1.7.1",
            fontSize = 12.sp,
            color = Color.LightGray,
            fontWeight = FontWeight.Normal
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
