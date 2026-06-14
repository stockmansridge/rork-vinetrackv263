package com.rork.vinetrack.data

import com.rork.vinetrack.data.auth.SessionStore
import com.rork.vinetrack.data.model.HistoricalBlockResult
import com.rork.vinetrack.data.model.HistoricalYieldRecord
import io.ktor.client.call.body
import io.ktor.client.request.get
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
 * Write path for archived seasonal yield records, mirroring the iOS
 * `historical_yield_records` sync contract (table +
 * `soft_delete_historical_yield_record` RPC). RLS scopes everything to the
 * signed-in user's vineyard role: owner/manager/supervisor/operator may insert
 * and update; only owner/manager/supervisor may soft-delete.
 *
 * Online-first — there is no local queue. Android authors block-level actual
 * yield records directly (one block per record, matching iOS's
 * `RecordActualYieldSheet`) and edits per-block actuals + notes on existing
 * records. `block_results` is a jsonb array with camelCase keys to stay binary
 * compatible with the iOS Codable encoding. Server-managed sync columns are
 * left untouched on edit.
 */
class YieldRepository(private val session: SessionStore) {

    @Serializable
    private data class YieldInsert(
        val id: String,
        @SerialName("vineyard_id") val vineyardId: String,
        val season: String,
        val year: Int,
        @SerialName("archived_at") val archivedAt: String,
        @SerialName("total_yield_tonnes") val totalYieldTonnes: Double,
        @SerialName("total_area_hectares") val totalAreaHectares: Double,
        val notes: String,
        @SerialName("block_results") val blockResults: List<HistoricalBlockResult>,
        @SerialName("created_by") val createdBy: String? = null,
        @SerialName("client_updated_at") val clientUpdatedAt: String,
    )

    /** Edit of the record-owned fields (no created_by/sync changes). */
    @Serializable
    private data class YieldPatch(
        val season: String,
        val year: Int,
        @SerialName("total_yield_tonnes") val totalYieldTonnes: Double,
        @SerialName("total_area_hectares") val totalAreaHectares: Double,
        val notes: String,
        @SerialName("block_results") val blockResults: List<HistoricalBlockResult>,
        @SerialName("client_updated_at") val clientUpdatedAt: String,
    )

    @Serializable
    private data class SoftDeleteArgs(@SerialName("p_id") val id: String)

    /** Fields captured when archiving a single block's actual yield. */
    data class CreateInput(
        val year: Int,
        val season: String,
        val paddockId: String,
        val paddockName: String,
        val areaHectares: Double,
        val totalVines: Int,
        val variety: String?,
        val actualYieldTonnes: Double,
        val notes: String?,
    )

    private fun nowIso(): String = Instant.now().toString()

    suspend fun listYieldRecords(vineyardId: String): List<HistoricalYieldRecord> =
        withContext(Dispatchers.IO) {
            requireConfig()
            val token = session.accessToken ?: throw BackendError.Unauthorized
            val response = SupabaseClient.http.get(
                SupabaseClient.restUrl(
                    "historical_yield_records?select=*&vineyard_id=eq.$vineyardId&deleted_at=is.null&order=year.desc,archived_at.desc",
                ),
            ) { authHeaders(token) }
            when {
                response.status.isSuccess() -> response.body()
                response.status.value == 401 || response.status.value == 403 -> throw BackendError.Unauthorized
                else -> throw BackendError.Server(response.status.value, response.bodyAsText())
            }
        }

    suspend fun createYieldRecord(vineyardId: String, input: CreateInput): HistoricalYieldRecord =
        withContext(Dispatchers.IO) {
            requireConfig()
            val token = session.accessToken ?: throw BackendError.Unauthorized
            val now = nowIso()
            val perHectare = if (input.areaHectares > 0) input.actualYieldTonnes / input.areaHectares else 0.0
            // One block per Android-authored record (mirrors iOS RecordActualYieldSheet):
            // the estimated yield equals the recorded actual since no sampling exists.
            val blockName = input.variety?.takeIf { it.isNotBlank() }
                ?.let { "${input.paddockName} \u2014 $it" } ?: input.paddockName
            val block = HistoricalBlockResult(
                id = UUID.randomUUID().toString(),
                paddockId = input.paddockId,
                paddockName = blockName,
                areaHectares = input.areaHectares,
                yieldTonnes = input.actualYieldTonnes,
                yieldPerHectare = perHectare,
                totalVines = input.totalVines,
                actualYieldTonnes = input.actualYieldTonnes,
                actualRecordedAt = now,
            )
            val body = YieldInsert(
                id = UUID.randomUUID().toString(),
                vineyardId = vineyardId,
                season = input.season.trim(),
                year = input.year,
                archivedAt = now,
                totalYieldTonnes = input.actualYieldTonnes,
                totalAreaHectares = input.areaHectares,
                notes = input.notes?.trim().orEmpty(),
                blockResults = listOf(block),
                createdBy = session.userId,
                clientUpdatedAt = now,
            )
            val response = SupabaseClient.http.post(SupabaseClient.restUrl("historical_yield_records")) {
                authHeaders(token)
                headers { append("Prefer", "return=representation") }
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            firstRow(response)
        }

    /**
     * Patch an existing record's editable fields after the caller updates the
     * per-block actuals / notes. The full reconciled `block_results` array and
     * recomputed totals are passed in so the record stays internally consistent.
     */
    suspend fun updateYieldRecord(
        id: String,
        season: String,
        year: Int,
        totalYieldTonnes: Double,
        totalAreaHectares: Double,
        notes: String,
        blockResults: List<HistoricalBlockResult>,
    ): HistoricalYieldRecord = withContext(Dispatchers.IO) {
        requireConfig()
        val token = session.accessToken ?: throw BackendError.Unauthorized
        val patch = YieldPatch(
            season = season.trim(),
            year = year,
            totalYieldTonnes = totalYieldTonnes,
            totalAreaHectares = totalAreaHectares,
            notes = notes.trim(),
            blockResults = blockResults,
            clientUpdatedAt = nowIso(),
        )
        val response = SupabaseClient.http.patch(SupabaseClient.restUrl("historical_yield_records?id=eq.$id")) {
            authHeaders(token)
            headers { append("Prefer", "return=representation") }
            contentType(ContentType.Application.Json)
            setBody(patch)
        }
        firstRow(response)
    }

    suspend fun softDeleteYieldRecord(id: String) = withContext(Dispatchers.IO) {
        requireConfig()
        val token = session.accessToken ?: throw BackendError.Unauthorized
        val response = SupabaseClient.http.post(SupabaseClient.rpcUrl("soft_delete_historical_yield_record")) {
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

    private suspend fun firstRow(response: io.ktor.client.statement.HttpResponse): HistoricalYieldRecord = when {
        response.status.isSuccess() -> response.body<List<HistoricalYieldRecord>>().firstOrNull()
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
