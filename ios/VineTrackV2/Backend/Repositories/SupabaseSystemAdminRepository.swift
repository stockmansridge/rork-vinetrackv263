import Foundation
import Supabase

// MARK: - Models

nonisolated enum SystemFeatureFlagKey {
    static let showSyncDiagnostics    = "show_sync_diagnostics"
    static let showPinDiagnostics     = "show_pin_diagnostics"
    static let showWeatherDiagnostics = "show_weather_diagnostics"
    static let showWillyWeatherDebug  = "show_willyweather_debug"
    static let showMapPinDiagnostics  = "show_map_pin_diagnostics"
    static let showRawJSONPanels      = "show_raw_json_panels"
    static let showCostingDiagnostics = "show_costing_diagnostics"
    static let enableBetaFeatures     = "enable_beta_features"
}

nonisolated struct SystemFeatureFlag: Identifiable, Sendable, Hashable {
    let key: String
    let valueType: String
    let category: String?
    let label: String?
    let description: String?
    let isEnabled: Bool
    let updatedAt: Date?

    var id: String { key }

    var displayLabel: String {
        if let label, !label.isEmpty { return label }
        return key
            .split(separator: "_")
            .map { $0.prefix(1).uppercased() + $0.dropFirst() }
            .joined(separator: " ")
    }
}

// MARK: - DTOs

nonisolated private struct FlagDTO: Decodable, Sendable {
    let key: String
    let valueType: String?
    let category: String?
    let label: String?
    let description: String?
    let isEnabled: Bool
    let updatedAt: Date?

    enum CodingKeys: String, CodingKey {
        case key, category, label, description
        case valueType  = "value_type"
        case isEnabled  = "is_enabled"
        case updatedAt  = "updated_at"
    }
}

nonisolated private struct SetFlagParams: Encodable, Sendable {
    let key: String
    let isEnabled: Bool
    enum CodingKeys: String, CodingKey {
        case key       = "p_key"
        case isEnabled = "p_is_enabled"
    }
}

nonisolated private struct EmptyParams: Encodable, Sendable {}

// MARK: - Repository

final class SupabaseSystemAdminRepository {
    private let provider: SupabaseClientProvider

    init(provider: SupabaseClientProvider = .shared) {
        self.provider = provider
    }

    func isSystemAdmin() async throws -> Bool {
        guard provider.isConfigured else { throw BackendRepositoryError.missingSupabaseConfiguration }
        let result: Bool = try await provider.client
            .rpc("is_system_admin")
            .execute()
            .value
        return result
    }

    func fetchFlags() async throws -> [SystemFeatureFlag] {
        guard provider.isConfigured else { throw BackendRepositoryError.missingSupabaseConfiguration }
        let rows: [FlagDTO] = try await provider.client
            .rpc("get_system_feature_flags")
            .execute()
            .value
        return rows.map {
            SystemFeatureFlag(
                key: $0.key,
                valueType: $0.valueType ?? "boolean",
                category: $0.category,
                label: $0.label,
                description: $0.description,
                isEnabled: $0.isEnabled,
                updatedAt: $0.updatedAt
            )
        }
    }

    func setFlag(key: String, isEnabled: Bool) async throws {
        guard provider.isConfigured else { throw BackendRepositoryError.missingSupabaseConfiguration }
        _ = try await provider.client
            .rpc("set_system_feature_flag", params: SetFlagParams(key: key, isEnabled: isEnabled))
            .execute()
    }
}
