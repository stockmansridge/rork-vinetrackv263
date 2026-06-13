package com.rork.vinetrack.data

import com.rork.vinetrack.data.auth.SessionStore
import com.rork.vinetrack.data.model.GrowthStageRecord
import io.ktor.client.call.body
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
import java.time.Instant
import java.util.UUID

/**
 * Write path for growth-stage observations, mirroring the iOS
 * `growth_stage_records` sync contract (table + `soft_delete_growth_stage_record`
 * RPC). RLS scopes everything to the signed-in user's vineyard role:
 * owner/manager/supervisor/operator may insert and update; only
 * owner/manager/supervisor may soft-delete.
 *
 * Online-first — there is no local queue yet. Android authors records directly
 * (no source pin), so `pin_id` is left null and the growth form is the sole
 * editor of these columns. `created_by` and the server-managed sync columns are
 * left untouched on edit. Records mirrored from iOS pins (with a `pin_id`) are
 * still readable and editable through the same path.
 */
class GrowthStageRecordRepository(private val session: SessionStore) {

    @Serializable
    private data class GrowthInsert(
        val id: String,
        @SerialName("vineyard_id") val vineyardId: String,
        @SerialName("paddock_id") val paddockId: String? = null,
        @SerialName("stage_code") val stageCode: String,
        @SerialName("stage_label") val stageLabel: String? = null,
        val variety: String? = null,
        @SerialName("observed_at") val observedAt: String,
        @SerialName("row_number") val rowNumber: Int? = null,
        val notes: String? = null,
        @SerialName("recorded_by_name") val recordedByName: String? = null,
        @SerialName("created_by") val createdBy: String? = null,
        @SerialName("client_updated_at") val clientUpdatedAt: String,
    )

    /** Edit of the form-owned columns (no pin/created_by/photo/sync changes). */
    @Serializable
    private data class GrowthPatch(
        @SerialName("paddock_id") val paddockId: String? = null,
        @SerialName("stage_code") val stageCode: String,
        @SerialName("stage_label") val stageLabel: String? = null,
        val variety: String? = null,
        @SerialName("observed_at") val observedAt: String,
        @SerialName("row_number") val rowNumber: Int? = null,
        val notes: String? = null,
        @SerialName("client_updated_at") val clientUpdatedAt: String,
    )

    @Serializable
    private data class SoftDeleteArgs(@SerialName("p_id") val id: String)

    /** Fields the growth-stage form edits, passed through both create and edit paths. */
    data class GrowthInput(
        val paddockId: String?,
        val stageCode: String,
        val stageLabel: String?,
        val variety: String?,
        val observedAt: String,
        val rowNumber: Int?,
        val notes: String?,
    )

    private fun nowIso(): String = Instant.now().toString()

    suspend fun createGrowthStageRecord(vineyardId: String, input: GrowthInput): GrowthStageRecord =
        withContext(Dispatchers.IO) {
            requireConfig()
            val token = session.accessToken ?: throw BackendError.Unauthorized
            val now = nowIso()
            val body = GrowthInsert(
                id = UUID.randomUUID().toString(),
                vineyardId = vineyardId,
                paddockId = input.paddockId,
                stageCode = input.stageCode,
                stageLabel = input.stageLabel,
                variety = input.variety,
                observedAt = input.observedAt,
                rowNumber = input.rowNumber,
                notes = input.notes,
                recordedByName = null,
                createdBy = session.userId,
                clientUpdatedAt = now,
            )
            val response = SupabaseClient.http.post(SupabaseClient.restUrl("growth_stage_records")) {
                authHeaders(token)
                headers { append("Prefer", "return=representation") }
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            firstRow(response)
        }

    suspend fun updateGrowthStageRecord(id: String, input: GrowthInput): GrowthStageRecord =
        withContext(Dispatchers.IO) {
            requireConfig()
            val token = session.accessToken ?: throw BackendError.Unauthorized
            val patch = GrowthPatch(
                paddockId = input.paddockId,
                stageCode = input.stageCode,
                stageLabel = input.stageLabel,
                variety = input.variety,
                observedAt = input.observedAt,
                rowNumber = input.rowNumber,
                notes = input.notes,
                clientUpdatedAt = nowIso(),
            )
            val response = SupabaseClient.http.patch(SupabaseClient.restUrl("growth_stage_records?id=eq.$id")) {
                authHeaders(token)
                headers { append("Prefer", "return=representation") }
                contentType(ContentType.Application.Json)
                setBody(patch)
            }
            firstRow(response)
        }

    suspend fun softDeleteGrowthStageRecord(id: String) = withContext(Dispatchers.IO) {
        requireConfig()
        val token = session.accessToken ?: throw BackendError.Unauthorized
        val response = SupabaseClient.http.post(SupabaseClient.rpcUrl("soft_delete_growth_stage_record")) {
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

    private suspend fun firstRow(response: io.ktor.client.statement.HttpResponse): GrowthStageRecord = when {
        response.status.isSuccess() -> response.body<List<GrowthStageRecord>>().firstOrNull()
            ?: throw BackendError.Server(response.status.value, "Empty response")
        response.status.value == 401 || response.status.value == 403 -> throw BackendError.Unauthorized
        else -> throw BackendError.Server(response.status.value, response.bodyAsText())
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
