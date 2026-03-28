package com.saferelay.android.net

import com.saferelay.android.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.github.jan.supabase.http.httpConfig
import io.ktor.client.engine.okhttp.*
import kotlinx.serialization.json.Json

/**
 * Singleton module to provide SupabaseClient.
 * Initialized in SafeRelayApplication.
 */
object SupabaseModule {
    private var client: SupabaseClient? = null

    fun init() {
        if (client != null) return
        
        val url = BuildConfig.SUPABASE_URL
        val key = BuildConfig.SUPABASE_ANON_KEY

        // Skip initialization if credentials are not provided yet
        if (url.isBlank() || key.isBlank()) {
            android.util.Log.w("SupabaseModule", "Supabase URL or Key is missing. Sync will be disabled.")
            return
        }

        try {
            client = createSupabaseClient(url, key) {
                install(Postgrest)
                install(Auth)
                install(Realtime)
                install(Storage)

                // Force all Supabase traffic through the Tor-aware OkHttp client
                httpConfig {
                    engine = OkHttp.create {
                        preconfigured = OkHttpProvider.httpClient()
                    }
                }
                
                defaultSerializer = KotlinXSerializer(Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                    coerceInputValues = true
                })
            }
            android.util.Log.i("SupabaseModule", "SupabaseClient initialized successfully with Tor proxy.")
        } catch (e: Exception) {
            android.util.Log.e("SupabaseModule", "Failed to initialize SupabaseClient", e)
        }
    }

    fun getClient(): SupabaseClient? {
        return client
    }
}
