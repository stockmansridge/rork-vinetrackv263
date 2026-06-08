import SwiftUI

struct AddEditWorkTaskView: View {
    @Environment(MigratedDataStore.self) private var store
    @Environment(NewBackendAuthService.self) private var auth
    @Environment(WorkTaskSyncService.self) private var workTaskSync
    @Environment(WorkTaskTypeSyncService.self) private var workTaskTypeSync
    @Environment(WorkTaskPaddockSyncService.self) private var workTaskPaddockSync
    @Environment(PaddockSyncService.self) private var paddockSync
    @Environment(\.accessControl) private var accessControl
    @Environment(\.dismiss) private var dismiss

    let existingTask: WorkTask?

    @State private var date: Date = Date()
    @State private var taskType: String = ""
    @State private var customTaskType: String = ""
    @State private var showCustomTaskField: Bool = false
    @State private var selectedBlockIds: Set<UUID> = []
    @State private var showBlockPicker: Bool = false
    @State private var durationText: String = ""
    @State private var notes: String = ""
    @State private var resources: [WorkTaskResource] = []
    @State private var showDelete: Bool = false
    @State private var showWorkerTypes: Bool = false

    init(existingTask: WorkTask? = nil) {
        self.existingTask = existingTask
    }

    private var isEditing: Bool { existingTask != nil }
    private var canDelete: Bool { accessControl?.canDelete ?? false }

    private var fmt: RegionFormatter { store.settings.regionFormatter }

    private var durationHours: Double {
        Double(durationText.replacingOccurrences(of: ",", with: ".")) ?? 0
    }

    private var totalPeople: Int { resources.reduce(0) { $0 + $1.count } }

    /// Active blocks/paddocks for the selected vineyard, loaded from the same
    /// offline-first store every other screen uses. Works fully offline once
    /// the vineyard/block data has previously synced.
    private var assignableBlocks: [Paddock] {
        store.orderedPaddocks
    }

    /// Selected blocks, returned in the same stable order as the assignable
    /// list so backward-compat fields and breakdowns are deterministic.
    private var selectedBlocksOrdered: [Paddock] {
        assignableBlocks.filter { selectedBlockIds.contains($0.id) }
    }

    /// Collapsed picker label per the multi-select rules.
    private var blockCollapsedLabel: String {
        let selected = selectedBlocksOrdered
        if selected.isEmpty { return "Does not apply to a block" }
        if selected.count == 1 { return selected[0].name }
        return "\(selected.count) blocks selected"
    }

    /// Block area (ha) only when known/positive; nil otherwise.
    private func areaFor(_ p: Paddock) -> Double? {
        let a = p.areaHectares
        return a > 0 ? a : nil
    }

    /// Sum of known selected block areas.
    private var totalSelectedArea: Double {
        selectedBlocksOrdered.compactMap { areaFor($0) }.reduce(0, +)
    }

    private var hasMissingArea: Bool {
        selectedBlocksOrdered.contains { areaFor($0) == nil }
    }

    /// Per-block hours/cost allocation proportional to block area.
    private var blockAllocations: [BlockAllocation] {
        let total = totalSelectedArea
        return selectedBlocksOrdered.map { p in
            let area = areaFor(p)
            let share = (total > 0 && area != nil) ? (area! / total) : 0
            let cost = totalCost * share
            let cph: Double? = (area ?? 0) > 0 ? cost / area! : nil
            return BlockAllocation(
                id: p.id,
                name: p.name,
                areaHa: area,
                pctOfTotal: share,
                allocatedHours: durationHours * share,
                allocatedCost: cost,
                costPerHa: cph
            )
        }
    }

    /// Vineyard-scoped catalog merged with the built-in defaults. Drives the
    /// Task Type picker so Lovable-created custom types appear alongside the
    /// existing iOS defaults.
    private var mergedTaskTypeNames: [String] {
        let vineyardId = store.selectedVineyardId
        let scoped = store.workTaskTypes.filter { vineyardId == nil || $0.vineyardId == vineyardId }
        return WorkTaskTypeCatalog.merged(with: scoped)
    }

    private var totalCost: Double {
        resources.reduce(0.0) { $0 + ($1.hourlyRate * durationHours * Double($1.count)) }
    }

