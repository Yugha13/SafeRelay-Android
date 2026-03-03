package com.bitchat.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.BatteryManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.bitchat.android.model.*
import com.bitchat.android.ui.SosManager.isSosAlert
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.min

// ── Colour constants matching iOS ──────────────────────────────────────────
private val SOSRed = Color(0xFFFF3B30)
private val UrgentOrange = Color(0xFFFF9500)
private val SafeGreen = Color(0xFF30D158)
private val InfoGray = Color(0xFF8E8E93)
private val DarkBg = Color(0xFF000000)
private val CardBg = Color(0xFF111111)

// ── Tabs ───────────────────────────────────────────────────────────────────
private enum class SafeRelayTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    FEED("Feed", Icons.Filled.Feed),
    MAP("Map", Icons.Filled.Map),
    RESOURCES("Resources", Icons.Filled.Inventory2),
    STATUS("Status", Icons.Filled.Person)
}

// ─────────────────────────────────────────────────────────────────────────
// SafeRelayMainScreen — replaces old ChatScreen as the top-level UI
// ─────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafeRelayMainScreen(
    viewModel: ChatViewModel,
    onOpenMeshChat: () -> Unit = {},
    onOpenProfile: () -> Unit = {}
) {
    val context = LocalContext.current
    val profileManager = remember { UserProfileManager.getInstance(context) }
    val profile by profileManager.profile.collectAsState()
    val messages by viewModel.messages.collectAsState(emptyList())
    val connectedPeers by viewModel.connectedPeers.collectAsState(emptyList())

    var selectedTab by remember { mutableStateOf(SafeRelayTab.FEED) }
    var showDisasterMap by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var showIncomingSosAlert by remember { mutableStateOf<BitchatMessage?>(null) }

    // Watch for incoming SOS messages
    LaunchedEffect(messages) {
        val latestSos = messages.lastOrNull { it.isSosAlert() && it.sender != viewModel.myNickname }
        if (latestSos != null && (showIncomingSosAlert == null || showIncomingSosAlert?.id != latestSos.id)) {
            showIncomingSosAlert = latestSos
            SosManager.triggerIncomingSosHaptic(context)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Header ─────────────────────────────────────────────────────
            SafeRelayHeader(
                profile = profile,
                connectedPeerCount = connectedPeers.size,
                onMapClick = { showDisasterMap = true },
                onProfileClick = { showProfile = true },
                onBrandClick = onOpenMeshChat
            )

            HorizontalDivider(color = SOSRed.copy(alpha = 0.3f), thickness = 0.5.dp)

            // ── Body (tabs) ────────────────────────────────────────────────
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    SafeRelayTab.FEED -> EmergencyFeedTab(
                        messages = messages,
                        viewModel = viewModel
                    )
                    SafeRelayTab.MAP -> DisasterMapTab(messages = messages)
                    SafeRelayTab.RESOURCES -> ResourcesTab()
                    SafeRelayTab.STATUS -> StatusTab(
                        viewModel = viewModel,
                        profile = profile
                    )
                }
            }

            HorizontalDivider(color = SOSRed.copy(alpha = 0.3f), thickness = 0.5.dp)

            // ── Bottom navigation ──────────────────────────────────────────
            NavigationBar(
                containerColor = Color(0xFF0A0A0A),
                tonalElevation = 0.dp
            ) {
                SafeRelayTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                tab.icon,
                                contentDescription = tab.label,
                                tint = if (selectedTab == tab) SOSRed else Color(0xFF555555)
                            )
                        },
                        label = {
                            Text(
                                tab.label,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (selectedTab == tab) SOSRed else Color(0xFF555555)
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = SOSRed.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }

        // ── Incoming SOS overlay ───────────────────────────────────────────
        showIncomingSosAlert?.let { sos ->
            IncomingSosOverlay(message = sos, onDismiss = { showIncomingSosAlert = null })
        }
    }

    // Sheets
    if (showDisasterMap) {
        DisasterMapSheet(messages = messages, onDismiss = { showDisasterMap = false })
    }
    if (showProfile) {
        UserProfileSheet(
            profileManager = profileManager,
            onDismiss = { showProfile = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Header — "SafeRelay/" + profile initials + map icon + peer count
// ─────────────────────────────────────────────────────────────────────────
@Composable
private fun SafeRelayHeader(
    profile: UserProfile,
    connectedPeerCount: Int,
    onMapClick: () -> Unit,
    onProfileClick: () -> Unit,
    onBrandClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(DarkBg)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Brand title (tap = open classic mesh chat)
        Text(
            text = "SafeRelay/",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            color = SOSRed,
            modifier = Modifier.clickable { onBrandClick() }
        )

        Spacer(Modifier.width(6.dp))

        // Profile name / initials (tap = open profile)
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(SOSRed.copy(alpha = 0.25f))
                .border(1.dp, SOSRed.copy(alpha = 0.6f), CircleShape)
                .clickable { onProfileClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (profile.fullName.isBlank()) "?" else profile.initials,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = SOSRed
            )
        }

        Spacer(Modifier.weight(1f))

        // Disaster Map button
        IconButton(onClick = onMapClick) {
            Icon(Icons.Filled.Map, contentDescription = "Disaster Map", tint = SOSRed)
        }

        // Peer count
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Group,
                contentDescription = "Peers",
                tint = if (connectedPeerCount > 0) Color(0xFF3A8FFF) else Color(0xFF555555),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = "$connectedPeerCount",
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = if (connectedPeerCount > 0) Color(0xFF3A8FFF) else Color(0xFF555555)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Emergency Feed Tab — priority-sorted message cards
// ─────────────────────────────────────────────────────────────────────────
@Composable
fun EmergencyFeedTab(
    messages: List<BitchatMessage>,
    viewModel: ChatViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Priority-sorted: CRITICAL first, then URGENT, then INFO; newest within each tier
    val sortedMessages = remember(messages) {
        with(EmergencyIntelligenceEngine) { messages.sortedByEmergencyPriority() }
    }

    // SOS hold state
    var isHoldingSOS by remember { mutableStateOf(false) }
    var sosProgress by remember { mutableStateOf(0f) }
    val requiredHoldMs = 3000L

    // Input text for regular messages
    var messageText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        // Feed list
        Box(modifier = Modifier.weight(1f)) {
            if (sortedMessages.isEmpty()) {
                EmptyFeedPlaceholder()
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(sortedMessages, key = { it.id }) { msg ->
                        EmergencyFeedCard(message = msg)
                    }
                }
            }
        }

        // Input bar with SOS button
        HorizontalDivider(color = SOSRed.copy(alpha = 0.2f), thickness = 0.5.dp)
        EmergencyInputBar(
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
                        sosProgress = (elapsed.toFloat() / requiredHoldMs).coerceIn(0f, 1f)
                        if (sosProgress >= 1f) {
                            // Trigger SOS
                            val batteryPct = getBatteryPercent(context)
                            val location = getLastLocation(context)
                            val sosMsg = SosManager.buildSosMessage(
                                senderNickname = viewModel.myNickname,
                                location = location,
                                batteryPercent = batteryPct
                            )
                            viewModel.sendEmergencyMessage(sosMsg)
                            SosManager.triggerSosHaptic(context)
                            isHoldingSOS = false
                            sosProgress = 0f
                            break
                        }
                        delay(50)
                    }
                }
            },
            onSosHoldEnd = {
                isHoldingSOS = false
                sosProgress = 0f
            }
        )
    }
}

