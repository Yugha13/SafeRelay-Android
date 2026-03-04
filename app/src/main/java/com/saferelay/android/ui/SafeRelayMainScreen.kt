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
import androidx.core.content.ContextCompat
import com.saferelay.android.model.*
import com.saferelay.android.ui.SosManager.isSosAlert
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

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
    STATUS("Home", Icons.Filled.Home),
    CHAT("Chat", Icons.Filled.Chat),
    MAP("Map", Icons.Filled.Map),
    NEARBY("Nearby", Icons.Filled.Wifi),
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

    var selectedTab by remember { mutableStateOf(SafeRelayTab.STATUS) }
    var showDisasterMap by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var showIncomingSosAlert by remember { mutableStateOf<SafeRelayMessage?>(null) }

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
        color = DarkBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()   // ← fixes top spacing gap
        ) {
            // ── Header ─────────────────────────────────────────────────
            SafeRelayHeader(
                profile = profile,
                connectedPeerCount = connectedPeers.size,
                onMapClick = { showDisasterMap = true },
                onProfileClick = { showProfile = true },
                onBrandClick = onOpenMeshChat
            )

            HorizontalDivider(color = SOSRed.copy(alpha = 0.25f), thickness = 0.5.dp)

            // ── Body ───────────────────────────────────────────────────
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    SafeRelayTab.STATUS -> StatusTab(viewModel = viewModel, profile = profile)
                    SafeRelayTab.CHAT   -> ChatScreen(viewModel = viewModel)
                    SafeRelayTab.MAP    -> DisasterMapTab(
                        messages = messages,
                        peerNicknames = peerNicknames,
                        onOpenChat = onOpenPrivateChat
                    )
                    SafeRelayTab.NEARBY -> NearbyDevicesTab(
                        connectedPeers = connectedPeers,
                        peerNicknames = peerNicknames,
                        peerRSSI = viewModel.peerRSSI.collectAsState(emptyMap()).value,
                        peerDirect = viewModel.peerDirect.collectAsState(emptyMap()).value,
                        onStartChat = { peer -> 
                            val nick = peerNicknames[peer] ?: peer
                            viewModel.startPrivateChat(peer)
                            onOpenPrivateChat(peer, nick)
                        },
                        onScan = { viewModel.meshService.sendBroadcastAnnounce() }
                    )
                }
            }

            HorizontalDivider(color = SOSRed.copy(alpha = 0.25f), thickness = 0.5.dp)

            // ── Bottom Navigation ──────────────────────────────────────
            NavigationBar(
                containerColor = Color(0xFF0A0A0A),
                tonalElevation = 0.dp,
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
                                tint = if (selected) SOSRed else Color(0xFF555555),
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                tab.label,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = if (selected) SOSRed else Color(0xFF555555)
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = SOSRed.copy(alpha = 0.15f)
                        )
                    )
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
            peerNicknames = peerNicknames,
            onOpenChat = { pid, nick ->
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
        Text(
            text = "SafeRelay/",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            color = SOSRed,
            modifier = Modifier.clickable { onBrandClick() }
        )

        Spacer(Modifier.width(8.dp))

        // Profile avatar
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(SOSRed.copy(alpha = 0.2f))
                .border(1.dp, SOSRed.copy(alpha = 0.5f), CircleShape)
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

        IconButton(onClick = onMapClick) {
            Icon(Icons.Filled.Map, contentDescription = "Map", tint = SOSRed, modifier = Modifier.size(22.dp))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Wifi,
                contentDescription = "Nodes",
                tint = if (connectedPeerCount > 0) MeshBlue else Color(0xFF555555),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(3.dp))
            Text(
                text = "$connectedPeerCount",
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = if (connectedPeerCount > 0) MeshBlue else Color(0xFF555555)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// STATUS TAB – home screen with "I'm Safe" / SOS broadcast
// ─────────────────────────────────────────────────────────────────────────
@Composable
fun StatusTab(viewModel: ChatViewModel, profile: UserProfile) {
    val context = LocalContext.current
    var lastAction by remember { mutableStateOf<String?>(null) }
    var lastActionTime by remember { mutableStateOf(0L) }

    LaunchedEffect(lastAction) {
        if (lastAction != null) {
            delay(4000)
            lastAction = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App brand
        Text(
            "🛡️",
            fontSize = 52.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "SafeRelay",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = SOSRed
        )
        Text(
            "Decentralized Disaster Communication",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF666666),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        // Profile greeting card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F0F)),
            border = BorderStroke(0.5.dp, SOSRed.copy(alpha = 0.25f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(SOSRed.copy(alpha = 0.2f))
                        .border(1.5.dp, SOSRed.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (profile.fullName.isBlank()) "@" else profile.initials,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = SOSRed
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text = if (profile.fullName.isNotBlank()) profile.fullName else "@${viewModel.myNickname}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFEEEEEE)
                    )
                    if (profile.bloodGroup.isNotBlank()) {
                        Text(
                            text = "🩸 ${profile.bloodGroup}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF888888)
                        )
                    }
                    if (profile.city.isNotBlank()) {
                        Text(
                            text = "📍 ${profile.city}${if (profile.state.isNotBlank()) ", ${profile.state}" else ""}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF888888)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        // ── I'm Safe ──
        Button(
            onClick = {
                val name = if (profile.fullName.isBlank()) viewModel.myNickname else profile.fullName
                val geo = getLastLocation(context)
                val msg = SafeRelayMessage(
                    sender = viewModel.myNickname,
                    content = "✅ SAFE: @${viewModel.myNickname} ($name) is SAFE." +
                        (geo?.let { "\n📍 ${String.format("%.4f", it.latitude)}, ${String.format("%.4f", it.longitude)}" } ?: ""),
                    timestamp = Date(),
                    emergencyType = EmergencyMessageType.SAFE_STATUS,
                    priorityLevel = PriorityLevel.URGENT,
                    geoLocation = geo
                )
                viewModel.sendEmergencyMessage(msg)
                lastAction = "safe"
            },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SafeGreen)
        ) {
            Text("✅  I'm SAFE", fontSize = 19.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace, color = Color.Black)
        }

        Spacer(Modifier.height(14.dp))

        // ── SOS ──
        SosHoldButtonLarge(
            onSosTriggered = {
                val geo = getLastLocation(context)
                val bat = getBatteryPercent(context)
                val sos = SosManager.buildSosMessage(viewModel.myNickname, geo, bat)
                viewModel.sendEmergencyMessage(sos)
                SosManager.triggerSosHaptic(context)
                lastAction = "sos"
            }
        )

        // Feedback
        AnimatedVisibility(
            visible = lastAction != null,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut()
        ) {
            Spacer(Modifier.height(16.dp))
        }
        if (lastAction != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (lastAction == "safe") "✅ Status broadcast to mesh network!" else "🆘 SOS broadcast! Stay calm, help is relaying.",
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = if (lastAction == "safe") SafeGreen else SOSRed,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        Spacer(Modifier.height(28.dp))

        // Info cards
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoCard(Modifier.weight(1f), "🚑", "108", "Ambulance", Color(0xFFFF9500))
            InfoCard(Modifier.weight(1f), "🚒", "101", "Fire", SOSRed)
            InfoCard(Modifier.weight(1f), "👮", "100", "Police", MeshBlue)
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
