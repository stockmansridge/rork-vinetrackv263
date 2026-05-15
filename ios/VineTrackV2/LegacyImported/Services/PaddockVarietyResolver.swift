import Foundation

/// Shared helper used by every surface that needs to resolve a
/// `PaddockVarietyAllocation` to a managed `GrapeVariety` / display name.
///
/// Resolution order:
/// 1. id match — `allocation.varietyId` against the supplied variety list.
/// 2. name snapshot — `allocation.name` matched case-insensitively against
///    the managed variety list. Useful when Lovable/web payloads or
///    cross-device data carry different ids for the same logical variety.
/// 3. raw name fallback — if a name snapshot exists but cannot be matched
///    to a managed variety, the name is still surfaced (with a warning),
///    so users see "Grüner Veltliner" instead of "Unassigned variety".
/// 4. unresolved — only when neither id nor name is usable.
///
/// Keep the canonicalisation rule (case-insensitive + whitespace/punct
/// stripped) identical to `RipenessVarietyResolver.canonicalName` so the
/// two helpers always agree.
nonisolated enum PaddockVarietyResolver {

    /// Outcome of resolving a single allocation.
    struct Resolved: Sendable {
        /// Variety id we ultimately resolved to. May differ from
        /// `allocation.varietyId` when matched by name.
        let varietyId: UUID?
        /// Display name to show in UIs and exports. Nil only when the
        /// allocation cannot be resolved at all.
        let displayName: String?
        /// True when either the id or the name resolved to a managed
        /// `GrapeVariety`.
        let isResolved: Bool
        /// Free-form debug reason. Only used by diagnostic logging.
        let reason: String
    }

    /// Canonical form used for name comparisons. Matches the rule used by
    /// `RipenessVarietyResolver` so both helpers agree.
    static func canonical(_ name: String) -> String {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let allowed = CharacterSet.alphanumerics
        return String(trimmed.unicodeScalars.filter { allowed.contains($0) })
    }

    /// Resolve a single allocation against the managed variety list.
    static func resolve(
        allocation: PaddockVarietyAllocation,
        varieties: [GrapeVariety]
    ) -> Resolved {
        // 1. id match.
        if let v = varieties.first(where: { $0.id == allocation.varietyId }) {
            return Resolved(
                varietyId: v.id,
                displayName: v.name,
                isResolved: true,
                reason: "id-match"
            )
        }

        // 2. + 3. name fallback.
        if let raw = allocation.name?.trimmingCharacters(in: .whitespacesAndNewlines),
           !raw.isEmpty {
            let key = canonical(raw)
            if let v = varieties.first(where: { canonical($0.name) == key }) {
                return Resolved(
                    varietyId: v.id,
                    displayName: v.name,
                    isResolved: true,
                    reason: "name-match"
                )
            }
            // Name supplied but no managed variety to match — still useful.
            return Resolved(
                varietyId: nil,
                displayName: raw,
                isResolved: true,
                reason: "name-only"
            )
        }

        return Resolved(
            varietyId: nil,
            displayName: nil,
            isResolved: false,
            reason: "no-match"
        )
    }

    /// Backfill helper: return a copy of `allocations` where each allocation
    /// whose id resolves to a managed variety has its `name` snapshot
    /// filled in. Existing names are preserved. Use this on save paths
    /// (e.g. `EditPaddockSheet`) so allocations carry a name forward and
    /// remain resolvable on devices/portals where the variety id list
    /// differs.
    static func backfillNames(
        _ allocations: [PaddockVarietyAllocation],
        varieties: [GrapeVariety]
    ) -> [PaddockVarietyAllocation] {
        allocations.map { alloc in
            if let existing = alloc.name,
               !existing.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                return alloc
            }
            let r = resolve(allocation: alloc, varieties: varieties)
            guard let n = r.displayName, !n.isEmpty else { return alloc }
            var copy = alloc
            copy.name = n
            return copy
        }
    }
}
