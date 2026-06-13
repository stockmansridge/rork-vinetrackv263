package com.rork.vinetrack.data

import com.rork.vinetrack.data.auth.SessionStore
import com.rork.vinetrack.data.model.CoordinatePoint
import com.rork.vinetrack.data.model.Trip
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
 * Write path for operational trips, mirroring the iOS trip sync contract
 * (`trips` table + `soft_delete_trip` RPC). RLS scopes everything to the
 * signed-in user's vineyard role: owner/manager/supervisor/operator may
 * insert and update; only owner/manager/supervisor may soft-delete.
 *
 * Online-first — there is no local queue yet. Every mutation sends only the
 * columns Android edits, leaving the iOS-managed JSONB fields it doesn't
 * touch (tank_sessions, row_sequence, seeding_details, etc.) intact.
 */
class TripRepository(private val session: SessionStore) {

    /** Insert payload when starting a new trip. */
    @Serializable
    data class TripInsert(
        val id: String,
        @SerialName("vineyard_id") val vineyardId: String,
        @SerialName("paddock_id") val paddockId: String? = null,
        @SerialName("paddock_name") val paddockName: String? = null,
        @SerialName("start_time") val startTime: String,
        @SerialName("is_active") val isActive: Boolean,
        @SerialName("is_paused") val isPaused: Boolean = false,
        @SerialName("person_name") val personName: String? = null,
        @SerialName("trip_function") val tripFunction: String? = null,
        @SerialName("trip_title") val tripTitle: String? = null,
        @SerialName("machine_id") val machineId: String? = null,
        @SerialName("work_task_id") val workTaskId: String? = null,
        @SerialName("operator_user_id") val operatorUserId: String? = null,
        @SerialName("operator_category_id") val operatorCategoryId: String? = null,
        @SerialName("total_distance") val totalDistance: Double = 0.0,
        @SerialName("path_points") val pathPoints: List<CoordinatePoint> = emptyList(),
        @SerialName("created_by") val createdBy: String? = null,
        @SerialName("client_updated_at") val clientUpdatedAt: String,
    )

    /** Metadata edit (start-sheet details) without disturbing live progress. */
    @Serializable
    private data class TripMetadataPatch(
        @SerialName("paddock_id") val paddockId: String? = null,
        @SerialName("paddock_name") val paddockName: String? = null,
        @SerialName("person_name") val personName: String? = null,
        @SerialName("trip_function") val tripFunction: String? = null,
        @SerialName("trip_title") val tripTitle: String? = null,
        @SerialName("machine_id") val machineId: String? = null,
        @SerialName("work_task_id") val workTaskId: String? = null,
        @SerialName("operator_user_id") val operatorUserId: String? = null,
        @SerialName("operator_category_id") val operatorCategoryId: String? = null,
        @SerialName("client_updated_at") val clientUpdatedAt: String,
    )

    /** Live progress autosave while a trip is active. */
    @Serializable
    private data class TripProgressPatch(
        @SerialName("path_points") val pathPoints: List<CoordinatePoint>,
        @SerialName("total_distance") val totalDistance: Double,
        @SerialName("is_paused") val isPaused: Boolean,
        @SerialName("client_updated_at") val clientUpdatedAt: String,
    )

    /** Final patch when ending a trip. */
    @Serializable
    private data class TripEndPatch(
        @SerialName("is_active") val isActive: Boolean = false,
        @SerialName("is_paused") val isPaused: Boolean = false,
        @SerialName("end_time") val endTime: String,
        @SerialName("total_distance") val totalDistance: Double,
        @SerialName("path_points") val pathPoints: List<CoordinatePoint>,
        @SerialName("completion_notes") val completionNotes: String? = null,
        @SerialName("client_updated_at") val clientUpdatedAt: String,
    )

    @Serializable
    private data class SoftDeleteArgs(@SerialName("p_trip_id") val tripId: String)

    private fun nowIso(): String = Instant.now().toString()

