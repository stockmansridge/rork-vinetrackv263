package com.rork.vinetrack.data

import com.rork.vinetrack.data.auth.SessionStore
import com.rork.vinetrack.data.model.Paddock
import com.rork.vinetrack.data.model.Pin
import com.rork.vinetrack.data.model.Trip
import com.rork.vinetrack.data.model.Vineyard
import com.rork.vinetrack.data.model.VineyardMachine
import com.rork.vinetrack.data.model.WorkTask
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads vineyard-scoped data from the Supabase PostgREST API, mirroring the
 * iOS `SupabaseVineyardRepository` and related repositories. RLS on the server
 * scopes every query to the signed-in user's memberships.
 */
class VineyardRepository(private val session: SessionStore) {

    suspend fun listMyVineyards(): List<Vineyard> = withContext(Dispatchers.IO) {
        get("vineyards?select=*&deleted_at=is.null&order=name.asc")
    }

    suspend fun listPaddocks(vineyardId: String): List<Paddock> = withContext(Dispatchers.IO) {
        get("paddocks?select=*&vineyard_id=eq.$vineyardId&deleted_at=is.null&order=name.asc")
    }

    suspend fun listPins(vineyardId: String): List<Pin> = withContext(Dispatchers.IO) {
        get("pins?select=*&vineyard_id=eq.$vineyardId&deleted_at=is.null&order=created_at.desc")
    }

    suspend fun listTrips(vineyardId: String): List<Trip> = withContext(Dispatchers.IO) {
        get("trips?select=*&vineyard_id=eq.$vineyardId&deleted_at=is.null&order=start_time.desc")
    }

    suspend fun listMachines(vineyardId: String): List<VineyardMachine> = withContext(Dispatchers.IO) {
        get("vineyard_machines?select=*&vineyard_id=eq.$vineyardId&deleted_at=is.null&order=name.asc")
    }

    suspend fun listWorkTasks(vineyardId: String): List<WorkTask> = withContext(Dispatchers.IO) {
        get("work_tasks?select=*&vineyard_id=eq.$vineyardId&deleted_at=is.null&is_archived=eq.false&order=date.desc")
    }

    private suspend inline fun <reified T> get(path: String): T {
        if (!SupabaseClient.isConfigured) throw BackendError.NotConfigured
        val token = session.accessToken ?: throw BackendError.Unauthorized
        val response = SupabaseClient.http.get(SupabaseClient.restUrl(path)) {
            headers {
                append("apikey", SupabaseClient.anonKey)
                append("Authorization", "Bearer $token")
            }
        }
        when {
            response.status.isSuccess() -> return response.body()
            response.status.value == 401 || response.status.value == 403 -> throw BackendError.Unauthorized
            else -> throw BackendError.Server(response.status.value, "")
        }
    }
}
