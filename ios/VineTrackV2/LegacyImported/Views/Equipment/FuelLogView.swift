import SwiftUI

/// Lists tractor fuel fills grouped per tractor (newest first) and lets any
/// vineyard member record a new fill. Litres/hour is derived for display only
/// from the previous fill for the same tractor.
struct FuelLogView: View {
    @Environment(MigratedDataStore.self) private var store
    @Environment(\.accessControl) private var accessControl
    @State private var showAddSheet: Bool = false
    @State private var editingLog: TractorFuelLog?

    private var canManageSetup: Bool { accessControl?.canManageSetup ?? false }

    private var vineyardTractors: [Tractor] {
        guard let vid = store.selectedVineyardId else { return [] }
        return store.tractors.filter { $0.vineyardId == vid }
    }

    private var logs: [TractorFuelLog] {
        guard let vid = store.selectedVineyardId else { return [] }
        return store.tractorFuelLogs
            .filter { $0.vineyardId == vid }
            .sorted { $0.fillDateTime > $1.fillDateTime }
    }

    /// Tractor id (nil = "Unassigned") in display order, only those with logs.
    private var groupedTractorKeys: [UUID?] {
        var seen: [UUID?] = []
        for log in logs where !seen.contains(log.tractorId) {
            seen.append(log.tractorId)
        }
        return seen
    }

    private func tractorName(_ id: UUID?) -> String {
        guard let id, let t = vineyardTractors.first(where: { $0.id == id }) else {
            return "Unassigned tractor"
        }
        return t.displayName
    }

    var body: some View {
        List {
            if logs.isEmpty {
                Section {
                    Text("No fuel fills recorded yet. Tap + to record litres added and engine hours when you fill a tractor.")
                        .font(.callout)
                        .foregroundStyle(.secondary)
                }
            }

            ForEach(groupedTractorKeys, id: \.self) { key in
                let tractorLogs = logs.filter { $0.tractorId == key }
                Section {
                    ForEach(tractorLogs) { log in
                        let previous = store.previousFuelLog(forTractor: log.tractorId, before: log.fillDateTime, excluding: log.id)
                        let rate = TractorFuelRateCalculator.rate(current: log, previous: previous)
                        let canEdit = canManageSetup || (log.operatorUserId != nil && log.operatorUserId == store.currentUserIdProvider?())
                        Group {
                            if canEdit {
                                Button { editingLog = log } label: { FuelLogRow(log: log, rate: rate) }
                            } else {
                                FuelLogRow(log: log, rate: rate)
                            }
                        }
                        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                            if canManageSetup {
                                Button(role: .destructive) {
                                    store.deleteTractorFuelLog(log)
                                } label: {
                                    Label("Delete", systemImage: "trash")
                                }
                            }
                        }
                    }
                } header: {
                    Text(tractorName(key))
                        .font(.caption.weight(.semibold))
                        .textCase(.uppercase)
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle("Fuel Log")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    showAddSheet = true
                } label: {
                    Image(systemName: "plus")
                }
            }
        }
        .sheet(isPresented: $showAddSheet) {
            FuelFillFormSheet(log: nil)
        }
        .sheet(item: $editingLog) { item in
            FuelFillFormSheet(log: item)
        }
    }
}

struct FuelLogRow: View {
    let log: TractorFuelLog
    let rate: TractorFuelRateResult
    @Environment(\.accessControl) private var accessControl

    private var litresText: String {
        "\(String(format: "%.1f", log.litresAdded)) L"
    }

    var body: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 8) {
                    Text(litresText)
                        .font(.body.weight(.semibold))
                    if log.filledToFull == true {
                        Label("Full", systemImage: "drop.fill")
                            .font(.caption2.weight(.medium))
                            .foregroundStyle(VineyardTheme.olive)
                            .labelStyle(.titleAndIcon)
                    }
                }
                HStack(spacing: 10) {
                    Label(log.fillDateTime.formatted(date: .abbreviated, time: .shortened), systemImage: "calendar")
                    if let hours = log.engineHours {
                        Label("\(String(format: "%.1f", hours)) hrs", systemImage: "gauge.with.needle")
                    }
                }
                .font(.caption)
                .foregroundStyle(.secondary)
                if let name = log.operatorName, !name.isEmpty {
                    Label(name, systemImage: "person.fill")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                if (accessControl?.canViewFinancials ?? false), let cpl = log.costPerLitre {
                    Text("$\(String(format: "%.2f", cpl))/L")
                        .font(.caption.weight(.medium))
                        .foregroundStyle(VineyardTheme.olive)
                }
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                if let lph = rate.litresPerHour {
                    Text("\(String(format: "%.1f", lph)) L/hr")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(rate.reliability == .reliable ? VineyardTheme.olive : .orange)
                    Text(rate.reliability == .reliable ? "calculated" : "estimate")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                } else {
                    Text("—")
                        .font(.subheadline)
                        .foregroundStyle(.tertiary)
                }
            }
        }
        .contentShape(Rectangle())
    }
}

