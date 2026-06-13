package com.rork.vinetrack.data

import com.rork.vinetrack.data.auth.SessionStore
import com.rork.vinetrack.data.model.Pin
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.headers
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Write path for operational pins, mirroring the iOS pin sync behaviour.
 *
 * Inserts and updates go straight to the PostgREST `pins` table (RLS scopes
 * them to the signed-in user's vineyard role). Deletes route through the
 * `soft_delete_pin` RPC so the server enforces the manager/supervisor
 * permission and stamps `deleted_at` instead of hard-deleting.
 *
 * Display-only — it never mutates records it doesn't own and only sends the
 * fields the Android UI edits, leaving every other column untouched.
 */
class PinRepository(private val session: SessionStore) {

    /** Mutable fields the Android pin editor exposes. */
    @Serializable
    data class PinInput(
        @SerialName("vineyard_id") val vineyardId: String,
        @SerialName("paddock_id") val paddockId: String? = null,
        val title: String? = null,
        val category: String? = null,
        val mode: String? = null,
        val notes: String? = null,
        @SerialName("row_number") val rowNumber: Int? = null,
        @SerialName("is_completed") val isCompleted: Boolean = false,
        val latitude: Double? = null,
        val longitude: Double? = null,
        @SerialName("created_by") val createdBy: String? = null,
    )

    /** Subset of editable fields used for PATCH updates (no created_by overwrite). */
    @Serializable
    private data class PinPatch(
        @SerialName("paddock_id") val paddockId: String? = null,
        val title: String? = null,
        val category: String? = null,
        val mode: String? = null,
        val notes: String? = null,
        @SerialName("row_number") val rowNumber: Int? = null,
        @SerialName("is_completed") val isCompleted: Boolean,
    )

    @Serializable
    private data class PhotoPatch(@SerialName("photo_path") val photoPath: String?)

    @Serializable
    private data class SoftDeleteArgs(@SerialName("p_pin_id") val pinId: String)

    suspend fun createPin(input: PinInput): Pin = withContext(Dispatchers.IO) {
        requireConfig()
        val token = session.accessToken ?: throw BackendError.Unauthorized
        val body = input.copy(createdBy = input.createdBy ?: session.userId)
        val response = SupabaseClient.http.post(SupabaseClient.restUrl("pins")) {
            authHeaders(token)
            headers { append("Prefer", "return=representation") }
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        when {
            response.status.isSuccess() -> response.body<List<Pin>>().firstOrNull()
                ?: throw BackendError.Server(response.status.value, "Empty response")
            response.status.value == 401 || response.status.value == 403 -> throw BackendError.Unauthorized
            else -> throw BackendError.Server(response.status.value, response.bodyAsText())
        }
    }

    suspend fun updatePin(
        id: String,
        paddockId: String?,
        title: String?,
        category: String?,
        mode: String?,
        notes: String?,
        rowNumber: Int?,
        isCompleted: Boolean,
    ): Pin = withContext(Dispatchers.IO) {
        requireConfig()
        val token = session.accessToken ?: throw BackendError.Unauthorized
        val patch = PinPatch(
            paddockId = paddockId,
            title = title,
            category = category,
            mode = mode,
            notes = notes,
            rowNumber = rowNumber,
            isCompleted = isCompleted,
        )
        val response = SupabaseClient.http.patch(SupabaseClient.restUrl("pins?id=eq.$id")) {
            authHeaders(token)
            headers { append("Prefer", "return=representation") }
            contentType(ContentType.Application.Json)
            setBody(patch)
        }
        when {
            response.status.isSuccess() -> response.body<List<Pin>>().firstOrNull()
                ?: throw BackendError.Server(response.status.value, "Empty response")
            response.status.value == 401 || response.status.value == 403 -> throw BackendError.Unauthorized
            else -> throw BackendError.Server(response.status.value, response.bodyAsText())
        }
    }

    /**
     * Set or clear a pin's `photo_path` after a storage upload/removal. Kept
     * separate from [updatePin] so photo writes don't disturb the editable
     * field set, and returns the reconciled row so the UI can refresh.
     */
    suspend fun updatePhotoPath(id: String, photoPath: String?): Pin = withContext(Dispatchers.IO) {
        requireConfig()
        val token = session.accessToken ?: throw BackendError.Unauthorized
        val response = SupabaseClient.http.patch(SupabaseClient.restUrl("pins?id=eq.$id")) {
            authHeaders(token)
            headers { append("Prefer", "return=representation") }
            contentType(ContentType.Application.Json)
            setBody(PhotoPatch(photoPath))
        }
        when {
            response.status.isSuccess() -> response.body<List<Pin>>().firstOrNull()
                ?: throw BackendError.Server(response.status.value, "Empty response")
            response.status.value == 401 || response.status.value == 403 -> throw BackendError.Unauthorized
            else -> throw BackendError.Server(response.status.value, response.bodyAsText())
        }
    }

    suspend fun softDeletePin(id: String) = withContext(Dispatchers.IO) {
        requireConfig()
        val token = session.accessToken ?: throw BackendError.Unauthorized
        val response = SupabaseClient.http.post(SupabaseClient.rpcUrl("soft_delete_pin")) {
            authHeaders(token)
            contentType(ContentType.Application.Json)
            setBody(SoftDeleteArgs(id))
        }
        when {
            response.status.isSuccess() -> Unit
            response.status.value == 401 || response.status.value == 403 -> throw BackendError.Unauthorized
            else -> throw BackendError.Server(response.status.value, response.bodyAsText())
        }
    }

    private fun requireConfig() {
        if (!SupabaseClient.isConfigured) throw BackendError.NotConfigured
    }

    private fun io.ktor.client.request.HttpRequestBuilder.authHeaders(token: String) {
        headers {
            append("apikey", SupabaseClient.anonKey)
            append("Authorization", "Bearer $token")
        }
    }
}
