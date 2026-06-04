package com.rork.vinetrack.data

import android.util.Log
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

    /**
     * Emits a safe, one-line diagnostic about the runtime Supabase config.
     * Never prints the key itself — only presence flags, the resolved URL,
     * and the anon key length so we can confirm the build-injected values are
     * actually reaching the APK at runtime.
     */
    fun logDiagnostics() {
        val url = supabaseUrl
        val key = supabaseAnonKey
        Log.i(
            TAG,
            "Supabase config — URL present: ${url.isNotBlank()} (\"$url\"), " +
                "anon key present: ${key.isNotBlank()}, anon key length: ${key.length}",
        )
    }

    private const val TAG = "VineTrackConfig"

    private fun resolve(vararg keys: String): String? {
        for (key in keys) {
            val value = Config.allValues[key]?.trim()
            if (!value.isNullOrEmpty()) return value
        }
        return null
    }
}
