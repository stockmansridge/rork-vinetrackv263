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
    @SerialName("completed_paths") val completedPaths: List<Double>? = null,
    @SerialName("skipped_paths") val skippedPaths: List<Double>? = null,
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
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
) {
    /** Best human label for the pin, mirroring iOS button-name fallback. */
    val displayTitle: String
        get() = title?.takeIf { it.isNotBlank() }
            ?: buttonName?.takeIf { it.isNotBlank() }
            ?: category?.takeIf { it.isNotBlank() }
            ?: mode?.takeIf { it.isNotBlank() }
            ?: "Pin"
}
