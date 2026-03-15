package com.saferelay.android.mesh

import com.saferelay.android.model.SosRelayPayload
import com.saferelay.android.protocol.SafeRelayPacket
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SosRelayManager: dedup, hop limit, queue cap, expiry, and encode/decode roundtrip.
 */
class SosRelayManagerTest {

    private lateinit var manager: SosRelayManager
    private val broadcastedPackets = mutableListOf<SafeRelayPacket>()

    @Before
    fun setup() {
        manager = SosRelayManager("abcdef0123456789")
        broadcastedPackets.clear()
        manager.delegate = object : SosRelayDelegate {
            override fun broadcastSosPacket(packet: SafeRelayPacket) {
                broadcastedPackets.add(packet)
            }
        }
    }

    // ─── Dedup ──────────────────────────────────────────────────

    @Test
    fun `same sosId is processed only once`() {
        val payload = createPayload(sosId = "UNIQUE-SOS-001")

        manager.onSosReceived(payload)
        manager.onSosReceived(payload) // duplicate

        // Only one upload queued
        assertEquals(1, manager.pendingUploads.size)
        // Only one broadcast
        assertEquals(1, broadcastedPackets.size)
    }

    @Test
    fun `different sosIds are all processed`() {
        manager.onSosReceived(createPayload(sosId = "SOS-A"))
        manager.onSosReceived(createPayload(sosId = "SOS-B"))
        manager.onSosReceived(createPayload(sosId = "SOS-C"))

        assertEquals(3, manager.pendingUploads.size)
        assertEquals(3, broadcastedPackets.size)
    }

    // ─── Hop Limit ─────────────────────────────────────────────

    @Test
    fun `payload at max hops is not re-broadcast`() {
        val maxHops = com.saferelay.android.util.AppConstants.SosRelay.MAX_RELAY_HOPS
        val payload = createPayload(sosId = "SOS-MAX-HOP", hopCount = maxHops)

        manager.onSosReceived(payload)

        // Should still be queued for upload
        assertEquals(1, manager.pendingUploads.size)
        // Should NOT be re-broadcast
        assertEquals(0, broadcastedPackets.size)
    }

    @Test
    fun `payload below max hops IS re-broadcast`() {
        val payload = createPayload(sosId = "SOS-HOP-5", hopCount = 5)

        manager.onSosReceived(payload)

        assertEquals(1, manager.pendingUploads.size)
        assertEquals(1, broadcastedPackets.size)
    }

    // ─── Queue Size ────────────────────────────────────────────

    @Test
    fun `queue caps at MAX_QUEUE_SIZE, dropping oldest`() {
        val maxQueue = com.saferelay.android.util.AppConstants.SosRelay.MAX_QUEUE_SIZE

        for (i in 1..maxQueue + 5) {
            manager.onSosReceived(createPayload(sosId = "SOS-Q-$i"))
        }

        assertEquals(maxQueue, manager.pendingUploads.size)

        // The first 5 should have been dropped; oldest remaining should be #6
        val firstRemaining = manager.pendingUploads.peek()
        assertEquals("SOS-Q-6", firstRemaining?.sosId)
    }

    // ─── Expiry ────────────────────────────────────────────────

    @Test
    fun `expired SOS payload is dropped`() {
        val expiredTimestamp = System.currentTimeMillis() -
                com.saferelay.android.util.AppConstants.SosRelay.SOS_EXPIRY_MS - 1000

        val payload = createPayload(sosId = "SOS-EXPIRED", timestampMs = expiredTimestamp)
        manager.onSosReceived(payload)

        assertEquals(0, manager.pendingUploads.size)
        assertEquals(0, broadcastedPackets.size)
    }

    @Test
    fun `fresh SOS payload is accepted`() {
        val freshTimestamp = System.currentTimeMillis() - 5000 // 5 seconds ago

        val payload = createPayload(sosId = "SOS-FRESH", timestampMs = freshTimestamp)
        manager.onSosReceived(payload)

        assertEquals(1, manager.pendingUploads.size)
    }

    // ─── Drain ─────────────────────────────────────────────────

    @Test
    fun `drainPendingUploads returns all and empties queue`() {
        manager.onSosReceived(createPayload(sosId = "SOS-DRAIN-1"))
        manager.onSosReceived(createPayload(sosId = "SOS-DRAIN-2"))

        val drained = manager.drainPendingUploads()

        assertEquals(2, drained.size)
        assertEquals(0, manager.pendingUploads.size)
    }

    // ─── Encode / Decode Roundtrip ─────────────────────────────

    @Test
    fun `SosRelayPayload encode-decode roundtrip preserves all fields`() {
        val original = SosRelayPayload(
            sosId = "ROUNDTRIP-001",
            senderNickname = "Alice",
            senderPeerID = "abcdef0123456789",
            latitude = 12.9716,
            longitude = 77.5946,
            batteryLevel = 42,
            timestampMs = 1710500000000L,
            hopCount = 3
        )

        val encoded = original.encode()
        val decoded = SosRelayPayload.decode(encoded)

        assertNotNull(decoded)
        assertEquals(original.sosId, decoded!!.sosId)
        assertEquals(original.senderNickname, decoded.senderNickname)
        assertEquals(original.senderPeerID, decoded.senderPeerID)
        assertEquals(original.latitude, decoded.latitude, 0.0001)
        assertEquals(original.longitude, decoded.longitude, 0.0001)
        assertEquals(original.batteryLevel, decoded.batteryLevel)
        assertEquals(original.timestampMs, decoded.timestampMs)
        assertEquals(original.hopCount, decoded.hopCount)
    }

    @Test
    fun `decode returns null for garbage data`() {
        assertNull(SosRelayPayload.decode(byteArrayOf(0, 1, 2)))
        assertNull(SosRelayPayload.decode(ByteArray(0)))
    }

    // ─── triggerSos ────────────────────────────────────────────

    @Test
    fun `triggerSos broadcasts and queues for upload`() {
        val payload = createPayload(sosId = "MY-SOS")
        manager.triggerSos(payload)

        assertEquals(1, broadcastedPackets.size)
        assertEquals(1, manager.pendingUploads.size)
    }

    @Test
    fun `triggerSos deduplicates own SOS on bounce-back`() {
        val payload = createPayload(sosId = "MY-SOS-BOUNCE")
        manager.triggerSos(payload)

        // Simulate bounce-back
        manager.onSosReceived(payload)

        // Should still only have 1 queued upload (not 2)
        assertEquals(1, manager.pendingUploads.size)
        // Only 1 broadcast (from triggerSos, not from onSosReceived)
        assertEquals(1, broadcastedPackets.size)
    }

    // ─── Helpers ────────────────────────────────────────────────

    private fun createPayload(
        sosId: String = "DEFAULT-SOS",
        hopCount: Int = 0,
        timestampMs: Long = System.currentTimeMillis()
    ) = SosRelayPayload(
        sosId = sosId,
        senderNickname = "TestUser",
        senderPeerID = "1234567890abcdef",
        latitude = 12.9716,
        longitude = 77.5946,
        batteryLevel = 85,
        timestampMs = timestampMs,
        hopCount = hopCount
    )
}
