package com.saferelay.android.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.saferelay.android.model.EmergencyMessageType
import com.saferelay.android.model.GeoLocation
import com.saferelay.android.model.PriorityLevel
import com.saferelay.android.model.SafeRelayMessage
import java.util.Date
import java.util.UUID

/**
 * SOS Manager – mirrors iOS SafeRelay SOS send logic.
 *
 * Triggered when user holds the ⚠️ SOS button for 3 seconds.
 * Attaches GPS location, battery %, and triggers haptic + alert sound.
 */
object SosManager {
    const val SOS_COOLDOWN_MS = 60000L // 60 seconds

    private var alertPlayer: MediaPlayer? = null

    /**
     * Build an SOS message ready to send over the mesh.
     * @param senderNickname  The user's nickname
     * @param location        Last known GPS fix (may be null if unavailable)
     * @param batteryPercent  Current battery level 0–100
     */
    fun buildSosMessage(
        senderNickname: String,
        location: GeoLocation?,
        batteryPercent: Int
    ): SafeRelayMessage {
        val locationPart = if (location != null) {
            "\n📍 GPS: ${location.latitude}, ${location.longitude}"
        } else ""

        val content = "🆘 SOS EMERGENCY\nSender: @$senderNickname\nBattery: $batteryPercent%$locationPart\nPlease respond immediately!"

        return SafeRelayMessage(
            id = UUID.randomUUID().toString().uppercase(),
            sender = senderNickname,
            content = content,
            timestamp = Date(),
            isRelay = false,
            emergencyType = EmergencyMessageType.SOS,
            priorityLevel = PriorityLevel.CRITICAL,
            geoLocation = location,
            batteryLevel = batteryPercent
        )
    }

    /**
     * Trigger haptic feedback for SOS send confirmation.
     * Matches iOS UINotificationFeedbackGenerator.notificationOccurred(.success).
     */
    fun triggerSosHaptic(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val v = vm.defaultVibrator
                // Three sharp pulses = success confirmation
                v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 80, 150, 80, 200), -1))
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                v?.vibrate(longArrayOf(0, 100, 80, 150, 80, 200), -1)
            }
        } catch (_: Exception) {}
    }

    /**
     * Trigger haptic feedback for SOS INCOMING alert.
     * Continuous pulsing to draw attention.
     */
    fun triggerIncomingSosHaptic(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 300, 200, 300, 200, 300), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                v?.vibrate(longArrayOf(0, 300, 200, 300, 200, 300), -1)
            }
        } catch (_: Exception) {}
    }

    /**
     * Whether this SafeRelayMessage is a SafeRelay emergency message (SOS or higher priority).
     */
    fun SafeRelayMessage.isSosAlert(): Boolean =
        emergencyType == EmergencyMessageType.SOS && priorityLevel == PriorityLevel.CRITICAL

    /**
     * Build a compact SOS relay payload for mesh store-and-forward.
     */
    fun buildSosRelayPayload(
        senderNickname: String,
        senderPeerID: String,
        location: GeoLocation?,
        batteryPercent: Int
    ): com.saferelay.android.model.SosRelayPayload {
        return com.saferelay.android.model.SosRelayPayload(
            senderNickname = senderNickname,
            senderPeerID = senderPeerID,
            latitude = location?.latitude ?: 0.0,
            longitude = location?.longitude ?: 0.0,
            batteryLevel = batteryPercent
        )
    }
}
