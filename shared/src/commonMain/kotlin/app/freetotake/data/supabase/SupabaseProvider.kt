// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
// Copyright (C) 2026 Dina Elokhova (DinaSimple) — Free to Take, https://freetotake.app
package app.freetotake.data.supabase

import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

/**
 * Single Supabase client for the app (free tier: Auth + Postgres + Storage).
 * URL / anon key come from local.properties via BuildConfig — never hard-coded.
 */
object SupabaseProvider {
    private var instance: SupabaseClient? = null

    fun init(url: String, anonKey: String): SupabaseClient {
        require(url.isNotBlank() && anonKey.isNotBlank()) {
            "Supabase URL/anon key missing — set supabase.url and supabase.anonKey in local.properties"
        }
        return instance ?: createSupabaseClient(supabaseUrl = url, supabaseKey = anonKey) {
            // Rows may carry more columns than a DTO maps (e.g. updated_at) — never fail on them.
            defaultSerializer = KotlinXSerializer(Json { ignoreUnknownKeys = true; encodeDefaults = true })
            install(Auth) {
                // v1.15.1: browser OAuth (Facebook) returns to freetotake://login-callback; PKCE keeps the code exchange on-device.
                scheme = "freetotake"
                host = "login-callback"
                flowType = io.github.jan.supabase.auth.FlowType.PKCE
            }
            install(Postgrest)
            install(Storage)
        }.also { instance = it }
    }

    val client: SupabaseClient
        get() = checkNotNull(instance) { "SupabaseProvider.init() was not called" }

    val isInitialized: Boolean get() = instance != null
}
