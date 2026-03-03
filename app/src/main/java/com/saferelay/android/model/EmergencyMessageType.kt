package com.saferelay.android.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * SafeRelay Emergency Message Types – matches iOS SafeRelay MessageType enum exactly.
 * Protocol wire values (rawValue) are preserved 1:1 for cross-platform compatibility.
 */
@Parcelize
enum class EmergencyMessageType(val rawValue: UByte, val emoji: String, val label: String) : Parcelable {
    NORMAL(0u, "💬", "Message"),
    SOS(1u, "🆘", "SOS"),
    BROADCAST(2u, "📢", "Broadcast"),
    RESOURCE_UPDATE(3u, "📦", "Resource Update"),
    MEDICAL_REQUEST(4u, "🏥", "Medical Request"),
    SAFE_STATUS(5u, "✅", "Safe");

    companion object {
        fun fromRawValue(value: UByte): EmergencyMessageType =
            values().firstOrNull { it.rawValue == value } ?: NORMAL
    }
}

/**
 * Priority levels – matches iOS SafeRelay PriorityLevel enum exactly.
 */
@Parcelize
enum class PriorityLevel(val rawValue: UByte, val label: String) : Parcelable {
    CRITICAL(1u, "CRITICAL"),
    URGENT(2u, "URGENT"),
    INFO(3u, "INFO");

    companion object {
        fun fromRawValue(value: UByte): PriorityLevel =
            values().firstOrNull { it.rawValue == value } ?: INFO
    }
}

/**
 * Geographic location attached to emergency messages.
 */
@Parcelize
data class GeoLocation(
    val latitude: Double,
    val longitude: Double
) : Parcelable {
    override fun toString(): String = "$latitude,$longitude"

    companion object {
        fun parse(s: String): GeoLocation? {
            val parts = s.split(",")
            if (parts.size != 2) return null
            return try {
                GeoLocation(parts[0].toDouble(), parts[1].toDouble())
            } catch (e: NumberFormatException) {
                null
            }
        }
    }
}
