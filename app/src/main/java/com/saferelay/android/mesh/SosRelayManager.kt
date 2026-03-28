package com.saferelay.android.mesh

import android.util.Log
import com.saferelay.android.model.SosRelayPayload
import com.saferelay.android.protocol.MessageType
import com.saferelay.android.protocol.SafeRelayPacket
import com.saferelay.android.protocol.SpecialRecipients
import com.saferelay.android.model.RoutedPacket
import com.saferelay.android.util.AppConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import com.saferelay.android.sync.SosSyncWorker
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.Collections

/**
 * Store-and-forward engine for SOS relay.
 *
 * - Deduplicates by [SosRelayPayload.sosId]
 * - Displays incoming SOS alerts via [incomingSosAlerts]
 * - Queues payloads for Supabase upload when internet is available
 * - Re-broadcasts with incremented hop count
 *
 * Battery-conscious: no extra BLE scanning — piggybacks on existing mesh cycle.
 */
class SosRelayManager(
    private val context: android.content.Context,
    private val myPeerID: String
) {

    companion object {
        private const val TAG = "SosRelayManager"
    }

    /** Emits decoded SOS payloads for UI display (map markers, notifications). */
    private val _incomingSosAlerts = MutableSharedFlow<SosRelayPayload>(replay = 10)
    val incomingSosAlerts: SharedFlow<SosRelayPayload> = _incomingSosAlerts.asSharedFlow()

    /** Dedup set — keyed by sosId. Thread-safe. */
    private val seenSosIds: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    /** Pending uploads awaiting internet connectivity. */
    val pendingUploads: ConcurrentLinkedQueue<SosRelayPayload> = ConcurrentLinkedQueue()

    /** Delegate for broadcasting packets over BLE mesh. */
    var delegate: SosRelayDelegate? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ─── Public API ─────────────────────────────────────────────

    /**
     * Called when the local user presses the SOS button.
     * Broadcasts immediately and queues for Supabase.
     */
    fun triggerSos(payload: SosRelayPayload) {
        Log.i(TAG, "🆘 LOCAL SOS triggered: sosId=${payload.sosId}, loc=${payload.latitude},${payload.longitude}")

        // Mark as seen so we don't re-process our own SOS when it bounces back
        seenSosIds.add(payload.sosId)

        // Queue for Supabase upload
        enqueueForUpload(payload)

        // Broadcast over BLE mesh
        broadcastSosPacket(payload)

        // Emit for local UI (map markers etc.)
        scope.launch { _incomingSosAlerts.emit(payload) }

        // Kick off sync worker
        SosSyncWorker.enqueue(context)
    }

    /**
     * Called when an SOS_RELAY packet arrives from the mesh.
     * Deduplicates, displays, stores for upload, and re-broadcasts.
     */
    fun onSosReceived(payload: SosRelayPayload) {
        // ── Dedup ──
        if (seenSosIds.contains(payload.sosId)) {
            Log.d(TAG, "SOS ${payload.sosId.take(8)} already seen, skipping")
            return
        }

        // ── Expiry check ──
        val age = System.currentTimeMillis() - payload.timestampMs
        if (age > AppConstants.SosRelay.SOS_EXPIRY_MS) {
            Log.d(TAG, "SOS ${payload.sosId.take(8)} expired (age=${age}ms), dropping")
            return
        }

        seenSosIds.add(payload.sosId)
        Log.i(TAG, "🆘 INCOMING SOS relay: sosId=${payload.sosId.take(8)}, from=${payload.senderNickname}, hops=${payload.hopCount}")

        // Emit for UI display
        scope.launch { _incomingSosAlerts.emit(payload) }

        // Queue for Supabase upload
        enqueueForUpload(payload)

        // Re-broadcast with incremented hop count
        val nextHop = payload.hopCount + 1
        if (nextHop <= AppConstants.SosRelay.MAX_RELAY_HOPS) {
            val relayPayload = payload.copy(hopCount = nextHop)
            broadcastSosPacket(relayPayload)
            Log.d(TAG, "Re-broadcasting SOS ${payload.sosId.take(8)} at hop $nextHop")
        } else {
            Log.d(TAG, "SOS ${payload.sosId.take(8)} reached max hops (${payload.hopCount}), not re-broadcasting")
        }

        // Kick off sync worker for incoming relay
        SosSyncWorker.enqueue(context)
    }

    /**
     * Drain all pending SOS payloads. Called by [SosSyncWorker] when internet is available.
     * Returns the list of payloads to upload.
     */
    fun drainPendingUploads(): List<SosRelayPayload> {
        val result = mutableListOf<SosRelayPayload>()
        while (true) {
            val item = pendingUploads.poll() ?: break
            result.add(item)
        }
        return result
    }

    /**
     * Shutdown: cancel scope, clear state.
     */
    fun shutdown() {
        scope.cancel()
        seenSosIds.clear()
        pendingUploads.clear()
    }

    // ─── Internal ───────────────────────────────────────────────

    private fun enqueueForUpload(payload: SosRelayPayload) {
        pendingUploads.add(payload)

        // Cap queue size
        while (pendingUploads.size > AppConstants.SosRelay.MAX_QUEUE_SIZE) {
            val dropped = pendingUploads.poll()
            Log.w(TAG, "Queue full, dropping oldest SOS: ${dropped?.sosId?.take(8)}")
        }
    }

    private fun broadcastSosPacket(payload: SosRelayPayload) {
        try {
            val binaryPayload = payload.encode()

            val packet = SafeRelayPacket(
                version = 1u,
                type = MessageType.SOS_RELAY.value,
                senderID = hexStringToByteArray(myPeerID),
                recipientID = SpecialRecipients.BROADCAST,
                timestamp = System.currentTimeMillis().toULong(),
                payload = binaryPayload,
                signature = null,
                ttl = AppConstants.MESSAGE_TTL_HOPS
            )

            delegate?.broadcastSosPacket(packet)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to broadcast SOS packet: ${e.message}")
        }
    }

    private fun hexStringToByteArray(hexString: String): ByteArray {
        val result = ByteArray(8) { 0 }
        var tempID = hexString
        var index = 0
        while (tempID.length >= 2 && index < 8) {
            val hexByte = tempID.substring(0, 2)
            val byte = hexByte.toIntOrNull(16)?.toByte()
            if (byte != null) result[index] = byte
            tempID = tempID.substring(2)
            index++
        }
        return result
    }
}

/**
 * Delegate for SOS relay broadcasting.
 */
interface SosRelayDelegate {
    /** Broadcast a signed SOS_RELAY packet to all connected peers. */
    fun broadcastSosPacket(packet: SafeRelayPacket)
}
