package com.saferelay.android.model

import org.junit.Assert.*
import org.junit.Test
import java.util.*

class SafeRelayMessageProtocolTest {

    @Test
    fun testV1ProtocolCompatibility() {
        val now = Date()
        val original = SafeRelayMessage(
            id = "TEST-ID-V1",
            sender = "Alice",
            content = "Hello Mesh!",
            timestamp = now,
            channel = "#general",
            senderPeerID = "1234567812345678"
        )

        val payload = original.toBinaryPayload()
        assertNotNull(payload)

        // Decode using fromBinaryPayload
        val decoded = SafeRelayMessage.fromBinaryPayload(payload!!)
        assertNotNull(decoded)
        
        assertEquals(original.id, decoded?.id)
        assertEquals(original.sender, decoded?.sender)
        assertEquals(original.content, decoded?.content)
        assertEquals(original.timestamp.time / 1000, decoded?.timestamp?.time?.div(1000)) // Ignore millisecond precision if needed, but here it should match
        assertEquals(original.channel, decoded?.channel)
        assertEquals(original.senderPeerID, decoded?.senderPeerID)
        assertEquals(EmergencyMessageType.NORMAL, decoded?.emergencyType)
    }

    @Test
    fun testV2ProtocolEmergencyMetadata() {
        val now = Date()
        val original = SafeRelayMessage(
            id = "TEST-ID-V2",
            sender = "Bob",
            content = "HELP! I need medical assistance.",
            timestamp = now,
            channel = "#emergency",
            emergencyType = EmergencyMessageType.MEDICAL_REQUEST,
            priorityLevel = PriorityLevel.CRITICAL,
            geoLocation = GeoLocation(37.7749, -122.4194)
        )

        val payload = original.toBinaryPayload()
        assertNotNull(payload)
        
        // Assert that v2 starts with 0x02
        assertEquals(0x02.toByte(), payload!![0])

        val decoded = SafeRelayMessage.fromBinaryPayload(payload!!)
        assertNotNull(decoded)

        assertEquals(original.id, decoded?.id)
        assertEquals(original.emergencyType, decoded?.emergencyType)
        assertEquals(PriorityLevel.CRITICAL, decoded?.priorityLevel)
        assertNotNull(decoded?.geoLocation)
        assertEquals(37.7749, decoded?.geoLocation?.latitude ?: 0.0, 0.0001)
        assertEquals(-122.4194, decoded?.geoLocation?.longitude ?: 0.0, 0.0001)
    }

    @Test
    fun testLegacyFallback() {
        val rawContent = "Legacy message"
        val payload = rawContent.toByteArray(Charsets.UTF_8)
        
        // Decoding a raw string as SafeRelayMessage should return null or we handle it in MessageHandler
        // Actually fromBinaryPayload returns null if it doesn't match format
        val decoded = SafeRelayMessage.fromBinaryPayload(payload)
        // Since "Legacy message" doesn't start with 0x02 or a valid flags byte followed by length prefix that matches, it should likely fail
        assertNull(decoded)
    }
}
