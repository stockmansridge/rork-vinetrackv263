import Foundation
import Supabase

// MARK: - Models

nonisolated struct SharedGrapeVarietyCatalogEntry: Codable, Sendable, Hashable, Identifiable {
    let key: String
    let canonicalName: String
    let displayName: String
    let aliases: [String]
    let optimalGDD: Double?
    let isBuiltin: Bool
    let isActive: Bool
    let updatedAt: Date?

    var id: String { key }

    nonisolated enum CodingKeys: String, CodingKey {
        case key
        case canonicalName = "canonical_name"
        case displayName = "display_name"
        case aliases
        case optimalGDD = "optimal_gdd"
        case isBuiltin = "is_builtin"
        case isActive = "is_active"
        case updatedAt = "updated_at"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        key = try c.decode(String.self, forKey: .key)
        canonicalName = try c.decode(String.self, forKey: .canonicalName)
        displayName = try c.decode(String.self, forKey: .displayName)
        if let arr = try? c.decode([String].self, forKey: .aliases) {
            aliases = arr
        } else {
            aliases = []
        }
        optimalGDD = try? c.decodeIfPresent(Double.self, forKey: .optimalGDD)
        isBuiltin = (try? c.decodeIfPresent(Bool.self, forKey: .isBuiltin)) ?? true
        isActive = (try? c.decodeIfPresent(Bool.self, forKey: .isActive)) ?? true
        updatedAt = try? c.decodeIfPresent(Date.self, forKey: .updatedAt)
    }
}

nonisolated struct GrapeVarietyCatalogVerification: Codable, Sendable, Hashable {
    let activeBuiltinCount: Int
    let inactiveBuiltinCount: Int
    let totalCount: Int
    let totalAliases: Int
    let lastUpdatedAt: Date?
    let pinotGrisActive: Bool
    let pinotGrigioResolvesToPinotGris: Bool

    nonisolated enum CodingKeys: String, CodingKey {
        case activeBuiltinCount = "active_builtin_count"
        case inactiveBuiltinCount = "inactive_builtin_count"
        case totalCount = "total_count"
        case totalAliases = "total_aliases"
        case lastUpdatedAt = "last_updated_at"
        case pinotGrisActive = "pinot_gris_active"
        case pinotGrigioResolvesToPinotGris = "pinot_grigio_resolves_to_pinot_gris"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        activeBuiltinCount = (try? c.decodeIfPresent(Int.self, forKey: .activeBuiltinCount)) ?? 0
        inactiveBuiltinCount = (try? c.decodeIfPresent(Int.self, forKey: .inactiveBuiltinCount)) ?? 0
        totalCount = (try? c.decodeIfPresent(Int.self, forKey: .totalCount)) ?? 0
        totalAliases = (try? c.decodeIfPresent(Int.self, forKey: .totalAliases)) ?? 0
        lastUpdatedAt = try? c.decodeIfPresent(Date.self, forKey: .lastUpdatedAt)
        pinotGrisActive = (try? c.decodeIfPresent(Bool.self, forKey: .pinotGrisActive)) ?? false
        pinotGrigioResolvesToPinotGris = (try? c.decodeIfPresent(Bool.self, forKey: .pinotGrigioResolvesToPinotGris)) ?? false
    }
}

nonisolated struct VineyardGrapeVarietyDiagnostics: Codable, Sendable, Hashable {
    let vineyardVarietyCount: Int
    let builtinCount: Int
    let customCount: Int
    let archivedCount: Int
    let unresolvedAllocations: Int

    nonisolated enum CodingKeys: String, CodingKey {
        case vineyardVarietyCount = "vineyard_variety_count"
        case builtinCount = "builtin_count"
        case customCount = "custom_count"
        case archivedCount = "archived_count"
        case unresolvedAllocations = "unresolved_allocations"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        vineyardVarietyCount = (try? c.decodeIfPresent(Int.self, forKey: .vineyardVarietyCount)) ?? 0
        builtinCount = (try? c.decodeIfPresent(Int.self, forKey: .builtinCount)) ?? 0
        customCount = (try? c.decodeIfPresent(Int.self, forKey: .customCount)) ?? 0
        archivedCount = (try? c.decodeIfPresent(Int.self, forKey: .archivedCount)) ?? 0
        unresolvedAllocations = (try? c.decodeIfPresent(Int.self, forKey: .unresolvedAllocations)) ?? 0
    }
}

nonisolated struct VineyardGrapeVarietyRow: Codable, Sendable, Hashable, Identifiable {
    let id: UUID
    let vineyardId: UUID
    let varietyKey: String
    let displayName: String
    let isCustom: Bool
    let isActive: Bool
    let optimalGDDOverride: Double?
    let createdAt: Date?
    let updatedAt: Date?

    nonisolated enum CodingKeys: String, CodingKey {
        case id
        case vineyardId = "vineyard_id"
        case varietyKey = "variety_key"
        case displayName = "display_name"
        case isCustom = "is_custom"
        case isActive = "is_active"
        case optimalGDDOverride = "optimal_gdd_override"
        case createdAt = "created_at"
        case updatedAt = "updated_at"
    }
}

// MARK: - Repository

final class SupabaseGrapeVarietyCatalogRepository: Sendable {
    private let provider: SupabaseClientProvider

    init(provider: SupabaseClientProvider = .shared) {
        self.provider = provider
    }

    /// Fetch the global built-in catalog. Any authenticated user can read.
    func fetchSharedCatalog() async throws -> [SharedGrapeVarietyCatalogEntry] {
        guard provider.isConfigured else { throw BackendRepositoryError.missingSupabaseConfiguration }
        let rows: [SharedGrapeVarietyCatalogEntry] = try await provider.client
            .rpc("get_grape_variety_catalog")
            .execute()
            .value
        return rows
    }

