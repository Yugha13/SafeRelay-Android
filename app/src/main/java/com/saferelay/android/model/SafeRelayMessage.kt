package com.saferelay.android.model

import android.os.Parcelable
import com.google.gson.GsonBuilder
import kotlinx.parcelize.Parcelize
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.Transient

@Serializable
@Parcelize
enum class SafeRelayMessageType : Parcelable {
    Message,
    Audio,
    Image,
    File
}

object DateSerializer : KSerializer<Date> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Date", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Date) {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
        encoder.encodeString(format.format(value))
    }
    override fun deserialize(decoder: Decoder): Date {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
        return format.parse(decoder.decodeString()) ?: Date()
    }
}

// Emergency type display helper
val EmergencyMessageType.displayLabel: String
    get() = when (this) {
        EmergencyMessageType.NORMAL -> "Message"
        EmergencyMessageType.SOS -> "🆘 SOS"
        EmergencyMessageType.BROADCAST -> "📢 Broadcast"
        EmergencyMessageType.RESOURCE_UPDATE -> "📦 Resource Update"
        EmergencyMessageType.MEDICAL_REQUEST -> "🏥 Medical Request"
        EmergencyMessageType.SAFE_STATUS -> "✅ Safe"
    }

/**
 * Delivery status for messages - exact same as iOS version
 */
sealed class DeliveryStatus : Parcelable {
    @Parcelize
    object Sending : DeliveryStatus()

    @Parcelize
    object Sent : DeliveryStatus()

    @Parcelize
    data class Delivered(val to: String, val at: Date) : DeliveryStatus()

    @Parcelize
    data class Read(val by: String, val at: Date) : DeliveryStatus()

    @Parcelize
    data class Failed(val reason: String) : DeliveryStatus()

    @Parcelize
    data class PartiallyDelivered(val reached: Int, val total: Int) : DeliveryStatus()

    fun getDisplayText(): String {
        return when (this) {
            is Sending -> "Sending..."
            is Sent -> "Sent"
            is Delivered -> "Delivered to ${this.to}"
            is Read -> "Read by ${this.by}"
            is Failed -> "Failed: ${this.reason}"
            is PartiallyDelivered -> "Delivered to ${this.reached}/${this.total}"
        }
    }
}

/**
 * SafeRelayMessage - 100% compatible with iOS version
 */
