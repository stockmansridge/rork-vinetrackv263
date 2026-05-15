import SwiftUI

/// Manual soil profile editor for a paddock. Used by the Irrigation Advisor
/// soil buffer panel (Phase 1) and later by Block / Paddock detail.
///
/// Phase 2 will add a "Fetch soil from NSW SEED" button here that calls the
/// Supabase Edge Function and pre-fills the same fields with a lower
/// confidence value so users can review before saving.
struct SoilProfileEditorSheet: View {
    let vineyardId: UUID
    let paddockId: UUID
    let paddockName: String
    let onSaved: (BackendSoilProfile?) -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(BackendAccessControl.self) private var accessControl

    @State private var defaults: [SoilClassDefault] = []
    @State private var existing: BackendSoilProfile?

    @State private var selectedClass: IrrigationSoilClass = .unknown
    @State private var awcText: String = ""
    @State private var rootDepthText: String = ""
    @State private var allowedDepletionText: String = ""
    @State private var notes: String = ""

    @State private var isLoading: Bool = false
    @State private var isSaving: Bool = false
    @State private var errorMessage: String?
    @State private var showDeleteConfirm: Bool = false

    private let repository: any SoilProfileRepositoryProtocol = SupabaseSoilProfileRepository()

    private var canEdit: Bool { accessControl.canChangeSettings }

    private var currentDefault: SoilClassDefault? {
        defaults.first(where: { $0.irrigationSoilClass == selectedClass.rawValue })
    }

    private var rootZoneCapacityMm: Double? {
        guard let awc = Double(awcText), let depth = Double(rootDepthText), awc > 0, depth > 0 else { return nil }
        return awc * depth
    }

    private var readilyAvailableMm: Double? {
        guard let rzc = rootZoneCapacityMm,
              let depl = Double(allowedDepletionText), depl > 0 else { return nil }
        return rzc * (depl / 100.0)
    }

