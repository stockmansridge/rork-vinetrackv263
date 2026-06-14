package com.rork.vinetrack.data

import com.rork.vinetrack.data.auth.SessionStore
import com.rork.vinetrack.data.model.OperatorCategory
import com.rork.vinetrack.data.model.Paddock
import com.rork.vinetrack.data.model.Pin
import com.rork.vinetrack.data.model.GrapeVarietyRow
import com.rork.vinetrack.data.model.GrowthStageRecord
import com.rork.vinetrack.data.model.MaintenanceLog
import com.rork.vinetrack.data.model.SprayEquipment
import com.rork.vinetrack.data.model.SprayRecord
import com.rork.vinetrack.data.model.TractorFuelLog
import com.rork.vinetrack.data.model.Trip
import com.rork.vinetrack.data.model.Vineyard
import com.rork.vinetrack.data.model.VineyardMachine
import com.rork.vinetrack.data.model.VineyardMember
import com.rork.vinetrack.data.model.WorkTask
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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

    suspend fun listSprayRecords(vineyardId: String): List<SprayRecord> = withContext(Dispatchers.IO) {
        get("spray_records?select=*&vineyard_id=eq.$vineyardId&deleted_at=is.null&order=date.desc")
    }

    suspend fun listSprayEquipment(vineyardId: String): List<SprayEquipment> = withContext(Dispatchers.IO) {
        get("spray_equipment?select=*&vineyard_id=eq.$vineyardId&deleted_at=is.null&order=name.asc")
    }

    suspend fun listMaintenanceLogs(vineyardId: String): List<MaintenanceLog> = withContext(Dispatchers.IO) {
        get("maintenance_logs?select=*&vineyard_id=eq.$vineyardId&deleted_at=is.null&order=date.desc")
    }

    suspend fun listGrowthStageRecords(vineyardId: String): List<GrowthStageRecord> = withContext(Dispatchers.IO) {
        get("growth_stage_records?select=*&vineyard_id=eq.$vineyardId&deleted_at=is.null&order=observed_at.desc")
    }

    suspend fun listFuelLogs(vineyardId: String): List<TractorFuelLog> = withContext(Dispatchers.IO) {
        get("tractor_fuel_logs?select=*&vineyard_id=eq.$vineyardId&deleted_at=is.null&order=fill_datetime.desc")
    }

    suspend fun listOperatorCategories(vineyardId: String): List<OperatorCategory> = withContext(Dispatchers.IO) {
        get("operator_categories?select=*&vineyard_id=eq.$vineyardId&deleted_at=is.null&order=name.asc")
    }

    /**
     * Loads the active vineyard's team members via the SECURITY DEFINER
     * `get_vineyard_team_members` RPC, which resolves display names + each
     * member's default operator category without weakening profiles RLS
     * (sql/022 + sql/082). Mirrors the iOS `SupabaseTeamRepository.listMembers`.
     */
    suspend fun listTeamMembers(vineyardId: String): List<VineyardMember> = withContext(Dispatchers.IO) {
        if (!SupabaseClient.isConfigured) throw BackendError.NotConfigured
        val token = session.accessToken ?: throw BackendError.Unauthorized
        val response = SupabaseClient.http.post(SupabaseClient.rpcUrl("get_vineyard_team_members")) {
            headers {
                append("apikey", SupabaseClient.anonKey)
                append("Authorization", "Bearer $token")
            }
            contentType(ContentType.Application.Json)
            setBody(VineyardIdArg(vineyardId))
        }
        when {
            response.status.isSuccess() -> response.body()
            response.status.value == 401 || response.status.value == 403 -> throw BackendError.Unauthorized
            else -> throw BackendError.Server(response.status.value, "")
        }
    }

    @Serializable
    private data class VineyardIdArg(@SerialName("p_vineyard_id") val vineyardId: String)

    /**
     * Lists the vineyard's grape variety catalog selections via the
     * SECURITY DEFINER `list_vineyard_grape_varieties` RPC (sql/073). Returns
     * built-in selections + custom varieties; any vineyard member may read.
     * Mirrors the iOS `SupabaseGrapeVarietyCatalogRepository.listVineyardVarieties`.
     */
    suspend fun listGrapeVarieties(vineyardId: String): List<GrapeVarietyRow> = withContext(Dispatchers.IO) {
        if (!SupabaseClient.isConfigured) throw BackendError.NotConfigured
        val token = session.accessToken ?: throw BackendError.Unauthorized
        val response = SupabaseClient.http.post(SupabaseClient.rpcUrl("list_vineyard_grape_varieties")) {
            headers {
                append("apikey", SupabaseClient.anonKey)
                append("Authorization", "Bearer $token")
            }
            contentType(ContentType.Application.Json)
            setBody(VineyardIdArg(vineyardId))
        }
        when {
            response.status.isSuccess() -> response.body()
            response.status.value == 401 || response.status.value == 403 -> throw BackendError.Unauthorized
            else -> throw BackendError.Server(response.status.value, "")
        }
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
