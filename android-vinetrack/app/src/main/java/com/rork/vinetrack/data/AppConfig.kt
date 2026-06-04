package com.rork.vinetrack.data

import com.rork.vinetrack.Config

/**
 * Centralised configuration, mirroring the iOS `AppConfig`.
 * Reads build-injected `EXPO_PUBLIC_*` values from [Config], falling back to
 * the public Supabase project URL used by VineTrack.
 */
object AppConfig {

    private const val DEFAULT_SUPABASE_URL = "https://tbafuqwruefgkbyxrxyb.supabase.co"

    val supabaseUrl: String
        get() = resolve("SUPABASE_URL", "EXPO_PUBLIC_SUPABASE_URL")
            ?.trimEnd('/')
            ?: DEFAULT_SUPABASE_URL

    val supabaseAnonKey: String
        get() = resolve("SUPABASE_ANON_KEY", "EXPO_PUBLIC_SUPABASE_ANON_KEY") ?: ""

    val isSupabaseConfigured: Boolean
        get() = supabaseAnonKey.isNotBlank()

    private fun resolve(vararg keys: String): String? {
        for (key in keys) {
            val value = Config.allValues[key]?.trim()
            if (!value.isNullOrEmpty()) return value
        }
        return null
    }
}