struct FuelFillFormSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(MigratedDataStore.self) private var store
    @Environment(NewBackendAuthService.self) private var auth
    @Environment(TractorFuelLogSyncService.self) private var fuelLogSync
    @Environment(TractorSyncService.self) private var tractorSync

    let log: TractorFuelLog?

    @State private var tractorId: UUID?
    @State private var litresText: String = ""
    @State private var engineHoursText: String = ""
    @State private var fillDate: Date = Date()
    @State private var operatorName: String = ""
    @State private var costPerLitreText: String = ""
    @State private var totalCostText: String = ""
    @State private var filledToFull: Bool = true
    @State private var notes: String = ""

    /// After a successful save, holds the derived rate so we can show the
    /// summary + optional "use as tractor default" action before dismissing.
    @State private var savedResult: TractorFuelRateResult?
    @State private var savedTractorId: UUID?
    @State private var didApplyDefault: Bool = false

    init(log: TractorFuelLog?) {
        self.log = log
        if let l = log {
            _tractorId = State(initialValue: l.tractorId)
            _litresText = State(initialValue: l.litresAdded > 0 ? String(format: "%.1f", l.litresAdded) : "")
            _engineHoursText = State(initialValue: l.engineHours.map { String(format: "%.1f", $0) } ?? "")
            _fillDate = State(initialValue: l.fillDateTime)
            _operatorName = State(initialValue: l.operatorName ?? "")
            _costPerLitreText = State(initialValue: l.costPerLitre.map { String(format: "%.2f", $0) } ?? "")
            _totalCostText = State(initialValue: l.totalCost.map { String(format: "%.2f", $0) } ?? "")
            _filledToFull = State(initialValue: l.filledToFull ?? true)
            _notes = State(initialValue: l.notes ?? "")
        }
    }

    private var vineyardTractors: [Tractor] {
        guard let vid = store.selectedVineyardId else { return [] }
        return store.tractors.filter { $0.vineyardId == vid }
    }

    private var litres: Double { Double(litresText) ?? 0 }
    private var engineHours: Double? { engineHoursText.isEmpty ? nil : Double(engineHoursText) }
    private var isValid: Bool { litres > 0 }

    var body: some View {
        NavigationStack {
            Form {
                if let result = savedResult {
                    savedSummarySection(result)
                } else {
                    formSections
                }
            }
            .navigationTitle(navTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                if savedResult == nil {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") { dismiss() }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Save") { save() }
                            .disabled(!isValid)
                    }
                } else {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Done") { dismiss() }
                    }
                }
            }
            .onAppear {
                if log == nil, operatorName.isEmpty {
                    operatorName = auth.userName ?? ""
                }
            }
        }
    }

    private var navTitle: String {
        if savedResult != nil { return "Fuel Fill Saved" }
        return log == nil ? "Record Fuel Fill" : "Edit Fuel Fill"
    }

    @ViewBuilder
    private var formSections: some View {
        Section("Tractor") {
            Picker("Tractor", selection: $tractorId) {
                Text("Select tractor").tag(UUID?.none)
                ForEach(vineyardTractors) { t in
                    Text(t.displayName).tag(UUID?.some(t.id))
                }
            }
        }

        Section {
            HStack {
                TextField("e.g. 120", text: $litresText)
                    .keyboardType(.decimalPad)
                Text("L").foregroundStyle(.secondary)
            }
        } header: {
            Text("Litres Added")
        }

        Section {
            HStack {
                TextField("e.g. 1320.5", text: $engineHoursText)
                    .keyboardType(.decimalPad)
                Text("hrs").foregroundStyle(.secondary)
            }
        } header: {
            Text("Engine Hours at Fill")
        } footer: {
            Text("Used to calculate litres per hour against the previous fill for this tractor.")
        }

        Section("When") {
            DatePicker("Fill date & time", selection: $fillDate)
        }

        Section("Operator") {
            TextField("Operator name", text: $operatorName)
        }

        Section {
            Toggle("Filled to full", isOn: $filledToFull)
        } footer: {
            Text("L/hr is most accurate when both this fill and the previous fill were to the same tank level (full).")
        }

        Section("Cost (optional)") {
            HStack {
                Text("$").foregroundStyle(.secondary)
                TextField("Cost per litre", text: $costPerLitreText)
                    .keyboardType(.decimalPad)
                Text("/L").foregroundStyle(.secondary)
            }
            HStack {
                Text("$").foregroundStyle(.secondary)
                TextField("Total cost", text: $totalCostText)
                    .keyboardType(.decimalPad)
            }
        }

        Section("Notes (optional)") {
            TextField("Notes", text: $notes, axis: .vertical)
                .lineLimit(2...5)
        }
    }

    @ViewBuilder
    private func savedSummarySection(_ result: TractorFuelRateResult) -> some View {
        Section {
            if let lph = result.litresPerHour {
                HStack {
                    Text("Fuel rate")
                    Spacer()
                    Text("\(String(format: "%.2f", lph)) L/hr")
                        .font(.headline)
                        .foregroundStyle(result.reliability == .reliable ? VineyardTheme.olive : .orange)
                }
                if let delta = result.engineHoursDelta {
                    HStack {
                        Text("Engine hours since last fill")
                        Spacer()
                        Text("\(String(format: "%.1f", delta)) hrs")
                            .foregroundStyle(.secondary)
                    }
                }
            } else {
                Text("Litres per hour could not be calculated for this fill.")
                    .foregroundStyle(.secondary)
            }
        } header: {
            Text("Calculated Rate")
        }

        if !result.warnings.isEmpty {
            Section {
                ForEach(Array(result.warnings.enumerated()), id: \.offset) { _, w in
                    Label(warningText(w), systemImage: warningIcon(w))
                        .font(.callout)
                        .foregroundStyle(.orange)
                }
            }
        }

        if let lph = result.litresPerHour,
           let tid = savedTractorId,
           let tractor = vineyardTractors.first(where: { $0.id == tid }) {
            Section {
                if didApplyDefault {
                    Label("Updated \(tractor.displayName) default to \(String(format: "%.1f", lph)) L/hr", systemImage: "checkmark.circle.fill")
                        .foregroundStyle(VineyardTheme.olive)
                } else {
                    Button {
                        applyAsTractorDefault(lph: lph, tractor: tractor)
                    } label: {
                        Label("Use \(String(format: "%.1f", lph)) L/hr as \(tractor.displayName) default", systemImage: "arrow.up.circle")
                    }
                }
            } footer: {
                Text("The tractor's default fuel rate is only changed if you choose to update it here.")
            }
        }
    }

    private func warningText(_ w: TractorFuelRateResult.Warning) -> String {
        switch w {
        case .missingEngineHours:
            return "Fuel log saved, but L/hr cannot be calculated without engine hours."
        case .engineHoursWentBackwards:
            return "Engine hours are lower than the previous fill — check the reading."
        case .engineHoursDeltaZero:
            return "Engine hours match the previous fill, so L/hr cannot be calculated."
        case .unrealisticRate:
            return "Calculated L/hr looks unusually high or low — double-check litres and engine hours."
        case .notFilledToFull:
            return "L/hr may be inaccurate unless both fills were to the same level."
        }
    }

    private func warningIcon(_ w: TractorFuelRateResult.Warning) -> String {
        switch w {
        case .missingEngineHours: return "gauge.with.dots.needle.bottom.0percent"
        case .engineHoursWentBackwards: return "arrow.uturn.backward"
        case .engineHoursDeltaZero: return "equal.circle"
        case .unrealisticRate: return "exclamationmark.triangle.fill"
        case .notFilledToFull: return "drop"
        }
    }

    private func save() {
        let cpl = Double(costPerLitreText)
        let total = Double(totalCostText)
        var entry: TractorFuelLog
        if let existing = log {
            entry = existing
            entry.tractorId = tractorId
            entry.litresAdded = litres
            entry.engineHours = engineHours
            entry.fillDateTime = fillDate
            entry.operatorName = operatorName.isEmpty ? nil : operatorName
            entry.costPerLitre = cpl
            entry.totalCost = total
            entry.filledToFull = filledToFull
            entry.notes = notes.isEmpty ? nil : notes
            store.updateTractorFuelLog(entry)
        } else {
            entry = TractorFuelLog(
                tractorId: tractorId,
                fillDateTime: fillDate,
                litresAdded: litres,
                engineHours: engineHours,
                operatorUserId: auth.userId,
                operatorName: operatorName.isEmpty ? nil : operatorName,
                costPerLitre: cpl,
                totalCost: total,
                filledToFull: filledToFull,
                notes: notes.isEmpty ? nil : notes
            )
            store.addTractorFuelLog(entry)
        }

        let previous = store.previousFuelLog(forTractor: entry.tractorId, before: entry.fillDateTime, excluding: entry.id)
        savedResult = TractorFuelRateCalculator.rate(current: entry, previous: previous)
        savedTractorId = entry.tractorId

        // Push immediately so other devices and the Portal see it promptly.
        Task { await fuelLogSync.syncForSelectedVineyard() }
    }

    private func applyAsTractorDefault(lph: Double, tractor: Tractor) {
        var updated = tractor
        updated.fuelUsageLPerHour = lph
        store.updateTractor(updated)
        didApplyDefault = true
        Task { await tractorSync.syncForSelectedVineyard() }
    }
}
