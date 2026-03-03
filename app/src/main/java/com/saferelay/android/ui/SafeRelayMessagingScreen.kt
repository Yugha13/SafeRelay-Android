package com.saferelay.android.ui

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saferelay.android.model.SafeRelayMessage
import com.saferelay.android.model.EmergencyMessageType
import com.saferelay.android.model.PriorityLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * SafeRelay Messaging Screen
 * - Own messages: right-aligned bubble (red)
 * - Others: left-aligned bubble (dark card)
 * - Input bar rises above keyboard (imePadding)
 * - Recording / Image / Camera icons in input bar
 * - SOS button in input bar (hold 3s)
 * - Profile shown above messages
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafeRelayMessagingScreen(
    viewModel: ChatViewModel,
    peerNickname: String? = null,   // null = global mesh chat
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val myNickname = viewModel.myNickname
    val messages by viewModel.messages.collectAsState(emptyList())

    // Input state
    var messageText by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableStateOf(0) }
    var isHoldingSos by remember { mutableStateOf(false) }
    var sosProgress by remember { mutableStateOf(0f) }

    // Recording timer
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0
            while (isRecording) {
                delay(1000)
                recordingSeconds++
            }
        }
    }

    // Image picker
    var pickedImageUri by remember { mutableStateOf<Uri?>(null) }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> pickedImageUri = uri }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bmp -> /* handle bitmap */ }
    val micPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) isRecording = true }

    val listState = rememberLazyListState()

    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Surface(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        color = DarkBg
    ) {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {

            // ── Top bar ────────────────────────────────────────────────
            MessagingTopBar(
                title = peerNickname ?: "Mesh Chat",
                subtitle = if (peerNickname != null) "Private · end-to-end encrypted" else "Global mesh broadcast",
                onBack = onBack
            )

            HorizontalDivider(color = SOSRed.copy(alpha = 0.2f), thickness = 0.5.dp)

            // ── Messages ───────────────────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    val isOwn = msg.sender == myNickname || msg.sender == "me"
                    MessageBubble(message = msg, isOwn = isOwn)
                }
            }

            // ── Recording indicator ────────────────────────────────────
            AnimatedVisibility(visible = isRecording) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A0000))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(8.dp).background(SOSRed, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Recording… ${recordingSeconds}s",
                        fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = SOSRed
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { isRecording = false; recordingSeconds = 0 }) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel",
                            tint = Color(0xFF888888), modifier = Modifier.size(18.dp))
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF1A1A1A))

            // ── Input bar ──────────────────────────────────────────────
            MessagingInputBar(
                text = messageText,
                onTextChange = { messageText = it },
                onSend = {
                    if (messageText.isNotBlank()) {
                        viewModel.sendMessage(messageText)
                        messageText = ""
                    }
                },
                isRecording = isRecording,
                onRecordStart = {
                    micPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                onRecordStop = {
                    isRecording = false
                    // In a real impl, viewModel.sendVoiceNote(...)
                },
                onGallery = { galleryLauncher.launch("image/*") },
                onCamera = { cameraLauncher.launch(null) },
                isHoldingSos = isHoldingSos,
                sosProgress = sosProgress,
                onSosHoldStart = {
                    isHoldingSos = true
                    scope.launch {
                        val start = System.currentTimeMillis()
                        while (isHoldingSos) {
                            val elapsed = System.currentTimeMillis() - start
                            sosProgress = (elapsed / 3000f).coerceIn(0f, 1f)
                            if (sosProgress >= 1f) {
                                val geo = getLastLocation(context)
                                val bat = getBatteryPercent(context)
                                val sos = SosManager.buildSosMessage(viewModel.myNickname, geo, bat)
                                viewModel.sendEmergencyMessage(sos)
                                SosManager.triggerSosHaptic(context)
                                isHoldingSos = false
                                sosProgress = 0f
                                break
                            }
                            delay(40)
                        }
                    }
                },
                onSosHoldEnd = { isHoldingSos = false; sosProgress = 0f }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Top bar
// ─────────────────────────────────────────────────────────────────────────
@Composable
private fun MessagingTopBar(title: String, subtitle: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Color(0xFF080808))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back",
                tint = SOSRed, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace, color = Color(0xFFEEEEEE))
            Text(subtitle, fontSize = 10.sp,
                fontFamily = FontFamily.Monospace, color = Color(0xFF555555))
        }
        // Peer profile indicator
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape)
                .background(SOSRed.copy(alpha = 0.15f))
                .border(1.dp, SOSRed.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(title.take(1).uppercase(), fontSize = 13.sp,
                fontWeight = FontWeight.Bold, color = SOSRed)
        }
        Spacer(Modifier.width(8.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Chat bubble — own = right/red, others = left/dark
// ─────────────────────────────────────────────────────────────────────────
@Composable
private fun MessageBubble(message: SafeRelayMessage, isOwn: Boolean) {
    val isSos = message.emergencyType == EmergencyMessageType.SOS
    val bubbleColor = when {
        isSos -> Color(0xFF3D0000)
        isOwn -> Color(0xFF2A0A0A)     // Own: dark red tint
        else  -> Color(0xFF111111)     // Others: dark gray
    }
    val borderColor = when {
        isSos -> SOSRed.copy(alpha = 0.7f)
        isOwn -> SOSRed.copy(alpha = 0.4f)
        else  -> Color(0xFF2A2A2A)
    }
    val textColor = if (isSos) SOSRed else Color(0xFFDDDDDD)
    val alignment = if (isOwn) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalAlignment = alignment
    ) {
        // Sender name (only for others in group chat)
        if (!isOwn) {
            Text(
                "@${message.sender}",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF555555),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 1.dp)
            )
        }

        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            // Avatar for others
            if (!isOwn) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1A1A1A))
                        .border(0.5.dp, Color(0xFF2A2A2A), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(message.sender.take(1).uppercase(), fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, color = Color(0xFF888888))
                }
                Spacer(Modifier.width(6.dp))
            }

            // Bubble
            Box(
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp, topEnd = 16.dp,
                            bottomStart = if (isOwn) 16.dp else 4.dp,
                            bottomEnd = if (isOwn) 4.dp else 16.dp
                        )
                    )
                    .background(bubbleColor)
                    .border(
                        0.5.dp, borderColor,
                        RoundedCornerShape(
                            topStart = 16.dp, topEnd = 16.dp,
                            bottomStart = if (isOwn) 16.dp else 4.dp,
                            bottomEnd = if (isOwn) 4.dp else 16.dp
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column {
                    // Emergency badge
                    if (isSos || message.priorityLevel == PriorityLevel.CRITICAL) {
                        Text(
                            "${message.emergencyType.emoji} ${message.priorityLevel.label}",
                            fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                            color = SOSRed,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                    Text(
                        message.content,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        color = textColor,
                        lineHeight = 20.sp
                    )
                    // Geo
                    message.geoLocation?.let { geo ->
                        Spacer(Modifier.height(3.dp))
                        Text("📍 ${String.format("%.4f", geo.latitude)}, ${String.format("%.4f", geo.longitude)}",
                            fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = MeshBlue)
                    }
                    // Time + delivery
                    Row(
                        modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(message.timestamp),
                            fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF555555)
                        )
                        if (isOwn) {
                            Spacer(Modifier.width(4.dp))
                            Text("✓", fontSize = 9.sp, color = SOSRed.copy(alpha = 0.7f))
                        }
                    }
                }
            }

            // Avatar placeholder for own (right side)
            if (isOwn) {
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(SOSRed.copy(alpha = 0.2f))
                        .border(0.5.dp, SOSRed.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Me", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = SOSRed)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Input bar with recording, image, camera, SOS
// ─────────────────────────────────────────────────────────────────────────
@Composable
private fun MessagingInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isRecording: Boolean,
    onRecordStart: () -> Unit,
    onRecordStop: () -> Unit,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    isHoldingSos: Boolean,
    sosProgress: Float,
    onSosHoldStart: () -> Unit,
    onSosHoldEnd: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF080808))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ── Camera button ───────────────────────────────────────
            IconButton(onClick = onCamera, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.CameraAlt, contentDescription = "Camera",
                    tint = Color(0xFF555555), modifier = Modifier.size(22.dp))
            }

            // ── Gallery button ──────────────────────────────────────
            IconButton(onClick = onGallery, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Image, contentDescription = "Image",
                    tint = Color(0xFF555555), modifier = Modifier.size(22.dp))
            }

            // ── Text field ──────────────────────────────────────────
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = {
                    Text("Message…", color = Color(0xFF444444),
                        fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                },
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color(0xFF222222),
                    focusedBorderColor = SOSRed.copy(alpha = 0.45f),
                    cursorColor = SOSRed,
                    unfocusedTextColor = Color(0xFFCCCCCC),
                    focusedTextColor = Color.White,
                    unfocusedContainerColor = Color(0xFF111111),
                    focusedContainerColor = Color(0xFF111111)
                ),
                shape = RoundedCornerShape(24.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                singleLine = true,
                maxLines = 1
            )

            // ── Mic (hold to record) or SOS ────────────────────────
            if (text.isBlank()) {
                // Mic – hold to record
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    onRecordStart()
                                    tryAwaitRelease()
                                    if (isRecording) onRecordStop()
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = "Record",
                        tint = if (isRecording) SOSRed else Color(0xFF555555),
                        modifier = Modifier.size(22.dp)
                    )
                }

                // SOS hold mini-button
                SosHoldButton(
                    isHolding = isHoldingSos,
                    progress = sosProgress,
                    onHoldStart = onSosHoldStart,
                    onHoldEnd = onSosHoldEnd
                )
            } else {
                // Send button
                IconButton(
                    onClick = onSend,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SOSRed)
                ) {
                    Icon(Icons.Filled.Send, contentDescription = "Send",
                        tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
