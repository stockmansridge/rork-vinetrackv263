package com.rork.vinetrack.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

@Serializable
data class AppUser(
    val id: String,
    val email: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class Vineyard(
    val id: String,
    val name: String,
    @SerialName("owner_id") val ownerId: String? = null,
    val country: String? = null,
    @SerialName("logo_path") val logoPath: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("elevation_metres") val elevationMetres: Double? = null,
    val timezone: String? = null,
)

@Serializable
data class CoordinatePoint(
    val latitude: Double,
    val longitude: Double,
)

@Serializable
data class PaddockRow(
    val number: Int = 0,
    val startPoint: CoordinatePoint? = null,
    val endPoint: CoordinatePoint? = null,
)

/**
 * A single variety allocation on a block. Tolerant of the various key names
 * written by iOS, the Lovable web portal, and legacy rows — mirrors the
 * iOS `PaddockVarietyAllocation` decoder. Clone and rootstock are
 * reference-only display fields.
 */
@Serializable
data class PaddockVarietyAllocation(
    val varietyKey: String? = null,
    val varietyId: String? = null,
    val name: String? = null,
    val varietyName: String? = null,
    val percentage: Double? = null,
    val percent: Double? = null,
    val clone: String? = null,
    val rootstock: String? = null,
) {
    val displayPercent: Double? get() = percent ?: percentage
    val displayName: String?
        get() = name?.takeIf { it.isNotBlank() }
            ?: varietyName?.takeIf { it.isNotBlank() }
}

@Serializable
data class Paddock(
    val id: String,
    @SerialName("vineyard_id") val vineyardId: String,
    val name: String,
    @SerialName("row_direction") val rowDirection: Double? = null,
    @SerialName("row_width") val rowWidth: Double? = null,
    @SerialName("row_offset") val rowOffset: Double? = null,
    @SerialName("vine_spacing") val vineSpacing: Double? = null,
    @SerialName("vine_count_override") val vineCountOverride: Int? = null,
    @SerialName("row_length_override") val rowLengthOverride: Double? = null,
    @SerialName("flow_per_emitter") val flowPerEmitter: Double? = null,
    @SerialName("emitter_spacing") val emitterSpacing: Double? = null,
    @SerialName("intermediate_post_spacing") val intermediatePostSpacing: Double? = null,
    @SerialName("planting_year") val plantingYear: Int? = null,
    @SerialName("polygon_points") val polygonPoints: List<CoordinatePoint>? = null,
    val rows: List<PaddockRow>? = null,
    @SerialName("variety_allocations") val varietyAllocations: List<PaddockVarietyAllocation>? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
) {
    /** Polygon area in hectares (equirectangular projection — matches iOS `areaHectares`). */
    val areaHectares: Double
        get() {
            val points = polygonPoints ?: return 0.0
            if (points.size < 3) return 0.0
            val centroidLat = points.sumOf { it.latitude } / points.size
            val mPerDegLat = 111_320.0
            val mPerDegLon = 111_320.0 * cos(centroidLat * Math.PI / 180.0)
            var area = 0.0
            val n = points.size
            for (i in 0 until n) {
                val j = (i + 1) % n
                val xi = points[i].longitude * mPerDegLon
                val yi = points[i].latitude * mPerDegLat
                val xj = points[j].longitude * mPerDegLon
                val yj = points[j].latitude * mPerDegLat
                area += xi * yj - xj * yi
            }
            return abs(area) / 2.0 / 10_000.0
        }

    /** True when the block has a mapped boundary polygon. */
    val hasGeometry: Boolean get() = (polygonPoints?.size ?: 0) >= 3

    /** True when individual rows have been laid out. */
    val hasRows: Boolean get() = !rows.isNullOrEmpty()

    val rowCount: Int get() = rows?.size ?: 0

    /** Total row length in metres, summed across mapped rows (matches iOS). */
    val totalRowLengthMetres: Double
        get() {
            val rs = rows ?: return 0.0
            if (rs.isEmpty()) return 0.0
            val pts = polygonPoints ?: emptyList()
            val centroidLat = if (pts.isEmpty()) 0.0 else pts.sumOf { it.latitude } / pts.size
            val mPerDegLat = 111_320.0
            val mPerDegLon = 111_320.0 * cos(centroidLat * Math.PI / 180.0)
            return rs.sumOf { row ->
                val s = row.startPoint
                val e = row.endPoint
                if (s == null || e == null) return@sumOf 0.0
                val dLat = (e.latitude - s.latitude) * mPerDegLat
                val dLon = (e.longitude - s.longitude) * mPerDegLon
                sqrt(dLat * dLat + dLon * dLon)
            }
        }

    val effectiveTotalRowLength: Double get() = rowLengthOverride ?: totalRowLengthMetres

    private val estimatedVineCount: Int
        get() {
            val spacing = vineSpacing ?: return 0
            if (spacing <= 0) return 0
            return (effectiveTotalRowLength / spacing).toInt()
        }

    /** Vine count: explicit override if set, otherwise derived from rows × spacing. */
    val effectiveVineCount: Int get() = vineCountOverride ?: estimatedVineCount

    /** Litres per hectare per hour for the configured drip setup, or null. */
    val litresPerHaPerHour: Double?
        get() {
            val flow = flowPerEmitter ?: return null
            val spacing = emitterSpacing ?: return null
            val width = rowWidth ?: return null
            if (spacing <= 0 || width <= 0) return null
            val emittersPerHa = 10_000.0 / (width * spacing)
            return emittersPerHa * flow
        }

    /** Effective application rate in mm/hour, or null when drip isn't configured. */
    val mmPerHour: Double?
        get() {
            val litres = litresPerHaPerHour ?: return null
            return litres / 1_000_000.0 * 100.0
        }

    val hasIrrigationSetup: Boolean
        get() = (flowPerEmitter ?: 0.0) > 0 && (emitterSpacing ?: 0.0) > 0
}

@Serializable
data class Trip(
    val id: String,
    @SerialName("vineyard_id") val vineyardId: String,
    @SerialName("paddock_id") val paddockId: String? = null,
    @SerialName("paddock_name") val paddockName: String? = null,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("end_time") val endTime: String? = null,
    @SerialName("is_active") val isActive: Boolean = false,
    @SerialName("is_paused") val isPaused: Boolean = false,
    @SerialName("total_distance") val totalDistance: Double? = null,
    @SerialName("person_name") val personName: String? = null,
    @SerialName("trip_function") val tripFunction: String? = null,
    @SerialName("trip_title") val tripTitle: String? = null,
    @SerialName("machine_id") val machineId: String? = null,
    @SerialName("tractor_id") val tractorId: String? = null,
    @SerialName("operator_user_id") val operatorUserId: String? = null,
    @SerialName("operator_category_id") val operatorCategoryId: String? = null,
    @SerialName("completed_paths") val completedPaths: List<Double>? = null,
    @SerialName("skipped_paths") val skippedPaths: List<Double>? = null,
    @SerialName("path_points") val pathPoints: List<CoordinatePoint>? = null,
    @SerialName("total_tanks") val totalTanks: Int? = null,
    @SerialName("completion_notes") val completionNotes: String? = null,
    @SerialName("work_task_id") val workTaskId: String? = null,
    @SerialName("pause_timestamps") val pauseTimestamps: List<String>? = null,
    @SerialName("resume_timestamps") val resumeTimestamps: List<String>? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
) {
    /** User-facing label, mirroring iOS `Trip.displayFunctionLabel`. */
    val displayLabel: String
        get() {
            tripTitle?.takeIf { it.isNotBlank() }?.let { return it }
            val raw = tripFunction
            if (!raw.isNullOrBlank()) {
                tripFunctionDisplayName(raw)?.let { return it }
                if (raw.startsWith("custom:")) {
                    val slug = raw.removePrefix("custom:")
                    if (slug.isNotBlank()) {
                        return slug.replace("-", " ")
                            .split(" ")
                            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                    }
                }
            }
            return "Trip"
        }

    val startEpochMs: Long? get() = parseIsoToEpochMs(startTime)
    val endEpochMs: Long? get() = parseIsoToEpochMs(endTime)

    /** Number of rows recorded as completed during this trip. */
    val completedRowCount: Int get() = completedPaths?.size ?: 0

    /**
     * Active duration in seconds, excluding paused intervals — mirrors the iOS
     * `Trip.activeDuration` calculation. Returns null when the trip has no end
     * time (i.e. still active) and no start, otherwise measures to now.
     */
    val activeDurationSeconds: Long?
        get() {
            val start = startEpochMs ?: return null
            val end = endEpochMs ?: System.currentTimeMillis()
            val pauses = pauseTimestamps.orEmpty().mapNotNull { parseIsoToEpochMs(it) }
            val resumes = resumeTimestamps.orEmpty().mapNotNull { parseIsoToEpochMs(it) }
            var total = 0L
            var lastStart = start
            for (i in pauses.indices) {
                total += pauses[i] - lastStart
                if (i < resumes.size) {
                    lastStart = resumes[i]
                } else {
                    return total / 1000
                }
            }
            total += end - lastStart
            return total / 1000
        }
}

/**
 * Built-in operation types offered when starting a trip, as (rawValue, label)
 * pairs. Raw values match the iOS `TripFunction` enum so the `trip_function`
 * column stays stable across platforms.
 */
val builtInTripFunctions: List<Pair<String, String>> = listOf(
    "slashing" to "Slashing",
    "mulching" to "Mulching",
    "mowing" to "Mowing",
    "spraying" to "Spraying",
    "fertilising" to "Fertilising",
    "undervineWeeding" to "Undervine weeding",
    "interRowCultivation" to "Inter-row cultivation",
    "pruning" to "Pruning",
    "shootThinning" to "Shoot thinning",
    "canopyWork" to "Canopy work",
    "harrowing" to "Harrowing",
    "irrigationCheck" to "Irrigation check",
    "repairs" to "Repairs",
    "seeding" to "Seeding",
    "spreading" to "Spreading",
    "other" to "Other",
)

/**
 * Friendly elapsed-duration string. Always uses "min" (never "m") and omits
 * the minutes component on whole hours — mirrors the iOS shared
 * `RegionFormatter.formatDuration`.
 */
fun formatTripDuration(seconds: Long): String {
    val safe = if (seconds > 0) seconds else 0
    val totalMinutes = ((safe + 30) / 60)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "$hours h $minutes min"
        hours > 0 -> "$hours h"
        else -> "$minutes min"
    }
}

