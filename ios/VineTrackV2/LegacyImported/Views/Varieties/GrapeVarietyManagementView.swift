import SwiftUI

struct GrapeVarietyManagementView: View {
    @Environment(MigratedDataStore.self) private var store
    @Environment(\.accessControl) private var accessControl
    @State private var showAddSheet: Bool = false
    @State private var editingVariety: GrapeVariety?

    private var canManageSetup: Bool { accessControl?.canManageSetup ?? false }

    private var sortedVarieties: [GrapeVariety] {
        store.grapeVarieties.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
    }

    var body: some View {
        List {
            Section {
                ForEach(sortedVarieties) { variety in
                    Group {
                        if canManageSetup {
                            Button { editingVariety = variety } label: { varietyRow(variety) }
                        } else {
                            varietyRow(variety, showChevron: false)
                        }
                    }
                    .swipeActions(edge: .trailing) {
                        if canManageSetup {
                            Button(role: .destructive) {
                                store.deleteGrapeVariety(variety)
                            } label: {
                                Label("Delete", systemImage: "trash")
                            }
                        }
                    }
                }
            } header: {
                Text("Master Variety List")
            } footer: {
                if canManageSetup {
                    Text("Optimal GDD (base 10°C) is the heat units typically needed for a variety to reach harvest ripeness.")
                } else {
                    Text("Setup data is managed by vineyard owners and managers.")
                }
            }
        }
        .navigationTitle("Grape Varieties")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if canManageSetup {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        showAddSheet = true
                    } label: {
                        Image(systemName: "plus")
                    }
                }
            }
        }
        .sheet(isPresented: $showAddSheet) {
            EditGrapeVarietySheet(variety: nil)
        }
        .sheet(item: $editingVariety) { variety in
            EditGrapeVarietySheet(variety: variety)
        }
    }

    @ViewBuilder
    private func varietyRow(_ variety: GrapeVariety, showChevron: Bool = true) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 3) {
                Text(variety.name)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.primary)
                Text("Optimal: \(Int(variety.optimalGDD)) GDD")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            if showChevron {
                Image(systemName: "chevron.right")
                    .font(.caption)
                    .foregroundStyle(.tertiary)
            }
        }
    }
}

struct EditGrapeVarietySheet: View {
    let variety: GrapeVariety?
    @Environment(MigratedDataStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State private var name: String = ""
    @State private var optimalGDDText: String = "1400"
    @State private var isSaving: Bool = false
    @State private var saveError: String?
    private let catalogRepository = SupabaseGrapeVarietyCatalogRepository()

    private var isEditing: Bool { variety != nil }

    var body: some View {
        NavigationStack {
            Form {
                Section("Name") {
                    TextField("e.g. Chardonnay", text: $name)
                        .autocorrectionDisabled()
                }

                Section {
                    HStack {
                        Text("Optimal GDD")
                        Spacer()
                        TextField("1400", text: $optimalGDDText)
                            .keyboardType(.numberPad)
                            .multilineTextAlignment(.trailing)
                            .frame(width: 100)
                            .font(.system(.body, design: .monospaced).weight(.semibold))
                        Text("°C·days")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                } header: {
                    Text("Ripeness Target")
                } footer: {
                    Text("Growing Degree Days (base 10°C) required to reach harvest ripeness.")
                }
            }
            .navigationTitle(isEditing ? "Edit Variety" : "New Variety")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        Task { await save() }
                    }
                    .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty || isSaving)
                }
            }
            .onAppear {
                if let variety {
                    name = variety.name
                    optimalGDDText = "\(Int(variety.optimalGDD))"
                }
            }
            .overlay(alignment: .bottom) {
                if let saveError {
                    Text(saveError)
                        .font(.caption)
                        .foregroundStyle(.red)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(.regularMaterial, in: Capsule())
                        .padding(.bottom, 12)
                }
            }
        }
    }

    private func save() async {
        let trimmedName = name.trimmingCharacters(in: .whitespaces)
        let gdd = Double(optimalGDDText) ?? 1400
        isSaving = true
        defer { isSaving = false }
        saveError = nil

        if var existing = variety {
            existing.name = trimmedName
            existing.optimalGDD = gdd
            store.updateGrapeVariety(existing)
            // Best-effort: mirror the rename / GDD override to the shared
            // vineyard_grape_varieties table so other clients see it.
            if let vid = store.selectedVineyardId {
                let key: String? = existing.key
                _ = try? await catalogRepository.upsertVineyardVariety(
                    vineyardId: vid,
                    key: key,
                    displayName: trimmedName,
                    optimalGDDOverride: existing.isBuiltIn ? nil : gdd,
                    isActive: true
                )
            }
            dismiss()
            return
        }

        // New variety. If the name matches a built-in catalog entry, the
        // local add helper will stamp the catalog key + deterministic id.
        // Otherwise we route through `upsert_vineyard_grape_variety` so the
        // server mints a stable `custom:<vineyardId>:<slug>` key — this is
        // what keeps custom varieties resolvable across devices.
        let catalogEntry = BuiltInGrapeVarietyCatalog.entry(matching: trimmedName)
        if catalogEntry != nil {
            let new = GrapeVariety(name: trimmedName, optimalGDD: gdd)
            store.addGrapeVariety(new)
            dismiss()
            return
        }

        guard let vid = store.selectedVineyardId else {
            // No vineyard selected — fall back to local-only add.
            let new = GrapeVariety(name: trimmedName, optimalGDD: gdd)
            store.addGrapeVariety(new)
            dismiss()
            return
        }

        do {
            let row = try await catalogRepository.upsertVineyardVariety(
                vineyardId: vid,
                key: nil,
                displayName: trimmedName,
                optimalGDDOverride: gdd,
                isActive: true
            )
            // Mirror the server row locally with the stable key so the
            // resolver can use it.
            let new = GrapeVariety(
                vineyardId: vid,
                name: row.displayName,
                optimalGDD: row.optimalGDDOverride ?? gdd,
                isBuiltIn: false,
                key: row.varietyKey
            )
            store.addGrapeVariety(new)
            dismiss()
        } catch {
            // Offline / RPC missing — degrade gracefully to a local add so
            // the user can keep working. The next sync/repair pass will
            // reconcile the key.
            let new = GrapeVariety(name: trimmedName, optimalGDD: gdd)
            store.addGrapeVariety(new)
            saveError = "Saved locally; couldn't reach catalogue server."
            try? await Task.sleep(for: .seconds(1))
            dismiss()
        }
    }
}