@Composable
private fun EmptyFeedPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📡", fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "No emergency reports yet",
                color = Color(0xFF555555),
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Mesh network is listening…",
                color = Color(0xFF444444),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Emergency Feed Card — single message card with priority badge
// ─────────────────────────────────────────────────────────────────────────
@Composable
fun EmergencyFeedCard(message: BitchatMessage) {
    val priorityColor = when (message.priorityLevel) {
        PriorityLevel.CRITICAL -> SOSRed
        PriorityLevel.URGENT -> UrgentOrange
        PriorityLevel.INFO -> InfoGray
    }
    val cardBorder = when (message.priorityLevel) {
        PriorityLevel.CRITICAL -> SOSRed.copy(alpha = 0.7f)
        PriorityLevel.URGENT -> UrgentOrange.copy(alpha = 0.5f)
        PriorityLevel.INFO -> Color(0xFF2A2A2A)
    }

    val pulsate = message.priorityLevel == PriorityLevel.CRITICAL
    val infiniteTransition = rememberInfiniteTransition(label = "sos_pulse")
    val glowAlpha by if (pulsate) {
        infiniteTransition.animateFloat(
            initialValue = 0.3f, targetValue = 0.9f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "glow"
        )
    } else {
        remember { mutableStateOf(0.3f) }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (message.priorityLevel == PriorityLevel.CRITICAL) 1.5.dp else 0.5.dp,
                color = cardBorder,
                shape = RoundedCornerShape(10.dp)
            ),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (message.priorityLevel) {
                PriorityLevel.CRITICAL -> Color(0xFF1A0000)
                PriorityLevel.URGENT -> Color(0xFF1A0D00)
                PriorityLevel.INFO -> CardBg
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            // Priority stripe
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(50.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(priorityColor.copy(alpha = if (pulsate) glowAlpha else 0.8f))
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Type emoji badge
                    Text(
                        text = message.emergencyType.emoji,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.width(6.dp))
                    // Sender
                    Text(
                        text = "@${message.sender}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = priorityColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    // Priority label
                    Text(
                        text = message.priorityLevel.label,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = priorityColor,
                        modifier = Modifier
                            .background(priorityColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    // Timestamp
                    Text(
                        text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(message.timestamp),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF555555)
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    text = message.content,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFDDDDDD),
                    lineHeight = 18.sp
                )
                // Geo location
                message.geoLocation?.let { geo ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "📍 ${String.format("%.4f", geo.latitude)}, ${String.format("%.4f", geo.longitude)}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF3A8FFF)
                    )
                }
                // Relay indicator
                if (message.isRelay) {
                    Text(
                        text = "⟳ relayed",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF444444)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Input Bar with SOS hold button
// ─────────────────────────────────────────────────────────────────────────
@Composable
private fun EmergencyInputBar(
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
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Text input
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = {
                Text(
                    "type a message…",
                    color = Color(0xFF444444),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
            },
            modifier = Modifier.weight(1f),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color(0xFF2A2A2A),
                focusedBorderColor = SOSRed.copy(alpha = 0.5f),
                cursorColor = SOSRed,
                unfocusedTextColor = Color(0xFFCCCCCC),
                focusedTextColor = Color(0xFFFFFFFF)
            ),
            shape = RoundedCornerShape(24.dp),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            ),
            singleLine = true
        )

        Spacer(Modifier.width(6.dp))

        // SOS hold button (⚠️ triangle — hold 3s to send)
        SosHoldButton(
            isHolding = isHoldingSOS,
            progress = sosProgress,
            onHoldStart = onSosHoldStart,
            onHoldEnd = onSosHoldEnd
        )

        Spacer(Modifier.width(4.dp))

        // Send button
        IconButton(
            onClick = onSend,
            enabled = text.isNotBlank()
        ) {
            Icon(
                Icons.Filled.Send,
                contentDescription = "Send",
                tint = if (text.isNotBlank()) SOSRed else Color(0xFF333333)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// SOS Hold Button — matches iOS sosButtonView exactly
// ─────────────────────────────────────────────────────────────────────────
@Composable
fun SosHoldButton(
    isHolding: Boolean,
    progress: Float,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isHolding) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    Box(
        modifier = Modifier
            .size(44.dp)
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
        // Background pulse when holding
        if (isHolding) {
            Box(
                modifier = Modifier
                    .size((44 * scale).dp)
                    .clip(CircleShape)
                    .background(SOSRed.copy(alpha = 0.2f))
            )
        }

        // Arc progress ring
        androidx.compose.foundation.Canvas(
            modifier = Modifier.size(40.dp)
        ) {
            if (isHolding && progress > 0f) {
                drawArc(
                    color = SOSRed,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
                    topLeft = Offset(3.dp.toPx(), 3.dp.toPx()),
                    size = Size(size.width - 6.dp.toPx(), size.height - 6.dp.toPx())
                )
            }
        }

        // Icon
        Text(
            text = "⚠️",
            fontSize = (if (isHolding) 17 else 20).sp
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Incoming SOS Overlay — full-screen dismissible alert
// ─────────────────────────────────────────────────────────────────────────
@Composable
private fun IncomingSosOverlay(message: BitchatMessage, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SOSRed.copy(alpha = 0.92f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🆘", fontSize = 80.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                "SOS RECEIVED",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color.White
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "@${message.sender}",
                fontSize = 18.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.White.copy(alpha = 0.9f)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                message.content,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            Spacer(Modifier.height(32.dp))
            Text(
                "Tap anywhere to dismiss",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Resources Tab — static resource list
// ─────────────────────────────────────────────────────────────────────────
@Composable
fun ResourcesTab() {
    data class ResourceItem(val emoji: String, val name: String, val desc: String, val color: Color)

    val resources = listOf(
        ResourceItem("🏥", "Nearest Hospital", "Use the map to locate the nearest medical facility.", Color(0xFF30D158)),
        ResourceItem("🚒", "Fire & Rescue", "Dial 101 – Fire Emergency Services", SOSRed),
        ResourceItem("🚑", "Ambulance", "Dial 108 – National Emergency Medical Services", UrgentOrange),
        ResourceItem("👮", "Police", "Dial 100 – Police Emergency Services", Color(0xFF3A8FFF)),
        ResourceItem("🏕️", "Relief Camps", "Broadcast resource updates via the Feed to coordinate.", SafeGreen),
        ResourceItem("💧", "Water/Food", "Send a RESOURCE_UPDATE message to share your needs.", Color(0xFF64D2FF)),
        ResourceItem("🔋", "Power Station", "Mark on the map and broadcast location.", UrgentOrange),
        ResourceItem("📡", "Signal Booster", "Stay within 30m BLE range to relay messages.", InfoGray)
    )

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize().background(DarkBg)
    ) {
        item {
            Text(
                "Emergency Resources",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = SOSRed,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        items(resources) { item ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = BorderStroke(0.5.dp, item.color.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(item.emoji, fontSize = 28.sp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            item.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            color = item.color
                        )
                        Text(
                            item.desc,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF888888),
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Status Tab — "I'm Safe" / "Need Help" quick-send
// ─────────────────────────────────────────────────────────────────────────
@Composable
fun StatusTab(viewModel: ChatViewModel, profile: UserProfile) {
    val context = LocalContext.current
    var lastAction by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("📡 Broadcast Your Status", fontSize = 18.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace, color = SOSRed)
        Spacer(Modifier.height(8.dp))
        Text("Let nearby nodes know you're okay or need help.",
            fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF666666),
            modifier = Modifier.padding(horizontal = 16.dp))

        Spacer(Modifier.height(40.dp))

        // I'm Safe button
        Button(
            onClick = {
                val name = if (profile.fullName.isBlank()) viewModel.myNickname else profile.fullName
                val geo = getLastLocation(context)
                val msg = BitchatMessage(
                    sender = viewModel.myNickname,
                    content = "✅ SAFE STATUS: @${viewModel.myNickname} (${name}) is SAFE." +
                        (if (geo != null) "\n📍 ${String.format("%.4f", geo.latitude)}, ${String.format("%.4f", geo.longitude)}" else ""),
                    timestamp = Date(),
                    emergencyType = EmergencyMessageType.SAFE_STATUS,
                    priorityLevel = PriorityLevel.URGENT,
                    geoLocation = geo
                )
                viewModel.sendEmergencyMessage(msg)
                lastAction = "safe"
            },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SafeGreen)
        ) {
            Text("🟢  I'm SAFE", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace, color = Color.Black)
        }

        Spacer(Modifier.height(20.dp))

        // Need Help button
        Button(
            onClick = {
                val geo = getLastLocation(context)
                val battPct = getBatteryPercent(context)
                val msg = SosManager.buildSosMessage(viewModel.myNickname, geo, battPct)
                viewModel.sendEmergencyMessage(msg)
                SosManager.triggerSosHaptic(context)
                lastAction = "sos"
            },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SOSRed)
        ) {
            Text("🔴  NEED HELP / SOS", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace, color = Color.White)
        }

        // Confirmation feedback
        lastAction?.let { action ->
            Spacer(Modifier.height(24.dp))
            AnimatedVisibility(visible = true, enter = fadeIn()) {
                Text(
                    text = if (action == "safe") "✅ Safe status broadcast!" else "🆘 SOS broadcast!",
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (action == "safe") SafeGreen else SOSRed
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────

private fun getBatteryPercent(context: Context): Int {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    return if (level >= 0 && scale > 0) ((level.toFloat() / scale.toFloat()) * 100).toInt() else -1
}

private fun getLastLocation(context: Context): GeoLocation? {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        != PackageManager.PERMISSION_GRANTED) return null
    return try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
        val providers = lm?.getProviders(true) ?: return null
        var best: Location? = null
        for (provider in providers) {
            val loc = lm.getLastKnownLocation(provider) ?: continue
            if (best == null || loc.accuracy < best.accuracy) best = loc
        }
        best?.let { GeoLocation(it.latitude, it.longitude) }
    } catch (_: Exception) { null }
}