/** Maps a stored `trip_function` raw value to its display name (mirrors iOS `TripFunction`). */
fun tripFunctionDisplayName(raw: String): String? = when (raw) {
    "slashing" -> "Slashing"
    "mulching" -> "Mulching"
    "harrowing" -> "Harrowing"
    "mowing" -> "Mowing"
    "spraying" -> "Spraying"
    "fertilising" -> "Fertilising"
    "undervineWeeding" -> "Undervine weeding"
    "undervineMowing" -> "Mowing"
    "undervineMulticlean" -> "Multiclean"
    "undervineRollHacke" -> "Roll Hacke"
    "undervineDisc" -> "Undervine Disc"
    "undervineKnifing" -> "Undervine Knifing"
    "interRowCultivation" -> "Inter-row cultivation"
    "pruning" -> "Pruning"
    "shootThinning" -> "Shoot thinning"
    "canopyWork" -> "Canopy work"
    "irrigationCheck" -> "Irrigation check"
    "repairs" -> "Repairs"
    "seeding" -> "Seeding"
    "spreading" -> "Spreading"
    "other" -> "Other"
    else -> null
}

/**
 * Parse an ISO-8601 / PostgREST timestamp string to epoch millis. Tolerant of
 * the `+00:00`, `Z`, and fractional-second variants Supabase returns.
 */
