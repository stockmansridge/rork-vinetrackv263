import Foundation
import Supabase

// MARK: - RPC payloads

nonisolated private struct PaddockIdParams: Encodable, Sendable {
    let paddockId: UUID
    enum CodingKeys: String, CodingKey { case paddockId = "p_paddock_id" }
}

nonisolated private struct VineyardIdParams: Encodable, Sendable {
    let vineyardId: UUID
    enum CodingKeys: String, CodingKey { case vineyardId = "p_vineyard_id" }
}

nonisolated private struct UpsertSoilProfileParams: Encodable, Sendable {
    let paddockId: UUID
    let irrigationSoilClass: String?
    let availableWaterCapacityMmPerM: Double?
    let effectiveRootDepthM: Double?
    let managementAllowedDepletionPercent: Double?
    let soilLandscape: String?
    let soilDescription: String?
    let soilTextureClass: String?
    let infiltrationRisk: String?
    let drainageRisk: String?
    let waterloggingRisk: String?
    let confidence: String?
    let isManualOverride: Bool
    let manualNotes: String?
    let source: String
    let sourceProvider: String?
    let sourceDataset: String?
    let sourceFeatureId: String?
    let sourceName: String?
    let countryCode: String?
    let regionCode: String?
    let modelVersion: String

    enum CodingKeys: String, CodingKey {
        case paddockId                          = "p_paddock_id"
        case irrigationSoilClass                = "p_irrigation_soil_class"
        case availableWaterCapacityMmPerM       = "p_available_water_capacity_mm_per_m"
        case effectiveRootDepthM                = "p_effective_root_depth_m"
        case managementAllowedDepletionPercent  = "p_management_allowed_depletion_percent"
        case soilLandscape                      = "p_soil_landscape"
        case soilDescription                    = "p_soil_description"
        case soilTextureClass                   = "p_soil_texture_class"
        case infiltrationRisk                   = "p_infiltration_risk"
        case drainageRisk                       = "p_drainage_risk"
        case waterloggingRisk                   = "p_waterlogging_risk"
        case confidence                         = "p_confidence"
        case isManualOverride                   = "p_is_manual_override"
        case manualNotes                        = "p_manual_notes"
        case source                             = "p_source"
        case sourceProvider                     = "p_source_provider"
        case sourceDataset                      = "p_source_dataset"
        case sourceFeatureId                    = "p_source_feature_id"
        case sourceName                         = "p_source_name"
        case countryCode                        = "p_country_code"
        case regionCode                         = "p_region_code"
        case modelVersion                       = "p_model_version"
    }
}

// MARK: - Repository

final class SupabaseSoilProfileRepository: SoilProfileRepositoryProtocol {
    private let provider: SupabaseClientProvider

    init(provider: SupabaseClientProvider = .shared) {
        self.provider = provider
    }

    func fetchSoilClassDefaults() async throws -> [SoilClassDefault] {
        guard provider.isConfigured else { throw BackendRepositoryError.missingSupabaseConfiguration }
        let rows: [SoilClassDefault] = try await provider.client
            .rpc("get_soil_class_defaults")
            .execute()
            .value
        return rows
    }

    func fetchPaddockSoilProfile(paddockId: UUID) async throws -> BackendSoilProfile? {
        guard provider.isConfigured else { throw BackendRepositoryError.missingSupabaseConfiguration }
        let rows: [BackendSoilProfile] = try await provider.client
            .rpc("get_paddock_soil_profile", params: PaddockIdParams(paddockId: paddockId))
            .execute()
            .value
        return rows.first
    }

    func listVineyardSoilProfiles(vineyardId: UUID) async throws -> [BackendSoilProfile] {
        guard provider.isConfigured else { throw BackendRepositoryError.missingSupabaseConfiguration }
        let rows: [BackendSoilProfile] = try await provider.client
            .rpc("list_vineyard_soil_profiles", params: VineyardIdParams(vineyardId: vineyardId))
            .execute()
            .value
        return rows
    }

    @discardableResult
    func upsertSoilProfile(_ profile: SoilProfileUpsert) async throws -> BackendSoilProfile? {
        guard provider.isConfigured else { throw BackendRepositoryError.missingSupabaseConfiguration }
        let params = UpsertSoilProfileParams(
            paddockId: profile.paddockId,
            irrigationSoilClass: profile.irrigationSoilClass,
            availableWaterCapacityMmPerM: profile.availableWaterCapacityMmPerM,
            effectiveRootDepthM: profile.effectiveRootDepthM,
            managementAllowedDepletionPercent: profile.managementAllowedDepletionPercent,
            soilLandscape: profile.soilLandscape,
            soilDescription: profile.soilDescription,
            soilTextureClass: profile.soilTextureClass,
            infiltrationRisk: profile.infiltrationRisk,
            drainageRisk: profile.drainageRisk,
            waterloggingRisk: profile.waterloggingRisk,
            confidence: profile.confidence,
            isManualOverride: profile.isManualOverride,
            manualNotes: profile.manualNotes,
            source: profile.source,
            sourceProvider: profile.sourceProvider,
            sourceDataset: profile.sourceDataset,
            sourceFeatureId: profile.sourceFeatureId,
            sourceName: profile.sourceName,
            countryCode: profile.countryCode,
            regionCode: profile.regionCode,
            modelVersion: profile.modelVersion
        )
        let rows: [BackendSoilProfile] = try await provider.client
            .rpc("upsert_paddock_soil_profile", params: params)
            .execute()
            .value
        return rows.first
    }

    func deleteSoilProfile(paddockId: UUID) async throws {
        guard provider.isConfigured else { throw BackendRepositoryError.missingSupabaseConfiguration }
        _ = try await provider.client
            .rpc("delete_paddock_soil_profile", params: PaddockIdParams(paddockId: paddockId))
            .execute()
    }
}
