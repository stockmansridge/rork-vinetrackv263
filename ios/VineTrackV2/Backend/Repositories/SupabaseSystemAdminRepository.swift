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
    /// Master switch for the soil-aware irrigation model + NSW SEED lookup
    /// button. Defaults to ON for system admins; falls back to enabled when
    /// the flag row is missing so existing Phase 1 manual soil profiles keep
    /// working.
    static let soilAwareIrrigation    = "soil_aware_irrigation"
    /// Show raw NSW SEED ArcGIS attributes / diagnostics in the soil editor
    /// after a fetch. Gated to system admins only.
    static let showNSWSeedDiagnostics = "show_nsw_seed_diagnostics"
    /// Show grape variety allocation diagnostics in Block/Paddock Settings
    /// (paddock id, allocation id, varietyId, resolver path, sync metadata).
    /// Gated to system admins only.
    static let showVarietyDiagnostics = "show_variety_diagnostics"
    /// Show the irrigation rate resolver diagnostics section inside the
    /// Irrigation Advisor. Gated to system admins only.
    static let showIrrigationDiagnostics = "show_irrigation_diagnostics"
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

// MARK: - System Admin Management

nonisolated struct SystemAdminUser: Identifiable, Sendable, Hashable {
    let userId: UUID
    let email: String
    let isActive: Bool
    let createdAt: Date?
    let createdBy: UUID?

    var id: UUID { userId }
}

nonisolated private struct SystemAdminRowDTO: Decodable, Sendable {
    let userId: UUID
    let email: String?
    let isActive: Bool
    let createdAt: Date?
    let createdBy: UUID?

    enum CodingKeys: String, CodingKey {
        case userId    = "user_id"
        case email
        case isActive  = "is_active"
        case createdAt = "created_at"
        case createdBy = "created_by"
    }

    func toModel() -> SystemAdminUser {
        SystemAdminUser(
            userId: userId,
            email: email ?? "",
            isActive: isActive,
            createdAt: createdAt,
            createdBy: createdBy
        )
    }
}

nonisolated private struct AddAdminParams: Encodable, Sendable {
    let email: String
    enum CodingKeys: String, CodingKey { case email = "p_email" }
}

nonisolated private struct SetActiveParams: Encodable, Sendable {
    let userId: UUID
    let isActive: Bool
    enum CodingKeys: String, CodingKey {
        case userId   = "p_user_id"
        case isActive = "p_is_active"
    }
}

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

    // MARK: - System Admin Management

    func listSystemAdmins() async throws -> [SystemAdminUser] {
        guard provider.isConfigured else { throw BackendRepositoryError.missingSupabaseConfiguration }
        let rows: [SystemAdminRowDTO] = try await provider.client
            .rpc("list_system_admins")
            .execute()
            .value
        return rows.map { $0.toModel() }
    }

    @discardableResult
    func addSystemAdmin(email: String) async throws -> SystemAdminUser? {
        guard provider.isConfigured else { throw BackendRepositoryError.missingSupabaseConfiguration }
        let rows: [SystemAdminRowDTO] = try await provider.client
            .rpc("add_system_admin", params: AddAdminParams(email: email))
            .execute()
            .value
        return rows.first?.toModel()
    }

    @discardableResult
    func setSystemAdminActive(userId: UUID, isActive: Bool) async throws -> SystemAdminUser? {
        guard provider.isConfigured else { throw BackendRepositoryError.missingSupabaseConfiguration }
        let rows: [SystemAdminRowDTO] = try await provider.client
            .rpc("set_system_admin_active", params: SetActiveParams(userId: userId, isActive: isActive))
            .execute()
            .value
        return rows.first?.toModel()
    }
}
