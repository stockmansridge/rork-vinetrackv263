package com.rork.vinetrack.data

import com.rork.vinetrack.data.auth.SessionStore
import com.rork.vinetrack.data.model.Paddock
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.Instant

/**
 * Focused write path for paddock/block phenology milestone dates, mirroring the
 * iOS `paddocks` contract (`budburst_date`, `flowering_date`, `veraison_date`,
 * `harvest_date` — nullable `timestamptz`). RLS `paddocks_update_members` allows
 * update for owner/manager/supervisor/operator.
 *
 * Unlike the iOS full-row upsert, Android sends a **partial PATCH** containing
 * only the four phenology columns, so geometry, rows, variety allocations, area,
 * and other paddock metadata are never touched. Cleared dates are sent as an
 * explicit JSON `null` (the shared client uses `explicitNulls = false`, so a
 * hand-built `JsonObject` is used to guarantee nulls are transmitted).
 */
class PaddockRepository(private val session: SessionStore) {

    /** The four editable phenology milestone dates as ISO-8601 strings (null = cleared). */
    data class PhenologyDates(
        val budburstDate: String?,
        val floweringDate: String?,
        val veraisonDate: String?,
        val harvestDate: String?,
    )

    /**
     * PATCH only the phenology date columns for [paddockId]. Returns the updated
     * paddock row so callers can reconcile state with the server-resolved values.
     */
    suspend fun updatePhenologyDates(paddockId: String, dates: PhenologyDates): Paddock =
        withContext(Dispatchers.IO) {
            if (!SupabaseClient.isConfigured) throw BackendError.NotConfigured
            val token = session.accessToken ?: throw BackendError.Unauthorized
            val body: JsonObject = buildJsonObject {
                put("budburst_date", dates.budburstDate?.let { JsonPrimitive(it) } ?: JsonNull)
                put("flowering_date", dates.floweringDate?.let { JsonPrimitive(it) } ?: JsonNull)
                put("veraison_date", dates.veraisonDate?.let { JsonPrimitive(it) } ?: JsonNull)
                put("harvest_date", dates.harvestDate?.let { JsonPrimitive(it) } ?: JsonNull)
                put("client_updated_at", JsonPrimitive(Instant.now().toString()))
            }
            val response = SupabaseClient.http.patch(SupabaseClient.restUrl("paddocks?id=eq.$paddockId")) {
                headers {
                    append("apikey", SupabaseClient.anonKey)
                    append("Authorization", "Bearer $token")
                    append("Prefer", "return=representation")
                }
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            firstRow(response)
        }

    private suspend fun firstRow(response: HttpResponse): Paddock = when {
        response.status.isSuccess() -> response.body<List<Paddock>>().firstOrNull()
            ?: throw BackendError.Server(response.status.value, "Empty response")
        response.status.value == 401 || response.status.value == 403 -> throw BackendError.Unauthorized
        else -> throw BackendError.Server(response.status.value, response.bodyAsText())
    }
}