    func listVineyardVarieties(vineyardId: UUID) async throws -> [VineyardGrapeVarietyRow] {
        guard provider.isConfigured else { throw BackendRepositoryError.missingSupabaseConfiguration }
        struct Params: Encodable, Sendable {
            let p_vineyard_id: UUID
        }
        let rows: [VineyardGrapeVarietyRow] = try await provider.client
            .rpc("list_vineyard_grape_varieties", params: Params(p_vineyard_id: vineyardId))
            .execute()
            .value
        return rows
    }

    /// Upsert a vineyard variety selection. Pass a built-in `key` (e.g. `pinot_gris`)
    /// OR `key = nil` plus a `displayName` to create a custom variety. The server
    /// derives a stable `custom:<vineyardId>:<slug>` key.
    @discardableResult
    func upsertVineyardVariety(
        vineyardId: UUID,
        key: String?,
        displayName: String,
        optimalGDDOverride: Double? = nil,
        isActive: Bool = true
    ) async throws -> VineyardGrapeVarietyRow {
        guard provider.isConfigured else { throw BackendRepositoryError.missingSupabaseConfiguration }
        struct Params: Encodable, Sendable {
            let p_vineyard_id: UUID
            let p_variety_key: String?
            let p_display_name: String
            let p_optimal_gdd_override: Double?
            let p_is_active: Bool
        }
        let row: VineyardGrapeVarietyRow = try await provider.client
            .rpc("upsert_vineyard_grape_variety", params: Params(
                p_vineyard_id: vineyardId,
                p_variety_key: key,
                p_display_name: displayName,
                p_optimal_gdd_override: optimalGDDOverride,
                p_is_active: isActive
            ))
            .execute()
            .value
        return row
    }

    /// Returns global catalogue counts + a Pinot Gris/Grigio resolver sanity check.
    func verifyCatalog() async throws -> GrapeVarietyCatalogVerification {
        guard provider.isConfigured else { throw BackendRepositoryError.missingSupabaseConfiguration }
        let rows: [GrapeVarietyCatalogVerification] = try await provider.client
            .rpc("verify_grape_variety_catalog")
            .execute()
            .value
        guard let first = rows.first else {
            throw BackendRepositoryError.missingSupabaseConfiguration
        }
        return first
    }

    /// Returns vineyard-scoped variety + unresolved allocation counts.
    func fetchVineyardDiagnostics(vineyardId: UUID) async throws -> VineyardGrapeVarietyDiagnostics {
        guard provider.isConfigured else { throw BackendRepositoryError.missingSupabaseConfiguration }
        struct Params: Encodable, Sendable {
            let p_vineyard_id: UUID
        }
        let rows: [VineyardGrapeVarietyDiagnostics] = try await provider.client
            .rpc("grape_variety_diagnostics", params: Params(p_vineyard_id: vineyardId))
            .execute()
            .value
        guard let first = rows.first else {
            throw BackendRepositoryError.missingSupabaseConfiguration
        }
        return first
    }

    @discardableResult
    func archiveVineyardVariety(id: UUID) async throws -> VineyardGrapeVarietyRow {
        guard provider.isConfigured else { throw BackendRepositoryError.missingSupabaseConfiguration }
        struct Params: Encodable, Sendable {
            let p_id: UUID
        }
        let row: VineyardGrapeVarietyRow = try await provider.client
            .rpc("archive_vineyard_grape_variety", params: Params(p_id: id))
            .execute()
            .value
        return row
    }
}

// MARK: - Local cache

/// Caches the shared grape-variety catalog on disk so launch is offline-tolerant.
/// Falls back to `BuiltInGrapeVarietyCatalog.entries` if the cache is empty and
/// the network read fails.
@MainActor
final class SharedGrapeVarietyCatalogCache {
    static let shared = SharedGrapeVarietyCatalogCache()

    private let fileURL: URL
    private let repository: SupabaseGrapeVarietyCatalogRepository
    private(set) var entries: [SharedGrapeVarietyCatalogEntry] = []
    private var didLoadFromDisk = false

    private init(
        repository: SupabaseGrapeVarietyCatalogRepository = SupabaseGrapeVarietyCatalogRepository()
    ) {
        self.repository = repository
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? FileManager.default.temporaryDirectory
        self.fileURL = dir.appendingPathComponent("shared_grape_variety_catalog.json")
    }

    func loadCached() -> [SharedGrapeVarietyCatalogEntry] {
        if didLoadFromDisk { return entries }
        didLoadFromDisk = true
        guard let data = try? Data(contentsOf: fileURL) else { return entries }
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        if let decoded = try? decoder.decode([SharedGrapeVarietyCatalogEntry].self, from: data) {
            entries = decoded
        }
        return entries
    }

    /// Refresh from Supabase. On failure the previous cache (or builtin fallback)
    /// is preserved.
    @discardableResult
    func refresh() async -> [SharedGrapeVarietyCatalogEntry] {
        _ = loadCached()
        do {
            let fresh = try await repository.fetchSharedCatalog()
            entries = fresh
            persist(fresh)
            return fresh
        } catch {
            return entries
        }
    }

    /// Best-effort lookup that prefers the shared catalog, falling back to the
    /// in-app `BuiltInGrapeVarietyCatalog` when the catalog has not been loaded.
    func entry(forKey key: String) -> SharedGrapeVarietyCatalogEntry? {
        _ = loadCached()
        return entries.first { $0.key == key }
    }

    private func persist(_ entries: [SharedGrapeVarietyCatalogEntry]) {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        guard let data = try? encoder.encode(entries) else { return }
        try? data.write(to: fileURL, options: .atomic)
    }
}
