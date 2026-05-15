import Foundation

nonisolated struct ForecastDay: Sendable, Hashable, Identifiable {
    let date: Date
    let forecastEToMm: Double
    let forecastRainMm: Double
    let forecastWindKmhMax: Double?
    let forecastTempMaxC: Double?
    let forecastTempMinC: Double?

    var id: Date { date }

    init(
        date: Date,
        forecastEToMm: Double,
        forecastRainMm: Double,
        forecastWindKmhMax: Double? = nil,
        forecastTempMaxC: Double? = nil,
        forecastTempMinC: Double? = nil
    ) {
        self.date = date
        self.forecastEToMm = forecastEToMm
        self.forecastRainMm = forecastRainMm
        self.forecastWindKmhMax = forecastWindKmhMax
        self.forecastTempMaxC = forecastTempMaxC
        self.forecastTempMinC = forecastTempMinC
    }
}

nonisolated struct IrrigationSettings: Sendable, Hashable {
    var irrigationApplicationRateMmPerHour: Double
    var cropCoefficientKc: Double
    var irrigationEfficiencyPercent: Double
    var rainfallEffectivenessPercent: Double
    var replacementPercent: Double
    var soilMoistureBufferMm: Double

    static let defaults = IrrigationSettings(
        irrigationApplicationRateMmPerHour: 0,
        cropCoefficientKc: 0.65,
        irrigationEfficiencyPercent: 90,
        rainfallEffectivenessPercent: 80,
        replacementPercent: 100,
        soilMoistureBufferMm: 0
    )
}

nonisolated struct DailyIrrigationBreakdown: Sendable, Hashable, Identifiable {
    let date: Date
    let forecastEToMm: Double
    let forecastRainMm: Double
    let cropUseMm: Double
    let effectiveRainMm: Double
    let dailyDeficitMm: Double

    var id: Date { date }
}

/// Soil-aware inputs derived from a `BackendSoilProfile` (or manual values).
/// All fields are optional so soil-aware logic stays additive — irrigation
/// recommendations still work when no soil profile exists.
nonisolated struct SoilProfileInputs: Sendable, Hashable {
    var irrigationSoilClass: String?
    var availableWaterCapacityMmPerM: Double?
    var effectiveRootDepthM: Double?
    var managementAllowedDepletionPercent: Double?
    var infiltrationRisk: String?
    var drainageRisk: String?
    var waterloggingRisk: String?
    /// Versioning so we can update AWC defaults / mapping / formulas later.
    var modelVersion: String

    static let empty = SoilProfileInputs(modelVersion: "soil_aware_irrigation_v1")

    /// Derived root-zone capacity (mm) = AWC × effective root depth.
    var rootZoneCapacityMm: Double? {
        guard let awc = availableWaterCapacityMmPerM,
              let depth = effectiveRootDepthM,
              awc > 0, depth > 0 else { return nil }
        return awc * depth
    }

    /// Readily available water (mm) = root-zone capacity × allowed depletion.
    var readilyAvailableWaterMm: Double? {
        guard let rzc = rootZoneCapacityMm,
              let depl = managementAllowedDepletionPercent,
              depl > 0 else { return nil }
        return rzc * (depl / 100.0)
    }
}

nonisolated enum IrrigationSoilAdvice: String, Sendable, Hashable {
    case sandyFrequent
    case loamNormal
    case clayCaution
    case shallow
    case generic
}

nonisolated struct IrrigationRecommendationResult: Sendable, Hashable {
    let dailyBreakdown: [DailyIrrigationBreakdown]
    let forecastCropUseMm: Double
    let forecastEffectiveRainMm: Double
    /// Recent measured/actual rainfall fed into the calculation as a soil
    /// moisture credit. Zero when no actual-rain offset is applied.
    let recentActualRainMm: Double
    let netDeficitMm: Double
    let grossIrrigationMm: Double
    let recommendedIrrigationHours: Double
    let recommendedIrrigationMinutes: Int
    /// Soil-aware values (root-zone capacity, readily available water,
    /// advice text). Nil when no soil profile is configured.
    let soilAdvice: IrrigationSoilAdvice?
    let rootZoneCapacityMm: Double?
    let readilyAvailableWaterMm: Double?
    let soilAdviceText: String?
    let soilCautionText: String?
}

