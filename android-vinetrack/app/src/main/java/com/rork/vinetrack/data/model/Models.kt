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
 * A work task — backs `public.work_tasks`. Trips optionally group under one
 * work task via `trips.work_task_id` (see sql/102_trips_work_task_link.sql).
 */
@Serializable
data class WorkTask(
    val id: String,
    @SerialName("vineyard_id") val vineyardId: String,
    @SerialName("paddock_id") val paddockId: String? = null,
    @SerialName("paddock_name") val paddockName: String? = null,
    val date: String? = null,
    @SerialName("task_type") val taskType: String? = null,
    val notes: String? = null,
    @SerialName("is_archived") val isArchived: Boolean = false,
    @SerialName("deleted_at") val deletedAt: String? = null,
) {
    val startEpochMs: Long? get() = parseIsoToEpochMs(date)

    /** User-facing label, mirroring the iOS work-task naming. */
    val displayLabel: String
        get() = taskType?.takeIf { it.isNotBlank() }
            ?: paddockName?.takeIf { it.isNotBlank() }
            ?: "Work task"
}

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