    private var costPerPerson: Double {
        guard totalPeople > 0 else { return 0 }
        return totalCost / Double(totalPeople)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Task Details") {
                    DatePicker("Date", selection: $date, displayedComponents: .date)

                    Menu {
                        ForEach(mergedTaskTypeNames, id: \.self) { t in
                            Button(t) {
                                taskType = t
                                showCustomTaskField = false
                            }
                        }
                        Divider()
                        Button {
                            showCustomTaskField = true
                            taskType = customTaskType
                        } label: {
                            Label("Custom…", systemImage: "pencil")
                        }
                    } label: {
                        HStack {
                            Text("Task Type")
                            Spacer()
                            Text(taskType.isEmpty ? "Select" : taskType)
                                .foregroundStyle(taskType.isEmpty ? .secondary : .primary)
                            Image(systemName: "chevron.up.chevron.down")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    if showCustomTaskField {
                        TextField("Custom task name", text: $customTaskType)
                            .onChange(of: customTaskType) { _, v in taskType = v }
                    }

                    Button {
                        showBlockPicker = true
                    } label: {
                        HStack {
                            Text("Block")
                                .foregroundStyle(.primary)
                            Spacer()
                            Text(blockCollapsedLabel)
                                .foregroundStyle(selectedBlockIds.isEmpty ? .secondary : .primary)
                                .lineLimit(1)
                            Image(systemName: "chevron.right")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .buttonStyle(.plain)

                    HStack {
                        Text("Duration (hours)")
                        Spacer()
                        TextField("0", text: $durationText)
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.trailing)
                            .frame(width: 100)
                    }
                }

                Section {
                    if resources.isEmpty {
                        Text("No workers added")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    } else {
                        ForEach($resources) { $res in
                            resourceRow($res)
                        }
                        .onDelete { idx in
                            resources.remove(atOffsets: idx)
                        }
                    }
                    Button {
                        addResource()
                    } label: {
                        Label("Add Worker Type", systemImage: "plus.circle.fill")
                    }
                    .disabled(store.operatorCategories.isEmpty)
                    if store.operatorCategories.isEmpty {
                        Text("Add worker types in Settings → Operator Categories first.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                } header: {
                    HStack {
                        Text("Resources")
                        Spacer()
                        Button {
                            showWorkerTypes = true
                        } label: {
                            Label("Edit", systemImage: "square.and.pencil")
                                .labelStyle(.titleAndIcon)
                                .font(.caption.weight(.semibold))
                                .textCase(nil)
                        }
                        .buttonStyle(.borderless)
                    }
                } footer: {
                    Text("Set the number of workers of each type used on this task.")
                }

                if accessControl?.canViewFinancials ?? false {
                    Section("Estimated Cost") {
                        LabeledContent("Total People") {
                            Text("\(totalPeople)")
                                .foregroundStyle(.secondary)
                        }
                        LabeledContent("Cost / Person") {
                            Text(fmt.formatCurrency(costPerPerson))
                                .foregroundStyle(.secondary)
                        }
                        HStack {
                            Text("Block Total")
                                .font(.headline)
                            Spacer()
                            Text(fmt.formatCurrency(totalCost))
                                .font(.title3.weight(.bold))
                                .foregroundStyle(VineyardTheme.leafGreen)
                        }
                        .padding(.vertical, 4)
                    }
                } else {
                    Section("Task Summary") {
                        LabeledContent("Total People") {
                            Text("\(totalPeople)")
                                .foregroundStyle(.secondary)
                        }
                    }
                }

                if selectedBlockIds.count > 1 {
                    Section("Block Breakdown") {
                        if hasMissingArea {
                            Label("One or more selected blocks are missing area, so the cost per hectare breakdown may be incomplete.", systemImage: "exclamationmark.triangle.fill")
                                .font(.caption)
                                .foregroundStyle(.orange)
                        }
                        ForEach(blockAllocations) { alloc in
                            blockAllocationRow(alloc)
                        }
                        LabeledContent("Total Area") {
                            Text(totalSelectedArea > 0 ? fmt.formatArea(hectares: totalSelectedArea) : "—")
                                .foregroundStyle(.secondary)
                        }
                        if (accessControl?.canViewFinancials ?? false) && totalSelectedArea > 0 {
                            LabeledContent("Cost / \(fmt.areaUnitAbbreviation)") {
                                Text("\(fmt.formatCurrency((totalCost / totalSelectedArea) / fmt.areaValue(hectares: 1)))/\(fmt.areaUnitAbbreviation)")
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }

                Section("Notes") {
                    TextField("Optional notes…", text: $notes, axis: .vertical)
                        .lineLimit(2...5)
                }

                if isEditing && canDelete {
                    Section {
                        Button(role: .destructive) {
                            showDelete = true
                        } label: {
                            HStack {
                                Spacer()
                                Label("Delete Task", systemImage: "trash")
                                Spacer()
                            }
                        }
                    }
                }
            }
            .navigationTitle(isEditing ? "Edit Task" : "New Task")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                if let task = existingTask {
                    ToolbarItem(placement: .principal) {
                        RecordSyncBadge(state: .forWorkTask(task.id, taskSync: workTaskSync))
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { saveTask() }
                        .fontWeight(.semibold)
                        .disabled(taskType.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .alert("Delete Task", isPresented: $showDelete) {
                Button("Delete", role: .destructive) {
                    if let t = existingTask {
                        store.deleteWorkTask(t.id)
                        Task { await workTaskSync.syncForSelectedVineyard() }
                    }
                    dismiss()
                }
                Button("Cancel", role: .cancel) {}
            }
            .onAppear(perform: loadIfEditing)
            .sheet(isPresented: $showBlockPicker) {
                BlockMultiSelectSheet(blocks: assignableBlocks, selected: $selectedBlockIds)
            }
            .sheet(isPresented: $showWorkerTypes) {
                NavigationStack {
                    OperatorCategoriesView()
                        .toolbar {
                            ToolbarItem(placement: .confirmationAction) {
                                Button("Done") { showWorkerTypes = false }
                            }
                        }
                }
            }
        }
    }

    @ViewBuilder
    private func blockAllocationRow(_ alloc: BlockAllocation) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(alloc.name)
                    .font(.subheadline.weight(.semibold))
                Spacer()
                Text(alloc.areaHa.map { fmt.formatArea(hectares: $0) } ?? "— \(fmt.areaUnitAbbreviation)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            HStack(spacing: 10) {
                Text("\(Int((alloc.pctOfTotal * 100).rounded()))%")
                Text(String(format: "%.1fh", alloc.allocatedHours))
                if accessControl?.canViewFinancials ?? false {
                    Text(fmt.formatCurrency(alloc.allocatedCost))
                    if let cph = alloc.costPerHa {
                        Text("\(fmt.formatCurrency(cph / fmt.areaValue(hectares: 1)))/\(fmt.areaUnitAbbreviation)")
                    }
                }
            }
            .font(.caption2)
            .foregroundStyle(.secondary)
        }
        .padding(.vertical, 2)
    }

    private func resourceRow(_ res: Binding<WorkTaskResource>) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Menu {
                ForEach(store.operatorCategories) { cat in
                    Button(cat.name) {
                        res.wrappedValue.operatorCategoryId = cat.id
                        res.wrappedValue.workerTypeName = cat.name
                        res.wrappedValue.hourlyRate = cat.costPerHour
                    }
                }
            } label: {
                HStack {
                    Text(res.wrappedValue.workerTypeName.isEmpty ? "Select worker type" : res.wrappedValue.workerTypeName)
                        .foregroundStyle(res.wrappedValue.workerTypeName.isEmpty ? .secondary : .primary)
                    Spacer()
                    if accessControl?.canViewFinancials ?? false {
                        Text(fmt.formatCurrency(res.wrappedValue.hourlyRate))
                            .foregroundStyle(.secondary)
                            .font(.caption)
                    }
                    Image(systemName: "chevron.up.chevron.down")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            HStack {
                Text("Count")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Spacer()
                Stepper(value: res.count, in: 1...99) {
                    Text("\(res.wrappedValue.count)")
                        .font(.subheadline.weight(.semibold).monospacedDigit())
                }
                .labelsHidden()
                Text("\(res.wrappedValue.count)")
                    .font(.subheadline.weight(.semibold).monospacedDigit())
                    .frame(width: 28)
            }
        }
        .padding(.vertical, 4)
    }

    private func addResource() {
        guard let first = store.operatorCategories.first else { return }
        resources.append(WorkTaskResource(
            operatorCategoryId: first.id,
            workerTypeName: first.name,
            hourlyRate: first.costPerHour,
            count: 1
        ))
    }

    /// Emits a diagnostic when the selected vineyard has no assignable blocks
    /// in the local store so we can tell whether the data simply hasn't synced
    /// yet versus a filtering bug. Read-only; never blocks the form.
    private func logBlockPickerDiagnosticsIfEmpty() {
        guard assignableBlocks.isEmpty else { return }
        #if DEBUG
        let allLocal: Int = store.paddocks.count
        let lastSync = paddockSync.lastSyncDate.map { ISO8601DateFormatter().string(from: $0) } ?? "never"
        let roleHint = "manageSetup=\(accessControl?.canManageSetup ?? false) delete=\(accessControl?.canDelete ?? false)"
        print("""
        [WorkTask] block picker has no assignable blocks \
        vineyardId=\(store.selectedVineyardId?.uuidString ?? "nil") \
        localPaddocks=\(allLocal) \
        afterActiveFilter=\(assignableBlocks.count) \
        role=\(roleHint) \
        syncStatus=\(String(describing: paddockSync.syncStatus)) lastSync=\(lastSync)
        """)
        #endif
    }

    private func loadIfEditing() {
        logBlockPickerDiagnosticsIfEmpty()
        if let t = existingTask {
            date = t.date
            taskType = t.taskType
            if !mergedTaskTypeNames.contains(t.taskType) && !t.taskType.isEmpty {
                showCustomTaskField = true
                customTaskType = t.taskType
            }
            // Prefer existing work_task_paddocks join rows for the selected
            // block set; fall back to the legacy single paddock_id when no join
            // rows exist. Works fully offline once paddock data has synced.
            let links = store.workTaskPaddocks.filter { $0.workTaskId == t.id }
            if !links.isEmpty {
                selectedBlockIds = Set(links.map { $0.paddockId })
            } else if let pid = t.paddockId {
                selectedBlockIds = [pid]
            } else {
                selectedBlockIds = []
            }
            durationText = t.durationHours > 0 ? String(format: "%.2f", t.durationHours) : ""
            notes = t.notes
            resources = t.resources
        }
    }

    private func saveTask() {
        let trimmed = taskType.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }

        // If the user entered a custom task type that is not in the merged
        // catalog yet, persist it to work_task_types so it syncs to other
        // devices and Lovable.
        let lower = trimmed.lowercased()
        let knownLower = Set(mergedTaskTypeNames.map { $0.lowercased() })
        if !knownLower.contains(lower), let vineyardId = store.selectedVineyardId {
            store.addWorkTaskType(WorkTaskType(
                vineyardId: vineyardId,
                name: trimmed,
                isDefault: false,
                sortOrder: 0
            ))
        }

        // Backward-compat scalar fields: first selected block id, and a
        // comma-separated list of selected block names. Empty when no block.
        let orderedSelected = selectedBlocksOrdered
        let primaryBlockId = orderedSelected.first?.id
        let blockNames = orderedSelected.map { $0.name }.joined(separator: ", ")

        var task = existingTask ?? WorkTask()
        task.date = date
        task.taskType = trimmed
        task.paddockId = primaryBlockId
        task.paddockName = blockNames
        task.durationHours = durationHours
        task.resources = resources
        task.notes = notes.trimmingCharacters(in: .whitespacesAndNewlines)
        let userName = auth.userName ?? ""
        task.createdBy = userName.isEmpty ? nil : userName

        // Auto-populate area (hectares) from the selected block so portal
        // reports can show hectares without the operator entering them.
        // Portal-created tasks persist `area_ha` directly; iPhone-created
        // tasks now match by deriving it from the linked block. The block ID
        // is still synced (`paddock_id`) so the portal can re-derive if needed.
        var areaSource = "none"
        let summedArea = totalSelectedArea
        if summedArea > 0 {
            task.areaHa = summedArea
            areaSource = orderedSelected.count > 1 ? "summed-from-blocks" : "derived-from-block"
        } else if orderedSelected.isEmpty, task.areaHa != nil {
            // No block selected (Does not apply to a block) — keep existing value.
            areaSource = "existing"
        } else if task.areaHa != nil {
            areaSource = "existing"
        }

        #if DEBUG
        print("""
        [WorkTask] saveTask id=\(task.id) type=\(task.taskType) \
        blockId=\(task.paddockId?.uuidString ?? "nil") \
        block=\(task.paddockName.isEmpty ? "<none>" : task.paddockName) \
        areaHa=\(task.areaHa.map { String(format: "%.4f", $0) } ?? "nil") \
        areaSource=\(areaSource)
        """)
        #endif

        if isEditing {
            store.updateWorkTask(task)
        } else {
            store.addWorkTask(task)
        }

        reconcileBlockLinks(for: task.id)

        Task {
            await workTaskTypeSync.syncForSelectedVineyard()
            await workTaskSync.syncForSelectedVineyard()
            await workTaskPaddockSync.syncForSelectedVineyard()
        }
        dismiss()
    }

    /// Reconciles work_task_paddocks join rows against the selected block set:
    /// inserts newly selected blocks, refreshes area snapshots for still-selected
    /// blocks, and removes (soft-deletes via sync) blocks that were deselected.
    private func reconcileBlockLinks(for taskId: UUID) {
        guard let vineyardId = store.selectedVineyardId else { return }
        let existing = store.workTaskPaddocks.filter { $0.workTaskId == taskId }
        let existingByPaddock = Dictionary(existing.map { ($0.paddockId, $0) }, uniquingKeysWith: { a, _ in a })

        // Remove links no longer selected.
        for link in existing where !selectedBlockIds.contains(link.paddockId) {
            store.deleteWorkTaskPaddock(link.id)
        }

        // Insert or update selected links with an area snapshot.
        for pid in selectedBlockIds {
            let area = store.paddocks.first(where: { $0.id == pid }).flatMap { areaFor($0) }
            if var row = existingByPaddock[pid] {
                if row.areaHa != area {
                    row.areaHa = area
                    store.updateWorkTaskPaddock(row)
                }
            } else {
                store.addWorkTaskPaddock(WorkTaskPaddock(
                    workTaskId: taskId,
                    vineyardId: vineyardId,
                    paddockId: pid,
                    areaHa: area
                ))
            }
        }
    }
}

/// Per-block hours/cost allocation used by the breakdown section.
private struct BlockAllocation: Identifiable {
    let id: UUID
    let name: String
    let areaHa: Double?
    let pctOfTotal: Double
    let allocatedHours: Double
    let allocatedCost: Double
    let costPerHa: Double?
}

/// Multi-select block picker presented as a sheet. Selecting “Does not apply
/// to a block” clears the set; selecting any block clears the no-block state.
private struct BlockMultiSelectSheet: View {
    @Environment(\.dismiss) private var dismiss
    let blocks: [Paddock]
    @Binding var selected: Set<UUID>

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Button {
                        selected.removeAll()
                    } label: {
                        HStack {
                            Text("Does not apply to a block")
                                .foregroundStyle(.primary)
                            Spacer()
                            if selected.isEmpty {
                                Image(systemName: "checkmark")
                                    .foregroundStyle(.tint)
                            }
                        }
                    }
                }

                if blocks.isEmpty {
                    Section {
                        Text("No blocks available for this vineyard yet.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                } else {
                    Section("Blocks") {
                        ForEach(blocks) { p in
                            Button {
                                toggle(p.id)
                            } label: {
                                HStack {
                                    Text(p.name)
                                        .foregroundStyle(.primary)
                                    Spacer()
                                    if selected.contains(p.id) {
                                        Image(systemName: "checkmark")
                                            .foregroundStyle(.tint)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle("Select Blocks")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                        .fontWeight(.semibold)
                }
            }
        }
    }

    private func toggle(_ id: UUID) {
        if selected.contains(id) {
            selected.remove(id)
        } else {
            selected.insert(id)
        }
    }
}
