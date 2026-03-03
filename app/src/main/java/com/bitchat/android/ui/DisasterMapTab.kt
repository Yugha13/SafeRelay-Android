package com.bitchat.android.ui

import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
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
import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.model.EmergencyMessageType
import com.bitchat.android.model.GeoLocation
import com.bitchat.android.model.PriorityLevel
import com.bitchat.android.ui.SosManager.isSosAlert

private val SOSRed = Color(0xFFFF3B30)
private val MapDarkBg = Color(0xFF000000)

// ─────────────────────────────────────────────────────────────────────────
// Disaster Map Tab (inside main screen)
// Uses a WebView loading Leaflet.js + OpenStreetMap – same approach as the
// iOS DisasterMapView (which uses MKMapView).
// ─────────────────────────────────────────────────────────────────────────
@Composable
fun DisasterMapTab(messages: List<BitchatMessage>) {
    val sosMessages = remember(messages) {
        messages.filter {
            it.emergencyType == EmergencyMessageType.SOS ||
            it.priorityLevel == PriorityLevel.CRITICAL
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MapDarkBg)) {
        LeafletMapView(sosMessages = sosMessages, modifier = Modifier.fillMaxSize())

        // SOS count badge (top-right) — mirrors iOS
        if (sosMessages.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .background(SOSRed.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    "🚨 ${sosMessages.size} SOS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Disaster Map Sheet (fullscreen – opened from header map button)
// ─────────────────────────────────────────────────────────────────────────
@Composable
fun DisasterMapSheet(messages: List<BitchatMessage>, onDismiss: () -> Unit) {
    val sosMessages = remember(messages) {
        messages.filter { it.emergencyType == EmergencyMessageType.SOS || it.priorityLevel == PriorityLevel.CRITICAL }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MapDarkBg)
        ) {
            LeafletMapView(sosMessages = sosMessages, modifier = Modifier.fillMaxSize())

            // Header overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .align(Alignment.TopStart),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("Disaster Map", fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace, color = Color.White)
                    Text("SOS reports from mesh network", fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace, color = Color.White.copy(alpha = 0.7f))
                }
                Spacer(Modifier.weight(1f))
                if (sosMessages.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .background(SOSRed.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text("🚨 ${sosMessages.size} SOS", fontSize = 12.sp,
                            fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                            color = Color.White)
                    }
                }
            }

            // SOS list (bottom portion)
            if (sosMessages.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.8f))
                        .padding(12.dp)
                ) {
                    Text("Active SOS Reports", fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                        color = SOSRed)
                    Spacer(Modifier.height(6.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 180.dp)) {
                        items(sosMessages.take(5)) { msg ->
                            Row(
                                modifier = Modifier.padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🆘", fontSize = 14.sp)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("@${msg.sender}", fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold, color = SOSRed)
                                    msg.geoLocation?.let { geo ->
                                        Text("📍 ${String.format("%.4f", geo.latitude)}, ${String.format("%.4f", geo.longitude)}",
                                            fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                                            color = Color(0xFF3A8FFF))
                                    }
                                }
                            }
                            HorizontalDivider(color = Color(0xFF2A2A2A), thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Leaflet.js WebView
// ─────────────────────────────────────────────────────────────────────────
@Composable
private fun LeafletMapView(
    sosMessages: List<BitchatMessage>,
    modifier: Modifier = Modifier
) {
    val html = buildLeafletHtml(sosMessages)
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                webViewClient = WebViewClient()
                loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        },
        modifier = modifier
    )
}

private fun buildLeafletHtml(sosMessages: List<BitchatMessage>): String {
    val markersJs = buildString {
        sosMessages.forEachIndexed { i, msg ->
            val lat = msg.geoLocation?.latitude ?: (20.5937 + (i * 0.1)) // scatter if no GPS
            val lon = msg.geoLocation?.longitude ?: (78.9629 + (i * 0.1))
            val title = "${msg.emergencyType.emoji} @${msg.sender}"
            val content = msg.content.take(80).replace("'", "\\'").replace("\n", " ")
            append("""
                var m$i = L.marker([$lat, $lon], {icon: sosIcon})
                    .addTo(map)
                    .bindPopup('<b>$title</b><br>${content}');
            """.trimIndent())
            append("\n")
        }
    }

    return """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  html, body, #map { height: 100%; width: 100%; background: #000; }
</style>
</head>
<body>
<div id="map"></div>
<script>
  var map = L.map('map', {
    center: [20.5937, 78.9629],
    zoom: 5,
    zoomControl: true
  });

  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    attribution: '© OpenStreetMap contributors',
    maxZoom: 18,
    subdomains: ['a','b','c']
  }).addTo(map);

  var sosIcon = L.divIcon({
    html: '<div style="font-size:24px;filter:drop-shadow(0 0 8px red)">🆘</div>',
    iconSize: [30, 30],
    iconAnchor: [15, 15],
    className: ''
  });

  $markersJs
</script>
</body>
</html>
    """.trimIndent()
}
