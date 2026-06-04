package com.rork.vinetrack.data

import android.util.Log
import com.rork.vinetrack.BuildConfig
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
        if (!BuildConfig.DEBUG) return
        val url = supabaseUrl
        val key = supabaseAnonKey
        val masked = if (key.length >= 8) {
            "${key.take(4)}…${key.takeLast(4)}"
        } else {
            "(too short to mask)"
        }
        Log.i(
            TAG,
            "Supabase config — URL present: ${url.isNotBlank()} (\"$url\"), " +
                "anon key present: ${key.isNotBlank()}, anon key length: ${key.length}, " +
                "anon key preview: $masked",
        )
    }

    private const val TAG = "VineTrackConfig"

    /**
     * Resolves a value from, in priority order:
     *   1. The Rork build-injected [Config] map (EXPO_PUBLIC_* values).
     *   2. Gradle [BuildConfig] fields injected from the build environment.
     * This makes config robust whether the Rork Config.kt injection or the
     * Gradle BuildConfig injection lands in the compiled APK.
     */
    private fun resolve(vararg keys: String): String? {
        for (key in keys) {
            val value = Config.allValues[key]?.trim()
            if (!value.isNullOrEmpty()) return value
        }
        for (key in keys) {
            val value = buildConfigValue(key)?.trim()
            if (!value.isNullOrEmpty()) return value
        }
        return null
    }

    private fun buildConfigValue(key: String): String? = when (key) {
        "SUPABASE_URL", "EXPO_PUBLIC_SUPABASE_URL" -> BuildConfig.SUPABASE_URL
        "SUPABASE_ANON_KEY", "EXPO_PUBLIC_SUPABASE_ANON_KEY" -> BuildConfig.SUPABASE_ANON_KEY
        else -> null
    }
}
