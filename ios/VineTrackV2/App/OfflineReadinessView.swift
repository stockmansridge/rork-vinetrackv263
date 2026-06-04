import SwiftUI
import CoreLocation

/// Read-only field-readiness screen. Surfaces whether the device has
/// everything cached locally to keep working in a vineyard with no mobile
/// network: signed-in session, the selected vineyard, its paddocks/rows,
/// the saved chemicals/equipment catalogues, GPS permission, and the
/// current sync backlog. Purely diagnostic — it never changes access or
/// sync behaviour.
struct OfflineReadinessView: View {
    @Environment(NewBackendAuthService.self) private var auth
    @Environment(MigratedDataStore.self) private var store
    @Environment(LocationService.self) private var location

    // Field-data sync services the offline operator depends on. Each exposes
    // `pendingUpsertCount` / `pendingDeleteCount` / `lastSyncDate`.
    @Environment(PinSyncService.self) private var pinSync
    @Environment(PaddockSyncService.self) private var paddockSync
    @Environment(TripSyncService.self) private var tripSync
    @Environment(SprayRecordSyncService.self) private var sprayRecordSync
    @Environment(WorkTaskSyncService.self) private var workTaskSync
    @Environment(MaintenanceLogSyncService.self) private var maintenanceLogSync
    @Environment(SavedChemicalSyncService.self) private var savedChemicalSync
    @Environment(EquipmentItemSyncService.self) private var equipmentItemSync

    @State private var isRefreshing: Bool = false

    var body: some View {
        Form {
            overallSection
            essentialsSection
            dataSection
            gpsSection
            syncSection
            refreshSection
            footerSection
        }
        .navigationTitle("Offline Readiness")
        .navigationBarTitleDisplayMode(.inline)
    }

    // MARK: - Overall banner

