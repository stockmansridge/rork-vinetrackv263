package com.rork.vinetrack.data.auth

import android.content.Context
import androidx.core.content.edit

/**
 * Persists the Supabase session tokens so the user stays signed in across
 * launches, mirroring the iOS session restore behaviour.
 */
class SessionStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("vinetrack_session", Context.MODE_PRIVATE)

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS, null)
        set(value) = prefs.edit { putString(KEY_ACCESS, value) }

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH, null)
        set(value) = prefs.edit { putString(KEY_REFRESH, value) }

    var userId: String?
        get() = prefs.getString(KEY_USER_ID, null)
        set(value) = prefs.edit { putString(KEY_USER_ID, value) }

    var userEmail: String?
        get() = prefs.getString(KEY_EMAIL, null)
        set(value) = prefs.edit { putString(KEY_EMAIL, value) }

    var selectedVineyardId: String?
        get() = prefs.getString(KEY_SELECTED_VINEYARD, null)
        set(value) = prefs.edit { putString(KEY_SELECTED_VINEYARD, value) }

    /**
     * Cached copy of the user's preferred default vineyard (server is the
     * source of truth). Kept locally so an offline launch can still prefer the
     * default when the profile can't be fetched.
     */
    var defaultVineyardId: String?
        get() = prefs.getString(KEY_DEFAULT_VINEYARD, null)
        set(value) = prefs.edit { putString(KEY_DEFAULT_VINEYARD, value) }

    val hasSession: Boolean get() = !accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()

    fun save(accessToken: String, refreshToken: String, userId: String?, email: String?) {
        prefs.edit {
            putString(KEY_ACCESS, accessToken)
            putString(KEY_REFRESH, refreshToken)
            putString(KEY_USER_ID, userId)
            putString(KEY_EMAIL, email)
        }
    }

    fun clear() {
        prefs.edit {
            remove(KEY_ACCESS)
            remove(KEY_REFRESH)
            remove(KEY_USER_ID)
            remove(KEY_EMAIL)
            // Keep selected vineyard so re-login restores context.
        }
    }

    private companion object {
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_EMAIL = "user_email"
        const val KEY_SELECTED_VINEYARD = "selected_vineyard_id"
        const val KEY_DEFAULT_VINEYARD = "default_vineyard_id"
    }
}
