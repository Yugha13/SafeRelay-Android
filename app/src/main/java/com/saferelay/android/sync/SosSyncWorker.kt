package com.saferelay.android.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.saferelay.android.mesh.SosRelayManager
import com.saferelay.android.net.SupabaseModule
import com.saferelay.android.util.AppConstants
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Lightweight coroutine-based SOS sync. Uploads queued SOS relay payloads to Supabase
 * when internet connectivity is available.
 *
 * No WorkManager dependency required – calls are idempotent and safe to invoke repeatedly.
 */
object SosSyncWorker {

    private const val TAG = "SosSyncWorker"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var running = false

    /**
     * Attempt to upload queued SOS payloads if internet is available.
     * Safe to call from any thread – deduplicates concurrent invocations.
     */
    fun enqueue(context: Context) {
        if (running) return

        if (!isNetworkAvailable(context)) {
            Log.d(TAG, "No network – will retry on next enqueue call")
            return
        }

        scope.launch {
            drainToSupabase(context)
        }
    }

    private suspend fun drainToSupabase(context: Context) {
        if (running) return
        running = true

        try {
            val meshService = com.saferelay.android.service.MeshServiceHolder.meshService
            if (meshService == null) {
                Log.w(TAG, "MeshService not available")
                return
            }

            val sosRelayManager = meshService.sosRelayManager
            val payloads = sosRelayManager.drainPendingUploads()

            if (payloads.isEmpty()) {
                Log.d(TAG, "No pending SOS payloads to upload")
                return
            }

            Log.i(TAG, "Uploading ${payloads.size} SOS payloads to Supabase")

            val client = SupabaseModule.getClient()
            if (client == null) {
                Log.w(TAG, "Supabase client not initialized, re-queuing payloads")
                payloads.forEach { sosRelayManager.pendingUploads.add(it) }
                return
            }

            var uploaded = 0
            for (payload in payloads) {
                try {
                    client.postgrest
                        .from(AppConstants.SosRelay.SUPABASE_TABLE)
                        .upsert(payload)
                    uploaded++
                    Log.d(TAG, "Uploaded SOS ${payload.sosId.take(8)} (hop=${payload.hopCount})")
                } catch (e: Exception) {
                    val msg = e.message ?: ""
                    if (msg.contains("duplicate") || msg.contains("23505") || msg.contains("conflict", ignoreCase = true)) {
                        Log.d(TAG, "SOS ${payload.sosId.take(8)} already in Supabase, dropping from queue")
                    } else {
                        Log.w(TAG, "Failed to upload SOS ${payload.sosId.take(8)}: $msg")
                        sosRelayManager.pendingUploads.add(payload)
                    }
                }
            }

            Log.i(TAG, "SOS sync complete: $uploaded/${payloads.size} uploaded")

        } catch (e: Exception) {
            Log.e(TAG, "SOS sync failed: ${e.message}")
        } finally {
            running = false
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }
}
