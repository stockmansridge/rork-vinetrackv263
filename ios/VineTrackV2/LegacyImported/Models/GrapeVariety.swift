import Foundation
import CryptoKit

nonisolated struct GrapeVariety: Codable, Identifiable, Sendable, Hashable {
    /// Stable identifier. Mutable so the canonicalization pass can
    /// migrate built-in varieties onto their deterministic id without
    /// losing the rest of the record.
    var id: UUID
    var vineyardId: UUID
    var name: String
    var optimalGDD: Double
    var isBuiltIn: Bool
    /// Stable slug for built-in varieties (e.g. "pinot_noir"). Optional so
    /// existing/user-created entries decode without it. Built-in varieties
    /// derive a deterministic `id` from `(vineyardId, key)`, so the same
    /// variety always resolves to the same id across devices/app resets.
    var key: String?

    init(
        id: UUID = UUID(),
        vineyardId: UUID = UUID(),
        name: String,
        optimalGDD: Double,
        isBuiltIn: Bool = false,
        key: String? = nil
    ) {
        self.id = id
        self.vineyardId = vineyardId
        self.name = name
        self.optimalGDD = optimalGDD
        self.isBuiltIn = isBuiltIn
        self.key = key
    }

    nonisolated enum CodingKeys: String, CodingKey {
        case id, vineyardId, name, optimalGDD, isBuiltIn, key
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(UUID.self, forKey: .id)
        vineyardId = try c.decode(UUID.self, forKey: .vineyardId)
        name = try c.decode(String.self, forKey: .name)
        optimalGDD = try c.decode(Double.self, forKey: .optimalGDD)
        isBuiltIn = try c.decodeIfPresent(Bool.self, forKey: .isBuiltIn) ?? false
        key = try c.decodeIfPresent(String.self, forKey: .key)
    }
}

extension GrapeVariety {
    /// Deterministic UUIDv5-style id derived from `(vineyardId, key)`. Ensures
    /// the same built-in variety always has the same id within a vineyard,
    /// regardless of when/where it was seeded — so paddock allocations stay
    /// resolvable across devices and app resets.
    static func deterministicID(vineyardId: UUID, key: String) -> UUID {
        var data = Data()
        withUnsafeBytes(of: vineyardId.uuid) { data.append(contentsOf: $0) }
        data.append(Data(key.utf8))
        let digest = Insecure.MD5.hash(data: data)
        var bytes = Array(digest)
        // RFC 4122-style version 5 + variant bits.
        bytes[6] = (bytes[6] & 0x0F) | 0x50
        bytes[8] = (bytes[8] & 0x3F) | 0x80
        let tuple: uuid_t = (
            bytes[0], bytes[1], bytes[2], bytes[3],
            bytes[4], bytes[5], bytes[6], bytes[7],
            bytes[8], bytes[9], bytes[10], bytes[11],
            bytes[12], bytes[13], bytes[14], bytes[15]
        )
        return UUID(uuid: tuple)
    }

    static func defaults(for vineyardId: UUID) -> [GrapeVariety] {
        BuiltInGrapeVarietyCatalog.entries.map { entry in
            GrapeVariety(
                id: deterministicID(vineyardId: vineyardId, key: entry.key),
                vineyardId: vineyardId,
                name: entry.name,
                optimalGDD: entry.optimalGDD,
                isBuiltIn: true,
                key: entry.key
            )
        }
    }
}

