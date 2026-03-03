package com.saferelay.android.ui

import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saferelay.android.model.SafeRelayMessage
import com.saferelay.android.model.EmergencyMessageType
import com.saferelay.android.model.PriorityLevel

// ─────────────────────────────────────────────────────────────────────────
// Disaster Map Tab (embedded in main screen)
// ─────────────────────────────────────────────────────────────────────────
@Composable
fun DisasterMapTab(messages: List<SafeRelayMessage>) {
    val context = LocalContext.current
    val sosMessages = remember(messages) {
        messages.filter {
            it.emergencyType == EmergencyMessageType.SOS || it.priorityLevel == PriorityLevel.CRITICAL
        }
    }
    var mapError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF050505))) {
        if (mapError) {
            // Fallback: simple SOS list when no internet
            OfflineMapFallback(sosMessages = sosMessages)
        } else {
            LeafletMapView(
                sosMessages = sosMessages,
                modifier = Modifier.fillMaxSize(),
                onError = { mapError = true },
                onLoaded = { isLoading = false }
            )
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize().background(Color(0xFF050505)),
                    contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = SOSRed, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Loading map…", fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace, color = Color(0xFF555555))
                    }
                }
            }
        }

        // SOS count badge (top-end)
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

        // Offline indicator
        if (mapError) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .background(Color(0xFF2A2A2A), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.WifiOff, contentDescription = null,
                        tint = Color(0xFF888888), modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Offline – SOS list mode", fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace, color = Color(0xFF888888))
                }
            }
        }
    }
}

// Offline fallback: shows SOS list when maps can't load
@Composable
private fun OfflineMapFallback(sosMessages: List<SafeRelayMessage>) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF050505))
            .padding(16.dp)
    ) {
        Text("🗺️ SOS Location Reports", fontSize = 15.sp, fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace, color = SOSRed)
        Spacer(Modifier.height(4.dp))
        Text("Map requires internet. Showing mesh SOS reports.",
            fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF555555))
        Spacer(Modifier.height(12.dp))

        if (sosMessages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✅", fontSize = 40.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("No SOS reports on mesh", fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace, color = Color(0xFF555555))
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(sosMessages) { msg ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A0000)),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, SOSRed.copy(alpha = 0.5f))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("🆘", fontSize = 24.sp)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("@${msg.sender}", fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace, color = SOSRed)
                                msg.geoLocation?.let { geo ->
                                    Text(
                                        "📍 ${String.format("%.5f", geo.latitude)}, ${String.format("%.5f", geo.longitude)}",
                                        fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MeshBlue
                                    )
                                }
                                Text(msg.content.take(80), fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace, color = Color(0xFF999999))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Disaster Map Sheet (full-screen from header button)
// ─────────────────────────────────────────────────────────────────────────
@Composable
fun DisasterMapSheet(messages: List<SafeRelayMessage>, onDismiss: () -> Unit) {
    val sosMessages = remember(messages) {
        messages.filter { it.emergencyType == EmergencyMessageType.SOS || it.priorityLevel == PriorityLevel.CRITICAL }
    }
    var mapError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

            if (mapError) {
                OfflineMapFallback(sosMessages = sosMessages)
            } else {
                LeafletMapView(
                    sosMessages = sosMessages,
                    modifier = Modifier.fillMaxSize(),
                    onError = { mapError = true },
                    onLoaded = {}
                )
            }

            // Top overlay bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(12.dp)
                    .align(Alignment.TopStart),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button
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
                Spacer(Modifier.weight(1f))
                if (sosMessages.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .background(SOSRed.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text("🚨 ${sosMessages.size} SOS", fontSize = 12.sp,
                            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = Color.White)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Leaflet WebView – with error detection
// ─────────────────────────────────────────────────────────────────────────
@Composable
fun LeafletMapView(
    sosMessages: List<SafeRelayMessage>,
    modifier: Modifier = Modifier,
    onError: () -> Unit,
    onLoaded: () -> Unit
) {
    val html = buildLeafletHtml(sosMessages)
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    cacheMode = WebSettings.LOAD_DEFAULT
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        onLoaded()
                    }
                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        if (request?.isForMainFrame == true) onError()
                    }
                }
                loadDataWithBaseURL("https://openstreetmap.org", html, "text/html", "UTF-8", null)
            }
        },
        update = { wv ->
            wv.loadDataWithBaseURL("https://openstreetmap.org", html, "text/html", "UTF-8", null)
        },
        modifier = modifier
    )
}

private fun buildLeafletHtml(sosMessages: List<SafeRelayMessage>): String {
    val markersJs = buildString {
        sosMessages.forEachIndexed { i, msg ->
            // Use real GPS if available, scatter across India if not
            val lat = msg.geoLocation?.latitude ?: (20.59 + (i % 5) * 0.8)
            val lon = msg.geoLocation?.longitude ?: (78.96 + (i % 5) * 0.8)
            val title = "${msg.emergencyType.emoji} @${msg.sender}".replace("'", "\\'")
            val body = msg.content.take(100).replace("'", "\\'").replace("\n", " ")
            append("""
                L.marker([$lat,$lon],{icon:si})
                  .addTo(map)
                  .bindPopup('<b>$title</b><p style="margin:4px 0">$body</p>');
            """.trimIndent())
            append("\n")
        }
    }

    // Default center: India
    val centerLat = sosMessages.firstOrNull()?.geoLocation?.latitude ?: 20.5937
    val centerLon = sosMessages.firstOrNull()?.geoLocation?.longitude ?: 78.9629
    val zoom = if (sosMessages.any { it.geoLocation != null }) 8 else 5

    return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"
  integrity="sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY=" crossorigin=""/>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"
  integrity="sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV/XN/WLs=" crossorigin=""></script>
<style>
  *{margin:0;padding:0;box-sizing:border-box}
  html,body,#map{height:100%;width:100%;background:#000}
</style>
</head>
<body>
<div id="map"></div>
<script>
var map = L.map('map',{center:[$centerLat,$centerLon],zoom:$zoom,zoomControl:true});
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{
  attribution:'© OpenStreetMap',
  maxZoom:19,
  subdomains:['a','b','c']
}).addTo(map);

var si = L.divIcon({
  html:'<div style="font-size:26px;filter:drop-shadow(0 0 6px red);line-height:1">🆘</div>',
  iconSize:[32,32],iconAnchor:[16,16],popupAnchor:[0,-18],className:''
});

$markersJs
</script>
</body>
</html>
""".trimIndent()
}