fun parseIsoToEpochMs(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return try {
        java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli()
    } catch (_: Exception) {
        try {
            java.time.Instant.parse(value).toEpochMilli()
        } catch (_: Exception) {
            try {
                java.time.LocalDateTime.parse(value)
                    .toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
    }
}

/**
 * A vineyard machine (tractor, ATV, harvester, etc.) — backs
 * `public.vineyard_machines`. Tractors are backfilled into this table with
 * `machine_type = 'tractor'` and a `legacy_tractor_id`, so loading machines
 * alone resolves both the preferred `machine_id` and the legacy `tractor_id`
 * trip links (mirrors the iOS `EquipmentResolver`).
 */
@Serializable
data class VineyardMachine(
    val id: String,
    @SerialName("vineyard_id") val vineyardId: String,
    val name: String = "",
    @SerialName("machine_type") val machineType: String? = null,
    @SerialName("fuel_usage_l_per_hour") val fuelUsageLPerHour: Double? = null,
    @SerialName("legacy_tractor_id") val legacyTractorId: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
) {
    /** Display name, falling back to the machine-type label when unnamed. */
    val displayName: String
        get() = name.trim().takeIf { it.isNotBlank() } ?: machineTypeLabel(machineType)
}

/** Maps a `machine_type` raw value to its display label (mirrors iOS `VineyardMachineType`). */
fun machineTypeLabel(raw: String?): String = when (raw) {
    "tractor" -> "Tractor"
    "atv" -> "ATV"
    "side_by_side" -> "Side-by-side"
    "harvester" -> "Harvester"
    "utility_vehicle" -> "Utility vehicle"
    "other_vineyard_machine" -> "Other vineyard machine"
    else -> "Machine"
}

/**
 * A work task — backs `public.work_tasks` (sql/014). Trips optionally group
 * under one work task via `trips.work_task_id` (sql/102). The `is_finalized`
 * flag is the iOS completion convention; `is_archived` hides the task from the
 * default list. Android writes only the columns it edits, leaving the
 * iOS-managed `resources` JSONB and Phase-16 costing fields untouched.
 */
@Serializable
data class WorkTask(
    val id: String,
    @SerialName("vineyard_id") val vineyardId: String,
    @SerialName("paddock_id") val paddockId: String? = null,
    @SerialName("paddock_name") val paddockName: String? = null,
    val date: String? = null,
    @SerialName("task_type") val taskType: String? = null,
    @SerialName("duration_hours") val durationHours: Double = 0.0,
    val notes: String? = null,
    val status: String? = null,
    @SerialName("is_archived") val isArchived: Boolean = false,
    @SerialName("is_finalized") val isFinalized: Boolean = false,
    @SerialName("finalized_at") val finalizedAt: String? = null,
    @SerialName("finalized_by") val finalizedBy: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
) {
    val startEpochMs: Long? get() = parseIsoToEpochMs(date)
    val finalizedEpochMs: Long? get() = parseIsoToEpochMs(finalizedAt)

    /** Completion state — mirrors the iOS `isFinalized` convention. */
    val isComplete: Boolean get() = isFinalized

    /** User-facing label, mirroring the iOS work-task naming. */
    val displayLabel: String
        get() = taskType?.takeIf { it.isNotBlank() }
            ?: paddockName?.takeIf { it.isNotBlank() }
            ?: "Work task"
}

/**
 * Built-in work-task types offered when logging a task. Mirrors the iOS
 * `WorkTaskTypeCatalog.defaults` so the `task_type` text column stays
 * consistent across platforms.
 */
val builtInWorkTaskTypes: List<String> = listOf(
    "Pruning",
    "Cane Tying",
    "Shoot Thinning",
    "Leaf Plucking",
    "Canopy Trimming",
    "Wire Lifting",
    "Bud Rubbing",
    "Weed Control",
    "Mowing",
    "Irrigation Check",
    "Harvest",
    "Planting",
    "Training",
    "Bird Netting",
    "Other",
)

/**
 * Resolve a trip's linked equipment display name, mirroring the iOS
 * `EquipmentResolver.tripMachineName`: prefer the linked `machine_id`, then a
 * machine backfilled from the legacy `tractor_id`. Returns null when no link
 * resolves so the UI can show a friendly fallback.
 */
fun resolveTripMachineName(trip: Trip, machines: List<VineyardMachine>): String? {
    trip.machineId?.let { mid ->
        machines.firstOrNull { it.id == mid }?.let { return it.displayName }
    }
    trip.tractorId?.let { tid ->
        machines.firstOrNull { it.legacyTractorId == tid && it.vineyardId == trip.vineyardId }
            ?.let { return it.displayName }
    }
    return null
}

/** Resolve the work task a trip is grouped under, or null when unlinked/unavailable. */
fun resolveTripWorkTask(trip: Trip, workTasks: List<WorkTask>): WorkTask? =
    trip.workTaskId?.let { id -> workTasks.firstOrNull { it.id == id } }

/**
 * A vineyard team member, decoded from the `get_vineyard_team_members` RPC
 * (sql/022 + sql/082). The RPC resolves a display-safe name plus the member's
 * default operator category without weakening profiles RLS. Trips link to a
 * member via `trips.operator_user_id` -> `vineyard_members.user_id`.
 */
@Serializable
data class VineyardMember(
    @SerialName("membership_id") val membershipId: String? = null,
    @SerialName("vineyard_id") val vineyardId: String? = null,
    @SerialName("user_id") val userId: String,
    val role: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    val email: String? = null,
    @SerialName("operator_category_id") val operatorCategoryId: String? = null,
    @SerialName("operator_category_name") val operatorCategoryName: String? = null,
) {
    /** Best human label, mirroring the RPC's coalesced fallback chain. */
    val name: String
        get() = displayName?.takeIf { it.isNotBlank() }
            ?: fullName?.takeIf { it.isNotBlank() }
            ?: email?.takeIf { it.isNotBlank() }
            ?: "User " + userId.take(8)
}

/**
 * A vineyard operator/labour cost category — backs `public.operator_categories`
 * (sql/011). Trips optionally link one via `trips.operator_category_id`.
 */
@Serializable
data class OperatorCategory(
    val id: String,
    @SerialName("vineyard_id") val vineyardId: String,
    val name: String = "",
    @SerialName("cost_per_hour") val costPerHour: Double? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
) {
    val displayName: String get() = name.trim().takeIf { it.isNotBlank() } ?: "Operator category"
}

/**
 * Resolve a trip's linked operator display name. Prefers the linked team member
 * (`operator_user_id`), then falls back to the free-text `person_name` snapshot
 * for legacy rows or members who have since left the team. Returns null only
 * when nothing resolves so the UI can show a friendly placeholder.
 */
fun resolveTripOperatorName(trip: Trip, members: List<VineyardMember>): String? {
    trip.operatorUserId?.let { uid ->
        members.firstOrNull { it.userId == uid }?.let { return it.name }
    }
    return trip.personName?.takeIf { it.isNotBlank() }
}

/** Resolve a trip's linked operator category, or null when unlinked/unavailable. */
fun resolveTripOperatorCategory(trip: Trip, categories: List<OperatorCategory>): OperatorCategory? =
    trip.operatorCategoryId?.let { id -> categories.firstOrNull { it.id == id } }

@Serializable
data class Pin(
    val id: String,
    @SerialName("vineyard_id") val vineyardId: String,
    @SerialName("paddock_id") val paddockId: String? = null,
    val title: String? = null,
    val category: String? = null,
    @SerialName("button_name") val buttonName: String? = null,
    val mode: String? = null,
    val notes: String? = null,
    @SerialName("is_completed") val isCompleted: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("photo_path") val photoPath: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
) {
    /** True when this pin has a synced photo in the `vineyard-pin-photos` bucket. */
    val hasPhoto: Boolean get() = !photoPath.isNullOrBlank()

    /** Best human label for the pin, mirroring iOS button-name fallback. */
    val displayTitle: String
        get() = title?.takeIf { it.isNotBlank() }
            ?: buttonName?.takeIf { it.isNotBlank() }
            ?: category?.takeIf { it.isNotBlank() }
            ?: mode?.takeIf { it.isNotBlank() }
            ?: "Pin"
}

/**
 * A per-day, per-worker-type labour entry for a work task — backs
 * `public.work_task_labour_lines` (sql/050). `total_hours` and `total_cost` are
 * database-generated columns, so Android only writes the inputs and reads the
 * computed totals back. Soft-deleted via the `soft_delete_work_task_labour_line`
 * RPC (owner/manager/supervisor only); inserts/updates follow membership RLS.
 */
@Serializable
data class WorkTaskLabourLine(
    val id: String,
    @SerialName("work_task_id") val workTaskId: String,
    @SerialName("vineyard_id") val vineyardId: String,
    @SerialName("work_date") val workDate: String? = null,
    @SerialName("operator_category_id") val operatorCategoryId: String? = null,
    @SerialName("worker_type") val workerType: String = "",
    @SerialName("worker_count") val workerCount: Int = 1,
    @SerialName("hours_per_worker") val hoursPerWorker: Double = 0.0,
    @SerialName("hourly_rate") val hourlyRate: Double? = null,
    @SerialName("total_hours") val totalHours: Double? = null,
    @SerialName("total_cost") val totalCost: Double? = null,
    val notes: String = "",
    @SerialName("deleted_at") val deletedAt: String? = null,
) {
    /** Computed hours, falling back to the local product when the server value is absent. */
    val resolvedHours: Double get() = totalHours ?: (workerCount.toDouble() * hoursPerWorker)

    /** Computed cost, falling back to the local product when the server value is absent. */
    val resolvedCost: Double get() = totalCost ?: (resolvedHours * (hourlyRate ?: 0.0))
}

/**
 * A manual machine/equipment work entry for a work task — backs
 * `public.work_task_machine_lines` (sql/103). Equipment identity uses the
 * migration-safe `equipment_source` + `equipment_ref_id` + `equipment_name_snapshot`
 * pattern, where the snapshot is the authoritative display value when no stable
 * link resolves. Soft-deleted via `soft_delete_work_task_machine_line`.
 */
@Serializable
data class WorkTaskMachineLine(
    val id: String,
    @SerialName("work_task_id") val workTaskId: String,
    @SerialName("vineyard_id") val vineyardId: String,
    @SerialName("work_date") val workDate: String? = null,
    @SerialName("equipment_source") val equipmentSource: String? = null,
    @SerialName("equipment_ref_id") val equipmentRefId: String? = null,
    @SerialName("equipment_name_snapshot") val equipmentNameSnapshot: String = "",
    @SerialName("operator_user_id") val operatorUserId: String? = null,
    @SerialName("operator_category_id") val operatorCategoryId: String? = null,
    @SerialName("duration_hours") val durationHours: Double? = null,
    @SerialName("fuel_litres") val fuelLitres: Double? = null,
    @SerialName("fuel_cost") val fuelCost: Double? = null,
    @SerialName("hourly_machine_rate") val hourlyMachineRate: Double? = null,
    @SerialName("total_machine_cost") val totalMachineCost: Double? = null,
    @SerialName("entry_source") val entrySource: String = "manual",
    val notes: String = "",
    @SerialName("deleted_at") val deletedAt: String? = null,
) {
    /**
     * Computed machine cost. Prefers the explicit `total_machine_cost`, else
     * derives `duration * hourly_rate + fuel_cost` so the roll-up still works
     * when only the inputs were entered.
     */
    val resolvedCost: Double
        get() = totalMachineCost
            ?: ((durationHours ?: 0.0) * (hourlyMachineRate ?: 0.0) + (fuelCost ?: 0.0))

    /**
     * Best display name for the linked equipment. Prefers a live match against
     * the loaded machines (handling deleted/renamed equipment) and falls back
     * to the stored snapshot or a friendly placeholder.
     */
    fun displayEquipment(machines: List<VineyardMachine>): String {
        equipmentRefId?.let { ref ->
            machines.firstOrNull { it.id == ref }?.let { return it.displayName }
        }
        return equipmentNameSnapshot.trim().takeIf { it.isNotBlank() } ?: "Machine"
    }
}

/**
 * A single chemical line within a spray tank mix. Serialized inside the
 * `spray_records.tanks` JSONB array, so the property names must stay camelCase
 * to match the iOS `SprayChemical` Codable keys exactly. `unit` is the
 * `ChemicalUnit` raw value ("Litres", "mL", "Kg", "g").
 */
@Serializable
data class SprayChemical(
    val id: String,
    val name: String = "",
    val volumePerTank: Double = 0.0,
    val ratePerHa: Double = 0.0,
    val ratePer100L: Double = 0.0,
    val costPerUnit: Double = 0.0,
    val unit: String = "Litres",
    val savedChemicalId: String? = null,
) {
    val costPerTank: Double get() = costPerUnit * volumePerTank
}

/**
 * A row-range a tank was applied across — part of the iOS `SprayTank` JSONB
 * shape. Android doesn't edit these yet but preserves them on round-trip.
 */
@Serializable
data class TankRowApplication(
    val id: String,
    val startRow: Double = 0.5,
    val endRow: Double = 0.5,
)

/**
 * A single tank within a spray record. Serialized inside the
 * `spray_records.tanks` JSONB array; property names mirror the iOS `SprayTank`
 * Codable keys (camelCase) so records round-trip cleanly across platforms.
 */
@Serializable
data class SprayTank(
    val id: String,
    val tankNumber: Int = 1,
    val waterVolume: Double = 0.0,
    val sprayRatePerHa: Double = 0.0,
    val concentrationFactor: Double = 0.0,
    val rowApplications: List<TankRowApplication> = emptyList(),
    val chemicals: List<SprayChemical> = emptyList(),
) {
    private val effectiveConcentrationFactor: Double
        get() = if (concentrationFactor > 0) concentrationFactor else 1.0

    /** Hectares this tank covers, mirroring the iOS `areaPerTank` calc. */
    val areaPerTank: Double
        get() = if (sprayRatePerHa > 0) (waterVolume * effectiveConcentrationFactor) / sprayRatePerHa else 0.0
}

/**
 * A completed spray application/compliance record — backs
 * `public.spray_records` (sql/007). Soft-deleted via the
 * `soft_delete_spray_record` RPC (owner/manager/supervisor only); inserts and
 * updates follow membership RLS. The `tanks` JSONB holds the full tank-mix
 * breakdown. Equipment uses the migration-safe `tractor` text snapshot plus
 * optional `machine_id`/`tractor_id` links, exactly like iOS.
 */
@Serializable
data class SprayRecord(
    val id: String,
    @SerialName("vineyard_id") val vineyardId: String,
    @SerialName("trip_id") val tripId: String? = null,
    val date: String? = null,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("end_time") val endTime: String? = null,
    val temperature: Double? = null,
    @SerialName("wind_speed") val windSpeed: Double? = null,
    @SerialName("wind_direction") val windDirection: String? = null,
    val humidity: Double? = null,
    @SerialName("spray_reference") val sprayReference: String? = null,
    val notes: String? = null,
    @SerialName("number_of_fans_jets") val numberOfFansJets: String? = null,
    @SerialName("average_speed") val averageSpeed: Double? = null,
    @SerialName("equipment_type") val equipmentType: String? = null,
    val tractor: String? = null,
    @SerialName("tractor_gear") val tractorGear: String? = null,
    @SerialName("machine_id") val machineId: String? = null,
    @SerialName("tractor_id") val tractorId: String? = null,
    @SerialName("spray_equipment_id") val sprayEquipmentId: String? = null,
    @SerialName("is_template") val isTemplate: Boolean = false,
    @SerialName("operation_type") val operationType: String? = null,
    val tanks: List<SprayTank>? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
) {
    val dateEpochMs: Long? get() = parseIsoToEpochMs(date ?: startTime)

    /** User-facing label: the spray reference, else operation type, else a fallback. */
    val displayLabel: String
        get() = sprayReference?.takeIf { it.isNotBlank() }
            ?: operationType?.takeIf { it.isNotBlank() }
            ?: "Spray record"

    val tankCount: Int get() = tanks?.size ?: 0

    /** Total water volume across all tanks (litres). */
    val totalWaterVolume: Double get() = tanks.orEmpty().sumOf { it.waterVolume }

    /** Distinct chemical names used across all tanks, in encounter order. */
    val chemicalNames: List<String>
        get() = tanks.orEmpty()
            .flatMap { it.chemicals }
            .mapNotNull { it.name.trim().takeIf { n -> n.isNotBlank() } }
            .distinct()

    /**
     * Best display name for the linked machine/tractor. Prefers the authoritative
     * `tractor` text snapshot (matching iOS), then a live machine lookup.
     */
    fun displayMachine(machines: List<VineyardMachine>): String? {
        tractor?.takeIf { it.isNotBlank() }?.let { return it }
        machineId?.let { mid -> machines.firstOrNull { it.id == mid }?.let { return it.displayName } }
        tractorId?.let { tid ->
            machines.firstOrNull { it.legacyTractorId == tid && it.vineyardId == vineyardId }?.let { return it.displayName }
        }
        return null
    }
}

/**
 * Resolve the trip a spray record was logged against, or null when unlinked or
 * the linked trip is unavailable (e.g. deleted). Mirrors the iOS lookup
 * `store.trips.first { $0.id == record.tripId }`.
 */
fun resolveSprayTrip(record: SprayRecord, trips: List<Trip>): Trip? =
    record.tripId?.let { id -> trips.firstOrNull { it.id == id } }

/**
 * Resolve the work task a spray record relates to. `spray_records` has no
 * `work_task_id` column, so — like iOS — the task is derived through the
 * record's linked trip (`trip.work_task_id`). Returns null when there is no
 * linked trip or the trip carries no work-task link.
 */
fun resolveSprayWorkTask(record: SprayRecord, trips: List<Trip>, workTasks: List<WorkTask>): WorkTask? =
    resolveSprayTrip(record, trips)?.let { trip -> resolveTripWorkTask(trip, workTasks) }

/** Built-in spray operation types — raw values match the iOS `OperationType` enum. */
val sprayOperationTypes: List<String> = listOf(
    "Foliar Spray",
    "Banded Spray",
    "Spreader",
)

/** Compass wind-direction options, mirroring the iOS `WindDirection` enum. */
val windDirectionOptions: List<String> = listOf(
    "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
    "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
)
