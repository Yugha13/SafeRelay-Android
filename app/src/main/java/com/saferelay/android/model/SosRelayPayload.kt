package com.saferelay.android.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Compact binary payload for SOS store-and-forward relay.
 *
 * Each SOS has a unique [sosId] for network-wide deduplication.
 * The [hopCount] is incremented each time a device relays the payload,
 * and capped at [com.saferelay.android.util.AppConstants.SosRelay.MAX_RELAY_HOPS].
 *
 * Wire format (big-endian):
 *   sosId length (1 byte) + sosId (UTF-8)
 *   senderNickname length (1 byte) + senderNickname (UTF-8)
 *   senderPeerID length (1 byte) + senderPeerID (UTF-8)
 *   latitude (8 bytes double)
 *   longitude (8 bytes double)
 *   batteryLevel (1 byte)
 *   timestampMs (8 bytes long)
 *   hopCount (1 byte)
 */
@Serializable
data class SosRelayPayload(
    @SerialName("sos_id")
    val sosId: String = UUID.randomUUID().toString().uppercase(),

    @SerialName("sender_nickname")
    val senderNickname: String,

    @SerialName("sender_peer_id")
    val senderPeerID: String,

    @SerialName("trigger_latitude")
    val latitude: Double,

    @SerialName("trigger_longitude")
    val longitude: Double,

    @SerialName("trigger_battery")
    val batteryLevel: Int,

    @Serializable(with = LongDateSerializer::class)
    @SerialName("triggered_at")
    val timestampMs: Long = System.currentTimeMillis(),

    @kotlinx.serialization.Transient
    @SerialName("hop_count")
    val hopCount: Int = 0
) {

    /**
     * Encode to compact binary for BLE transmission.
     */
    fun encode(): ByteArray {
        val buffer = ByteBuffer.allocate(512).apply { order(ByteOrder.BIG_ENDIAN) }

        val sosIdBytes = sosId.toByteArray(Charsets.UTF_8)
        buffer.put(sosIdBytes.size.coerceAtMost(255).toByte())
        buffer.put(sosIdBytes.take(255).toByteArray())

        val nickBytes = senderNickname.toByteArray(Charsets.UTF_8)
        buffer.put(nickBytes.size.coerceAtMost(255).toByte())
        buffer.put(nickBytes.take(255).toByteArray())

        val peerIdBytes = senderPeerID.toByteArray(Charsets.UTF_8)
        buffer.put(peerIdBytes.size.coerceAtMost(255).toByte())
        buffer.put(peerIdBytes.take(255).toByteArray())

        buffer.putDouble(latitude)
        buffer.putDouble(longitude)
        buffer.put(batteryLevel.coerceIn(0, 255).toByte())
        buffer.putLong(timestampMs)
        buffer.put(hopCount.coerceIn(0, 255).toByte())

        val result = ByteArray(buffer.position())
        buffer.rewind()
        buffer.get(result)
        return result
    }

    companion object {
        /**
         * Decode from compact binary.
         */
        fun decode(data: ByteArray): SosRelayPayload? {
            return try {
                if (data.size < 20) return null
                val buffer = ByteBuffer.wrap(data).apply { order(ByteOrder.BIG_ENDIAN) }

                val sosIdLen = buffer.get().toInt() and 0xFF
                if (buffer.remaining() < sosIdLen) return null
                val sosIdBytes = ByteArray(sosIdLen)
                buffer.get(sosIdBytes)
                val sosId = String(sosIdBytes, Charsets.UTF_8)

                val nickLen = buffer.get().toInt() and 0xFF
                if (buffer.remaining() < nickLen) return null
                val nickBytes = ByteArray(nickLen)
                buffer.get(nickBytes)
                val nick = String(nickBytes, Charsets.UTF_8)

                val peerIdLen = buffer.get().toInt() and 0xFF
                if (buffer.remaining() < peerIdLen) return null
                val peerIdBytes = ByteArray(peerIdLen)
                buffer.get(peerIdBytes)
                val peerId = String(peerIdBytes, Charsets.UTF_8)

                if (buffer.remaining() < 26) return null // 8+8+1+8+1

                val lat = buffer.getDouble()
                val lon = buffer.getDouble()
                val battery = buffer.get().toInt() and 0xFF
                val timestamp = buffer.getLong()
                val hops = buffer.get().toInt() and 0xFF

                SosRelayPayload(
                    sosId = sosId,
                    senderNickname = nick,
                    senderPeerID = peerId,
                    latitude = lat,
                    longitude = lon,
                    batteryLevel = battery,
                    timestampMs = timestamp,
                    hopCount = hops
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

object LongDateSerializer : kotlinx.serialization.KSerializer<Long> {
    override val descriptor: kotlinx.serialization.descriptors.SerialDescriptor =
        kotlinx.serialization.descriptors.PrimitiveSerialDescriptor("LongDate", kotlinx.serialization.descriptors.PrimitiveKind.STRING)

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Long) {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.US)
        format.timeZone = java.util.TimeZone.getTimeZone("UTC")
        encoder.encodeString(format.format(java.util.Date(value)))
    }

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): Long {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", java.util.Locale.US)
        return try {
            format.parse(decoder.decodeString())?.time ?: System.currentTimeMillis()
        } catch(e: Exception) {
            System.currentTimeMillis()
        }
    }
}
