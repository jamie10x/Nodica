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

/**
 * Provides a configured instance of [SupabaseClient] using values from BuildConfig.
 *
 * Keys are securely injected via BuildConfig. For additional security, refer to
 * **Android BuildConfig** {🔑} [secure BuildConfig injection](https://www.google.com/search?q=Android+BuildConfig+usage).
 *
 * @param context The application context.
 * @return A fully configured [SupabaseClient] instance.
 */
fun provideSupabaseClient(context: Context): SupabaseClient {
    return createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
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
        defaultSerializer = KotlinXSerializer(Json {
            ignoreUnknownKeys = true
            isLenient = true
        })
    }
}