    var body: some View {
        NavigationStack {
            Form {
                if isLoading {
                    Section {
                        HStack {
                            ProgressView()
                            Text("Loading soil profile\u{2026}").foregroundStyle(.secondary)
                        }
                    }
                } else {
                    soilClassSection
                    valuesSection
                    derivedSection
                    notesSection
                    if existing != nil, canEdit {
                        Section {
                            Button(role: .destructive) { showDeleteConfirm = true } label: {
                                Label("Reset soil profile", systemImage: "trash")
                            }
                        }
                    }
                    disclaimerSection
                }
                if let error = errorMessage {
                    Section {
                        Label(error, systemImage: "exclamationmark.triangle.fill")
                            .font(.caption)
                            .foregroundStyle(.orange)
                    }
                }
            }
            .navigationTitle("Soil Profile")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        Task { await save() }
                    } label: {
                        if isSaving {
                            ProgressView()
                        } else {
                            Text("Save")
                        }
                    }
                    .disabled(!canEdit || isSaving || isLoading)
                }
            }
            .task { await load() }
            .confirmationDialog("Reset soil profile for \(paddockName)?", isPresented: $showDeleteConfirm, titleVisibility: .visible) {
                Button("Reset", role: .destructive) { Task { await deleteProfile() } }
                Button("Cancel", role: .cancel) {}
            }
        }
    }

    // MARK: - Sections

    private var soilClassSection: some View {
        Section("Soil class") {
            Picker("Class", selection: $selectedClass) {
                ForEach(orderedClasses, id: \.self) { cls in
                    Text(label(for: cls)).tag(cls)
                }
            }
            .pickerStyle(.menu)
            .onChange(of: selectedClass) { _, _ in
                applyDefaultsForSelectedClass()
            }
            if let def = currentDefault, let desc = def.description, !desc.isEmpty {
                Text(desc)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if !canEdit {
                Text("Read-only: only Owner or Manager can edit the soil profile.")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var valuesSection: some View {
        Section {
            HStack {
                Text("Available water (mm/m)")
                Spacer()
                TextField("e.g. 150", text: $awcText)
                    .keyboardType(.decimalPad)
                    .multilineTextAlignment(.trailing)
                    .frame(maxWidth: 120)
                    .disabled(!canEdit)
            }
            HStack {
                Text("Effective root depth (m)")
                Spacer()
                TextField("e.g. 0.6", text: $rootDepthText)
                    .keyboardType(.decimalPad)
                    .multilineTextAlignment(.trailing)
                    .frame(maxWidth: 120)
                    .disabled(!canEdit)
            }
            HStack {
                Text("Allowed depletion (%)")
                Spacer()
                TextField("e.g. 45", text: $allowedDepletionText)
                    .keyboardType(.decimalPad)
                    .multilineTextAlignment(.trailing)
                    .frame(maxWidth: 120)
                    .disabled(!canEdit)
            }
        } header: {
            Text("Soil water values")
        } footer: {
            Text("Defaults are pre-filled from the selected soil class. Override with your own observations where possible.")
        }
    }

    @ViewBuilder
    private var derivedSection: some View {
        if rootZoneCapacityMm != nil || readilyAvailableMm != nil {
            Section("Derived") {
                if let rzc = rootZoneCapacityMm {
                    LabeledContent("Root-zone capacity") {
                        Text(String(format: "%.0f mm", rzc)).foregroundStyle(.secondary)
                    }
                }
                if let raw = readilyAvailableMm {
                    LabeledContent("Readily available water") {
                        Text(String(format: "%.0f mm", raw)).foregroundStyle(.secondary)
                    }
                }
            }
        }
    }

    private var notesSection: some View {
        Section("Notes") {
            TextField("Site-specific notes (optional)", text: $notes, axis: .vertical)
                .lineLimit(2...5)
                .disabled(!canEdit)
        }
    }

    private var disclaimerSection: some View {
        Section {
            VStack(alignment: .leading, spacing: 6) {
                Label("Soil information is estimated and may not reflect site-specific vineyard soil conditions. Adjust soil class and water-holding values using your own soil knowledge where needed.", systemImage: "info.circle")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }

    // MARK: - Class helpers

    private var orderedClasses: [IrrigationSoilClass] {
        if defaults.isEmpty {
            return IrrigationSoilClass.allCases
        }
        return defaults
            .sorted { $0.sortOrder < $1.sortOrder }
            .compactMap { $0.soilClass }
    }

    private func label(for cls: IrrigationSoilClass) -> String {
        defaults.first(where: { $0.irrigationSoilClass == cls.rawValue })?.label ?? cls.fallbackLabel
    }

    // MARK: - Load / Save

    private func load() async {
        isLoading = true
        defer { isLoading = false }
        do {
            async let defs = repository.fetchSoilClassDefaults()
            async let prof = repository.fetchPaddockSoilProfile(paddockId: paddockId)
            let (loadedDefaults, loadedProfile) = try await (defs, prof)
            defaults = loadedDefaults
            existing = loadedProfile
            applyExistingOrDefaults()
        } catch {
            errorMessage = "Failed to load soil profile: \(error.localizedDescription)"
        }
    }

    private func applyExistingOrDefaults() {
        if let existing {
            if let raw = existing.irrigationSoilClass,
               let cls = IrrigationSoilClass(rawValue: raw) {
                selectedClass = cls
            } else {
                selectedClass = .unknown
            }
            awcText = existing.availableWaterCapacityMmPerM.map { String(format: "%.0f", $0) } ?? ""
            rootDepthText = existing.effectiveRootDepthM.map { String(format: "%.2f", $0) } ?? ""
            allowedDepletionText = existing.managementAllowedDepletionPercent.map { String(format: "%.0f", $0) } ?? ""
            notes = existing.manualNotes ?? ""
            if awcText.isEmpty || rootDepthText.isEmpty || allowedDepletionText.isEmpty {
                applyDefaultsForSelectedClass(fillEmptyOnly: true)
            }
        } else {
            selectedClass = .unknown
            applyDefaultsForSelectedClass()
        }
    }

    private func applyDefaultsForSelectedClass(fillEmptyOnly: Bool = false) {
        guard let def = currentDefault else { return }
        if !fillEmptyOnly || awcText.isEmpty {
            awcText = String(format: "%.0f", def.defaultAwcMmPerM)
        }
        if !fillEmptyOnly || rootDepthText.isEmpty {
            rootDepthText = String(format: "%.2f", def.defaultRootDepthM)
        }
        if !fillEmptyOnly || allowedDepletionText.isEmpty {
            allowedDepletionText = String(format: "%.0f", def.defaultAllowedDepletionPercent)
        }
    }

    private func save() async {
        guard canEdit else { return }
        isSaving = true
        defer { isSaving = false }
        errorMessage = nil
        let payload = SoilProfileUpsert(
            paddockId: paddockId,
            irrigationSoilClass: selectedClass.rawValue,
            availableWaterCapacityMmPerM: Double(awcText),
            effectiveRootDepthM: Double(rootDepthText),
            managementAllowedDepletionPercent: Double(allowedDepletionText),
            confidence: "manual",
            isManualOverride: true,
            manualNotes: notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : notes,
            source: "manual",
            sourceProvider: "manual"
        )
        do {
            let saved = try await repository.upsertSoilProfile(payload)
            onSaved(saved)
            dismiss()
        } catch {
            errorMessage = friendlyError(error)
        }
    }

    private func deleteProfile() async {
        guard canEdit else { return }
        do {
            try await repository.deleteSoilProfile(paddockId: paddockId)
            existing = nil
            onSaved(nil)
            dismiss()
        } catch {
            errorMessage = friendlyError(error)
        }
    }

    private func friendlyError(_ error: Error) -> String {
        let raw = String(describing: error).lowercased()
        if raw.contains("not_authorized") {
            return "You don't have permission to edit the soil profile for this paddock."
        }
        if raw.contains("paddock_not_found") {
            return "Paddock not found."
        }
        if raw.contains("invalid_awc") {
            return "Available water capacity must be between 0 and 400 mm/m."
        }
        if raw.contains("invalid_root_depth") {
            return "Effective root depth must be between 0 and 5 m."
        }
        if raw.contains("invalid_allowed_depletion") {
            return "Allowed depletion must be between 0 and 100%."
        }
        if raw.contains("invalid_irrigation_soil_class") {
            return "Unknown irrigation soil class."
        }
        return "Failed to save soil profile: \(error.localizedDescription)"
    }
}
