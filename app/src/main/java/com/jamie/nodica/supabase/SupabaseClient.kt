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

Provides a configured instance of [SupabaseClient] using values from BuildConfig.

Keys are securely injected via BuildConfig.

IMPORTANT: For production, ensure Row Level Security (RLS) is ENABLED on all tables

in your Supabase dashboard to prevent unauthorized data access.

SECURITY NOTE: While BuildConfig is convenient for development, consider more robust

key management solutions (like secrets managers or CI/CD environment variables) for production builds.

Refer to Android BuildConfig {🔑} secure BuildConfig injection.

@param context The application context (can be injected via Koin).

@return A fully configured [SupabaseClient] instance.
 */
fun provideSupabaseClient(context: Context): SupabaseClient {
    Timber.d("Initializing SupabaseClient...") // Added log for initialization tracking
    return createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL, supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
// Install the authentication module for user management
        install(Auth)
// Install the PostgREST module for database operations
        install(Postgrest)
// Install the Realtime module for live data updates
        install(Realtime)
// Install the Storage module for handling file uploads/downloads
        install(Storage)
// Configure the default JSON serializer
// Allowing leniency and ignoring unknown keys can help prevent crashes
// if the backend adds fields the client doesn't know about yet.
        defaultSerializer = KotlinXSerializer(Json {
            ignoreUnknownKeys = true
            isLenient = true // Good for slightly malformed JSON, but use with caution.
        })

// Consider adding further configuration here if needed, e.g., custom headers, etc.
    }
}

