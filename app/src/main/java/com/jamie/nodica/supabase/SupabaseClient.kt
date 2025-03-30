package com.jamie.nodica.supabase

import android.content.Context
import com.jamie.nodica.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Provides a configured instance of [SupabaseClient] using values from BuildConfig.
 *
 * Keys are securely injected via BuildConfig during the build process.
 * Refer to Android documentation on securing keys in build files.
 *
 * IMPORTANT: Ensure Row Level Security (RLS) is ENABLED on all sensitive tables
 * in your Supabase dashboard to prevent unauthorized data access from the client-side key.
 *
 * @param context The application context (injected via Koin).
 * @return A fully configured [SupabaseClient] instance.
 * @throws IllegalArgumentException if SUPABASE_URL or SUPABASE_ANON_KEY are missing in BuildConfig.
 */
fun provideSupabaseClient(context: Context): SupabaseClient {
    // Validate keys early - ensures build configuration is correct.
    require(BuildConfig.SUPABASE_URL.isNotBlank()) { "Supabase URL not found in BuildConfig." }
    require(BuildConfig.SUPABASE_ANON_KEY.isNotBlank()) { "Supabase Anon Key not found in BuildConfig." }

    Timber.d("Initializing SupabaseClient for URL: ${BuildConfig.SUPABASE_URL}")

    return createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        // Install required Supabase modules
        install(Auth) {
            // Auth configuration options (e.g., storage, deep links) can be added here if needed
            // scheme = "nodica"
            // host = "auth-callback"
            Timber.v("Supabase Auth module installed.")
        }
        install(Postgrest) {
            // Postgrest configuration (e.g., default schema)
            // defaultSchema = "public"
            Timber.v("Supabase Postgrest module installed.")
        }
        install(Realtime) {
            // Realtime configuration (e.g., reconnect delays, heartbeat interval)
            Timber.v("Supabase Realtime module installed.")
        }
        install(Storage) {
            // Storage configuration (e.g., default bucket)
            Timber.v("Supabase Storage module installed.")
        }

        // Configure the default JSON serializer
        // Allowing leniency and ignoring unknown keys increases robustness against API changes.
        defaultSerializer = KotlinXSerializer(Json {
            encodeDefaults = true // Ensure default values are encoded if needed
            ignoreUnknownKeys = true // Prevents crashes if backend adds new fields
            isLenient = true // Allows slightly non-standard JSON, use cautiously
        })
        Timber.v("Supabase KotlinXSerializer configured.")

        // Consider adding further configuration here if needed, e.g., custom headers
        // httpConfig { // Example
        //    customRequestHeaders = mapOf("X-Custom-Header" to "SomeValue")
        // }
    }
}