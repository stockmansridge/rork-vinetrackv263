package com.rork.vinetrack.data.model

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A single forecast day used by the irrigation calculator. Mirrors the iOS
 * `ForecastDay` contract (Open-Meteo `et0_fao_evapotranspiration` +
 * `precipitation_sum`).
 */
data class ForecastDay(
    val dateEpochMs: Long,
    val forecastEToMm: Double,
    val forecastRainMm: Double,
)

/**
 * Tunable irrigation parameters. Mirrors the iOS `IrrigationSettings` defaults.
 * Calculator-only — these are not persisted to the backend.
 */
data class IrrigationSettings(
    val irrigationApplicationRateMmPerHour: Double,
    val cropCoefficientKc: Double = 0.65,
    val irrigationEfficiencyPercent: Double = 90.0,
    val rainfallEffectivenessPercent: Double = 80.0,
    val replacementPercent: Double = 100.0,
    val soilMoistureBufferMm: Double = 0.0,
) {
    companion object {
        val defaults = IrrigationSettings(irrigationApplicationRateMmPerHour = 0.0)
    }
}

/** Per-day breakdown produced by [IrrigationCalculator]. */
data class DailyIrrigationBreakdown(
    val dateEpochMs: Long,
    val forecastEToMm: Double,
    val forecastRainMm: Double,
    val cropUseMm: Double,
    val effectiveRainMm: Double,
    val dailyDeficitMm: Double,
)

/** Aggregate recommendation produced by [IrrigationCalculator]. */
data class IrrigationRecommendationResult(
    val dailyBreakdown: List<DailyIrrigationBreakdown>,
    val forecastCropUseMm: Double,
    val forecastEffectiveRainMm: Double,
    val netDeficitMm: Double,
    val grossIrrigationMm: Double,
    val recommendedIrrigationHours: Double,
    val recommendedIrrigationMinutes: Int,
)

/**
 * Pure irrigation maths, ported 1:1 from the iOS `IrrigationCalculator` so the
 * recommendation matches across platforms. Crop use = ETo × Kc; rainfall under
 * 2 mm is treated as ineffective; the deficit is reduced by the soil buffer,
 * scaled by replacement %, then grossed up by irrigation efficiency before
 * converting to run-time hours via the system application rate.
 */
object IrrigationCalculator {
    fun calculate(
        forecastDays: List<ForecastDay>,
        settings: IrrigationSettings,
    ): IrrigationRecommendationResult? {
        if (forecastDays.isEmpty()) return null
        if (settings.irrigationApplicationRateMmPerHour <= 0) return null

        val kc = settings.cropCoefficientKc
        val rainEff = settings.rainfallEffectivenessPercent / 100.0
        val irrEff = max(settings.irrigationEfficiencyPercent / 100.0, 0.0001)
        val replacement = settings.replacementPercent / 100.0

        val breakdown = mutableListOf<DailyIrrigationBreakdown>()
        var totalCropUse = 0.0
        var totalEffectiveRain = 0.0
        var totalDeficit = 0.0

        for (day in forecastDays) {
            val cropUseMm = day.forecastEToMm * kc
            val rawEffectiveRain = day.forecastRainMm * rainEff
            val effectiveRainMm = if (day.forecastRainMm < 2.0) 0.0 else rawEffectiveRain
            val dailyDeficitMm = max(0.0, cropUseMm - effectiveRainMm)

            breakdown.add(
                DailyIrrigationBreakdown(
                    dateEpochMs = day.dateEpochMs,
                    forecastEToMm = day.forecastEToMm,
                    forecastRainMm = day.forecastRainMm,
                    cropUseMm = cropUseMm,
                    effectiveRainMm = effectiveRainMm,
                    dailyDeficitMm = dailyDeficitMm,
                )
            )

            totalCropUse += cropUseMm
            totalEffectiveRain += effectiveRainMm
            totalDeficit += dailyDeficitMm
        }

        val adjustedNetDeficitMm = max(0.0, totalDeficit - settings.soilMoistureBufferMm)
        val targetNetIrrigationMm = adjustedNetDeficitMm * replacement
        val grossIrrigationMm = targetNetIrrigationMm / irrEff
        val hours = grossIrrigationMm / settings.irrigationApplicationRateMmPerHour
        val minutes = (hours * 60.0).roundToInt()

        return IrrigationRecommendationResult(
            dailyBreakdown = breakdown,
            forecastCropUseMm = totalCropUse,
            forecastEffectiveRainMm = totalEffectiveRain,
            netDeficitMm = adjustedNetDeficitMm,
            grossIrrigationMm = grossIrrigationMm,
            recommendedIrrigationHours = hours,
            recommendedIrrigationMinutes = minutes,
        )
    }
}
