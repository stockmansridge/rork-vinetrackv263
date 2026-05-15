import Foundation

nonisolated struct GrapeVariety: Codable, Identifiable, Sendable, Hashable {
    let id: UUID
    var vineyardId: UUID
    var name: String
    var optimalGDD: Double
    var isBuiltIn: Bool

    init(
        id: UUID = UUID(),
        vineyardId: UUID = UUID(),
        name: String,
        optimalGDD: Double,
        isBuiltIn: Bool = false
    ) {
        self.id = id
        self.vineyardId = vineyardId
        self.name = name
        self.optimalGDD = optimalGDD
        self.isBuiltIn = isBuiltIn
    }

    nonisolated enum CodingKeys: String, CodingKey {
        case id, vineyardId, name, optimalGDD, isBuiltIn
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(UUID.self, forKey: .id)
        vineyardId = try c.decode(UUID.self, forKey: .vineyardId)
        name = try c.decode(String.self, forKey: .name)
        optimalGDD = try c.decode(Double.self, forKey: .optimalGDD)
        isBuiltIn = try c.decodeIfPresent(Bool.self, forKey: .isBuiltIn) ?? false
    }
}

extension GrapeVariety {
    static func defaults(for vineyardId: UUID) -> [GrapeVariety] {
        // Optimal growing degree days (base 10°C) to harvest ripeness.
        // Values are typical ranges from viticulture references.
        // Approx BEDD (Biologically Effective Degree Days) values from viticulture reference table.
        // Midpoint used for ranges.
        let data: [(String, Double)] = [
            ("Chardonnay", 1145),
            ("Pinot Gris / Grigio", 1100),
            ("Riesling", 1200),
            ("Sauvignon Blanc", 1150),
            ("Semillon", 1200),
            ("Chenin Blanc", 1250),
            ("Gewurztraminer", 1150),
            ("Viognier", 1260),
            ("Shiraz", 1255),
            ("Merlot", 1250),
            ("Cabernet Franc", 1255),
            ("Cabernet Sauvignon", 1310),
            ("Pinot Noir", 1145),
            ("Tempranillo", 1230),
            ("Sangiovese", 1285),
            ("Grenache", 1365),
            ("Mataro / Mourvedre", 1440),
            ("Barbera", 1285),
            ("Malbec", 1230),
            ("Colombard", 1300),
            ("Muscat Gordo Blanco", 1350),
            ("Fiano", 1320),
            ("Prosecco", 1410),
            ("Vermentino", 1290),
            ("Gruner Veltliner", 1200),
            ("Primitivo", 1200)
        ]
        return data.map { GrapeVariety(vineyardId: vineyardId, name: $0.0, optimalGDD: $0.1, isBuiltIn: true) }
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

    init(id: UUID = UUID(), varietyId: UUID, percent: Double, name: String? = nil) {
        self.id = id
        self.varietyId = varietyId
        self.percent = percent
        self.name = name
    }

    nonisolated enum CodingKeys: String, CodingKey {
        case id
        case varietyId
        case percent
        case name
        // Tolerant aliases for payloads written by other systems.
        case variety_id
        case variety
        case varietyName
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
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(varietyId, forKey: .varietyId)
        try c.encode(percent, forKey: .percent)
        try c.encodeIfPresent(name, forKey: .name)
    }
}