    suspend fun createTrip(
        vineyardId: String,
        paddockId: String?,
        paddockName: String?,
        personName: String?,
        tripFunction: String?,
        tripTitle: String?,
        machineId: String? = null,
        workTaskId: String? = null,
        operatorUserId: String? = null,
        operatorCategoryId: String? = null,
    ): Trip = withContext(Dispatchers.IO) {
        requireConfig()
        val token = session.accessToken ?: throw BackendError.Unauthorized
        val now = nowIso()
        val body = TripInsert(
            id = UUID.randomUUID().toString(),
            vineyardId = vineyardId,
            paddockId = paddockId,
            paddockName = paddockName,
            startTime = now,
            isActive = true,
            personName = personName,
            tripFunction = tripFunction,
            tripTitle = tripTitle,
            machineId = machineId,
            workTaskId = workTaskId,
            operatorUserId = operatorUserId,
            operatorCategoryId = operatorCategoryId,
            createdBy = session.userId,
            clientUpdatedAt = now,
        )
        val response = SupabaseClient.http.post(SupabaseClient.restUrl("trips")) {
            authHeaders(token)
            headers { append("Prefer", "return=representation") }
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        firstRow(response)
    }

    suspend fun updateMetadata(
        id: String,
        paddockId: String?,
        paddockName: String?,
        personName: String?,
        tripFunction: String?,
        tripTitle: String?,
        machineId: String? = null,
        workTaskId: String? = null,
        operatorUserId: String? = null,
        operatorCategoryId: String? = null,
    ): Trip = withContext(Dispatchers.IO) {
        requireConfig()
        val token = session.accessToken ?: throw BackendError.Unauthorized
        val patch = TripMetadataPatch(
            paddockId = paddockId,
            paddockName = paddockName,
            personName = personName,
            tripFunction = tripFunction,
            tripTitle = tripTitle,
            machineId = machineId,
            workTaskId = workTaskId,
            operatorUserId = operatorUserId,
            operatorCategoryId = operatorCategoryId,
            clientUpdatedAt = nowIso(),
        )
        patchTrip(id, patch, token)
    }

    suspend fun saveProgress(
        id: String,
        pathPoints: List<CoordinatePoint>,
        totalDistance: Double,
        isPaused: Boolean,
    ): Trip = withContext(Dispatchers.IO) {
        requireConfig()
        val token = session.accessToken ?: throw BackendError.Unauthorized
        val patch = TripProgressPatch(
            pathPoints = pathPoints,
            totalDistance = totalDistance,
            isPaused = isPaused,
            clientUpdatedAt = nowIso(),
        )
        patchTrip(id, patch, token)
    }

    suspend fun endTrip(
        id: String,
        pathPoints: List<CoordinatePoint>,
        totalDistance: Double,
        completionNotes: String?,
    ): Trip = withContext(Dispatchers.IO) {
        requireConfig()
        val token = session.accessToken ?: throw BackendError.Unauthorized
        val now = nowIso()
        val patch = TripEndPatch(
            endTime = now,
            totalDistance = totalDistance,
            pathPoints = pathPoints,
            completionNotes = completionNotes,
            clientUpdatedAt = now,
        )
        patchTrip(id, patch, token)
    }

    suspend fun softDeleteTrip(id: String) = withContext(Dispatchers.IO) {
        requireConfig()
        val token = session.accessToken ?: throw BackendError.Unauthorized
        val response = SupabaseClient.http.post(SupabaseClient.rpcUrl("soft_delete_trip")) {
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

    private suspend inline fun <reified T> patchTrip(
        id: String,
        patch: T,
        token: String,
    ): Trip {
        val response = SupabaseClient.http.patch(SupabaseClient.restUrl("trips?id=eq.$id")) {
            authHeaders(token)
            headers { append("Prefer", "return=representation") }
            contentType(ContentType.Application.Json)
            setBody(patch)
        }
        return firstRow(response)
    }

    private suspend fun firstRow(response: io.ktor.client.statement.HttpResponse): Trip = when {
        response.status.isSuccess() -> response.body<List<Trip>>().firstOrNull()
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