nonisolated enum IrrigationCalculator {
    static func calculate(
        forecastDays: [ForecastDay],
        settings: IrrigationSettings,
        recentActualRainMm: Double = 0,
        soil: SoilProfileInputs = .empty
    ) -> IrrigationRecommendationResult? {
        guard !forecastDays.isEmpty else { return nil }
        guard settings.irrigationApplicationRateMmPerHour > 0 else { return nil }

        let kc = settings.cropCoefficientKc
        let rainEff = settings.rainfallEffectivenessPercent / 100.0
        let irrEff = max(settings.irrigationEfficiencyPercent / 100.0, 0.0001)
        let replacement = settings.replacementPercent / 100.0

        var breakdown: [DailyIrrigationBreakdown] = []
        var totalCropUse: Double = 0
        var totalEffectiveRain: Double = 0
        var totalDeficit: Double = 0

        for day in forecastDays {
            let cropUseMm = day.forecastEToMm * kc
            let rawEffectiveRain = day.forecastRainMm * rainEff
            let effectiveRainMm = day.forecastRainMm < 2.0 ? 0 : rawEffectiveRain
            let dailyDeficitMm = max(0, cropUseMm - effectiveRainMm)

            breakdown.append(DailyIrrigationBreakdown(
                date: day.date,
                forecastEToMm: day.forecastEToMm,
                forecastRainMm: day.forecastRainMm,
                cropUseMm: cropUseMm,
                effectiveRainMm: effectiveRainMm,
                dailyDeficitMm: dailyDeficitMm
            ))

            totalCropUse += cropUseMm
            totalEffectiveRain += effectiveRainMm
            totalDeficit += dailyDeficitMm
        }

        // Subtract recent measured rainfall (capped at calc deficit) before
        // soil-buffer offset, so users with Davis stations don't over-irrigate
        // after a recent storm.
        let actualRainOffset = max(0, recentActualRainMm * (settings.rainfallEffectivenessPercent / 100.0))
        let adjustedNetDeficitMm = max(0, totalDeficit - settings.soilMoistureBufferMm - actualRainOffset)
        let targetNetIrrigationMm = adjustedNetDeficitMm * replacement
        let grossIrrigationMm = targetNetIrrigationMm / irrEff
        let hours = grossIrrigationMm / settings.irrigationApplicationRateMmPerHour
        let minutes = Int((hours * 60.0).rounded())

        // Derive soil-aware advice. This is descriptive only in v1 — it
        // does not alter the recommended depth so users can build trust
        // before deeper soil-driven decision rules are layered in.
        let advice = soilAdvice(for: soil)
        let adviceText = adviceCopy(for: advice, soil: soil, grossIrrigationMm: grossIrrigationMm)
        let cautionText = cautionCopy(for: advice, soil: soil, forecastDays: forecastDays)

        return IrrigationRecommendationResult(
            dailyBreakdown: breakdown,
            forecastCropUseMm: totalCropUse,
            forecastEffectiveRainMm: totalEffectiveRain,
            recentActualRainMm: actualRainOffset,
            netDeficitMm: adjustedNetDeficitMm,
            grossIrrigationMm: grossIrrigationMm,
            recommendedIrrigationHours: hours,
            recommendedIrrigationMinutes: minutes,
            soilAdvice: advice,
            rootZoneCapacityMm: soil.rootZoneCapacityMm,
            readilyAvailableWaterMm: soil.readilyAvailableWaterMm,
            soilAdviceText: adviceText,
            soilCautionText: cautionText
        )
    }

    private static func soilAdvice(for soil: SoilProfileInputs) -> IrrigationSoilAdvice? {
        guard let raw = soil.irrigationSoilClass,
              let cls = IrrigationSoilClass(rawValue: raw) else { return nil }
        switch cls {
        case .sandLoamySand, .sandyLoam:
            return .sandyFrequent
        case .loam, .siltLoam, .clayLoam, .basaltClayLoam:
            return .loamNormal
        case .clayHeavyClay:
            return .clayCaution
        case .shallowRocky:
            return .shallow
        case .unknown:
            return .generic
        }
    }

    private static func adviceCopy(
        for advice: IrrigationSoilAdvice?,
        soil: SoilProfileInputs,
        grossIrrigationMm: Double
    ) -> String? {
        guard let advice else { return nil }
        let raw = soil.readilyAvailableWaterMm
        switch advice {
        case .sandyFrequent:
            if let raw, raw > 0, grossIrrigationMm > raw {
                return String(format: "Sandy soils drain quickly. Consider splitting this into smaller irrigations of about %.0f mm each.", raw)
            }
            return "Sandy soils drain quickly. Prefer smaller, more frequent irrigations to limit drainage below the root zone."
        case .loamNormal:
            return "Loam / clay-loam soils hold water well. Use the soil buffer to smooth irrigation decisions between events."
        case .clayCaution:
            return "Heavy clay soils hold water but drain slowly. Avoid large refilling events, especially before forecast rain."
        case .shallow:
            return "Shallow / rocky soils have a small root zone. Irrigate little and often to avoid runoff."
        case .generic:
            return "Soil class is unknown — irrigation recommendation uses generic defaults. Update the soil profile for site-specific guidance."
        }
    }

    private static func cautionCopy(
        for advice: IrrigationSoilAdvice?,
        soil: SoilProfileInputs,
        forecastDays: [ForecastDay]
    ) -> String? {
        guard let advice else { return nil }
        let forecastRain = forecastDays.reduce(0) { $0 + $1.forecastRainMm }
        switch advice {
        case .clayCaution:
            if forecastRain >= 10 {
                return "Significant rain forecast on heavy clay — risk of waterlogging or slow drainage."
            }
            return nil
        case .sandyFrequent:
            if let raw = soil.readilyAvailableWaterMm, raw > 0 {
                return String(format: "Applying more than ~%.0f mm at once may drain below the root zone.", raw)
            }
            return nil
        default:
            return nil
        }
    }
}