    private var overallSection: some View {
        Section {
            HStack(spacing: 14) {
                Image(systemName: isFieldReady ? "checkmark.shield.fill" : "exclamationmark.shield.fill")
                    .font(.system(size: 34))
                    .foregroundStyle(isFieldReady ? Color.green : Color.orange)
                VStack(alignment: .leading, spacing: 3) {
                    Text(isFieldReady ? "Ready for the field" : "Not fully ready")
                        .font(.headline)
                    Text(isFieldReady
                         ? "This device has everything cached to keep working without mobile network."
                         : "Some items below need attention while you still have signal.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .padding(.vertical, 4)
        }
    }

    // MARK: - Essentials

    private var essentialsSection: some View {
        Section {
            ReadinessRow(
                title: "Signed in",
                detail: auth.userEmail ?? (auth.isSignedIn ? "Active session" : "Not signed in"),
                state: auth.isSignedIn ? .good : .bad
            )
            ReadinessRow(
                title: "Vineyard downloaded",
                detail: store.selectedVineyard?.name ?? "None selected",
                state: store.selectedVineyard != nil ? .good : .bad
            )
        } header: {
            Text("Essentials")
        } footer: {
            Text("Your session and the selected vineyard are stored on this device, so the app opens and runs even when Supabase is unreachable.")
        }
    }

    // MARK: - Downloaded data

    private var dataSection: some View {
        Section {
            ReadinessRow(
                title: "Paddocks & rows",
                detail: "\(paddockCount) paddock\(paddockCount == 1 ? "" : "s")",
                state: paddockCount > 0 ? .good : .warn
            )
            ReadinessRow(
                title: "Saved chemicals",
                detail: "\(store.savedChemicals.count) saved",
                state: store.savedChemicals.isEmpty ? .warn : .good
            )
            ReadinessRow(
                title: "Equipment",
                detail: "\(equipmentCount) item\(equipmentCount == 1 ? "" : "s")",
                state: equipmentCount > 0 ? .good : .warn
            )
        } header: {
            Text("Downloaded for offline use")
        } footer: {
            Text("These come from your last sync and are cached on disk. A warning here just means none are set up yet — it won't stop you working offline.")
        }
    }

    // MARK: - GPS

    private var gpsSection: some View {
        Section {
            ReadinessRow(
                title: "GPS permission",
                detail: gpsDetail,
                state: gpsState
            )
            if location.lastUpdateTimestamp != nil {
                LabeledContent("Last GPS fix", value: location.lastUpdateTimestamp?.formatted(date: .omitted, time: .standard) ?? "—")
                    .font(.footnote)
            }
        } header: {
            Text("Location")
        } footer: {
            Text("Trip tracking and pin placement work fully offline — GPS does not require a network connection. \"Always\" allows tracking to continue when the screen locks.")
        }
    }

    // MARK: - Sync backlog

    private var syncSection: some View {
        Section {
            ReadinessRow(
                title: "Pending uploads",
                detail: pendingCount == 0 ? "All changes uploaded" : "\(pendingCount) waiting to sync",
                state: pendingCount == 0 ? .good : .warn
            )
            LabeledContent("Last successful sync", value: lastSyncText)
                .font(.footnote)
        } header: {
            Text("Sync status")
        } footer: {
            Text("Anything you create offline is saved locally and queued. It uploads automatically the next time the device has signal — nothing is lost if you stay out of range.")
        }
    }

    // MARK: - Refresh

    private var refreshSection: some View {
        Section {
            Button {
                Task { await refresh() }
            } label: {
                HStack {
                    Label(isRefreshing ? "Refreshing…" : "Refresh & sync now", systemImage: "arrow.triangle.2.circlepath")
                    Spacer()
                    if isRefreshing { ProgressView() }
                }
            }
            .disabled(isRefreshing || !auth.isSignedIn || store.selectedVineyardId == nil)
        } footer: {
            Text("Run this while you still have signal to push pending changes and pull the latest paddocks, chemicals and equipment before heading out.")
        }
    }

    private var footerSection: some View {
        Section {
            Text("This screen is read-only and never changes app access. Use it as a pre-trip checklist before working in no-service areas.")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
    }

    // MARK: - Derived state

    private var paddockCount: Int {
        guard let vineyardId = store.selectedVineyardId else { return 0 }
        return store.paddocks.filter { $0.vineyardId == vineyardId }.count
    }

    private var equipmentCount: Int {
        guard let vineyardId = store.selectedVineyardId else { return store.equipmentItems.count }
        return store.equipmentItems.filter { $0.vineyardId == vineyardId }.count
    }

    private var pendingCount: Int {
        pinSync.pendingUpsertCount + pinSync.pendingDeleteCount
            + tripSync.pendingUpsertCount + tripSync.pendingDeleteCount
            + sprayRecordSync.pendingUpsertCount + sprayRecordSync.pendingDeleteCount
            + workTaskSync.pendingUpsertCount + workTaskSync.pendingDeleteCount
            + savedChemicalSync.pendingUpsertCount + savedChemicalSync.pendingDeleteCount
            + equipmentItemSync.pendingUpsertCount + equipmentItemSync.pendingDeleteCount
    }

    private var lastSyncDate: Date? {
        [
            pinSync.lastSyncDate,
            paddockSync.lastSyncDate,
            tripSync.lastSyncDate,
            sprayRecordSync.lastSyncDate,
            workTaskSync.lastSyncDate,
            maintenanceLogSync.lastSyncDate,
            savedChemicalSync.lastSyncDate,
            equipmentItemSync.lastSyncDate,
        ].compactMap { $0 }.max()
    }

    private var lastSyncText: String {
        guard let date = lastSyncDate else { return "Never this session" }
        return date.formatted(date: .abbreviated, time: .shortened)
    }

    private var gpsState: ReadinessRow.State {
        switch location.authorizationStatus {
        case .authorizedAlways: return .good
        case .authorizedWhenInUse: return .good
        case .notDetermined: return .warn
        default: return .bad
        }
    }

    private var gpsDetail: String {
        switch location.authorizationStatus {
        case .authorizedAlways: return "Always"
        case .authorizedWhenInUse: return "While using the app"
        case .notDetermined: return "Not requested yet"
        case .denied: return "Denied — enable in Settings"
        case .restricted: return "Restricted"
        @unknown default: return "Unknown"
        }
    }

    /// True when the must-haves for offline work are present. Saved
    /// chemicals/equipment are optional, so they don't block readiness.
    private var isFieldReady: Bool {
        auth.isSignedIn
            && store.selectedVineyard != nil
            && paddockCount > 0
            && (gpsState == .good)
    }

    // MARK: - Actions

    private func refresh() async {
        guard !isRefreshing else { return }
        isRefreshing = true
        defer { isRefreshing = false }
        await pinSync.syncPinsForSelectedVineyard()
        await paddockSync.syncPaddocksForSelectedVineyard()
        await tripSync.syncTripsForSelectedVineyard()
        await sprayRecordSync.syncSprayRecordsForSelectedVineyard()
        await workTaskSync.syncForSelectedVineyard()
        await maintenanceLogSync.syncForSelectedVineyard()
        await savedChemicalSync.syncForSelectedVineyard()
        await equipmentItemSync.syncForSelectedVineyard()
    }
}

/// A single readiness line with a coloured status indicator.
private struct ReadinessRow: View {
    enum State {
        case good, warn, bad

        var symbol: String {
            switch self {
            case .good: return "checkmark.circle.fill"
            case .warn: return "exclamationmark.circle.fill"
            case .bad: return "xmark.circle.fill"
            }
        }

        var color: Color {
            switch self {
            case .good: return .green
            case .warn: return .orange
            case .bad: return .red
            }
        }
    }

    let title: String
    let detail: String
    let state: State

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: state.symbol)
                .font(.title3)
                .foregroundStyle(state.color)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.body)
                Text(detail)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            Spacer()
        }
        .padding(.vertical, 2)
    }
}