@Serializable
@Parcelize
data class SafeRelayMessage(
    val id: String = UUID.randomUUID().toString().uppercase(),
    
    @SerialName("sender_nickname")
    val sender: String,
    
    @SerialName("text")
    val content: String,
    
    @SerialName("message_type")
    val type: SafeRelayMessageType = SafeRelayMessageType.Message,
    
    @SerialName("triggered_at")
    @Serializable(with = DateSerializer::class)
    val timestamp: Date,
    
    @SerialName("is_relay")
    val isRelay: Boolean = false,
    
    @SerialName("original_sender")
    val originalSender: String? = null,
    
    @SerialName("is_private")
    val isPrivate: Boolean = false,
    
    @SerialName("recipient_nickname")
    val recipientNickname: String? = null,
    
    @SerialName("sender_peer_id")
    val senderPeerID: String? = null,
    
    val mentions: List<String>? = null,
    
    val channel: String? = null,
    
    @Transient // Not stored in Supabase
    val encryptedContent: ByteArray? = null,
    
    @SerialName("is_encrypted")
    val isEncrypted: Boolean = false,
    
    @Transient // Not stored in Supabase
    val deliveryStatus: DeliveryStatus? = null,
    
    @Transient // Not stored in Supabase
    val powDifficulty: Int? = null,
    
    // SafeRelay emergency fields (iOS-compatible)
    @SerialName("emergency_type")
    val emergencyType: EmergencyMessageType = EmergencyMessageType.NORMAL,
    
    @SerialName("priority_level")
    val priorityLevel: PriorityLevel = PriorityLevel.INFO,
    
    @Transient // Flattened for Supabase
    val geoLocation: GeoLocation? = null,
    
    @SerialName("is_verified")
    val isVerified: Boolean = true,
    
    // Supabase flattened location and extra fields
    @SerialName("trigger_latitude")
    val latitude: Double? = geoLocation?.latitude,
    
    @SerialName("trigger_longitude")
    val longitude: Double? = geoLocation?.longitude,
    
    @SerialName("trigger_battery")
    val batteryLevel: Int? = null,
    
    @SerialName("report_category")
    val reportCategory: String? = null
) : Parcelable {

    /**
     * Convert message to binary payload format - exactly same as iOS version
     * Supports v2 with emergency metadata
     */
    fun toBinaryPayload(): ByteArray? {
        try {
            val buffer = ByteBuffer.allocate(8192).apply { order(ByteOrder.BIG_ENDIAN) }

            // Message format v1:
            // - Flags: 1 byte
            // - Timestamp: 8 bytes
            // - ID: 1 byte length + data
            // - Sender: 1 byte length + data
            // - Content: 2 bytes length + data
            // - Optional: originalSender, recipientNickname, senderPeerID, mentions, channel
            
            // Message format v2 adds:
            // - Version: 1 byte (0x02) - inserted at the beginning or as a flag?
            // iOS implementation typically uses a version byte at the very start for breaking changes.
            // Let's use 0x02 as the first byte for v2, or keep it v1 if no extra fields.
            
            val isV2 = emergencyType != EmergencyMessageType.NORMAL || 
                       priorityLevel != PriorityLevel.INFO || 
                       geoLocation != null

            if (isV2) {
                buffer.put(0x02.toByte()) // Version prefix for v2
            }

            var flags: UByte = 0u
            if (isRelay) flags = flags or 0x01u
            if (isPrivate) flags = flags or 0x02u
            if (originalSender != null) flags = flags or 0x04u
            if (recipientNickname != null) flags = flags or 0x08u
            if (senderPeerID != null) flags = flags or 0x10u
            if (mentions != null && mentions.isNotEmpty()) flags = flags or 0x20u
            if (channel != null) flags = flags or 0x40u
            if (isEncrypted) flags = flags or 0x80u

            buffer.put(flags.toByte())

            // Timestamp (8 bytes)
            buffer.putLong(timestamp.time)

            // ID
            val idBytes = id.toByteArray(Charsets.UTF_8)
            buffer.put(minOf(idBytes.size, 255).toByte())
            buffer.put(idBytes.take(255).toByteArray())

            // Sender
            val senderBytes = sender.toByteArray(Charsets.UTF_8)
            buffer.put(minOf(senderBytes.size, 255).toByte())
            buffer.put(senderBytes.take(255).toByteArray())

            // Content
            if (isEncrypted && encryptedContent != null) {
                val length = minOf(encryptedContent.size, 65535)
                buffer.putShort(length.toShort())
                buffer.put(encryptedContent.take(length).toByteArray())
            } else {
                val contentBytes = content.toByteArray(Charsets.UTF_8)
                val length = minOf(contentBytes.size, 65535)
                buffer.putShort(length.toShort())
                buffer.put(contentBytes.take(length).toByteArray())
            }

            // Optional fields (v1)
            originalSender?.let { s -> 
                val b = s.toByteArray(); buffer.put(minOf(b.size, 255).toByte()); buffer.put(b.take(255).toByteArray()) 
            }
            recipientNickname?.let { s -> 
                val b = s.toByteArray(); buffer.put(minOf(b.size, 255).toByte()); buffer.put(b.take(255).toByteArray()) 
            }
            senderPeerID?.let { s -> 
                val b = s.toByteArray(); buffer.put(minOf(b.size, 255).toByte()); buffer.put(b.take(255).toByteArray()) 
            }
            
            mentions?.let { list ->
                buffer.put(minOf(list.size, 255).toByte())
                list.take(255).forEach { m ->
                    val b = m.toByteArray(); buffer.put(minOf(b.size, 255).toByte()); buffer.put(b.take(255).toByteArray())
                }
            }
            
            channel?.let { s -> 
                val b = s.toByteArray(); buffer.put(minOf(b.size, 255).toByte()); buffer.put(b.take(255).toByteArray()) 
            }

            // v2 specific fields
            if (isV2) {
                // Emergency Type (1 byte)
                buffer.put(emergencyType.rawValue.toByte())
                // Priority Level (1 byte)
                buffer.put(priorityLevel.rawValue.toByte())
                // GeoLocation (2 * 8 bytes)
                if (geoLocation != null) {
                    buffer.put(0x01.toByte()) // Has Location flag
                    buffer.putDouble(geoLocation.latitude)
                    buffer.putDouble(geoLocation.longitude)
                } else {
                    buffer.put(0x00.toByte()) // No Location flag
                }
            }

            val result = ByteArray(buffer.position())
            buffer.rewind()
            buffer.get(result)
            return result
        } catch (e: Exception) {
            return null
        }
    }

    companion object {
        /**
         * Parse message from binary payload - exactly same logic as iOS version
         */
        fun fromBinaryPayload(data: ByteArray): SafeRelayMessage? {
            try {
                if (data.size < 1) return null

                val buffer = ByteBuffer.wrap(data).apply { order(ByteOrder.BIG_ENDIAN) }
                
                // Detect version
                var version = 1
                buffer.mark()
                val firstByte = buffer.get().toInt() and 0xFF
                if (firstByte == 0x02) {
                    version = 2
                } else {
                    buffer.reset() // It's v1, first byte is flags
                }

                // Flags
                val flags = buffer.get().toUByte()
                val isRelay = (flags and 0x01u) != 0u.toUByte()
                val isPrivate = (flags and 0x02u) != 0u.toUByte()
                val hasOriginalSender = (flags and 0x04u) != 0u.toUByte()
                val hasRecipientNickname = (flags and 0x08u) != 0u.toUByte()
                val hasSenderPeerID = (flags and 0x10u) != 0u.toUByte()
                val hasMentions = (flags and 0x20u) != 0u.toUByte()
                val hasChannel = (flags and 0x40u) != 0u.toUByte()
                val isEncrypted = (flags and 0x80u) != 0u.toUByte()

                // Timestamp
                val timestampMillis = buffer.getLong()
                val timestamp = Date(timestampMillis)

                // ID
                val idLength = buffer.get().toInt() and 0xFF
                if (buffer.remaining() < idLength) return null
                val idBytes = ByteArray(idLength)
                buffer.get(idBytes)
                val id = String(idBytes, Charsets.UTF_8)

                // Sender
                val senderLength = buffer.get().toInt() and 0xFF
                if (buffer.remaining() < senderLength) return null
                val senderBytes = ByteArray(senderLength)
                buffer.get(senderBytes)
                val sender = String(senderBytes, Charsets.UTF_8)

                // Content
                val contentLength = buffer.getShort().toInt() and 0xFFFF
                if (buffer.remaining() < contentLength) return null

                val content: String
                val encryptedContent: ByteArray?

                if (isEncrypted) {
                    val encryptedBytes = ByteArray(contentLength)
                    buffer.get(encryptedBytes)
                    encryptedContent = encryptedBytes
                    content = "" // Empty placeholder
                } else {
                    val contentBytes = ByteArray(contentLength)
                    buffer.get(contentBytes)
                    content = String(contentBytes, Charsets.UTF_8)
                    encryptedContent = null
                }

                // Optional fields (v1)
                val originalSender = if (hasOriginalSender && buffer.hasRemaining()) {
                    val length = buffer.get().toInt() and 0xFF
                    if (buffer.remaining() >= length) {
                        val bytes = ByteArray(length)
                        buffer.get(bytes)
                        String(bytes, Charsets.UTF_8)
                    } else null
                } else null

                val recipientNickname = if (hasRecipientNickname && buffer.hasRemaining()) {
                    val length = buffer.get().toInt() and 0xFF
                    if (buffer.remaining() >= length) {
                        val bytes = ByteArray(length)
                        buffer.get(bytes)
                        String(bytes, Charsets.UTF_8)
                    } else null
                } else null

                val senderPeerID = if (hasSenderPeerID && buffer.hasRemaining()) {
                    val length = buffer.get().toInt() and 0xFF
                    if (buffer.remaining() >= length) {
                        val bytes = ByteArray(length)
                        buffer.get(bytes)
                        String(bytes, Charsets.UTF_8)
                    } else null
                } else null

                // Mentions array
                val mentions = if (hasMentions && buffer.hasRemaining()) {
                    val mentionCount = buffer.get().toInt() and 0xFF
                    val mentionList = mutableListOf<String>()
                    repeat(mentionCount) {
                        if (buffer.hasRemaining()) {
                            val length = buffer.get().toInt() and 0xFF
                            if (buffer.remaining() >= length) {
                                val bytes = ByteArray(length)
                                buffer.get(bytes)
                                mentionList.add(String(bytes, Charsets.UTF_8))
                            }
                        }
                    }
                    if (mentionList.isNotEmpty()) mentionList else null
                } else null

                // Channel
                val channel = if (hasChannel && buffer.hasRemaining()) {
                    val length = buffer.get().toInt() and 0xFF
                    if (buffer.remaining() >= length) {
                        val bytes = ByteArray(length)
                        buffer.get(bytes)
                        String(bytes, Charsets.UTF_8)
                    } else null
                } else null

                // v2 Fields
                var emergencyType = EmergencyMessageType.NORMAL
                var priorityLevel = PriorityLevel.INFO
                var geoLocation: GeoLocation? = null

                if (version == 2 && buffer.hasRemaining()) {
                    emergencyType = EmergencyMessageType.fromRawValue(buffer.get().toUByte())
                    priorityLevel = PriorityLevel.fromRawValue(buffer.get().toUByte())
                    
                    if (buffer.hasRemaining() && buffer.get().toInt() == 0x01) {
                        if (buffer.remaining() >= 16) {
                            val lat = buffer.getDouble()
                            val lon = buffer.getDouble()
                            geoLocation = GeoLocation(lat, lon)
                        }
                    }
                }

                return SafeRelayMessage(
                    id = id,
                    sender = sender,
                    content = content,
                    type = SafeRelayMessageType.Message,
                    timestamp = timestamp,
                    isRelay = isRelay,
                    originalSender = originalSender,
                    isPrivate = isPrivate,
                    recipientNickname = recipientNickname,
                    senderPeerID = senderPeerID,
                    mentions = mentions,
                    channel = channel,
                    encryptedContent = encryptedContent,
                    isEncrypted = isEncrypted,
                    isVerified = true,
                    emergencyType = emergencyType,
                    priorityLevel = priorityLevel,
                    geoLocation = geoLocation
                )

            } catch (e: Exception) {
                return null
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SafeRelayMessage

        if (id != other.id) return false
        if (sender != other.sender) return false
        if (content != other.content) return false
        if (type != other.type) return false
        if (timestamp != other.timestamp) return false
        if (isRelay != other.isRelay) return false
        if (originalSender != other.originalSender) return false
        if (isPrivate != other.isPrivate) return false
        if (recipientNickname != other.recipientNickname) return false
        if (senderPeerID != other.senderPeerID) return false
        if (mentions != other.mentions) return false
        if (channel != other.channel) return false
        if (encryptedContent != null) {
            if (other.encryptedContent == null) return false
            if (!encryptedContent.contentEquals(other.encryptedContent)) return false
        } else if (other.encryptedContent != null) return false
        if (isEncrypted != other.isEncrypted) return false
        if (deliveryStatus != other.deliveryStatus) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + sender.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + isRelay.hashCode()
        result = 31 * result + (originalSender?.hashCode() ?: 0)
        result = 31 * result + isPrivate.hashCode()
        result = 31 * result + (recipientNickname?.hashCode() ?: 0)
        result = 31 * result + (senderPeerID?.hashCode() ?: 0)
        result = 31 * result + (mentions?.hashCode() ?: 0)
        result = 31 * result + (channel?.hashCode() ?: 0)
        result = 31 * result + (encryptedContent?.contentHashCode() ?: 0)
        result = 31 * result + isEncrypted.hashCode()
        result = 31 * result + (deliveryStatus?.hashCode() ?: 0)
        return result
    }
}