/// Stable catalog of built-in grape varieties. Slugs MUST remain stable —
/// they are part of the variety identity. Add new varieties at the end;
/// never repurpose an existing slug.
nonisolated enum BuiltInGrapeVarietyCatalog {
    struct Entry: Sendable, Hashable {
        let key: String
        let name: String
        let optimalGDD: Double
        /// Alternate display names (case/punct-insensitive) that should
        /// also resolve to this slug. Used to repair allocations whose
        /// `name` snapshot was written with a different spelling.
        let aliases: [String]
    }

    static let entries: [Entry] = [
        Entry(key: "chardonnay", name: "Chardonnay", optimalGDD: 1145, aliases: []),
        Entry(key: "pinot_gris", name: "Pinot Gris / Grigio", optimalGDD: 1100, aliases: ["Pinot Gris", "Pinot Grigio"]),
        Entry(key: "riesling", name: "Riesling", optimalGDD: 1200, aliases: []),
        Entry(key: "sauvignon_blanc", name: "Sauvignon Blanc", optimalGDD: 1150, aliases: ["Sav Blanc", "Savvy B"]),
        Entry(key: "semillon", name: "Semillon", optimalGDD: 1200, aliases: ["Sémillon"]),
        Entry(key: "chenin_blanc", name: "Chenin Blanc", optimalGDD: 1250, aliases: []),
        Entry(key: "gewurztraminer", name: "Gewurztraminer", optimalGDD: 1150, aliases: ["Gewürztraminer"]),
        Entry(key: "viognier", name: "Viognier", optimalGDD: 1260, aliases: []),
        Entry(key: "shiraz", name: "Shiraz", optimalGDD: 1255, aliases: ["Syrah"]),
        Entry(key: "merlot", name: "Merlot", optimalGDD: 1250, aliases: []),
        Entry(key: "cabernet_franc", name: "Cabernet Franc", optimalGDD: 1255, aliases: ["Cab Franc"]),
        Entry(key: "cabernet_sauvignon", name: "Cabernet Sauvignon", optimalGDD: 1310, aliases: ["Cab Sav", "Cab Sauv"]),
        Entry(key: "pinot_noir", name: "Pinot Noir", optimalGDD: 1145, aliases: []),
        Entry(key: "tempranillo", name: "Tempranillo", optimalGDD: 1230, aliases: []),
        Entry(key: "sangiovese", name: "Sangiovese", optimalGDD: 1285, aliases: []),
        Entry(key: "grenache", name: "Grenache", optimalGDD: 1365, aliases: ["Garnacha"]),
        Entry(key: "mataro_mourvedre", name: "Mataro / Mourvedre", optimalGDD: 1440, aliases: ["Mataro", "Mourvedre", "Mourvèdre", "Monastrell"]),
        Entry(key: "barbera", name: "Barbera", optimalGDD: 1285, aliases: []),
        Entry(key: "malbec", name: "Malbec", optimalGDD: 1230, aliases: []),
        Entry(key: "colombard", name: "Colombard", optimalGDD: 1300, aliases: []),
        Entry(key: "muscat_gordo_blanco", name: "Muscat Gordo Blanco", optimalGDD: 1350, aliases: ["Muscat Gordo", "Muscat of Alexandria"]),
        Entry(key: "fiano", name: "Fiano", optimalGDD: 1320, aliases: []),
        Entry(key: "prosecco", name: "Prosecco", optimalGDD: 1410, aliases: ["Glera"]),
        Entry(key: "vermentino", name: "Vermentino", optimalGDD: 1290, aliases: []),
        Entry(key: "gruner_veltliner", name: "Gruner Veltliner", optimalGDD: 1200, aliases: ["Grüner Veltliner", "Gruner"]),
        Entry(key: "primitivo", name: "Primitivo", optimalGDD: 1200, aliases: ["Zinfandel"])
    ]

    /// Canonical-name → entry index (built once).
    static let entriesByCanonicalName: [String: Entry] = {
        var map: [String: Entry] = [:]
        for entry in entries {
            map[canonical(entry.name)] = entry
            for alias in entry.aliases {
                map[canonical(alias)] = entry
            }
        }
        return map
    }()

    /// Same canonicalisation rule used by `PaddockVarietyResolver` /
    /// `RipenessVarietyResolver`. Kept in sync intentionally.
    static func canonical(_ name: String) -> String {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let allowed = CharacterSet.alphanumerics
        return String(trimmed.unicodeScalars.filter { allowed.contains($0) })
    }

    /// Find the built-in catalog entry that matches a free-form name. Uses
    /// canonical comparison + alias table. Returns nil for unknown names.
    static func entry(matching name: String) -> Entry? {
        entriesByCanonicalName[canonical(name)]
    }
}

nonisolated struct PaddockVarietyAllocation: Codable, Sendable, Hashable, Identifiable {
    var id: UUID
    var varietyId: UUID
    var percent: Double
    /// Optional display-name snapshot. Populated when the allocation is
    /// resolved against the managed `GrapeVariety` list, or when remote
    /// systems (e.g. the Lovable web portal) write a `name`/`variety`
    /// name string alongside the id. Used by
    /// `PaddockVarietyResolver` as a fallback when the id no longer
    /// matches a managed variety (e.g. local-only varieties seeded with
    /// different UUIDs across devices).
    var name: String?
    /// Stable catalog key (e.g. `pinot_gris`) or vineyard-scoped custom
    /// key (`custom:<vineyardId>:<slug>`). Carries the allocation's
    /// identity across devices, resets, and id drift — the resolver
    /// trusts this before `varietyId` or `name`.
    var varietyKey: String?

    init(
        id: UUID = UUID(),
        varietyId: UUID,
        percent: Double,
        name: String? = nil,
        varietyKey: String? = nil
    ) {
        self.id = id
        self.varietyId = varietyId
        self.percent = percent
        self.name = name
        self.varietyKey = varietyKey
    }

    nonisolated enum CodingKeys: String, CodingKey {
        case id
        case varietyId
        case percent
        case name
        case varietyKey
        // Tolerant aliases for payloads written by other systems.
        case variety_id
        case variety
        case varietyName
        case variety_key
        case key
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = (try? c.decodeIfPresent(UUID.self, forKey: .id)) ?? UUID()

        // Variety id: accept `varietyId`, `variety_id`, or a UUID-shaped
        // `variety` value. Fall back to a placeholder UUID so the model
        // still decodes — the resolver will fall back to name matching.
        if let v = try? c.decode(UUID.self, forKey: .varietyId) {
            varietyId = v
        } else if let v = try? c.decode(UUID.self, forKey: .variety_id) {
            varietyId = v
        } else if let v = try? c.decode(UUID.self, forKey: .variety) {
            varietyId = v
        } else if let s = try? c.decode(String.self, forKey: .varietyId),
                  let v = UUID(uuidString: s) {
            varietyId = v
        } else if let s = try? c.decode(String.self, forKey: .variety_id),
                  let v = UUID(uuidString: s) {
            varietyId = v
        } else {
            varietyId = UUID()
        }

        percent = (try? c.decode(Double.self, forKey: .percent)) ?? 0

        // Optional name snapshot. Accept `name`, `varietyName`, or
        // `variety` when it is not a UUID string.
        if let n = try? c.decode(String.self, forKey: .name),
           !n.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            name = n
        } else if let n = try? c.decode(String.self, forKey: .varietyName),
                  !n.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            name = n
        } else if let n = try? c.decode(String.self, forKey: .variety),
                  UUID(uuidString: n) == nil,
                  !n.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            name = n
        } else {
            name = nil
        }

        // Optional stable key. Accept `varietyKey`, `variety_key`, or `key`.
        if let k = try? c.decode(String.self, forKey: .varietyKey),
           !k.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            varietyKey = k
        } else if let k = try? c.decode(String.self, forKey: .variety_key),
                  !k.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            varietyKey = k
        } else if let k = try? c.decode(String.self, forKey: .key),
                  !k.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            varietyKey = k
        } else {
            varietyKey = nil
        }
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(varietyId, forKey: .varietyId)
        try c.encode(percent, forKey: .percent)
        try c.encodeIfPresent(name, forKey: .name)
        try c.encodeIfPresent(varietyKey, forKey: .varietyKey)
    }
}
